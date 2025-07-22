package pro.sketchware.activities.ai.chat.actions;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Map;

/**
 * Base class for UniversalAction implementations with common utilities
 */
public abstract class BaseUniversalAction implements UniversalAction {
    
    protected static final String TAG = "UniversalAction";
    
    protected boolean isValidProjectPath(String filePath, String projectId) {
        if (filePath == null) {
            Log.d(TAG, "Path validation failed: filePath is null");
            return false;
        }
        
        Log.d(TAG, "Validating path: " + filePath + " for project: " + projectId);
        
        // Allow any path that looks like a project file
        boolean isValid = filePath.contains("/storage/emulated/") || 
               filePath.contains("/.sketchware/") ||
               filePath.contains("/data/") ||
               filePath.contains("/files/") ||
               filePath.contains("/drawable/") ||
               filePath.contains("/layout/") ||
               filePath.contains("/values/") ||
               filePath.contains("/res/") ||
               filePath.contains("/java/") ||
               filePath.contains("/main/") ||
               filePath.contains("/app/") ||
               filePath.startsWith("/");
        
        Log.d(TAG, "Path validation result: " + isValid);
        return isValid;
    }
    
    protected String createSuccessResult(String action, Object data, String message) {
        try {
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("action", action);
            result.put("message", message);
            result.put("data", data);
            result.put("timestamp", System.currentTimeMillis());
            return result.toString();
        } catch (JSONException e) {
            return "{\"success\":true,\"message\":\"" + message + "\"}";
        }
    }
    
    protected String createErrorResult(String error) {
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
    
    protected void logAction(String action, Map<String, Object> parameters, String result) {
        Log.d(TAG, String.format("Action: %s, Parameters: %s, Result: %s", 
            action, parameters.toString(), result));
    }
    
    protected boolean hasRequiredParameter(Map<String, Object> parameters, String key) {
        return parameters.containsKey(key) && parameters.get(key) != null;
    }
    
    protected String getStringParameter(Map<String, Object> parameters, String key, String defaultValue) {
        Object value = parameters.get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    protected boolean getBooleanParameter(Map<String, Object> parameters, String key, boolean defaultValue) {
        Object value = parameters.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }
    
    protected long getFileSize(File file) {
        if (file.exists() && file.isFile()) {
            return file.length();
        }
        return 0;
    }
    
    protected String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1).toLowerCase() : "";
    }
    
    @Override
    public boolean canExecute(String projectId, Context context) {
        // Default implementation - can be overridden
        return true;
    }
    
    @Override
    public boolean validateParameters(Map<String, Object> parameters) {
        // Default implementation - can be overridden
        return true;
    }
    
    @Override
    public boolean isDestructive() {
        // Default implementation - safe actions
        return false;
    }
    
    @Override
    public String getCategory() {
        return "file_system";
    }
}