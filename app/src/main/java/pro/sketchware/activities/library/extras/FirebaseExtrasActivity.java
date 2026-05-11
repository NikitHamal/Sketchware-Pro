package pro.sketchware.activities.library.extras;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Objects;

import a.a.a.jC;
import com.besome.sketch.beans.ProjectLibraryBean;
import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.project.ProjectSettings;
import pro.sketchware.databinding.ActivityFirebaseExtrasBinding;
import pro.sketchware.settings.LibraryExtrasSettings;
import pro.sketchware.settings.ProjectSettingsStore;

public class FirebaseExtrasActivity extends AppCompatActivity {

    public static final String EXTRA_SC_ID = "sc_id";

    private String scId;
    private ActivityFirebaseExtrasBinding binding;
    private LibraryExtrasSettings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scId = getIntent().getStringExtra(EXTRA_SC_ID);
        if (scId == null || scId.trim().isEmpty()) {
            finish();
            return;
        }
        EdgeToEdge.enable(this);
        binding = ActivityFirebaseExtrasBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        settings = new LibraryExtrasSettings(new ProjectSettingsStore(this, scId));
        initialize();
    }

    private void initialize() {
        // ── Restore saved states ────────────────────────────────────────────
        binding.swAnalytics.setChecked(settings.isUseGoogleAnalytics());
        binding.swBilling.setChecked(settings.isUseAndroidBilling());
        binding.swOnesignal.setChecked(settings.isUseOneSignal());

        // ── Listeners ───────────────────────────────────────────────────────
        binding.swAnalytics.setOnCheckedChangeListener((b, checked) ->
                settings.setUseGoogleAnalytics(checked));
        binding.swBilling.setOnCheckedChangeListener((b, checked) ->
                settings.setUseAndroidBilling(checked));
        binding.swOnesignal.setOnCheckedChangeListener((b, checked) ->
                settings.setUseOneSignal(checked));

        // ── Row click toggles ────────────────────────────────────────────────
        binding.lnAnalytics.setOnClickListener(v -> binding.swAnalytics.toggle());
        binding.lnBilling.setOnClickListener(v -> binding.swBilling.toggle());
        binding.lnOnesignal.setOnClickListener(v -> binding.swOnesignal.toggle());

        // ── Availability guards ──────────────────────────────────────────────
        initializeAnalytics();
        initializeBilling();
        initializeOneSignal();
    }

    // ── Guards ───────────────────────────────────────────────────────────────

    private boolean isFirebaseEnabled() {
        ProjectLibraryBean fb = jC.c(scId).d();
        return fb != null && ProjectLibraryBean.LIB_USE_Y.equals(fb.useYn);
    }

    private boolean isAppCompatEnabled() {
        ProjectLibraryBean compat = jC.c(scId).c();
        return compat != null && ProjectLibraryBean.LIB_USE_Y.equals(compat.useYn);
    }

    private boolean isJava7() {
        BuildSettings bs = new BuildSettings(scId);
        return BuildSettings.SETTING_JAVA_VERSION_1_7.equals(
                bs.getValue(BuildSettings.SETTING_JAVA_VERSION,
                        BuildSettings.SETTING_JAVA_VERSION_1_8));
    }

    private int getMinSdk() {
        return new ProjectSettings(scId).getMinSdkVersion();
    }

    private void initializeAnalytics() {
        boolean ok = true;
        if (getMinSdk() < 24) {
            ok = false;
            binding.tvAnalyticsNote.setText("To use, min SDK required is 24 or newer (Android 7+). "
                    + binding.tvAnalyticsNote.getText());
        } else if (isJava7()) {
            ok = false;
            binding.tvAnalyticsNote.setText("To use, use a newer version of Java. "
                    + binding.tvAnalyticsNote.getText());
        } else if (!isFirebaseEnabled()) {
            ok = false;
            binding.tvAnalyticsNote.setText("To use, enable Firebase. "
                    + binding.tvAnalyticsNote.getText());
        }
        setRowEnabled(binding.lnAnalytics, ok);
    }

    private void initializeBilling() {
        boolean ok = true;
        if (!isFirebaseEnabled()) {
            ok = false;
            binding.tvBillingNote.setText("To use, enable Firebase. "
                    + binding.tvBillingNote.getText());
        } else if (!isAppCompatEnabled()) {
            ok = false;
            binding.tvBillingNote.setText("To use, enable AppCompat. "
                    + binding.tvBillingNote.getText());
        }
        setRowEnabled(binding.lnBilling, ok);
    }

    private void initializeOneSignal() {
        boolean ok = true;
        if (!isFirebaseEnabled()) {
            ok = false;
            binding.tvOnesignalNote.setText("To use, enable Firebase. "
                    + binding.tvOnesignalNote.getText());
        } else if (!isAppCompatEnabled()) {
            ok = false;
            binding.tvOnesignalNote.setText("To use, enable AppCompat. "
                    + binding.tvOnesignalNote.getText());
        }
        setRowEnabled(binding.lnOnesignal, ok);
    }

    private void setRowEnabled(android.view.View row, boolean enabled) {
        row.setEnabled(enabled);
        row.setAlpha(enabled ? 1f : 0.5f);
    }
}
