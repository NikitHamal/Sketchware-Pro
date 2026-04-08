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
            "You are an expert AI assistant integrated into Sketchware Pro, an Android app development IDE for mobile devices. "
                    + "Your role is to help users create, edit, and improve Android applications.\n\n"
                    + "You have deep knowledge of:\n"
                    + "- Android SDK, Android components (Activities, Fragments, Services, BroadcastReceivers, ContentProviders)\n"
                    + "- Java and XML for Android development\n"
                    + "- Android UI design (Views, Layouts, RecyclerView, Material Design components)\n"
                    + "- Sketchware Pro's block-based programming model and its project structure\n"
                    + "- Common Android libraries and dependencies\n"
                    + "- Android Manifest configuration, permissions, and intent filters\n"
                    + "- Gradle build configuration and dependency management\n"
                    + "- Android resource management (drawables, strings, colors, dimensions, styles, themes)\n\n"
                    + "When assisting the user:\n"
                    + "1. Write clean, efficient, and well-structured code.\n"
                    + "2. Follow Android best practices and Material Design guidelines.\n"
                    + "3. Provide clear explanations for your changes and suggestions.\n"
                    + "4. Consider device compatibility and performance implications.\n"
                    + "5. When generating layouts, use appropriate ViewGroups and ensure responsive design.\n"
                    + "6. Handle errors gracefully and suggest proper exception handling.\n"
                    + "7. When modifying existing code, preserve the user's existing logic and style where possible.\n"
                    + "8. Suggest improvements and optimizations when you spot potential issues.\n"
                    + "9. If a task is ambiguous, ask clarifying questions before proceeding.\n"
                    + "10. Always consider the context of the current workspace and project when providing assistance.";

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

    // --- API Key Methods ---

    public void setApiKey(@NonNull AiProvider provider, @NonNull String key) {
        prefs.edit().putString(KEY_API_KEY_PREFIX + provider.name(), key).apply();
    }

    @Nullable
    public String getApiKey(@NonNull AiProvider provider) {
        return prefs.getString(KEY_API_KEY_PREFIX + provider.name(), null);
    }

    public boolean hasApiKey(@NonNull AiProvider provider) {
        String key = getApiKey(provider);
        return key != null && !key.isEmpty();
    }

    public void clearApiKey(@NonNull AiProvider provider) {
        prefs.edit().remove(KEY_API_KEY_PREFIX + provider.name()).apply();
    }

    // --- Cached Models Methods ---

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

    // --- Selected Model Methods ---

    public void setSelectedModel(@NonNull AiProvider provider, @NonNull String modelId) {
        prefs.edit().putString(KEY_SELECTED_MODEL_PREFIX + provider.name(), modelId).apply();
    }

    @Nullable
    public String getSelectedModel(@NonNull AiProvider provider) {
        return prefs.getString(KEY_SELECTED_MODEL_PREFIX + provider.name(), null);
    }

    // --- Selected Provider Methods ---

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

    // --- System Prompt Methods ---

    public void setSystemPrompt(@NonNull String prompt) {
        prefs.edit().putString(KEY_SYSTEM_PROMPT, prompt).apply();
    }

    @NonNull
    public String getSystemPrompt() {
        return prefs.getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT);
    }
}
