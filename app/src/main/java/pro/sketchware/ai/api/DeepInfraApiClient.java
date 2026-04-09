package pro.sketchware.ai.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.ModelInfo;

/**
 * DeepInfra client using the OpenAI-compatible chat completions endpoint.
 * DeepInfra chat access is supported without a user-supplied API key in this integration,
 * so bearer authentication is only attached when a key is present.
 */
public class DeepInfraApiClient extends AiApiClient {

    private static final String CHAT_URL = "https://api.deepinfra.com/v1/openai/chat/completions";
    private static final String MODELS_URL = "https://api.deepinfra.com/models/featured";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public DeepInfraApiClient(String apiKey) {
        super(apiKey, AiProvider.DEEPINFRA);
    }

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(MODELS_URL)
                .get()
                .header("User-Agent", "Sketchware Pro Agent")
                .header("Accept", "application/json");
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            addBearerAuth(builder);
        }

        try (Response response = client.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("DeepInfra fetchModels failed: HTTP " + response.code()
                        + " " + readBodySafely(response));
            }

            ResponseBody body = response.body();
            if (body == null) {
                return fallbackModels();
            }

            JsonElement root = JsonParser.parseString(body.string());
            List<ModelInfo> result = new ArrayList<>();
            JsonArray array;
            if (root.isJsonArray()) {
                array = root.getAsJsonArray();
            } else if (root.isJsonObject() && root.getAsJsonObject().has("data")
                    && root.getAsJsonObject().get("data").isJsonArray()) {
                array = root.getAsJsonObject().getAsJsonArray("data");
            } else {
                array = new JsonArray();
            }

            for (JsonElement element : array) {
                if (!element.isJsonObject()) continue;
                JsonObject model = element.getAsJsonObject();
                String id = getString(model, "model_name");
                if (id == null || id.isEmpty()) {
                    id = getString(model, "id");
                }
                if (id == null || id.isEmpty()) continue;
                String type = getString(model, "type");
                if (type != null && !type.isEmpty()
                        && !("text-generation".equalsIgnoreCase(type)
                        || "chat-completion".equalsIgnoreCase(type)
                        || "llm".equalsIgnoreCase(type))) {
                    continue;
                }
                String description = getString(model, "description");
                result.add(new ModelInfo(id, toDisplayName(id), AiProvider.DEEPINFRA, 0L,
                        description != null ? description : "DeepInfra model"));
            }

            return result.isEmpty() ? fallbackModels() : result;
        }
    }

    @Override
    public void sendChatRequest(List<ChatMessage> messages, String modelId,
                                String systemPrompt, StreamingResponseHandler handler) {
        sendChatRequest(messages, modelId, systemPrompt, null, handler);
    }

    @Override
    public void sendChatRequest(List<ChatMessage> messages, String modelId,
                                String systemPrompt, List<ToolDefinition> tools,
                                StreamingResponseHandler handler) {
        try {
            JsonObject requestBody = NvidiaApiClient.buildOpenAiRequestBody(
                    messages,
                    (modelId == null || modelId.trim().isEmpty()) ? "deepseek-ai/DeepSeek-V3" : modelId,
                    systemPrompt,
                    tools
            );

            Request.Builder builder = new Request.Builder()
                    .url(CHAT_URL)
                    .post(RequestBody.create(requestBody.toString(), JSON))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("User-Agent", "Sketchware Pro Agent");
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                addBearerAuth(builder);
            }

            client.newCall(builder.build()).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    handler.onError("DeepInfra request failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        String errorBody = readBodySafely(response);
                        handler.onError("DeepInfra HTTP " + response.code() + ": " + errorBody);
                        response.close();
                        return;
                    }

                    ResponseBody body = response.body();
                    if (body == null) {
                        handler.onError("DeepInfra returned empty response body");
                        return;
                    }

                    NvidiaApiClient.parseOpenAiSseStream(body, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("Failed to build DeepInfra request: " + e.getMessage());
        }
    }

    private static String getString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }

    private static String readBodySafely(Response response) {
        try {
            ResponseBody body = response.body();
            return body != null ? body.string() : "(no body)";
        } catch (Exception e) {
            return "(failed to read body: " + e.getMessage() + ")";
        }
    }

    private static List<ModelInfo> fallbackModels() {
        List<ModelInfo> fallback = new ArrayList<>();
        fallback.add(new ModelInfo("deepseek-ai/DeepSeek-V3", "DeepInfra DeepSeek V3",
                AiProvider.DEEPINFRA, 0L, "Fast default DeepInfra chat model"));
        fallback.add(new ModelInfo("meta-llama/Llama-3.3-70B-Instruct", "DeepInfra Llama 3.3 70B",
                AiProvider.DEEPINFRA, 0L, "General-purpose instruct model"));
        return fallback;
    }

    private static String toDisplayName(String id) {
        String value = id.replace('/', ' ').replace('-', ' ').replace('_', ' ').trim();
        if (value.isEmpty()) {
            return "DeepInfra";
        }
        return value.substring(0, 1).toUpperCase(Locale.US) + value.substring(1);
    }
}
