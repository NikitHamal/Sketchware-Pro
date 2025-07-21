package pro.sketchware.fragments.settings.api;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import pro.sketchware.activities.ai.config.ApiConfig;
import pro.sketchware.databinding.FragmentApiConfigBinding;
import a.a.a.qA;

public class ApiConfigFragment extends qA {

    private FragmentApiConfigBinding binding;
    private ApiConfig apiConfig;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentApiConfigBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        apiConfig = new ApiConfig(requireContext());
        
        setupToolbar();
        loadCurrentValues();
        setupClickListeners();
    }

    private void setupToolbar() {
        binding.toolbar.setTitle("API Configuration");
        binding.toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
    }

    private void loadCurrentValues() {
        binding.authorizationValue.setText(maskSensitiveData(apiConfig.getAuthorization()));
        binding.userAgentValue.setText(apiConfig.getUserAgent());
        binding.bxVValue.setText(apiConfig.getBxV());
        binding.sourceValue.setText(apiConfig.getSource());
        binding.timezoneValue.setText(apiConfig.getTimezone());
        binding.cookieValue.setText(maskSensitiveData(apiConfig.getCookie()));
        binding.bxUaValue.setText(maskSensitiveData(apiConfig.getBxUa()));
        binding.bxUmidtokenValue.setText(maskSensitiveData(apiConfig.getBxUmidtoken()));
    }

    private String maskSensitiveData(String data) {
        if (data == null || data.isEmpty()) {
            return "Not set";
        }
        if (data.length() <= 20) {
            return data.substring(0, Math.min(8, data.length())) + "...";
        }
        return data.substring(0, 8) + "..." + data.substring(data.length() - 8);
    }

    private void setupClickListeners() {
        binding.authorizationLayout.setOnClickListener(v -> showEditDialog("Authorization", apiConfig.getAuthorization(), apiConfig::setAuthorization));
        binding.userAgentLayout.setOnClickListener(v -> showEditDialog("User Agent", apiConfig.getUserAgent(), apiConfig::setUserAgent));
        binding.bxVLayout.setOnClickListener(v -> showEditDialog("BX-V", apiConfig.getBxV(), apiConfig::setBxV));
        binding.sourceLayout.setOnClickListener(v -> showEditDialog("Source", apiConfig.getSource(), apiConfig::setSource));
        binding.timezoneLayout.setOnClickListener(v -> showEditDialog("Timezone", apiConfig.getTimezone(), apiConfig::setTimezone));
        binding.cookieLayout.setOnClickListener(v -> showEditDialog("Cookie", apiConfig.getCookie(), apiConfig::setCookie));
        binding.bxUaLayout.setOnClickListener(v -> showEditDialog("BX-UA", apiConfig.getBxUa(), apiConfig::setBxUa));
        binding.bxUmidtokenLayout.setOnClickListener(v -> showEditDialog("BX-Umidtoken", apiConfig.getBxUmidtoken(), apiConfig::setBxUmidtoken));
        
        binding.resetButton.setOnClickListener(v -> showResetDialog());
    }

    private void showEditDialog(String title, String currentValue, ValueSetter setter) {
        android.widget.EditText editText = new android.widget.EditText(requireContext());
        editText.setText(currentValue);
        editText.setHint("Enter " + title.toLowerCase());
        
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Edit " + title)
                .setView(editText)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newValue = editText.getText().toString().trim();
                    setter.setValue(newValue);
                    loadCurrentValues();
                    Toast.makeText(requireContext(), title + " updated", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showResetDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Reset to Defaults")
                .setMessage("This will reset all API configuration values to their defaults. Are you sure?")
                .setPositiveButton("Reset", (dialog, which) -> {
                    apiConfig.resetToDefaults();
                    loadCurrentValues();
                    Toast.makeText(requireContext(), "API configuration reset to defaults", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @FunctionalInterface
    private interface ValueSetter {
        void setValue(String value);
    }
}