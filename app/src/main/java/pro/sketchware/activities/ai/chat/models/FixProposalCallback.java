package pro.sketchware.activities.ai.chat.models;

import org.json.JSONObject;

public interface FixProposalCallback {
    void onFixProposal(String explanation, JSONObject actionData);
    void onFixApplied(String result, String projectId);
    void onFixError(String error);
}