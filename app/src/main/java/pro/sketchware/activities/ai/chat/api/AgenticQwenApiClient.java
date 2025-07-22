package pro.sketchware.activities.ai.chat.api;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.json.JSONArray;

import pro.sketchware.activities.ai.chat.context.ContextBuilder;
import pro.sketchware.activities.ai.chat.models.ChatMessage;
import pro.sketchware.activities.ai.chat.models.ConversationContext;
import pro.sketchware.activities.ai.storage.ConversationContextStorage;
import pro.sketchware.activities.ai.config.ApiConfig;

public class AgenticQwenApiClient extends QwenApiClient {
    private static final String TAG = "AgenticQwenApiClient";
    
    private final ContextBuilder contextBuilder;
    private final ConversationContextStorage contextStorage;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, String> agenticChatIdMap = new HashMap<>();

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

    private String createNewChatSync(String model, String chatType) {
        try {
            URL url = new URL("https://chat.qwen.ai/api/v2/chats/new");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            
            // Set headers based on reverse-engineered API
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + getAuthToken());
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12; itel A662LM) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Mobile Safari/537.36");
            conn.setRequestProperty("bx-v", "2.5.31");
            conn.setRequestProperty("source", "h5");
            conn.setRequestProperty("timezone", "Fri Jul 18 2025 13:32:16 GMT+0545");
            conn.setRequestProperty("x-request-id", UUID.randomUUID().toString());
            conn.setDoOutput(true);

            // Create request body
            JSONObject requestBody = new JSONObject();
            requestBody.put("title", "New Chat");
            JSONArray models = new JSONArray();
            models.put(model);
            requestBody.put("models", models);
            requestBody.put("chat_mode", "normal");
            requestBody.put("chat_type", chatType);
            requestBody.put("timestamp", System.currentTimeMillis());

            // Send request
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Read response
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                String responseStr = readResponse(conn.getInputStream());
                JSONObject response = new JSONObject(responseStr);
                if (response.getBoolean("success")) {
                    return response.getJSONObject("data").getString("id");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating new chat", e);
        }
        return null;
    }

    private String getAuthToken() {
        // This should return the actual auth token - for now using placeholder
        return "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6IjhiYjQ1NjVmLTk3NjUtNDQwNi04OWQ5LTI3NmExMTIxMjBkNiIsImxhc3RfcGFzc3dvcmRfY2hhbmdlIjoxNzUwNjYwODczLCJleHAiOjE3NTU0MTY4MjJ9.jmyaxu5mrr1M1rvtRtpGi2DKyp6RM8xRZ1nEx-rHRgQ";
    }

    private String readResponse(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        return response.toString();
    }

    public void sendMessage(String conversationId, String model, String message, AgenticChatCallback callback) {
        sendMessage(conversationId, model, message, null, false, false, callback);
    }
    
    public void sendMessage(String conversationId, String model, String message, 
                          List<ChatMessage.AttachedFile> attachedFiles, 
                          boolean thinkingEnabled, boolean webSearchEnabled, 
                          AgenticChatCallback callback) {
        executor.execute(() -> {
            try {
                // Load or create conversation context
                ConversationContext context = contextStorage.loadContext(conversationId);
                
                // Build enhanced prompt with context
                String enhancedMessage = contextBuilder.buildEnhancedPrompt(message, context);
                
                Log.d(TAG, "Enhanced prompt: " + enhancedMessage);
                
                // Send message using enhanced method with files and features
                sendMessageWithContext(context, model, enhancedMessage, attachedFiles, 
                                     thinkingEnabled, webSearchEnabled, new ChatCallback() {
                    @Override
                    public void onResponse(String response) {
                        // Check if response contains an action
                        try {
                            JSONObject responseJson = new JSONObject(response.trim());
                            if ("action".equals(responseJson.optString("response_type"))) {
                                // First show the explanation to the user
                                String explanation = responseJson.optString("explanation", "I'm working on that for you...");
                                mainHandler.post(() -> callback.onResponse(explanation));
                                
                                // Then execute the action
                                executeAction(responseJson, context, callback);
                                return;
                            }
                        } catch (JSONException e) {
                            // Not JSON, treat as regular response
                        }
                        
                        // Regular response - only call this if no action was executed
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
        sendMessageWithContext(context, model, message, null, false, false, callback);
    }
    
    private void sendMessageWithContext(ConversationContext context, String model, String message, 
                                      List<ChatMessage.AttachedFile> attachedFiles, 
                                      boolean thinkingEnabled, boolean webSearchEnabled, 
                                      ChatCallback callback) {
        String qwenChatId = context.getQwenChatId();
        
        if (qwenChatId == null) {
            // Create new chat synchronously using our method
            executor.execute(() -> {
                try {
                    String chatType = webSearchEnabled ? "search" : "t2t";
                    String chatId = createNewChatSync(model, chatType);
                    if (chatId != null) {
                        context.setQwenChatId(chatId);
                        contextStorage.saveContext(context);
                        // Store the chatId in our map for direct use
                        agenticChatIdMap.put(context.getConversationId(), chatId);
                        // Send the message using our own implementation
                        sendMessageDirectly(chatId, model, message, attachedFiles, 
                                           thinkingEnabled, webSearchEnabled, context, callback);
                    } else {
                        mainHandler.post(() -> callback.onError("Failed to create new chat"));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error creating new chat", e);
                    mainHandler.post(() -> callback.onError("Error creating new chat: " + e.getMessage()));
                }
            });
        } else {
            // Use existing chat
            sendMessageDirectly(qwenChatId, model, message, attachedFiles, 
                               thinkingEnabled, webSearchEnabled, context, callback);
        }
    }

    private void sendMessageDirectly(String chatId, String model, String message, 
                                    List<ChatMessage.AttachedFile> attachedFiles, 
                                    boolean thinkingEnabled, boolean webSearchEnabled, 
                                    ConversationContext context, ChatCallback callback) {
        executor.execute(() -> {
            try {
                String response = sendChatMessageSync(chatId, model, message, attachedFiles, 
                                                     thinkingEnabled, webSearchEnabled, context);
                if (response != null) {
                    // Save context with updated parent_id
                    contextStorage.saveContext(context);
                    mainHandler.post(() -> callback.onResponse(response));
                } else {
                    mainHandler.post(() -> callback.onError("Failed to get response"));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending message", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    private String sendChatMessageSync(String chatId, String model, String message, 
                                      List<ChatMessage.AttachedFile> attachedFiles, 
                                      boolean thinkingEnabled, boolean webSearchEnabled, 
                                      ConversationContext context) {
        try {
            URL url = new URL("https://chat.qwen.ai/api/v2/chat/completions?chat_id=" + chatId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // Set headers
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("Authorization", "Bearer " + getAuthToken());
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12; itel A662LM) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Mobile Safari/537.36");
            conn.setRequestProperty("bx-v", "2.5.31");
            conn.setRequestProperty("source", "h5");
            conn.setRequestProperty("timezone", "Fri Jul 18 2025 13:32:16 GMT+0545");
            conn.setRequestProperty("x-accel-buffering", "no");
            conn.setRequestProperty("x-request-id", UUID.randomUUID().toString());
            conn.setDoOutput(true);

            // Generate unique message ID
            String userMessageId = UUID.randomUUID().toString();
            
            // Create message object with proper parent chain
            JSONObject messageObj = new JSONObject();
            messageObj.put("fid", userMessageId);
            
            // Set proper parent ID - link to last AI response
            Object parentId = context.getLastParentId() != null ? context.getLastParentId() : JSONObject.NULL;
            messageObj.put("parentId", parentId);
            messageObj.put("childrenIds", new JSONArray());
            messageObj.put("role", "user");
            messageObj.put("content", message);
            messageObj.put("user_action", "chat");
            
            // Add files array
            JSONArray filesArray = new JSONArray();
            if (attachedFiles != null) {
                for (ChatMessage.AttachedFile file : attachedFiles) {
                    JSONObject fileObj = new JSONObject();
                    fileObj.put("type", "file");
                    
                    JSONObject fileData = new JSONObject();
                    fileData.put("id", file.getId());
                    fileData.put("filename", file.getName());
                    fileData.put("url", file.getUrl());
                    
                    JSONObject meta = new JSONObject();
                    meta.put("name", file.getName());
                    meta.put("size", file.getSize());
                    meta.put("content_type", file.getMimeType());
                    fileData.put("meta", meta);
                    
                    fileObj.put("file", fileData);
                    fileObj.put("id", file.getId());
                    fileObj.put("url", file.getUrl());
                    fileObj.put("name", file.getName());
                    fileObj.put("size", file.getSize());
                    fileObj.put("file_type", file.getMimeType());
                    fileObj.put("showType", "file");
                    fileObj.put("file_class", "document");
                    
                    filesArray.put(fileObj);
                }
            }
            messageObj.put("files", filesArray);
            
            messageObj.put("timestamp", System.currentTimeMillis() / 1000);
            JSONArray models = new JSONArray();
            models.put(model);
            messageObj.put("models", models);
            
            // Set chat type based on web search
            String chatType = webSearchEnabled ? "search" : "t2t";
            messageObj.put("chat_type", chatType);
            
            JSONObject featureConfig = new JSONObject();
            featureConfig.put("thinking_enabled", thinkingEnabled);
            featureConfig.put("output_schema", "phase");
            messageObj.put("feature_config", featureConfig);
            
            JSONObject extra = new JSONObject();
            JSONObject meta = new JSONObject();
            meta.put("subChatType", chatType);
            extra.put("meta", meta);
            messageObj.put("extra", extra);
            messageObj.put("sub_chat_type", chatType);
            messageObj.put("parent_id", parentId);

            // Store this message in context for future reference
            context.addToQwenMessageHistory(new JSONObject(messageObj.toString()));
            context.setLastUserMessageId(userMessageId);

            // Create request body
            JSONObject requestBody = new JSONObject();
            requestBody.put("stream", true);
            requestBody.put("incremental_output", true);
            requestBody.put("chat_id", chatId);
            requestBody.put("chat_mode", "normal");
            requestBody.put("model", model);
            requestBody.put("parent_id", parentId);  // Set proper parent_id for request
            JSONArray messages = new JSONArray();
            messages.put(messageObj);
            requestBody.put("messages", messages);
            requestBody.put("timestamp", System.currentTimeMillis() / 1000);

            // Send request
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Read streaming response
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return readStreamingResponseInternal(conn.getInputStream(), context);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending chat message", e);
        }
        return null;
    }

    private String readStreamingResponseInternal(InputStream inputStream, ConversationContext context) throws IOException {
        StringBuilder fullResponse = new StringBuilder();
        StringBuilder thinkingContent = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("data: ")) {
                String data = line.substring(6);
                if (!data.trim().isEmpty()) {
                    try {
                        JSONObject jsonData = new JSONObject(data);
                        
                        // Extract parent_id from response.created
                        if (jsonData.has("response.created")) {
                            JSONObject responseCreated = jsonData.getJSONObject("response.created");
                            if (responseCreated.has("response_id")) {
                                String responseId = responseCreated.getString("response_id");
                                // Store this as the parent_id for the next message
                                context.setLastParentId(responseId);
                                Log.d(TAG, "Extracted AI response ID: " + responseId);
                            }
                        }
                        
                        if (jsonData.has("choices")) {
                            JSONArray choices = jsonData.getJSONArray("choices");
                            if (choices.length() > 0) {
                                JSONObject choice = choices.getJSONObject(0);
                                if (choice.has("delta")) {
                                    JSONObject delta = choice.getJSONObject("delta");
                                    
                                    // Handle different phases (web_search, answer, etc.)
                                    String phase = delta.optString("phase", "answer");
                                    String role = delta.optString("role", "assistant");
                                    
                                    // Handle thinking content
                                    if ("thinking".equals(phase) && delta.has("content")) {
                                        String content = delta.getString("content");
                                        if (!content.isEmpty()) {
                                            thinkingContent.append(content);
                                        }
                                    }
                                    
                                    // Only append content from answer phase with assistant role
                                    if ("answer".equals(phase) && "assistant".equals(role) && delta.has("content")) {
                                        String content = delta.getString("content");
                                        if (!content.isEmpty()) {
                                            fullResponse.append(content);
                                        }
                                    }
                                    
                                    // Handle web search phase display
                                    if ("web_search".equals(phase) && "function".equals(role)) {
                                        // You could show web search status here if needed
                                        Log.d(TAG, "Web search in progress...");
                                    }
                                        
                                    // Check if finished
                                    if (delta.has("status") && "finished".equals(delta.getString("status"))) {
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (JSONException e) {
                        Log.w(TAG, "Error parsing streaming data: " + data, e);
                    }
                }
            }
        }
        
        // Create response object that includes both content and thinking
        try {
            JSONObject responseObj = new JSONObject();
            responseObj.put("content", fullResponse.toString().trim());
            if (thinkingContent.length() > 0) {
                responseObj.put("thinking_content", thinkingContent.toString().trim());
            }
            return responseObj.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error creating response object", e);
            return fullResponse.toString().trim();
        }
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
            
            // Check if this is a fix_file_error action - should show proposal first
            if ("fix_file_error".equals(actionName)) {
                // Create proposal JSON for user approval
                JSONObject proposalJson = new JSONObject();
                try {
                    proposalJson.put("action", actionName);
                    proposalJson.put("parameters", parameters);
                    
                    mainHandler.post(() -> callback.onFixProposal(explanation, proposalJson.toString(), context.getCurrentProjectId()));
                } catch (JSONException e) {
                    Log.e(TAG, "Error creating fix proposal", e);
                }
                return;
            }
            
            // Execute the action directly for non-fix actions
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
    
    public void executeApprovedAction(String conversationId, JSONObject actionData, AgenticChatCallback callback) {
        executor.execute(() -> {
            try {
                Log.d(TAG, "executeApprovedAction called with: " + actionData.toString());
                ConversationContext context = contextStorage.loadContext(conversationId);
                
                String actionName = actionData.getString("action");
                JSONObject parameters = actionData.getJSONObject("parameters");
                
                Log.d(TAG, "Action name: " + actionName);
                Log.d(TAG, "Parameters: " + parameters.toString());
                
                Map<String, Object> paramMap = new HashMap<>();
                parameters.keys().forEachRemaining(key -> {
                    try {
                        paramMap.put(key, parameters.get(key));
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing parameter: " + key, e);
                    }
                });
                
                // Execute the approved action
                Log.d(TAG, "About to execute action: " + actionName + " with paramMap: " + paramMap);
                String result = contextBuilder.executeAction(actionName, paramMap, context.getCurrentProjectId());
                Log.d(TAG, "Action execution result: " + result);
                
                // Update context
                context.addExecutedAction(actionName);
                contextStorage.saveContext(context);
                
                mainHandler.post(() -> callback.onActionExecuted(result, context.getCurrentProjectId()));
                
            } catch (Exception e) {
                Log.e(TAG, "Error executing approved action", e);
                mainHandler.post(() -> callback.onError("Action execution failed: " + e.getMessage()));
            }
        });
    }
}