package pro.sketchware.activities.ai.chat.actions;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.util.Map;

public class DeleteFileAction extends BaseUniversalAction {

    private static final String ACTION_NAME = "delete_file";
    private static final String PARAM_PATH = "path";
    private static final String PARAM_RECURSIVE = "recursive";

    @Override
    public String execute(Map<String, Object> parameters, String projectId, Context context) {
        String path = getStringParameter(parameters, PARAM_PATH, null);
        boolean recursive = getBooleanParameter(parameters, PARAM_RECURSIVE, false);

        if (path == null || path.trim().isEmpty()) {
            return createErrorResult("File path is required.");
        }

        File fileToDelete = new File(path);

        if (!fileToDelete.exists()) {
            return createErrorResult("File or directory does not exist at path: " + path);
        }

        if (fileToDelete.isDirectory() && !recursive) {
            File[] files = fileToDelete.listFiles();
            if (files != null && files.length > 0) {
                return createErrorResult("Directory is not empty. Use recursive delete if you want to delete it.");
            }
        }

        try {
            boolean deleted = deleteRecursively(fileToDelete);
            if (deleted) {
                return createSuccessResult(ACTION_NAME, null, "File or directory deleted successfully at path: " + path);
            } else {
                return createErrorResult("Failed to delete file or directory at path: " + path);
            }
        } catch (Exception e) {
            return createErrorResult("An error occurred while deleting the file or directory: " + e.getMessage());
        }
    }

    private boolean deleteRecursively(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] files = fileOrDirectory.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursively(child);
                }
            }
        }
        return fileOrDirectory.delete();
    }

    @Override
    public String getActionName() {
        return ACTION_NAME;
    }

    @Override
    public String getDescription() {
        return "Deletes a file or a directory. If the directory is not empty, recursive must be set to true.";
    }

    @Override
    public JSONObject getParametersSchema() {
        try {
            JSONObject schema = new JSONObject();
            schema.put("type", "object");

            JSONObject properties = new JSONObject();
            properties.put(PARAM_PATH, new JSONObject().put("type", "string").put("description", "The absolute path of the file or directory to delete."));
            properties.put(PARAM_RECURSIVE, new JSONObject().put("type", "boolean").put("description", "If true, deletes a non-empty directory recursively. Default is false.").put("optional", true));
            schema.put("properties", properties);

            JSONArray required = new JSONArray();
            required.put(PARAM_PATH);
            schema.put("required", required);

            return schema;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    @Override
    public boolean isDestructive() {
        return true;
    }
}
