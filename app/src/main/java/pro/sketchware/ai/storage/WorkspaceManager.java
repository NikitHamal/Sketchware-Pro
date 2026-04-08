package pro.sketchware.ai.storage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;

import pro.sketchware.ai.models.Workspace;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WorkspaceManager {

    private final File storageDir;
    private final Gson gson;
    private final ConversationManager conversationManager;

    public WorkspaceManager(@NonNull Context context) {
        storageDir = new File(context.getFilesDir(), "ai_agent/workspaces");
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        gson = new Gson();
        conversationManager = new ConversationManager(context);
    }

    public void saveWorkspace(@NonNull Workspace workspace) {
        File file = new File(storageDir, workspace.getId() + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(workspace, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Nullable
    public Workspace getWorkspace(@NonNull String id) {
        File file = new File(storageDir, id + ".json");
        if (!file.exists()) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return gson.fromJson(reader, Workspace.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @NonNull
    public List<Workspace> getAllWorkspaces() {
        List<Workspace> workspaces = new ArrayList<>();
        File[] files = storageDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) {
            return workspaces;
        }
        for (File file : files) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                Workspace workspace = gson.fromJson(reader, Workspace.class);
                if (workspace != null) {
                    workspaces.add(workspace);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Collections.sort(workspaces, (a, b) -> Long.compare(b.getUpdatedAt(), a.getUpdatedAt()));
        return workspaces;
    }

    public void deleteWorkspace(@NonNull String id) {
        File file = new File(storageDir, id + ".json");
        if (file.exists()) {
            file.delete();
        }
        conversationManager.deleteAllConversationsForWorkspace(id);
    }

    public void updateWorkspace(@NonNull Workspace workspace) {
        workspace.setUpdatedAt(System.currentTimeMillis());
        saveWorkspace(workspace);
    }
}
