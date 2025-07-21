package pro.sketchware.activities.ai.chat.models;

public class ChatMessage {
    public static final int TYPE_USER = 1;
    public static final int TYPE_AI = 2;

    private String id;
    private String content;
    private int type;
    private long timestamp;

    public ChatMessage() {
    }

    public ChatMessage(String id, String content, int type, long timestamp) {
        this.id = id;
        this.content = content;
        this.type = type;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isUserMessage() {
        return type == TYPE_USER;
    }

    public boolean isAiMessage() {
        return type == TYPE_AI;
    }
}