package pro.sketchware.ai;

import android.os.Environment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pro.sketchware.utility.FileUtil;

public class AgentStorage {
    private static volatile AgentStorage instance;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File baseDir = new File(Environment.getExternalStorageDirectory(), ".sketchware/data/system/agents");
    private final File conversationsDir = new File(baseDir, "conversations");
    private final File messagesDir = new File(baseDir, "messages");
    private final File workspacesFile = new File(baseDir, "workspaces.json");
    private final File modelCacheFile = new File(baseDir, "model_cache.json");

    private AgentStorage() {
        ensureStructure();
    }

    public static AgentStorage getInstance() {
        if (instance == null) {
            synchronized (AgentStorage.class) {
                if (instance == null) {
                    instance = new AgentStorage();
                }
            }
        }
        return instance;
    }

    private void ensureStructure() {
        FileUtil.makeDir(baseDir.getAbsolutePath());
        FileUtil.makeDir(conversationsDir.getAbsolutePath());
        FileUtil.makeDir(messagesDir.getAbsolutePath());
        if (!workspacesFile.exists()) {
            FileUtil.writeFile(workspacesFile.getAbsolutePath(), "[]");
        }
        if (!modelCacheFile.exists()) {
            FileUtil.writeFile(modelCacheFile.getAbsolutePath(), "{}");
        }
    }

    public synchronized ArrayList<AgentWorkspace> getWorkspaces() {
        Type type = new TypeToken<ArrayList<AgentWorkspace>>() {
        }.getType();
        ArrayList<AgentWorkspace> workspaces = read(workspacesFile, type, new ArrayList<>());
        for (AgentWorkspace workspace : workspaces) {
            if (workspace.projectIds == null) {
                workspace.projectIds = new ArrayList<>();
            }
            if (workspace.name == null || workspace.name.trim().isEmpty()) {
                workspace.name = "Workspace";
            }
        }
        return workspaces;
    }

    public synchronized AgentWorkspace getWorkspace(String workspaceId) {
        for (AgentWorkspace workspace : getWorkspaces()) {
            if (workspace.id != null && workspace.id.equals(workspaceId)) {
                return workspace;
            }
        }
        return null;
    }

    public synchronized AgentWorkspace createWorkspace(String name) {
        ArrayList<AgentWorkspace> workspaces = getWorkspaces();
        AgentWorkspace workspace = AgentWorkspace.create(name);
        workspaces.add(0, workspace);
        saveWorkspaces(workspaces);
        return workspace;
    }

    public synchronized void updateWorkspace(AgentWorkspace updatedWorkspace) {
        ArrayList<AgentWorkspace> workspaces = getWorkspaces();
        for (int i = 0; i < workspaces.size(); i++) {
            AgentWorkspace workspace = workspaces.get(i);
            if (workspace.id != null && workspace.id.equals(updatedWorkspace.id)) {
                updatedWorkspace.updatedAt = System.currentTimeMillis();
                workspaces.set(i, updatedWorkspace);
                saveWorkspaces(workspaces);
                return;
            }
        }
    }

    public synchronized void deleteWorkspace(String workspaceId) {
        ArrayList<AgentWorkspace> workspaces = getWorkspaces();
        workspaces.removeIf(workspace -> workspace.id != null && workspace.id.equals(workspaceId));
        saveWorkspaces(workspaces);

        ArrayList<AgentConversation> conversations = getConversations(workspaceId);
        for (AgentConversation conversation : conversations) {
            deleteMessageFile(conversation.id);
        }

        File conversationFile = getConversationFile(workspaceId);
        if (conversationFile.exists()) {
            FileUtil.deleteFile(conversationFile.getAbsolutePath());
        }
    }

    private void saveWorkspaces(ArrayList<AgentWorkspace> workspaces) {
        FileUtil.writeFile(workspacesFile.getAbsolutePath(), gson.toJson(workspaces));
    }

    public synchronized ArrayList<AgentConversation> getConversations(String workspaceId) {
        Type type = new TypeToken<ArrayList<AgentConversation>>() {
        }.getType();
        ArrayList<AgentConversation> conversations = read(getConversationFile(workspaceId), type, new ArrayList<>());
        for (AgentConversation conversation : conversations) {
            if (conversation.title == null || conversation.title.trim().isEmpty()) {
                conversation.title = "Conversation";
            }
        }
        return conversations;
    }

    public synchronized AgentConversation getConversation(String workspaceId, String conversationId) {
        ArrayList<AgentConversation> conversations = getConversations(workspaceId);
        for (AgentConversation conversation : conversations) {
            if (conversation.id != null && conversation.id.equals(conversationId)) {
                return conversation;
            }
        }
        return null;
    }

    public synchronized AgentConversation createConversation(String workspaceId, String title) {
        ArrayList<AgentConversation> conversations = getConversations(workspaceId);
        AgentConversation conversation = AgentConversation.create(workspaceId, title);
        conversations.add(0, conversation);
        saveConversations(workspaceId, conversations);
        return conversation;
    }

    private void saveConversations(String workspaceId, ArrayList<AgentConversation> conversations) {
        FileUtil.writeFile(getConversationFile(workspaceId).getAbsolutePath(), gson.toJson(conversations));
    }

    public synchronized void updateConversation(AgentConversation updatedConversation) {
        ArrayList<AgentConversation> conversations = getConversations(updatedConversation.workspaceId);
        for (int i = 0; i < conversations.size(); i++) {
            AgentConversation conversation = conversations.get(i);
            if (conversation.id != null && conversation.id.equals(updatedConversation.id)) {
                updatedConversation.updatedAt = System.currentTimeMillis();
                conversations.set(i, updatedConversation);
                saveConversations(updatedConversation.workspaceId, conversations);
                return;
            }
        }
    }

    public synchronized void deleteConversation(String workspaceId, String conversationId) {
        ArrayList<AgentConversation> conversations = getConversations(workspaceId);
        conversations.removeIf(conversation -> conversation.id != null && conversation.id.equals(conversationId));
        saveConversations(workspaceId, conversations);
        deleteMessageFile(conversationId);
    }

    public synchronized ArrayList<AgentMessage> getMessages(String conversationId) {
        Type type = new TypeToken<ArrayList<AgentMessage>>() {
        }.getType();
        return read(getMessageFile(conversationId), type, new ArrayList<>());
    }

    public synchronized void appendMessage(AgentMessage message) {
        ArrayList<AgentMessage> messages = getMessages(message.conversationId);
        messages.add(message);
        saveMessages(message.conversationId, messages);
    }

    public synchronized void saveMessages(String conversationId, ArrayList<AgentMessage> messages) {
        FileUtil.writeFile(getMessageFile(conversationId).getAbsolutePath(), gson.toJson(messages));
    }

    public synchronized Map<String, AgentProviderModelCache> getModelCache() {
        Type type = new TypeToken<HashMap<String, AgentProviderModelCache>>() {
        }.getType();
        return read(modelCacheFile, type, new HashMap<>());
    }

    public synchronized ArrayList<AgentModelInfo> getCachedModels(String provider) {
        AgentProviderModelCache cache = getModelCache().get(provider);
        if (cache == null || cache.models == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(cache.models);
    }

    public synchronized void updateCachedModels(String provider, List<AgentModelInfo> models) {
        Map<String, AgentProviderModelCache> cacheMap = getModelCache();
        AgentProviderModelCache cache = new AgentProviderModelCache();
        cache.updatedAt = System.currentTimeMillis();
        cache.models.addAll(models);
        cacheMap.put(provider, cache);
        FileUtil.writeFile(modelCacheFile.getAbsolutePath(), gson.toJson(cacheMap));
    }

    private File getConversationFile(String workspaceId) {
        File file = new File(conversationsDir, workspaceId + ".json");
        if (!file.exists()) {
            FileUtil.writeFile(file.getAbsolutePath(), "[]");
        }
        return file;
    }

    private File getMessageFile(String conversationId) {
        File file = new File(messagesDir, conversationId + ".json");
        if (!file.exists()) {
            FileUtil.writeFile(file.getAbsolutePath(), "[]");
        }
        return file;
    }

    private void deleteMessageFile(String conversationId) {
        File file = new File(messagesDir, conversationId + ".json");
        if (file.exists()) {
            FileUtil.deleteFile(file.getAbsolutePath());
        }
    }

    private <T> T read(File file, Type type, T fallback) {
        try {
            if (!file.exists()) {
                FileUtil.writeFile(file.getAbsolutePath(), gson.toJson(fallback));
                return fallback;
            }
            String content = FileUtil.readFile(file.getAbsolutePath());
            if (content == null || content.trim().isEmpty()) {
                return fallback;
            }
            T parsed = gson.fromJson(content, type);
            return parsed == null ? fallback : parsed;
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
