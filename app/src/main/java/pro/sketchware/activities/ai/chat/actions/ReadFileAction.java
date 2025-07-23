package pro.sketchware.activities.ai.chat.actions;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

/**
 * Action to read file contents
 */
public class ReadFileAction extends BaseUniversalAction {
    
    @Override
    public String execute(Map<String, Object> parameters, String projectId, Context context) {
        try {
            if (!validateParameters(parameters)) {
                return createErrorResult("Invalid parameters for read_file action");
            }
            
            String filePath = getStringParameter(parameters, "file_path", null);
            String encoding = getStringParameter(parameters, "encoding", "UTF-8");
            String maxSizeStr = getStringParameter(parameters, "max_size", "1048576");
            int maxSize = Integer.parseInt(maxSizeStr); // 1MB default
            
            if (!isValidProjectPath(filePath, projectId)) {
                return createErrorResult("Invalid file path: " + filePath);
            }
            
            File file = new File(filePath);
            if (!file.exists()) {
                return createErrorResult("File does not exist: " + filePath);
            }
            
            if (!file.isFile()) {
                return createErrorResult("Path is not a file: " + filePath);
            }
            
            if (file.length() > maxSize) {
                return createErrorResult("File too large: " + file.length() + " bytes (max: " + maxSize + ")");
            }
            
            String content = readFileContent(file, encoding);
            
            JSONObject result = new JSONObject();
            result.put("file_path", filePath);
            result.put("content", content);
            result.put("size", file.length());
            result.put("last_modified", file.lastModified());
            result.put("encoding", encoding);
            
            logAction(getActionName(), parameters, "Successfully read file: " + filePath);
            return createSuccessResult(getActionName(), result, "File read successfully");
            
        } catch (Exception e) {
            return createErrorResult("Error reading file: " + e.getMessage());
        }
    }
    
    private String readFileContent(File file, String encoding) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }
    
    @Override
    public boolean validateParameters(Map<String, Object> parameters) {
        return hasRequiredParameter(parameters, "file_path");
    }
    
    @Override
    public String getActionName() {
        return "read_file";
    }
    
    @Override
    public String getDescription() {
        return "Read contents of a single file with optional encoding specification";
    }
    
    @Override
    public JSONObject getParametersSchema() {
        try {
            JSONObject schema = new JSONObject();
            schema.put("file_path", "string (required) - Path to the file to read");
            schema.put("encoding", "string (optional) - File encoding, default UTF-8");
            schema.put("max_size", "number (optional) - Maximum file size in bytes, default 1MB");
            return schema;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }
    
    @Override
    public boolean isDestructive() {
        return false; // Reading is non-destructive
    }
    
    @Override
    public String getCategory() {
        return "file_read";
    }
}