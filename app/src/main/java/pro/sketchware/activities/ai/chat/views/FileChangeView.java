package pro.sketchware.activities.ai.chat.views;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

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
            
            // Set code content with diff highlighting
            if (!content.isEmpty()) {
                codeDiffText.setText(highlightDiff(content));
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
        
        // Calculate actual diff based on content and action
        switch (action.toLowerCase()) {
            case "create_file":
                // All lines are additions for new files
                info.addedLines = content.split("\n").length;
                info.removedLines = 0;
                break;
                
            case "delete_file":
                // All lines are removals - try to get actual count if possible
                info.addedLines = 0;
                info.removedLines = 0; // Will be set to 0 since we don't know original content
                break;
                
            case "edit_file":
            default:
                // For edits, count lines with diff markers or assume all are additions if no markers
                String[] lines = content.split("\n");
                for (String line : lines) {
                    if (line.startsWith("+")) {
                        info.addedLines++;
                    } else if (line.startsWith("-")) {
                        info.removedLines++;
                    } else if (!line.startsWith(" ") && !line.startsWith("@@")) {
                        // Lines without diff markers are treated as additions in new content
                        info.addedLines++;
                    }
                }
                
                // If no diff markers found, treat all lines as additions
                if (info.addedLines == 0 && info.removedLines == 0) {
                    info.addedLines = lines.length;
                }
                break;
        }
        
        return info;
    }
    
    private void updateDiffIndicators(DiffInfo diffInfo) {
        // Only show added lines indicator if there are actual additions
        if (diffInfo.addedLines > 0) {
            addedLinesText.setText("+" + diffInfo.addedLines);
            addedLinesText.setVisibility(View.VISIBLE);
        } else {
            addedLinesText.setVisibility(View.GONE);
        }
        
        // Only show removed lines indicator if there are actual removals
        if (diffInfo.removedLines > 0) {
            removedLinesText.setText("-" + diffInfo.removedLines);
            removedLinesText.setVisibility(View.VISIBLE);
        } else {
            removedLinesText.setVisibility(View.GONE);
        }
    }
    
    private SpannableString highlightDiff(String content) {
        SpannableString spannable = new SpannableString(content);
        
        // Define colors for diff highlighting
        int addedBgColor = Color.parseColor("#E8F5E8"); // Light green background
        int addedTextColor = Color.parseColor("#155724"); // Dark green text
        int removedBgColor = Color.parseColor("#FFEBEE"); // Light red background
        int removedTextColor = Color.parseColor("#721C24"); // Dark red text
        
        String[] lines = content.split("\n");
        int currentIndex = 0;
        
        for (String line : lines) {
            int lineStart = currentIndex;
            int lineEnd = currentIndex + line.length();
            
            if (line.startsWith("+")) {
                // Added line - green background and text
                spannable.setSpan(new BackgroundColorSpan(addedBgColor), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(new ForegroundColorSpan(addedTextColor), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (line.startsWith("-")) {
                // Removed line - red background and text
                spannable.setSpan(new BackgroundColorSpan(removedBgColor), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(new ForegroundColorSpan(removedTextColor), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            
            currentIndex = lineEnd + 1; // +1 for the newline character
        }
        
        return spannable;
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