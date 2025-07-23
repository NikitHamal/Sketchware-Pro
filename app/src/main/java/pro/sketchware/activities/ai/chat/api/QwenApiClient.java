package pro.sketchware.activities.ai.chat.api;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.activities.ai.config.ApiConfig;

public class QwenApiClient {
    private static final String TAG = "QwenApiClient";
    private static final String BASE_URL = "https://chat.qwen.ai/api/v2";

    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Map<String, String> chatIdMap = new HashMap<>();
    private ApiConfig apiConfig;

    public interface ChatCallback {
        void onResponse(String response);
        void onError(String error);
        
        default void onStreamingResponse(String partialResponse) {
            // Default implementation does nothing - subclasses can override
        }
    }

    public interface NewChatCallback {
        void onChatCreated(String chatId);
        void onError(String error);
    }

    public QwenApiClient(Context context) {
        apiConfig = new ApiConfig(context);
    }

    public void sendMessage(String conversationId, String model, String message, ChatCallback callback) {
        executor.execute(() -> {
            try {
                String chatId = chatIdMap.get(conversationId);
                if (chatId == null) {
                    chatId = createNewChat(model);
                    if (chatId == null) {
                        mainHandler.post(() -> callback.onError("Failed to create new chat"));
                        return;
                    }
                    chatIdMap.put(conversationId, chatId);
                }

                String response = sendChatMessage(chatId, model, message);
                if (response != null) {
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

    private String createNewChat(String model) {
        try {
            URL url = new URL(BASE_URL + "/chats/new");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            
            // Set headers based on reverse-engineered API
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", apiConfig.getAuthorization());
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", apiConfig.getUserAgent());
            conn.setRequestProperty("bx-v", apiConfig.getBxV());
            conn.setRequestProperty("source", apiConfig.getSource());
            conn.setRequestProperty("timezone", apiConfig.getTimezone());
            conn.setRequestProperty("x-request-id", UUID.randomUUID().toString());
            
            // Add optional headers if they exist
            if (!apiConfig.getCookie().isEmpty()) {
                conn.setRequestProperty("Cookie", apiConfig.getCookie());
            }
            if (!apiConfig.getBxUa().isEmpty()) {
                conn.setRequestProperty("bx-ua", apiConfig.getBxUa());
            }
            if (!apiConfig.getBxUmidtoken().isEmpty()) {
                conn.setRequestProperty("bx-umidtoken", apiConfig.getBxUmidtoken());
            }
            conn.setDoOutput(true);

            // Create request body
            JSONObject requestBody = new JSONObject();
            requestBody.put("title", "New Chat");
            JSONArray models = new JSONArray();
            models.put(model);
            requestBody.put("models", models);
            requestBody.put("chat_mode", "normal");
            requestBody.put("chat_type", "t2t");
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

    private String sendChatMessage(String chatId, String model, String message) {
        try {
            URL url = new URL(BASE_URL + "/chat/completions?chat_id=" + chatId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            
            // Set headers
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("Authorization", apiConfig.getAuthorization());
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", apiConfig.getUserAgent());
            conn.setRequestProperty("bx-v", apiConfig.getBxV());
            conn.setRequestProperty("source", apiConfig.getSource());
            conn.setRequestProperty("timezone", apiConfig.getTimezone());
            conn.setRequestProperty("x-accel-buffering", "no");
            conn.setRequestProperty("x-request-id", UUID.randomUUID().toString());
            
            // Add optional headers if they exist
            if (!apiConfig.getCookie().isEmpty()) {
                conn.setRequestProperty("Cookie", apiConfig.getCookie());
            }
            if (!apiConfig.getBxUa().isEmpty()) {
                conn.setRequestProperty("bx-ua", apiConfig.getBxUa());
            }
            if (!apiConfig.getBxUmidtoken().isEmpty()) {
                conn.setRequestProperty("bx-umidtoken", apiConfig.getBxUmidtoken());
            }
            conn.setDoOutput(true);

            // Create message object
            JSONObject messageObj = new JSONObject();
            messageObj.put("fid", UUID.randomUUID().toString());
            messageObj.put("parentId", JSONObject.NULL);
            messageObj.put("childrenIds", new JSONArray());
            messageObj.put("role", "user");
            messageObj.put("content", message);
            messageObj.put("user_action", "chat");
            messageObj.put("files", new JSONArray());
            messageObj.put("timestamp", System.currentTimeMillis() / 1000);
            JSONArray models = new JSONArray();
            models.put(model);
            messageObj.put("models", models);
            messageObj.put("chat_type", "t2t");
            
            JSONObject featureConfig = new JSONObject();
            featureConfig.put("thinking_enabled", false);
            featureConfig.put("output_schema", "phase");
            messageObj.put("feature_config", featureConfig);
            
            JSONObject extra = new JSONObject();
            JSONObject meta = new JSONObject();
            meta.put("subChatType", "t2t");
            extra.put("meta", meta);
            messageObj.put("extra", extra);
            messageObj.put("sub_chat_type", "t2t");
            messageObj.put("parent_id", JSONObject.NULL);

            // Create request body
            JSONObject requestBody = new JSONObject();
            requestBody.put("stream", true);
            requestBody.put("incremental_output", true);
            requestBody.put("chat_id", chatId);
            requestBody.put("chat_mode", "normal");
            requestBody.put("model", model);
            requestBody.put("parent_id", JSONObject.NULL);
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
                return readStreamingResponse(conn.getInputStream());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending chat message", e);
        }
        return null;
    }

    private String readStreamingResponse(InputStream inputStream) throws IOException {
        StringBuilder fullResponse = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("data: ")) {
                String data = line.substring(6);
                if (!data.trim().isEmpty()) {
                    try {
                        JSONObject jsonData = new JSONObject(data);
                        if (jsonData.has("choices")) {
                            JSONArray choices = jsonData.getJSONArray("choices");
                            if (choices.length() > 0) {
                                JSONObject choice = choices.getJSONObject(0);
                                if (choice.has("delta")) {
                                    JSONObject delta = choice.getJSONObject("delta");
                                    if (delta.has("content")) {
                                        String content = delta.getString("content");
                                        if (!content.isEmpty()) {
                                            fullResponse.append(content);
                                        }
                                        
                                        // Check if finished
                                        if (delta.has("status") && "finished".equals(delta.getString("status"))) {
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
        
        return fullResponse.toString().trim();
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
}