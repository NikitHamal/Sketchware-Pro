package pro.sketchware.ai.offline;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.ai.chat.coordinator.ChatCoordinator;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolContext;
import pro.sketchware.ai.tools.ToolRegistry;

public class OfflineModeController {

    private static final String TAG = "OfflineModeController";

    @NonNull
    private final ToolRegistry toolRegistry;

    @NonNull
    private final ToolContext toolContext;

    @NonNull
    private final ChatCoordinator coordinator;

    @NonNull
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @NonNull
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "OfflineTool-Worker");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean isOfflineModeActive = false;

    public OfflineModeController(
            @NonNull ToolRegistry toolRegistry,
            @NonNull ToolContext toolContext,
            @NonNull ChatCoordinator coordinator
    ) {
        this.toolRegistry = toolRegistry;
        this.toolContext = toolContext;
        this.coordinator = coordinator;
    }

    public void activateOfflineMode() {
        if (isOfflineModeActive) return;
        isOfflineModeActive = true;
        Log.d(TAG, "Offline mode ACTIVATED.");
        mainHandler.post(() ->
            coordinator.addSystemMessage(
                "**Offline Mode Active**\n"
                + "AI is not available. You can still use tools directly.\n"
                + "Type a tool command or tap the tools icon."
            )
        );
    }

    public void deactivateOfflineMode() {
        if (!isOfflineModeActive) return;
        isOfflineModeActive = false;
        Log.d(TAG, "Offline mode DEACTIVATED.");
        mainHandler.post(() ->
            coordinator.addSystemMessage("**Online Mode Restored** — AI is now available.")
        );
    }

    public boolean isOfflineModeActive() {
        return isOfflineModeActive;
    }

    public void executeToolDirectly(
            @NonNull String toolName,
            @Nullable String jsonInput
    ) {
        Log.d(TAG, "executeToolDirectly: " + toolName);

        AgentTool tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            mainHandler.post(() ->
                coordinator.addInternalAssistantMessage(
                    "Unknown tool: `" + toolName + "`\n\n" + getAvailableToolsText()
                )
            );
            return;
        }

        mainHandler.post(() ->
            coordinator.addInternalAssistantMessage(
                "Running tool: `" + toolName + "`..."
            )
        );

        JsonObject args = parseJsonInput(jsonInput);

        executor.execute(() -> {
            pro.sketchware.ai.models.ToolResult result = tool.execute(args, toolContext);
            mainHandler.post(() -> {
                if (result.isSuccess()) {
                    coordinator.addToolResultMessage(toolName, result.getOutput());
                } else {
                    coordinator.addInternalAssistantMessage(
                        "**Tool failed: `" + toolName + "`**\n\n" + result.getError()
                    );
                }
            });
        });
    }

    @NonNull
    public List<AgentTool> getAvailableTools() {
        return toolRegistry.getAllTools();
    }

    @Nullable
    public AgentTool getTool(@NonNull String toolName) {
        return toolRegistry.getTool(toolName);
    }

    public boolean handleOfflineMessage(@NonNull ChatMessage userMessage) {
        if (!isOfflineModeActive) return false;

        String text = userMessage.getContent();
        if (text == null || text.trim().isEmpty()) return false;

        String lower = text.trim().toLowerCase();

        if (lower.startsWith("/")) {
            return parseAndExecuteToolCommand(text.trim());
        }

        showAvailableToolsHelp();
        return true;
    }

    private boolean parseAndExecuteToolCommand(@NonNull String text) {
        int spaceIdx = text.indexOf(' ');
        String toolName;
        String jsonInput = null;

        if (spaceIdx < 0) {
            toolName = text.substring(1);
        } else {
            toolName = text.substring(1, spaceIdx);
            jsonInput = text.substring(spaceIdx + 1).trim();
        }

        if (toolRegistry.getTool(toolName) == null) {
            mainHandler.post(() ->
                coordinator.addInternalAssistantMessage(
                    "Unknown tool: `" + toolName + "`\n\n" + getAvailableToolsText()
                )
            );
            return true;
        }

        executeToolDirectly(toolName, jsonInput);
        return true;
    }

    private void showAvailableToolsHelp() {
        mainHandler.post(() ->
            coordinator.addInternalAssistantMessage(
                "**Offline Mode** — Available commands:\n\n"
                + getAvailableToolsText()
                + "\n\nUsage: `/tool_name {\"key\":\"value\"}`"
            )
        );
    }

    @NonNull
    private String getAvailableToolsText() {
        List<AgentTool> tools = toolRegistry.getAllTools();
        if (tools.isEmpty()) return "No tools registered.";

        StringBuilder sb = new StringBuilder();
        for (AgentTool tool : tools) {
            sb.append("- `/").append(tool.getName()).append("` — ")
              .append(tool.getDescription()).append('\n');
        }
        return sb.toString().trim();
    }

    @Nullable
    private JsonObject parseJsonInput(@Nullable String jsonInput) {
        if (jsonInput == null || jsonInput.trim().isEmpty()) return new JsonObject();
        try {
            return JsonParser.parseString(jsonInput).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}