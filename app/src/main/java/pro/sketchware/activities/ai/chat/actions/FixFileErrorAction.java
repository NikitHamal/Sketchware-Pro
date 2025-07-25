package pro.sketchware.activities.ai.chat.actions;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import pro.sketchware.activities.ai.chat.models.AgenticAction;

public class FixFileErrorAction implements AgenticAction {
    private static final String TAG = "FixFileErrorAction";

    @Override
    public String execute(Map<String, Object> parameters, String projectId, Context context) {
        try {
            Log.d(TAG, "FixFileErrorAction.execute called with parameters: " + parameters.toString());
            Log.d(TAG, "ProjectId: " + projectId);
            
            // Debug all parameter keys
            Log.d(TAG, "Available parameter keys: " + parameters.keySet());
            for (String key : parameters.keySet()) {
                Log.d(TAG, "Parameter '" + key + "': " + parameters.get(key));
            }
            
            String action = (String) parameters.get("action");
            String filePath = (String) parameters.get("file_path");
            // Fallback to "path" if "file_path" is not found (for compatibility)
            if (filePath == null) {
                filePath = (String) parameters.get("path");
                Log.d(TAG, "Using fallback 'path' parameter: " + filePath);
            }
            String content = (String) parameters.get("content");
            String explanation = (String) parameters.get("explanation");
            
            Log.d(TAG, "Extracted - action: " + action + ", filePath: " + filePath + ", content length: " + (content != null ? content.length() : "null"));
            Log.d(TAG, "Content preview (first 200 chars): " + (content != null && content.length() > 0 ? content.substring(0, Math.min(200, content.length())) : "null/empty"));
            
            if (action == null || filePath == null) {
                Log.e(TAG, "Missing required parameters - action: " + action + ", filePath: " + filePath);
                return createErrorResult("Missing required parameters: action or file_path/path");
            }
            
            // Validate file path is within project
            if (!isValidProjectPath(filePath, projectId)) {
                Log.e(TAG, "Invalid project path: " + filePath + " for project: " + projectId);
                return createErrorResult("File path is not within the project directory");
            }
            
            File file = new File(filePath);
            Log.d(TAG, "Working with file: " + file.getAbsolutePath());
            
            switch (action.toLowerCase()) {
                case "create_file":
                    return createFile(file, content, explanation);
                    
                case "edit_file":
                    return editFile(file, content, explanation);
                    
                case "delete_file":
                    return deleteFile(file, explanation);
                    
                case "create_directory":
                    return createDirectory(file, explanation);
                    
                default:
                    return createErrorResult("Unknown action: " + action);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error executing file fix action", e);
            return createErrorResult("Error executing action: " + e.getMessage());
        }
    }
    
    private boolean isValidProjectPath(String filePath, String projectId) {
        if (filePath == null) {
            Log.d(TAG, "Path validation failed: filePath is null");
            return false;
        }
        
        Log.d(TAG, "Validating path: " + filePath + " for project: " + projectId);
        
        // More lenient path validation - allow any path that looks like a project file
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
               filePath.startsWith("/");  // Allow absolute paths
        
        Log.d(TAG, "Path validation result: " + isValid);
        return isValid;
    }
    
    private String createFile(File file, String content, String explanation) {
        try {
            // Create parent directories if they don't exist
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                Log.d(TAG, "Creating parent directories: " + parentDir.getAbsolutePath() + " - Success: " + created);
                if (!created && !parentDir.exists()) {
                    return createErrorResult("Failed to create parent directories: " + parentDir.getAbsolutePath());
                }
            }
            
            // Check if file already exists
            if (file.exists()) {
                Log.w(TAG, "File already exists, will overwrite: " + file.getAbsolutePath());
            }
            
            // Create the file and write content
            try (FileWriter writer = new FileWriter(file, false)) { // false = overwrite existing file
                if (content != null && !content.trim().isEmpty()) {
                    writer.write(content);
                } else {
                    // Write minimal valid content if none provided
                    writer.write("");
                }
                writer.flush(); // Ensure content is written to disk
            }
            
            // Force sync to ensure file is written to storage
            try {
                Runtime.getRuntime().exec("sync").waitFor();
            } catch (Exception syncError) {
                Log.w(TAG, "Sync command failed, continuing", syncError);
            }
            
            // Verify file was created/updated with a small delay to allow filesystem operations
            Thread.sleep(100);
            if (file.exists()) {
                Log.d(TAG, "Successfully created/updated file: " + file.getAbsolutePath() + " (size: " + file.length() + " bytes)");
            } else {
                Log.e(TAG, "File was not created: " + file.getAbsolutePath());
                return createErrorResult("File was not created on disk");
            }
            
            return createSuccessResult(
                "create_file",
                file.getAbsolutePath(),
                "Created file: " + file.getName(),
                explanation != null ? explanation : "File created successfully"
            );
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating file: " + file.getAbsolutePath(), e);
            return createErrorResult("Failed to create file: " + e.getMessage());
        }
    }
    
    private String editFile(File file, String content, String explanation) {
        try {
            // Create parent directories if needed
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                Log.d(TAG, "Creating parent directories for edit: " + parentDir.getAbsolutePath() + " - Success: " + created);
                if (!created && !parentDir.exists()) {
                    return createErrorResult("Failed to create parent directories: " + parentDir.getAbsolutePath());
                }
            }
            
            // Backup original file if it exists
            if (file.exists()) {
                File backupFile = new File(file.getAbsolutePath() + ".backup." + System.currentTimeMillis());
                try {
                    Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    Log.d(TAG, "Created backup: " + backupFile.getAbsolutePath());
                } catch (IOException backupError) {
                    Log.w(TAG, "Failed to create backup, continuing with edit", backupError);
                }
            }
            
            // Write new content
            Log.d(TAG, "About to write content to file: " + file.getAbsolutePath());
            Log.d(TAG, "Content length: " + (content != null ? content.length() : "null"));
            try (FileWriter writer = new FileWriter(file, false)) { // false = overwrite
                if (content != null) {
                    writer.write(content);
                    Log.d(TAG, "Content written successfully");
                } else {
                    writer.write("");
                    Log.d(TAG, "Empty content written");
                }
                writer.flush(); // Ensure content is written to disk
                Log.d(TAG, "File writer flushed");
            }
            
            // Force sync to ensure file is written to storage
            try {
                Runtime.getRuntime().exec("sync").waitFor();
            } catch (Exception syncError) {
                Log.w(TAG, "Sync command failed, continuing", syncError);
            }
            
            // Verify file was updated with a small delay
            Thread.sleep(100);
            if (file.exists()) {
                Log.d(TAG, "Successfully edited file: " + file.getAbsolutePath() + " (size: " + file.length() + " bytes)");
            } else {
                Log.e(TAG, "File disappeared after edit: " + file.getAbsolutePath());
                return createErrorResult("File disappeared after edit");
            }
            
            return createSuccessResult(
                "edit_file",
                file.getAbsolutePath(),
                "Edited file: " + file.getName(),
                explanation != null ? explanation : "File edited successfully"
            );
            
        } catch (Exception e) {
            Log.e(TAG, "Error editing file: " + file.getAbsolutePath(), e);
            return createErrorResult("Failed to edit file: " + e.getMessage());
        }
    }
    
    private String deleteFile(File file, String explanation) {
        try {
            if (!file.exists()) {
                return createErrorResult("File does not exist: " + file.getName());
            }
            
            // Create backup before deleting
            File backupFile = new File(file.getAbsolutePath() + ".deleted");
            Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            
            if (file.delete()) {
                Log.d(TAG, "Deleted file: " + file.getAbsolutePath());
                
                return createSuccessResult(
                    "delete_file",
                    file.getAbsolutePath(),
                    "Deleted file: " + file.getName(),
                    explanation != null ? explanation : "File deleted successfully"
                );
            } else {
                return createErrorResult("Failed to delete file: " + file.getName());
            }
            
        } catch (IOException e) {
            Log.e(TAG, "Error deleting file: " + file.getAbsolutePath(), e);
            return createErrorResult("Failed to delete file: " + e.getMessage());
        }
    }
    
    private String createDirectory(File dir, String explanation) {
        try {
            if (dir.exists()) {
                return createErrorResult("Directory already exists: " + dir.getName());
            }
            
            if (dir.mkdirs()) {
                Log.d(TAG, "Created directory: " + dir.getAbsolutePath());
                
                return createSuccessResult(
                    "create_directory",
                    dir.getAbsolutePath(),
                    "Created directory: " + dir.getName(),
                    explanation != null ? explanation : "Directory created successfully"
                );
            } else {
                return createErrorResult("Failed to create directory: " + dir.getName());
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating directory: " + dir.getAbsolutePath(), e);
            return createErrorResult("Failed to create directory: " + e.getMessage());
        }
    }
    
    private String createSuccessResult(String action, String filePath, String message, String explanation) {
        try {
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("action", action);
            result.put("file_path", filePath);
            result.put("message", message);
            result.put("explanation", explanation);
            return result.toString();
        } catch (JSONException e) {
            return "{\"success\":true,\"message\":\"" + message + "\"}";
        }
    }
    
    private String createErrorResult(String error) {
        try {
            JSONObject result = new JSONObject();
            result.put("success", false);
            result.put("error", error);
            return result.toString();
        } catch (JSONException e) {
            return "{\"success\":false,\"error\":\"" + error + "\"}";
        }
    }

    @Override
    public boolean canExecute(String projectId, Context context) {
        // Check if we have file system permissions
        try {
            String testPath = "/storage/emulated/0/.sketchware/data/" + projectId;
            File testDir = new File(testPath);
            return testDir.exists() || testDir.getParentFile().canWrite();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Fix file errors by creating, editing, or deleting files within the project";
    }

    @Override
    public String getActionName() {
        return "fix_file_error";
    }
}