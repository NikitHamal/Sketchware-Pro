package com.stormx.agent.storage;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.stormx.agent.model.Conversation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ConversationStorage {
    private static final String PREFS_NAME = "ai_conversations";
    private static final String KEY = "conversations";
    private final SharedPreferences prefs;
    private final Gson gson;
    private final Type listType = new TypeToken<ArrayList<Conversation>>(){}.getType();

    public ConversationStorage(Context context){
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
    }

    public List<Conversation> getAll(){
        String json = prefs.getString(KEY, "[]");
        List<Conversation> list = gson.fromJson(json, listType);
        return list==null? new ArrayList<>() : list;
    }

    public void saveConversation(Conversation conv){
        List<Conversation> list = getAll();
        list.removeIf(c -> c.getId().equals(conv.getId()));
        list.add(0, conv);
        saveAll(list);
    }

    public void deleteConversation(String id){
        List<Conversation> list = getAll();
        list.removeIf(c -> c.getId().equals(id));
        saveAll(list);
    }

    public void updateConversationTitle(String id,String title){
        List<Conversation> list = getAll();
        for(Conversation c:list){ if(c.getId().equals(id)){ c.setTitle(title); break;}}
        saveAll(list);
    }

    private void saveAll(List<Conversation> list){
        prefs.edit().putString(KEY,gson.toJson(list)).apply();
    }
}