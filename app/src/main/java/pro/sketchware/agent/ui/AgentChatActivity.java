package pro.sketchware.agent.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.noties.markwon.Markwon;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.agent.AgentChatOrchestrator;
import pro.sketchware.agent.AgentModelService;
import pro.sketchware.agent.AgentProvider;
import pro.sketchware.agent.AgentRepository;
import pro.sketchware.databinding.ActivityAgentChatBinding;
import pro.sketchware.databinding.ItemAgentMessageBinding;

public class AgentChatActivity extends AppCompatActivity {

    public static final String EXTRA_WORKSPACE_ID = "workspace_id";
    public static final String EXTRA_CONVERSATION_ID = "conversation_id";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ActivityAgentChatBinding binding;
    private AgentRepository repository;
    private AgentModelService modelService;
    private AgentChatOrchestrator orchestrator;
    private AgentRepository.Workspace workspace;
    private AgentRepository.Conversation conversation;
    private MessageAdapter adapter;
    private ArrayList<AgentRepository.ModelInfo> currentModels = new ArrayList<>();
    private AgentProvider selectedProvider = AgentProvider.GEMINI;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAgentChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new AgentRepository();
        modelService = new AgentModelService(repository);
        orchestrator = new AgentChatOrchestrator(this, repository);

        workspace = repository.getWorkspace(getIntent().getStringExtra(EXTRA_WORKSPACE_ID));
        conversation = repository.getConversation(getIntent().getStringExtra(EXTRA_CONVERSATION_ID));
        if (workspace == null || conversation == null) {
            finish();
            return;
        }

        selectedProvider = AgentProvider.fromId(conversation.providerId);

        binding.topAppBar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.topAppBar.inflateMenu(R.menu.agent_chat_menu);
        binding.topAppBar.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_rename_conversation) {
                showRenameConversationDialog();
                return true;
            }
            if (itemId == R.id.action_delete_conversation) {
                confirmDeleteConversation();
                return true;
            }
            if (itemId == R.id.action_agent_settings) {
                startActivity(new Intent(this, AgentSettingsActivity.class));
                return true;
            }
            return false;
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(false);
        binding.messageList.setLayoutManager(layoutManager);
        adapter = new MessageAdapter();
        binding.messageList.setAdapter(adapter);

        binding.promptInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.sendButton.setEnabled(!TextUtils.isEmpty(s.toString().trim()));
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        binding.sendButton.setOnClickListener(v -> sendPrompt());
        binding.refreshModelsButton.setOnClickListener(v -> refreshSelectedProviderModels(true));
        setupProviderPicker();
        bindConversation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        AgentRepository.Conversation latest = repository.getConversation(conversation.id);
        if (latest != null) {
            conversation = latest;
        }
        selectedProvider = AgentProvider.fromId(conversation.providerId);
        bindConversation();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void setupProviderPicker() {
        String[] providerLabels = new String[AgentProvider.values().length];
        for (int i = 0; i < AgentProvider.values().length; i++) {
            providerLabels[i] = AgentProvider.values()[i].displayName;
        }
        ArrayAdapter<String> providerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, providerLabels);
        binding.providerInput.setAdapter(providerAdapter);
        binding.providerInput.setText(selectedProvider.displayName, false);
        binding.providerInput.setOnItemClickListener((parent, view, position, id) -> {
            selectedProvider = AgentProvider.values()[position];
            conversation.providerId = selectedProvider.id;
            conversation.modelId = null;
            conversation.updatedAt = System.currentTimeMillis();
            repository.saveConversation(conversation);
            bindModelsForProvider();
        });
        bindModelsForProvider();
    }

    private void bindModelsForProvider() {
        currentModels = new ArrayList<>(modelService.getCachedModels(selectedProvider));
        boolean conversationChanged = false;
        if (!TextUtils.equals(conversation.providerId, selectedProvider.id)) {
            conversation.providerId = selectedProvider.id;
            conversationChanged = true;
        }
        if (currentModels.isEmpty()) {
            if (!TextUtils.isEmpty(conversation.modelId)) {
                conversation.modelId = null;
                conversationChanged = true;
            }
            if (conversationChanged) {
                repository.saveConversation(conversation);
            }
            binding.modelInput.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                    new String[]{"No cached models"}));
            binding.modelInput.setText("No cached models", false);
            binding.modelInput.setOnItemClickListener(null);
            binding.modelStatus.setText("Set an API key and refresh models for " + selectedProvider.displayName + ".");
            return;
        }

        String[] labels = new String[currentModels.size()];
        int selectedIndex = 0;
        for (int i = 0; i < currentModels.size(); i++) {
            labels[i] = currentModels.get(i).getDisplayName();
            if (currentModels.get(i).id.equals(conversation.modelId)) {
                selectedIndex = i;
            }
        }
        if (TextUtils.isEmpty(conversation.modelId) || indexOfModel(conversation.modelId) == -1) {
            conversation.modelId = currentModels.get(selectedIndex).id;
            conversationChanged = true;
        }
        if (conversationChanged) {
            repository.saveConversation(conversation);
        }

        binding.modelInput.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));
        binding.modelInput.setText(labels[selectedIndex], false);
        binding.modelInput.setOnItemClickListener((parent, view, position, id) -> {
            conversation.modelId = currentModels.get(position).id;
            conversation.updatedAt = System.currentTimeMillis();
            repository.saveConversation(conversation);
            bindConversation();
        });
        long updatedAt = repository.getProviderState(selectedProvider).modelsUpdatedAt;
        binding.modelStatus.setText(updatedAt <= 0
                ? currentModels.size() + " cached models"
                : currentModels.size() + " cached models • " + android.text.format.DateUtils.getRelativeTimeSpanString(updatedAt));
    }

    private int indexOfModel(@NonNull String modelId) {
        for (int i = 0; i < currentModels.size(); i++) {
            if (modelId.equals(currentModels.get(i).id)) {
                return i;
            }
        }
        return -1;
    }

    private void bindConversation() {
        selectedProvider = AgentProvider.fromId(conversation.providerId);
        binding.topAppBar.setTitle(conversation.title);
        binding.providerInput.setText(selectedProvider.displayName, false);
        adapter.submit(conversation.messages);
        boolean empty = conversation.messages.isEmpty();
        binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.messageList.setVisibility(empty ? View.GONE : View.VISIBLE);
        bindModelsForProvider();
        scrollToBottom();
    }

    private void showRenameConversationDialog() {
        pro.sketchware.databinding.DialogInputLayoutBinding dialogBinding =
                pro.sketchware.databinding.DialogInputLayoutBinding.inflate(getLayoutInflater());
        dialogBinding.renameOccurrencesCheckBox.setVisibility(View.GONE);
        dialogBinding.textInputLayout.setHint("Conversation title");
        dialogBinding.inputText.setText(conversation.title);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Rename conversation")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Save", (dialog, which) -> {
                    conversation.title = AgentRepository.sanitizeTitle(Helper.getText(dialogBinding.inputText), "New conversation");
                    conversation.updatedAt = System.currentTimeMillis();
                    repository.saveConversation(conversation);
                    bindConversation();
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void confirmDeleteConversation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete conversation")
                .setMessage("Delete this conversation from the workspace history?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    repository.deleteConversation(conversation.id);
                    finish();
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void sendPrompt() {
        String prompt = Helper.getText(binding.promptInput).trim();
        if (prompt.isEmpty()) {
            return;
        }

        AgentRepository.ProviderState state = repository.getProviderState(selectedProvider);
        if (TextUtils.isEmpty(state.apiKey)) {
            showError(selectedProvider.displayName + " API key is missing. Open Agent Settings first.");
            return;
        }
        if (currentModels.isEmpty()) {
            showError("No cached models are available for " + selectedProvider.displayName + ". Refresh models first.");
            return;
        }

        binding.promptInput.setText("");
        setWorking(true);
        conversation.providerId = selectedProvider.id;
        repository.saveConversation(conversation);

        executor.execute(() -> {
            try {
                AgentRepository.Conversation updated = orchestrator.runTurn(workspace, conversation, prompt);
                runOnUiThread(() -> {
                    conversation = updated;
                    bindConversation();
                    setWorking(false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setWorking(false);
                    showError(e.getMessage());
                });
            }
        });
    }

    private void refreshSelectedProviderModels(boolean showDialogOnError) {
        AgentRepository.ProviderState state = repository.getProviderState(selectedProvider);
        if (TextUtils.isEmpty(state.apiKey)) {
            showError("Add an API key for " + selectedProvider.displayName + " first.");
            return;
        }

        binding.refreshModelsButton.setEnabled(false);
        binding.modelStatus.setText("Refreshing models…");
        executor.execute(() -> {
            try {
                modelService.refreshModels(selectedProvider, state.apiKey);
                runOnUiThread(() -> {
                    binding.refreshModelsButton.setEnabled(true);
                    bindModelsForProvider();
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    binding.refreshModelsButton.setEnabled(true);
                    bindModelsForProvider();
                    if (showDialogOnError) {
                        showError(e.getMessage());
                    }
                });
            }
        });
    }

    private void setWorking(boolean working) {
        binding.sendButton.setEnabled(!working && !TextUtils.isEmpty(Helper.getText(binding.promptInput).trim()));
        binding.sendProgress.setVisibility(working ? View.VISIBLE : View.GONE);
        binding.refreshModelsButton.setEnabled(!working);
        binding.providerInput.setEnabled(!working);
        binding.modelInput.setEnabled(!working);
        binding.promptInput.setEnabled(!working);
    }

    private void scrollToBottom() {
        if (adapter.getItemCount() > 0) {
            binding.messageList.post(() -> binding.messageList.smoothScrollToPosition(adapter.getItemCount() - 1));
        }
    }

    private void showError(String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Agent error")
                .setMessage(message)
                .setPositiveButton(R.string.common_word_ok, null)
                .show();
    }

    private static class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

        private final ArrayList<AgentRepository.Message> items = new ArrayList<>();
        private Markwon markwon;

        void submit(@NonNull List<AgentRepository.Message> messages) {
            items.clear();
            items.addAll(messages);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (markwon == null) {
                markwon = Markwon.create(parent.getContext());
            }
            return new ViewHolder(ItemAgentMessageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), markwon);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final ItemAgentMessageBinding binding;
            private final Markwon markwon;

            ViewHolder(ItemAgentMessageBinding binding, Markwon markwon) {
                super(binding.getRoot());
                this.binding = binding;
                this.markwon = markwon;
            }

            void bind(@NonNull AgentRepository.Message message) {
                boolean user = "user".equals(message.role);
                boolean tool = "tool".equals(message.role);
                ViewGroup.LayoutParams layoutParams = binding.messageCard.getLayoutParams();
                if (layoutParams instanceof ViewGroup.MarginLayoutParams marginLayoutParams) {
                    marginLayoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                }
                ((ViewGroup) binding.messageCard.getParent()).setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
                ((android.widget.LinearLayout) binding.container).setGravity(user ? Gravity.END : Gravity.START);

                binding.role.setText(tool
                        ? "Tool • " + (TextUtils.isEmpty(message.name) ? "operation" : message.name)
                        : user ? "You" : "Agent");
                if (tool) {
                    binding.messageCard.setCardBackgroundColor(MaterialColors.getColor(binding.messageCard,
                            com.google.android.material.R.attr.colorSurfaceContainerHighest));
                    binding.content.setTypeface(Typeface.MONOSPACE);
                    binding.content.setText(message.content);
                } else if (user) {
                    binding.messageCard.setCardBackgroundColor(MaterialColors.getColor(binding.messageCard,
                            com.google.android.material.R.attr.colorPrimaryContainer));
                    binding.content.setTypeface(Typeface.DEFAULT);
                    binding.content.setText(message.content);
                } else {
                    binding.messageCard.setCardBackgroundColor(MaterialColors.getColor(binding.messageCard,
                            com.google.android.material.R.attr.colorSecondaryContainer));
                    binding.content.setTypeface(Typeface.DEFAULT);
                    markwon.setMarkdown(binding.content, message.content);
                }
            }
        }
    }
}
