package pro.sketchware.activities.projecttools;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mod.hey.studios.code.SrcCodeEditor;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class SearchInProjectActivity extends BaseAppCompatActivity {

    private String scId;
    private TextView statusView;
    private final List<SearchResult> results = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SearchAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        scId = getIntent().getStringExtra("sc_id");
        if (scId == null || scId.trim().isEmpty()) {
            SketchwareUtil.toastError("Project id missing");
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Search in Project");
        toolbar.setSubtitle("Project " + scId);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar);

        TextInputLayout inputLayout = new TextInputLayout(this);
        inputLayout.setHint("Search text, class names, resources, Gradle content...");
        int pad = dp(16);
        inputLayout.setPadding(pad, pad / 2, pad, 0);
        TextInputEditText input = new TextInputEditText(this);
        inputLayout.addView(input);
        root.addView(inputLayout);

        statusView = new TextView(this);
        statusView.setPadding(pad, pad / 2, pad, pad / 2);
        statusView.setText("Type at least 2 characters...");
        root.addView(statusView);

        RecyclerView recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SearchAdapter();
        recyclerView.setAdapter(adapter);
        root.addView(recyclerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f));

        input.addTextChangedListener(new TextWatcher() {
            private Runnable pending;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable editable) {
                if (pending != null) {
                    input.removeCallbacks(pending);
                }
                pending = () -> search(editable == null ? "" : editable.toString().trim());
                input.postDelayed(pending, 300);
            }
        });

        setContentView(root);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void search(String query) {
        if (query.length() < 2) {
            results.clear();
            adapter.notifyDataSetChanged();
            statusView.setText("Type at least 2 characters...");
            return;
        }
        statusView.setText("Searching...");
        executor.execute(() -> {
            List<SearchResult> found = searchAll(query);
            runOnUiThread(() -> {
                results.clear();
                results.addAll(found);
                adapter.notifyDataSetChanged();
                statusView.setText(found.size() + " results for \"" + query + "\"");
            });
        });
    }

    private List<SearchResult> searchAll(String query) {
        List<SearchResult> out = new ArrayList<>();
        Pattern pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE);

        List<File> roots = new ArrayList<>();
        roots.add(ProjectToolPaths.getProjectDataDir(scId));
        roots.add(ProjectToolPaths.getProjectEditableRoot(scId));
        roots.add(ProjectToolPaths.getProjectInjectionDir(scId));
        roots.add(ProjectToolPaths.getProjectMyscDir(scId));

        for (File root : roots) {
            if (root.exists()) {
                searchDirectory(root, root, pattern, out);
            }
        }
        return out;
    }

    private void searchDirectory(File root, File dir, Pattern pattern, List<SearchResult> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        java.util.Arrays.sort(children, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        for (File child : children) {
            if (child.isDirectory()) {
                searchDirectory(root, child, pattern, out);
            } else if (isSearchable(child)) {
                searchInFile(root, child, pattern, out);
            }
        }
    }

    private boolean isSearchable(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".xml")
                || name.endsWith(".json") || name.endsWith(".gradle") || name.endsWith(".kts")
                || name.endsWith(".properties") || name.endsWith(".txt") || !name.contains(".");
    }

    private void searchInFile(File root, File file, Pattern pattern, List<SearchResult> out) {
        try {
            String content = FileUtil.readFileIfExist(file.getAbsolutePath());
            String[] lines = content.split("\n", -1);
            Matcher matcher = pattern.matcher(content);
            int count = 0;
            int lastLine = -1;
            while (matcher.find() && count < 10) {
                int position = matcher.start();
                int lineNumber = 1;
                int accumulated = 0;
                for (int i = 0; i < lines.length; i++) {
                    accumulated += lines[i].length() + 1;
                    if (accumulated > position) {
                        lineNumber = i + 1;
                        break;
                    }
                }
                if (lineNumber == lastLine) continue;
                lastLine = lineNumber;
                String preview = lines[Math.max(0, lineNumber - 1)].trim();
                if (preview.length() > 140) preview = preview.substring(0, 140) + "...";
                out.add(new SearchResult(ProjectToolPaths.relativize(root, file), lineNumber, preview, file));
                count++;
            }
        } catch (Exception ignored) {
        }
    }

    private void open(SearchResult result) {
        if (ProjectToolPaths.isEditableFile(scId, result.file)) {
            Intent intent = new Intent(this, SrcCodeEditor.class);
            intent.putExtra("title", result.file.getName());
            intent.putExtra("content", result.file.getAbsolutePath());
            startActivity(intent);
        } else {
            Intent intent = new Intent(this, ReadOnlyCodeViewerActivity.class);
            intent.putExtra(ReadOnlyCodeViewerActivity.EXTRA_TITLE, result.file.getName());
            intent.putExtra(ReadOnlyCodeViewerActivity.EXTRA_PATH, result.file.getAbsolutePath());
            startActivity(intent);
        }
    }

    private static final class SearchResult {
        final String path;
        final int line;
        final String preview;
        final File file;

        SearchResult(String path, int line, String preview, File file) {
            this.path = path;
            this.line = line;
            this.preview = preview;
            this.file = file;
        }
    }

    private final class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(12);
            row.setPadding(pad, pad, pad, pad);
            row.setClickable(true);
            row.setFocusable(true);
            android.util.TypedValue value = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
            row.setBackgroundResource(value.resourceId);

            TextView title = new TextView(parent.getContext());
            title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
            row.addView(title);

            TextView subtitle = new TextView(parent.getContext());
            row.addView(subtitle);

            TextView preview = new TextView(parent.getContext());
            preview.setTypeface(android.graphics.Typeface.MONOSPACE);
            row.addView(preview);
            return new ViewHolder(row, title, subtitle, preview);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SearchResult result = results.get(position);
            holder.title.setText(result.file.getName());
            holder.subtitle.setText(result.path + ":" + result.line);
            holder.preview.setText(result.preview);
            holder.itemView.setOnClickListener(v -> open(result));
        }

        @Override
        public int getItemCount() {
            return results.size();
        }

        final class ViewHolder extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView subtitle;
            final TextView preview;

            ViewHolder(@NonNull android.view.View itemView, TextView title, TextView subtitle, TextView preview) {
                super(itemView);
                this.title = title;
                this.subtitle = subtitle;
                this.preview = preview;
            }
        }
    }
}
