package pro.sketchware.ai.models;

import com.google.gson.JsonObject;

public class ToolCall {

    private final String id;
    private final String name;
    private final String arguments;

    public ToolCall(String id, String name, String arguments) {
        this.id = id;
        this.name = name;
        this.arguments = arguments;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getArguments() {
        return arguments;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("name", name);
        json.addProperty("arguments", arguments);
        return json;
    }

    public static ToolCall fromJson(JsonObject json) {
        if (json == null) return null;

        String id = json.has("id") && !json.get("id").isJsonNull()
                ? json.get("id").getAsString() : null;
        String name = json.has("name") && !json.get("name").isJsonNull()
                ? json.get("name").getAsString() : null;
        String arguments = json.has("arguments") && !json.get("arguments").isJsonNull()
                ? json.get("arguments").getAsString() : null;

        return new ToolCall(id, name, arguments);
    }

    @Override
    public String toString() {
        return "ToolCall{id='" + id + "', name='" + name + "'}";
    }
}
