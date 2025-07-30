package com.stormx.agent.api;

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

import com.stormx.agent.config.ApiConfig;

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

            // Create request body
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", model);
            requestBody.put("name", "New Chat");

            String jsonInputString = requestBody.toString();
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (InputStream inputStream = conn.getInputStream()) {
                    String response = readResponse(inputStream);
                    JSONObject jsonResponse = new JSONObject(response);
                    return jsonResponse.getString("chat_id");
                }
            } else {
                Log.e(TAG, "Failed to create chat. Response code: " + responseCode);
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating new chat", e);
            return null;
        }
    }

    private String sendChatMessage(String chatId, String model, String message) {
        try {
            URL url = new URL(BASE_URL + "/chats/" + chatId + "/messages");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            
            // Set headers
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", apiConfig.getAuthorization());
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", apiConfig.getUserAgent());
            conn.setRequestProperty("bx-v", apiConfig.getBxV());
            conn.setRequestProperty("source", apiConfig.getSource());
            conn.setRequestProperty("timezone", apiConfig.getTimezone());
            conn.setRequestProperty("x-request-id", UUID.randomUUID().toString());
            
            if (!apiConfig.getCookie().isEmpty()) {
                conn.setRequestProperty("Cookie", apiConfig.getCookie());
            }

            // Create request body
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", model);
            requestBody.put("messages", new JSONArray().put(new JSONObject()
                    .put("role", "user")
                    .put("content", message)));
            requestBody.put("stream", false);

            String jsonInputString = requestBody.toString();
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (InputStream inputStream = conn.getInputStream()) {
                    String response = readResponse(inputStream);
                    JSONObject jsonResponse = new JSONObject(response);
                    JSONArray choices = jsonResponse.getJSONArray("choices");
                    if (choices.length() > 0) {
                        JSONObject choice = choices.getJSONObject(0);
                        JSONObject messageObj = choice.getJSONObject("message");
                        return messageObj.getString("content");
                    }
                }
            } else {
                Log.e(TAG, "Failed to send message. Response code: " + responseCode);
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending chat message", e);
            return null;
        }
        return null;
    }

    private String readResponse(InputStream inputStream) throws IOException {
        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        }
        return response.toString();
    }
}