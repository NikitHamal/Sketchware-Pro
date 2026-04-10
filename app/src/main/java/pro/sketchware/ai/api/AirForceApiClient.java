package pro.sketchware.ai.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

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
 * API Airforce client - free, no authentication required.
 * Base URL: https://api.airforce/v1
 * Models: /v1/models, Chat: /v1/chat/completions
 * 
 * Many models are free (multiplier=0), others require credits.
 * This client filters for free models only by default.
 */
public class AirForceApiClient extends AiApiClient {

    private static final String BASE_URL = "https://api.airforce";
    private static final String MODELS_URL = BASE_URL + "/v1/models";
    private static final String CHAT_URL = BASE_URL + "/v1/chat/completions";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final String[] USER_AGENTS = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36",
    };

    private static final Random RANDOM = new Random();
    
    /** If true, only show free models (multiplier=0). If false, show all. */
    private boolean showFreeOnly = true;

    public AirForceApiClient(String apiKey) {
        super(apiKey, AiProvider.AIRFORCE);
    }

    public void setShowFreeOnly(boolean showFreeOnly) {
        this.showFreeOnly = showFreeOnly;
    }

    public boolean isShowFreeOnly() {
        return showFreeOnly;
    }

    private String getRandomUserAgent() {
        return USER_AGENTS[RANDOM.nextInt(USER_AGENTS.length)];
    }

    private String generateRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private Request.Builder applyHeaders(Request.Builder builder) {
        return builder
                .header("User-Agent", getRandomUserAgent())
                .header("Accept", "application/json, text/event-stream")
                .header("X-Request-ID", generateRequestId())
                .header("Cache-Control", "no-cache");
    }

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(MODELS_URL)
                .get();
        
        applyHeaders(builder);

        try (Response response = client.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                String body = readBodySafely(response);
                if (response.code() == 403 || response.code() == 429) {
                    return fallbackModels();
                }
                throw new IOException("AirForce fetchModels failed: HTTP " + response.code() + " " + body);
            }

            ResponseBody body = response.body();
            if (body == null) {
                return fallbackModels();
            }

            String bodyString = body.string();
            JsonElement root = JsonParser.parseString(bodyString);
            List<ModelInfo> result = new ArrayList<>();
            
            JsonArray dataArray;
            if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                if (obj.has("data") && obj.get("data").isJsonArray()) {
                    dataArray = obj.getAsJsonArray("data");
                } else {
                    return fallbackModels();
                }
            } else if (root.isJsonArray()) {
                dataArray = root.getAsJsonArray();
            } else {
                return fallbackModels();
            }

            for (JsonElement element : dataArray) {
                if (!element.isJsonObject()) continue;
                JsonObject model = element.getAsJsonObject();
                
                String id = getString(model, "id");
                if (id == null || id.isEmpty()) continue;
                
                // Only include chat models
                Boolean supportsChat = model.has("supports_chat") ? model.get("supports_chat").getAsBoolean() : null;
                if (supportsChat != null && !supportsChat) continue;
                
                // Filter by free only
                if (showFreeOnly) {
                    JsonElement mult = model.get("multiplier");
                    if (mult != null && !mult.isJsonNull()) {
                        try {
                            double m = mult.getAsDouble();
                            if (m > 0) continue; // skip paid models
                        } catch (Exception ignore) {}
                    }
                }
                
                // Check status
                String status = getString(model, "status");
                if (status != null && "major_outage".equalsIgnoreCase(status)) continue;

                String ownedBy = getString(model, "owned_by");
                String displayName = id;
                if (ownedBy != null && !ownedBy.isEmpty()) {
                    displayName = ownedBy + "/" + id;
                }

                int maxTokens = 0;
                if (model.has("max_tokens") && !model.get("max_tokens").isJsonNull()) {
                    try { maxTokens = model.get("max_tokens").getAsInt(); } catch (Exception ignore) {}
                }

                boolean supportsStreaming = model.has("supports_streaming")
                        && model.get("supports_streaming").getAsBoolean();
                boolean supportsNonStreaming = model.has("supports_non_streaming")
                        && model.get("supports_non_streaming").getAsBoolean();

                String description = "AirForce model";
                if (status != null) {
                    description = "Status: " + status;
                }
                if (maxTokens > 0) {
                    description += " | Max tokens: " + maxTokens;
                }

                ModelInfo baseInfo = new ModelInfo(id, displayName, AiProvider.AIRFORCE, maxTokens, description);
                result.add(baseInfo.withMetadata(maxTokens, supportsStreaming, supportsNonStreaming, status));
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
                    (modelId == null || modelId.trim().isEmpty()) ? "roleplay:free" : modelId,
                    systemPrompt,
                    tools
            );

            Request.Builder builder = new Request.Builder()
                    .url(CHAT_URL)
                    .post(RequestBody.create(requestBody.toString(), JSON));
            
            applyHeaders(builder);

            client.newCall(builder.build()).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    handler.onError("AirForce request failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (response.code() == 429) {
                        handler.onError("AirForce rate limited. Please wait and try again.");
                        response.close();
                        return;
                    }
                    
                    if (response.code() == 403) {
                        handler.onError("AirForce returned 403. Try again in a moment.");
                        response.close();
                        return;
                    }
                    
                    if (!response.isSuccessful()) {
                        String errorBody = readBodySafely(response);
                        handler.onError("AirForce HTTP " + response.code() + ": " + errorBody);
                        response.close();
                        return;
                    }

                    ResponseBody body = response.body();
                    if (body == null) {
                        handler.onError("AirForce returned empty response body");
                        return;
                    }

                    NvidiaApiClient.parseOpenAiSseStream(body, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("Failed to build AirForce request: " + e.getMessage());
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
        fallback.add(new ModelInfo("roleplay:free", "AirForce Roleplay Free",
                AiProvider.AIRFORCE, 4096, "Free roleplay model"));
        fallback.add(new ModelInfo("lana", "AirForce Lana",
                AiProvider.AIRFORCE, 4096, "Free Lana model"));
        fallback.add(new ModelInfo("grok-4.1-mini:free", "AirForce Grok 4.1 Mini Free",
                AiProvider.AIRFORCE, 4096, "Free Grok model"));
        fallback.add(new ModelInfo("deepseek-v3:free", "AirForce DeepSeek V3 Free",
                AiProvider.AIRFORCE, 4096, "Free DeepSeek model"));
        return fallback;
    }

    private static String toDisplayName(String id) {
        String value = id.replace('/', ' ').replace('-', ' ').replace('_', ' ').trim();
        if (value.isEmpty()) {
            return "AirForce";
        }
        return value.substring(0, 1).toUpperCase(Locale.US) + value.substring(1);
    }
}
