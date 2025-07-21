package pro.sketchware.activities.settings;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import com.besome.sketch.lib.base.BaseAppCompatActivity;

import pro.sketchware.databinding.ActivitySettingsBinding;
import pro.sketchware.fragments.settings.appearance.SettingsAppearanceFragment;
import pro.sketchware.fragments.settings.block.selector.BlockSelectorManagerFragment;
import pro.sketchware.fragments.settings.events.EventsManagerFragment;
import pro.sketchware.fragments.settings.api.ApiConfigFragment;

public class SettingsActivity extends BaseAppCompatActivity {

    public static final String FRAGMENT_TAG_EXTRA = "fragment_tag";
    public static final String SETTINGS_APPEARANCE_FRAGMENT = "settings_appearance";
    public static final String EVENTS_MANAGER_FRAGMENT = "events_manager";
    public static final String BLOCK_SELECTOR_MANAGER_FRAGMENT = "block_selector_manager";
    public static final String API_CONFIG_FRAGMENT = "api_config";
    private ActivitySettingsBinding binding;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String fragmentTag = getIntent().getStringExtra(FRAGMENT_TAG_EXTRA);
        Fragment fragment = switch (fragmentTag) {
            case SETTINGS_APPEARANCE_FRAGMENT -> new SettingsAppearanceFragment();
            case EVENTS_MANAGER_FRAGMENT -> new EventsManagerFragment();
            case BLOCK_SELECTOR_MANAGER_FRAGMENT -> new BlockSelectorManagerFragment();
            case API_CONFIG_FRAGMENT -> new ApiConfigFragment();
            default -> throw new IllegalArgumentException("Unknown fragment tag: " + fragmentTag);
        };

        openFragment(fragment);
    }

    private void openFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(binding.settingsFragmentContainer.getId(), fragment)
                .commit();
    }
}