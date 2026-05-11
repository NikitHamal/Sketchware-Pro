package pro.sketchware.ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatMessage {

    public enum MessageType {
        USER,
        AI,
        SYSTEM,
        TOOL,
        INTERNAL_ASSISTANT
    }

    public enum MessageStatus {
        SENDING,
        SENT,
        ERROR
    }

    private final String id;
    private final String conversationId;
    private final String role;
    private String content;
    private List<ToolCall> toolCalls;
    private String toolCallId;
    private String toolName;
    private final long timestamp;
    private transient boolean isStreaming;

    private MessageType type;
    private MessageStatus status;

    public ChatMessage(String conversationId, String content) {
        this.id = UUID.randomUUID().toString();
        this.conversationId = conversationId;
        this.role = "user";
        this.content = content;
        this.toolCalls = null;
        this.toolCallId = null;
        this.toolName = null;
        this.timestamp = System.currentTimeMillis();
        this.isStreaming = false;
        this.type = MessageType.USER;
        this.status = MessageStatus.SENDING;
    }

    public ChatMessage(String conversationId, String content, List<ToolCall> toolCalls) {
        this.id = UUID.randomUUID().toString();
        this.conversationId = conversationId;
        this.role = "assistant";
        this.content = content;
        this.toolCalls = toolCalls != null ? new ArrayList<>(toolCalls) : null;
        this.toolCallId = null;
        this.toolName = null;
        this.timestamp = System.currentTimeMillis();
        this.isStreaming = false;
        this.type = MessageType.AI;
        this.status = MessageStatus.SENDING;
    }

    public ChatMessage(String conversationId, String toolCallId, String toolName, String content, boolean isToolResult) {
        this.id = UUID.randomUUID().toString();
        this.conversationId = conversationId;
        this.role = "tool";
        this.content = content;
        this.toolCalls = null;
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.timestamp = System.currentTimeMillis();
        this.isStreaming = false;
        this.type = MessageType.TOOL;
        this.status = MessageStatus.SENT;
    }

    private ChatMessage(String id, String conversationId, String role, String content,
                        List<ToolCall> toolCalls, String toolCallId, String toolName, long timestamp,
                        MessageType type, MessageStatus status) {
        this.id = id;
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.toolCalls = toolCalls;
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.timestamp = timestamp;
        this.isStreaming = false;
        this.type = type;
        this.status = status;
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

    @Nullable
    public String getText() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setText(@Nullable String text) {
        this.content = text;
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

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @NonNull
    public MessageType getType() {
        return type;
    }

    @NonNull
    public MessageStatus getStatus() {
        return status;
    }

    public void setStatus(@NonNull MessageStatus status) {
        this.status = status;
    }

    public boolean isStreaming() {
        return isStreaming;
    }

    public void setStreaming(boolean streaming) {
        this.isStreaming = streaming;
    }

    public synchronized void appendText(@NonNull String chunk) {
        if (chunk == null) return;
        if (this.content == null) {
            this.content = chunk;
        } else {
            this.content = this.content + chunk;
        }
    }

    public void appendContent(String chunk) {
        appendText(chunk);
    }

    public boolean isFromUser() {
        return type == MessageType.USER;
    }

    public boolean isFromAi() {
        return type == MessageType.AI || type == MessageType.INTERNAL_ASSISTANT;
    }

    public boolean hasText() {
        return content != null && !content.trim().isEmpty();
    }

    public boolean hasVisibleAssistantContent() {
        return content != null && !content.trim().isEmpty();
    }

    public boolean isError() {
        return status == MessageStatus.ERROR;
    }

    public boolean contentEquals(@NonNull ChatMessage other) {
        if (!id.equals(other.id)) return false;
        if (type != other.type) return false;
        if (status != other.status) return false;
        if (isStreaming != other.isStreaming) return false;
        if (timestamp != other.timestamp) return false;
        if (content == null && other.content != null) return false;
        if (content != null && !content.equals(other.content)) return false;
        return true;
    }

    @NonNull
    public static ChatMessage user(@NonNull String text) {
        return new ChatMessage(UUID.randomUUID().toString(), null, "user", text,
                null, null, null, System.currentTimeMillis(),
                MessageType.USER, MessageStatus.SENDING);
    }

    @NonNull
    public static ChatMessage user(@NonNull String text, @Nullable String conversationId) {
        return new ChatMessage(UUID.randomUUID().toString(), conversationId, "user", text,
                null, null, null, System.currentTimeMillis(),
                MessageType.USER, MessageStatus.SENDING);
    }

    @NonNull
    public static ChatMessage aiPlaceholder() {
        return new ChatMessage(UUID.randomUUID().toString(), null, "assistant", null,
                null, null, null, System.currentTimeMillis(),
                MessageType.AI, MessageStatus.SENDING);
    }

    @NonNull
    public static ChatMessage ai(@NonNull String text) {
        return new ChatMessage(UUID.randomUUID().toString(), null, "assistant", text,
                null, null, null, System.currentTimeMillis(),
                MessageType.AI, MessageStatus.SENT);
    }

    @NonNull
    public static ChatMessage system(@NonNull String text) {
        return new ChatMessage(UUID.randomUUID().toString(), null, "system", text,
                null, null, null, System.currentTimeMillis(),
                MessageType.SYSTEM, MessageStatus.SENT);
    }

    @NonNull
    public static ChatMessage tool(@NonNull String toolName, @NonNull String toolCallId, @NonNull String text) {
        return new ChatMessage(UUID.randomUUID().toString(), null, "tool", text,
                null, toolCallId, toolName, System.currentTimeMillis(),
                MessageType.TOOL, MessageStatus.SENT);
    }

    @NonNull
    public static ChatMessage internalAssistant(@NonNull String text) {
        return new ChatMessage(UUID.randomUUID().toString(), null, "assistant", text,
                null, null, null, System.currentTimeMillis(),
                MessageType.INTERNAL_ASSISTANT, MessageStatus.SENT);
    }

    @NonNull
    public static ChatMessage error(@Nullable String originalText, @NonNull String errorDetail) {
        return new ChatMessage(UUID.randomUUID().toString(), null, "assistant", errorDetail,
                null, null, null, System.currentTimeMillis(),
                MessageType.AI, MessageStatus.ERROR);
    }

    public static ChatMessage userMessage(String conversationId, String content) {
        return new ChatMessage(conversationId, content);
    }

    public static ChatMessage assistantMessage(String content, List<ToolCall> toolCalls) {
        return new ChatMessage(null, content, toolCalls);
    }

    public static ChatMessage toolResultMessage(String toolCallId, String content) {
        return new ChatMessage(null, toolCallId, null, content, true);
    }

    public static ChatMessage toolResultMessage(String toolCallId, String toolName, String content) {
        return new ChatMessage(null, toolCallId, toolName, content, true);
    }

    public static ChatMessage systemMessage(String content) {
        return new ChatMessage(UUID.randomUUID().toString(), null, "system", content,
                null, null, null, System.currentTimeMillis(),
                null, null);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("conversationId", conversationId);
        json.addProperty("role", role);
        json.addProperty("content", content);
        json.addProperty("toolCallId", toolCallId);
        json.addProperty("toolName", toolName);
        json.addProperty("timestamp", timestamp);

        if (type != null) json.addProperty("type", type.name());
        if (status != null) json.addProperty("status", status.name());

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
        String toolName = json.has("toolName") && !json.get("toolName").isJsonNull()
                ? json.get("toolName").getAsString() : null;
        long timestamp = json.has("timestamp") && !json.get("timestamp").isJsonNull()
                ? json.get("timestamp").getAsLong() : System.currentTimeMillis();

        MessageType type = null;
        if (json.has("type") && !json.get("type").isJsonNull()) {
            try { type = MessageType.valueOf(json.get("type").getAsString()); } catch (IllegalArgumentException ignored) {}
        }
        if (type == null) {
            type = inferTypeFromRole(role);
        }

        MessageStatus status = null;
        if (json.has("status") && !json.get("status").isJsonNull()) {
            try { status = MessageStatus.valueOf(json.get("status").getAsString()); } catch (IllegalArgumentException ignored) {}
        }
        if (status == null) {
            status = MessageStatus.SENT;
        }

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

        return new ChatMessage(id, conversationId, role, content, toolCalls, toolCallId, toolName, timestamp, type, status);
    }

    private static MessageType inferTypeFromRole(String role) {
        if (role == null) return MessageType.USER;
        switch (role) {
            case "user": return MessageType.USER;
            case "assistant": return MessageType.AI;
            case "system": return MessageType.SYSTEM;
            case "tool": return MessageType.TOOL;
            default: return MessageType.USER;
        }
    }

    @Override
    public String toString() {
        return "ChatMessage{id='" + id + "', role='" + role + "', type=" + type + "'}";
    }
}