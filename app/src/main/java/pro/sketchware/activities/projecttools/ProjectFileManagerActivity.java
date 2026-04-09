package pro.sketchware.activities.projecttools;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mod.hey.studios.code.SrcCodeEditor;
import pro.sketchware.R;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class ProjectFileManagerActivity extends BaseAppCompatActivity {

    private String scId;
    private RecyclerView recyclerView;
    private final Map<String, Boolean> expandState = new HashMap<>();
    private final List<FileTreeNode> visibleNodes = new ArrayList<>();
    private FileTreeAdapter adapter;
    private String filterQuery = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        scId = getIntent().getStringExtra("sc_id");
        if (TextUtils.isEmpty(scId)) {
            SketchwareUtil.toastError("Project id missing");
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Project File Manager");
        toolbar.setSubtitle("Project " + scId);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar);

        TextInputLayout searchLayout = new TextInputLayout(this);
        searchLayout.setHint("Filter files and folders");
        int pad = dp(16);
        searchLayout.setPadding(pad, pad / 2, pad, 0);
        TextInputEditText searchInput = new TextInputEditText(this);
        searchLayout.addView(searchInput);
        root.addView(searchLayout);

        recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileTreeAdapter();
        recyclerView.setAdapter(adapter);
        root.addView(recyclerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f));

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable editable) {
                filterQuery = editable == null ? "" : editable.toString().trim().toLowerCase();
                refreshTree();
            }
        });

        setContentView(root);
        refreshTree();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void refreshTree() {
        visibleNodes.clear();
        addRoot("Editable Java", ProjectToolPaths.getProjectEditableJavaDir(scId), true);
        addRoot("Editable Resources", ProjectToolPaths.getProjectEditableResDir(scId), true);
        addRoot("Editable Assets", ProjectToolPaths.getProjectEditableAssetsDir(scId), true);
        addRoot("Project Data", ProjectToolPaths.getProjectDataDir(scId), true);
        addRoot("Gradle Injection", ProjectToolPaths.getProjectGradleInjectionDir(scId), true);
        addRoot("Generated App", ProjectToolPaths.getProjectGeneratedAppDir(scId), false);
        addRoot("Generated Java", ProjectToolPaths.getProjectGeneratedJavaDir(scId), false);
        addRoot("Generated Resources", ProjectToolPaths.getProjectGeneratedResDir(scId), false);
        adapter.notifyDataSetChanged();
    }

    private void addRoot(String label, File root, boolean editable) {
        FileTreeNode node = new FileTreeNode(root, label, 0, editable, true);
        if (matches(node)) {
            visibleNodes.add(node);
        }
        if (root.exists() && isExpanded(root)) {
            buildTree(root, 1, editable);
        }
    }

    private boolean matches(FileTreeNode node) {
        if (filterQuery.isEmpty()) return true;
        return node.label.toLowerCase().contains(filterQuery) || node.file.getAbsolutePath().toLowerCase().contains(filterQuery);
    }

    private void buildTree(File dir, int depth, boolean editable) {
        File[] children = dir.listFiles();
        if (children == null) return;
        Arrays.sort(children, (left, right) -> {
            if (left.isDirectory() && !right.isDirectory()) return -1;
            if (!left.isDirectory() && right.isDirectory()) return 1;
            return left.getName().compareToIgnoreCase(right.getName());
        });
        for (File child : children) {
            FileTreeNode node = new FileTreeNode(child, child.getName(), depth, editable, false);
            if (matches(node)) {
                visibleNodes.add(node);
            }
            if (child.isDirectory() && isExpanded(child)) {
                buildTree(child, depth + 1, editable);
            }
        }
    }

    private boolean isExpanded(File file) {
        return expandState.getOrDefault(file.getAbsolutePath(), false);
    }

    private void toggleExpanded(File file) {
        expandState.put(file.getAbsolutePath(), !isExpanded(file));
        refreshTree();
    }

    private void openFile(FileTreeNode node) {
        if (node.file.isDirectory()) {
            toggleExpanded(node.file);
            return;
        }
        if (ProjectToolPaths.isEditableFile(scId, node.file)) {
            Intent intent = new Intent(this, SrcCodeEditor.class);
            intent.putExtra("title", node.file.getName());
            intent.putExtra("content", node.file.getAbsolutePath());
            startActivity(intent);
        } else {
            Intent intent = new Intent(this, ReadOnlyCodeViewerActivity.class);
            intent.putExtra(ReadOnlyCodeViewerActivity.EXTRA_TITLE, node.file.getName());
            intent.putExtra(ReadOnlyCodeViewerActivity.EXTRA_PATH, node.file.getAbsolutePath());
            startActivity(intent);
        }
    }

    private void showCreateDialog(File parentDir) {
        if (parentDir == null || !parentDir.isDirectory()) return;
        TextInputLayout inputLayout = new TextInputLayout(this);
        inputLayout.setHint("Name");
        TextInputEditText input = new TextInputEditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        inputLayout.addView(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Create")
                .setView(inputLayout)
                .setNegativeButton(R.string.common_word_cancel, null)
                .setNeutralButton("File", (dialog, which) -> {
                    String name = input.getText() == null ? "" : input.getText().toString().trim();
                    if (name.isEmpty()) {
                        SketchwareUtil.toastError("Enter a name");
                        return;
                    }
                    FileUtil.writeFile(new File(parentDir, name).getAbsolutePath(), "");
                    refreshTree();
                })
                .setPositiveButton("Folder", (dialog, which) -> {
                    String name = input.getText() == null ? "" : input.getText().toString().trim();
                    if (name.isEmpty()) {
                        SketchwareUtil.toastError("Enter a name");
                        return;
                    }
                    new File(parentDir, name).mkdirs();
                    refreshTree();
                })
                .show();
    }

    private void showRenameDialog(FileTreeNode node) {
        if (node.rootNode) return;
        TextInputLayout inputLayout = new TextInputLayout(this);
        inputLayout.setHint("New name");
        TextInputEditText input = new TextInputEditText(this);
        input.setText(node.file.getName());
        inputLayout.addView(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Rename")
                .setView(inputLayout)
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton("Rename", (dialog, which) -> {
                    String name = input.getText() == null ? "" : input.getText().toString().trim();
                    if (name.isEmpty()) {
                        SketchwareUtil.toastError("Enter a name");
                        return;
                    }
                    File target = new File(node.file.getParentFile(), name);
                    if (!node.file.renameTo(target)) {
                        SketchwareUtil.toastError("Rename failed");
                    }
                    refreshTree();
                })
                .show();
    }

    private void showDeleteDialog(FileTreeNode node) {
        if (node.rootNode) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete")
                .setMessage("Delete " + node.file.getName() + "?")
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteRecursive(node.file);
                    refreshTree();
                })
                .show();
    }

    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }

    private void copyPath(FileTreeNode node) {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboardManager.setPrimaryClip(ClipData.newPlainText("path", node.file.getAbsolutePath()));
        SketchwareUtil.toast("Path copied");
    }

    private void compareWithGenerated(FileTreeNode node) {
        File counterpart = ProjectToolPaths.findGeneratedCounterpart(scId, node.file);
        if (counterpart == null || !counterpart.isFile()) {
            SketchwareUtil.toastError("No generated counterpart found");
            return;
        }
        Intent intent = new Intent(this, CodeDiffActivity.class);
        intent.putExtra(CodeDiffActivity.EXTRA_TITLE, node.file.getName());
        intent.putExtra(CodeDiffActivity.EXTRA_ORIGINAL, FileUtil.readFile(node.file.getAbsolutePath()));
        intent.putExtra(CodeDiffActivity.EXTRA_MODIFIED, FileUtil.readFile(counterpart.getAbsolutePath()));
        startActivity(intent);
    }

    private void showActions(FileTreeNode node) {
        List<String> actions = new ArrayList<>();
        if (node.file.isDirectory() && node.editable) actions.add("Create inside");
        actions.add(node.file.isDirectory() ? (isExpanded(node.file) ? "Collapse" : "Expand") : "Open");
        if (!node.rootNode && node.editable) actions.add("Rename");
        if (!node.rootNode && node.editable) actions.add("Delete");
        actions.add("Copy path");
        if (node.editable && node.file.isFile() && ProjectToolPaths.findGeneratedCounterpart(scId, node.file) != null) {
            actions.add("Compare with generated");
        }
        CharSequence[] items = actions.toArray(new CharSequence[0]);
        new MaterialAlertDialogBuilder(this)
                .setTitle(node.label)
                .setItems(items, (dialog, which) -> {
                    String selected = actions.get(which);
                    if ("Create inside".equals(selected)) showCreateDialog(node.file);
                    else if ("Open".equals(selected)) openFile(node);
                    else if ("Expand".equals(selected) || "Collapse".equals(selected)) toggleExpanded(node.file);
                    else if ("Rename".equals(selected)) showRenameDialog(node);
                    else if ("Delete".equals(selected)) showDeleteDialog(node);
                    else if ("Copy path".equals(selected)) copyPath(node);
                    else if ("Compare with generated".equals(selected)) compareWithGenerated(node);
                })
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, 1, Menu.NONE, "Expand all roots");
        menu.add(Menu.NONE, 2, Menu.NONE, "Collapse all roots");
        menu.add(Menu.NONE, 3, Menu.NONE, "Search in project");
        menu.add(Menu.NONE, 4, Menu.NONE, "Gradle injection");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            expandState.put(ProjectToolPaths.getProjectEditableJavaDir(scId).getAbsolutePath(), true);
            expandState.put(ProjectToolPaths.getProjectEditableResDir(scId).getAbsolutePath(), true);
            expandState.put(ProjectToolPaths.getProjectEditableAssetsDir(scId).getAbsolutePath(), true);
            expandState.put(ProjectToolPaths.getProjectDataDir(scId).getAbsolutePath(), true);
            expandState.put(ProjectToolPaths.getProjectGradleInjectionDir(scId).getAbsolutePath(), true);
            expandState.put(ProjectToolPaths.getProjectGeneratedAppDir(scId).getAbsolutePath(), true);
            expandState.put(ProjectToolPaths.getProjectGeneratedJavaDir(scId).getAbsolutePath(), true);
            expandState.put(ProjectToolPaths.getProjectGeneratedResDir(scId).getAbsolutePath(), true);
            refreshTree();
            return true;
        }
        if (item.getItemId() == 2) {
            expandState.clear();
            refreshTree();
            return true;
        }
        if (item.getItemId() == 3) {
            startActivity(new Intent(this, SearchInProjectActivity.class).putExtra("sc_id", scId));
            return true;
        }
        if (item.getItemId() == 4) {
            startActivity(new Intent(this, GradleInjectionActivity.class).putExtra("sc_id", scId));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static final class FileTreeNode {
        final File file;
        final String label;
        final int depth;
        final boolean editable;
        final boolean rootNode;

        FileTreeNode(File file, String label, int depth, boolean editable, boolean rootNode) {
            this.file = file;
            this.label = label;
            this.depth = depth;
            this.editable = editable;
            this.rootNode = rootNode;
        }
    }

    private final class FileTreeAdapter extends RecyclerView.Adapter<FileTreeAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int pad = dp(12);
            row.setPadding(pad, pad, pad, pad);
            row.setClickable(true);
            row.setFocusable(true);
            android.util.TypedValue value = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
            row.setBackgroundResource(value.resourceId);

            TextView arrow = new TextView(parent.getContext());
            arrow.setTextSize(18);
            row.addView(arrow);

            TextView title = new TextView(parent.getContext());
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(title, titleParams);

            TextView badge = new TextView(parent.getContext());
            row.addView(badge);
            return new ViewHolder(row, arrow, title, badge);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FileTreeNode node = visibleNodes.get(position);
            holder.itemView.setPadding(dp(12 + node.depth * 18), dp(12), dp(12), dp(12));
            holder.title.setText(node.label);
            holder.badge.setText(node.editable ? "editable" : "generated");
            holder.arrow.setText(node.file.isDirectory() ? (isExpanded(node.file) ? "▾" : "▸") : "•");
            holder.itemView.setOnClickListener(v -> openFile(node));
            holder.itemView.setOnLongClickListener(v -> {
                showActions(node);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return visibleNodes.size();
        }

        final class ViewHolder extends RecyclerView.ViewHolder {
            final TextView arrow;
            final TextView title;
            final TextView badge;

            ViewHolder(@NonNull View itemView, TextView arrow, TextView title, TextView badge) {
                super(itemView);
                this.arrow = arrow;
                this.title = title;
                this.badge = badge;
            }
        }
    }
}
