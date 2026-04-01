package pro.sketchware.activities.importproject;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import a.a.a.MA;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.importer.AndroidStudioProjectImporter;
import pro.sketchware.utility.SketchwareUtil;

public class ImportAndroidStudioProjectActivity extends BaseAppCompatActivity {
    private static final int REQUEST_PICK_ZIP = 9101;

    private Uri selectedZipUri;
    private TextView selectedZipText;
    private TextView statusText;
    private TextInputEditText githubUrlInput;
    private TextInputEditText branchInput;
    private TextInputEditText tokenInput;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import_android_studio_project);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setTitle("Import Android Studio / GitHub Project");
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        selectedZipText = findViewById(R.id.tv_selected_zip);
        statusText = findViewById(R.id.tv_import_status);
        githubUrlInput = findViewById(R.id.et_github_url);
        branchInput = findViewById(R.id.et_branch);
        tokenInput = findViewById(R.id.et_token);
        Button pickZipButton = findViewById(R.id.btn_pick_zip);
        Button importZipButton = findViewById(R.id.btn_import_zip);
        Button importGithubButton = findViewById(R.id.btn_import_github);

        pickZipButton.setOnClickListener(v -> pickZip());
        importZipButton.setOnClickListener(v -> {
            if (selectedZipUri == null) {
                SketchwareUtil.toastError("Choose an Android Studio ZIP archive first");
                return;
            }
            new ImportTask(ImportTask.MODE_ZIP).execute();
        });
        importGithubButton.setOnClickListener(v -> {
            if (TextUtils.isEmpty(Helper.getText(githubUrlInput).trim())) {
                githubUrlInput.setError("Enter a GitHub repository URL");
                return;
            }
            new ImportTask(ImportTask.MODE_GITHUB).execute();
        });
    }

    private void pickZip() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
        startActivityForResult(intent, REQUEST_PICK_ZIP);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_ZIP && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedZipUri = data.getData();
            selectedZipText.setText(String.valueOf(selectedZipUri));
            try {
                int takeFlags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
                getContentResolver().takePersistableUriPermission(selectedZipUri, takeFlags);
            } catch (Exception ignored) {
            }
        }
    }

    private void showResult(AndroidStudioProjectImporter.ImportResult result) {
        statusText.setText(result.toDisplayText());
        new MaterialAlertDialogBuilder(this)
                .setTitle("Import completed")
                .setMessage(result.toDisplayText() + "\n\nThe imported project is now available in your project list.")
                .setPositiveButton("OK", null)
                .show();
    }

    private class ImportTask extends MA {
        private static final int MODE_ZIP = 1;
        private static final int MODE_GITHUB = 2;

        private final int mode;
        private AndroidStudioProjectImporter.ImportResult result;

        public ImportTask(int mode) {
            super(ImportAndroidStudioProjectActivity.this);
            this.mode = mode;
            addTask(this);
            k();
            statusText.setText(mode == MODE_ZIP ? "Importing Android Studio ZIP..." : "Downloading and importing GitHub repo...");
        }

        @Override
        public void a() {
            h();
            if (result != null) {
                showResult(result);
            }
        }

        @Override
        public void a(String errorMessage) {
            h();
            statusText.setText(errorMessage);
            SketchwareUtil.showAnErrorOccurredDialog(ImportAndroidStudioProjectActivity.this, errorMessage);
        }

        @Override
        public void b() throws a.a.a.By {
            try {
                AndroidStudioProjectImporter importer = new AndroidStudioProjectImporter(ImportAndroidStudioProjectActivity.this);
                if (mode == MODE_ZIP) {
                    result = importer.importFromZipUri(selectedZipUri);
                } else {
                    result = importer.importFromGitHub(
                            Helper.getText(githubUrlInput).trim(),
                            Helper.getText(branchInput).trim(),
                            Helper.getText(tokenInput).trim()
                    );
                }
            } catch (Exception e) {
                throw new a.a.a.By(e.getMessage() == null ? e.toString() : e.getMessage());
            }
        }
    }
}
