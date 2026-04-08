package pro.sketchware.ai.storage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.Conversation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConversationManager {

    private final File conversationsBaseDir;
    private final File messagesBaseDir;
    private final Gson gson;

    public ConversationManager(@NonNull Context context) {
        conversationsBaseDir = new File(context.getFilesDir(), "ai_agent/conversations");
        messagesBaseDir = new File(context.getFilesDir(), "ai_agent/messages");
        if (!conversationsBaseDir.exists()) {
            conversationsBaseDir.mkdirs();
        }
        if (!messagesBaseDir.exists()) {
            messagesBaseDir.mkdirs();
        }
        gson = new Gson();
    }

    // --- Conversation Methods ---

    public void saveConversation(@NonNull Conversation conversation) {
        File workspaceDir = new File(conversationsBaseDir, conversation.getWorkspaceId());
        if (!workspaceDir.exists()) {
            workspaceDir.mkdirs();
        }
        File file = new File(workspaceDir, conversation.getId() + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(conversation, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Nullable
    public Conversation getConversation(@NonNull String id, @NonNull String workspaceId) {
        File file = new File(conversationsBaseDir, workspaceId + "/" + id + ".json");
        if (!file.exists()) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return gson.fromJson(reader, Conversation.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @NonNull
    public List<Conversation> getConversationsForWorkspace(@NonNull String workspaceId) {
        List<Conversation> conversations = new ArrayList<>();
        File workspaceDir = new File(conversationsBaseDir, workspaceId);
        if (!workspaceDir.exists() || !workspaceDir.isDirectory()) {
            return conversations;
        }
        File[] files = workspaceDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) {
            return conversations;
        }
        for (File file : files) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                Conversation conversation = gson.fromJson(reader, Conversation.class);
                if (conversation != null) {
                    conversations.add(conversation);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Collections.sort(conversations, (a, b) -> Long.compare(b.getUpdatedAt(), a.getUpdatedAt()));
        return conversations;
    }

    public void deleteConversation(@NonNull String id, @NonNull String workspaceId) {
        File file = new File(conversationsBaseDir, workspaceId + "/" + id + ".json");
        if (file.exists()) {
            file.delete();
        }
        deleteMessages(id);
    }

    public void deleteAllConversationsForWorkspace(@NonNull String workspaceId) {
        File workspaceDir = new File(conversationsBaseDir, workspaceId);
        if (workspaceDir.exists() && workspaceDir.isDirectory()) {
            File[] files = workspaceDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    if (name.endsWith(".json")) {
                        String conversationId = name.substring(0, name.length() - 5);
                        deleteMessages(conversationId);
                    }
                    file.delete();
                }
            }
            workspaceDir.delete();
        }
    }

    // --- Message Methods ---

    public void saveMessage(@NonNull String conversationId, @NonNull ChatMessage message) {
        List<ChatMessage> messages = getMessages(conversationId);
        messages.add(message);
        writeMessages(conversationId, messages);
    }

    @NonNull
    public List<ChatMessage> getMessages(@NonNull String conversationId) {
        File messagesDir = new File(messagesBaseDir, conversationId);
        File file = new File(messagesDir, "messages.json");
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            Type listType = new TypeToken<List<ChatMessage>>() {}.getType();
            List<ChatMessage> messages = gson.fromJson(reader, listType);
            if (messages == null) {
                return new ArrayList<>();
            }
            Collections.sort(messages, (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));
            return messages;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public void updateLastMessage(@NonNull String conversationId, @NonNull ChatMessage message) {
        List<ChatMessage> messages = getMessages(conversationId);
        if (!messages.isEmpty()) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                if (messages.get(i).getId().equals(message.getId())) {
                    messages.set(i, message);
                    writeMessages(conversationId, messages);
                    return;
                }
            }
        }
        messages.add(message);
        writeMessages(conversationId, messages);
    }

    public void deleteMessages(@NonNull String conversationId) {
        File messagesDir = new File(messagesBaseDir, conversationId);
        if (messagesDir.exists() && messagesDir.isDirectory()) {
            File[] files = messagesDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            messagesDir.delete();
        }
    }

    // --- Private Helper ---

    private void writeMessages(@NonNull String conversationId, @NonNull List<ChatMessage> messages) {
        File messagesDir = new File(messagesBaseDir, conversationId);
        if (!messagesDir.exists()) {
            messagesDir.mkdirs();
        }
        File file = new File(messagesDir, "messages.json");
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(messages, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
