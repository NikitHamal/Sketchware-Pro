package pro.sketchware.ai;

import java.util.ArrayList;
import java.util.UUID;

public class AgentWorkspace {
    public String id;
    public String name;
    public long createdAt;
    public long updatedAt;
    public ArrayList<String> projectIds = new ArrayList<>();

    public static AgentWorkspace create(String name) {
        AgentWorkspace workspace = new AgentWorkspace();
        workspace.id = UUID.randomUUID().toString();
        workspace.name = name;
        workspace.createdAt = System.currentTimeMillis();
        workspace.updatedAt = workspace.createdAt;
        return workspace;
    }
}
