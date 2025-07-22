package pro.sketchware.activities.ai.chat.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONException;
import org.json.JSONObject;

import pro.sketchware.R;

public class FixProposalView extends LinearLayout {
    
    private TextView filePathText;
    private TextView explanationText;
    private TextView codeDiffText;
    private MaterialButton acceptButton;
    private MaterialButton discardButton;
    private MaterialCardView codeDiffCard;
    
    private OnProposalActionListener listener;
    private JSONObject proposalData;
    
    public interface OnProposalActionListener {
        void onAccept(JSONObject proposalData);
        void onDiscard(JSONObject proposalData);
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
        
        filePathText = findViewById(R.id.filePathText);
        explanationText = findViewById(R.id.explanationText);
        codeDiffText = findViewById(R.id.codeDiffText);
        acceptButton = findViewById(R.id.acceptButton);
        discardButton = findViewById(R.id.discardButton);
        codeDiffCard = findViewById(R.id.codeDiffCard);
        
        acceptButton.setOnClickListener(v -> {
            if (listener != null && proposalData != null) {
                listener.onAccept(proposalData);
            }
        });
        
        discardButton.setOnClickListener(v -> {
            if (listener != null && proposalData != null) {
                listener.onDiscard(proposalData);
            }
        });
    }
    
    public void setProposal(String explanation, JSONObject actionData) {
        this.proposalData = actionData;
        
        try {
            // Set explanation
            explanationText.setText(explanation);
            
            // Extract and display file information
            String filePath = actionData.optString("file_path", "");
            String content = actionData.optString("content", "");
            
            // Update file path
            if (!filePath.isEmpty()) {
                String fileName = filePath.substring(filePath.lastIndexOf("/") + 1);
                filePathText.setText(fileName + "\n" + filePath);
            }
            
            // Show code preview if available
            if (!content.isEmpty()) {
                codeDiffText.setText(content);
                codeDiffCard.setVisibility(VISIBLE);
            } else {
                codeDiffCard.setVisibility(GONE);
            }
            
        } catch (Exception e) {
            explanationText.setText("Error parsing proposal: " + e.getMessage());
        }
    }
    
    public void setOnProposalActionListener(OnProposalActionListener listener) {
        this.listener = listener;
    }
    
    public void hideProposal() {
        setVisibility(GONE);
    }
    
    public void showSuccessState(String message) {
        explanationText.setText(message);
        acceptButton.setVisibility(GONE);
        discardButton.setText("Close");
    }
}