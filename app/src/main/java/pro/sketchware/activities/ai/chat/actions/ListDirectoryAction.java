package pro.sketchware.activities.ai.chat.actions;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Arrays;
import java.util.Map;

/**
 * Action to list directory contents
 */
public class ListDirectoryAction extends BaseUniversalAction {
    
    @Override
    public String execute(Map<String, Object> parameters, String projectId, Context context) {
        try {
            if (!validateParameters(parameters)) {
                return createErrorResult("Invalid parameters for list_directory action");
            }
            
            String dirPath = getStringParameter(parameters, "path", null);
            boolean recursive = getBooleanParameter(parameters, "recursive", false);
            String fileFilter = getStringParameter(parameters, "filter", null);
            boolean includeHidden = getBooleanParameter(parameters, "include_hidden", false);
            
            if (!isValidProjectPath(dirPath, projectId)) {
                return createErrorResult("Invalid directory path: " + dirPath);
            }
            
            File directory = new File(dirPath);
            if (!directory.exists()) {
                return createErrorResult("Directory does not exist: " + dirPath);
            }
            
            if (!directory.isDirectory()) {
                return createErrorResult("Path is not a directory: " + dirPath);
            }
            
            JSONArray files = new JSONArray();
            listDirectory(directory, files, recursive, fileFilter, includeHidden, 0, 50); // Max 50 files
            
            JSONObject result = new JSONObject();
            result.put("path", dirPath);
            result.put("files", files);
            result.put("total_count", files.length());
            result.put("recursive", recursive);
            
            logAction(getActionName(), parameters, "Successfully listed directory: " + dirPath);
            return createSuccessResult(getActionName(), result, "Directory listed successfully");
            
        } catch (Exception e) {
            return createErrorResult("Error listing directory: " + e.getMessage());
        }
    }
    
    private void listDirectory(File dir, JSONArray result, boolean recursive, String filter, 
                             boolean includeHidden, int depth, int maxFiles) throws JSONException {
        if (result.length() >= maxFiles || depth > 10) { // Prevent deep recursion
            return;
        }
        
        File[] files = dir.listFiles();
        if (files == null) return;
        
        Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        
        for (File file : files) {
            if (result.length() >= maxFiles) break;
            
            if (!includeHidden && file.isHidden()) continue;
            
            if (filter != null && !file.getName().toLowerCase().contains(filter.toLowerCase())) {
                continue;
            }
            
            JSONObject fileInfo = new JSONObject();
            fileInfo.put("name", file.getName());
            fileInfo.put("path", file.getAbsolutePath());
            fileInfo.put("is_directory", file.isDirectory());
            fileInfo.put("size", file.isFile() ? file.length() : 0);
            fileInfo.put("last_modified", file.lastModified());
            fileInfo.put("readable", file.canRead());
            fileInfo.put("writable", file.canWrite());
            
            if (file.isFile()) {
                fileInfo.put("extension", getFileExtension(file.getName()));
            }
            
            result.put(fileInfo);
            
            if (recursive && file.isDirectory()) {
                listDirectory(file, result, true, filter, includeHidden, depth + 1, maxFiles);
            }
        }
    }
    
    @Override
    public boolean validateParameters(Map<String, Object> parameters) {
        return hasRequiredParameter(parameters, "path");
    }
    
    @Override
    public String getActionName() {
        return "list_directory";
    }
    
    @Override
    public String getDescription() {
        return "List files and folders in a directory with optional filtering and recursion";
    }
    
    @Override
    public JSONObject getParametersSchema() {
        try {
            JSONObject schema = new JSONObject();
            schema.put("path", "string (required) - Path to the directory to list");
            schema.put("recursive", "boolean (optional) - Include subdirectories, default false");
            schema.put("filter", "string (optional) - Filter files by name substring");
            schema.put("include_hidden", "boolean (optional) - Include hidden files, default false");
            return schema;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }
    
    @Override
    public boolean isDestructive() {
        return false; // Listing is non-destructive
    }
    
    @Override
    public String getCategory() {
        return "file_read";
    }
}