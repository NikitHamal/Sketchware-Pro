package pro.sketchware.ai.engine;

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
import pro.sketchware.ai.api.GeminiApiClient;
import pro.sketchware.ai.api.NvidiaApiClient;
import pro.sketchware.ai.api.OpenRouterApiClient;
import pro.sketchware.ai.api.StreamingResponseHandler;
import pro.sketchware.ai.api.ToolDefinition;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.ToolCall;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.ai.storage.AiPreferences;
import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolContext;
import pro.sketchware.ai.tools.ToolRegistry;

public class AgentExecutor {

    private static final int MAX_TOOL_ITERATIONS = 20;
    private static final long STREAM_TIMEOUT_MS = 180_000L;

    private final Context context;
    private final ToolRegistry toolRegistry;
    private final AiPreferences preferences;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final AtomicBoolean isCancelled;

    public interface AgentCallback {
        void onStreamingChunk(String chunk);
        void onAssistantMessage(ChatMessage assistantMessage);
        void onToolCallStarted(ToolCall toolCall);
        void onToolCallCompleted(ToolCall toolCall, ToolResult result);
        void onToolMessage(ChatMessage toolMessage);
        void onResponseComplete(ChatMessage assistantMessage);
        void onError(String error);
        void onThinking(String status);
    }

    public AgentExecutor(Context context, List<String> workspaceProjectIds, String workspaceId) {
        this.context = context.getApplicationContext();
        this.preferences = AiPreferences.getInstance(this.context);
        this.toolRegistry = ToolRegistry.createDefault();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.isCancelled = new AtomicBoolean(false);
    }

    public void cancel() {
        isCancelled.set(true);
    }

    public void execute(List<ChatMessage> conversationHistory, String modelId,
                        AiProvider provider, String systemPrompt,
                        List<String> allowedProjectIds, String workspaceId,
                        AgentCallback callback) {
        isCancelled.set(false);

        executor.execute(() -> {
            try {
                String apiKey = preferences.getApiKey(provider);
                if (apiKey == null || apiKey.isEmpty()) {
                    postError(callback, "No API key set for " + provider.getDisplayName() +
                            ". Please configure it in AI Settings.");
                    return;
                }

                AiApiClient client = createClient(provider, apiKey);
                if (client == null) {
                    postError(callback, "Failed to create API client for " + provider.getDisplayName());
                    return;
                }

                ToolContext toolContext = new ToolContext(context, allowedProjectIds, workspaceId);
                List<ChatMessage> messages = new ArrayList<>(conversationHistory);
                String effectiveSystemPrompt = buildSystemPrompt(systemPrompt, allowedProjectIds);
                List<ToolDefinition> toolDefs = toolRegistry.getToolDefinitions();

                int iteration = 0;
                while (iteration < MAX_TOOL_ITERATIONS && !isCancelled.get()) {
                    iteration++;

                    StringBuilder fullResponse = new StringBuilder();
                    List<ToolCall> pendingToolCalls = new ArrayList<>();
                    AtomicBoolean hasError = new AtomicBoolean(false);
                    CountDownLatch streamLatch = new CountDownLatch(1);
                    String[] streamError = new String[1];

                    mainHandler.post(() -> callback.onThinking("Thinking..."));

                    client.sendChatRequest(messages, modelId, effectiveSystemPrompt, toolDefs,
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
                    if (!completed) {
                        postError(callback, "The AI provider timed out before completing the response.");
                        return;
                    }
                    if (isCancelled.get()) {
                        return;
                    }
                    if (hasError.get()) {
                        postError(callback, streamError[0] != null ? streamError[0] : "Unknown AI request error");
                        return;
                    }

                    ChatMessage assistantMsg = ChatMessage.assistantMessage(
                            fullResponse.toString(), pendingToolCalls.isEmpty() ? null : pendingToolCalls);
                    messages.add(assistantMsg);
                    mainHandler.post(() -> callback.onAssistantMessage(assistantMsg));

                    if (pendingToolCalls.isEmpty()) {
                        mainHandler.post(() -> callback.onResponseComplete(assistantMsg));
                        return;
                    }

                    for (ToolCall tc : pendingToolCalls) {
                        if (isCancelled.get()) return;

                        mainHandler.post(() -> callback.onThinking("Running: " + tc.getName()));
                        ToolResult result = executeTool(tc, toolContext);
                        mainHandler.post(() -> callback.onToolCallCompleted(tc, result));

                        String toolContent = result.isSuccess()
                                ? (result.getOutput() != null ? result.getOutput() : "")
                                : "Error: " + (result.getError() != null ? result.getError() : "Tool execution failed");
                        ChatMessage toolResultMsg = ChatMessage.toolResultMessage(tc.getId(), tc.getName(), toolContent);
                        messages.add(toolResultMsg);
                        mainHandler.post(() -> callback.onToolMessage(toolResultMsg));
                    }
                }

                if (iteration >= MAX_TOOL_ITERATIONS) {
                    postError(callback, "Agent reached maximum tool call iterations (" +
                            MAX_TOOL_ITERATIONS + "). Please try a more focused request.");
                }

            } catch (Exception e) {
                postError(callback, "Error: " + e.getMessage());
            }
        });
    }

    private AiApiClient createClient(AiProvider provider, String apiKey) {
        switch (provider) {
            case GEMINI:
                return new GeminiApiClient(apiKey);
            case NVIDIA:
                return new NvidiaApiClient(apiKey);
            case OPENROUTER:
                return new OpenRouterApiClient(apiKey);
            default:
                return null;
        }
    }

    private ToolResult executeTool(ToolCall toolCall, ToolContext toolContext) {
        AgentTool tool = toolRegistry.getTool(toolCall.getName());
        if (tool == null) {
            return new ToolResult(toolCall.getId(), false, null,
                    "Unknown tool: " + toolCall.getName());
        }

        try {
            JsonObject args;
            String argsStr = toolCall.getArguments();
            if (argsStr != null && !argsStr.isEmpty()) {
                args = JsonParser.parseString(argsStr).getAsJsonObject();
            } else {
                args = new JsonObject();
            }
            return tool.execute(args, toolContext);
        } catch (Exception e) {
            return new ToolResult(toolCall.getId(), false, null,
                    "Tool execution error: " + e.getMessage());
        }
    }

    private String buildSystemPrompt(String userSystemPrompt, List<String> projectIds) {
        StringBuilder sb = new StringBuilder();

        if (userSystemPrompt != null && !userSystemPrompt.isEmpty()) {
            sb.append(userSystemPrompt.trim());
        } else {
            sb.append(AiPreferences.DEFAULT_SYSTEM_PROMPT.trim());
        }

        sb.append("\n\n## Operating Rules\n");
        sb.append("- Use tools for all project, file, activity, resource, library, and compilation actions.\n");
        sb.append("- Never claim a file, screen, resource, dependency, or project was created unless a tool result confirms it.\n");
        sb.append("- Before changing an existing project, inspect it first with project and activity/file tools.\n");
        sb.append("- Keep all changes compatible with Sketchware Pro storage, code generation, and compilation flows.\n");
        sb.append("- When a tool fails, inspect the returned error, adapt the plan, and retry only with a more specific corrective action.\n");
        sb.append("- Prefer incremental, verifiable steps over large speculative edits.\n");

        sb.append("\n## Available Tools\n");
        for (AgentTool tool : toolRegistry.getAllTools()) {
            sb.append("- **").append(tool.getName()).append("**: ").append(tool.getDescription()).append("\n");
        }

        if (projectIds != null && !projectIds.isEmpty()) {
            sb.append("\n## Workspace Projects\n");
            sb.append("You can access only these workspace project IDs: ")
                    .append(String.join(", ", projectIds)).append(".\n");
            sb.append("Use get_project_info and list_activities or file tools before mutating them.\n");
        } else {
            sb.append("\n## Workspace Projects\n");
            sb.append("No existing projects are currently attached to this workspace. If the task requires a project, create one first.\n");
        }

        return sb.toString();
    }

    private void postError(AgentCallback callback, String error) {
        mainHandler.post(() -> callback.onError(error));
    }

    public void shutdown() {
        isCancelled.set(true);
        executor.shutdownNow();
    }
}
