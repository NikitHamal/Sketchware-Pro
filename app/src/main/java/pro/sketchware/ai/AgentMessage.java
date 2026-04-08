package pro.sketchware.ai;

import java.util.UUID;

public class AgentMessage {
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_TOOL = "tool";

    public String id;
    public String conversationId;
    public String role;
    public String content;
    public long createdAt;

    public static AgentMessage create(String conversationId, String role, String content) {
        AgentMessage message = new AgentMessage();
        message.id = UUID.randomUUID().toString();
        message.conversationId = conversationId;
        message.role = role;
        message.content = content;
        message.createdAt = System.currentTimeMillis();
        return message;
    }
}
