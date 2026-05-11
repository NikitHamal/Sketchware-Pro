package pro.sketchware.ai.engine;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pro.sketchware.ai.api.AiApiClient;
import pro.sketchware.ai.api.AiClientFactory;
import pro.sketchware.ai.api.StreamingResponseHandler;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.ModelInfo;
import pro.sketchware.ai.models.ToolCall;
import pro.sketchware.ai.storage.AiPreferences;

/**
 * ModelManager — dynamic fallback model system.
 *
 * <p>Models are NOT hardcoded. The user defines their active model list
 * (ACTIVE_MODELS) in AI Settings. At runtime, ModelManager tries each model in
 * order until one succeeds. If all fail the request is failed cleanly.
 *
 * <p>All heavy work must run on a background thread (blocks until completion).
 */
public final class ModelManager {

    private static final String TAG = "ModelManager";

    /** Prefs key for the user's ordered active-model JSON list. */
    public static final String KEY_ACTIVE_MODELS = "active_model_list";

    // ── ActiveModel entry ──────────────────────────────────────────────────────

    /**
     * Entry in the ACTIVE_MODELS list.
     * Stored as JSON: [{"provider":"GROQ","modelId":"compound-beta-mini"}, …]
     */
    public static class ActiveModel {
        public String provider; // AiProvider.name()
        public String modelId;

        public ActiveModel() {}

        public ActiveModel(AiProvider p, String modelId) {
            this.provider = p.name();
            this.modelId  = modelId;
        }

        public AiProvider resolveProvider() {
            if (provider == null) return null;
            try { return AiProvider.valueOf(provider); }
            catch (IllegalArgumentException e) { return null; }
        }
    }

    // ── FallbackCallback ───────────────────────────────────────────────────────

    /**
     * Callback for a model-fallback execution attempt.
     * Note: onToolCall here passes the parsed name/args as strings because
     * AIEngine layout tools do not use tool-calling — they are one-shot text responses.
     */
    public interface FallbackCallback {
        /** Called when a model responds successfully. */
        void onSuccess(String modelId, AiProvider provider);
        /** Called after ALL models have been tried and all failed. */
        void onAllFailed(String lastError);
        /** Forwarded streaming text chunk. */
        void onStreamChunk(String chunk);
        /** Forwarded tool-call: name and raw arguments JSON string. */
        void onToolCall(String name, String argsJson);
        /** A single model failed; willRetry indicates another model will be tried. */
        void onError(String error, boolean willRetry);
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final Context       context;
    private final AiPreferences prefs;
    private final Gson          gson = new Gson();

    public ModelManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs   = AiPreferences.getInstance(context);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Executes a chat request using ACTIVE_MODELS with automatic fallback.
     * Tries each model in sequence; stops at first success.
     *
     * <p><b>Must be called from a background thread — this method blocks.</b>
     *
     * @param messages     conversation history
     * @param systemPrompt system instruction (may be null)
     * @param tools        tool definitions (may be null for one-shot requests)
     * @param callback     result/streaming callback
     */
    public void executeWithFallback(
            List<ChatMessage> messages,
            String systemPrompt,
            List<pro.sketchware.ai.api.ToolDefinition> tools,
            FallbackCallback callback) {

        List<ActiveModel> models = getActiveModels();
        if (models.isEmpty()) {
            callback.onAllFailed("No active models configured. "
                    + "Go to AI Settings → Active Models and add at least one model.");
            return;
        }

        String lastError = "Unknown error";

        for (ActiveModel am : models) {
            AiProvider provider = am.resolveProvider();
            if (provider == null) {
                Log.w(TAG, "Skipping unknown provider: " + am.provider);
                continue;
            }

            String apiKey = prefs.getApiKey(provider);
            if ((apiKey == null || apiKey.isEmpty()) && !isNoKeyProvider(provider)) {
                Log.d(TAG, "Skipping " + provider + " — no API key configured");
                continue;
            }
            if (!prefs.isProviderEnabled(provider)) {
                Log.d(TAG, "Skipping " + provider + " — disabled by user");
                continue;
            }
            if (apiKey == null) apiKey = "";

            AiApiClient client = AiClientFactory.createClient(context, provider, apiKey);
            if (client == null) {
                Log.w(TAG, "No client implementation for provider: " + provider);
                continue;
            }

            // ── Try this model ─────────────────────────────────────────────
            final boolean[] succeeded   = { false };
            final String[]  errorHolder = { null };
            final Object    lock        = new Object();

            // FIX: onToolCall signature must match StreamingResponseHandler exactly:
            //      void onToolCall(ToolCall toolCall)
            StreamingResponseHandler handler = new StreamingResponseHandler() {
                @Override
                public void onChunk(String textDelta) {
                    callback.onStreamChunk(textDelta);
                }

                @Override
                public void onToolCall(ToolCall toolCall) {
                    // Bridge ToolCall object → FallbackCallback's (String, String) API
                    String name    = toolCall != null ? toolCall.getName()      : "";
                    String argsJson = toolCall != null ? toolCall.getArguments() : "{}";
                    callback.onToolCall(name, argsJson);
                }

                @Override
                public void onComplete(String fullResponse) {
                    succeeded[0] = true;
                    synchronized (lock) { lock.notifyAll(); }
                }

                @Override
                public void onError(String error) {
                    errorHolder[0] = error;
                    synchronized (lock) { lock.notifyAll(); }
                }
            };

            Log.d(TAG, "Trying " + provider + " / " + am.modelId);

            if (tools != null && !tools.isEmpty()) {
                client.sendChatRequest(messages, am.modelId, systemPrompt, tools, handler);
            } else {
                client.sendChatRequest(messages, am.modelId, systemPrompt, handler);
            }

            // Block until the request completes or times out (120 s max)
            synchronized (lock) {
                long deadline = System.currentTimeMillis() + 120_000L;
                while (!succeeded[0] && errorHolder[0] == null) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) {
                        errorHolder[0] = "Timeout after 120 s";
                        break;
                    }
                    try { lock.wait(remaining); }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        callback.onAllFailed("Interrupted");
                        return;
                    }
                }
            }

            if (succeeded[0]) {
                callback.onSuccess(am.modelId, provider);
                return;
            }

            lastError = errorHolder[0] != null ? errorHolder[0] : "Unknown error";
            Log.w(TAG, provider + "/" + am.modelId + " failed: " + lastError);
            callback.onError(lastError, true /* willRetry */);
        }

        // All models exhausted
        callback.onAllFailed("All " + models.size() + " model(s) failed. "
                + "Last error: " + lastError);
    }

    // ── ACTIVE_MODELS CRUD ────────────────────────────────────────────────────

    /** Returns the user-configured active model list (in order). */
    public List<ActiveModel> getActiveModels() {
        String json = prefs.prefs().getString(KEY_ACTIVE_MODELS, null);
        if (json == null || json.isEmpty()) return buildDefaultActiveModels();
        try {
            Type type = new TypeToken<List<ActiveModel>>() {}.getType();
            List<ActiveModel> list = gson.fromJson(json, type);
            return (list != null && !list.isEmpty()) ? list : buildDefaultActiveModels();
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse active models — using defaults", e);
            return buildDefaultActiveModels();
        }
    }

    /** Saves the user-configured active model list. */
    public void setActiveModels(List<ActiveModel> models) {
        prefs.prefs().edit()
                .putString(KEY_ACTIVE_MODELS, gson.toJson(models))
                .apply();
    }

    /** Adds a model to the end of the active list (no-op if already present). */
    public void addActiveModel(AiProvider provider, String modelId) {
        List<ActiveModel> list = new ArrayList<>(getActiveModels());
        for (ActiveModel m : list) {
            if (provider.name().equals(m.provider) && modelId.equals(m.modelId)) return;
        }
        list.add(new ActiveModel(provider, modelId));
        setActiveModels(list);
    }

    /** Removes a model from the active list. */
    public void removeActiveModel(AiProvider provider, String modelId) {
        List<ActiveModel> list = new ArrayList<>(getActiveModels());
        list.removeIf(m -> provider.name().equals(m.provider) && modelId.equals(m.modelId));
        setActiveModels(list);
    }

    /** Moves the model at {@code index} one position higher in the priority order. */
    public void moveUp(int index) {
        List<ActiveModel> list = new ArrayList<>(getActiveModels());
        if (index <= 0 || index >= list.size()) return;
        ActiveModel tmp = list.get(index - 1);
        list.set(index - 1, list.get(index));
        list.set(index, tmp);
        setActiveModels(list);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Default active-model list when the user hasn't configured anything.
     * Ordered by speed + quality for Sketchware use cases.
     */
    private List<ActiveModel> buildDefaultActiveModels() {
        return new ArrayList<>(Arrays.asList(
                new ActiveModel(AiProvider.GROQ,            AiPreferences.DEFAULT_GROQ_MODEL),
                new ActiveModel(AiProvider.SAMBANOVA,       AiPreferences.DEFAULT_SAMBANOVA_MODEL),
                new ActiveModel(AiProvider.TOGETHER,        AiPreferences.DEFAULT_TOGETHER_MODEL),
                new ActiveModel(AiProvider.GEMINI,          AiPreferences.DEFAULT_GEMINI_MODEL),
                new ActiveModel(AiProvider.ANTHROPIC,       AiPreferences.DEFAULT_ANTHROPIC_MODEL),
                new ActiveModel(AiProvider.DEEPSEEK,        AiPreferences.DEFAULT_DEEPSEEK_MODEL)
        ));
    }

    /** Returns true for providers that work without an API key. */
    private boolean isNoKeyProvider(AiProvider p) {
        return p == AiProvider.LOCAL_LLM;
    }
}
