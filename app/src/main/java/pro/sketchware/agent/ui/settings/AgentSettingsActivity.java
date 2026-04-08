package pro.sketchware.agent.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

import pro.sketchware.R;
import pro.sketchware.agent.data.AgentDatabase;
import pro.sketchware.agent.provider.AIProvider;
import pro.sketchware.agent.provider.ProviderRegistry;
import pro.sketchware.databinding.ActivityAgentSettingsBinding;

public class AgentSettingsActivity extends BaseAppCompatActivity {

    private ActivityAgentSettingsBinding binding;
    private AgentDatabase db;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityAgentSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = AgentDatabase.getInstance(this);

        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());

        setupProviders();
    }

    private void setupProviders() {
        List<AIProvider> providers = ProviderRegistry.getAllProviders();
        LinearLayout container = binding.providersContainer;
        container.removeAllViews();

        for (AIProvider provider : providers) {
            View itemView = LayoutInflater.from(this).inflate(R.layout.item_provider_setting, container, false);

            TextView nameText = itemView.findViewById(R.id.provider_name);
            TextView statusText = itemView.findViewById(R.id.provider_status);
            TextInputEditText apiKeyInput = itemView.findViewById(R.id.input_api_key);
            MaterialButton saveBtn = itemView.findViewById(R.id.btn_save_key);
            MaterialButton refreshBtn = itemView.findViewById(R.id.btn_refresh_models);
            TextView modelsCount = itemView.findViewById(R.id.models_count);

            nameText.setText(provider.getDisplayName());

            android.util.TypedValue tv = new android.util.TypedValue();
            String existingKey = db.getApiKey(provider.getId());
            if (!existingKey.isEmpty()) {
                apiKeyInput.setText(existingKey);
                statusText.setText("Active");
                getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true);
                statusText.setTextColor(tv.data);
            } else {
                statusText.setText("Not configured");
                getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, tv, true);
                statusText.setTextColor(tv.data);
            }

            updateModelsCount(provider.getId(), modelsCount);

            saveBtn.setOnClickListener(v -> {
                String key = apiKeyInput.getText() != null ? apiKeyInput.getText().toString().trim() : "";
                db.saveProviderSetting(provider.getId(), key, !key.isEmpty());
                if (!key.isEmpty()) {
                    statusText.setText("Active");
                    android.util.TypedValue tv2 = new android.util.TypedValue();
                    getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv2, true);
                    statusText.setTextColor(tv2.data);
                    // Fetch models after saving key
                    refreshBtn.setEnabled(false);
                    provider.fetchModels(key, models -> {
                        refreshBtn.setEnabled(true);
                        if (!models.isEmpty()) {
                            db.cacheModels(provider.getId(), models);
                        }
                        updateModelsCount(provider.getId(), modelsCount);
                    });
                } else {
                    statusText.setText("Not configured");
                    android.util.TypedValue tv3 = new android.util.TypedValue();
                    getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, tv3, true);
                    statusText.setTextColor(tv3.data);
                }
            });

            refreshBtn.setOnClickListener(v -> {
                String key = db.getApiKey(provider.getId());
                if (key.isEmpty()) return;
                refreshBtn.setEnabled(false);
                provider.fetchModels(key, models -> {
                    refreshBtn.setEnabled(true);
                    if (!models.isEmpty()) {
                        db.cacheModels(provider.getId(), models);
                    }
                    updateModelsCount(provider.getId(), modelsCount);
                });
            });

            container.addView(itemView);
        }
    }

    private void updateModelsCount(String providerId, TextView modelsCount) {
        List<AgentDatabase.ModelInfo> cached = db.getCachedModels(providerId);
        if (cached.isEmpty()) {
            modelsCount.setText("No models cached");
        } else {
            modelsCount.setText(cached.size() + " model" + (cached.size() != 1 ? "s" : "") + " cached");
        }
    }
}
