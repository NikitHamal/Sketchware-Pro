package pro.sketchware.ai.models;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Workspace {

    private static final Gson GSON = new Gson();

    private String id;
    private String name;
    private String description;
    private List<String> projectIds;
    private long createdAt;
    private long updatedAt;

    public Workspace(String name, String description) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.description = description;
        this.projectIds = new ArrayList<>();
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Workspace(String id, String name, String description, List<String> projectIds,
                     long createdAt, long updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.projectIds = projectIds != null ? new ArrayList<>(projectIds) : new ArrayList<>();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = System.currentTimeMillis();
    }

    public List<String> getProjectIds() {
        return projectIds;
    }

    public void setProjectIds(List<String> projectIds) {
        this.projectIds = projectIds != null ? new ArrayList<>(projectIds) : new ArrayList<>();
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

    public void addProject(String scId) {
        if (scId != null && !projectIds.contains(scId)) {
            projectIds.add(scId);
            this.updatedAt = System.currentTimeMillis();
        }
    }

    public void removeProject(String scId) {
        if (projectIds.remove(scId)) {
            this.updatedAt = System.currentTimeMillis();
        }
    }

    public boolean hasProject(String scId) {
        return projectIds.contains(scId);
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static Workspace fromJson(String json) {
        if (json == null || json.isEmpty()) return null;
        return GSON.fromJson(json, Workspace.class);
    }

    @Override
    public String toString() {
        return "Workspace{id='" + id + "', name='" + name + "'}";
    }
}
