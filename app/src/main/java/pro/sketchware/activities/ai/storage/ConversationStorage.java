package pro.sketchware.activities.ai.storage;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import pro.sketchware.activities.main.fragments.ai.models.Conversation;

public class ConversationStorage {
    private static final String PREFS_NAME = "ai_conversations";
    private static final String CONVERSATIONS_KEY = "conversations";
    
    private SharedPreferences prefs;
    
    public ConversationStorage(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public void saveConversation(Conversation conversation) {
        List<Conversation> conversations = getAllConversations();
        
        // Check if conversation already exists and update it
        boolean found = false;
        for (int i = 0; i < conversations.size(); i++) {
            if (conversations.get(i).getId().equals(conversation.getId())) {
                conversations.set(i, conversation);
                found = true;
                break;
            }
        }
        
        // If not found, add new conversation at the beginning
        if (!found) {
            conversations.add(0, conversation);
        }
        
        saveAllConversations(conversations);
    }
    
    public List<Conversation> getAllConversations() {
        List<Conversation> conversations = new ArrayList<>();
        String json = prefs.getString(CONVERSATIONS_KEY, "[]");
        
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                Conversation conversation = new Conversation();
                conversation.setId(obj.getString("id"));
                conversation.setTitle(obj.getString("title"));
                conversation.setLastMessage(obj.optString("lastMessage", ""));
                conversation.setModel(obj.optString("model", ""));
                
                long timestamp = obj.optLong("lastMessageTime", 0);
                if (timestamp > 0) {
                    conversation.setLastMessageTime(new Date(timestamp));
                }
                
                conversations.add(conversation);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        return conversations;
    }
    
    public void deleteConversation(String conversationId) {
        List<Conversation> conversations = getAllConversations();
        conversations.removeIf(conv -> conv.getId().equals(conversationId));
        saveAllConversations(conversations);
    }
    
    public void updateConversationTitle(String conversationId, String newTitle) {
        List<Conversation> conversations = getAllConversations();
        for (Conversation conv : conversations) {
            if (conv.getId().equals(conversationId)) {
                conv.setTitle(newTitle);
                break;
            }
        }
        saveAllConversations(conversations);
    }
    
    public void updateConversationLastMessage(String conversationId, String lastMessage, String model) {
        List<Conversation> conversations = getAllConversations();
        for (Conversation conv : conversations) {
            if (conv.getId().equals(conversationId)) {
                conv.setLastMessage(lastMessage);
                conv.setLastMessageTime(new Date());
                if (model != null) {
                    conv.setModel(model);
                }
                break;
            }
        }
        saveAllConversations(conversations);
    }
    
    private void saveAllConversations(List<Conversation> conversations) {
        JSONArray array = new JSONArray();
        
        try {
            for (Conversation conv : conversations) {
                JSONObject obj = new JSONObject();
                obj.put("id", conv.getId());
                obj.put("title", conv.getTitle());
                obj.put("lastMessage", conv.getLastMessage() != null ? conv.getLastMessage() : "");
                obj.put("model", conv.getModel() != null ? conv.getModel() : "");
                
                if (conv.getLastMessageTime() != null) {
                    obj.put("lastMessageTime", conv.getLastMessageTime().getTime());
                }
                
                array.put(obj);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        prefs.edit().putString(CONVERSATIONS_KEY, array.toString()).apply();
    }
}