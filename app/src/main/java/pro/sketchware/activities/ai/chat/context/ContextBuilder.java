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
        prompt.append("  \"explanation\": \"I'll create that project for you right away!\"\n");
        prompt.append("}\n");
        prompt.append("IMPORTANT: Always include a helpful explanation that will be shown to the user.\n\n");

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

        // Smart guidance for project creation
        prompt.append("PROJECT_CREATION_GUIDANCE:\n");
        prompt.append("When creating projects:\n");
        prompt.append("- If user doesn't specify project name, generate a descriptive one based on their request\n");
        prompt.append("- If user doesn't specify package name, auto-generate one like 'com.my.projectname'\n");
        prompt.append("- If user doesn't specify app name, use the project name\n");
        prompt.append("- Always validate that names follow Android conventions\n\n");
        
        // Error fixing guidance
        prompt.append("ERROR_FIXING_GUIDANCE:\n");
        prompt.append("When fixing compile errors:\n");
        prompt.append("1. Analyze the error carefully and identify the root cause\n");
        prompt.append("2. Explain the issue and your proposed solution clearly\n");
        prompt.append("3. Use appropriate actions based on the error type:\n");
        prompt.append("   - Missing binding class: Use create_xml_resource for layout files\n");
        prompt.append("   - Missing Java class: Use create_java_file with proper package\n");
        prompt.append("   - Missing resources: Use create_xml_resource for drawables/values\n");
        prompt.append("   - File modifications: Use edit_file with proper content\n");
        prompt.append("4. For empty XML files, provide complete valid XML content\n");
        prompt.append("5. Include both explanation AND action in your response\n");
        prompt.append("6. Focus on minimal, targeted fixes rather than major restructuring\n");
        prompt.append("7. Always use proper package structure and file paths\n\n");

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