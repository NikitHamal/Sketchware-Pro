package pro.sketchware.ai.models;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatMessage {

    private final String id;
    private final String conversationId;
    private final String role;
    private String content;
    private List<ToolCall> toolCalls;
    private String toolCallId;
    private final long timestamp;
    private transient boolean isStreaming;

    /**
     * Constructor for a user message.
     */
    public ChatMessage(String conversationId, String content) {
        this.id = UUID.randomUUID().toString();
        this.conversationId = conversationId;
        this.role = "user";
        this.content = content;
        this.toolCalls = null;
        this.toolCallId = null;
        this.timestamp = System.currentTimeMillis();
        this.isStreaming = false;
    }

    /**
     * Constructor for an assistant message with optional tool calls.
     */
    public ChatMessage(String conversationId, String content, List<ToolCall> toolCalls) {
        this.id = UUID.randomUUID().toString();
        this.conversationId = conversationId;
        this.role = "assistant";
        this.content = content;
        this.toolCalls = toolCalls != null ? new ArrayList<>(toolCalls) : null;
        this.toolCallId = null;
        this.timestamp = System.currentTimeMillis();
        this.isStreaming = false;
    }

    /**
     * Constructor for a tool result message.
     */
    public ChatMessage(String conversationId, String toolCallId, String content, boolean isToolResult) {
        this.id = UUID.randomUUID().toString();
        this.conversationId = conversationId;
        this.role = "tool";
        this.content = content;
        this.toolCalls = null;
        this.toolCallId = toolCallId;
        this.timestamp = System.currentTimeMillis();
        this.isStreaming = false;
    }

    /**
     * Full constructor for deserialization.
     */
    private ChatMessage(String id, String conversationId, String role, String content,
                        List<ToolCall> toolCalls, String toolCallId, long timestamp) {
        this.id = id;
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.toolCalls = toolCalls;
        this.toolCallId = toolCallId;
        this.timestamp = timestamp;
        this.isStreaming = false;
    }

    public String getId() {
        return id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls != null ? new ArrayList<>(toolCalls) : null;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isStreaming() {
        return isStreaming;
    }

    public void setStreaming(boolean streaming) {
        this.isStreaming = streaming;
    }

    // --- Static Factory Methods ---

    public static ChatMessage userMessage(String conversationId, String content) {
        return new ChatMessage(conversationId, content);
    }

    public static ChatMessage assistantMessage(String content, List<ToolCall> toolCalls) {
        return new ChatMessage(null, content, toolCalls);
    }

    public static ChatMessage toolResultMessage(String toolCallId, String content) {
        return new ChatMessage(null, toolCallId, content, true);
    }

    public void appendContent(String chunk) {
        if (chunk == null) return;
        if (this.content == null) {
            this.content = chunk;
        } else {
            this.content += chunk;
        }
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("conversationId", conversationId);
        json.addProperty("role", role);
        json.addProperty("content", content);
        json.addProperty("toolCallId", toolCallId);
        json.addProperty("timestamp", timestamp);

        if (toolCalls != null && !toolCalls.isEmpty()) {
            JsonArray callsArray = new JsonArray();
            for (ToolCall call : toolCalls) {
                callsArray.add(call.toJson());
            }
            json.add("toolCalls", callsArray);
        }

        return json;
    }

    public static ChatMessage fromJson(JsonObject json) {
        if (json == null) return null;

        String id = json.has("id") && !json.get("id").isJsonNull()
                ? json.get("id").getAsString() : UUID.randomUUID().toString();
        String conversationId = json.has("conversationId") && !json.get("conversationId").isJsonNull()
                ? json.get("conversationId").getAsString() : null;
        String role = json.has("role") && !json.get("role").isJsonNull()
                ? json.get("role").getAsString() : "user";
        String content = json.has("content") && !json.get("content").isJsonNull()
                ? json.get("content").getAsString() : null;
        String toolCallId = json.has("toolCallId") && !json.get("toolCallId").isJsonNull()
                ? json.get("toolCallId").getAsString() : null;
        long timestamp = json.has("timestamp") && !json.get("timestamp").isJsonNull()
                ? json.get("timestamp").getAsLong() : System.currentTimeMillis();

        List<ToolCall> toolCalls = null;
        if (json.has("toolCalls") && json.get("toolCalls").isJsonArray()) {
            toolCalls = new ArrayList<>();
            JsonArray callsArray = json.getAsJsonArray("toolCalls");
            for (JsonElement element : callsArray) {
                if (element.isJsonObject()) {
                    ToolCall call = ToolCall.fromJson(element.getAsJsonObject());
                    if (call != null) {
                        toolCalls.add(call);
                    }
                }
            }
            if (toolCalls.isEmpty()) {
                toolCalls = null;
            }
        }

        return new ChatMessage(id, conversationId, role, content, toolCalls, toolCallId, timestamp);
    }

    @Override
    public String toString() {
        return "ChatMessage{id='" + id + "', role='" + role + "', conversationId='" + conversationId + "'}";
    }
}
