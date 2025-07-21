package pro.sketchware.activities.ai.chat;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import pro.sketchware.activities.ai.chat.adapters.ChatAdapter;
import pro.sketchware.activities.ai.chat.models.ChatMessage;
import pro.sketchware.activities.ai.chat.models.QwenModel;
import pro.sketchware.activities.ai.chat.api.QwenApiClient;
import pro.sketchware.databinding.ActivityChatBinding;

public class ChatActivity extends AppCompatActivity {
    private ActivityChatBinding binding;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messages = new ArrayList<>();
    private QwenApiClient apiClient;
    private String conversationId;
    private String selectedModel = "qwen3-235b-a22b"; // Default model
    private boolean isTyping = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        conversationId = getIntent().getStringExtra("conversation_id");
        if (conversationId == null) {
            conversationId = UUID.randomUUID().toString();
        }

        String conversationTitle = getIntent().getStringExtra("conversation_title");
        if (!TextUtils.isEmpty(conversationTitle)) {
            setTitle(conversationTitle);
        } else {
            setTitle("New Chat");
        }

        setupToolbar();
        setupModelSelector();
        setupRecyclerView();
        setupInputArea();
        
        apiClient = new QwenApiClient();
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
        List<QwenModel> models = getAvailableModels();
        List<String> modelNames = new ArrayList<>();
        for (QwenModel model : models) {
            modelNames.add(model.getDisplayName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, 
            android.R.layout.simple_dropdown_item_1line, 
            modelNames
        );
        
        binding.modelSelector.setAdapter(adapter);
        binding.modelSelector.setText(getModelDisplayName(selectedModel), false);
        
        binding.modelSelector.setOnItemClickListener((parent, view, position, id) -> {
            QwenModel selected = models.get(position);
            selectedModel = selected.getId();
        });
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
}