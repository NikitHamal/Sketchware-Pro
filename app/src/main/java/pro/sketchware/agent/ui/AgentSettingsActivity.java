package pro.sketchware.agent.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.agent.AgentModelService;
import pro.sketchware.agent.AgentProvider;
import pro.sketchware.agent.AgentRepository;
import pro.sketchware.databinding.ActivityAgentSettingsBinding;

public class AgentSettingsActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ActivityAgentSettingsBinding binding;
    private AgentRepository repository;
    private AgentModelService modelService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAgentSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new AgentRepository();
        modelService = new AgentModelService(repository);

        binding.topAppBar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        bindProviderState(AgentProvider.GEMINI);
        bindProviderState(AgentProvider.NVIDIA);
        bindProviderState(AgentProvider.OPENROUTER);

        binding.saveGeminiButton.setOnClickListener(v -> saveKey(AgentProvider.GEMINI));
        binding.saveNvidiaButton.setOnClickListener(v -> saveKey(AgentProvider.NVIDIA));
        binding.saveOpenrouterButton.setOnClickListener(v -> saveKey(AgentProvider.OPENROUTER));

        binding.refreshGeminiButton.setOnClickListener(v -> refreshModels(AgentProvider.GEMINI));
        binding.refreshNvidiaButton.setOnClickListener(v -> refreshModels(AgentProvider.NVIDIA));
        binding.refreshOpenrouterButton.setOnClickListener(v -> refreshModels(AgentProvider.OPENROUTER));
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void saveKey(@NonNull AgentProvider provider) {
        AgentRepository.ProviderState state = repository.getProviderState(provider);
        String newKey = getKeyInput(provider);
        boolean shouldRefresh = !TextUtils.equals(state.apiKey, newKey) && !TextUtils.isEmpty(newKey);
        state.apiKey = newKey;
        repository.saveProviderState(provider, state);
        bindProviderState(provider);
        if (shouldRefresh) {
            refreshModels(provider);
        }
    }

    private void refreshModels(@NonNull AgentProvider provider) {
        AgentRepository.ProviderState state = repository.getProviderState(provider);
        if (TextUtils.isEmpty(state.apiKey)) {
            showError("Add an API key for " + provider.displayName + " first.");
            return;
        }
        setLoading(provider, true);
        executor.execute(() -> {
            try {
                modelService.refreshModels(provider, state.apiKey);
                runOnUiThread(() -> {
                    setLoading(provider, false);
                    bindProviderState(provider);
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    setLoading(provider, false);
                    bindProviderState(provider);
                    showError(e.getMessage());
                });
            }
        });
    }

    private void bindProviderState(@NonNull AgentProvider provider) {
        AgentRepository.ProviderState state = repository.getProviderState(provider);
        setKeyInput(provider, state.apiKey);
        String message = TextUtils.isEmpty(state.apiKey)
                ? "API key not configured"
                : state.cachedModels.size() + " cached models"
                + (state.modelsUpdatedAt > 0 ? " • " + DateUtils.getRelativeTimeSpanString(state.modelsUpdatedAt) : "");
        getStatusView(provider).setText(message);
    }

    private void setLoading(@NonNull AgentProvider provider, boolean loading) {
        getSaveButton(provider).setEnabled(!loading);
        getRefreshButton(provider).setEnabled(!loading);
        getStatusView(provider).setText(loading ? "Refreshing models…" : getStatusView(provider).getText());
    }

    @NonNull
    private String getKeyInput(@NonNull AgentProvider provider) {
        return switch (provider) {
            case GEMINI -> Helper.getText(binding.geminiKeyInput).trim();
            case NVIDIA -> Helper.getText(binding.nvidiaKeyInput).trim();
            case OPENROUTER -> Helper.getText(binding.openrouterKeyInput).trim();
        };
    }

    private void setKeyInput(@NonNull AgentProvider provider, String value) {
        switch (provider) {
            case GEMINI -> binding.geminiKeyInput.setText(value);
            case NVIDIA -> binding.nvidiaKeyInput.setText(value);
            case OPENROUTER -> binding.openrouterKeyInput.setText(value);
        }
    }

    @NonNull
    private View getSaveButton(@NonNull AgentProvider provider) {
        return switch (provider) {
            case GEMINI -> binding.saveGeminiButton;
            case NVIDIA -> binding.saveNvidiaButton;
            case OPENROUTER -> binding.saveOpenrouterButton;
        };
    }

    @NonNull
    private View getRefreshButton(@NonNull AgentProvider provider) {
        return switch (provider) {
            case GEMINI -> binding.refreshGeminiButton;
            case NVIDIA -> binding.refreshNvidiaButton;
            case OPENROUTER -> binding.refreshOpenrouterButton;
        };
    }

    @NonNull
    private android.widget.TextView getStatusView(@NonNull AgentProvider provider) {
        return switch (provider) {
            case GEMINI -> binding.geminiStatus;
            case NVIDIA -> binding.nvidiaStatus;
            case OPENROUTER -> binding.openrouterStatus;
        };
    }

    private void showError(String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Agent settings")
                .setMessage(message)
                .setPositiveButton(R.string.common_word_ok, null)
                .show();
    }
}
