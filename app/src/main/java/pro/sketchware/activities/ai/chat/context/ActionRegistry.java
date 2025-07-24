package pro.sketchware.activities.ai.chat.context;

import java.util.HashMap;
import java.util.Map;

import pro.sketchware.activities.ai.chat.models.AgenticAction;
import pro.sketchware.activities.ai.chat.actions.CreateProjectAction;
import pro.sketchware.activities.ai.chat.actions.FixFileErrorAction;
import pro.sketchware.activities.ai.chat.actions.UpdateProjectSettingsAction;

public class ActionRegistry {

    private final Map<String, AgenticAction> availableActions = new HashMap<>();

    public ActionRegistry() {
        registerAction(new CreateProjectAction());
        registerAction(new FixFileErrorAction());
        registerAction(new UpdateProjectSettingsAction());
    }

    private void registerAction(AgenticAction action) {
        availableActions.put(action.getActionName(), action);
    }

    public AgenticAction getAction(String actionName) {
        return availableActions.get(actionName);
    }

    public Map<String, AgenticAction> getAvailableActions() {
        return availableActions;
    }
}
