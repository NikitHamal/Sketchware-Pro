package pro.sketchware.activities.projecttools;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.utility.SketchwareUtil;

public class CodeDiffActivity extends BaseAppCompatActivity {

    public static final String EXTRA_ORIGINAL = "original";
    public static final String EXTRA_MODIFIED = "modified";
    public static final String EXTRA_TITLE = "title";

    private android.widget.TextView diffView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Code Diff");
        String subtitle = getIntent().getStringExtra(EXTRA_TITLE);
        if (subtitle != null) toolbar.setSubtitle(subtitle);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar);

        android.widget.TextView statsView = new android.widget.TextView(this);
        int pad = dp(12);
        statsView.setPadding(pad, pad / 2, pad, pad / 2);
        root.addView(statsView);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        diffView = new android.widget.TextView(this);
        diffView.setTypeface(Typeface.MONOSPACE);
        diffView.setTextIsSelectable(true);
        diffView.setPadding(pad, pad, pad, pad);
        scrollView.addView(diffView, new android.widget.ScrollView.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scrollView, new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f));

        setContentView(root);

        String original = getIntent().getStringExtra(EXTRA_ORIGINAL);
        String modified = getIntent().getStringExtra(EXTRA_MODIFIED);
        if (original == null || modified == null) {
            SketchwareUtil.toastError("Missing diff content");
            finish();
            return;
        }
        renderDiff(original, modified, statsView);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private enum LineType { EQUAL, ADD, DELETE }

    private static final class DiffLine {
        final LineType type;
        final String text;
        final int originalNumber;
        final int modifiedNumber;

        DiffLine(LineType type, String text, int originalNumber, int modifiedNumber) {
            this.type = type;
            this.text = text;
            this.originalNumber = originalNumber;
            this.modifiedNumber = modifiedNumber;
        }
    }

    private List<DiffLine> computeDiff(String[] original, String[] modified) {
        int m = original.length;
        int n = modified.length;
        int[][] dp = new int[m + 1][n + 1];
        for (int i = m - 1; i >= 0; i--) {
            for (int j = n - 1; j >= 0; j--) {
                if (original[i].equals(modified[j])) {
                    dp[i][j] = dp[i + 1][j + 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }

        List<DiffLine> result = new ArrayList<>();
        int i = 0;
        int j = 0;
        int oi = 1;
        int mi = 1;
        while (i < m && j < n) {
            if (original[i].equals(modified[j])) {
                result.add(new DiffLine(LineType.EQUAL, original[i], oi++, mi++));
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                result.add(new DiffLine(LineType.DELETE, original[i], oi++, -1));
                i++;
            } else {
                result.add(new DiffLine(LineType.ADD, modified[j], -1, mi++));
                j++;
            }
        }
        while (i < m) result.add(new DiffLine(LineType.DELETE, original[i++], oi++, -1));
        while (j < n) result.add(new DiffLine(LineType.ADD, modified[j++], -1, mi++));
        return result;
    }

    private void renderDiff(String original, String modified, android.widget.TextView statsView) {
        String[] originalLines = original.split("\n", -1);
        String[] modifiedLines = modified.split("\n", -1);
        List<DiffLine> diffLines = computeDiff(originalLines, modifiedLines);

        int added = 0;
        int deleted = 0;
        int same = 0;
        for (DiffLine line : diffLines) {
            if (line.type == LineType.ADD) added++;
            else if (line.type == LineType.DELETE) deleted++;
            else same++;
        }
        statsView.setText("+" + added + "   -" + deleted + "   =" + same);

        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (DiffLine line : diffLines) {
            int start = sb.length();
            String gutter;
            if (line.type == LineType.EQUAL) {
                gutter = String.format("  %4s  %4s  ", line.originalNumber, line.modifiedNumber);
            } else if (line.type == LineType.DELETE) {
                gutter = String.format("- %4s        ", line.originalNumber);
            } else {
                gutter = String.format("+      %4s  ", line.modifiedNumber);
            }
            sb.append(gutter).append(line.text).append('\n');
            int end = sb.length();
            int codeStart = start + gutter.length();
            if (line.type == LineType.ADD) {
                sb.setSpan(new BackgroundColorSpan(0x2200C853), start, end, 0);
                sb.setSpan(new ForegroundColorSpan(0xFF1B5E20), codeStart, end, 0);
            } else if (line.type == LineType.DELETE) {
                sb.setSpan(new BackgroundColorSpan(0x22F44336), start, end, 0);
                sb.setSpan(new ForegroundColorSpan(0xFFB71C1C), codeStart, end, 0);
            } else {
                sb.setSpan(new ForegroundColorSpan(0xFF888888), start, end, 0);
            }
            sb.setSpan(new ForegroundColorSpan(0xFF607D8B), start, codeStart, 0);
        }
        diffView.setText(sb);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, 1, Menu.NONE, "Copy diff");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            android.content.ClipboardManager clipboardManager =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("diff", diffView.getText()));
            SketchwareUtil.toast("Diff copied");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
