package pro.sketchware.activities.ai.chat.actions;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import a.a.a.GB;
import a.a.a.lC;
import a.a.a.nB;
import a.a.a.oB;
import a.a.a.wq;
import mod.hey.studios.project.ProjectSettings;
import mod.hey.studios.util.ProjectFile;
import pro.sketchware.activities.ai.chat.models.AgenticAction;

import static mod.hey.studios.util.ProjectFile.getDefaultColor;

public class CreateProjectAction implements AgenticAction {
    private static final String TAG = "CreateProjectAction";

    @Override
    public String getActionName() {
        return "create_project";
    }

    @Override
    public String getDescription() {
        return "Creates a new SketchwarePro project with specified name, package name, and app name";
    }

    @Override
    public boolean canExecute(String projectId, Context context) {
        return true; // Can always create new projects
    }

    @Override
    public String execute(Map<String, Object> parameters, String projectId, Context context) {
        try {
            // Extract parameters with defaults
            String projectName = (String) parameters.get("project_name");
            if (projectName == null || projectName.trim().isEmpty()) {
                projectName = generateDefaultProjectName();
            }
            String appName = (String) parameters.get("app_name");
            if (appName == null || appName.trim().isEmpty()) {
                appName = projectName;
            }
            String packageName = (String) parameters.get("package_name");
            if (packageName == null || packageName.trim().isEmpty()) {
                packageName = generateDefaultPackageName(projectName);
            }
            
            // Validate parameters
            if (projectName == null || projectName.trim().isEmpty()) {
                return "Error: Project name cannot be empty";
            }
            
            if (packageName == null || !isValidPackageName(packageName)) {
                packageName = generateDefaultPackageName(projectName);
            }
            
            if (appName == null || appName.trim().isEmpty()) {
                appName = projectName;
            }
            
            // Generate new project ID
            String sc_id = lC.b();
            
            // Create project metadata
            HashMap<String, Object> projectData = new HashMap<>();
            projectData.put("sc_id", sc_id);
            projectData.put("my_sc_pkg_name", packageName);
            projectData.put("my_ws_name", projectName);
            projectData.put("my_app_name", appName);
            projectData.put("my_sc_reg_dt", new nB().a("yyyyMMddHHmmss"));
            projectData.put("custom_icon", false);
            projectData.put("isIconAdaptive", false);
            projectData.put("sc_ver_code", "1");
            projectData.put("sc_ver_name", "1.0");
            projectData.put("sketchware_ver", GB.d(context));
            
            // Set default theme colors
            projectData.put("color_accent", getDefaultColor(ProjectFile.COLOR_ACCENT));
            projectData.put("color_primary", getDefaultColor(ProjectFile.COLOR_PRIMARY));
            projectData.put("color_primary_dark", getDefaultColor(ProjectFile.COLOR_PRIMARY_DARK));
            projectData.put("color_control_highlight", getDefaultColor(ProjectFile.COLOR_CONTROL_HIGHLIGHT));
            projectData.put("color_control_normal", getDefaultColor(ProjectFile.COLOR_CONTROL_NORMAL));
            
            // Save project data
            lC.a(sc_id, projectData);
            
            // Initialize project structure
            wq.a(context, sc_id);
            new oB().b(wq.b(sc_id));
            
            // Set project settings
            ProjectSettings projectSettings = new ProjectSettings(sc_id);
            projectSettings.setValue(ProjectSettings.SETTING_NEW_XML_COMMAND, ProjectSettings.SETTING_GENERIC_VALUE_TRUE);
            projectSettings.setValue(ProjectSettings.SETTING_ENABLE_VIEWBINDING, ProjectSettings.SETTING_GENERIC_VALUE_TRUE);
            
            Log.d(TAG, "Created project: " + projectName + " with ID: " + sc_id);
            
            // Return success message with project details
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("project_id", sc_id);
            result.put("project_name", projectName);
            result.put("app_name", appName);
            result.put("package_name", packageName);
            result.put("message", "Project '" + projectName + "' created successfully!");
            
            return result.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating project", e);
            return "Error creating project: " + e.getMessage();
        }
    }
    
    private String generateDefaultProjectName() {
        return lC.c(); // Uses existing SketchwarePro logic for generating project names
    }
    
    private String generateDefaultPackageName(String projectName) {
        // Clean project name and make it a valid package name
        String cleanName = projectName.toLowerCase()
                .replaceAll("[^a-z0-9]", "")
                .replaceAll("^[0-9]+", ""); // Remove leading numbers
        
        if (cleanName.isEmpty()) {
            cleanName = "myapp";
        }
        
        return "com.my." + cleanName;
    }
    
    private boolean isValidPackageName(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return false;
        }
        
        // Basic package name validation
        String[] parts = packageName.split("\\.");
        if (parts.length < 2) {
            return false;
        }
        
        for (String part : parts) {
            if (part.isEmpty() || !part.matches("^[a-zA-Z][a-zA-Z0-9_]*$")) {
                return false;
            }
        }
        
        return true;
    }
}