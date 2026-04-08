package pro.sketchware.ai;

import java.util.UUID;

public class AgentConversation {
    public String id;
    public String workspaceId;
    public String title;
    public String provider;
    public String model;
    public long createdAt;
    public long updatedAt;

    public static AgentConversation create(String workspaceId, String title) {
        AgentConversation conversation = new AgentConversation();
        conversation.id = UUID.randomUUID().toString();
        conversation.workspaceId = workspaceId;
        conversation.title = title;
        conversation.provider = AgentProvider.GEMINI;
        conversation.createdAt = System.currentTimeMillis();
        conversation.updatedAt = conversation.createdAt;
        return conversation;
    }
}
