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
 * Groq API client — uses OpenAI-compatible endpoints.
 * Base URL: https://api.groq.com/openai/v1
 * Requires API key (Bearer token).
 * Known for UNLIMITED rate limits on LPU hardware.
 */
public class GroqApiClient extends AiApiClient {

    private static final String BASE        = "https://api.groq.com/openai/v1";
    private static final String MODELS_URL  = BASE + "/models";
    private static final String CHAT_URL    = BASE + "/chat/completions";
    private static final MediaType JSON     = MediaType.get("application/json; charset=utf-8");

    public GroqApiClient(String apiKey) {
        super(apiKey, AiProvider.GROQ);
    }

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        Request request = new Request.Builder()
                .url(MODELS_URL)
                .get()
                .header("Authorization", "Bearer " + apiKey)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Groq fetchModels HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) return fallbackModels();

            JsonElement root = JsonParser.parseString(body.string());
            List<ModelInfo> result = new ArrayList<>();

            JsonArray data = null;
            if (root.isJsonObject() && root.getAsJsonObject().has("data")) {
                data = root.getAsJsonObject().getAsJsonArray("data");
            } else if (root.isJsonArray()) {
                data = root.getAsJsonArray();
            }

            if (data == null) return fallbackModels();

            for (JsonElement el : data) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();

                String id = str(obj, "id");
                if (id == null || id.isEmpty()) continue;

                // Skip non-coding / non-general useful models
                // Skip image/audio/embedding/non-chat models
                {
                    String _lo = id == null ? "" : id.toLowerCase(java.util.Locale.ROOT);
                    if (_lo.contains("whisper") || _lo.contains("tts") || _lo.contains("guard")
                        || _lo.contains("audio") || _lo.contains("speech") || _lo.contains("embed")
                        || _lo.contains("moderation") || _lo.contains("realtime")
                        || _lo.contains("dall-e") || _lo.contains("stable-diff")
                        || _lo.contains("sdxl") || _lo.contains("flux") || _lo.contains("imagen")
                        || _lo.contains("image-gen") || _lo.contains("text-to-image")
                        || _lo.contains("video") || _lo.contains("rerank")
                        || _lo.contains("transcrib") || _lo.contains("midjourney")) continue;
                }

                long ctx = 0;
                if (obj.has("context_window") && !obj.get("context_window").isJsonNull()) {
                    try { ctx = obj.get("context_window").getAsLong(); } catch (Exception ignored) {}
                }

                result.add(new ModelInfo(id, toName(id), AiProvider.GROQ, ctx,
                        "Groq ∞ — " + toName(id)));
            }
            
            // Sort models alphabetically (A-Z)
            java.util.Collections.sort(result);
            
            return result.isEmpty() ? fallbackModels() : result;
        }
    }

    @Override
    public void sendChatRequest(List<ChatMessage> messages, String modelId,
                                String systemPrompt, StreamingResponseHandler handler) {
        sendChatRequest(messages, modelId, systemPrompt, null, null, handler);
    }

    @Override
    public void sendChatRequest(List<ChatMessage> messages, String modelId,
                                String systemPrompt, Object tag, StreamingResponseHandler handler) {
        sendChatRequest(messages, modelId, systemPrompt, null, tag, handler);
    }

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
            // Groq supports up to 32 768 output tokens — set explicitly so long
            // agent responses are never truncated mid-stream.
            JsonObject body = NvidiaApiClient.buildOpenAiRequestBody(
                    messages,
                    modelId != null && !modelId.isEmpty() ? modelId : "llama-3.3-70b-versatile",
                    systemPrompt,
                    tools,
                    0f,     // temperature: use provider default
                    8192    // max_tokens: safe limit (IA uses 4000, avoids 429)
            );

            Request.Builder builder = new Request.Builder()
                    .url(CHAT_URL)
                    .post(RequestBody.create(body.toString(), JSON))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json");
            
            if (tag != null) builder.tag(tag);
            Request request = builder.build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    handler.onError("Groq request failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        String err = AiErrorHelper.readBodySafely(response);
                        response.close();
                        handler.onError("Groq: " + AiErrorHelper.getFriendlyMessage(code, err));
                        return;
                    }
                    ResponseBody rb = response.body();
                    if (rb == null) {
                        handler.onError("Groq returned empty body");
                        return;
                    }
                    NvidiaApiClient.parseOpenAiSseStream(rb, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("Groq build error: " + e.getMessage());
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private static String readBody(Response r) {
        try { ResponseBody b = r.body(); return b != null ? b.string() : ""; }
        catch (Exception e) { return ""; }
    }

    private static String toName(String id) {
        // "llama-3.3-70b-versatile" → "Llama 3.3 70B Versatile"
        String s = id.replace("-", " ").replace("_", " ");
        if (s.isEmpty()) return id;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static List<ModelInfo> fallbackModels() {
        List<ModelInfo> list = new ArrayList<>();
        list.add(new ModelInfo("deepseek-r1-distill-llama-70b", "DeepSeek R1 Distill Llama 70B", AiProvider.GROQ, 128000, "Groq ∞ — Best for logic & reasoning"));
        list.add(new ModelInfo("llama-3.3-70b-versatile",      "Llama 3.3 70B Versatile",      AiProvider.GROQ, 128000, "Groq ∞ — Best for code & Sketchware projects"));
        list.add(new ModelInfo("llama-3.1-8b-instant",         "Llama 3.1 8B Instant",          AiProvider.GROQ, 128000, "Groq ∞ — Fast, good for small tasks"));
        return list;
    }
}
