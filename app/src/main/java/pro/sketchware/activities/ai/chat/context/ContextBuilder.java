package pro.sketchware.activities.ai.chat.context;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import a.a.a.lC;
import a.a.a.yB;
import pro.sketchware.activities.ai.chat.actions.CreateProjectAction;
import pro.sketchware.activities.ai.chat.actions.FixFileErrorAction;
import pro.sketchware.activities.ai.chat.actions.UpdateProjectSettingsAction;
import pro.sketchware.activities.ai.chat.actions.UniversalActionManager;
import pro.sketchware.activities.ai.chat.models.AgenticAction;
import pro.sketchware.activities.ai.chat.models.ConversationContext;

public class ContextBuilder {
    private static final String TAG = "ContextBuilder";
    private final Context context;
    private final Map<String, AgenticAction> availableActions;
    private final UniversalActionManager universalActionManager;

    public ContextBuilder(Context context) {
        this.context = context;
        this.availableActions = new HashMap<>();
        this.universalActionManager = new UniversalActionManager(context);
        registerActions();
    }

    private void registerActions() {
        registerAction(new CreateProjectAction());
        registerAction(new FixFileErrorAction());
        registerAction(new UpdateProjectSettingsAction());
        // More actions will be added here in future phases
    }

    private void registerAction(AgenticAction action) {
        availableActions.put(action.getActionName(), action);
    }

    public String buildEnhancedPrompt(String userMessage, ConversationContext conversationContext) {
        StringBuilder prompt = new StringBuilder();

        // System instructions
        prompt.append("SYSTEM_CONTEXT:\n");
        prompt.append("You are an advanced AI assistant integrated into SketchwarePro, an Android app development IDE.\n");
        prompt.append("You can execute actions to help users build Android applications.\n\n");

        // Action protocol
        prompt.append("ACTION_PROTOCOL:\n");
        prompt.append("When you need to execute an action, respond with JSON in this exact format:\n");
        prompt.append("{\n");
        prompt.append("  \"response_type\": \"action\",\n");
        prompt.append("  \"action\": \"action_name\",\n");
        prompt.append("  \"parameters\": {\n");
        prompt.append("    \"param1\": \"value1\",\n");
        prompt.append("    \"param2\": \"value2\"\n");
        prompt.append("  },\n");
        prompt.append("  \"explanation\": \"I'll create that for you. Please review the proposed changes and click Accept to apply them.\"\n");
        prompt.append("}\n");
        prompt.append("IMPORTANT NOTES:\n");
        prompt.append("- File operations (create_java_file, create_xml_resource, edit_file, fix_file_error) require user approval\n");
        prompt.append("- Never claim to have completed file operations - they will be shown as proposals first\n");
        prompt.append("- Use phrases like 'I'll create...', 'I'll add...', 'Let me prepare...', 'I'll delete...' instead of 'I've created...'\n");
        prompt.append("- Always include a helpful explanation that will be shown to the user\n");
        prompt.append("- For compilation errors, analyze the error and propose the most appropriate fix\n");
        prompt.append("- For duplicate resource conflicts, usually delete one of the conflicting files\n");
        prompt.append("- For missing files, create them with appropriate content\n\n");

        // Available actions
        prompt.append("AVAILABLE_ACTIONS:\n");
        for (AgenticAction action : availableActions.values()) {
            prompt.append("- ").append(action.getActionName()).append(": ").append(action.getDescription()).append("\n");
        }
        
        // Add Universal Actions information
        prompt.append("\nUNIVERSAL_ACTIONS:\n");
        prompt.append("You also have access to powerful Universal Actions for file operations:\n");
        prompt.append("- create_java_file: Create Java classes with proper package structure\n");
        prompt.append("- create_xml_resource: Create Android XML resources (layouts, drawables, etc.)\n");
        prompt.append("- read_file: Read file contents safely\n");
        prompt.append("- edit_file: Edit existing files with backup support\n");
        prompt.append("- list_directory: List directory contents with filtering\n");
        prompt.append("- fix_file_error: Legacy action for backward compatibility\n");
        prompt.append("\n");
        
        // Add Sketchware project structure guide
        prompt.append("SKETCHWARE_PROJECT_STRUCTURE:\n");
        prompt.append("Base path: /storage/emulated/0/.sketchware/data/<PROJECT_ID>/\n");
        prompt.append("- files/java/<package_path>/: Java source files\n");
        prompt.append("- files/resource/layout/: Layout XML files\n");
        prompt.append("- files/resource/drawable/: Drawable resources\n");
        prompt.append("- files/resource/values/: String, color, style resources\n");
        prompt.append("- files/assets/: Asset files\n");
        prompt.append("Package structure: Use dots in Java (com.my.app) and slashes in paths (com/my/app/)\n");
        prompt.append("\n");

        // Project context
        if (conversationContext.getCurrentProjectId() != null) {
            prompt.append("CURRENT_PROJECT_CONTEXT:\n");
            prompt.append(getProjectInfo(conversationContext.getCurrentProjectId()));
            prompt.append("\n");
        }

        // Recent actions
        if (!conversationContext.getExecutedActions().isEmpty()) {
            prompt.append("RECENT_ACTIONS: ");
            prompt.append(String.join(", ", conversationContext.getExecutedActions()));
            prompt.append("\n\n");
        }

        // Smart guidance for project creation and management
        prompt.append("PROJECT_CREATION_GUIDANCE:\n");
        prompt.append("When creating projects:\n");
        prompt.append("- If user doesn't specify project name, generate a descriptive one based on their request\n");
        prompt.append("- If user doesn't specify package name, auto-generate one like 'com.my.projectname'\n");
        prompt.append("- If user doesn't specify app name, use the project name\n");
        prompt.append("- Always validate that names follow Android conventions\n");
        prompt.append("- Set appropriate default settings: minSdk=21, targetSdk=34, viewBinding=true, oldMethods=false, materialComponents=false\n\n");
        
        prompt.append("PROJECT_MANAGEMENT_GUIDANCE:\n");
        prompt.append("When user mentions @projectId in their message:\n");
        prompt.append("- You get full project context including all settings and file paths\n");
        prompt.append("- Use update_project_settings action to modify project properties\n");
        prompt.append("- Available project settings to modify:\n");
        prompt.append("  * project_name: Change project workspace name\n");
        prompt.append("  * app_name: Change application display name\n");
        prompt.append("  * package_name: Change package identifier (e.g. com.example.app)\n");
        prompt.append("  * version_code: Integer version for updates\n");
        prompt.append("  * version_name: String version for display (e.g. 1.0)\n");
        prompt.append("  * minimum_sdk: Minimum Android SDK (default 21)\n");
        prompt.append("  * target_sdk: Target Android SDK (default 34)\n");
        prompt.append("  * application_class: Main application class (default .SketchApplication)\n");
        prompt.append("  * enable_view_binding: Enable view binding (true/false)\n");
        prompt.append("  * remove_old_methods: Remove deprecated methods (true/false)\n");
        prompt.append("  * enable_material_components: Use material components (true/false)\n");
        prompt.append("- When updating settings, explain what each change does\n");
        prompt.append("- Always use the update_project_settings action with proper parameters\n\n");
        
        // File operations guidance
        prompt.append("FILE_OPERATIONS_GUIDANCE:\n");
        prompt.append("When working with files:\n");
        prompt.append("1. Analyze the request carefully and identify what files need to be created/modified\n");
        prompt.append("2. Explain what you'll create/modify and why\n");
        prompt.append("3. Use appropriate actions based on the file type:\n");
        prompt.append("   - Java classes: Use create_java_file with proper package structure\n");
        prompt.append("   - Layout files: Use create_xml_resource with resource_type='layout'\n");
        prompt.append("   - Drawable resources: Use create_xml_resource with resource_type='drawable'\n");
        prompt.append("   - Values (strings, colors): Use create_xml_resource with resource_type='values'\n");
        prompt.append("   - File modifications: Use edit_file with complete updated content\n");
        prompt.append("4. For XML files, provide complete valid XML content\n");
        prompt.append("5. For Java files, include proper imports and method implementations\n");
        prompt.append("6. Remember: All file operations will be shown as proposals for user approval\n");
        prompt.append("7. Use future tense ('I'll create...') not past tense ('I've created...')\n");
        prompt.append("8. Always use proper package structure and file paths\n\n");

        prompt.append("USER_MESSAGE: ").append(userMessage);

        return prompt.toString();
    }

    private String getProjectInfo(String projectId) {
        try {
            HashMap<String, Object> projectData = lC.b(projectId);
            if (projectData != null) {
                JSONObject projectInfo = new JSONObject();
                projectInfo.put("project_id", projectId);
                projectInfo.put("project_name", yB.c(projectData, "my_ws_name"));
                projectInfo.put("app_name", yB.c(projectData, "my_app_name"));
                projectInfo.put("package_name", yB.c(projectData, "my_sc_pkg_name"));
                projectInfo.put("version_code", yB.c(projectData, "sc_ver_code"));
                projectInfo.put("version_name", yB.c(projectData, "sc_ver_name"));
                return projectInfo.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting project info", e);
        }
        return "No project currently active";
    }

    public String executeAction(String actionName, Map<String, Object> parameters, String projectId) {
        // First try universal actions
        if (universalActionManager.hasAction(actionName)) {
            return universalActionManager.executeAction(actionName, parameters, projectId);
        }
        
        // Fallback to legacy actions
        AgenticAction action = availableActions.get(actionName);
        if (action != null && action.canExecute(projectId, context)) {
            return action.execute(parameters, projectId, context);
        }
        
        return "Error: Action '" + actionName + "' not found or cannot be executed";
    }

    public Map<String, AgenticAction> getAvailableActions() {
        return availableActions;
    }
}