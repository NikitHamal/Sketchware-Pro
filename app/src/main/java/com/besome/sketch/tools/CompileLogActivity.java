package com.besome.sketch.tools;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.PopupMenu;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashMap;
import java.util.List;

import mod.hey.studios.util.CompileLogHelper;
import mod.hey.studios.util.Helper;
import mod.jbk.diagnostic.CompileErrorSaver;
import mod.jbk.util.AddMarginOnApplyWindowInsetsListener;
import pro.sketchware.activities.ai.chat.ChatActivity;
import pro.sketchware.activities.ai.errors.CompileErrorParser;
import pro.sketchware.activities.ai.errors.FileContentReader;
import pro.sketchware.databinding.CompileLogBinding;
import pro.sketchware.utility.SketchwareUtil;

public class CompileLogActivity extends BaseAppCompatActivity {

    private static final String PREFERENCE_WRAPPED_TEXT = "wrapped_text";
    private static final String PREFERENCE_USE_MONOSPACED_FONT = "use_monospaced_font";
    private static final String PREFERENCE_FONT_SIZE = "font_size";
    private CompileErrorSaver compileErrorSaver;
    private SharedPreferences logViewerPreferences;

    private CompileLogBinding binding;

    @SuppressLint("SetTextI18n")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = CompileLogBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.optionsLayout,
                new AddMarginOnApplyWindowInsetsListener(WindowInsetsCompat.Type.navigationBars(), WindowInsetsCompat.CONSUMED));

        logViewerPreferences = getPreferences(Context.MODE_PRIVATE);

        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));

        if (getIntent().getBooleanExtra("showingLastError", false)) {
            binding.topAppBar.setTitle("Last compile log");
        } else {
            binding.topAppBar.setTitle("Compile log");
        }

        String sc_id = getIntent().getStringExtra("sc_id");
        if (sc_id == null) {
            finish();
            return;
        }

        compileErrorSaver = new CompileErrorSaver(sc_id);

        if (compileErrorSaver.logFileExists()) {
            binding.clearButton.setOnClickListener(v -> {
                if (compileErrorSaver.logFileExists()) {
                    compileErrorSaver.deleteSavedLogs();
                    getIntent().removeExtra("error");
                    SketchwareUtil.toast("Compile logs have been cleared.");
                } else {
                    SketchwareUtil.toast("No compile logs found.");
                }

                setErrorText();
            });
        }

        // AI Fix button
        binding.aiFixButton.setOnClickListener(v -> {
            if (compileErrorSaver.logFileExists()) {
                openAiErrorFixer(sc_id);
            } else {
                SketchwareUtil.toast("No compile errors to analyze");
            }
        });

        final String wrapTextLabel = "Wrap text";
        final String monospacedFontLabel = "Monospaced font";
        final String fontSizeLabel = "Font size";

        PopupMenu options = new PopupMenu(this, binding.formatButton);
        options.getMenu().add(wrapTextLabel).setCheckable(true).setChecked(getWrappedTextPreference());
        options.getMenu().add(monospacedFontLabel).setCheckable(true).setChecked(getMonospacedFontPreference());
        options.getMenu().add(fontSizeLabel);

        options.setOnMenuItemClickListener(menuItem -> {
            switch (menuItem.getTitle().toString()) {
                case wrapTextLabel -> {
                    menuItem.setChecked(!menuItem.isChecked());
                    toggleWrapText(menuItem.isChecked());
                }
                case monospacedFontLabel -> {
                    menuItem.setChecked(!menuItem.isChecked());
                    toggleMonospacedText(menuItem.isChecked());
                }
                case fontSizeLabel -> changeFontSizeDialog();
                default -> {
                    return false;
                }
            }

            return true;
        });

        binding.formatButton.setOnClickListener(v -> options.show());

        applyLogViewerPreferences();

        setErrorText();
    }

    private void setErrorText() {
        String error = getIntent().getStringExtra("error");
        if (error == null) error = compileErrorSaver.getLogsFromFile();
        if (error == null) {
            binding.noContentLayout.setVisibility(View.VISIBLE);
            binding.optionsLayout.setVisibility(View.GONE);
            return;
        }

        binding.optionsLayout.setVisibility(View.VISIBLE);
        binding.noContentLayout.setVisibility(View.GONE);

        binding.tvCompileLog.setText(CompileLogHelper.getColoredLogs(this, error));
        binding.tvCompileLog.setTextIsSelectable(true);
    }

    private void applyLogViewerPreferences() {
        toggleWrapText(getWrappedTextPreference());
        toggleMonospacedText(getMonospacedFontPreference());
        binding.tvCompileLog.setTextSize(getFontSizePreference());
    }

    private boolean getWrappedTextPreference() {
        return logViewerPreferences.getBoolean(PREFERENCE_WRAPPED_TEXT, false);
    }

    private boolean getMonospacedFontPreference() {
        return logViewerPreferences.getBoolean(PREFERENCE_USE_MONOSPACED_FONT, true);
    }

    private int getFontSizePreference() {
        return logViewerPreferences.getInt(PREFERENCE_FONT_SIZE, 11);
    }

    private void toggleWrapText(boolean isChecked) {
        logViewerPreferences.edit().putBoolean(PREFERENCE_WRAPPED_TEXT, isChecked).apply();

        if (isChecked) {
            binding.errVScroll.removeAllViews();
            if (binding.tvCompileLog.getParent() != null) {
                ((ViewGroup) binding.tvCompileLog.getParent()).removeView(binding.tvCompileLog);
            }
            binding.errVScroll.addView(binding.tvCompileLog);
        } else {
            binding.errVScroll.removeAllViews();
            if (binding.tvCompileLog.getParent() != null) {
                ((ViewGroup) binding.tvCompileLog.getParent()).removeView(binding.tvCompileLog);
            }
            binding.errHScroll.removeAllViews();
            binding.errHScroll.addView(binding.tvCompileLog);
            binding.errVScroll.addView(binding.errHScroll);
        }
    }

    private void toggleMonospacedText(boolean isChecked) {
        logViewerPreferences.edit().putBoolean(PREFERENCE_USE_MONOSPACED_FONT, isChecked).apply();

        if (isChecked) {
            binding.tvCompileLog.setTypeface(Typeface.MONOSPACE);
        } else {
            binding.tvCompileLog.setTypeface(Typeface.DEFAULT);
        }
    }

    private void changeFontSizeDialog() {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(10); //Must not be less than setValue(), which is currently 11 in compile_log.xml
        picker.setMaxValue(70);
        picker.setWrapSelectorWheel(false);
        picker.setValue(getFontSizePreference());

        LinearLayout layout = new LinearLayout(this);
        layout.addView(picker, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select font size")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    logViewerPreferences.edit().putInt(PREFERENCE_FONT_SIZE, picker.getValue()).apply();

                    binding.tvCompileLog.setTextSize((float) picker.getValue());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openAiErrorFixer(String projectId) {
        try {
            // Read the compile log
            String compileLog = compileErrorSaver.getLogsFromFile();
            if (compileLog == null || compileLog.trim().isEmpty()) {
                SketchwareUtil.toast("No compile log found");
                return;
            }

            Log.d("CompileLogActivity", "Opening AI error fixer for project: " + projectId);

            // Parse errors from compile log
            CompileErrorParser.ParseResult parseResult = CompileErrorParser.parseCompileLog(compileLog, projectId);
            
            if (parseResult.errors.isEmpty()) {
                SketchwareUtil.toast("No errors found in compile log");
                return;
            }

            Log.d("CompileLogActivity", "Found " + parseResult.errors.size() + " errors");

            // Read affected files
            List<String> affectedFiles = CompileErrorParser.getUniqueFilePaths(parseResult);
            List<String> projectFiles = FileContentReader.filterProjectFiles(affectedFiles, projectId);
            List<FileContentReader.FileContent> fileContents = FileContentReader.readFiles(projectFiles);

            // Build context message for AI
            StringBuilder contextMessage = new StringBuilder();
            contextMessage.append("🔧 **COMPILE ERROR ANALYSIS REQUEST**\n\n");
            
            // Project info
            contextMessage.append("**Project ID:** ").append(projectId).append("\n");
            contextMessage.append("**Error Summary:** ").append(parseResult.summary).append("\n\n");
            
            // Error details
            contextMessage.append("**📋 COMPILE ERRORS:**\n");
            contextMessage.append("```\n").append(compileLog).append("\n```\n\n");
            
            // File contents
            if (!fileContents.isEmpty()) {
                contextMessage.append("**📄 AFFECTED FILES:**\n\n");
                for (FileContentReader.FileContent fileContent : fileContents) {
                    contextMessage.append("**").append(fileContent.fileName).append("** (").append(fileContent.fileType).append("):\n");
                    if (fileContent.isReadable()) {
                        contextMessage.append("```").append(fileContent.fileType).append("\n");
                        contextMessage.append(fileContent.content);
                        contextMessage.append("\n```\n\n");
                    } else {
                        contextMessage.append("❌ Could not read file: ").append(fileContent.errorMessage).append("\n\n");
                    }
                }
            }
            
            // Instructions for AI
            contextMessage.append("**🎯 INSTRUCTIONS:**\n");
            contextMessage.append("Please analyze these compile errors and provide solutions. ");
            contextMessage.append("When you need to create, edit, or delete files, respond with JSON actions in this format:\n\n");
            
            contextMessage.append("```json\n");
            contextMessage.append("{\n");
            contextMessage.append("  \"response_type\": \"action\",\n");
            contextMessage.append("  \"action\": \"fix_file_error\",\n");
            contextMessage.append("  \"parameters\": {\n");
            contextMessage.append("    \"action\": \"delete_file\",\n");
            contextMessage.append("    \"file_path\": \"/storage/emulated/0/.sketchware/libs/local_libs/annotation-v1.8.2/classes.jar\",\n");
            contextMessage.append("    \"explanation\": \"Removing duplicate annotation library to resolve resource conflict\"\n");
            contextMessage.append("  },\n");
            contextMessage.append("  \"explanation\": \"I'll delete the annotation-v1.8.2 library file to resolve the duplicate resource conflict. The collection library already includes the necessary annotations. Please review and accept to apply this change.\"\n");
            contextMessage.append("}\n");
            contextMessage.append("```\n\n");
            
            contextMessage.append("**You can wrap the JSON in markdown code blocks (```json ... ```) or provide it directly.**\n\n");
            
            contextMessage.append("**Available action types for fix_file_error:**\n");
            contextMessage.append("- `create_file`: Create a new file\n");
            contextMessage.append("- `edit_file`: Modify an existing file\n");
            contextMessage.append("- `delete_file`: Remove a file (for duplicate conflicts)\n");
            contextMessage.append("- `create_directory`: Create a directory\n\n");
            
            contextMessage.append("**IMPORTANT:**\n");
            contextMessage.append("- Use the JSON structure exactly as shown above\n");
            contextMessage.append("- Include both 'parameters.explanation' and top-level 'explanation'\n");
            contextMessage.append("- Use future tense: 'I'll delete...' not 'I've deleted...'\n");
            contextMessage.append("- All file operations require user approval first\n");
            contextMessage.append("- For duplicate file errors like this one, usually delete the redundant file\n");
            contextMessage.append("- Always provide clear explanations for proposed changes\n");

            // Create intent to open ChatActivity
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra("auto_message", contextMessage.toString());
            intent.putExtra("context_type", "error_analysis");
            intent.putExtra("project_id", projectId);
            intent.putExtra("error_count", parseResult.errors.size());
            intent.putExtra("affected_files_count", fileContents.size());
            
            // Add file paths for reference
            if (!affectedFiles.isEmpty()) {
                intent.putExtra("affected_files", affectedFiles.toArray(new String[0]));
            }

            startActivity(intent);
            
        } catch (Exception e) {
            Log.e("CompileLogActivity", "Error opening AI error fixer", e);
            SketchwareUtil.toast("Error analyzing compile log: " + e.getMessage());
        }
    }
}
