package pro.sketchware.ai.activities;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.ai.api.AiApiClient;
import pro.sketchware.ai.api.GeminiApiClient;
import pro.sketchware.ai.api.NvidiaApiClient;
import pro.sketchware.ai.api.OpenRouterApiClient;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.ModelInfo;
import pro.sketchware.ai.storage.AiPreferences;
import pro.sketchware.databinding.ActivityAiSettingsBinding;

public class AiSettingsActivity extends AppCompatActivity {

    private ActivityAiSettingsBinding binding;
    private AiPreferences preferences;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * Stores the default system prompt text so we can compare against it
     * when deciding whether to populate the input field.
     */
    private String defaultSystemPrompt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityAiSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        preferences = AiPreferences.getInstance(this);

        // Capture the default prompt (what getSystemPrompt returns when nothing is stored)
        defaultSystemPrompt = preferences.getSystemPrompt();

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        loadApiKeys();
        setupSaveListeners();
        setupRefreshButtons();
        setupSystemPrompt();
        updateModelCounts();
    }

    private void loadApiKeys() {
        String geminiKey = preferences.getApiKey(AiProvider.GEMINI);
        String nvidiaKey = preferences.getApiKey(AiProvider.NVIDIA);
        String openrouterKey = preferences.getApiKey(AiProvider.OPENROUTER);

        if (geminiKey != null) binding.inputGeminiKey.setText(geminiKey);
        if (nvidiaKey != null) binding.inputNvidiaKey.setText(nvidiaKey);
        if (openrouterKey != null) binding.inputOpenrouterKey.setText(openrouterKey);
    }

    private void setupSaveListeners() {
        binding.inputGeminiKey.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) saveApiKey(AiProvider.GEMINI, getInputText(binding.inputGeminiKey));
        });
        binding.inputNvidiaKey.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) saveApiKey(AiProvider.NVIDIA, getInputText(binding.inputNvidiaKey));
        });
        binding.inputOpenrouterKey.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) saveApiKey(AiProvider.OPENROUTER, getInputText(binding.inputOpenrouterKey));
        });
    }

    private void saveApiKey(AiProvider provider, String key) {
        String existing = preferences.getApiKey(provider);
        if (key.equals(existing != null ? existing : "")) return;

        if (key.isEmpty()) {
            preferences.clearApiKey(provider);
            preferences.clearCachedModels(provider);
            updateModelCounts();
        } else {
            preferences.setApiKey(provider, key);
            fetchModels(provider);
        }
    }

    private String getInputText(com.google.android.material.textfield.TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private void setupRefreshButtons() {
        binding.btnRefreshGemini.setOnClickListener(v -> {
            saveApiKey(AiProvider.GEMINI, getInputText(binding.inputGeminiKey));
            fetchModels(AiProvider.GEMINI);
        });
        binding.btnRefreshNvidia.setOnClickListener(v -> {
            saveApiKey(AiProvider.NVIDIA, getInputText(binding.inputNvidiaKey));
            fetchModels(AiProvider.NVIDIA);
        });
        binding.btnRefreshOpenrouter.setOnClickListener(v -> {
            saveApiKey(AiProvider.OPENROUTER, getInputText(binding.inputOpenrouterKey));
            fetchModels(AiProvider.OPENROUTER);
        });
    }

    private void fetchModels(AiProvider provider) {
        String apiKey = preferences.getApiKey(provider);
        if (apiKey == null || apiKey.isEmpty()) {
            Toast.makeText(this,
                    "Set an API key for " + provider.getDisplayName() + " first",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        setModelCountText(provider, "Fetching models...");

        executor.execute(() -> {
            try {
                AiApiClient client = createClient(provider, apiKey);
                if (client == null) return;

                List<ModelInfo> models = client.fetchModels();
                preferences.setCachedModels(provider, models);
                client.shutdown();

                runOnUiThread(() -> {
                    setModelCountText(provider, models.size() + " models available");
                    Toast.makeText(this,
                            "Loaded " + models.size() + " models from "
                                    + provider.getDisplayName(),
                            Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setModelCountText(provider, "Failed to fetch models");
                    Toast.makeText(this,
                            "Error: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private AiApiClient createClient(AiProvider provider, String apiKey) {
        switch (provider) {
            case GEMINI:
                return new GeminiApiClient(apiKey);
            case NVIDIA:
                return new NvidiaApiClient(apiKey);
            case OPENROUTER:
                return new OpenRouterApiClient(apiKey);
            default:
                return null;
        }
    }

    private void updateModelCounts() {
        for (AiProvider provider : AiProvider.values()) {
            List<ModelInfo> cached = preferences.getCachedModels(provider);
            if (cached != null && !cached.isEmpty()) {
                setModelCountText(provider, cached.size() + " models available");
            } else if (preferences.hasApiKey(provider)) {
                setModelCountText(provider, "No models loaded - tap refresh");
            } else {
                setModelCountText(provider, "No API key set");
            }
        }
    }

    private void setModelCountText(AiProvider provider, String text) {
        switch (provider) {
            case GEMINI:
                binding.geminiModelsCount.setText(text);
                break;
            case NVIDIA:
                binding.nvidiaModelsCount.setText(text);
                break;
            case OPENROUTER:
                binding.openrouterModelsCount.setText(text);
                break;
        }
    }

    private void setupSystemPrompt() {
        String systemPrompt = preferences.getSystemPrompt();

        // Only populate the field if the user has customized the prompt
        if (!systemPrompt.equals(defaultSystemPrompt)) {
            binding.inputSystemPrompt.setText(systemPrompt);
        }

        binding.inputSystemPrompt.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                saveSystemPrompt();
            }
        });

        binding.btnResetSystemPrompt.setOnClickListener(v -> {
            // Clear the stored prompt so that getSystemPrompt() returns the built-in default
            preferences.setSystemPrompt(defaultSystemPrompt);
            binding.inputSystemPrompt.setText("");
            Toast.makeText(this, "System prompt reset to default", Toast.LENGTH_SHORT).show();
        });
    }

    private void saveSystemPrompt() {
        String text = binding.inputSystemPrompt.getText() != null
                ? binding.inputSystemPrompt.getText().toString().trim() : "";
        if (!text.isEmpty()) {
            preferences.setSystemPrompt(text);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Persist any unsaved API keys when the activity loses focus
        saveApiKey(AiProvider.GEMINI, getInputText(binding.inputGeminiKey));
        saveApiKey(AiProvider.NVIDIA, getInputText(binding.inputNvidiaKey));
        saveApiKey(AiProvider.OPENROUTER, getInputText(binding.inputOpenrouterKey));
        saveSystemPrompt();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
