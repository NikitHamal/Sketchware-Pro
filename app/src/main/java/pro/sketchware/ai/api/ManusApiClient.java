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

/**
 * AI API client for Manus AI (OpenAI-compatible endpoint).
 *
 * <p>Manus AI is an agentic AI platform accessible at https://manus.im.
 * Users can obtain an API key from https://manus.im/settings/api.
 *
 * <p>This client uses the shared OpenAI-compatible helpers from {@link NvidiaApiClient}
 * for request building and SSE stream parsing.
 */
public class ManusApiClient extends AiApiClient {

    private static final String BASE_URL   = "https://api.manus.im";
    private static final String MODELS_URL = BASE_URL + "/v1/models";
    private static final String CHAT_URL   = BASE_URL + "/v1/chat/completions";
    private static final MediaType JSON    = MediaType.get("application/json; charset=utf-8");

    /** Fallback model list shown before the API key is validated. */
    private static final List<ModelInfo> FALLBACK_MODELS = new ArrayList<>();

    static {
        FALLBACK_MODELS.add(new ModelInfo("manus-v1",
                "Manus v1", AiProvider.MANUS, 128000,
                "Manus flagship model — advanced agentic reasoning and code generation."));
        FALLBACK_MODELS.add(new ModelInfo("manus-v1-mini",
                "Manus v1 Mini", AiProvider.MANUS, 32000,
                "Manus lightweight model — fast responses for simpler tasks."));
    }

    public ManusApiClient(String apiKey) {
        super(apiKey, AiProvider.MANUS);
    }

    // ── Model listing ────────────────────────────────────────────────────────

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        Request request = addBearerAuth(new Request.Builder())
                .url(MODELS_URL)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return FALLBACK_MODELS;
            }
            String body = response.body().string();
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            JsonArray data  = json.getAsJsonArray("data");
            if (data == null) return FALLBACK_MODELS;

            List<ModelInfo> models = new ArrayList<>();
            for (JsonElement el : data) {
                JsonObject obj = el.getAsJsonObject();
                String id   = obj.has("id")   ? obj.get("id").getAsString()   : "";
                String name = obj.has("name") ? obj.get("name").getAsString() : id;
                if (id.isEmpty()) continue;
                models.add(new ModelInfo(id, name, AiProvider.MANUS, 128000, "Manus AI model"));
            }
            return models.isEmpty() ? FALLBACK_MODELS : models;
        }
    }

    // ── Chat (streaming, no tools) ───────────────────────────────────────────

    @Override
    public void sendChatRequest(List<ChatMessage> messages, String modelId,
                                String systemPrompt,
                                StreamingResponseHandler handler) {
        sendChatRequest(messages, modelId, systemPrompt, null, null, handler);
    }

    @Override
    public void sendChatRequest(List<ChatMessage> messages, String modelId,
                                String systemPrompt, Object tag,
                                StreamingResponseHandler handler) {
        sendChatRequest(messages, modelId, systemPrompt, null, tag, handler);
    }

    // ── Chat (streaming, with tools) ─────────────────────────────────────────

    @Override
    public void sendChatRequest(List<ChatMessage> messages, String modelId,
                                String systemPrompt, List<ToolDefinition> tools,
                                StreamingResponseHandler handler) {
        sendChatRequest(messages, modelId, systemPrompt, tools, null, handler);
    }

    @Override
    public void sendChatRequest(List<ChatMessage> messages, String modelId,
                                String systemPrompt, List<ToolDefinition> tools,
                                Object tag, StreamingResponseHandler handler) {
        try {
            JsonObject requestBody = NvidiaApiClient.buildOpenAiRequestBody(
                    messages, modelId, systemPrompt, tools);
            Request.Builder builder = addBearerAuth(new Request.Builder())
                    .url(CHAT_URL)
                    .post(RequestBody.create(requestBody.toString(), JSON));
            
            if (tag != null) builder.tag(tag);
            Request request = builder.build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    handler.onError("Manus AI error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        String err = NvidiaApiClient.readBodySafely(response);
                        response.close();
                        handler.onError("Manus AI: " + NvidiaApiClient.getFriendlyErrorMessage(code, err));
                        return;
                    }
                    ResponseBody rb = response.body();
                    if (rb == null) {
                        handler.onError("Empty response from Manus AI");
                        return;
                    }
                    NvidiaApiClient.parseOpenAiSseStream(rb, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("Manus AI: " + e.getMessage());
        }
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private String readBodySafely(Response response) {
        try {
            if (response.body() != null) return response.body().string();
        } catch (Exception ignored) {}
        return "(no body)";
    }
}
