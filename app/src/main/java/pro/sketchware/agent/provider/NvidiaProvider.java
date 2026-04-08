package pro.sketchware.agent.provider;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import pro.sketchware.agent.data.AgentDatabase;

public class NvidiaProvider extends OpenRouterProvider {

    private static final String BASE_URL = "https://integrate.api.nvidia.com/v1";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient.Builder()
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Call currentCall;

    @Override
    public String getId() {
        return "nvidia";
    }

    @Override
    public String getDisplayName() {
        return "NVIDIA";
    }

    @Override
    public void fetchModels(String apiKey, Consumer<List<AgentDatabase.ModelInfo>> callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + "/models")
                .addHeader("Authorization", "Bearer " + apiKey)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> callback.accept(new ArrayList<>()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                List<AgentDatabase.ModelInfo> models = new ArrayList<>();
                try (ResponseBody body = response.body()) {
                    if (body != null && response.isSuccessful()) {
                        String json = body.string();
                        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                        if (root.has("data")) {
                            JsonArray data = root.getAsJsonArray("data");
                            for (JsonElement el : data) {
                                JsonObject m = el.getAsJsonObject();
                                String id = m.has("id") ? m.get("id").getAsString() : "";
                                String type = m.has("object") ? m.get("object").getAsString() : "";
                                if (!"model".equals(type)) continue;
                                AgentDatabase.ModelInfo info = new AgentDatabase.ModelInfo();
                                info.id = id;
                                info.name = id;
                                info.contextLength = 0;
                                models.add(info);
                            }
                        }
                    }
                }
                mainHandler.post(() -> callback.accept(models));
            }
        });
    }

    @Override
    public void sendMessage(String apiKey, String model, List<MessagePayload> messages,
                            List<ToolDefinition> tools, StreamCallback callback) {
        JsonObject requestBody = buildOpenAIRequestBody(model, messages, tools);
        RequestBody body = RequestBody.create(requestBody.toString(), JSON);

        Request request = new Request.Builder()
                .url(BASE_URL + "/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();

        currentCall = client.newCall(request);
        currentCall.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (!call.isCanceled()) {
                    mainHandler.post(() -> callback.onError(e.getMessage()));
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    mainHandler.post(() -> callback.onError("HTTP " + response.code() + ": " + errorBody));
                    return;
                }
                processSSEResponse(call, response, callback);
            }
        });
    }

    @Override
    public void cancelRequest() {
        if (currentCall != null && !currentCall.isCanceled()) {
            currentCall.cancel();
        }
    }
}
