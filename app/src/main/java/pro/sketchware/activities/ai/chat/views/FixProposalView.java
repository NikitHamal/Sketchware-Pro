package pro.sketchware.activities.ai.chat.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;

public class FixProposalView extends LinearLayout {
    
    private TextView explanationText;
    private LinearLayout filesContainer;
    private MaterialButton acceptButton;
    private MaterialButton discardButton;
    
    private OnProposalActionListener listener;
    private List<JSONObject> proposalDataList;
    
    public interface OnProposalActionListener {
        void onAccept(List<JSONObject> proposalDataList);
        void onDiscard(List<JSONObject> proposalDataList);
    }
    
    public FixProposalView(Context context) {
        super(context);
        init();
    }
    
    public FixProposalView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public FixProposalView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.fix_proposal_card, this, true);
        
        explanationText = findViewById(R.id.explanationText);
        filesContainer = findViewById(R.id.filesContainer);
        acceptButton = findViewById(R.id.acceptButton);
        discardButton = findViewById(R.id.discardButton);
        
        proposalDataList = new ArrayList<>();
        
        acceptButton.setOnClickListener(v -> {
            if (listener != null && proposalDataList != null && !proposalDataList.isEmpty()) {
                listener.onAccept(proposalDataList);
            }
        });
        
        discardButton.setOnClickListener(v -> {
            if (listener != null && proposalDataList != null && !proposalDataList.isEmpty()) {
                listener.onDiscard(proposalDataList);
            }
        });
    }
    
    public void setProposal(String explanation, JSONObject actionData) {
        try {
            // Set explanation
            explanationText.setText(explanation);
            
            // Clear existing proposal data and views
            proposalDataList.clear();
            filesContainer.removeAllViews();
            
            // Handle single action or multiple actions
            if (actionData.has("actions")) {
                // Multiple actions case
                JSONArray actions = actionData.getJSONArray("actions");
                for (int i = 0; i < actions.length(); i++) {
                    JSONObject action = actions.getJSONObject(i);
                    proposalDataList.add(action);
                    addFileChangeView(action);
                }
            } else {
                // Single action case
                proposalDataList.add(actionData);
                addFileChangeView(actionData);
            }
            
        } catch (Exception e) {
            explanationText.setText("Error parsing proposal: " + e.getMessage());
        }
    }
    
    private void addFileChangeView(JSONObject fileData) {
        FileChangeView fileChangeView = new FileChangeView(getContext());
        fileChangeView.setFileData(fileData);
        filesContainer.addView(fileChangeView);
    }
    
    public void setOnProposalActionListener(OnProposalActionListener listener) {
        this.listener = listener;
    }
    
    public void hideProposal() {
        setVisibility(GONE);
    }
    
    public void showSuccessState(String message, List<JSONObject> affectedFiles) {
        explanationText.setText(message);
        acceptButton.setVisibility(GONE);
        discardButton.setVisibility(GONE);
        
        // Always show affected files in collapsed state - don't clear existing files
        if (affectedFiles != null && !affectedFiles.isEmpty()) {
            // Only add files if container is empty (avoid duplicates)
            if (filesContainer.getChildCount() == 0) {
                for (JSONObject fileData : affectedFiles) {
                    FileChangeView fileChangeView = new FileChangeView(getContext());
                    fileChangeView.setFileData(fileData);
                    fileChangeView.setExpanded(false); // Start collapsed
                    filesContainer.addView(fileChangeView);
                }
            }
        }
    }
}