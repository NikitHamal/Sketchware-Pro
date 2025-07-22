package pro.sketchware.activities.ai.chat.actions;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Action to edit file contents
 */
public class EditFileAction extends BaseUniversalAction {
    
    @Override
    public String execute(Map<String, Object> parameters, String projectId, Context context) {
        try {
            if (!validateParameters(parameters)) {
                return createErrorResult("Invalid parameters for edit_file action");
            }
            
            String filePath = getStringParameter(parameters, "file_path", null);
            String content = getStringParameter(parameters, "content", "");
            String operation = getStringParameter(parameters, "operation", "overwrite");
            boolean createBackup = getBooleanParameter(parameters, "create_backup", true);
            
            if (!isValidProjectPath(filePath, projectId)) {
                return createErrorResult("Invalid file path: " + filePath);
            }
            
            File file = new File(filePath);
            
            // Create parent directories if needed
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                if (!created && !parentDir.exists()) {
                    return createErrorResult("Failed to create parent directories: " + parentDir.getAbsolutePath());
                }
            }
            
            // Create backup if file exists and backup is requested
            if (file.exists() && createBackup) {
                try {
                    File backupFile = new File(file.getAbsolutePath() + ".backup." + System.currentTimeMillis());
                    Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException backupError) {
                    // Continue without backup
                }
            }
            
            // Perform the edit operation
            String result = performEditOperation(file, content, operation);
            if (result != null) {
                return result; // Error occurred
            }
            
            // Force sync to ensure file is written to storage
            try {
                Runtime.getRuntime().exec("sync").waitFor();
                Thread.sleep(100); // Small delay for filesystem operations
            } catch (Exception syncError) {
                // Continue even if sync fails
            }
            
            // Verify file was updated
            if (!file.exists()) {
                return createErrorResult("File disappeared after edit");
            }
            
            JSONObject resultData = new JSONObject();
            resultData.put("file_path", filePath);
            resultData.put("operation", operation);
            resultData.put("size", file.length());
            resultData.put("last_modified", file.lastModified());
            
            logAction(getActionName(), parameters, "Successfully edited file: " + filePath);
            return createSuccessResult(getActionName(), resultData, "File edited successfully");
            
        } catch (Exception e) {
            return createErrorResult("Error editing file: " + e.getMessage());
        }
    }
    
    private String performEditOperation(File file, String content, String operation) throws IOException {
        switch (operation.toLowerCase()) {
            case "overwrite":
                try (FileWriter writer = new FileWriter(file, false)) {
                    writer.write(content != null ? content : "");
                    writer.flush();
                }
                break;
                
            case "append":
                try (FileWriter writer = new FileWriter(file, true)) {
                    writer.write(content != null ? content : "");
                    writer.flush();
                }
                break;
                
            case "prepend":
                String existingContent = "";
                if (file.exists()) {
                    existingContent = Files.readString(file.toPath());
                }
                try (FileWriter writer = new FileWriter(file, false)) {
                    writer.write((content != null ? content : "") + existingContent);
                    writer.flush();
                }
                break;
                
            default:
                return createErrorResult("Unknown operation: " + operation);
        }
        
        return null; // Success
    }
    
    @Override
    public boolean validateParameters(Map<String, Object> parameters) {
        return hasRequiredParameter(parameters, "file_path");
    }
    
    @Override
    public String getActionName() {
        return "edit_file";
    }
    
    @Override
    public String getDescription() {
        return "Edit file contents with overwrite, append, or prepend operations";
    }
    
    @Override
    public JSONObject getParametersSchema() {
        try {
            JSONObject schema = new JSONObject();
            schema.put("file_path", "string (required) - Path to the file to edit");
            schema.put("content", "string (optional) - Content to write");
            schema.put("operation", "string (optional) - Operation: overwrite (default), append, prepend");
            schema.put("create_backup", "boolean (optional) - Create backup before editing, default true");
            return schema;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }
    
    @Override
    public boolean isDestructive() {
        return true; // Editing is destructive
    }
    
    @Override
    public String getCategory() {
        return "file_write";
    }
}