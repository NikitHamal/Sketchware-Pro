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

public class GeminiProvider implements AIProvider {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
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
        return "gemini";
    }

    @Override
    public String getDisplayName() {
        return "Gemini";
    }

    @Override
    public void fetchModels(String apiKey, Consumer<List<AgentDatabase.ModelInfo>> callback) {
        String url = BASE_URL + "/models?key=" + apiKey;
        Request request = new Request.Builder().url(url).get().build();

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
                        if (root.has("models")) {
                            JsonArray arr = root.getAsJsonArray("models");
                            for (JsonElement el : arr) {
                                JsonObject m = el.getAsJsonObject();
                                String name = m.has("name") ? m.get("name").getAsString() : "";
                                String displayName = m.has("displayName") ? m.get("displayName").getAsString() : name;
                                if (!name.contains("generateContent")) {
                                    // Filter for models that support generation
                                    if (m.has("supportedGenerationMethods")) {
                                        JsonArray methods = m.getAsJsonArray("supportedGenerationMethods");
                                        boolean canGenerate = false;
                                        for (JsonElement method : methods) {
                                            if (method.getAsString().contains("generateContent")) {
                                                canGenerate = true;
                                                break;
                                            }
                                        }
                                        if (!canGenerate) continue;
                                    }
                                }
                                AgentDatabase.ModelInfo info = new AgentDatabase.ModelInfo();
                                info.id = name.replace("models/", "");
                                info.name = displayName;
                                info.contextLength = m.has("inputTokenLimit") ? m.get("inputTokenLimit").getAsInt() : 0;
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
        String url = BASE_URL + "/models/" + model + ":streamGenerateContent?key=" + apiKey + "&alt=sse";

        JsonObject requestBody = buildRequestBody(messages, tools);
        RequestBody body = RequestBody.create(requestBody.toString(), JSON);

        Request request = new Request.Builder()
                .url(url)
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

                StringBuilder fullResponse = new StringBuilder();
                List<ToolCall> allToolCalls = new ArrayList<>();

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
                            if (data.isEmpty()) continue;
                            try {
                                JsonObject chunk = JsonParser.parseString(data).getAsJsonObject();
                                processGeminiChunk(chunk, fullResponse, allToolCalls, callback);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }

                if (!call.isCanceled()) {
                    if (!allToolCalls.isEmpty()) {
                        mainHandler.post(() -> callback.onToolCall(allToolCalls));
                    } else {
                        String result = fullResponse.toString();
                        mainHandler.post(() -> callback.onComplete(result));
                    }
                }
            }
        });
    }

    private void processGeminiChunk(JsonObject chunk, StringBuilder fullResponse,
                                     List<ToolCall> toolCalls, StreamCallback callback) {
        if (!chunk.has("candidates")) return;
        JsonArray candidates = chunk.getAsJsonArray("candidates");
        if (candidates.isEmpty()) return;

        JsonObject candidate = candidates.get(0).getAsJsonObject();
        if (!candidate.has("content")) return;

        JsonObject content = candidate.getAsJsonObject("content");
        if (!content.has("parts")) return;

        JsonArray parts = content.getAsJsonArray("parts");
        for (JsonElement partEl : parts) {
            JsonObject part = partEl.getAsJsonObject();
            if (part.has("text")) {
                String text = part.get("text").getAsString();
                fullResponse.append(text);
                mainHandler.post(() -> callback.onToken(text));
            } else if (part.has("functionCall")) {
                JsonObject fc = part.getAsJsonObject("functionCall");
                String name = fc.has("name") ? fc.get("name").getAsString() : "";
                String args = fc.has("args") ? fc.get("args").toString() : "{}";
                toolCalls.add(new ToolCall(
                        "call_" + System.currentTimeMillis() + "_" + toolCalls.size(),
                        name, args
                ));
            }
        }
    }

    private JsonObject buildRequestBody(List<MessagePayload> messages, List<ToolDefinition> tools) {
        JsonObject root = new JsonObject();

        // Build contents array
        JsonArray contents = new JsonArray();
        String systemInstruction = null;

        for (MessagePayload msg : messages) {
            if ("system".equals(msg.role)) {
                systemInstruction = msg.content;
                continue;
            }

            JsonObject content = new JsonObject();
            String role = "user".equals(msg.role) ? "user" : "model";
            content.addProperty("role", role);

            JsonArray parts = new JsonArray();

            if ("tool".equals(msg.role) && msg.toolCallId != null) {
                JsonObject functionResponse = new JsonObject();
                functionResponse.addProperty("name", msg.toolCallId);
                JsonObject responseObj = new JsonObject();
                responseObj.addProperty("result", msg.content);
                functionResponse.add("response", responseObj);
                JsonObject part = new JsonObject();
                part.add("functionResponse", functionResponse);
                parts.add(part);
                content.addProperty("role", "user");
            } else if (msg.toolCalls != null && !msg.toolCalls.isEmpty()) {
                // Model message with tool calls
                try {
                    JsonArray toolCallsArr = JsonParser.parseString(msg.toolCalls).getAsJsonArray();
                    for (JsonElement tc : toolCallsArr) {
                        JsonObject tcObj = tc.getAsJsonObject();
                        JsonObject functionCall = new JsonObject();
                        functionCall.addProperty("name", tcObj.get("name").getAsString());
                        functionCall.add("args", JsonParser.parseString(tcObj.get("arguments").getAsString()));
                        JsonObject part = new JsonObject();
                        part.add("functionCall", functionCall);
                        parts.add(part);
                    }
                } catch (Exception e) {
                    JsonObject textPart = new JsonObject();
                    textPart.addProperty("text", msg.content);
                    parts.add(textPart);
                }
            } else {
                JsonObject textPart = new JsonObject();
                textPart.addProperty("text", msg.content != null ? msg.content : "");
                parts.add(textPart);
            }

            content.add("parts", parts);
            contents.add(content);
        }

        root.add("contents", contents);

        // System instruction
        if (systemInstruction != null) {
            JsonObject si = new JsonObject();
            JsonArray siParts = new JsonArray();
            JsonObject siPart = new JsonObject();
            siPart.addProperty("text", systemInstruction);
            siParts.add(siPart);
            si.add("parts", siParts);
            root.add("systemInstruction", si);
        }

        // Tools
        if (tools != null && !tools.isEmpty()) {
            JsonArray toolsArray = new JsonArray();
            JsonArray functionDeclarations = new JsonArray();
            for (ToolDefinition tool : tools) {
                JsonObject fd = new JsonObject();
                fd.addProperty("name", tool.name);
                fd.addProperty("description", tool.description);
                if (tool.parametersJson != null && !tool.parametersJson.isEmpty()) {
                    fd.add("parameters", JsonParser.parseString(tool.parametersJson));
                }
                functionDeclarations.add(fd);
            }
            JsonObject toolObj = new JsonObject();
            toolObj.add("functionDeclarations", functionDeclarations);
            toolsArray.add(toolObj);
            root.add("tools", toolsArray);
        }

        // Generation config
        JsonObject genConfig = new JsonObject();
        genConfig.addProperty("temperature", 0.7);
        genConfig.addProperty("maxOutputTokens", 8192);
        root.add("generationConfig", genConfig);

        return root;
    }

    @Override
    public void cancelRequest() {
        if (currentCall != null && !currentCall.isCanceled()) {
            currentCall.cancel();
        }
    }
}
