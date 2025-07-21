package pro.sketchware.activities.ai.chat;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import pro.sketchware.activities.ai.chat.adapters.ChatAdapter;
import pro.sketchware.activities.ai.chat.models.ChatMessage;
import pro.sketchware.activities.ai.chat.models.QwenModel;
import pro.sketchware.activities.ai.chat.api.QwenApiClient;
import pro.sketchware.activities.ai.storage.ConversationStorage;
import pro.sketchware.activities.main.fragments.ai.models.Conversation;
import pro.sketchware.databinding.ActivityChatBinding;

public class ChatActivity extends AppCompatActivity {
    private ActivityChatBinding binding;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messages = new ArrayList<>();
    private QwenApiClient apiClient;
    private String conversationId;
    private String selectedModel = "qwen3-235b-a22b"; // Default model
    private boolean isTyping = false;
    private ConversationStorage conversationStorage;
    private String qwenChatId; // The actual chat ID from Qwen server
    private boolean isNewConversation = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        conversationId = getIntent().getStringExtra("conversation_id");
        if (conversationId == null) {
            conversationId = UUID.randomUUID().toString();
            isNewConversation = true;
        } else {
            isNewConversation = false;
        }

        String conversationTitle = getIntent().getStringExtra("conversation_title");
        if (!TextUtils.isEmpty(conversationTitle)) {
            setTitle(conversationTitle);
        } else {
            setTitle("New Chat");
        }

        conversationStorage = new ConversationStorage(this);
        setupToolbar();
        setupModelSelector();
        setupRecyclerView();
        setupInputArea();
        
        apiClient = new QwenApiClient(this);
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupModelSelector() {
        // Update the model selector text
        binding.modelSelectorText.setText(getModelDisplayName(selectedModel));
        
        // Set click listener for model selector button
        binding.modelSelectorButton.setOnClickListener(v -> showModelSelectionDialog());
    }

    private void showModelSelectionDialog() {
        List<QwenModel> models = getAvailableModels();
        String[] modelNames = new String[models.size()];
        int selectedIndex = 0;
        
        for (int i = 0; i < models.size(); i++) {
            modelNames[i] = models.get(i).getDisplayName();
            if (models.get(i).getId().equals(selectedModel)) {
                selectedIndex = i;
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select AI Model")
                .setSingleChoiceItems(modelNames, selectedIndex, (dialog, which) -> {
                    selectedModel = models.get(which).getId();
                    binding.modelSelectorText.setText(models.get(which).getDisplayName());
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupRecyclerView() {
        chatAdapter = new ChatAdapter(messages);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        
        binding.messagesRecyclerView.setLayoutManager(layoutManager);
        binding.messagesRecyclerView.setAdapter(chatAdapter);
    }

    private void setupInputArea() {
        binding.sendButton.setOnClickListener(v -> sendMessage());
        
        binding.messageInput.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });
    }

    private void sendMessage() {
        String messageText = binding.messageInput.getText().toString().trim();
        if (TextUtils.isEmpty(messageText) || isTyping) {
            return;
        }

        // Add user message
        ChatMessage userMessage = new ChatMessage(
            UUID.randomUUID().toString(),
            messageText,
            ChatMessage.TYPE_USER,
            System.currentTimeMillis()
        );
        messages.add(userMessage);
        chatAdapter.notifyItemInserted(messages.size() - 1);
        
        // Clear input
        binding.messageInput.setText("");
        
        // Scroll to bottom
        binding.messagesRecyclerView.scrollToPosition(messages.size() - 1);
        
        // Show typing indicator
        showTypingIndicator();
        
        // Send to API
        apiClient.sendMessage(conversationId, selectedModel, messageText, new QwenApiClient.ChatCallback() {
            @Override
            public void onResponse(String response) {
                runOnUiThread(() -> {
                    hideTypingIndicator();
                    
                    ChatMessage aiMessage = new ChatMessage(
                        UUID.randomUUID().toString(),
                        response,
                        ChatMessage.TYPE_AI,
                        System.currentTimeMillis()
                    );
                    messages.add(aiMessage);
                    chatAdapter.notifyItemInserted(messages.size() - 1);
                    binding.messagesRecyclerView.scrollToPosition(messages.size() - 1);
                    
                    // Save/update conversation
                    saveConversation(messageText, response);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    hideTypingIndicator();
                    
                    ChatMessage errorMessage = new ChatMessage(
                        UUID.randomUUID().toString(),
                        "Sorry, I encountered an error: " + error,
                        ChatMessage.TYPE_AI,
                        System.currentTimeMillis()
                    );
                    messages.add(errorMessage);
                    chatAdapter.notifyItemInserted(messages.size() - 1);
                    binding.messagesRecyclerView.scrollToPosition(messages.size() - 1);
                });
            }
        });
    }

    private void showTypingIndicator() {
        isTyping = true;
        binding.sendButton.setEnabled(false);
        binding.typingIndicator.setVisibility(View.VISIBLE);
    }

    private void hideTypingIndicator() {
        isTyping = false;
        binding.sendButton.setEnabled(true);
        binding.typingIndicator.setVisibility(View.GONE);
    }

    private List<QwenModel> getAvailableModels() {
        List<QwenModel> models = new ArrayList<>();
        models.add(new QwenModel("qwen3-235b-a22b", "Qwen3-235B-A22B", "The most powerful mixture-of-experts language model"));
        models.add(new QwenModel("qwen3-30b-a3b", "Qwen3-30B-A3B", "A compact and high-performance Mixture of Experts (MoE) model"));
        models.add(new QwenModel("qwen3-32b", "Qwen3-32B", "The most powerful dense model"));
        models.add(new QwenModel("qwen-max-latest", "Qwen2.5-Max", "The most powerful language model in the Qwen series"));
        models.add(new QwenModel("qwen-plus-2025-01-25", "Qwen2.5-Plus", "Capable of complex tasks"));
        models.add(new QwenModel("qwq-32b", "QwQ-32B", "Capable of thinking and reasoning"));
        models.add(new QwenModel("qwen-turbo-2025-02-11", "Qwen2.5-Turbo", "Fast and 1M-token context"));
        models.add(new QwenModel("qwen2.5-72b-instruct", "Qwen2.5-72B-Instruct", "Smart large language model"));
        return models;
    }

    private String getModelDisplayName(String modelId) {
        for (QwenModel model : getAvailableModels()) {
            if (model.getId().equals(modelId)) {
                return model.getDisplayName();
            }
        }
        return modelId;
    }

    private void saveConversation(String userMessage, String aiResponse) {
        // Generate title from first user message if this is a new conversation
        String title = isNewConversation ? generateConversationTitle(userMessage) : getTitle().toString();
        
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setTitle(title);
        conversation.setLastMessage(aiResponse);
        conversation.setLastMessageTime(new Date());
        conversation.setModel(getModelDisplayName(selectedModel));
        
        conversationStorage.saveConversation(conversation);
        
        // Update title if this was a new conversation
        if (isNewConversation) {
            setTitle(title);
            isNewConversation = false;
        }
    }

    private String generateConversationTitle(String firstMessage) {
        // Take first 50 characters or until first sentence ends
        String title = firstMessage.trim();
        if (title.length() > 50) {
            int endIndex = title.indexOf('.', 30);
            if (endIndex > 0 && endIndex < 50) {
                title = title.substring(0, endIndex);
            } else {
                title = title.substring(0, 50) + "...";
            }
        }
        return title;
    }
}