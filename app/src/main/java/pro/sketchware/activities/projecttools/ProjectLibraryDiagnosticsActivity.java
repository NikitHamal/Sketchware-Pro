package pro.sketchware.activities.projecttools;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.besome.sketch.editor.manage.library.ManageLibraryActivity;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;

import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import dev.aldi.sayuti.editor.manage.ManageLocalLibraryActivity;
import pro.sketchware.util.library.BuiltInLibraryCompatibilityMatrix;
import pro.sketchware.utility.FileUtil;

public class ProjectLibraryDiagnosticsActivity extends BaseAppCompatActivity {

    private String scId;
    private TextView diagnosticsView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        scId = getIntent().getStringExtra("sc_id");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Library Diagnostics");
        toolbar.setSubtitle(scId == null ? "" : "Project " + scId);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar);

        int pad = dp(16);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, pad);

        MaterialButton manageBuiltIn = new MaterialButton(this);
        manageBuiltIn.setText("Open built-in libraries");
        manageBuiltIn.setOnClickListener(v -> {
            Intent intent = new Intent(this, ManageLibraryActivity.class);
            intent.putExtra("sc_id", scId);
            startActivity(intent);
        });
        content.addView(manageBuiltIn);

        MaterialButton manageLocal = new MaterialButton(this);
        manageLocal.setText("Open local libraries");
        manageLocal.setOnClickListener(v -> {
            Intent intent = new Intent(this, ManageLocalLibraryActivity.class);
            intent.putExtra("sc_id", scId);
            startActivity(intent);
        });
        content.addView(manageLocal);

        MaterialButton refresh = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        refresh.setText("Refresh diagnostics");
        refresh.setOnClickListener(v -> refreshDiagnostics());
        content.addView(refresh);

        diagnosticsView = new TextView(this);
        diagnosticsView.setTextIsSelectable(true);
        diagnosticsView.setPadding(0, pad, 0, 0);
        content.addView(diagnosticsView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(content);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f));

        setContentView(root);
        refreshDiagnostics();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void refreshDiagnostics() {
        StringBuilder report = new StringBuilder();
        BuiltInLibraryCompatibilityMatrix.ValidationResult validationResult =
                BuiltInLibraryCompatibilityMatrix.validate(scId);
        report.append("Built-in library configuration: ")
                .append(validationResult.isValid() ? "healthy" : "needs attention")
                .append("\n\n");

        if (validationResult.getErrors().isEmpty()) {
            report.append("No built-in library conflicts detected.\n\n");
        } else {
            report.append("Issues:\n");
            for (String error : validationResult.getErrors()) {
                report.append("• ").append(error).append("\n");
            }
            report.append('\n');
        }

        report.append("Required transitive built-in libraries:\n");
        for (String library : validationResult.getRequiredLibraries()) {
            report.append("• ").append(library).append("\n");
        }
        report.append('\n');

        ArrayList<HashMap<String, Object>> attachedLibraries = LocalLibrariesUtil.getLocalLibraries(scId);
        report.append("Attached local libraries: ").append(attachedLibraries.size()).append("\n");
        for (HashMap<String, Object> map : attachedLibraries) {
            Object name = map.get("name");
            if (name != null) {
                report.append("• ").append(name).append("\n");
            }
        }
        report.append('\n');

        String localLibFile = LocalLibrariesUtil.getLocalLibFile(scId).getAbsolutePath();
        report.append("Local library descriptor: ").append(localLibFile).append("\n");
        if (FileUtil.isExistFile(localLibFile)) {
            String descriptor = FileUtil.readFile(localLibFile);
            if (!TextUtils.isEmpty(descriptor)) {
                report.append("Descriptor bytes: ").append(descriptor.length()).append("\n");
            }
        }

        diagnosticsView.setText(report.toString());
    }
}
