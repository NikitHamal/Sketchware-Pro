package pro.sketchware.ai.api;

import android.content.Context;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.storage.AiPreferences;

/**
 * Centralized factory for creating AI API clients.
 * Supported: GEMINI, OPENAI, ANTHROPIC, DEEPSEEK, XAI_GROK, GROQ, NVIDIA,
 * OPENROUTER, DEEPINFRA, TOGETHER, HUGGINGFACE, CEREBRAS,
 * SAMBANOVA, LOCAL_LLM.
 */
public final class AiClientFactory {

    private AiClientFactory() {}

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
            case GROQ:             return new GroqApiClient(apiKey);
            case TOGETHER:         return new TogetherApiClient(apiKey);
            case HUGGINGFACE:      return new HuggingFaceApiClient(apiKey);
            case CEREBRAS:         return new CerebrasApiClient(apiKey);
            case SAMBANOVA:        return new SambaNovaApiClient(apiKey);
            case LOCAL_LLM: {
                String url      = preferences.prefs().getString("ai_local_llm_url",       "http://localhost:1234");
                String model    = preferences.prefs().getString("ai_local_llm_model",      "local-model");
                String filePath = preferences.prefs().getString("ai_local_llm_file_path", "");
                return new LocalLlmApiClient(url, model, filePath.isEmpty() ? null : filePath);
            }
            default: return null;
        }
    }

    public static String getCompatibilityNote(AiProvider provider) {
        switch (provider) {
            case GEMINI:           return "\u2705 Stable \u2014 use gemini-2.0-flash or gemini-2.5-pro";
            case OPENAI:           return "\u2705 Stable \u2014 use gpt-4o-mini for best cost/performance";
            case ANTHROPIC:        return "\u2705 Stable \u2014 prompt caching enabled (saves ~90% tokens)";
            case GROQ:             return "\u2705 Stable \u221e \u2014 fastest inference, use llama-3.3-70b-versatile";
            case DEEPSEEK:         return "\u2705 Stable \u2014 deepseek-chat is very cost-effective";
            case XAI_GROK:         return "\u2705 Stable \u2014 use grok-3-mini for coding tasks";
            case CEREBRAS:         return "\u2705 Stable \u2014 extremely fast, use llama3.1-8b for quick tasks";
            case TOGETHER:         return "\u2705 Stable \u2014 supports Gemma 3 27B, Llama 3.3, DeepSeek R1";
            case SAMBANOVA:        return "\u2705 Stable \u2014 Gemma 3/2, Llama 4, DeepSeek R1 free at cloud.sambanova.ai";
            case NVIDIA:           return "\u26A0\uFE0F May have rate limits \u2014 use meta/llama-3.3-70b-instruct";
            case OPENROUTER:       return "\u26A0\uFE0F Quality varies by sub-model \u2014 prefix model with provider/";
            case DEEPINFRA:        return "\u26A0\uFE0F Supports Gemma 2/3 \u2014 check model ID matches exactly";
            case HUGGINGFACE:      return "\u26A0\uFE0F Free tier has rate limits \u2014 set model to specific HF model ID";
            case LOCAL_LLM:        return "\u2139\uFE0F Requires local server (LM Studio / Ollama) \u2014 supports Gemma 2/3/4";
            default:               return "Unknown provider";
        }
    }

    public static boolean requiresApiKey(AiProvider provider) {
        return provider != AiProvider.LOCAL_LLM;
    }
}
