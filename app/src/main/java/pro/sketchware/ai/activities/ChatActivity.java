package pro.sketchware.ai.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.ai.adapters.ChatAdapter;
import pro.sketchware.ai.adapters.ModelSelectorAdapter;
import pro.sketchware.ai.engine.AgentExecutor;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.Conversation;
import pro.sketchware.ai.models.ModelInfo;
import pro.sketchware.ai.models.ToolCall;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.ai.models.Workspace;
import pro.sketchware.ai.storage.AiPreferences;
import pro.sketchware.ai.storage.ConversationManager;
import pro.sketchware.ai.storage.WorkspaceManager;
import pro.sketchware.databinding.ActivityChatBinding;
import pro.sketchware.databinding.DialogModelSelectorBinding;

public class ChatActivity extends AppCompatActivity implements AgentExecutor.AgentCallback,
        ChatAdapter.OnArtifactActionListener {

    public static final String EXTRA_CONVERSATION_ID = "conversation_id";
    public static final String EXTRA_WORKSPACE_ID = "workspace_id";

    private ActivityChatBinding binding;
    private ConversationManager conversationManager;
    private WorkspaceManager workspaceManager;
    private AiPreferences preferences;
    private ChatAdapter chatAdapter;
    private AgentExecutor agentExecutor;

    private String conversationId;
    private String workspaceId;
    private Conversation conversation;
    private Workspace workspace;

    private AiProvider currentProvider;
    private String currentModelId;
    private boolean isAgentRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        conversationId = getIntent().getStringExtra(EXTRA_CONVERSATION_ID);
        workspaceId = getIntent().getStringExtra(EXTRA_WORKSPACE_ID);

        if (conversationId == null || workspaceId == null) {
            finish();
            return;
        }

        conversationManager = new ConversationManager(this);
        workspaceManager = new WorkspaceManager(this);
        preferences = AiPreferences.getInstance(this);

        conversation = conversationManager.getConversation(conversationId, workspaceId);
        workspace = workspaceManager.getWorkspace(workspaceId);

        if (conversation == null) {
            Toast.makeText(this, "Conversation not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupToolbar();
        setupChat();
        setupInput();
        loadModelInfo();
        loadMessages();
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbarTitle.setText(conversation.getTitle());
        binding.modelSelectorRow.setOnClickListener(v -> showModelSelector());
    }

    private void setupChat() {
        chatAdapter = new ChatAdapter();
        chatAdapter.setArtifactActionListener(this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.messagesList.setLayoutManager(layoutManager);
        binding.messagesList.setAdapter(chatAdapter);
    }

    private void setupInput() {
        binding.btnSend.setOnClickListener(v -> {
            if (isAgentRunning) {
                stopAgent();
            } else {
                sendMessage();
            }
        });
        binding.btnSend.setEnabled(false);

        binding.inputMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshComposerState();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void refreshComposerState() {
        if (isAgentRunning) {
            binding.btnSend.setEnabled(true);
            binding.btnSend.setImageResource(R.drawable.ic_mtrl_cancel);
            binding.btnSend.setContentDescription("Stop");
        } else {
            boolean hasInput = binding.inputMessage.getText() != null && binding.inputMessage.getText().length() > 0;
            binding.btnSend.setEnabled(hasInput);
            binding.btnSend.setImageResource(R.drawable.ic_send);
            binding.btnSend.setContentDescription("Send");
        }
    }

    private void stopAgent() {
        if (agentExecutor != null) {
            agentExecutor.cancel();
        }
        binding.typingText.setText("Stopping…");
        setAgentRunning(false);
    }

    private void loadModelInfo() {
        String providerName = conversation.getProviderName();
        if (providerName != null && !providerName.isEmpty()) {
            currentProvider = AiProvider.fromName(providerName);
        }
        if (currentProvider == null) {
            currentProvider = preferences.getSelectedProvider();
        }

        String modelId = conversation.getModelId();
        if (modelId != null && !modelId.isEmpty()) {
            currentModelId = modelId;
        } else {
            currentModelId = preferences.getSelectedModel(currentProvider);
            if (currentModelId == null) {
                List<ModelInfo> models = preferences.getCachedModels(currentProvider);
                if (!models.isEmpty()) {
                    currentModelId = models.get(0).getId();
                }
            }
        }

        updateModelDisplay();
    }

    private void updateModelDisplay() {
        if (currentModelId != null && !currentModelId.isEmpty()) {
            String displayName = currentModelId;
            if (displayName.contains("/")) {
                displayName = displayName.substring(displayName.lastIndexOf('/') + 1);
            }
            binding.toolbarModel.setText(displayName);
        } else {
            binding.toolbarModel.setText("Select model");
        }
    }

    private void loadMessages() {
        List<ChatMessage> messages = conversationManager.getMessages(conversationId);
        chatAdapter.setMessages(messages);
        updateEmptyState();
        if (!messages.isEmpty()) {
            binding.messagesList.scrollToPosition(chatAdapter.getItemCount() - 1);
        }
    }

    private void updateEmptyState() {
        boolean showEmpty = chatAdapter.getItemCount() == 0 && !isAgentRunning;
        binding.emptyState.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        binding.messagesList.setVisibility(showEmpty ? View.INVISIBLE : View.VISIBLE);
    }

    private void sendMessage() {
        String text = binding.inputMessage.getText() != null
                ? binding.inputMessage.getText().toString().trim() : "";
        if (text.isEmpty() || isAgentRunning) return;

        if (currentModelId == null || currentModelId.isEmpty()) {
            Toast.makeText(this, "Please select a model first", Toast.LENGTH_SHORT).show();
            showModelSelector();
            return;
        }

        String apiKey = preferences.getApiKey(currentProvider);
        if (apiKey == null || apiKey.isEmpty()) {
            Toast.makeText(this,
                    "No API key set for " + currentProvider.getDisplayName(),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        binding.inputMessage.setText("");

        ChatMessage userMsg = new ChatMessage(conversationId, text);
        conversationManager.saveMessage(conversationId, userMsg);
        chatAdapter.addUserMessage(userMsg);
        updateConversationTimestamp();
        updateEmptyState();
        scrollToBottom();

        if ("New Chat".equals(conversation.getTitle())) {
            String title = text.length() > 50 ? text.substring(0, 50) + "..." : text;
            conversation.setTitle(title);
            conversationManager.saveConversation(conversation);
            binding.toolbarTitle.setText(title);
        }

        ChatMessage assistantPlaceholder = new ChatMessage(conversationId, "", null);
        assistantPlaceholder.setStreaming(true);
        chatAdapter.addAssistantMessage(assistantPlaceholder);
        updateEmptyState();
        scrollToBottom();

        setAgentRunning(true);
        binding.typingText.setText("Thinking…");

        List<ChatMessage> history = conversationManager.getMessages(conversationId);
        String systemPrompt = preferences.getSystemPrompt();
        List<String> projectIds = workspace != null ? workspace.getProjectIds() : new ArrayList<>();

        agentExecutor = new AgentExecutor(this, projectIds, workspaceId);
        agentExecutor.execute(history, currentModelId, currentProvider, systemPrompt,
                projectIds, workspaceId, this);
    }

    private void setAgentRunning(boolean running) {
        isAgentRunning = running;
        binding.inputMessage.setEnabled(!running);
        binding.typingIndicator.setVisibility(running ? View.VISIBLE : View.GONE);
        if (running) {
            binding.emptyDescription.setText("The agent is planning and executing tools in this workspace.");
        } else {
            binding.emptyDescription.setText("Create or open a conversation and ask for a Sketchware-compatible app, feature, fix, or refactor.");
        }
        refreshComposerState();
        updateEmptyState();
    }

    private void scrollToBottom() {
        if (chatAdapter.getItemCount() > 0) {
            binding.messagesList.post(() -> binding.messagesList.smoothScrollToPosition(chatAdapter.getItemCount() - 1));
        }
    }

    private void updateConversationTimestamp() {
        conversation.setUpdatedAt(System.currentTimeMillis());
        conversationManager.saveConversation(conversation);
    }

    private void persistToolMessage(ChatMessage toolMessage) {
        conversationManager.saveMessage(conversationId, toolMessage);
        updateConversationTimestamp();
    }

    private void showModelSelector() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        DialogModelSelectorBinding dialogBinding = DialogModelSelectorBinding.inflate(getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());

        List<AiProvider> availableProviders = new ArrayList<>();
        for (AiProvider p : AiProvider.values()) {
            if (preferences.hasApiKey(p)) {
                availableProviders.add(p);
            }
        }

        if (availableProviders.isEmpty()) {
            dialogBinding.emptyState.setVisibility(View.VISIBLE);
            dialogBinding.modelsList.setVisibility(View.GONE);
            dialogBinding.emptyText.setText("No API keys configured.\nGo to AI Settings to add one.");
            dialog.show();
            return;
        }

        for (AiProvider p : availableProviders) {
            dialogBinding.providerTabs.addTab(dialogBinding.providerTabs.newTab()
                    .setText(p.getDisplayName())
                    .setTag(p));
        }

        ModelSelectorAdapter modelAdapter = new ModelSelectorAdapter(model -> {
            currentProvider = model.getProvider();
            currentModelId = model.getId();

            conversation.setModelId(currentModelId);
            conversation.setProviderName(currentProvider.name());
            conversationManager.saveConversation(conversation);

            preferences.setSelectedModel(currentProvider, currentModelId);
            preferences.setSelectedProvider(currentProvider);
            updateModelDisplay();

            dialog.dismiss();
        });
        modelAdapter.setSelectedModelId(currentModelId);
        dialogBinding.modelsList.setAdapter(modelAdapter);

        AiProvider firstProvider = availableProviders.get(0);
        loadModelsForProvider(firstProvider, modelAdapter, dialogBinding);

        for (int i = 0; i < availableProviders.size(); i++) {
            if (availableProviders.get(i) == currentProvider) {
                TabLayout.Tab tab = dialogBinding.providerTabs.getTabAt(i);
                if (tab != null) tab.select();
                loadModelsForProvider(currentProvider, modelAdapter, dialogBinding);
                break;
            }
        }

        dialogBinding.providerTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                AiProvider provider = (AiProvider) tab.getTag();
                if (provider != null) {
                    loadModelsForProvider(provider, modelAdapter, dialogBinding);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        dialog.show();
    }

    private void loadModelsForProvider(AiProvider provider, ModelSelectorAdapter adapter,
                                       DialogModelSelectorBinding dialogBinding) {
        List<ModelInfo> cached = preferences.getCachedModels(provider);
        if (cached != null && !cached.isEmpty()) {
            adapter.setModels(cached);
            dialogBinding.modelsList.setVisibility(View.VISIBLE);
            dialogBinding.emptyState.setVisibility(View.GONE);
        } else {
            adapter.setModels(new ArrayList<>());
            dialogBinding.modelsList.setVisibility(View.GONE);
            dialogBinding.emptyState.setVisibility(View.VISIBLE);
            dialogBinding.emptyText.setText("No models cached for " + provider.getDisplayName() + ".\nRefresh models in AI Settings.");
        }
    }

    @Override
    public void onStreamingChunk(String chunk) {
        chatAdapter.updateLastAssistantMessage(chunk);
        updateEmptyState();
        scrollToBottom();
    }

    @Override
    public void onAssistantMessage(ChatMessage assistantMessage) {
        conversationManager.saveMessage(conversationId, assistantMessage);
        updateConversationTimestamp();
        chatAdapter.replaceStreamingAssistantMessage(assistantMessage);
        updateEmptyState();
        scrollToBottom();
    }

    @Override
    public void onToolCallStarted(ToolCall toolCall) {
        chatAdapter.addToolCall(toolCall);
        scrollToBottom();
    }

    @Override
    public void onToolCallProgress(String toolCallId, String status, int progress, boolean indeterminate) {
        chatAdapter.updateToolCallProgress(toolCallId, status, progress, indeterminate);
        if (status != null && !status.isEmpty()) {
            binding.typingText.setText(status);
        }
    }

    @Override
    public void onToolCallCompleted(ToolCall toolCall, ToolResult result) {
        chatAdapter.updateToolCallResult(toolCall.getId(), result);
        if (toolCall != null) {
            String name = toolCall.getName();
            if ("create_project".equals(name)
                    || "duplicate_project".equals(name)
                    || "delete_project".equals(name)) {
                workspace = workspaceManager.getWorkspace(workspaceId);
            }
        }
        scrollToBottom();
    }

    @Override
    public void onToolMessage(ChatMessage toolMessage) {
        persistToolMessage(toolMessage);
    }

    @Override
    public void onResponseComplete(ChatMessage assistantMessage) {
        setAgentRunning(false);
    }

    @Override
    public void onCancelled() {
        setAgentRunning(false);
        binding.typingText.setText("Stopped");
        Toast.makeText(this, "Agent stopped", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onError(String error) {
        setAgentRunning(false);
        ChatMessage errorMessage = new ChatMessage(conversationId, "⚠️ " + error, null);
        conversationManager.saveMessage(conversationId, errorMessage);
        updateConversationTimestamp();
        chatAdapter.replaceStreamingAssistantMessage(errorMessage);
        updateEmptyState();
        scrollToBottom();
        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onThinking(String status) {
        binding.typingText.setText(status);
    }

    @Override
    public void onInstallArtifact(@NonNull String artifactPath) {
        try {
            File artifact = new File(artifactPath);
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", artifact);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open installer: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (agentExecutor != null) {
            agentExecutor.shutdown();
        }
    }
}
