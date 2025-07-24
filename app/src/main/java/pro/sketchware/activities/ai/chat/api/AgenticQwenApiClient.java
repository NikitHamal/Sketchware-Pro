package pro.sketchware.activities.ai.chat.api;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.activities.ai.chat.context.ContextBuilder;
import pro.sketchware.activities.ai.chat.models.ChatMessage;
import pro.sketchware.activities.ai.chat.models.ConversationContext;
import pro.sketchware.activities.ai.storage.ConversationContextStorage;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AgenticQwenApiClient extends QwenApiClient {
    private static final String TAG = "AgenticQwenApiClient";

    private final ContextBuilder contextBuilder;
    private final ConversationContextStorage contextStorage;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface AgenticChatCallback extends ChatCallback {
        void onActionExecuted(String actionResult, String projectId);
        void onProjectCreated(String projectId, String projectName);
        void onFixProposal(String explanation, String actionJson, String projectId);
    }

    public AgenticQwenApiClient(Context context) {
        super(context);
        this.contextBuilder = new ContextBuilder(context);
        this.contextStorage = new ConversationContextStorage(context);
    }

    public void sendMessage(String conversationId, String model, String message,
                          List<ChatMessage.AttachedFile> attachedFiles,
                          boolean thinkingEnabled, boolean webSearchEnabled,
                          AgenticChatCallback callback) {
        executor.execute(() -> {
            try {
                ConversationContext context = contextStorage.loadContext(conversationId);
                String enhancedMessage = contextBuilder.buildEnhancedPrompt(message, context);
                Log.d(TAG, "Enhanced prompt: " + enhancedMessage);

                sendMessage(conversationId, model, enhancedMessage, new ChatCallback() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            JSONObject responseJson = new JSONObject(response);
                            if (responseJson.has("response_type") && "action".equals(responseJson.getString("response_type"))) {
                                executeAction(responseJson, context, callback);
                            } else {
                                mainHandler.post(() -> callback.onResponse(response));
                            }
                        } catch (JSONException e) {
                            mainHandler.post(() -> callback.onResponse(response));
                        }
                    }

                    @Override
                    public void onError(String error) {
                        mainHandler.post(() -> callback.onError(error));
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error in agentic sendMessage", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    private void executeAction(JSONObject actionJson, ConversationContext context, AgenticChatCallback callback) {
        try {
            String actionName = actionJson.getString("action");
            String explanation = actionJson.optString("explanation", "Executing action...");
            JSONObject parameters = actionJson.optJSONObject("parameters");

            Map<String, Object> paramMap = new HashMap<>();
            if (parameters != null) {
                parameters.keys().forEachRemaining(key -> {
                    try {
                        paramMap.put(key, parameters.get(key));
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing parameter: " + key, e);
                    }
                });
            }

            if (isFileRelatedAction(actionName)) {
                mainHandler.post(() -> callback.onFixProposal(explanation, actionJson.toString(), context.getCurrentProjectId()));
                return;
            }

            String result = contextBuilder.executeAction(actionName, paramMap, context.getCurrentProjectId());

            try {
                JSONObject resultJson = new JSONObject(result);
                if (resultJson.optBoolean("success", false)) {
                    if ("create_project".equals(actionName)) {
                        String projectId = resultJson.optString("project_id");
                        String projectName = resultJson.optString("project_name");
                        context.setCurrentProjectId(projectId);
                        context.addExecutedAction(actionName);
                        contextStorage.saveContext(context);
                        mainHandler.post(() -> callback.onProjectCreated(projectId, projectName));
                        return;
                    }
                }
            } catch (JSONException e) {
                // Result is not JSON, treat as plain text
            }

            context.addExecutedAction(actionName);
            contextStorage.saveContext(context);
            mainHandler.post(() -> callback.onActionExecuted(result, context.getCurrentProjectId()));

        } catch (JSONException e) {
            Log.e(TAG, "Error executing action", e);
            mainHandler.post(() -> callback.onError("Error executing action: " + e.getMessage()));
        }
    }

    private boolean isFileRelatedAction(String actionName) {
        return actionName.equals("create_java_file") ||
                actionName.equals("create_xml_resource") ||
                actionName.equals("edit_file") ||
                actionName.equals("delete_file") ||
                actionName.equals("fix_file_error") ||
                actionName.equals("create_file");
    }
}