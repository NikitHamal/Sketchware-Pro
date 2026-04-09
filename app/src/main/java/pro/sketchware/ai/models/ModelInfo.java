package pro.sketchware.ai.models;

import com.google.gson.JsonObject;

import java.util.Objects;

public class ModelInfo implements Comparable<ModelInfo> {

    private final String id;
    private final String name;
    private final AiProvider provider;
    private final long contextLength;
    private final String description;

    public ModelInfo(String id, String name, AiProvider provider, long contextLength, String description) {
        this.id = id;
        this.name = name;
        this.provider = provider;
        this.contextLength = contextLength;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public AiProvider getProvider() {
        return provider;
    }

    public long getContextLength() {
        return contextLength;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModelInfo modelInfo = (ModelInfo) o;
        return Objects.equals(id, modelInfo.id) && provider == modelInfo.provider;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, provider);
    }

    @Override
    public int compareTo(ModelInfo other) {
        if (other == null) return 1;
        String thisName = name != null ? name : "";
        String otherName = other.name != null ? other.name : "";
        return thisName.compareToIgnoreCase(otherName);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("name", name);
        json.addProperty("provider", provider != null ? provider.name() : null);
        json.addProperty("contextLength", contextLength);
        json.addProperty("description", description);
        return json;
    }

    public static ModelInfo fromJson(JsonObject json) {
        if (json == null) return null;

        String id = json.has("id") && !json.get("id").isJsonNull()
                ? json.get("id").getAsString() : null;
        String name = json.has("name") && !json.get("name").isJsonNull()
                ? json.get("name").getAsString() : null;
        AiProvider provider = json.has("provider") && !json.get("provider").isJsonNull()
                ? AiProvider.fromName(json.get("provider").getAsString()) : null;
        long contextLength = json.has("contextLength") && !json.get("contextLength").isJsonNull()
                ? json.get("contextLength").getAsLong() : 0L;
        String description = json.has("description") && !json.get("description").isJsonNull()
                ? json.get("description").getAsString() : null;

        return new ModelInfo(id, name, provider, contextLength, description);
    }

    @Override
    public String toString() {
        return "ModelInfo{id='" + id + "', name='" + name + "', provider=" + provider + "}";
    }
}
