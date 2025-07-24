package pro.sketchware.activities.ai.storage;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.activities.ai.chat.models.ChatMessage;

public class MessageStorage {
    private static final String PREFS_NAME = "ai_messages";
    private final SharedPreferences prefs;
    private final Gson gson;
    private final Type messageListType = new TypeToken<ArrayList<ChatMessage>>() {}.getType();

    public MessageStorage(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Use the same Gson configuration as ConversationContextStorage for consistency
        gson = new GsonBuilder()
                .registerTypeAdapter(org.json.JSONObject.class, new JsonObjectTypeAdapter())
                .create();
    }

    public void saveMessages(String conversationId, List<ChatMessage> messages) {
        // Debug: Check if any messages are proposals before saving
        for (ChatMessage msg : messages) {
            if (msg.getType() == ChatMessage.TYPE_PROPOSAL) {
                android.util.Log.d("MessageStorage", "Saving proposal message: ID=" + msg.getId() + ", HasProposalData=" + msg.hasProposalData());
                android.util.Log.d("MessageStorage", "Proposal data before serialization: " + msg.getProposalData());
            }
        }
        
        String json = gson.toJson(messages);
        android.util.Log.d("MessageStorage", "Serialized JSON length: " + json.length());
        
        prefs.edit().putString(conversationId, json).apply();
    }

    public List<ChatMessage> getMessages(String conversationId) {
        String json = prefs.getString(conversationId, "[]");
        android.util.Log.d("MessageStorage", "Loading messages from JSON length: " + json.length());
        
        List<ChatMessage> messages = gson.fromJson(json, messageListType);
        if (messages == null) {
            return new ArrayList<>();
        }
        
        // Debug: Check if any loaded messages are proposals
        for (ChatMessage msg : messages) {
            if (msg.getType() == ChatMessage.TYPE_PROPOSAL) {
                android.util.Log.d("MessageStorage", "Loaded proposal message: ID=" + msg.getId() + ", HasProposalData=" + msg.hasProposalData());
                android.util.Log.d("MessageStorage", "Proposal data after deserialization: " + msg.getProposalData());
            }
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