package pro.sketchware.ai.api;

import android.content.Context;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.storage.AiPreferences;

/**
 * Centralized factory for creating AI API clients.
 *
 * <p>This class eliminates the duplicated provider-switch logic that previously existed in
 * both {@link pro.sketchware.ai.engine.AgentExecutor} and
 * {@link pro.sketchware.ai.activities.AiSettingsActivity}.
 *
 *  * All provider instantiation should go through this factory to ensure a single source of truth.
 *
 * <p>Supported providers: GEMINI, OPENAI, ANTHROPIC, DEEPSEEK, XAI_GROK, GROQ, NVIDIA,
 * OPENROUTER, DEEPINFRA, PAXSENIX, AIRFORCE, MANUS, TOGETHER, HUGGINGFACE, CEREBRAS,
 * GOOGLE_AI_STUDIO, LOCAL_LLM.
 */
public final class AiClientFactory {

    private AiClientFactory() {}

    /**
     * Creates and returns an {@link AiApiClient} for the given provider and API key.
     *
     * @param context  Android context (used to read Local LLM preferences)
     * @param provider the AI provider to create a client for
     * @param apiKey   the API key for authentication (may be empty for no-auth providers)
     * @return a configured {@link AiApiClient}, or {@code null} if the provider is unknown
     */
    public static AiApiClient createClient(Context context, AiProvider provider, String apiKey) {
        AiPreferences preferences = AiPreferences.getInstance(context);
        switch (provider) {
            case GEMINI:           return new GeminiApiClient(apiKey);
            case OPENAI:           return new OpenAiApiClient(apiKey);
            case ANTHROPIC:        return new AnthropicApiClient(apiKey);
            case DEEPSEEK:         return new DeepSeekApiClient(apiKey);
            case XAI_GROK:         return new XaiGrokApiClient(apiKey);
            case NVIDIA:           return new NvidiaApiClient(apiKey);
            case OPENROUTER:       return new OpenRouterApiClient(apiKey);
            case DEEPINFRA:        return new DeepInfraApiClient(apiKey);
            case PAXSENIX:         return new PaxsenixApiClient(apiKey);
            case AIRFORCE:         return new AirForceApiClient(apiKey);
            case GROQ:             return new GroqApiClient(apiKey);
            case MANUS:            return new ManusApiClient(apiKey);
            case TOGETHER:         return new TogetherApiClient(apiKey);
            case HUGGINGFACE:      return new HuggingFaceApiClient(apiKey);
            case CEREBRAS:         return new CerebrasApiClient(apiKey);
            case GOOGLE_AI_STUDIO: return new GoogleAiStudioApiClient(apiKey);
            case LOCAL_LLM: {
                String url       = preferences.prefs().getString("ai_local_llm_url",        "http://localhost:1234");
                String model     = preferences.prefs().getString("ai_local_llm_model",       "local-model");
                // File path takes priority over server URL (File Mode)
                String filePath  = preferences.prefs().getString("ai_local_llm_file_path",  "");
                return new LocalLlmApiClient(url, model, filePath.isEmpty() ? null : filePath);
            }
            default: return null;
        }
    }

    // ── Provider Compatibility Audit ────────────────────────────────────────

    /**
     * Returns a human-readable compatibility note for a provider.
     * These notes are shown in AiSettings to warn users about known issues
     * and help them choose a working configuration.
     *
     * Updated based on provider API compatibility audit (April 2026):
     */
    public static String getCompatibilityNote(AiProvider provider) {
        switch (provider) {
            case GEMINI:
                return "✅ Stable — use gemini-2.0-flash or gemini-2.5-pro";
            case OPENAI:
                return "✅ Stable — use gpt-4o-mini for best cost/performance";
            case ANTHROPIC:
                return "✅ Stable — prompt caching enabled (saves ~90% tokens)";
            case GROQ:
                return "✅ Stable — fastest inference, use llama-3.3-70b-versatile";
            case DEEPSEEK:
                return "✅ Stable — deepseek-chat is very cost-effective";
            case XAI_GROK:
                return "✅ Stable — use grok-3-mini for coding tasks";
            case CEREBRAS:
                return "✅ Stable — extremely fast, use llama3.1-8b for quick tasks";
            case TOGETHER:
                return "✅ Stable — use meta-llama/Meta-Llama-3.3-70B-Instruct-Turbo";
            case GOOGLE_AI_STUDIO:
                return "✅ Stable — Gemma 3 models free via aistudio.google.com key";
            case NVIDIA:
                return "⚠️ May have rate limits — use meta/llama-3.3-70b-instruct";
            case OPENROUTER:
                return "⚠️ Quality varies by sub-model — prefix model with provider/";
            case DEEPINFRA:
                return "⚠️ Some models require specific request format — test first";
            case HUGGINGFACE:
                return "⚠️ Free tier has rate limits — set model to a specific HF model ID";
            case PAXSENIX:
                return "⚠️ Unofficial proxy — may be unstable, use for testing only";
            case AIRFORCE:
                return "⚠️ Unofficial proxy — no SLA, avoid for production use";
            case MANUS:
                return "ℹ️ Requires active Manus subscription at manus.im";
            case LOCAL_LLM:
                return "ℹ️ Requires local server (LM Studio / Ollama) running on device or LAN";
            default:
                return "Unknown provider";
        }
    }

    /**
     * Returns {@code true} if the provider requires a non-empty API key.
     * Free/proxy providers (PAXSENIX, AIRFORCE) and LOCAL_LLM don't need keys.
     */
    public static boolean requiresApiKey(AiProvider provider) {
        switch (provider) {
            case PAXSENIX:
            case AIRFORCE:
            case LOCAL_LLM:
                return false;
            default:
                return true;
        }
    }
}
