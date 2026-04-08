package pro.sketchware.activities.main.agent;

import android.os.Bundle;
import android.widget.ArrayAdapter;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.besome.sketch.lib.base.BaseAppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mod.hey.studios.util.Helper;
import pro.sketchware.ai.AgentConversation;
import pro.sketchware.ai.AgentEngine;
import pro.sketchware.ai.AgentMessage;
import pro.sketchware.ai.AgentModelInfo;
import pro.sketchware.ai.AgentModelRepository;
import pro.sketchware.ai.AgentProjectManager;
import pro.sketchware.ai.AgentProvider;
import pro.sketchware.ai.AgentSettings;
import pro.sketchware.ai.AgentStorage;
import pro.sketchware.ai.AgentToolExecutor;
import pro.sketchware.ai.AgentWorkspace;
import pro.sketchware.databinding.ActivityAgentConversationBinding;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.UI;

public class AgentConversationActivity extends BaseAppCompatActivity {
    public static final String EXTRA_WORKSPACE_ID = "workspace_id";
    public static final String EXTRA_CONVERSATION_ID = "conversation_id";

    private final AgentStorage storage = AgentStorage.getInstance();
    private final AgentModelRepository modelRepository = new AgentModelRepository();
    private final AgentEngine agentEngine = new AgentEngine();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<String> providerIds = AgentProvider.all();
    private final ArrayList<AgentModelInfo> currentModels = new ArrayList<>();

    private ActivityAgentConversationBinding binding;
    private ConversationMessageAdapter messageAdapter;
    private AgentWorkspace workspace;
    private AgentConversation conversation;
    private String workspaceId;
    private String conversationId;
    private boolean processing;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityAgentConversationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        workspaceId = getIntent().getStringExtra(EXTRA_WORKSPACE_ID);
        conversationId = getIntent().getStringExtra(EXTRA_CONVERSATION_ID);
        workspace = storage.getWorkspace(workspaceId);
        conversation = storage.getConversation(workspaceId, conversationId);
        if (workspace == null || conversation == null) {
            finish();
            return;
        }

        messageAdapter = new ConversationMessageAdapter();
        binding.messagesList.setLayoutManager(new LinearLayoutManager(this));
        binding.messagesList.setAdapter(messageAdapter);

        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
        binding.toolbar.setTitle(conversation.title == null ? "Conversation" : conversation.title);
        binding.sendButton.setOnClickListener(v -> submitPrompt());
        binding.refreshModels.setOnClickListener(v -> loadModels(true));

        UI.addSystemWindowInsetToPadding(binding.toolbar, true, true, true, false);
        UI.addSystemWindowInsetToPadding(binding.messagesList, true, false, true, false);
        UI.addSystemWindowInsetToPadding(binding.composerContainer, true, false, true, true);

        setupProviderDropdown();
        updateScopeText();
        reloadMessages();
        loadModels(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void setupProviderDropdown() {
        ArrayList<String> labels = new ArrayList<>();
        for (String providerId : providerIds) {
            labels.add(AgentProvider.getDisplayName(providerId));
        }

        ArrayAdapter<String> providerAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                labels
        );
        binding.providerInput.setAdapter(providerAdapter);

        String selectedProvider = conversation.provider == null ? AgentProvider.GEMINI : conversation.provider;
        int selectedIndex = providerIds.indexOf(selectedProvider);
        if (selectedIndex < 0) {
            selectedIndex = 0;
            selectedProvider = providerIds.get(0);
        }
        conversation.provider = selectedProvider;
        storage.updateConversation(conversation);
        binding.providerInput.setText(labels.get(selectedIndex), false);

        binding.providerInput.setOnItemClickListener((parent, view, position, id) -> {
            String providerId = providerIds.get(position);
            if (providerId.equals(conversation.provider)) {
                return;
            }
            conversation.provider = providerId;
            conversation.model = null;
            storage.updateConversation(conversation);
            loadModels(false);
        });
    }

    private void loadModels(boolean forceRefresh) {
        String provider = conversation.provider == null ? AgentProvider.GEMINI : conversation.provider;
        String apiKey = AgentSettings.getApiKey(provider);
        if (apiKey.trim().isEmpty()) {
            currentModels.clear();
            binding.modelInput.setText("", false);
            binding.modelInput.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>()));
            SketchwareUtil.toastError("Set API key for " + AgentProvider.getDisplayName(provider) + " first.");
            return;
        }

        setProcessing(true);
        executor.execute(() -> {
            Exception error = null;
            ArrayList<AgentModelInfo> models = new ArrayList<>();
            try {
                if (forceRefresh) {
                    models = modelRepository.refreshModels(provider, apiKey);
                } else {
                    models = modelRepository.ensureModels(provider, apiKey);
                }
            } catch (Exception e) {
                error = e;
            }

            ArrayList<AgentModelInfo> finalModels = models;
            Exception finalError = error;
            runOnUiThread(() -> {
                setProcessing(false);
                if (finalError != null) {
                    SketchwareUtil.toastError("Failed to load models: " + finalError.getMessage());
                    return;
                }
                currentModels.clear();
                currentModels.addAll(finalModels);
                ArrayList<String> modelNames = new ArrayList<>();
                for (AgentModelInfo model : currentModels) {
                    modelNames.add(model.name);
                }
                ArrayAdapter<String> modelAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, modelNames);
                binding.modelInput.setAdapter(modelAdapter);

                if (!currentModels.isEmpty()) {
                    AgentModelInfo selected = pickSelectedModel(conversation.model, currentModels);
                    conversation.model = selected.id;
                    storage.updateConversation(conversation);
                    binding.modelInput.setText(selected.name, false);
                } else {
                    conversation.model = null;
                    storage.updateConversation(conversation);
                    binding.modelInput.setText("", false);
                }

                binding.modelInput.setOnItemClickListener((parent, view, position, id) -> {
                    AgentModelInfo selectedModel = currentModels.get(position);
                    conversation.model = selectedModel.id;
                    storage.updateConversation(conversation);
                });
            });
        });
    }

    private AgentModelInfo pickSelectedModel(String selectedId, ArrayList<AgentModelInfo> models) {
        if (selectedId != null) {
            for (AgentModelInfo model : models) {
                if (selectedId.equals(model.id)) {
                    return model;
                }
            }
        }
        return models.get(0);
    }

    private void updateScopeText() {
        AgentWorkspace latestWorkspace = storage.getWorkspace(workspaceId);
        if (latestWorkspace != null) {
            workspace = latestWorkspace;
        }
        binding.workspaceScope.setText("Workspace scope: " + workspace.projectIds.size() + " selected projects");
    }

    private void reloadMessages() {
        ArrayList<AgentMessage> messages = storage.getMessages(conversation.id);
        messageAdapter.submit(messages);
        scrollToBottom();
    }

    private void submitPrompt() {
        if (processing) {
            return;
        }

        String prompt = Helper.getText(binding.promptInput).trim();
        if (prompt.isEmpty()) {
            return;
        }

        if (conversation.model == null || conversation.model.trim().isEmpty()) {
            SketchwareUtil.toastError("Select a model first.");
            return;
        }

        AgentMessage userMessage = AgentMessage.create(conversation.id, AgentMessage.ROLE_USER, prompt);
        storage.appendMessage(userMessage);
        messageAdapter.append(userMessage);
        binding.promptInput.setText("");
        scrollToBottom();

        if ("New conversation".equalsIgnoreCase(conversation.title) || "Conversation".equalsIgnoreCase(conversation.title)) {
            conversation.title = abbreviate(prompt, 48);
            storage.updateConversation(conversation);
            binding.toolbar.setTitle(conversation.title);
        }

        runAgentTurn();
    }

    private void runAgentTurn() {
        setProcessing(true);
        executor.execute(() -> {
            Exception error = null;
            try {
                ArrayList<AgentMessage> history = storage.getMessages(conversation.id);
                AgentWorkspace latestWorkspace = storage.getWorkspace(workspaceId);
                if (latestWorkspace != null) {
                    workspace = latestWorkspace;
                }
                AgentToolExecutor toolExecutor = new AgentToolExecutor(storage, workspace, new AgentProjectManager(this));
                ArrayList<AgentMessage> generated = agentEngine.runTurn(conversation, workspace, history, toolExecutor);
                for (AgentMessage message : generated) {
                    storage.appendMessage(message);
                }
                conversation.updatedAt = System.currentTimeMillis();
                storage.updateConversation(conversation);
            } catch (Exception e) {
                error = e;
            }

            Exception finalError = error;
            runOnUiThread(() -> {
                setProcessing(false);
                if (finalError != null) {
                    SketchwareUtil.toastError("Agent execution failed: " + finalError.getMessage());
                } else {
                    updateScopeText();
                    reloadMessages();
                }
            });
        });
    }

    private void setProcessing(boolean processing) {
        this.processing = processing;
        binding.sendButton.setEnabled(!processing);
        binding.refreshModels.setEnabled(!processing);
        binding.providerInput.setEnabled(!processing);
        binding.modelInput.setEnabled(!processing);
        binding.typingIndicator.setVisibility(processing ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private void scrollToBottom() {
        int count = messageAdapter.getItemCount();
        if (count > 0) {
            binding.messagesList.scrollToPosition(count - 1);
        }
    }

    private String abbreviate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
