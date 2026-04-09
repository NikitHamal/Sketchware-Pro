package pro.sketchware.activities.editor.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.rosemoe.sora.widget.component.EditorAutoCompletion;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.databinding.ActivityCodeViewerBinding;
import pro.sketchware.utility.EditorUtils;
import pro.sketchware.utility.UI;

public class JavaToBlocksImportActivity extends BaseAppCompatActivity {

    public static final String EXTRA_CODE = "code";
    public static final String EXTRA_SC_ID = "sc_id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_SUBTITLE = "subtitle";

    private ActivityCodeViewerBinding binding;
    private String initialCode = "";

    private final OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (!isModified()) {
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                return;
            }

            new MaterialAlertDialogBuilder(JavaToBlocksImportActivity.this)
                    .setIcon(R.drawable.ic_warning_96dp)
                    .setTitle(Helper.getResString(R.string.common_word_warning))
                    .setMessage(Helper.getResString(R.string.src_code_editor_unsaved_changes_dialog_warning_message))
                    .setPositiveButton(Helper.getResString(R.string.common_word_exit), (dialog, which) -> {
                        dialog.dismiss();
                        finish();
                    })
                    .setNegativeButton(Helper.getResString(R.string.common_word_cancel), null)
                    .show();
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = ActivityCodeViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);

        initialCode = savedInstanceState != null
                ? savedInstanceState.getString(EXTRA_CODE, "")
                : getIntent().getStringExtra(EXTRA_CODE);
        if (initialCode == null) {
            initialCode = "";
        }

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String subtitle = getIntent().getStringExtra(EXTRA_SUBTITLE);
        String scId = getIntent().getStringExtra(EXTRA_SC_ID);

        binding.toolbar.setTitle("Import Java to Blocks");
        if (title != null && !title.isEmpty()) {
            binding.toolbar.setSubtitle(subtitle == null || subtitle.isEmpty() ? title : title + " • " + subtitle);
        } else {
            binding.toolbar.setSubtitle(subtitle == null || subtitle.isEmpty() ? scId : subtitle);
        }
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressedCallback.handleOnBackPressed());

        binding.editor.setTypefaceText(EditorUtils.getTypeface(this));
        binding.editor.setTextSize(14);
        binding.editor.setText(initialCode);
        binding.editor.setEditable(true);
        binding.editor.setWordwrap(false);
        binding.editor.getComponent(EditorAutoCompletion.class).setEnabled(true);
        EditorUtils.loadJavaConfig(binding.editor);

        UI.addSystemWindowInsetToPadding(binding.appBarLayout, true, true, true, false);
        UI.addSystemWindowInsetToMargin(binding.editor, true, false, true, true);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(EXTRA_CODE, binding.editor.getText().toString());
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, 1, Menu.NONE, "Undo")
                .setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_undo))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, 2, Menu.NONE, "Redo")
                .setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_redo))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, 3, Menu.NONE, "Import")
                .setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_save))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, 4, Menu.NONE, "Reload Java colors");
        menu.add(Menu.NONE, 5, Menu.NONE, "Word wrap")
                .setCheckable(true)
                .setChecked(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case 1 -> {
                binding.editor.undo();
                return true;
            }
            case 2 -> {
                binding.editor.redo();
                return true;
            }
            case 3 -> {
                finishWithResult();
                return true;
            }
            case 4 -> {
                EditorUtils.loadJavaConfig(binding.editor);
                return true;
            }
            case 5 -> {
                item.setChecked(!item.isChecked());
                binding.editor.setWordwrap(item.isChecked());
                return true;
            }
            default -> {
                return super.onOptionsItemSelected(item);
            }
        }
    }

    private void finishWithResult() {
        Intent data = new Intent();
        data.putExtra(EXTRA_CODE, binding.editor.getText().toString());
        setResult(RESULT_OK, data);
        finish();
    }

    private boolean isModified() {
        return !initialCode.equals(binding.editor.getText().toString());
    }
}
