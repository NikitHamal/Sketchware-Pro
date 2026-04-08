package pro.sketchware.agent.ui.chat;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import pro.sketchware.R;
import pro.sketchware.agent.data.AgentDatabase;
import pro.sketchware.agent.provider.AIProvider;
import pro.sketchware.agent.provider.ProviderRegistry;
import pro.sketchware.agent.tools.AgentToolRegistry;
import pro.sketchware.databinding.ActivityChatBinding;

public class ChatActivity extends BaseAppCompatActivity {

    private ActivityChatBinding binding;
    private AgentDatabase db;
    private String conversationId;
    private String workspaceId;
    private String workspaceName;
    private AgentDatabase.Conversation conversation;
    private final List<AgentDatabase.ChatMessage> messages = new ArrayList<>();
    private MessageAdapter adapter;
    private AgentToolRegistry toolRegistry;
    private final Gson gson = new Gson();

    private String currentProvider = "";
    private String currentModel = "";
    private boolean isStreaming = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = AgentDatabase.getInstance(this);
        conversationId = getIntent().getStringExtra("conversation_id");
        workspaceId = getIntent().getStringExtra("workspace_id");
        workspaceName = getIntent().getStringExtra("workspace_name");

        conversation = db.getConversation(conversationId);
        if (conversation == null) {
            finish();
            return;
        }

        toolRegistry = new AgentToolRegistry(this, workspaceId);

        setupToolbar();
        setupMessageList();
        setupInput();
        setupModelSelector();
        loadMessages();
    }

    private void setupToolbar() {
        binding.toolbar.setTitle(conversation.title);
        binding.toolbar.setSubtitle(workspaceName);
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupMessageList() {
        adapter = new MessageAdapter();
        LinearLayoutManager lm = (LinearLayoutManager) binding.messagesList.getLayoutManager();
        if (lm != null) {
            lm.setStackFromEnd(true);
        }
        binding.messagesList.setAdapter(adapter);
    }

    private void setupInput() {
        binding.btnSend.setOnClickListener(v -> {
            if (isStreaming) {
                cancelStreaming();
            } else {
                sendMessage();
            }
        });

        binding.inputMessage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                binding.btnSend.setEnabled(s.length() > 0 || isStreaming);
            }
        });
    }

    private void setupModelSelector() {
        currentProvider = conversation.provider;
        currentModel = conversation.model;
        updateModelDisplay();

        binding.chipProvider.setOnClickListener(v -> showProviderSelector());
        binding.chipModel.setOnClickListener(v -> showModelSelector());
        binding.btnRefreshModels.setOnClickListener(v -> refreshModels());
    }

    private void updateModelDisplay() {
        if (currentProvider.isEmpty()) {
            binding.chipProvider.setText("Select provider");
            binding.chipModel.setText("Select model");
        } else {
            AIProvider provider = ProviderRegistry.getProvider(currentProvider);
            binding.chipProvider.setText(provider != null ? provider.getDisplayName() : currentProvider);
            binding.chipModel.setText(currentModel.isEmpty() ? "Select model" : currentModel);
        }
    }

    private void showProviderSelector() {
        List<AIProvider> providers = ProviderRegistry.getAllProviders();
        String[] names = new String[providers.size()];
        for (int i = 0; i < providers.size(); i++) {
            String apiKey = db.getApiKey(providers.get(i).getId());
            names[i] = providers.get(i).getDisplayName() + (apiKey.isEmpty() ? " (no API key)" : "");
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Provider")
                .setItems(names, (dialog, which) -> {
                    AIProvider selected = providers.get(which);
                    String apiKey = db.getApiKey(selected.getId());
                    if (apiKey.isEmpty()) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("API Key Required")
                                .setMessage("Please set your " + selected.getDisplayName() + " API key in Agent Settings first.")
                                .setPositiveButton("OK", null)
                                .show();
                        return;
                    }
                    currentProvider = selected.getId();
                    currentModel = "";
                    updateModelDisplay();
                    saveConversationSettings();

                    // Auto-load models if cached
                    List<AgentDatabase.ModelInfo> cached = db.getCachedModels(currentProvider);
                    if (cached.isEmpty()) {
                        refreshModels();
                    }
                })
                .show();
    }

    private void showModelSelector() {
        if (currentProvider.isEmpty()) {
            showProviderSelector();
            return;
        }

        List<AgentDatabase.ModelInfo> models = db.getCachedModels(currentProvider);
        if (models.isEmpty()) {
            refreshModels();
            return;
        }

        String[] names = new String[models.size()];
        for (int i = 0; i < models.size(); i++) {
            AgentDatabase.ModelInfo m = models.get(i);
            names[i] = m.name + (m.contextLength > 0 ? " (" + (m.contextLength / 1000) + "k)" : "");
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Model")
                .setItems(names, (dialog, which) -> {
                    currentModel = models.get(which).id;
                    updateModelDisplay();
                    saveConversationSettings();
                })
                .show();
    }

    private void refreshModels() {
        if (currentProvider.isEmpty()) {
            showProviderSelector();
            return;
        }

        AIProvider provider = ProviderRegistry.getProvider(currentProvider);
        String apiKey = db.getApiKey(currentProvider);
        if (provider == null || apiKey.isEmpty()) return;

        binding.btnRefreshModels.setEnabled(false);
        binding.btnRefreshModels.setAlpha(0.5f);

        provider.fetchModels(apiKey, models -> {
            binding.btnRefreshModels.setEnabled(true);
            binding.btnRefreshModels.setAlpha(1f);

            if (!models.isEmpty()) {
                db.cacheModels(currentProvider, models);
                if (currentModel.isEmpty() && !models.isEmpty()) {
                    showModelSelector();
                }
            } else {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("No Models Found")
                        .setMessage("Could not fetch models. Please check your API key.")
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }

    private void saveConversationSettings() {
        conversation.provider = currentProvider;
        conversation.model = currentModel;
        db.updateConversation(conversation);
    }

    private void loadMessages() {
        messages.clear();
        messages.addAll(db.getMessagesForConversation(conversationId));
        adapter.notifyDataSetChanged();
        scrollToBottom();
    }

    private void sendMessage() {
        String text = binding.inputMessage.getText() != null ? binding.inputMessage.getText().toString().trim() : "";
        if (text.isEmpty()) return;

        if (currentProvider.isEmpty() || currentModel.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Select Provider & Model")
                    .setMessage("Please select a provider and model before sending messages.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        binding.inputMessage.setText("");

        // Add user message
        AgentDatabase.ChatMessage userMsg = new AgentDatabase.ChatMessage();
        userMsg.id = UUID.randomUUID().toString();
        userMsg.conversationId = conversationId;
        userMsg.role = "user";
        userMsg.content = text;
        userMsg.status = "complete";
        userMsg.createdAt = System.currentTimeMillis();
        db.insertMessage(userMsg);
        messages.add(userMsg);
        adapter.notifyItemInserted(messages.size() - 1);
        scrollToBottom();

        // Start AI response
        startStreaming();
    }

    private void startStreaming() {
        AIProvider provider = ProviderRegistry.getProvider(currentProvider);
        String apiKey = db.getApiKey(currentProvider);
        if (provider == null || apiKey.isEmpty()) return;

        isStreaming = true;
        binding.btnSend.setImageResource(R.drawable.ic_stop);
        binding.inputMessage.setEnabled(false);

        // Add placeholder assistant message
        AgentDatabase.ChatMessage assistantMsg = new AgentDatabase.ChatMessage();
        assistantMsg.id = UUID.randomUUID().toString();
        assistantMsg.conversationId = conversationId;
        assistantMsg.role = "assistant";
        assistantMsg.content = "";
        assistantMsg.status = "streaming";
        assistantMsg.createdAt = System.currentTimeMillis();
        messages.add(assistantMsg);
        adapter.notifyItemInserted(messages.size() - 1);
        scrollToBottom();

        // Build message payloads
        List<AIProvider.MessagePayload> payloads = buildPayloads();

        // Get tool definitions
        List<AIProvider.ToolDefinition> tools = toolRegistry.getToolDefinitions();

        final int assistantIndex = messages.size() - 1;

        provider.sendMessage(apiKey, currentModel, payloads, tools, new AIProvider.StreamCallback() {
            @Override
            public void onToken(String token) {
                if (assistantIndex < messages.size()) {
                    AgentDatabase.ChatMessage msg = messages.get(assistantIndex);
                    msg.content += token;
                    adapter.notifyItemChanged(assistantIndex);
                    scrollToBottom();
                }
            }

            @Override
            public void onToolCall(List<AIProvider.ToolCall> toolCalls) {
                handleToolCalls(assistantMsg, toolCalls, assistantIndex);
            }

            @Override
            public void onComplete(String fullResponse) {
                assistantMsg.content = fullResponse;
                assistantMsg.status = "complete";
                db.insertMessage(assistantMsg);
                adapter.notifyItemChanged(assistantIndex);
                finishStreaming();
            }

            @Override
            public void onError(String error) {
                assistantMsg.content = "Error: " + error;
                assistantMsg.status = "error";
                db.insertMessage(assistantMsg);
                adapter.notifyItemChanged(assistantIndex);
                finishStreaming();
            }
        });
    }

    private void handleToolCalls(AgentDatabase.ChatMessage assistantMsg, List<AIProvider.ToolCall> toolCalls, int assistantIndex) {
        // Save the assistant message with tool calls
        JsonArray toolCallsJson = new JsonArray();
        for (AIProvider.ToolCall tc : toolCalls) {
            JsonObject tcObj = new JsonObject();
            tcObj.addProperty("id", tc.id);
            tcObj.addProperty("name", tc.name);
            tcObj.addProperty("arguments", tc.arguments);
            toolCallsJson.add(tcObj);
        }
        assistantMsg.toolCalls = toolCallsJson.toString();
        assistantMsg.status = "tool_calling";
        db.insertMessage(assistantMsg);
        adapter.notifyItemChanged(assistantIndex);

        // Execute each tool call
        for (AIProvider.ToolCall tc : toolCalls) {
            // Show tool execution in UI
            AgentDatabase.ChatMessage toolMsg = new AgentDatabase.ChatMessage();
            toolMsg.id = UUID.randomUUID().toString();
            toolMsg.conversationId = conversationId;
            toolMsg.role = "tool";
            toolMsg.content = "Executing: " + tc.name;
            toolMsg.toolCalls = tc.id;
            toolMsg.status = "streaming";
            toolMsg.createdAt = System.currentTimeMillis();
            messages.add(toolMsg);
            adapter.notifyItemInserted(messages.size() - 1);
            scrollToBottom();

            // Execute tool
            String result = toolRegistry.executeTool(tc.name, tc.arguments);

            // Update tool message with result
            int toolIndex = messages.size() - 1;
            toolMsg.content = result;
            toolMsg.toolResults = result;
            toolMsg.status = "complete";
            db.insertMessage(toolMsg);
            adapter.notifyItemChanged(toolIndex);
        }

        // Continue the conversation with tool results
        assistantMsg.status = "complete";
        db.updateMessage(assistantMsg);

        continueAfterToolCalls();
    }

    private void continueAfterToolCalls() {
        AIProvider provider = ProviderRegistry.getProvider(currentProvider);
        String apiKey = db.getApiKey(currentProvider);
        if (provider == null || apiKey.isEmpty()) {
            finishStreaming();
            return;
        }

        // Add new assistant placeholder
        AgentDatabase.ChatMessage nextMsg = new AgentDatabase.ChatMessage();
        nextMsg.id = UUID.randomUUID().toString();
        nextMsg.conversationId = conversationId;
        nextMsg.role = "assistant";
        nextMsg.content = "";
        nextMsg.status = "streaming";
        nextMsg.createdAt = System.currentTimeMillis();
        messages.add(nextMsg);
        int nextIndex = messages.size() - 1;
        adapter.notifyItemInserted(nextIndex);
        scrollToBottom();

        List<AIProvider.MessagePayload> payloads = buildPayloads();
        List<AIProvider.ToolDefinition> tools = toolRegistry.getToolDefinitions();

        provider.sendMessage(apiKey, currentModel, payloads, tools, new AIProvider.StreamCallback() {
            @Override
            public void onToken(String token) {
                if (nextIndex < messages.size()) {
                    messages.get(nextIndex).content += token;
                    adapter.notifyItemChanged(nextIndex);
                    scrollToBottom();
                }
            }

            @Override
            public void onToolCall(List<AIProvider.ToolCall> toolCalls) {
                handleToolCalls(nextMsg, toolCalls, nextIndex);
            }

            @Override
            public void onComplete(String fullResponse) {
                nextMsg.content = fullResponse;
                nextMsg.status = "complete";
                db.insertMessage(nextMsg);
                adapter.notifyItemChanged(nextIndex);
                finishStreaming();
            }

            @Override
            public void onError(String error) {
                nextMsg.content = "Error: " + error;
                nextMsg.status = "error";
                db.insertMessage(nextMsg);
                adapter.notifyItemChanged(nextIndex);
                finishStreaming();
            }
        });
    }

    private List<AIProvider.MessagePayload> buildPayloads() {
        List<AIProvider.MessagePayload> payloads = new ArrayList<>();

        // System prompt
        String systemPrompt = toolRegistry.getSystemPrompt();
        payloads.add(new AIProvider.MessagePayload("system", systemPrompt));

        // Conversation history
        for (AgentDatabase.ChatMessage msg : messages) {
            if ("streaming".equals(msg.status)) continue;

            if ("user".equals(msg.role)) {
                payloads.add(new AIProvider.MessagePayload("user", msg.content));
            } else if ("assistant".equals(msg.role)) {
                AIProvider.MessagePayload payload = new AIProvider.MessagePayload("assistant", msg.content);
                if (msg.toolCalls != null && !msg.toolCalls.isEmpty()) {
                    payload.toolCalls = msg.toolCalls;
                }
                payloads.add(payload);
            } else if ("tool".equals(msg.role)) {
                String toolCallId = msg.toolCalls; // We stored tool call id in toolCalls field
                String result = msg.toolResults != null && !msg.toolResults.isEmpty() ? msg.toolResults : msg.content;
                payloads.add(new AIProvider.MessagePayload("tool", result, toolCallId));
            }
        }

        return payloads;
    }

    private void cancelStreaming() {
        if (currentProvider != null) {
            AIProvider provider = ProviderRegistry.getProvider(currentProvider);
            if (provider != null) {
                provider.cancelRequest();
            }
        }
        finishStreaming();
    }

    private void finishStreaming() {
        isStreaming = false;
        binding.btnSend.setImageResource(R.drawable.ic_send);
        binding.inputMessage.setEnabled(true);

        // Update conversation timestamp
        conversation.updatedAt = System.currentTimeMillis();
        db.updateConversation(conversation);
    }

    private void scrollToBottom() {
        if (messages.size() > 0) {
            binding.messagesList.smoothScrollToPosition(messages.size() - 1);
        }
    }

    // Message Adapter
    private class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            AgentDatabase.ChatMessage msg = messages.get(position);

            // Set alignment
            LinearLayout root = (LinearLayout) holder.itemView;
            boolean isUser = "user".equals(msg.role);
            boolean isTool = "tool".equals(msg.role);

            android.util.TypedValue tv = new android.util.TypedValue();
            if (isUser) {
                root.setGravity(Gravity.END);
                holder.role.setVisibility(View.GONE);
                getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, tv, true);
                holder.card.setCardBackgroundColor(tv.data);
            } else if (isTool) {
                root.setGravity(Gravity.START);
                holder.role.setVisibility(View.VISIBLE);
                holder.role.setText("Tool");
                getTheme().resolveAttribute(com.google.android.material.R.attr.colorTertiaryContainer, tv, true);
                holder.card.setCardBackgroundColor(tv.data);
            } else {
                root.setGravity(Gravity.START);
                holder.role.setVisibility(View.VISIBLE);
                holder.role.setText("Agent");
                getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerHigh, tv, true);
                holder.card.setCardBackgroundColor(tv.data);
            }

            // Content
            String content = msg.content;
            if (isTool && msg.toolResults != null && !msg.toolResults.isEmpty()) {
                // Show a truncated version of tool results
                try {
                    com.google.gson.JsonObject result = com.google.gson.JsonParser.parseString(msg.toolResults).getAsJsonObject();
                    if (result.has("error")) {
                        content = "Error: " + result.get("error").getAsString();
                    } else if (result.has("message")) {
                        content = result.get("message").getAsString();
                    } else if (result.has("success")) {
                        content = "Done";
                        if (result.has("sc_id")) content += " (Project: " + result.get("sc_id").getAsString() + ")";
                    } else {
                        String raw = msg.toolResults;
                        content = raw.length() > 200 ? raw.substring(0, 200) + "..." : raw;
                    }
                } catch (Exception e) {
                    content = msg.toolResults.length() > 200 ? msg.toolResults.substring(0, 200) + "..." : msg.toolResults;
                }
            }

            holder.content.setText(content.isEmpty() && "streaming".equals(msg.status) ? "Thinking..." : content);

            // Tool call indicator
            if (msg.toolCalls != null && !msg.toolCalls.isEmpty() && "assistant".equals(msg.role)) {
                holder.toolCallContainer.setVisibility(View.VISIBLE);
                try {
                    com.google.gson.JsonArray tcs = com.google.gson.JsonParser.parseString(msg.toolCalls).getAsJsonArray();
                    StringBuilder toolNames = new StringBuilder();
                    for (int i = 0; i < tcs.size(); i++) {
                        if (i > 0) toolNames.append(", ");
                        toolNames.append(tcs.get(i).getAsJsonObject().get("name").getAsString());
                    }
                    holder.toolCallText.setText("Using: " + toolNames);
                } catch (Exception e) {
                    holder.toolCallContainer.setVisibility(View.GONE);
                }
                boolean calling = "tool_calling".equals(msg.status);
                holder.toolProgress.setVisibility(calling ? View.VISIBLE : View.GONE);
                holder.toolIcon.setVisibility(calling ? View.GONE : View.VISIBLE);
            } else {
                holder.toolCallContainer.setVisibility(View.GONE);
            }

            // Status indicator
            boolean streaming = "streaming".equals(msg.status);
            holder.statusContainer.setVisibility(streaming ? View.VISIBLE : View.GONE);
            holder.statusText.setText(streaming ? "Thinking..." : "");
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView role, content, toolCallText, statusText;
            MaterialCardView card;
            LinearLayout toolCallContainer, statusContainer;
            CircularProgressIndicator toolProgress, streamingIndicator;
            ImageView toolIcon;

            VH(View v) {
                super(v);
                role = v.findViewById(R.id.msg_role);
                content = v.findViewById(R.id.msg_content);
                card = v.findViewById(R.id.msg_card);
                toolCallContainer = v.findViewById(R.id.tool_call_container);
                toolCallText = v.findViewById(R.id.tool_call_text);
                toolProgress = v.findViewById(R.id.tool_progress);
                toolIcon = v.findViewById(R.id.tool_icon);
                statusContainer = v.findViewById(R.id.status_container);
                streamingIndicator = v.findViewById(R.id.streaming_indicator);
                statusText = v.findViewById(R.id.status_text);
            }
        }
    }
}
