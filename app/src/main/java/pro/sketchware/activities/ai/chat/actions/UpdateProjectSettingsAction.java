package pro.sketchware.activities.ai.chat.actions;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import a.a.a.lC;
import a.a.a.yB;
import mod.hey.studios.project.ProjectSettings;
import pro.sketchware.activities.ai.chat.models.AgenticAction;

public class UpdateProjectSettingsAction implements AgenticAction {
    private static final String TAG = "UpdateProjectSettingsAction";

    @Override
    public String getActionName() {
        return "update_project_settings";
    }

    @Override
    public String getDescription() {
        return "Update project settings including name, package name, SDK versions, view binding, etc.";
    }

    @Override
    public boolean canExecute(String projectId, Context context) {
        return projectId != null && !projectId.isEmpty() && lC.b(projectId) != null;
    }

    @Override
    public String execute(Map<String, Object> parameters, String projectId, Context context) {
        try {
            JSONObject result = new JSONObject();
            result.put("action", getActionName());
            result.put("success", false);

            // Get current project data
            HashMap<String, Object> projectData = lC.b(projectId);
            if (projectData == null) {
                result.put("error", "Project not found: " + projectId);
                return result.toString();
            }

            boolean hasChanges = false;
            StringBuilder changes = new StringBuilder();

            // Update basic project information
            if (parameters.containsKey("project_name")) {
                String newProjectName = (String) parameters.get("project_name");
                String oldProjectName = yB.c(projectData, "my_ws_name");
                if (!newProjectName.equals(oldProjectName)) {
                    projectData.put("my_ws_name", newProjectName);
                    changes.append("- Project name changed from '").append(oldProjectName)
                           .append("' to '").append(newProjectName).append("'\n");
                    hasChanges = true;
                }
            }

            if (parameters.containsKey("app_name")) {
                String newAppName = (String) parameters.get("app_name");
                String oldAppName = yB.c(projectData, "my_app_name");
                if (!newAppName.equals(oldAppName)) {
                    projectData.put("my_app_name", newAppName);
                    changes.append("- App name changed from '").append(oldAppName)
                           .append("' to '").append(newAppName).append("'\n");
                    hasChanges = true;
                }
            }

            if (parameters.containsKey("package_name")) {
                String newPackageName = (String) parameters.get("package_name");
                String oldPackageName = yB.c(projectData, "my_sc_pkg_name");
                if (!newPackageName.equals(oldPackageName)) {
                    projectData.put("my_sc_pkg_name", newPackageName);
                    changes.append("- Package name changed from '").append(oldPackageName)
                           .append("' to '").append(newPackageName).append("'\n");
                    hasChanges = true;
                }
            }

            if (parameters.containsKey("version_code")) {
                String newVersionCode = String.valueOf(parameters.get("version_code"));
                String oldVersionCode = yB.c(projectData, "sc_ver_code");
                if (!newVersionCode.equals(oldVersionCode)) {
                    projectData.put("sc_ver_code", newVersionCode);
                    changes.append("- Version code changed from ").append(oldVersionCode)
                           .append(" to ").append(newVersionCode).append("\n");
                    hasChanges = true;
                }
            }

            if (parameters.containsKey("version_name")) {
                String newVersionName = (String) parameters.get("version_name");
                String oldVersionName = yB.c(projectData, "sc_ver_name");
                if (!newVersionName.equals(oldVersionName)) {
                    projectData.put("sc_ver_name", newVersionName);
                    changes.append("- Version name changed from '").append(oldVersionName)
                           .append("' to '").append(newVersionName).append("'\n");
                    hasChanges = true;
                }
            }

            // Update project settings (SDK, view binding, etc.)
            ProjectSettings settings = new ProjectSettings(projectId);
            
            if (parameters.containsKey("minimum_sdk")) {
                String newMinSdk = String.valueOf(parameters.get("minimum_sdk"));
                String oldMinSdk = String.valueOf(settings.getMinSdkVersion());
                if (!newMinSdk.equals(oldMinSdk)) {
                    settings.setValue(ProjectSettings.SETTING_MINIMUM_SDK_VERSION, newMinSdk);
                    changes.append("- Minimum SDK changed from ").append(oldMinSdk)
                           .append(" to ").append(newMinSdk).append("\n");
                    hasChanges = true;
                }
            }

            if (parameters.containsKey("target_sdk")) {
                String newTargetSdk = String.valueOf(parameters.get("target_sdk"));
                String oldTargetSdk = settings.getValue(ProjectSettings.SETTING_TARGET_SDK_VERSION, "34");
                if (!newTargetSdk.equals(oldTargetSdk)) {
                    settings.setValue(ProjectSettings.SETTING_TARGET_SDK_VERSION, newTargetSdk);
                    changes.append("- Target SDK changed from ").append(oldTargetSdk)
                           .append(" to ").append(newTargetSdk).append("\n");
                    hasChanges = true;
                }
            }

            if (parameters.containsKey("application_class")) {
                String newAppClass = (String) parameters.get("application_class");
                String oldAppClass = settings.getValue(ProjectSettings.SETTING_APPLICATION_CLASS, ".SketchApplication");
                if (!newAppClass.equals(oldAppClass)) {
                    settings.setValue(ProjectSettings.SETTING_APPLICATION_CLASS, newAppClass);
                    changes.append("- Application class changed from '").append(oldAppClass)
                           .append("' to '").append(newAppClass).append("'\n");
                    hasChanges = true;
                }
            }

            if (parameters.containsKey("enable_view_binding")) {
                boolean newViewBinding = Boolean.parseBoolean(String.valueOf(parameters.get("enable_view_binding")));
                boolean oldViewBinding = settings.getValue(ProjectSettings.SETTING_ENABLE_VIEWBINDING, "false").equals("true");
                if (newViewBinding != oldViewBinding) {
                    settings.setValue(ProjectSettings.SETTING_ENABLE_VIEWBINDING, 
                                    newViewBinding ? "true" : "false");
                    changes.append("- View binding ").append(newViewBinding ? "enabled" : "disabled").append("\n");
                    hasChanges = true;
                }
            }

            if (parameters.containsKey("remove_old_methods")) {
                boolean newRemoveOld = Boolean.parseBoolean(String.valueOf(parameters.get("remove_old_methods")));
                boolean oldRemoveOld = settings.getValue(ProjectSettings.SETTING_DISABLE_OLD_METHODS, "false").equals("true");
                if (newRemoveOld != oldRemoveOld) {
                    settings.setValue(ProjectSettings.SETTING_DISABLE_OLD_METHODS, 
                                    newRemoveOld ? "true" : "false");
                    changes.append("- Remove old deprecated methods ").append(newRemoveOld ? "enabled" : "disabled").append("\n");
                    hasChanges = true;
                }
            }

            if (parameters.containsKey("enable_material_components")) {
                boolean newMaterial = Boolean.parseBoolean(String.valueOf(parameters.get("enable_material_components")));
                boolean oldMaterial = settings.getValue(ProjectSettings.SETTING_ENABLE_BRIDGELESS_THEMES, "false").equals("true");
                if (newMaterial != oldMaterial) {
                    settings.setValue(ProjectSettings.SETTING_ENABLE_BRIDGELESS_THEMES, 
                                    newMaterial ? "true" : "false");
                    changes.append("- Material components ").append(newMaterial ? "enabled" : "disabled").append("\n");
                    hasChanges = true;
                }
            }

            if (hasChanges) {
                // Save project data changes
                lC.b(projectId, projectData);
                
                result.put("success", true);
                result.put("message", "Project settings updated successfully");
                result.put("changes", changes.toString().trim());
                result.put("project_id", projectId);
                
                Log.d(TAG, "Updated project " + projectId + " settings: " + changes.toString());
            } else {
                result.put("success", true);
                result.put("message", "No changes were needed - all settings are already as requested");
            }

            return result.toString();

        } catch (Exception e) {
            Log.e(TAG, "Error updating project settings", e);
            try {
                JSONObject errorResult = new JSONObject();
                errorResult.put("action", getActionName());
                errorResult.put("success", false);
                errorResult.put("error", "Failed to update project settings: " + e.getMessage());
                return errorResult.toString();
            } catch (Exception je) {
                return "{\"action\":\"" + getActionName() + "\",\"success\":false,\"error\":\"Failed to update project settings\"}";
            }
        }
    }
}