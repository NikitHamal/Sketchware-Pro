package pro.sketchware.activities.ai.chat.actions;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Manager for all Universal Actions
 */
public class UniversalActionManager {
    
    private static final String TAG = "UniversalActionManager";
    private final Map<String, UniversalAction> actions;
    private final Context context;
    
    public UniversalActionManager(Context context) {
        this.context = context;
        this.actions = new HashMap<>();
        registerActions();
    }
    
    private void registerActions() {
        // File operations
        registerAction(new ReadFileAction());
        registerAction(new EditFileAction());
        registerAction(new ListDirectoryAction());
        registerAction(new DeleteFileAction());
        
        // Android development
        registerAction(new CreateJavaFileAction());
        registerAction(new CreateXMLResourceAction());
        
        // Legacy compatibility
        registerAction(new LegacyFixFileErrorAction());
        
        Log.d(TAG, "Registered " + actions.size() + " universal actions");
    }
    
    private void registerAction(UniversalAction action) {
        actions.put(action.getActionName(), action);
        Log.d(TAG, "Registered action: " + action.getActionName() + " (" + action.getDescription() + ")");
    }
    
    /**
     * Execute an action by name
     */
    public String executeAction(String actionName, Map<String, Object> parameters, String projectId) {
        try {
            UniversalAction action = actions.get(actionName);
            if (action == null) {
                return createErrorResult("Unknown action: " + actionName);
            }
            
            if (!action.canExecute(projectId, context)) {
                return createErrorResult("Action cannot be executed in current context: " + actionName);
            }
            
            if (!action.validateParameters(parameters)) {
                return createErrorResult("Invalid parameters for action: " + actionName);
            }
            
            Log.d(TAG, "Executing action: " + actionName + " with parameters: " + parameters);
            String result = action.execute(parameters, projectId, context);
            Log.d(TAG, "Action " + actionName + " completed");
            
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "Error executing action: " + actionName, e);
            return createErrorResult("Action execution failed: " + e.getMessage());
        }
    }
    
    /**
     * Get all available actions
     */
    public Map<String, UniversalAction> getAvailableActions() {
        return new HashMap<>(actions);
    }
    
    /**
     * Get actions by category
     */
    public Map<String, UniversalAction> getActionsByCategory(String category) {
        Map<String, UniversalAction> result = new HashMap<>();
        for (Map.Entry<String, UniversalAction> entry : actions.entrySet()) {
            if (category.equals(entry.getValue().getCategory())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
    
    /**
     * Get action schemas for AI context
     */
    public JSONObject getActionSchemas() {
        try {
            JSONObject schemas = new JSONObject();
            JSONArray actionList = new JSONArray();
            
            for (UniversalAction action : actions.values()) {
                JSONObject actionInfo = new JSONObject();
                actionInfo.put("name", action.getActionName());
                actionInfo.put("description", action.getDescription());
                actionInfo.put("category", action.getCategory());
                actionInfo.put("destructive", action.isDestructive());
                actionInfo.put("parameters", action.getParametersSchema());
                
                actionList.put(actionInfo);
            }
            
            schemas.put("actions", actionList);
            schemas.put("total_count", actionList.length());
            
            return schemas;
            
        } catch (JSONException e) {
            Log.e(TAG, "Error creating action schemas", e);
            return new JSONObject();
        }
    }
    
    /**
     * Check if an action exists
     */
    public boolean hasAction(String actionName) {
        return actions.containsKey(actionName);
    }
    
    /**
     * Get action description
     */
    public String getActionDescription(String actionName) {
        UniversalAction action = actions.get(actionName);
        return action != null ? action.getDescription() : "Unknown action";
    }
    
    private String createErrorResult(String error) {
        try {
            JSONObject result = new JSONObject();
            result.put("success", false);
            result.put("error", error);
            result.put("timestamp", System.currentTimeMillis());
            return result.toString();
        } catch (JSONException e) {
            return "{\"success\":false,\"error\":\"" + error + "\"}";
        }
    }
}