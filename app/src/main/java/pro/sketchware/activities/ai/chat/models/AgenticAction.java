package pro.sketchware.activities.ai.chat.models;

import android.content.Context;
import java.util.Map;

public interface AgenticAction {
    String execute(Map<String, Object> parameters, String projectId, Context context);
    boolean canExecute(String projectId, Context context);
    String getDescription();
    String getActionName();
}