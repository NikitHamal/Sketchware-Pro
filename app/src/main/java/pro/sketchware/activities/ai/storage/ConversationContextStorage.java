package pro.sketchware.activities.ai.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import org.json.JSONObject;

import pro.sketchware.activities.ai.chat.models.ConversationContext;

public class ConversationContextStorage {
    private static final String TAG = "ConversationContextStorage";
    private static final String PREFS_NAME = "conversation_contexts";
    private final SharedPreferences prefs;
    private final Gson gson;

    public ConversationContextStorage(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // A regular Gson instance would not serialize a raw JSONObject
        gson = new GsonBuilder()
                .registerTypeAdapter(JSONObject.class, new JsonObjectTypeAdapter())
                .create();
    }

    public void saveContext(ConversationContext context) {
        try {
            String json = gson.toJson(context);
            prefs.edit().putString(context.getConversationId(), json).apply();
            Log.d(TAG, "Saved context for conversation: " + context.getConversationId());
        } catch (Exception e) {
            Log.e(TAG, "Error saving conversation context", e);
        }
    }

    public ConversationContext loadContext(String conversationId) {
        String contextJson = prefs.getString(conversationId, null);
        if (contextJson != null) {
            try {
                ConversationContext context = gson.fromJson(contextJson, ConversationContext.class);
                Log.d(TAG, "Loaded context for conversation: " + conversationId);
                return context;
            } catch (JsonSyntaxException e) {
                Log.e(TAG, "Error loading conversation context", e);
            }
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