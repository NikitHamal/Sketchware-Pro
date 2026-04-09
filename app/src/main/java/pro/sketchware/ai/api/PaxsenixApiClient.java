package pro.sketchware.ai.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

/** OpenAI-compatible Paxsenix provider. */
public class PaxsenixApiClient extends AiApiClient {

    private static final String BASE_URL = "https://api.paxsenix.org";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public PaxsenixApiClient(String apiKey) {
        super(apiKey, AiProvider.PAXSENIX);
    }

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        Request request = addBearerAuth(new Request.Builder())
                .url(BASE_URL + "/v1/models")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Paxsenix fetchModels failed: HTTP " + response.code()
                        + " " + readBodySafely(response));
            }
            ResponseBody body = response.body();
            if (body == null) {
                return fallbackModels();
            }

            JsonObject root = JsonParser.parseString(body.string()).getAsJsonObject();
            JsonArray dataArray = root.has("data") && root.get("data").isJsonArray()
                    ? root.getAsJsonArray("data") : new JsonArray();

            List<ModelInfo> result = new ArrayList<>();
            for (JsonElement elem : dataArray) {
                if (!elem.isJsonObject()) continue;
                JsonObject model = elem.getAsJsonObject();
                String id = getStringOrDefault(model, "id", "");
                if (id.isEmpty()) continue;
                String display = getStringOrDefault(model, "name", id);
                String description = getStringOrDefault(model, "description", "Paxsenix model");
                result.add(new ModelInfo(id, display, AiProvider.PAXSENIX, 0L, description));
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
                    (modelId == null || modelId.trim().isEmpty()) ? "gpt-4.1-mini" : modelId,
                    systemPrompt,
                    tools
            );

            Request request = addBearerAuth(new Request.Builder())
                    .url(BASE_URL + "/v1/chat/completions")
                    .post(RequestBody.create(requestBody.toString(), JSON))
                    .header("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    handler.onError("Paxsenix request failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        String errorBody = readBodySafely(response);
                        handler.onError("Paxsenix HTTP " + response.code() + ": " + errorBody);
                        response.close();
                        return;
                    }

                    ResponseBody body = response.body();
                    if (body == null) {
                        handler.onError("Paxsenix returned empty response body");
                        return;
                    }

                    NvidiaApiClient.parseOpenAiSseStream(body, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("Failed to build Paxsenix request: " + e.getMessage());
        }
    }

    private static String readBodySafely(Response response) {
        try {
            ResponseBody body = response.body();
            return body != null ? body.string() : "(no body)";
        } catch (Exception e) {
            return "(failed to read body: " + e.getMessage() + ")";
        }
    }

    private static String getStringOrDefault(JsonObject obj, String key, String defaultValue) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : defaultValue;
    }

    private static List<ModelInfo> fallbackModels() {
        List<ModelInfo> fallback = new ArrayList<>();
        fallback.add(new ModelInfo("gpt-4.1-mini", "Paxsenix GPT-4.1 Mini",
                AiProvider.PAXSENIX, 0L, "Default Paxsenix model"));
        return fallback;
    }
}
