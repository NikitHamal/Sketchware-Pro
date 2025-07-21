package pro.sketchware.activities.ai.storage;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.activities.ai.chat.models.ChatMessage;

public class MessageStorage {
    private static final String PREFS_NAME = "ai_messages";
    private SharedPreferences prefs;

    public MessageStorage(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveMessages(String conversationId, List<ChatMessage> messages) {
        JSONArray jsonArray = new JSONArray();
        
        try {
            for (ChatMessage message : messages) {
                JSONObject jsonMessage = new JSONObject();
                jsonMessage.put("id", message.getId());
                jsonMessage.put("content", message.getContent());
                jsonMessage.put("type", message.getType());
                jsonMessage.put("timestamp", message.getTimestamp());
                jsonArray.put(jsonMessage);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        prefs.edit().putString(conversationId, jsonArray.toString()).apply();
    }

    public List<ChatMessage> getMessages(String conversationId) {
        List<ChatMessage> messages = new ArrayList<>();
        String json = prefs.getString(conversationId, "[]");
        
        try {
            JSONArray jsonArray = new JSONArray(json);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonMessage = jsonArray.getJSONObject(i);
                ChatMessage message = new ChatMessage(
                    jsonMessage.getString("id"),
                    jsonMessage.getString("content"),
                    jsonMessage.getInt("type"),
                    jsonMessage.getLong("timestamp")
                );
                messages.add(message);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        return messages;
    }

    public void addMessage(String conversationId, ChatMessage message) {
        List<ChatMessage> messages = getMessages(conversationId);
        messages.add(message);
        saveMessages(conversationId, messages);
    }

    public void deleteMessages(String conversationId) {
        prefs.edit().remove(conversationId).apply();
    }
}