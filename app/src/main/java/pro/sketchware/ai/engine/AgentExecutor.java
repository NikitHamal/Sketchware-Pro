package pro.sketchware.ai.engine;

import pro.sketchware.ai.engine.TokenOptimizer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import pro.sketchware.ai.api.AiApiClient;
import pro.sketchware.ai.api.AiClientFactory;
import pro.sketchware.ai.api.StreamingResponseHandler;
import pro.sketchware.ai.api.ToolDefinition;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.ToolCall;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.ai.prompts.SystemPrompts;
import pro.sketchware.ai.storage.AiPreferences;
import pro.sketchware.ai.fix.AiFixSupport;
import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolContext;
import pro.sketchware.ai.tools.ToolRegistry;

public class AgentExecutor {

    /**
     * Pulse callback — called before major AI actions.
     * The UI shows a plan summary with Continue (auto 30s) / Cancel buttons.
     */
    public interface PulseConfirmationCallback {
        void onConfirmationRequired(String plan, Runnable onContinue, Runnable onCancel);
    }

    private PulseConfirmationCallback pulseCallback;

    public void setPulseCallback(PulseConfirmationCallback cb) {
        this.pulseCallback = cb;
    }

    private static final int  SAFETY_TOOL_ITERATION_LIMIT = 200;
    private static final long STREAM_TIMEOUT_MS   = 120_000L;  // 120s per request
    /** After how many tool iterations we pause and show Continue/Cancel. */
    private static final int  PULSE_STEPS         = 2;  // every 2 tool calls
    /** Countdown seconds before Continue is auto-selected. */
    private static final int  PULSE_AUTO_SECS     = 10;
    /** Ordered failover providers (tried in sequence on timeout/error). */
    /** Failover order - only enabled providers with API keys will be used. */
    private static final pro.sketchware.ai.models.AiProvider[] FAILOVER_ORDER = {
        pro.sketchware.ai.models.AiProvider.GROQ,
        pro.sketchware.ai.models.AiProvider.SAMBANOVA,
        pro.sketchware.ai.models.AiProvider.TOGETHER,
        pro.sketchware.ai.models.AiProvider.OPENAI,
        pro.sketchware.ai.models.AiProvider.ANTHROPIC,
        pro.sketchware.ai.models.AiProvider.DEEPSEEK,
        pro.sketchware.ai.models.AiProvider.GEMINI,
    };

    private final Context context;
    private final ToolRegistry toolRegistry;
    private final AiPreferences preferences;
    /** Latch used by pulse: UI counts down, then releases to let agent continue. */
    private volatile java.util.concurrent.CountDownLatch pulseLatch;
    /** Counts tool calls across all iterations for pulse trigger. */
    private int toolCallCount = 0;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final AtomicBoolean isCancelled;

    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    private volatile AiApiClient currentClient;

    public interface AgentCallback {
        void onStreamingChunk(String chunk);
        void onAssistantMessage(ChatMessage assistantMessage);
        void onToolCallStarted(ToolCall toolCall);
        void onToolCallProgress(String toolCallId, String status, int progress, boolean indeterminate);
        void onToolCallCompleted(ToolCall toolCall, ToolResult result);
        void onToolMessage(ChatMessage toolMessage);
        void onResponseComplete(ChatMessage assistantMessage);
        void onCancelled();
        void onError(String error);
        void onThinking(String status);
    }

    /** Scope constants — passed from ChatActivity via Intent extras. */
    public static final String SCOPE_GLOBAL  = "global";
    public static final String SCOPE_PROJECT = "project";

    public AgentExecutor(Context context, List<String> workspaceProjectIds, String workspaceId) {
        this(context, workspaceProjectIds, workspaceId, SCOPE_GLOBAL, null);
    }

    /**
     * @param scope           {@link #SCOPE_PROJECT} or {@link #SCOPE_GLOBAL}
     * @param scopedProjectId the single project ID when scope == SCOPE_PROJECT; ignored otherwise
     */
    public AgentExecutor(Context context, List<String> workspaceProjectIds, String workspaceId,
                         String scope, String scopedProjectId) {
        this.context = context.getApplicationContext();
        this.preferences = AiPreferences.getInstance(this.context);
        if (SCOPE_PROJECT.equals(scope) && scopedProjectId != null && !scopedProjectId.isEmpty()) {
            this.toolRegistry = ToolRegistry.createForProject(scopedProjectId);
        } else {
            this.toolRegistry = ToolRegistry.createGlobal();
        }
        this.executor    = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.isCancelled = new AtomicBoolean(false);
    }

    /**
     * Forced stop — sets the cancellation flag, cancels all in-flight HTTP calls,
     * and interrupts the executor thread so the agent loop exits immediately
     * even if it is blocked on a CountDownLatch or a slow tool call.
     */
    public void cancel() {
        isCancelled.set(true);
        // 1. Cancel all in-flight OkHttp requests (streaming + tool downloads)
        AiApiClient client = currentClient;
        if (client != null) {
            client.cancelAll();
        }
        // 2. Interrupt the executor thread so CountDownLatch.await() / Thread.sleep()
        //    throw InterruptedException and the loop exits without waiting for the timeout.
        executor.shutdownNow();   // sends interrupt to running thread
    }

    public void execute(List<ChatMessage> conversationHistory, String modelId,
                        AiProvider provider, String systemPrompt,
                        List<String> allowedProjectIds, String workspaceId,
                        AgentCallback callback) {
        execute(conversationHistory, modelId, provider, systemPrompt,
                allowedProjectIds, workspaceId, null, callback);
    }

    public void execute(List<ChatMessage> conversationHistory, String modelId,
                        AiProvider provider, String systemPrompt,
                        List<String> allowedProjectIds, String workspaceId,
                        String pageContext,
                        AgentCallback callback) {
        isCancelled.set(false);
        toolCallCount = 0;

        executor.execute(() -> {
            try {
                String apiKey = preferences.getApiKey(provider);
                if (provider.requiresApiKey() && (apiKey == null || apiKey.isEmpty())) {
                    postError(callback, "No API key set for " + provider.getDisplayName()
                            + ". Please configure it in AI Settings.");
                    return;
                }

                currentClient = AiClientFactory.createClient(context, provider, apiKey);
                if (currentClient == null) {
                    postError(callback, "Failed to create API client for " + provider.getDisplayName());
                    return;
                }

                // mutable model holder for failover reassignment inside while loop
                // (Java doesn't allow reassigning effectively-final params in lambdas)
                final String[] modelHolder = {preferences.getSelectedModel(provider)};

                ToolContext toolContext = new ToolContext(context, allowedProjectIds, workspaceId);
                toolContext.setCancellationChecker(isCancelled::get);
                toolContext.setToolProgressListener((toolCallId, status, progress, indeterminate) ->
                        mainHandler.post(() -> callback.onToolCallProgress(
                                toolCallId, status, progress, indeterminate)));

                // ── Token Optimiser pipeline ─────────────────────────────────
                // Summarise old turns, truncate bulky tool results, cap total size.
                List<ChatMessage> messages = TokenOptimizer.optimise(
                        new ArrayList<>(conversationHistory));
                String effectiveSystemPrompt     = buildSystemPrompt(systemPrompt, allowedProjectIds, pageContext);
                List<ToolDefinition> toolDefs    = toolRegistry.getToolDefinitions();

                int iteration = 0;
                while (!isCancelled.get()) {
                    iteration++;
                    if (iteration > SAFETY_TOOL_ITERATION_LIMIT) {
                        postError(callback,
                                "Agent stopped after an unusually long autonomous loop. "
                                + "Review the tool cards and continue from the latest state if needed.");
                        return;
                    }

                    StringBuilder fullResponse    = new StringBuilder();
                    List<ToolCall> pendingToolCalls = new ArrayList<>();
                    AtomicBoolean hasError         = new AtomicBoolean(false);
                    CountDownLatch streamLatch      = new CountDownLatch(1);
                    String[] streamError            = new String[1];

                    mainHandler.post(() -> callback.onThinking("Thinking..."));

                    // Use conversationId from the first message as a Tag for cancellation
                    String conversationIdTag = !messages.isEmpty() ? messages.get(0).getConversationId() : null;
                    if (conversationIdTag != null) {
                        currentClient.cancelByTag(conversationIdTag);
                    }

                    currentClient.sendChatRequest(messages, modelHolder[0], effectiveSystemPrompt, toolDefs, conversationIdTag,
                            new StreamingResponseHandler() {
                                @Override
                                public void onChunk(String textDelta) {
                                    if (textDelta == null || isCancelled.get()) return;
                                    fullResponse.append(textDelta);
                                    mainHandler.post(() -> callback.onStreamingChunk(textDelta));
                                }

                                @Override
                                public void onToolCall(ToolCall toolCall) {
                                    if (toolCall == null || isCancelled.get()) return;
                                    pendingToolCalls.add(toolCall);
                                    mainHandler.post(() -> callback.onToolCallStarted(toolCall));
                                }

                                @Override
                                public void onComplete(String response) {
                                    streamLatch.countDown();
                                }

                                @Override
                                public void onError(String error) {
                                    hasError.set(true);
                                    streamError[0] = error;
                                    streamLatch.countDown();
                                }
                            });

                    boolean completed = streamLatch.await(STREAM_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if (isCancelled.get()) { postCancelled(callback); return; }

                    if (!completed || hasError.get()) {
                        String failReason = !completed
                                ? "timed out after " + (STREAM_TIMEOUT_MS / 1000) + "s"
                                : (streamError[0] != null ? streamError[0] : "request error");

                        // Try to failover to next available provider
                        pro.sketchware.ai.models.AiProvider failoverProvider =
                                findFailoverProvider(provider, preferences);
                        if (failoverProvider != null && !isCancelled.get()) {
                            String failoverKey = preferences.getApiKey(failoverProvider);
                            String failoverModel = preferences.getSelectedModel(failoverProvider);
                            mainHandler.post(() -> callback.onThinking(
                                    "⚡ " + provider.getDisplayName() + " " + failReason
                                    + " → switching to " + failoverProvider.getDisplayName() + "..."));
                            try { Thread.sleep(800); } catch (InterruptedException ignored2) {}
                            currentClient = pro.sketchware.ai.api.AiClientFactory
                                    .createClient(context, failoverProvider, failoverKey);
                            modelHolder[0] = failoverModel;
                            continue; // retry this iteration with new provider
                        }

                        // No failover available
                        if (!completed) {
                            postError(callback, "⏱ Provider timed out and no failover is configured.");
                        } else {
                            postError(callback, streamError[0] != null ? streamError[0] : "Unknown AI error");
                        }
                        return;
                    }

                    ChatMessage assistantMsg = ChatMessage.assistantMessage(
                            fullResponse.toString(),
                            pendingToolCalls.isEmpty() ? null : pendingToolCalls);
                    messages.add(assistantMsg);
                    mainHandler.post(() -> callback.onAssistantMessage(assistantMsg));

                    // ── No tool calls → conversation turn complete ──────────────────
                    if (pendingToolCalls.isEmpty()) {
                        mainHandler.post(() -> callback.onResponseComplete(assistantMsg));
                        return;  // ✅ FIX: was missing in original causing infinite loop risk
                    }

                    // ── Execute each tool call ──────────────────────────────────────
                    for (ToolCall tc : pendingToolCalls) {
                        if (isCancelled.get()) {
                            postCancelled(callback);
                            return;
                        }

                        mainHandler.post(() -> callback.onThinking("Running \"" + tc.getName() + "\"..."));
                        toolContext.beginToolCall(tc.getId());
                        toolContext.reportProgress("Starting...", -1, true);
                        ToolResult result = executeTool(tc, toolContext);
                        toolContext.endToolCall();

                        ToolResult finalResult = result;
                        mainHandler.post(() -> callback.onToolCallCompleted(tc, finalResult));

                        String toolContent = result.isSuccess()
                                ? (result.getOutput() != null ? result.getOutput() : "")
                                : "Error: " + (result.getError() != null ? result.getError() : "Tool execution failed");
                        ChatMessage toolResultMsg = ChatMessage.toolResultMessage(
                                tc.getId(), tc.getName(), toolContent);
                        messages.add(toolResultMsg);
                        mainHandler.post(() -> callback.onToolMessage(toolResultMsg));

                        // ── Pulse: pause after every N tool calls for Continue/Cancel ────
                        toolCallCount++;
                        if (pulseCallback != null && toolCallCount % PULSE_STEPS == 0 && !isCancelled.get()) {
                            pulseLatch = new java.util.concurrent.CountDownLatch(1);
                            final boolean[] cancelled = {false};
                            final String stepSummary = "Tool " + toolCallCount
                                    + ": \"" + tc.getName() + "\" done";
                            mainHandler.post(() -> pulseCallback.onConfirmationRequired(
                                    stepSummary,
                                    () -> pulseLatch.countDown(),          // Continue
                                    () -> { cancelled[0] = true; pulseLatch.countDown(); } // Cancel
                            ));
                            boolean timedOut = !pulseLatch.await(PULSE_AUTO_SECS, java.util.concurrent.TimeUnit.SECONDS);
                            if (timedOut || !cancelled[0]) {
                                // Auto-continue or user pressed Continue
                                mainHandler.post(() -> callback.onThinking("Continuing..."));
                            } else {
                                // User pressed Cancel
                                postCancelled(callback);
                                return;
                            }
                        }

                        // ── Feedback Loop: auto-inject fix instruction on build failure ──
                        if ("build_project".equals(tc.getName()) && !result.isSuccess()
                                && preferences.isAutoFixOnError()) {
                            String errOutput = result.getError() != null ? result.getError() : toolContent;
                            if (errOutput != null && !errOutput.trim().isEmpty()) {
                                // Use AiFixSupport to extract richer error context
                                // Use the first allowed project ID for fix context
                                String scId = (toolContext != null
                                        && !toolContext.getAllowedProjectIds().isEmpty())
                                        ? toolContext.getAllowedProjectIds().get(0) : null;
                                String fixPrompt;
                                if (scId != null && !scId.isEmpty()) {
                                    try {
                                        AiFixSupport.FixContext fixCtx =
                                                AiFixSupport.buildSessionAndPrompt(context, scId, errOutput);
                                        if (fixCtx != null && fixCtx.agentPrompt != null) {
                                            fixPrompt = SystemPrompts.AUTO_FIX_PREFIX + fixCtx.agentPrompt;
                                        } else {
                                            fixPrompt = buildBasicFixPrompt(errOutput);
                                        }
                                    } catch (Exception ex) {
                                        fixPrompt = buildBasicFixPrompt(errOutput);
                                    }
                                } else {
                                    fixPrompt = buildBasicFixPrompt(errOutput);
                                }
                                ChatMessage feedbackMsg = ChatMessage.systemMessage(fixPrompt);
                                messages.add(feedbackMsg);
                                mainHandler.post(() -> callback.onThinking("Auto-fixing build errors..."));
                            }
                        }
                    }
                    // loop continues → next AI turn with tool results injected
                }

                // ✅ FIX: postCancelled is now only reached when isCancelled exits the while loop
                postCancelled(callback);

            } catch (Exception e) {
                if (isCancelled.get()) {
                    postCancelled(callback);
                } else {
                    postError(callback, "Error: " + e.getMessage());
                }
            } finally {
                AiApiClient client = currentClient;
                if (client != null) {
                    client.shutdown();
                }
                currentClient = null;
            }
        });
    }

    

    private ToolResult executeTool(ToolCall toolCall, ToolContext toolContext) {
        AgentTool tool = toolRegistry.getTool(toolCall.getName());
        if (tool == null) {
            return ToolResult.failure(toolCall.getId(), "Unknown tool: " + toolCall.getName());
        }
        try {
            JsonObject args;
            String argsStr = toolCall.getArguments();
            if (argsStr != null && !argsStr.isEmpty()) {
                args = JsonParser.parseString(argsStr).getAsJsonObject();
            } else {
                args = new JsonObject();
            }
            ToolResult result = tool.execute(args, toolContext);
            if (result == null) {
                return ToolResult.failure(toolCall.getId(), "Tool returned no result");
            }
            // Ensure the toolCallId is always populated
            if (result.getToolCallId() == null || result.getToolCallId().isEmpty()) {
                return new ToolResult(toolCall.getId(), result.isSuccess(),
                        result.getOutput(), result.getError());
            }
            return result;
        } catch (Exception e) {
            return ToolResult.failure(toolCall.getId(), "Tool execution error: " + e.getMessage());
        }
    }

    // ── System prompt builder ───────────────────────────────────────────────

    private String buildSystemPrompt(String userSystemPrompt, List<String> projectIds) {
        return buildSystemPrompt(userSystemPrompt, projectIds, null);
    }

private String buildSystemPrompt(String userSystemPrompt, List<String> projectIds, String pageContext) {
        StringBuilder sb = new StringBuilder();

        // ── Base system prompt ─────────────────────────────────────────────
        if (userSystemPrompt != null && !userSystemPrompt.isEmpty()) {
            sb.append(userSystemPrompt.trim());
        } else {
            sb.append(AiPreferences.DEFAULT_SYSTEM_PROMPT.trim());
        }

        // ── Active tool catalog (dynamic) ──────────────────────────────────
        sb.append("\n\n");
        sb.append("═══════════════════════════════════════════\n");
        sb.append("  ACTIVE TOOLS IN THIS SESSION\n");
        sb.append("═══════════════════════════════════════════\n");

        java.util.List<AgentTool> all = toolRegistry.getAllTools();
        appendToolGroup(sb, all, "PROJECT",
            "list_projects","get_project_info","create_project","delete_project","duplicate_project");
        appendToolGroup(sb, all, "FILES",
            "read_file","write_file","delete_file","list_files","copy_file","move_file");
        appendToolGroup(sb, all, "ACTIVITIES",
            "list_activities","get_screen_source","create_activity","delete_activity");
        appendToolGroup(sb, all, "UI LAYOUT",
            "get_layout","edit_layout","describe_layout","add_view","modify_view","remove_view");
        appendToolGroup(sb, all, "BLOCK LOGIC (Phase 4)",
            "get_activity_events","get_event_blocks","add_block","modify_block","delete_block",
            "get_moreblocks","create_moreblock","delete_moreblock");
        appendToolGroup(sb, all, "RESOURCES",
            "add_string_resource","add_color_resource","list_resources");
        appendToolGroup(sb, all, "LIBRARIES",
            "list_libraries","add_library","remove_library",
            "attach_local_library","detach_local_library","download_dependency","validate_libraries");
        appendToolGroup(sb, all, "BUILD & COMPILE",
            "build_project","build_with_r8","set_build_compiler",
            "get_compile_logs","get_project_structure");
        appendToolGroup(sb, all, "EXPORT",
            "export_to_android_studio");
        appendToolGroup(sb, all, "UI TOOLS — USE THESE FOR ALL SCREEN CHANGES",
            "generate_layout", "generate_layout_from_description",
            "add_view_xml", "describe_layout",
            "describe_layout_live", "add_view_live", "modify_view_live", "remove_view_live");
        sb.append("\n\u26a1 PREFERRED for UI generation:\n");
        sb.append("   generate_layout / generate_layout_from_description: full screen from description.\n");
        sb.append("   add_view_xml: append XML views to existing layout.\n");
        sb.append("   Both use ViewBeanParser → jC.c.put → live canvas reload (IA proven path).\n\n");
        appendToolGroup(sb, all, "CODE ANALYSIS & QUALITY",
            "analyze_code","review_source_code","validate_rtl_layout");
        appendToolGroup(sb, all, "LIBRARY DISCOVERY", "search_maven");
        appendToolGroup(sb, all, "APP TEMPLATES & LOCALIZATION",
            "create_from_template","add_locale_strings");

        // ── Any remaining tools ────────────────────────────────────────────
        java.util.Set<String> listed = new java.util.HashSet<>(java.util.Arrays.asList(
            "list_projects","get_project_info","create_project","delete_project","duplicate_project",
            "read_file","write_file","delete_file","list_files","copy_file","move_file",
            "list_activities","get_screen_source","create_activity","delete_activity",
            "get_layout","edit_layout","describe_layout","add_view","modify_view","remove_view",
            "get_activity_events","get_event_blocks","add_block","modify_block","delete_block",
            "get_moreblocks","create_moreblock","delete_moreblock",
            "add_string_resource","add_color_resource","list_resources",
            "list_libraries","add_library","remove_library",
            "attach_local_library","detach_local_library","download_dependency","validate_libraries",
            "build_project","build_with_r8","set_build_compiler",
            "get_compile_logs","get_project_structure","export_to_android_studio",
            "analyze_code","review_source_code","validate_rtl_layout",
            "search_maven","create_from_template","add_locale_strings"
        ));
        StringBuilder extras = new StringBuilder();
        for (AgentTool t : all) {
            if (!listed.contains(t.getName())) {
                extras.append("  ").append(t.getName()).append("\n");
                extras.append("      ").append(t.getDescription()).append("\n");
            }
        }
        if (extras.length() > 0) {
            sb.append("── OTHER TOOLS ──────────────────────────\n");
            sb.append(extras);
        }

        // ── Tool routing rules ────────────────────────────────────────────
        sb.append(SystemPrompts.TOOL_ROUTING_HEADER);
        sb.append(SystemPrompts.TOOL_ROUTING_TABLE);
        sb.append(SystemPrompts.WORKFLOW_UI_EDIT);
        sb.append(SystemPrompts.WORKFLOW_UI_NEW);
        // ── BUILD PIPELINE ─────────────────────────────────────────────────
        sb.append(SystemPrompts.BUILD_PIPELINE_HEADER);
        sb.append(SystemPrompts.BUILD_PIPELINE_A);
        sb.append(SystemPrompts.BUILD_PIPELINE_B);
        sb.append(SystemPrompts.BUILD_COMPILER_SETTINGS);
        sb.append(SystemPrompts.BUILD_ERROR_DEDUPLICATION);
        sb.append(SystemPrompts.BUILD_ERROR_ROUTING_TABLE);
        sb.append(SystemPrompts.BUILD_ABSOLUTE_RULES);

        // ── Destructive action guard ───────────────────────────────────────
        sb.append(SystemPrompts.DESTRUCTIVE_ACTION_GUARD);

        // ── Page context ───────────────────────────────────────────────────
        if (pageContext != null && !pageContext.trim().isEmpty()) {
            sb.append("\n");
            sb.append("═══════════════════════════════════════\n");
            sb.append("  LAUNCH CONTEXT\n");
            sb.append("═══════════════════════════════════════\n");
            String contextKey = pageContext.contains("\n") ? pageContext.split("\\n")[0].trim() : pageContext.trim();
            switch (contextKey) {
                case "errors":
                    sb.append(SystemPrompts.CONTEXT_ERRORS);
                    break;
                case "blocks":
                    sb.append(SystemPrompts.CONTEXT_BLOCKS);
                    break;
                case "blocks_creator":
                    sb.append(SystemPrompts.CONTEXT_BLOCKS_CREATOR);
                    break;
                case "libraries":
                    sb.append(SystemPrompts.CONTEXT_LIBRARIES);
                    break;
                case "source_editor":
                    sb.append(SystemPrompts.CONTEXT_SOURCE_EDITOR);
                    break;
                case "design_editor":
                case "design_editor_with_context":
                    sb.append("Launched from: Design Editor (DesignActivity)\n");
                    sb.append("User goal: Edit, generate, or improve the visual UI of the current screen.\n");
                    // ── Parse injected sc_id and current activity ──────────
                    String injectedScId = null;
                    String injectedXmlName = null;
                    String injectedActName = null;
                    boolean multipleProjects = false;
                    for (String contextLine : pageContext.split("\\n")) {
                        if (contextLine.startsWith("sc_id:")) {
                            injectedScId = contextLine.replace("sc_id:", "").trim();
                        } else if (contextLine.startsWith("current_activity:")) {
                            injectedActName = contextLine.replace("current_activity:", "").trim();
                        } else if (contextLine.startsWith("current_xml:")) {
                            injectedXmlName = contextLine.replace("current_xml:", "").trim();
                        } else if (contextLine.startsWith("project_count: multiple")) {
                            multipleProjects = true;
                        }
                    }
                    if (injectedScId != null && !injectedScId.isEmpty()) {
                        sb.append("Active project sc_id: ").append(injectedScId).append("\n");
                    }
                    if (injectedActName != null && !injectedActName.isEmpty()) {
                        sb.append("Currently open screen: ").append(injectedXmlName)
                          .append(" (activity_name=\"").append(injectedActName).append("\")\n");
                    }
                    sb.append("\n");
                    sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                    sb.append("  UI GENERATION — MANDATORY APPROACH\n");
                    sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                    sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                    sb.append("  RULE: GENERATE vs EDIT\n");
                    sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                    sb.append("• Creating NEW layout from scratch → generate_layout(description=\"...\")\n");
                    sb.append("  Do NOT include current_layout in description for new creation.\n");
                    sb.append("• EDITING/MODIFYING existing layout → FIRST call describe_layout\n");
                    sb.append("  then call generate_layout with description that includes\n");
                    sb.append("  current_layout=<xml from describe_layout> AND your changes.\n");
                    sb.append("  Or use add_view_xml with replace=false to add specific views.\n");
                    sb.append("\n");
                    sb.append("To CREATE or REPLACE an entire screen layout:\n");
                    sb.append("  → Use tool: generate_layout\n");
                    sb.append("  → Required params:\n");
                    final String safeScId = (injectedScId != null && !injectedScId.isEmpty()) ? injectedScId : "<sc_id>";
                    final String safeActName = (injectedActName != null && !injectedActName.isEmpty()) ? injectedActName : "<activity_name>";
                    sb.append("      sc_id:          \"").append(safeScId).append("\"\n");
                    sb.append("      activity_name:  \"").append(safeActName).append("\"\n");
                    sb.append("      description:    <natural language of desired UI>\n");
                    sb.append("  Example:\n");
                    sb.append("    generate_layout({\"sc_id\":\"").append(safeScId)
                      .append("\",\"activity_name\":\"").append(safeActName)
                      .append("\",\"description\":\"calculator with 4x4 button grid\"})\n");
                    sb.append("\n");
                    sb.append("To ADD specific views to existing layout:\n");
                    sb.append("  → Use tool: add_view_xml\n");
                    sb.append("  → Required params:\n");
                    sb.append("      sc_id:         \"").append(safeScId).append("\"\n");
                    sb.append("      activity_name: \"").append(safeActName).append("\"\n");
                    sb.append("      xml:           <Android XML snippet for the view>\n");
                    sb.append("      replace:       false (to merge) or true (to replace all)\n");
                    sb.append("\n");
                    sb.append("To READ current layout:\n");
                    sb.append("  → Use tool: describe_layout\n");
                    sb.append("      sc_id:         \"").append(safeScId).append("\"\n");
                    sb.append("      activity_name: \"").append(safeActName).append("\"\n");
                    sb.append("\n");
                    if (multipleProjects || injectedActName == null) {
                        sb.append("⚠ CONFIRMATION REQUIRED:\n");
                        sb.append("  If the user asks to update the UI but hasn't said WHICH screen,\n");
                        sb.append("  ASK: \"Which screen do you want me to update?\"\n");
                        sb.append("  If there is only one activity (the open one), act immediately without asking.\n");
                    } else {
                        sb.append("✅ You know the target screen: sc_id=\"").append(safeScId)
                          .append("\" activity_name=\"").append(safeActName).append("\"\n");
                        sb.append("  → Act immediately. No need to ask which screen.\n");
                    }
                    sb.append("\n");
                    sb.append("❌ FORBIDDEN for UI editing:\n");
                    sb.append("  - write_file to layout files (wrong format — Sketchware uses encrypted ViewBeans)\n");
                    sb.append("  - Manual JSON ViewBean construction\n");
                    sb.append("  - Using add_view / modify_view (these are JSON tools, not XML)\n");
                    sb.append("\n");
                    sb.append("After generating/editing the layout, the Design Editor canvas reloads automatically.\n");
                    break;
                case "resource_editor":
                    sb.append(SystemPrompts.CONTEXT_RESOURCE_EDITOR);
                    break;
                case "build_fix":
                    sb.append(SystemPrompts.CONTEXT_BUILD_FIX);
                    break;
                default:
                    sb.append("Launch context: ").append(pageContext.trim()).append("\n");
                    break;
            }
        }

        // ── Scope (Global / Project / Page) ───────────────────────────────
        sb.append("\n");
        sb.append("═══════════════════════════════════════════════════\n");
        sb.append("  SEARCH & FILE ACCESS SCOPE\n");
        sb.append("═══════════════════════════════════════════════════\n");
        if (projectIds != null && projectIds.size() == 1) {
            String pid = projectIds.get(0);
            sb.append("Scope: PROJECT (sc_id=").append(pid).append(")\n");
            sb.append("• All file, layout, logic, and block operations are restricted to project ").append(pid).append(".\n");
            sb.append("• list_files / read_file / write_file: only paths inside sc_id=").append(pid).append(".\n");
            sb.append("• global_search: searches ONLY the files of project ").append(pid).append(", not other projects.\n");
            sb.append("• You CANNOT read, write, or create files in any other project.\n");
            if (pageContext != null && (pageContext.equals("design_editor") || pageContext.equals("resource_editor"))) {
                sb.append("• Page scope active: prefer describe_layout_live / add_view_live / modify_view_live\n");
                sb.append("  for instant live-preview changes without restarting the project.\n");
            }
        } else if (projectIds != null && !projectIds.isEmpty()) {
            sb.append("Scope: GLOBAL WORKSPACE\n");
            sb.append("• Projects accessible: ").append(String.join(", ", projectIds)).append("\n");
            sb.append("• global_search: searches ALL projects in this workspace.\n");
            sb.append("• You can create, delete, duplicate, and cross-copy files between workspace projects.\n");
            sb.append("• Always specify sc_id when calling project-specific tools.\n");
        } else {
            sb.append("Scope: GLOBAL (no projects attached)\n");
            sb.append("• Create or add a project first before editing files or logic.\n");
        }

        return sb.toString();
    }

    /**
     * Appends a formatted tool group section to the system prompt.
     * Only includes tools that are actually registered in the current registry.
     */
    private void appendToolGroup(StringBuilder sb, java.util.List<AgentTool> all,
                                  String groupName, String... toolNames) {
        java.util.List<AgentTool> found = new java.util.ArrayList<>();
        for (String name : toolNames) {
            for (AgentTool t : all) {
                if (t.getName().equals(name)) { found.add(t); break; }
            }
        }
        if (found.isEmpty()) return;
        sb.append("── ").append(groupName).append(" ");
        int pad = 40 - groupName.length() - 3;
        for (int i = 0; i < pad; i++) sb.append("─");
        sb.append("\n");
        for (AgentTool t : found) {
            sb.append("  ").append(t.getName());
            int spaces = 30 - t.getName().length();
            for (int i = 0; i < spaces; i++) sb.append(" ");
            String desc = t.getDescription();
            int dot = desc.indexOf(". ");
            if (dot > 0 && dot < 80) desc = desc.substring(0, dot);
            if (desc.length() > 75) desc = desc.substring(0, 72) + "...";
            sb.append(desc).append("\n");
        }
        sb.append("\n");
    }

    /**
     * Builds a basic auto-fix prompt when AiFixSupport cannot resolve deeper context.
     * Truncates the error log to avoid token overflow.
     */
    private String buildBasicFixPrompt(String errOutput) {
        return SystemPrompts.buildBasicFixPrompt(errOutput);
    }

    private void postError(AgentCallback callback, String error) {
        mainHandler.post(() -> callback.onError(error));
    }

    /**
     * Finds the next available provider from FAILOVER_ORDER that has an API key
     * and is different from the current provider.
     */
    /**
     * Finds next available provider: different from current, enabled by user, has API key.
     */
    private static pro.sketchware.ai.models.AiProvider findFailoverProvider(
            pro.sketchware.ai.models.AiProvider current, AiPreferences prefs) {
        for (pro.sketchware.ai.models.AiProvider p : FAILOVER_ORDER) {
            if (p == current) continue;
            if (!prefs.isProviderEnabled(p)) continue;
            if (!p.requiresApiKey()) return p;
            if (prefs.hasApiKey(p)) return p;
        }
        return null;
    }

    private void postCancelled(AgentCallback callback) {
        mainHandler.post(callback::onCancelled);
    }

    public void shutdown() {
        isCancelled.set(true);
        AiApiClient client = currentClient;
        if (client != null) client.cancelAll();
        if (!executor.isShutdown()) executor.shutdownNow();
        executor.shutdownNow();
    }
}
