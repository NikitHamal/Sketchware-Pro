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

    private static final int SAFETY_TOOL_ITERATION_LIMIT = 200;
    private static final long STREAM_TIMEOUT_MS = 180_000L;

    private final Context context;
    private final ToolRegistry toolRegistry;
    private final AiPreferences preferences;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final AtomicBoolean isCancelled;

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

                    currentClient.sendChatRequest(messages, modelId, effectiveSystemPrompt, toolDefs, conversationIdTag,
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
                    if (isCancelled.get()) {
                        postCancelled(callback);
                        return;
                    }
                    if (!completed) {
                        postError(callback, "The AI provider timed out before completing the response.");
                        return;
                    }
                    if (hasError.get()) {
                        postError(callback, streamError[0] != null ? streamError[0] : "Unknown AI request error");
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
                                            fixPrompt = "SYSTEM: Build failed. Analyse and fix all errors, "
                                                    + "then run build_project again. Fix automatically — do NOT ask the user.\n\n"
                                                    + fixCtx.agentPrompt;
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
            "build_project","get_compile_logs","get_project_structure");
        appendToolGroup(sb, all, "EXPORT",
            "export_to_android_studio");
        appendToolGroup(sb, all, "XML LAYOUT (Preferred — uses ViewBeanParser, always works)",
            "add_view_xml","generate_layout","describe_layout","add_view","modify_view","remove_view",
            "describe_layout_live","add_view_live","modify_view_live","remove_view_live");
        sb.append("\n\u26a1 PREFERRED: use add_view_xml or generate_layout (ViewBeanParser, guaranteed on canvas).\n\n");
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
            "build_project","get_compile_logs","get_project_structure","export_to_android_studio",
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

        // ── Critical rules ─────────────────────────────────────────────────
        sb.append("\n");
        sb.append("═══════════════════════════════════════════════════\n");
        sb.append("  CRITICAL RULES — READ BEFORE EVERY ACTION\n");
        sb.append("═══════════════════════════════════════════════════\n");
        sb.append("UI EDITING: Use add_view/modify_view/remove_view ONLY.\n");
        sb.append("            Never use write_file to edit a screen layout.\n");
        sb.append("            Sketchware uses JSON view format, not XML files.\n");
        sb.append("RAW XML:    EXCEPTION for raw XML files (e.g. res/layout/design.xml):\n");
        sb.append("            describe_layout FAILS on these — use read_file + write_file.\n");
        sb.append("            Always use android:id=\"@+id/\" (NOT @id/) to declare IDs.\n");
        sb.append("            @id/ only REFERENCES; @+id/ DECLARES. Wrong prefix = build error.\n");
        sb.append("LOGIC:      Always call get_event_blocks before add_block.\n");
        sb.append("            Check block IDs before modify_block or delete_block.\n");
        sb.append("READ FIRST: Before any edit, read the current state with:\n");
        sb.append("            describe_layout (UI), get_event_blocks (logic),\n");
        sb.append("            get_project_info (project), list_files (files).\n");
        sb.append("            For raw XML files: use read_file instead of describe_layout.\n");
        sb.append("BUILD:      After edits, call build_project to verify.\n");
        sb.append("            On error, call get_compile_logs and fix automatically.\n");
        sb.append("CONFIRM:    Before create_project/delete_project/duplicate_project,\n");
        sb.append("            confirm with the user. Never act on ambiguous input.\n");

        // ── Destructive action guard ───────────────────────────────────────
        sb.append("\n");
        sb.append("═══════════════════════════════════════════════\n");
        sb.append("  DESTRUCTIVE ACTIONS — REQUIRE CONFIRMATION\n");
        sb.append("═══════════════════════════════════════════════\n");
        sb.append("The following require explicit user confirmation before execution:\n");
        sb.append("  delete_project, duplicate_project, create_project\n");
        sb.append("  delete_activity, delete_file, delete_block, delete_moreblock\n");
        sb.append("If the user request is unclear or contains gibberish, ask for\n");
        sb.append("clarification. Never guess intent for destructive operations.\n");

        // ── Page context ───────────────────────────────────────────────────
        if (pageContext != null && !pageContext.trim().isEmpty()) {
            sb.append("\n");
            sb.append("═══════════════════════════════════════\n");
            sb.append("  LAUNCH CONTEXT\n");
            sb.append("═══════════════════════════════════════\n");
            switch (pageContext.trim()) {
                case "errors":
                    sb.append("Launched from: Compile Log screen\n");
                    sb.append("User goal: Fix build errors shown in the log\n");
                    sb.append("Action plan:\n");
                    sb.append("  1. Call get_compile_logs to read the full error\n");
                    sb.append("  2. Identify the root cause (wrong import, typo, etc.)\n");
                    sb.append("  3. Fix the source or resource file with write_file\n");
                    sb.append("  4. Call build_project to verify the fix\n");
                    sb.append("  5. Repeat until the build succeeds\n");
                    break;
                case "blocks":
                    sb.append("Launched from: Custom Blocks Manager\n");
                    sb.append("User goal: Manage custom block definitions\n");
                    sb.append("Action plan:\n");
                    sb.append("  1. Call get_moreblocks to list existing moreblocks\n");
                    sb.append("  2. Use create_moreblock / add_block to add logic\n");
                    sb.append("  3. Use modify_block / delete_moreblock to edit\n");
                    break;
                case "blocks_creator":
                    sb.append("Launched from: Blocks Creator screen\n");
                    sb.append("User goal: Create a complete set of custom blocks\n");
                    sb.append("Action plan:\n");
                    sb.append("  1. Ask user what kind of blocks they want\n");
                    sb.append("  2. Call create_moreblock for each function\n");
                    sb.append("  3. Use add_block with addSourceDirectly for Java code\n");
                    break;
                case "libraries":
                    sb.append("Launched from: Library Manager screen\n");
                    sb.append("User goal: Audit and improve project dependencies\n");
                    sb.append("Action plan:\n");
                    sb.append("  1. Call list_libraries to see current state\n");
                    sb.append("  2. Call validate_libraries to check compatibility\n");
                    sb.append("  3. Use add_library / attach_local_library as needed\n");
                    break;
                case "source_editor":
                    sb.append("Launched from: Source Code Editor\n");
                    sb.append("User goal: Review or improve Java source code\n");
                    sb.append("Action plan:\n");
                    sb.append("  1. Call get_screen_source to read the current code\n");
                    sb.append("  2. Identify improvements (null safety, imports, etc.)\n");
                    sb.append("  3. Use write_file to apply the corrected source\n");
                    sb.append("  4. Call build_project to verify compilation\n");
                    break;
                case "design_editor":
                    sb.append("Launched from: Design Editor (DesignActivity)\n");
                    sb.append("User goal: Edit the visual UI of the current screen\n");
                    sb.append("Action plan:\n");
                    sb.append("  1. Call describe_layout to understand the current UI structure\n");
                    sb.append("  2. Use add_view / modify_view / remove_view for Sketchware JSON layouts\n");
                    sb.append("  ⚠ IMPORTANT: res/layout/design.xml is a RAW XML file, NOT Sketchware JSON.\n");
                    sb.append("     describe_layout WILL FAIL on design.xml — use read_file + write_file.\n");
                    sb.append("     Always use android:id=\"@+id/\" (not @id/) when editing raw XML.\n");
                    sb.append("  3. Call build_project to verify changes\n");
                    break;
                case "resource_editor":
                    sb.append("Launched from: Resource Editor\n");
                    sb.append("User goal: Edit or add resources (strings, colors, drawables, layouts)\n");
                    sb.append("Action plan:\n");
                    sb.append("  1. Call list_resources to see current resources\n");
                    sb.append("  2. Use add_string_resource / add_color_resource for simple resources\n");
                    sb.append("  3. For raw XML resource files, use read_file + write_file\n");
                    sb.append("  4. When adding android:id in XML, always use @+id/ prefix\n");
                    sb.append("  5. Call build_project to verify\n");
                    break;
                case "build_fix":
                    sb.append("Launched from: Build Error Fix mode\n");
                    sb.append("User goal: Automatically diagnose and fix build errors\n");
                    sb.append("Action plan:\n");
                    sb.append("  1. Call get_compile_logs to read the full error output\n");
                    sb.append("  2. Identify the error type:\n");
                    sb.append("     - 'resource not found @id/X' → change @id/ to @+id/ in XML\n");
                    sb.append("     - 'cannot find symbol' → check imports and class names\n");
                    sb.append("     - 'duplicate resource' → remove duplicate declarations\n");
                    sb.append("     - 'missing drawable' → create the missing drawable XML\n");
                    sb.append("  3. For raw XML files (design.xml, etc.): use read_file + write_file\n");
                    sb.append("  4. Call build_project to verify the fix\n");
                    sb.append("  5. Repeat until build succeeds\n");
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
        String shortErr = errOutput.trim();
        if (shortErr.length() > 1600) {
            shortErr = shortErr.substring(0, 800)
                    + "\n\n... [middle of log truncated to save tokens] ...\n\n"
                    + shortErr.substring(shortErr.length() - 800);
        }
        return "SYSTEM: Build failed. Analyse and fix all errors below, "
                + "then run build_project again. Fix automatically — do NOT ask the user.\n\n"
                + "=== BUILD ERRORS ===\n" + shortErr + "\n=== END ===";
    }

    private void postError(AgentCallback callback, String error) {
        mainHandler.post(() -> callback.onError(error));
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
