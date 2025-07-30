package com.stormx.agent.storage;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.stormx.agent.models.ChatMessage;

public class MessageStorage {
    private static final String PREFS_NAME = "stormx_messages";
    private final SharedPreferences prefs;
    private final Gson gson;
    private final Type messageListType = new TypeToken<ArrayList<ChatMessage>>() {}.getType();

    public MessageStorage(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new GsonBuilder().create();
    }

    public void saveMessages(String conversationId, List<ChatMessage> messages) {
        String json = gson.toJson(messages);
        prefs.edit().putString(conversationId, json).apply();
    }

    public List<ChatMessage> getMessages(String conversationId) {
        String json = prefs.getString(conversationId, "[]");
        List<ChatMessage> messages = gson.fromJson(json, messageListType);
        if (messages == null) {
            return new ArrayList<>();
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