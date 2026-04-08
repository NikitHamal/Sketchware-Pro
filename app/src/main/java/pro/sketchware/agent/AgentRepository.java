package pro.sketchware.agent;

import static pro.sketchware.utility.GsonUtils.getGson;

import android.os.Environment;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import pro.sketchware.utility.FileUtil;

public class AgentRepository {

    private static final File ROOT_DIR = new File(Environment.getExternalStorageDirectory(), ".sketchware/data/agent");
    private static final File WORKSPACES_FILE = new File(ROOT_DIR, "workspaces.json");
    private static final File SETTINGS_FILE = new File(ROOT_DIR, "settings.json");
    private static final File CONVERSATIONS_DIR = new File(ROOT_DIR, "conversations");
    private static final Type WORKSPACE_LIST_TYPE = new TypeToken<ArrayList<Workspace>>() {}.getType();
    private static final Type SETTINGS_TYPE = new TypeToken<SettingsState>() {}.getType();

    public AgentRepository() {
        ensureStorage();
    }

    public synchronized void ensureStorage() {
        FileUtil.makeDir(ROOT_DIR.getAbsolutePath());
        FileUtil.makeDir(CONVERSATIONS_DIR.getAbsolutePath());
        if (!WORKSPACES_FILE.exists()) {
            FileUtil.writeFile(WORKSPACES_FILE.getAbsolutePath(), "[]");
        }
        if (!SETTINGS_FILE.exists()) {
            FileUtil.writeFile(SETTINGS_FILE.getAbsolutePath(), getGson().toJson(new SettingsState()));
        }
    }

    @NonNull
    public synchronized ArrayList<Workspace> getWorkspaces() {
        String raw = FileUtil.readFile(WORKSPACES_FILE.getAbsolutePath());
        ArrayList<Workspace> workspaces = getGson().fromJson(raw, WORKSPACE_LIST_TYPE);
        if (workspaces == null) {
            workspaces = new ArrayList<>();
        }
        workspaces.sort(Comparator.comparingLong(Workspace::getSortKey).reversed());
        return workspaces;
    }

    @Nullable
    public synchronized Workspace getWorkspace(String workspaceId) {
        for (Workspace workspace : getWorkspaces()) {
            if (workspace.id.equals(workspaceId)) {
                return workspace;
            }
        }
        return null;
    }

    @NonNull
    public synchronized Workspace createWorkspace(String name) {
        ArrayList<Workspace> workspaces = getWorkspaces();
        Workspace workspace = new Workspace();
        workspace.id = newId("ws");
        workspace.name = sanitizeTitle(name, "New workspace");
        workspace.createdAt = System.currentTimeMillis();
        workspace.updatedAt = workspace.createdAt;
        workspaces.add(workspace);
        saveWorkspaces(workspaces);
        return workspace;
    }

    public synchronized void saveWorkspace(@NonNull Workspace updatedWorkspace) {
        ArrayList<Workspace> workspaces = getWorkspaces();
        boolean updated = false;
        for (int i = 0; i < workspaces.size(); i++) {
            if (workspaces.get(i).id.equals(updatedWorkspace.id)) {
                workspaces.set(i, updatedWorkspace);
                updated = true;
                break;
            }
        }
        if (!updated) {
            workspaces.add(updatedWorkspace);
        }
        saveWorkspaces(workspaces);
    }

    public synchronized void deleteWorkspace(String workspaceId) {
        ArrayList<Workspace> workspaces = getWorkspaces();
        workspaces.removeIf(workspace -> workspace.id.equals(workspaceId));
        saveWorkspaces(workspaces);

        ArrayList<Conversation> conversations = getConversations(workspaceId);
        for (Conversation conversation : conversations) {
            FileUtil.deleteFile(getConversationFile(conversation.id).getAbsolutePath());
        }
    }

    @NonNull
    public synchronized ArrayList<Conversation> getConversations(String workspaceId) {
        ensureStorage();
        ArrayList<Conversation> conversations = new ArrayList<>();
        File[] files = CONVERSATIONS_DIR.listFiles();
        if (files == null) {
            return conversations;
        }

        for (File file : files) {
            if (!file.isFile() || !file.getName().endsWith(".json")) {
                continue;
            }

            try {
                Conversation conversation = getGson().fromJson(FileUtil.readFile(file.getAbsolutePath()), Conversation.class);
                if (conversation != null && workspaceId.equals(conversation.workspaceId)) {
                    conversations.add(conversation);
                }
            } catch (Exception ignored) {
            }
        }

        conversations.sort(Comparator.comparingLong(Conversation::getSortKey).reversed());
        return conversations;
    }

    @Nullable
    public synchronized Conversation getConversation(String conversationId) {
        File file = getConversationFile(conversationId);
        if (!file.exists()) {
            return null;
        }
        Conversation conversation = getGson().fromJson(FileUtil.readFile(file.getAbsolutePath()), Conversation.class);
        if (conversation != null) {
            conversation.ensureLists();
        }
        return conversation;
    }

    @NonNull
    public synchronized Conversation createConversation(String workspaceId, @Nullable String title, @Nullable String providerId,
                                                        @Nullable String modelId) {
        Conversation conversation = new Conversation();
        conversation.id = newId("conv");
        conversation.workspaceId = workspaceId;
        conversation.title = sanitizeTitle(title, "New conversation");
        conversation.providerId = TextUtils.isEmpty(providerId) ? AgentProvider.GEMINI.id : providerId;
        conversation.modelId = modelId;
        conversation.createdAt = System.currentTimeMillis();
        conversation.updatedAt = conversation.createdAt;
        conversation.ensureLists();
        saveConversation(conversation);
        touchWorkspace(workspaceId);
        return conversation;
    }

    public synchronized void saveConversation(@NonNull Conversation conversation) {
        conversation.ensureLists();
        if (conversation.updatedAt <= 0L) {
            conversation.updatedAt = System.currentTimeMillis();
        }
        FileUtil.writeFile(getConversationFile(conversation.id).getAbsolutePath(), getGson().toJson(conversation));
        touchWorkspace(conversation.workspaceId);
    }

    public synchronized void deleteConversation(String conversationId) {
        Conversation conversation = getConversation(conversationId);
        FileUtil.deleteFile(getConversationFile(conversationId).getAbsolutePath());
        if (conversation != null) {
            touchWorkspace(conversation.workspaceId);
        }
    }

    @NonNull
    public synchronized SettingsState getSettings() {
        SettingsState state = getGson().fromJson(FileUtil.readFile(SETTINGS_FILE.getAbsolutePath()), SETTINGS_TYPE);
        if (state == null) {
            state = new SettingsState();
        }
        state.ensureDefaults();
        return state;
    }

    @NonNull
    public synchronized ProviderState getProviderState(@NonNull AgentProvider provider) {
        SettingsState settings = getSettings();
        ProviderState providerState = settings.providers.get(provider.id);
        if (providerState == null) {
            providerState = new ProviderState();
            providerState.providerId = provider.id;
            settings.providers.put(provider.id, providerState);
            saveSettings(settings);
        }
        providerState.ensureLists();
        return providerState;
    }

    public synchronized void saveProviderState(@NonNull AgentProvider provider, @NonNull ProviderState providerState) {
        SettingsState settings = getSettings();
        providerState.providerId = provider.id;
        providerState.ensureLists();
        settings.providers.put(provider.id, providerState);
        saveSettings(settings);
    }

    private synchronized void saveSettings(@NonNull SettingsState state) {
        state.ensureDefaults();
        FileUtil.writeFile(SETTINGS_FILE.getAbsolutePath(), getGson().toJson(state));
    }

    private synchronized void saveWorkspaces(@NonNull List<Workspace> workspaces) {
        FileUtil.writeFile(WORKSPACES_FILE.getAbsolutePath(), getGson().toJson(workspaces));
    }

    private synchronized void touchWorkspace(@Nullable String workspaceId) {
        if (TextUtils.isEmpty(workspaceId)) {
            return;
        }
        ArrayList<Workspace> workspaces = getWorkspaces();
        for (Workspace workspace : workspaces) {
            if (workspace.id.equals(workspaceId)) {
                workspace.updatedAt = System.currentTimeMillis();
                saveWorkspaces(workspaces);
                return;
            }
        }
    }

    @NonNull
    private File getConversationFile(@NonNull String conversationId) {
        return new File(CONVERSATIONS_DIR, conversationId + ".json");
    }

    @NonNull
    private static String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    @NonNull
    public static String sanitizeTitle(@Nullable String value, @NonNull String fallback) {
        if (TextUtils.isEmpty(value)) {
            return fallback;
        }
        String cleaned = value.trim().replaceAll("\\s+", " ");
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    public static class Workspace {
        public String id;
        public String name;
        public long createdAt;
        public long updatedAt;
        public ArrayList<String> projectIds = new ArrayList<>();

        public long getSortKey() {
            return updatedAt > 0 ? updatedAt : createdAt;
        }
    }

    public static class Conversation {
        public String id;
        public String workspaceId;
        public String title;
        public String providerId;
        public String modelId;
        public long createdAt;
        public long updatedAt;
        public ArrayList<Message> messages = new ArrayList<>();

        public void ensureLists() {
            if (messages == null) {
                messages = new ArrayList<>();
            }
        }

        public long getSortKey() {
            return updatedAt > 0 ? updatedAt : createdAt;
        }

        public void addMessage(@NonNull String role, @Nullable String name, @NonNull String content) {
            ensureLists();
            Message message = new Message();
            message.id = newId("msg");
            message.role = role;
            message.name = name;
            message.content = content;
            message.createdAt = System.currentTimeMillis();
            messages.add(message);
            updatedAt = message.createdAt;
            if (TextUtils.isEmpty(title) || "New conversation".equals(title)) {
                if ("user".equals(role) && !TextUtils.isEmpty(content)) {
                    String normalized = content.trim().replace('\n', ' ');
                    title = normalized.length() > 54 ? normalized.substring(0, 54) + "…" : normalized;
                }
            }
        }
    }

    public static class Message {
        public String id;
        public String role;
        public String name;
        public String content;
        public long createdAt;
    }

    public static class SettingsState {
        public HashMap<String, ProviderState> providers = new HashMap<>();

        public void ensureDefaults() {
            if (providers == null) {
                providers = new HashMap<>();
            }
            for (AgentProvider provider : AgentProvider.values()) {
                providers.computeIfAbsent(provider.id, key -> {
                    ProviderState state = new ProviderState();
                    state.providerId = key;
                    return state;
                });
            }
            for (Map.Entry<String, ProviderState> entry : providers.entrySet()) {
                ProviderState state = entry.getValue();
                if (state == null) {
                    state = new ProviderState();
                    state.providerId = entry.getKey();
                    entry.setValue(state);
                }
                state.ensureLists();
            }
        }
    }

    public static class ProviderState {
        public String providerId;
        public String apiKey = "";
        public long modelsUpdatedAt;
        public ArrayList<ModelInfo> cachedModels = new ArrayList<>();

        public void ensureLists() {
            if (cachedModels == null) {
                cachedModels = new ArrayList<>();
            }
            cachedModels.sort(Comparator.comparing(model -> model.label == null ? model.id : model.label, String.CASE_INSENSITIVE_ORDER));
        }
    }

    public static class ModelInfo {
        public String id;
        public String label;
        public String description;
        public boolean supportsTools = true;

        @NonNull
        public String getDisplayName() {
            if (!TextUtils.isEmpty(label)) {
                return label;
            }
            return id == null ? "" : id;
        }

        @NonNull
        @Override
        public String toString() {
            return getDisplayName();
        }
    }
}
