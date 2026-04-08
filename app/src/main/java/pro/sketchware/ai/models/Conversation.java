package pro.sketchware.ai.models;

import com.google.gson.Gson;

import java.util.UUID;

public class Conversation {

    private static final Gson GSON = new Gson();

    private String id;
    private String workspaceId;
    private String title;
    private long createdAt;
    private long updatedAt;
    private String modelId;
    private String providerName;

    public Conversation(String workspaceId, String title, String modelId, String providerName) {
        this.id = UUID.randomUUID().toString();
        this.workspaceId = workspaceId;
        this.title = title;
        this.modelId = modelId;
        this.providerName = providerName;
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        this.updatedAt = System.currentTimeMillis();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
        this.updatedAt = System.currentTimeMillis();
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static Conversation fromJson(String json) {
        if (json == null || json.isEmpty()) return null;
        return GSON.fromJson(json, Conversation.class);
    }

    @Override
    public String toString() {
        return "Conversation{id='" + id + "', title='" + title + "', workspaceId='" + workspaceId + "'}";
    }
}
