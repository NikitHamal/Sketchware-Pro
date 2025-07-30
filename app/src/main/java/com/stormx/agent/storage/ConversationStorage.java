package com.stormx.agent.storage;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.stormx.agent.models.Conversation;

public class ConversationStorage {
    private static final String PREFS_NAME = "stormx_conversations";
    private static final String CONVERSATIONS_KEY = "conversations";

    private final SharedPreferences prefs;
    private final Gson gson;
    private final Type conversationListType = new TypeToken<ArrayList<Conversation>>() {}.getType();

    public ConversationStorage(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Configure Gson to handle Date objects as timestamps (long values)
        gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd HH:mm:ss") // Fallback format
                .registerTypeAdapter(Date.class, new com.google.gson.JsonSerializer<Date>() {
                    @Override
                    public com.google.gson.JsonElement serialize(Date src, java.lang.reflect.Type typeOfSrc, com.google.gson.JsonSerializationContext context) {
                        return new com.google.gson.JsonPrimitive(src.getTime());
                    }
                })
                .registerTypeAdapter(Date.class, new com.google.gson.JsonDeserializer<Date>() {
                    @Override
                    public Date deserialize(com.google.gson.JsonElement json, java.lang.reflect.Type typeOfT, com.google.gson.JsonDeserializationContext context) {
                        try {
                            return new Date(json.getAsLong());
                        } catch (Exception e) {
                            // Fallback: try to parse as string
                            try {
                                return new Date(Long.parseLong(json.getAsString()));
                            } catch (Exception ex) {
                                // Last resort: return current time
                                return new Date();
                            }
                        }
                    }
                })
                .create();
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
        String json = prefs.getString(CONVERSATIONS_KEY, "[]");
        List<Conversation> conversations = gson.fromJson(json, conversationListType);
        if (conversations == null) {
            return new ArrayList<>();
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
                conv.setModel(model);
                break;
            }
        }
        saveAllConversations(conversations);
    }

    private void saveAllConversations(List<Conversation> conversations) {
        String json = gson.toJson(conversations);
        prefs.edit().putString(CONVERSATIONS_KEY, json).apply();
    }
}