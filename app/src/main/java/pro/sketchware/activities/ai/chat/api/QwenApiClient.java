package pro.sketchware.activities.ai.chat.api;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.activities.ai.chat.api.retrofit.QwenApiService;
import pro.sketchware.activities.ai.chat.models.QwenRequest;
import pro.sketchware.activities.ai.chat.models.QwenResponse;
import pro.sketchware.activities.ai.config.ApiConfig;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class QwenApiClient {
    private static final String TAG = "QwenApiClient";
    private static final String BASE_URL = "https://chat.qwen.ai/api/v2/";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, String> chatIdMap = new HashMap<>();
    private final ApiConfig apiConfig;
    private final QwenApiService apiService;

    public interface ChatCallback {
        void onResponse(String response);
        void onError(String error);
        default void onStreamingResponse(String partialResponse) {}
    }

    public QwenApiClient(Context context) {
        apiConfig = new ApiConfig(context);
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        apiService = retrofit.create(QwenApiService.class);
    }

    public void sendMessage(String conversationId, String model, String message, ChatCallback callback) {
        executor.execute(() -> {
            try {
                String chatId = chatIdMap.get(conversationId);
                if (chatId == null) {
                    Response<QwenResponse> response = createNewChat(model).execute();
                    if (response.isSuccessful() && response.body() != null && response.body().success) {
                        chatId = response.body().data.id;
                        chatIdMap.put(conversationId, chatId);
                    } else {
                        mainHandler.post(() -> callback.onError("Failed to create new chat"));
                        return;
                    }
                }

                Response<QwenResponse> response = sendChatMessage(chatId, model, message).execute();
                if (response.isSuccessful() && response.body() != null) {
                    StringBuilder fullResponse = new StringBuilder();
                    for (QwenResponse.Choice choice : response.body().choices) {
                        fullResponse.append(choice.delta.content);
                    }
                    mainHandler.post(() -> callback.onResponse(fullResponse.toString()));
                } else {
                    mainHandler.post(() -> callback.onError("Failed to get response"));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending message", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    private Call<QwenResponse> createNewChat(String model) {
        QwenRequest request = new QwenRequest();
        request.title = "New Chat";
        request.models = java.util.Collections.singletonList(model);
        request.chat_mode = "normal";
        request.chat_type = "t2t";
        request.timestamp = System.currentTimeMillis();
        return apiService.createChat(BASE_URL + "chats/new", "Bearer " + apiConfig.getAuthorization(), request);
    }

    private Call<QwenResponse> sendChatMessage(String chatId, String model, String message) {
        QwenRequest request = new QwenRequest();
        request.stream = true;
        request.incremental_output = true;
        request.chat_id = chatId;
        request.chat_mode = "normal";
        request.model = model;
        request.parent_id = null;
        request.messages = java.util.Collections.singletonList(
                new pro.sketchware.activities.ai.chat.models.ChatMessage(
                        java.util.UUID.randomUUID().toString(),
                        message,
                        pro.sketchware.activities.ai.chat.models.ChatMessage.TYPE_USER,
                        System.currentTimeMillis()
                )
        );
        request.timestamp = System.currentTimeMillis() / 1000;
        return apiService.sendMessage(BASE_URL + "chat/completions?chat_id=" + chatId, "Bearer " + apiConfig.getAuthorization(), request);
    }
}