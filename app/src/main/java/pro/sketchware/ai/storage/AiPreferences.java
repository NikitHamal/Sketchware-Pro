package pro.sketchware.ai.storage;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.ModelInfo;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class AiPreferences {

    private static final String PREFS_NAME = "ai_preferences";
    private static final String KEY_API_KEY_PREFIX = "api_key_";
    private static final String KEY_CACHED_MODELS_PREFIX = "cached_models_";
    private static final String KEY_SELECTED_MODEL_PREFIX = "selected_model_";
    private static final String KEY_SELECTED_PROVIDER = "selected_provider";
    private static final String KEY_SYSTEM_PROMPT = "system_prompt";
    private static final String KEY_TEMPERATURE = "ai_temperature";
    private static final String KEY_MAX_TOKENS = "ai_max_tokens";
    private static final String KEY_AUTO_FIX_ON_ERROR = "ai_auto_fix_on_error";

    /** Prefix for provider enabled toggle — must match AiSettingsActivity.PREF_ENABLED */
    public static final String KEY_PROVIDER_ENABLED = "provider_enabled_";
    
    /** Morph (MORF) code-edit AI — used to refine AI-generated layouts */
    public static final String KEY_MORPH_API_KEY    = "morph_api_key";
    public static final String KEY_MORPH_ENABLED    = "morph_enabled";
    public static final String KEY_MORPH_FOR_LAYOUT = "morph_for_layout";

    /** Optional: dedicated provider for layout generation (Groq recommended — fast). */
    public static final String KEY_LAYOUT_AI_PROVIDER = "layout_ai_provider";
    
    /** Profile-specific model and provider settings */
    private static final String KEY_PROFILE_MODEL_PREFIX = "profile_model_";
    private static final String KEY_PROFILE_PROVIDER_PREFIX = "profile_provider_";

    /** الموديلات الافتراضية الذكية للمزودات (برمجية وقوية) */
    public static final String DEFAULT_DEEPINFRA_MODEL        = "google/gemma-3-27b-it";
    public static final String DEFAULT_GROQ_MODEL             = "compound-beta-mini";
    public static final String DEFAULT_TOGETHER_MODEL         = "google/gemma-3-27b-it";
    public static final String DEFAULT_SAMBANOVA_MODEL        = "Gemma-3-27B-IT";
    public static final String DEFAULT_DEEPSEEK_MODEL  = "deepseek-chat";
    public static final String DEFAULT_ANTHROPIC_MODEL  = "claude-sonnet-4-5"; // Claude Sonnet 4.5 — best for code
    public static final String DEFAULT_OPENAI_MODEL     = "gpt-4o-mini";
    public static final String DEFAULT_GEMINI_MODEL     = "gemini-2.0-flash";
    public static final String DEFAULT_OLLAMA_URL      = "http://localhost:11434";
    public static final String DEFAULT_OLLAMA_MODEL    = "qwen2.5-coder:1.5b";

public static final String DEFAULT_SYSTEM_PROMPT = pro.sketchware.ai.prompts.SystemPrompts.BASE_SYSTEM_PROMPT;

    private static volatile AiPreferences instance;
    private final SharedPreferences prefs;
    private final Gson gson;

    private AiPreferences(@NonNull Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    @NonNull
    public static AiPreferences getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (AiPreferences.class) {
                if (instance == null) {
                    instance = new AiPreferences(context);
                }
            }
        }
        return instance;
    }

    @NonNull
    public SharedPreferences prefs() { return prefs; }

    public void setApiKey(@NonNull AiProvider provider, @NonNull String key) {
        prefs.edit().putString(KEY_API_KEY_PREFIX + provider.name(), key).apply();
    }

    @Nullable
    public String getApiKey(@NonNull AiProvider provider) {
        return prefs.getString(KEY_API_KEY_PREFIX + provider.name(), null);
    }

    public boolean hasApiKey(@NonNull AiProvider provider) {
        if (!provider.requiresApiKey()) return true;
        String key = getApiKey(provider);
        return key != null && !key.isEmpty();
    }

    /**
     * Returns true if the provider has been toggled ON by the user in AI Settings.
     * SAMBANOVA is enabled by default.
     */
    public boolean isProviderEnabled(@NonNull AiProvider provider) {
        boolean defaultEnabled = provider == AiProvider.SAMBANOVA;
        return prefs.getBoolean(KEY_PROVIDER_ENABLED + provider.name(), defaultEnabled);
    }

    public void clearApiKey(@NonNull AiProvider provider) {
        prefs.edit().remove(KEY_API_KEY_PREFIX + provider.name()).apply();
    }

    public void setCachedModels(@NonNull AiProvider provider, @NonNull List<ModelInfo> models) {
        prefs.edit().putString(KEY_CACHED_MODELS_PREFIX + provider.name(), gson.toJson(models)).apply();
    }

    @NonNull
    public List<ModelInfo> getCachedModels(@NonNull AiProvider provider) {
        String json = prefs.getString(KEY_CACHED_MODELS_PREFIX + provider.name(), null);
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            Type t = new TypeToken<List<ModelInfo>>() {}.getType();
            List<ModelInfo> m = gson.fromJson(json, t);
            return m != null ? m : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void clearCachedModels(@NonNull AiProvider provider) {
        prefs.edit().remove(KEY_CACHED_MODELS_PREFIX + provider.name()).apply();
    }

    public void setSelectedModel(@NonNull AiProvider provider, @NonNull String modelId) {
        prefs.edit().putString(KEY_SELECTED_MODEL_PREFIX + provider.name(), modelId).apply();
    }

    @Nullable
    public String getSelectedModel(@NonNull AiProvider provider) {
        String saved = prefs.getString(KEY_SELECTED_MODEL_PREFIX + provider.name(), null);
        if (saved != null && !saved.isEmpty()) return saved;
        
        switch (provider) {
            case DEEPINFRA:        return DEFAULT_DEEPINFRA_MODEL;
            case GROQ:             return DEFAULT_GROQ_MODEL;
            case DEEPSEEK:         return DEFAULT_DEEPSEEK_MODEL;
            case ANTHROPIC:        return DEFAULT_ANTHROPIC_MODEL;
            case OPENAI:           return DEFAULT_OPENAI_MODEL;
            case GEMINI:           return DEFAULT_GEMINI_MODEL;
            case TOGETHER:         return DEFAULT_TOGETHER_MODEL;
            case SAMBANOVA:        return DEFAULT_SAMBANOVA_MODEL;
            default:               return null;
        }
    }

    public void setSelectedProvider(@NonNull AiProvider provider) {
        prefs.edit().putString(KEY_SELECTED_PROVIDER, provider.name()).apply();
    }

    @NonNull
    public AiProvider getSelectedProvider() {
        String key = prefs.getString(KEY_SELECTED_PROVIDER, null);
        if (key != null) {
            AiProvider p = AiProvider.fromName(key);
            if (p != null) return p;
        }
        return AiProvider.GROQ; // Default: Groq (unlimited, fast — matches Sketchware-IA default)
    }

    public void setSystemPrompt(@NonNull String prompt) {
        prefs.edit().putString(KEY_SYSTEM_PROMPT, prompt).apply();
    }

    @NonNull
    public String getSystemPrompt() {
        return prefs.getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT);
    }

    public float getTemperature() {
        return prefs.getFloat(KEY_TEMPERATURE, 0.7f);
    }

    public void setTemperature(float temperature) {
        prefs.edit().putFloat(KEY_TEMPERATURE, Math.max(0f, Math.min(1f, temperature))).apply();
    }

    public int getMaxTokens() {
        return prefs.getInt(KEY_MAX_TOKENS, 4096);
    }

    public void setMaxTokens(int maxTokens) {
        prefs.edit().putInt(KEY_MAX_TOKENS, Math.max(256, Math.min(8192, maxTokens))).apply();
    }

    public boolean isAutoFixOnError() {
        return prefs.getBoolean(KEY_AUTO_FIX_ON_ERROR, true);
    }

    public void setAutoFixOnError(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_FIX_ON_ERROR, enabled).apply();
    }

    // ── Morph (MORF) Layout Refinement ─────────────────────────────────────

    public String getMorphApiKey() {
        return prefs.getString(KEY_MORPH_API_KEY, "");
    }

    public void setMorphApiKey(@NonNull String key) {
        prefs.edit().putString(KEY_MORPH_API_KEY, key.trim()).apply();
    }

    /** True if Morph is enabled globally (has API key + user turned it on). */
    public boolean isMorphEnabled() {
        return prefs.getBoolean(KEY_MORPH_ENABLED, false)
                && !getMorphApiKey().isEmpty();
    }

    /** True if Morph should automatically refine AI-generated layouts. */
    public boolean isMorphForLayoutEnabled() {
        return isMorphEnabled()
                && prefs.getBoolean(KEY_MORPH_FOR_LAYOUT, false);
    }

    // ── Profile-Specific Settings ───────────────────────────────────────────

    public void setProfileModel(String profile, String modelId) {
        prefs.edit().putString(KEY_PROFILE_MODEL_PREFIX + profile, modelId).apply();
    }

    @Nullable
    public String getProfileModel(String profile) {
        return prefs.getString(KEY_PROFILE_MODEL_PREFIX + profile, null);
    }

    public void setProfileProvider(String profile, AiProvider provider) {
        prefs.edit().putString(KEY_PROFILE_PROVIDER_PREFIX + profile, provider.name()).apply();
    }

    @Nullable
    public AiProvider getProfileProvider(String profile) {
        String name = prefs.getString(KEY_PROFILE_PROVIDER_PREFIX + profile, null);
        if (name != null) {
            AiProvider p = AiProvider.fromName(name);
            if (p != null) return p;
        }
        return null;
    }
}
