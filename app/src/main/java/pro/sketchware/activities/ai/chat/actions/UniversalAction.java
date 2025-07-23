package pro.sketchware.activities.ai.chat.actions;

import android.content.Context;

import org.json.JSONObject;

import java.util.Map;

/**
 * Universal action interface for AI-driven file system operations
 */
public interface UniversalAction {
    
    /**
     * Execute the action with given parameters
     * @param parameters Action parameters from AI
     * @param projectId Current project ID context
     * @param context Android context
     * @return JSON result string with operation result
     */
    String execute(Map<String, Object> parameters, String projectId, Context context);
    
    /**
     * Check if this action can be executed in the current context
     * @param projectId Current project ID
     * @param context Android context
     * @return true if action can be executed
     */
    boolean canExecute(String projectId, Context context);
    
    /**
     * Get the action name/identifier
     * @return action name
     */
    String getActionName();
    
    /**
     * Get a description of what this action does
     * @return human-readable description
     */
    String getDescription();
    
    /**
     * Get the parameters schema for this action
     * @return JSON schema describing expected parameters
     */
    JSONObject getParametersSchema();
    
    /**
     * Validate parameters before execution
     * @param parameters Parameters to validate
     * @return true if parameters are valid
     */
    boolean validateParameters(Map<String, Object> parameters);
    
    /**
     * Check if this action is destructive (requires confirmation)
     * @return true if action modifies/deletes data
     */
    boolean isDestructive();
    
    /**
     * Get the category of this action for organization
     * @return category string
     */
    String getCategory();
}