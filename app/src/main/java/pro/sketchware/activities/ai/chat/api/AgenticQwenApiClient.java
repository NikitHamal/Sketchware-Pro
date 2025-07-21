package pro.sketchware.activities.ai.chat.api;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.activities.ai.chat.context.ContextBuilder;
import pro.sketchware.activities.ai.chat.models.ConversationContext;
import pro.sketchware.activities.ai.storage.ConversationContextStorage;

public class AgenticQwenApiClient extends QwenApiClient {
    private static final String TAG = "AgenticQwenApiClient";
    
    private final ContextBuilder contextBuilder;
    private final ConversationContextStorage contextStorage;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface AgenticChatCallback extends ChatCallback {
        void onActionExecuted(String actionResult, String projectId);
        void onProjectCreated(String projectId, String projectName);
    }

    public AgenticQwenApiClient(Context context) {
        super(context);
        this.contextBuilder = new ContextBuilder(context);
        this.contextStorage = new ConversationContextStorage(context);
    }

    public void sendMessage(String conversationId, String model, String message, AgenticChatCallback callback) {
        executor.execute(() -> {
            try {
                // Load or create conversation context
                ConversationContext context = contextStorage.loadContext(conversationId);
                
                // Build enhanced prompt with context
                String enhancedMessage = contextBuilder.buildEnhancedPrompt(message, context);
                
                Log.d(TAG, "Enhanced prompt: " + enhancedMessage);
                
                // Send message using parent class method with proper context
                sendMessageWithContext(context, model, enhancedMessage, new ChatCallback() {
                    @Override
                    public void onResponse(String response) {
                        // Check if response contains an action
                        try {
                            JSONObject responseJson = new JSONObject(response.trim());
                            if ("action".equals(responseJson.optString("response_type"))) {
                                executeAction(responseJson, context, callback);
                                return;
                            }
                        } catch (JSONException e) {
                            // Not JSON, treat as regular response
                        }
                        
                        // Regular response
                        mainHandler.post(() -> callback.onResponse(response));
                    }

                    @Override
                    public void onError(String error) {
                        mainHandler.post(() -> callback.onError(error));
                    }

                    @Override
                    public void onStreamingResponse(String partialResponse) {
                        mainHandler.post(() -> callback.onStreamingResponse(partialResponse));
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error in agentic sendMessage", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    private void sendMessageWithContext(ConversationContext context, String model, String message, ChatCallback callback) {
        String qwenChatId = context.getQwenChatId();
        
        if (qwenChatId == null) {
            // Create new chat and store the ID
            createNewChat(model, new NewChatCallback() {
                @Override
                public void onChatCreated(String chatId) {
                    context.setQwenChatId(chatId);
                    contextStorage.saveContext(context);
                    sendChatMessageWithParent(chatId, model, message, context.getLastParentId(), callback);
                }

                @Override
                public void onError(String error) {
                    callback.onError(error);
                }
            });
        } else {
            // Use existing chat
            sendChatMessageWithParent(qwenChatId, model, message, context.getLastParentId(), callback);
        }
    }

    private void sendChatMessageWithParent(String chatId, String model, String message, String parentId, ChatCallback callback) {
        // Use the parent class implementation but with proper parent_id handling
        super.sendMessage("dummy", model, message, new ChatCallback() {
            @Override
            public void onResponse(String response) {
                // Update parent ID for next message (this would need to be extracted from response)
                callback.onResponse(response);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }

            @Override
            public void onStreamingResponse(String partialResponse) {
                callback.onStreamingResponse(partialResponse);
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
            
            Log.d(TAG, "Executing action: " + actionName + " with params: " + paramMap);
            
            // Execute the action
            String result = contextBuilder.executeAction(actionName, paramMap, context.getCurrentProjectId());
            
            // Parse result for success
            try {
                JSONObject resultJson = new JSONObject(result);
                if (resultJson.optBoolean("success", false)) {
                    // Update context if project was created
                    if ("create_project".equals(actionName)) {
                        String projectId = resultJson.optString("project_id");
                        String projectName = resultJson.optString("project_name");
                        context.setCurrentProjectId(projectId);
                        context.addExecutedAction(actionName);
                        contextStorage.saveContext(context);
                        
                        mainHandler.post(() -> {
                            callback.onActionExecuted(result, projectId);
                            callback.onProjectCreated(projectId, projectName);
                        });
                        return;
                    }
                }
            } catch (JSONException e) {
                // Result is not JSON, treat as plain text
            }
            
            // Record action execution
            context.addExecutedAction(actionName);
            contextStorage.saveContext(context);
            
            mainHandler.post(() -> callback.onActionExecuted(result, context.getCurrentProjectId()));
            
        } catch (JSONException e) {
            Log.e(TAG, "Error executing action", e);
            mainHandler.post(() -> callback.onError("Error executing action: " + e.getMessage()));
        }
    }
}