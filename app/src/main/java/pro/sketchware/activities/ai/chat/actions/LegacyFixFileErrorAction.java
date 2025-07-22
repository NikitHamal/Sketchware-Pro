package pro.sketchware.activities.ai.chat.actions;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

/**
 * Legacy wrapper for FixFileErrorAction to maintain backward compatibility
 */
public class LegacyFixFileErrorAction extends BaseUniversalAction {
    
    private final FixFileErrorAction legacyAction;
    
    public LegacyFixFileErrorAction() {
        this.legacyAction = new FixFileErrorAction();
    }
    
    @Override
    public String execute(Map<String, Object> parameters, String projectId, Context context) {
        // Delegate to the original FixFileErrorAction
        return legacyAction.execute(parameters, projectId, context);
    }
    
    @Override
    public boolean canExecute(String projectId, Context context) {
        return legacyAction.canExecute(projectId, context);
    }
    
    @Override
    public String getActionName() {
        return "fix_file_error";
    }
    
    @Override
    public String getDescription() {
        return legacyAction.getDescription();
    }
    
    @Override
    public JSONObject getParametersSchema() {
        try {
            JSONObject schema = new JSONObject();
            schema.put("action", "string (required) - Action type: create_file, edit_file, delete_file, create_directory");
            schema.put("file_path", "string (required) - Path to the file/directory");
            schema.put("content", "string (optional) - File content for create/edit operations");
            schema.put("explanation", "string (optional) - Description of the change");
            return schema;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }
    
    @Override
    public boolean validateParameters(Map<String, Object> parameters) {
        return hasRequiredParameter(parameters, "action") && 
               hasRequiredParameter(parameters, "file_path");
    }
    
    @Override
    public boolean isDestructive() {
        return true; // File operations are potentially destructive
    }
    
    @Override
    public String getCategory() {
        return "legacy";
    }
}