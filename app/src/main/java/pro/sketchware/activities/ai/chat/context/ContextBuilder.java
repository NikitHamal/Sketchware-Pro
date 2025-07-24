package pro.sketchware.activities.ai.chat.context;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import a.a.a.lC;
import a.a.a.yB;
import pro.sketchware.activities.ai.chat.actions.UniversalActionManager;
import pro.sketchware.activities.ai.chat.models.AgenticAction;
import pro.sketchware.activities.ai.chat.models.ConversationContext;

public class ContextBuilder {
    private static final String TAG = "ContextBuilder";
    private final Context context;
    private final ActionRegistry actionRegistry;
    private final UniversalActionManager universalActionManager;

    public ContextBuilder(Context context) {
        this.context = context;
        this.actionRegistry = new ActionRegistry();
        this.universalActionManager = new UniversalActionManager(context);
    }

    public String buildEnhancedPrompt(String userMessage, ConversationContext conversationContext) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("SYSTEM_CONTEXT:\n");
        prompt.append(SketchwareProjectGuide.getSystemContext()).append("\n\n");

        prompt.append("ACTION_PROTOCOL:\n");
        prompt.append(SketchwareProjectGuide.getActionProtocol()).append("\n\n");

        prompt.append("AVAILABLE_ACTIONS:\n");
        for (AgenticAction action : actionRegistry.getAvailableActions().values()) {
            prompt.append("- ").append(action.getActionName()).append(": ").append(action.getDescription()).append("\n");
        }

        prompt.append("\nUNIVERSAL_ACTIONS:\n");
        prompt.append(SketchwareProjectGuide.getUniversalActionsGuide()).append("\n\n");

        prompt.append("SKETCHWARE_PROJECT_STRUCTURE:\n");
        prompt.append(SketchwareProjectGuide.getProjectStructureGuide()).append("\n\n");

        if (conversationContext.getCurrentProjectId() != null) {
            prompt.append("CURRENT_PROJECT_CONTEXT:\n");
            prompt.append(getProjectInfo(conversationContext.getCurrentProjectId())).append("\n\n");
        }

        if (!conversationContext.getExecutedActions().isEmpty()) {
            prompt.append("RECENT_ACTIONS: ");
            prompt.append(String.join(", ", conversationContext.getExecutedActions())).append("\n\n");
        }

        prompt.append("PROJECT_CREATION_GUIDANCE:\n");
        prompt.append(SketchwareProjectGuide.getProjectCreationGuidance()).append("\n\n");

        prompt.append("PROJECT_MANAGEMENT_GUIDANCE:\n");
        prompt.append(SketchwareProjectGuide.getProjectManagementGuidance()).append("\n\n");

        prompt.append("FILE_OPERATIONS_GUIDANCE:\n");
        prompt.append(SketchwareProjectGuide.getFileOperationsGuidance()).append("\n\n");

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
        if (universalActionManager.hasAction(actionName)) {
            return universalActionManager.executeAction(actionName, parameters, projectId);
        }

        AgenticAction action = actionRegistry.getAction(actionName);
        if (action != null && action.canExecute(projectId, context)) {
            return action.execute(parameters, projectId, context);
        }

        return "Error: Action '" + actionName + "' not found or cannot be executed";
    }
}