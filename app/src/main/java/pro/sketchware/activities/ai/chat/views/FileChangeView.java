package pro.sketchware.activities.ai.chat.views;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;

import pro.sketchware.R;

public class FileChangeView extends LinearLayout {
    
    private TextView filePathText;
    private TextView addedLinesText;
    private TextView removedLinesText;
    private TextView codeDiffText;
    private ImageView expandIcon;
    private LinearLayout codeContainer;
    private View fileHeader;
    
    private boolean isExpanded = false;
    private JSONObject fileData;
    
    public FileChangeView(Context context) {
        super(context);
        init();
    }
    
    public FileChangeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public FileChangeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.item_file_change, this, true);
        
        filePathText = findViewById(R.id.filePathText);
        addedLinesText = findViewById(R.id.addedLinesText);
        removedLinesText = findViewById(R.id.removedLinesText);
        codeDiffText = findViewById(R.id.codeDiffText);
        expandIcon = findViewById(R.id.expandIcon);
        codeContainer = findViewById(R.id.codeContainer);
        fileHeader = findViewById(R.id.fileHeader);
        
        fileHeader.setOnClickListener(v -> toggleExpanded());
    }
    
    public void setFileData(JSONObject data) {
        this.fileData = data;
        updateUI();
    }
    
    private void updateUI() {
        if (fileData == null) return;
        
        try {
            String filePath = fileData.optString("file_path", "");
            String content = fileData.optString("content", "");
            String action = fileData.optString("action", "edit_file");
            
            // Display short file path
            String shortPath = getShortPath(filePath);
            filePathText.setText(shortPath);
            
            // Calculate and show diff indicators
            DiffInfo diffInfo = calculateDiffInfo(content, action);
            updateDiffIndicators(diffInfo);
            
            // Set code content with syntax highlighting
            if (!content.isEmpty()) {
                codeDiffText.setText(highlightCode(content, action));
            } else {
                codeDiffText.setText("No content preview available");
            }
            
        } catch (Exception e) {
            filePathText.setText("Error loading file data");
            codeDiffText.setText("Error: " + e.getMessage());
        }
    }
    
    private String getShortPath(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            return "unknown file";
        }
        
        // Extract the relevant part of the path
        // Example: storage/emulated/0/.sketchware/data/784/files/resource/drawable/default.xml
        // Should become: /resource/drawable/default.xml
        
        String[] pathParts = fullPath.split("/");
        StringBuilder shortPath = new StringBuilder();
        
        boolean foundRelevantPart = false;
        for (String part : pathParts) {
            if (foundRelevantPart || part.equals("resource") || part.equals("java") || 
                part.equals("layout") || part.equals("drawable") || part.equals("values") ||
                part.equals("main") || part.equals("app") || part.equals("src")) {
                if (!foundRelevantPart) {
                    foundRelevantPart = true;
                }
                shortPath.append("/").append(part);
            }
        }
        
        if (shortPath.length() == 0) {
            // Fallback: show last 2-3 path components
            int startIndex = Math.max(0, pathParts.length - 3);
            for (int i = startIndex; i < pathParts.length; i++) {
                shortPath.append("/").append(pathParts[i]);
            }
        }
        
        return shortPath.toString().isEmpty() ? fullPath : shortPath.toString();
    }
    
    private DiffInfo calculateDiffInfo(String content, String action) {
        DiffInfo info = new DiffInfo();
        
        if (content == null || content.isEmpty()) {
            return info;
        }
        
        // For simplicity, estimate diff based on action and content
        switch (action.toLowerCase()) {
            case "create_file":
                // All lines are additions
                info.addedLines = content.split("\n").length;
                info.removedLines = 0;
                break;
                
            case "delete_file":
                // All lines are removals (estimate)
                info.addedLines = 0;
                info.removedLines = 20; // Estimate
                break;
                
            case "edit_file":
            default:
                // Estimate based on content size
                int lines = content.split("\n").length;
                info.addedLines = Math.max(1, lines / 2);
                info.removedLines = Math.max(0, lines / 4);
                break;
        }
        
        return info;
    }
    
    private void updateDiffIndicators(DiffInfo diffInfo) {
        if (diffInfo.addedLines > 0) {
            addedLinesText.setText("+" + diffInfo.addedLines);
            addedLinesText.setVisibility(View.VISIBLE);
        } else {
            addedLinesText.setVisibility(View.GONE);
        }
        
        if (diffInfo.removedLines > 0) {
            removedLinesText.setText("-" + diffInfo.removedLines);
            removedLinesText.setVisibility(View.VISIBLE);
        } else {
            removedLinesText.setVisibility(View.GONE);
        }
    }
    
    private SpannableString highlightCode(String content, String action) {
        SpannableString spannable = new SpannableString(content);
        
        // Simple syntax highlighting for XML and Java
        if (content.contains("<?xml") || content.contains("<")) {
            highlightXml(spannable);
        } else if (content.contains("class ") || content.contains("public ") || content.contains("import ")) {
            highlightJava(spannable);
        }
        
        // Highlight diff-style changes
        highlightDiffStyle(spannable, action);
        
        return spannable;
    }
    
    private void highlightXml(SpannableString spannable) {
        String text = spannable.toString();
        int xmlTagColor = ContextCompat.getColor(getContext(), android.R.color.holo_blue_dark);
        
        // Simple XML tag highlighting
        int start = 0;
        while ((start = text.indexOf('<', start)) != -1) {
            int end = text.indexOf('>', start);
            if (end != -1) {
                spannable.setSpan(new ForegroundColorSpan(xmlTagColor), start, end + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                start = end + 1;
            } else {
                break;
            }
        }
    }
    
    private void highlightJava(SpannableString spannable) {
        String text = spannable.toString();
        int keywordColor = ContextCompat.getColor(getContext(), android.R.color.holo_purple);
        
        // Highlight common Java keywords
        String[] keywords = {"public", "private", "class", "import", "static", "void", "return", "if", "else", "for", "while"};
        for (String keyword : keywords) {
            int start = 0;
            while ((start = text.indexOf(keyword, start)) != -1) {
                int end = start + keyword.length();
                // Check if it's a whole word
                if ((start == 0 || !Character.isLetterOrDigit(text.charAt(start - 1))) &&
                    (end == text.length() || !Character.isLetterOrDigit(text.charAt(end)))) {
                    spannable.setSpan(new ForegroundColorSpan(keywordColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                start = end;
            }
        }
    }
    
    private void highlightDiffStyle(SpannableString spannable, String action) {
        int addedColor = ContextCompat.getColor(getContext(), android.R.color.holo_green_light);
        int removedColor = ContextCompat.getColor(getContext(), android.R.color.holo_red_light);
        
        String text = spannable.toString();
        String[] lines = text.split("\n");
        int currentIndex = 0;
        
        for (String line : lines) {
            int lineStart = currentIndex;
            int lineEnd = currentIndex + line.length();
            
            if (line.startsWith("+")) {
                spannable.setSpan(new BackgroundColorSpan(addedColor), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (line.startsWith("-")) {
                spannable.setSpan(new BackgroundColorSpan(removedColor), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            
            currentIndex = lineEnd + 1; // +1 for the newline character
        }
    }
    
    private void toggleExpanded() {
        isExpanded = !isExpanded;
        
        if (isExpanded) {
            codeContainer.setVisibility(View.VISIBLE);
            ObjectAnimator.ofFloat(expandIcon, "rotation", 0f, 180f).setDuration(200).start();
        } else {
            codeContainer.setVisibility(View.GONE);
            ObjectAnimator.ofFloat(expandIcon, "rotation", 180f, 0f).setDuration(200).start();
        }
    }
    
    public void setExpanded(boolean expanded) {
        if (this.isExpanded != expanded) {
            toggleExpanded();
        }
    }
    
    private static class DiffInfo {
        int addedLines = 0;
        int removedLines = 0;
    }
}