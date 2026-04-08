package pro.sketchware.agent.provider;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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

public class OpenRouterProvider implements AIProvider {

    private static final String BASE_URL = "https://openrouter.ai/api/v1";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient.Builder()
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();
    private Call currentCall;

    @Override
    public String getId() {
        return "openrouter";
    }

    @Override
    public String getDisplayName() {
        return "OpenRouter";
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
                                AgentDatabase.ModelInfo info = new AgentDatabase.ModelInfo();
                                info.id = m.has("id") ? m.get("id").getAsString() : "";
                                info.name = m.has("name") ? m.get("name").getAsString() : info.id;
                                info.contextLength = m.has("context_length") ? m.get("context_length").getAsInt() : 0;
                                if (!info.id.isEmpty()) {
                                    models.add(info);
                                }
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
                .addHeader("HTTP-Referer", "https://sketchware.pro")
                .addHeader("X-Title", "Sketchware Pro Agent")
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

    protected void processSSEResponse(Call call, Response response, StreamCallback callback) throws IOException {
        StringBuilder fullResponse = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        StringBuilder[] currentToolArgs = new StringBuilder[0];

        try (ResponseBody responseBody = response.body()) {
            if (responseBody == null) {
                mainHandler.post(() -> callback.onError("Empty response"));
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody.byteStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (call.isCanceled()) return;
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) break;
                    if (data.isEmpty()) continue;
                    try {
                        JsonObject chunk = JsonParser.parseString(data).getAsJsonObject();
                        processOpenAIChunk(chunk, fullResponse, toolCalls, callback);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        if (!call.isCanceled()) {
            if (!toolCalls.isEmpty()) {
                mainHandler.post(() -> callback.onToolCall(toolCalls));
            } else {
                String result = fullResponse.toString();
                mainHandler.post(() -> callback.onComplete(result));
            }
        }
    }

    protected void processOpenAIChunk(JsonObject chunk, StringBuilder fullResponse,
                                       List<ToolCall> toolCalls, StreamCallback callback) {
        if (!chunk.has("choices")) return;
        JsonArray choices = chunk.getAsJsonArray("choices");
        if (choices.isEmpty()) return;

        JsonObject choice = choices.get(0).getAsJsonObject();
        if (!choice.has("delta")) return;

        JsonObject delta = choice.getAsJsonObject("delta");

        if (delta.has("content") && !delta.get("content").isJsonNull()) {
            String content = delta.get("content").getAsString();
            fullResponse.append(content);
            mainHandler.post(() -> callback.onToken(content));
        }

        if (delta.has("tool_calls")) {
            JsonArray tcs = delta.getAsJsonArray("tool_calls");
            for (JsonElement tcEl : tcs) {
                JsonObject tc = tcEl.getAsJsonObject();
                int index = tc.has("index") ? tc.get("index").getAsInt() : 0;

                // Ensure list is large enough
                while (toolCalls.size() <= index) {
                    toolCalls.add(new ToolCall("", "", ""));
                }

                ToolCall existing = toolCalls.get(index);

                if (tc.has("id") && !tc.get("id").isJsonNull()) {
                    existing.id = tc.get("id").getAsString();
                }

                if (tc.has("function")) {
                    JsonObject fn = tc.getAsJsonObject("function");
                    if (fn.has("name") && !fn.get("name").isJsonNull()) {
                        existing.name = fn.get("name").getAsString();
                    }
                    if (fn.has("arguments") && !fn.get("arguments").isJsonNull()) {
                        existing.arguments = existing.arguments + fn.get("arguments").getAsString();
                    }
                }
            }
        }
    }

    protected JsonObject buildOpenAIRequestBody(String model, List<MessagePayload> messages,
                                                 List<ToolDefinition> tools) {
        JsonObject root = new JsonObject();
        root.addProperty("model", model);
        root.addProperty("stream", true);
        root.addProperty("temperature", 0.7);
        root.addProperty("max_tokens", 8192);

        JsonArray messagesArr = new JsonArray();
        for (MessagePayload msg : messages) {
            JsonObject m = new JsonObject();
            m.addProperty("role", msg.role);

            if ("tool".equals(msg.role) && msg.toolCallId != null) {
                m.addProperty("content", msg.content);
                m.addProperty("tool_call_id", msg.toolCallId);
            } else if ("assistant".equals(msg.role) && msg.toolCalls != null && !msg.toolCalls.isEmpty()) {
                if (msg.content != null && !msg.content.isEmpty()) {
                    m.addProperty("content", msg.content);
                } else {
                    m.add("content", null);
                }
                try {
                    JsonArray tcs = JsonParser.parseString(msg.toolCalls).getAsJsonArray();
                    JsonArray openaiTcs = new JsonArray();
                    for (JsonElement tc : tcs) {
                        JsonObject tcObj = tc.getAsJsonObject();
                        JsonObject openaiTc = new JsonObject();
                        openaiTc.addProperty("id", tcObj.get("id").getAsString());
                        openaiTc.addProperty("type", "function");
                        JsonObject fn = new JsonObject();
                        fn.addProperty("name", tcObj.get("name").getAsString());
                        fn.addProperty("arguments", tcObj.get("arguments").getAsString());
                        openaiTc.add("function", fn);
                        openaiTcs.add(openaiTc);
                    }
                    m.add("tool_calls", openaiTcs);
                } catch (Exception ignored) {
                }
            } else {
                m.addProperty("content", msg.content);
            }

            messagesArr.add(m);
        }
        root.add("messages", messagesArr);

        if (tools != null && !tools.isEmpty()) {
            JsonArray toolsArr = new JsonArray();
            for (ToolDefinition tool : tools) {
                JsonObject t = new JsonObject();
                t.addProperty("type", "function");
                JsonObject fn = new JsonObject();
                fn.addProperty("name", tool.name);
                fn.addProperty("description", tool.description);
                if (tool.parametersJson != null && !tool.parametersJson.isEmpty()) {
                    fn.add("parameters", JsonParser.parseString(tool.parametersJson));
                }
                t.add("function", fn);
                toolsArr.add(t);
            }
            root.add("tools", toolsArr);
        }

        return root;
    }

    @Override
    public void cancelRequest() {
        if (currentCall != null && !currentCall.isCanceled()) {
            currentCall.cancel();
        }
    }
}
