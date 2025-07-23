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

import java.util.ArrayList;

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
    
    /**
     * Splits a string containing multiple JSON objects into individual JSON strings.
     * This method manually parses the string to find complete JSON objects without
     * using regex lookbehind/lookahead which are not supported on Android.
     */
    private String[] splitJsonObjects(String input) {
        java.util.List<String> jsonObjects = new java.util.ArrayList<>();
        int braceCount = 0;
        int start = 0;
        boolean inString = false;
        boolean escaped = false;
        
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            
            if (escaped) {
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                continue;
            }
            
            if (!inString) {
                if (c == '{') {
                    if (braceCount == 0) {
                        start = i; // Start of new JSON object
                    }
                    braceCount++;
                } else if (c == '}') {
                    braceCount--;
                    if (braceCount == 0) {
                        // Complete JSON object found
                        String jsonObject = input.substring(start, i + 1).trim();
                        if (!jsonObject.isEmpty()) {
                            jsonObjects.add(jsonObject);
                        }
                    }
                }
            }
        }
        
        // If no complete JSON objects were found, return the original input
        if (jsonObjects.isEmpty()) {
            return new String[]{input};
        }
        
        return jsonObjects.toArray(new String[0]);
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
                                     thinkingEnabled, webSearchEnabled, new AgenticChatCallback() {
                    @Override
                    public void onResponse(String response) {
                        // Check if response contains action(s)
                        String trimmedResponse = response.trim();
                        
                        // Handle multiple JSON objects in response
                        if (trimmedResponse.startsWith("{") && trimmedResponse.contains("\"response_type\"")) {
                            // Split multiple JSON objects if they exist using a simpler approach
                            String[] jsonParts = splitJsonObjects(trimmedResponse);
                            
                            // Group file-related actions together
                            List<JSONObject> fileActions = new ArrayList<>();
                            List<JSONObject> otherActions = new ArrayList<>();
                            String overallExplanation = "";
                            
                            for (String jsonPart : jsonParts) {
                                try {
                                    JSONObject responseJson = new JSONObject(jsonPart.trim());
                                    String responseType = responseJson.optString("response_type");
                                    
                                    if ("action".equals(responseType)) {
                                        String actionName = responseJson.optString("action");
                                        if (overallExplanation.isEmpty()) {
                                            overallExplanation = responseJson.optString("explanation", "I'm working on that for you...");
                                        }
                                        
                                        if (isFileRelatedAction(actionName)) {
                                            fileActions.add(responseJson);
                                        } else {
                                            otherActions.add(responseJson);
                                        }
                                    }
                                } catch (JSONException e) {
                                    // JSON parsing failed, treat as regular response
                                }
                            }
                            
                            // Process grouped file actions as a single proposal
                            if (!fileActions.isEmpty()) {
                                mainHandler.post(() -> callback.onResponse(overallExplanation));
                                executeGroupedFileActions(fileActions, context, callback);
                                
                                // Process other actions normally
                                for (JSONObject action : otherActions) {
                                    executeAction(action, context, callback);
                                }
                                return; // Don't send as regular response
                            }
                            
                            // If no file actions, process normally
                            boolean actionProcessed = false;
                            for (JSONObject action : otherActions) {
                                if (!actionProcessed) {
                                    mainHandler.post(() -> callback.onResponse(overallExplanation));
                                    actionProcessed = true;
                                }
                                executeAction(action, context, callback);
                            }
                            
                            if (actionProcessed) {
                                return; // Don't send as regular response
                            }
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

                    @Override
                    public void onActionExecuted(String actionResult, String projectId) {
                        mainHandler.post(() -> callback.onActionExecuted(actionResult, projectId));
                    }

                    @Override
                    public void onProjectCreated(String projectId, String projectName) {
                        mainHandler.post(() -> callback.onProjectCreated(projectId, projectName));
                    }

                    @Override
                    public void onFixProposal(String explanation, String actionJson, String projectId) {
                        mainHandler.post(() -> callback.onFixProposal(explanation, actionJson, projectId));
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error in agentic sendMessage", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    private void sendMessageWithContext(ConversationContext context, String model, String message, AgenticChatCallback callback) {
        sendMessageWithContext(context, model, message, null, false, false, callback);
    }
    
    private void sendMessageWithContext(ConversationContext context, String model, String message, 
                                      List<ChatMessage.AttachedFile> attachedFiles, 
                                      boolean thinkingEnabled, boolean webSearchEnabled, 
                                      AgenticChatCallback callback) {
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
                                    ConversationContext context, AgenticChatCallback callback) {
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
            if (thinkingEnabled) {
                featureConfig.put("thinking_budget", 38912); // Based on reference API
            }
            if (webSearchEnabled) {
                featureConfig.put("search_version", "v2"); // Based on reference API
            }
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
        StringBuilder debugInfo = new StringBuilder();
        List<WebSearchResult> webSearchResults = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        boolean hasAnswerPhase = false;
        
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("data: ")) {
                String data = line.substring(6);
                if (!data.trim().isEmpty()) {
                    Log.d(TAG, "Processing streaming data: " + data);
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
                                    
                                    // Handle different phases (web_search, think, answer, etc.)
                                    String phase = delta.optString("phase", "answer");
                                    String role = delta.optString("role", "assistant");
                                    
                                    // Collect debug info
                                    debugInfo.append("[").append(phase).append(":").append(role).append("] ");
                                    
                                    // Handle thinking content (think phase)
                                    if ("think".equals(phase) && "assistant".equals(role)) {
                                        if (delta.has("content")) {
                                            String content = delta.getString("content");
                                            if (!content.isEmpty()) {
                                                thinkingContent.append(content);
                                            }
                                        }
                                        // Check if thinking phase is finished
                                        if (delta.has("status") && "finished".equals(delta.getString("status"))) {
                                            Log.d(TAG, "Thinking phase finished, content length: " + thinkingContent.length());
                                        }
                                    }
                                    
                                    // Handle web search results - based on reference API format
                                    if ("web_search".equals(phase)) {
                                        if ("assistant".equals(role) && delta.has("function_call")) {
                                            // Web search function call initiation
                                            Log.d(TAG, "Web search function call detected");
                                        } else if ("function".equals(role) && delta.has("extra")) {
                                            JSONObject extra = delta.getJSONObject("extra");
                                            if (extra.has("web_search_info")) {
                                                JSONArray searchInfo = extra.getJSONArray("web_search_info");
                                                Log.d(TAG, "Found web search results: " + searchInfo.length());
                                                for (int i = 0; i < searchInfo.length(); i++) {
                                                    JSONObject searchResult = searchInfo.getJSONObject(i);
                                                    WebSearchResult result = new WebSearchResult(
                                                        searchResult.optString("url", ""),
                                                        searchResult.optString("title", ""),
                                                        searchResult.optString("snippet", "")
                                                    );
                                                    webSearchResults.add(result);
                                                }
                                            }
                                        }
                                        // Check if web search phase is finished
                                        if (delta.has("status") && "finished".equals(delta.getString("status"))) {
                                            Log.d(TAG, "Web search phase finished, results count: " + webSearchResults.size());
                                        }
                                    }
                                    
                                    // Handle answer phase content
                                    if ("answer".equals(phase) && "assistant".equals(role) && delta.has("content")) {
                                        String content = delta.getString("content");
                                        if (!content.isEmpty()) {
                                            fullResponse.append(content);
                                            hasAnswerPhase = true;
                                        }
                                    }
                                        
                                                                            // Check if finished - handle both answer and overall finish
                                        String status = delta.optString("status", "");
                                        if ("finished".equals(status)) {
                                            String currentPhase = delta.optString("phase", "");
                                            Log.d(TAG, "Phase finished: " + currentPhase + " with status: " + status);
                                            
                                            // If answer phase is finished, we're done
                                            if ("answer".equals(currentPhase)) {
                                                break;
                                            }
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
        
        String responseText = fullResponse.toString().trim();
        
        // Check if the response is an action - if so, return it directly without wrapping
        try {
            JSONObject possibleAction = new JSONObject(responseText);
            if (possibleAction.has("response_type") && "action".equals(possibleAction.getString("response_type"))) {
                // Return the action as-is so the upper level can detect and process it
                return responseText;
            }
        } catch (JSONException e) {
            // Not a JSON action, continue with normal response processing
        }
        
        // Enhanced response processing logic
        if (!hasAnswerPhase) {
            if (thinkingContent.length() == 0 && webSearchResults.isEmpty()) {
                String debugMessage = "🔍 No response received from the AI. This might be a temporary issue.";
                Log.w(TAG, "No content received. Debug info: " + debugInfo.toString());
                return debugMessage;
            } else if (thinkingContent.length() > 0) {
                Log.w(TAG, "Only thinking phase received, no answer phase. Debug info: " + debugInfo.toString());
                // In case of thinking-only response, we should still wait for answer phase
                // But if stream ended, return what we have
                responseText = "🤔 AI is processing your request...";
            }
        }
        
        // Ensure we have some response content
        if (responseText.trim().isEmpty() && thinkingContent.length() == 0 && webSearchResults.isEmpty()) {
            String debugMessage = "🔍 No response content received. Debug info: " + debugInfo.toString();
            Log.w(TAG, debugMessage);
            return debugMessage;
        }
        
        // Build response with structured data
        try {
            JSONObject responseObj = new JSONObject();
            responseObj.put("content", responseText);
            
            if (thinkingContent.length() > 0) {
                responseObj.put("thinking_content", thinkingContent.toString().trim());
            }
            
            if (!webSearchResults.isEmpty()) {
                JSONArray sourcesArray = new JSONArray();
                for (WebSearchResult result : webSearchResults) {
                    JSONObject source = new JSONObject();
                    source.put("url", result.getUrl());
                    source.put("title", result.getTitle());
                    source.put("snippet", result.getSnippet());
                    sourcesArray.put(source);
                }
                responseObj.put("web_search_sources", sourcesArray);
            }
            
            return responseObj.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error creating response object", e);
            return responseText;
        }
    }

    // Helper class for web search results
    private static class WebSearchResult {
        private final String url;
        private final String title;
        private final String snippet;
        
        public WebSearchResult(String url, String title, String snippet) {
            this.url = url;
            this.title = title;
            this.snippet = snippet;
        }
        
        public String getUrl() { return url; }
        public String getTitle() { return title; }
        public String getSnippet() { return snippet; }
    }

    /**
     * Check if an action is file-related and requires user approval
     */
    private boolean isFileRelatedAction(String actionName) {
        return actionName.equals("create_java_file") ||
               actionName.equals("create_xml_resource") ||
               actionName.equals("edit_file") ||
               actionName.equals("delete_file") ||
               actionName.equals("fix_file_error") ||
               actionName.equals("create_file");
    }

    /**
     * Execute grouped file actions as a single proposal
     */
    private void executeGroupedFileActions(List<JSONObject> fileActions, ConversationContext context, AgenticChatCallback callback) {
        Log.d(TAG, "=== CREATING GROUPED FILE OPERATIONS PROPOSAL ===");
        Log.d(TAG, "Number of file actions: " + fileActions.size());
        
        try {
            // Create a combined proposal with all file actions
            JSONObject groupedProposal = new JSONObject();
            JSONArray actionsArray = new JSONArray();
            
            StringBuilder combinedExplanation = new StringBuilder();
            
            for (int i = 0; i < fileActions.size(); i++) {
                JSONObject action = fileActions.get(i);
                String actionName = action.getString("action");
                String explanation = action.optString("explanation", "");
                JSONObject parameters = action.optJSONObject("parameters");
                
                // Add to actions array
                JSONObject actionEntry = new JSONObject();
                actionEntry.put("action", actionName);
                actionEntry.put("parameters", parameters);
                actionsArray.put(actionEntry);
                
                // Build combined explanation
                if (i > 0) combinedExplanation.append("\n");
                combinedExplanation.append("• ").append(explanation);
                
                Log.d(TAG, "Added action to group: " + actionName);
            }
            
            groupedProposal.put("grouped_actions", actionsArray);
            groupedProposal.put("action_count", fileActions.size());
            
            String finalExplanation = combinedExplanation.toString();
            if (finalExplanation.isEmpty()) {
                finalExplanation = "I'll make the following changes to your project:";
            }
            
            Log.d(TAG, "Grouped proposal created: " + groupedProposal.toString());
            Log.d(TAG, "Combined explanation: " + finalExplanation);
            
            mainHandler.post(() -> {
                callback.onFixProposal(finalExplanation, groupedProposal.toString(), context.getCurrentProjectId());
            });
            
        } catch (JSONException e) {
            Log.e(TAG, "Error creating grouped file operations proposal", e);
            // Fallback to individual proposals
            for (JSONObject action : fileActions) {
                executeAction(action, context, callback);
            }
        }
    }

    private void executeAction(JSONObject actionJson, ConversationContext context, AgenticChatCallback callback) {
        Log.d(TAG, "=== EXECUTE ACTION CALLED ===");
        Log.d(TAG, "Action JSON: " + actionJson.toString());
        Log.d(TAG, "Callback type: " + callback.getClass().getSimpleName());
        
        try {
            String actionName = actionJson.getString("action");
            String explanation = actionJson.optString("explanation", "Executing action...");
            JSONObject parameters = actionJson.optJSONObject("parameters");
            
            Log.d(TAG, "Action name: " + actionName);
            Log.d(TAG, "Explanation: " + explanation);
            Log.d(TAG, "Parameters: " + (parameters != null ? parameters.toString() : "null"));
            
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
            
            // Check if this is a file-related action that requires user approval
            if (isFileRelatedAction(actionName)) {
                Log.d(TAG, "=== CREATING FILE OPERATION PROPOSAL ===");
                
                // Create proposal JSON for user approval
                JSONObject proposalJson = new JSONObject();
                try {
                    proposalJson.put("action", actionName);
                    proposalJson.put("parameters", parameters);
                    
                    Log.d(TAG, "Proposal JSON created: " + proposalJson.toString());
                    Log.d(TAG, "Calling onFixProposal with explanation: " + explanation);
                    
                    mainHandler.post(() -> {
                        callback.onFixProposal(explanation, proposalJson.toString(), context.getCurrentProjectId());
                    });
                } catch (JSONException e) {
                    Log.e(TAG, "Error creating file operation proposal", e);
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
                        
                        // For project creation, only call onProjectCreated, not onActionExecuted
                        mainHandler.post(() -> callback.onProjectCreated(projectId, projectName));
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
                
                // Check if this is a grouped action
                if (actionData.has("grouped_actions")) {
                    JSONArray groupedActions = actionData.getJSONArray("grouped_actions");
                    List<String> results = new ArrayList<>();
                    
                    Log.d(TAG, "Executing grouped actions, count: " + groupedActions.length());
                    
                    for (int i = 0; i < groupedActions.length(); i++) {
                        JSONObject action = groupedActions.getJSONObject(i);
                        String actionName = action.getString("action");
                        JSONObject parameters = action.getJSONObject("parameters");
                        
                        Log.d(TAG, "Executing grouped action " + (i+1) + ": " + actionName);
                        
                        Map<String, Object> paramMap = new HashMap<>();
                        parameters.keys().forEachRemaining(key -> {
                            try {
                                paramMap.put(key, parameters.get(key));
                            } catch (JSONException e) {
                                Log.e(TAG, "Error parsing parameter: " + key, e);
                            }
                        });
                        
                        String result = contextBuilder.executeAction(actionName, paramMap, context.getCurrentProjectId());
                        results.add(result);
                        
                        // Update context for each action
                        context.addExecutedAction(actionName);
                    }
                    
                    contextStorage.saveContext(context);
                    
                    // Combine results into a single response
                    JSONObject combinedResult = new JSONObject();
                    combinedResult.put("success", true);
                    combinedResult.put("action", "grouped_file_operations");
                    combinedResult.put("action_count", results.size());
                    
                    JSONArray resultsArray = new JSONArray();
                    for (String result : results) {
                        try {
                            JSONObject resultObj = new JSONObject(result);
                            resultsArray.put(resultObj);
                        } catch (JSONException e) {
                            // If result is not JSON, wrap it
                            JSONObject wrapper = new JSONObject();
                            wrapper.put("result", result);
                            resultsArray.put(wrapper);
                        }
                    }
                    combinedResult.put("results", resultsArray);
                    
                    mainHandler.post(() -> callback.onActionExecuted(combinedResult.toString(), context.getCurrentProjectId()));
                    
                } else {
                    // Single action execution (existing logic)
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
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error executing approved action", e);
                mainHandler.post(() -> callback.onError("Action execution failed: " + e.getMessage()));
            }
        });
    }
}