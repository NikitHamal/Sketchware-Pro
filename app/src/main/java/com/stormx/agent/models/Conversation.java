package com.stormx.agent.models;

import java.util.Date;

public class Conversation {
    private String id;
    private String title;
    private String lastMessage;
    private Date lastMessageTime;
    private String model;

    public Conversation() {
    }

    public Conversation(String id, String title, String lastMessage, Date lastMessageTime, String model) {
        this.id = id;
        this.title = title;
        this.lastMessage = lastMessage;
        this.lastMessageTime = lastMessageTime;
        this.model = model;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public Date getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessageTime(Date lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}