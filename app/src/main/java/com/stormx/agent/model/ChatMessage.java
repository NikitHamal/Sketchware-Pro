package com.stormx.agent.model;

import java.util.UUID;

public class ChatMessage {
    public static final int TYPE_USER = 1;
    public static final int TYPE_AI = 2;

    private String id;
    private String content;
    private int type;
    private long timestamp;

    public ChatMessage(String id, String content, int type, long timestamp) {
        this.id = id;
        this.content = content;
        this.type = type;
        this.timestamp = timestamp;
    }

    public static ChatMessage user(String content){
        return new ChatMessage(UUID.randomUUID().toString(), content, TYPE_USER, System.currentTimeMillis());
    }
    public static ChatMessage ai(String content){
        return new ChatMessage(UUID.randomUUID().toString(), content, TYPE_AI, System.currentTimeMillis());
    }

    // getters & setters
    public String getId(){ return id; }
    public String getContent(){ return content; }
    public void setContent(String c){ content=c; }
    public int getType(){ return type; }
    public long getTimestamp(){ return timestamp; }
}