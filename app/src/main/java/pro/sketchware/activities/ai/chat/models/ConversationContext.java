package pro.sketchware.activities.ai.chat.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConversationContext {
    private String qwenChatId;
    private String conversationId;
    private List<ChatMessage> fullHistory;
    private String currentProjectId;
    private Map<String, Object> sessionState;
    private List<String> executedActions;
    private String lastParentId;
    
    public ConversationContext(String conversationId) {
        this.conversationId = conversationId;
        this.fullHistory = new ArrayList<>();
        this.sessionState = new HashMap<>();
        this.executedActions = new ArrayList<>();
    }
    
    // Getters and setters
    public String getQwenChatId() {
        return qwenChatId;
    }
    
    public void setQwenChatId(String qwenChatId) {
        this.qwenChatId = qwenChatId;
    }
    
    public String getConversationId() {
        return conversationId;
    }
    
    public List<ChatMessage> getFullHistory() {
        return fullHistory;
    }
    
    public void addMessage(ChatMessage message) {
        this.fullHistory.add(message);
    }
    
    public String getCurrentProjectId() {
        return currentProjectId;
    }
    
    public void setCurrentProjectId(String currentProjectId) {
        this.currentProjectId = currentProjectId;
    }
    
    public Map<String, Object> getSessionState() {
        return sessionState;
    }
    
    public List<String> getExecutedActions() {
        return executedActions;
    }
    
    public void addExecutedAction(String action) {
        this.executedActions.add(action);
    }
    
    public String getLastParentId() {
        return lastParentId;
    }
    
    public void setLastParentId(String lastParentId) {
        this.lastParentId = lastParentId;
    }
}