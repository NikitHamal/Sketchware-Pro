package pro.sketchware.activities.ai.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import pro.sketchware.activities.ai.chat.models.ConversationContext;

public class ConversationContextStorage {
    private static final String TAG = "ConversationContextStorage";
    private static final String PREFS_NAME = "conversation_contexts";
    private final SharedPreferences prefs;

    public ConversationContextStorage(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveContext(ConversationContext context) {
        try {
            JSONObject contextJson = new JSONObject();
            contextJson.put("conversation_id", context.getConversationId());
            contextJson.put("qwen_chat_id", context.getQwenChatId());
            contextJson.put("current_project_id", context.getCurrentProjectId());
            contextJson.put("last_parent_id", context.getLastParentId());
            contextJson.put("last_user_message_id", context.getLastUserMessageId());

            // Save session state
            JSONObject sessionStateJson = new JSONObject();
            for (Map.Entry<String, Object> entry : context.getSessionState().entrySet()) {
                sessionStateJson.put(entry.getKey(), entry.getValue());
            }
            contextJson.put("session_state", sessionStateJson);

            // Save executed actions
            JSONArray actionsArray = new JSONArray();
            for (String action : context.getExecutedActions()) {
                actionsArray.put(action);
            }
            contextJson.put("executed_actions", actionsArray);

            // Save Qwen message history
            JSONArray messageHistoryArray = new JSONArray();
            for (JSONObject message : context.getQwenMessageHistory()) {
                messageHistoryArray.put(message);
            }
            contextJson.put("qwen_message_history", messageHistoryArray);

            prefs.edit().putString(context.getConversationId(), contextJson.toString()).apply();
            Log.d(TAG, "Saved context for conversation: " + context.getConversationId());

        } catch (JSONException e) {
            Log.e(TAG, "Error saving conversation context", e);
        }
    }

    public ConversationContext loadContext(String conversationId) {
        try {
            String contextJson = prefs.getString(conversationId, null);
            if (contextJson != null) {
                JSONObject json = new JSONObject(contextJson);
                
                ConversationContext context = new ConversationContext(conversationId);
                context.setQwenChatId(json.optString("qwen_chat_id", null));
                context.setCurrentProjectId(json.optString("current_project_id", null));
                context.setLastParentId(json.optString("last_parent_id", null));
                context.setLastUserMessageId(json.optString("last_user_message_id", null));

                // Load session state
                JSONObject sessionState = json.optJSONObject("session_state");
                if (sessionState != null) {
                    Iterator<String> keys = sessionState.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        context.getSessionState().put(key, sessionState.get(key));
                    }
                }

                // Load executed actions
                JSONArray actionsArray = json.optJSONArray("executed_actions");
                if (actionsArray != null) {
                    for (int i = 0; i < actionsArray.length(); i++) {
                        context.addExecutedAction(actionsArray.getString(i));
                    }
                }

                // Load Qwen message history
                JSONArray messageHistoryArray = json.optJSONArray("qwen_message_history");
                if (messageHistoryArray != null) {
                    for (int i = 0; i < messageHistoryArray.length(); i++) {
                        context.addToQwenMessageHistory(messageHistoryArray.getJSONObject(i));
                    }
                }

                Log.d(TAG, "Loaded context for conversation: " + conversationId);
                return context;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error loading conversation context", e);
        }
        
        // Return new context if not found or error occurred
        return new ConversationContext(conversationId);
    }

    public void deleteContext(String conversationId) {
        prefs.edit().remove(conversationId).apply();
        Log.d(TAG, "Deleted context for conversation: " + conversationId);
    }

    public boolean hasContext(String conversationId) {
        return prefs.contains(conversationId);
    }
}