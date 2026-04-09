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

    public static final String DEFAULT_SYSTEM_PROMPT =
            "You are the autonomous Sketchware Pro build agent embedded inside the IDE. "
                    + "You must create, inspect, edit, and repair real Sketchware-compatible Android projects using the available tools.\n\n"
                    + "Your outputs must stay grounded in the actual workspace state and tool results.\n\n"
                    + "Core responsibilities:\n"
                    + "- Build complete Android apps that remain compatible with Sketchware Pro storage, editors, and compiler flows.\n"
                    + "- Modify existing projects without corrupting their metadata, resources, generated sources, or visual editor data.\n"
                    + "- Diagnose compile failures, inspect generated artifacts, and apply targeted fixes.\n"
                    + "- Work across multiple workspace projects only when the workspace explicitly grants access.\n\n"
                    + "Behavior rules:\n"
                    + "1. Use tools for every state-changing action. Do not pretend a file or project exists if a tool has not created or confirmed it.\n"
                    + "2. Inspect before mutating. Understand the current project, activities, files, resources, and libraries before making deep changes.\n"
                    + "3. Prefer small verifiable steps when the task is complex. After each material step, reassess from tool outputs.\n"
                    + "4. Keep Java, XML, manifest, resources, and libraries production-grade and internally consistent.\n"
                    + "5. When a tool returns an error, reason from the error, then apply the narrowest corrective action.\n"
                    + "6. Preserve user intent, naming, package structure, and existing functionality unless the user asks for a redesign.\n"
                    + "7. Treat Sketchware-specific data files, activity metadata, and project settings as first-class constraints.\n"
                    + "8. Do not end with vague promises. Finish with concrete completed work, remaining blockers, or the next exact tool-backed step.\n"
                    + "9. Ask clarifying questions only when a decision materially changes app behavior, architecture, or destructive operations.\n"
                    + "10. Optimize for compilable, maintainable, mobile-friendly Android apps, not toy examples.";

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

    public void setApiKey(@NonNull AiProvider provider, @NonNull String key) {
        prefs.edit().putString(KEY_API_KEY_PREFIX + provider.name(), key).apply();
    }

    @Nullable
    public String getApiKey(@NonNull AiProvider provider) {
        return prefs.getString(KEY_API_KEY_PREFIX + provider.name(), null);
    }

    public boolean hasApiKey(@NonNull AiProvider provider) {
        if (!provider.requiresApiKey()) {
            return true;
        }
        String key = getApiKey(provider);
        return key != null && !key.isEmpty();
    }

    public void clearApiKey(@NonNull AiProvider provider) {
        prefs.edit().remove(KEY_API_KEY_PREFIX + provider.name()).apply();
    }

    public void setCachedModels(@NonNull AiProvider provider, @NonNull List<ModelInfo> models) {
        String json = gson.toJson(models);
        prefs.edit().putString(KEY_CACHED_MODELS_PREFIX + provider.name(), json).apply();
    }

    @NonNull
    public List<ModelInfo> getCachedModels(@NonNull AiProvider provider) {
        String json = prefs.getString(KEY_CACHED_MODELS_PREFIX + provider.name(), null);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            Type listType = new TypeToken<List<ModelInfo>>() {}.getType();
            List<ModelInfo> models = gson.fromJson(json, listType);
            return models != null ? models : new ArrayList<>();
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
        return prefs.getString(KEY_SELECTED_MODEL_PREFIX + provider.name(), null);
    }

    public void setSelectedProvider(@NonNull AiProvider provider) {
        prefs.edit().putString(KEY_SELECTED_PROVIDER, provider.name()).apply();
    }

    @NonNull
    public AiProvider getSelectedProvider() {
        String key = prefs.getString(KEY_SELECTED_PROVIDER, null);
        if (key != null) {
            AiProvider provider = AiProvider.fromName(key);
            if (provider != null) {
                return provider;
            }
        }
        return AiProvider.GEMINI;
    }

    public void setSystemPrompt(@NonNull String prompt) {
        prefs.edit().putString(KEY_SYSTEM_PROMPT, prompt).apply();
    }

    @NonNull
    public String getSystemPrompt() {
        return prefs.getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT);
    }
}
