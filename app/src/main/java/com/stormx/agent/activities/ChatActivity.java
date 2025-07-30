package com.stormx.agent.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.stormx.agent.R;
import com.stormx.agent.adapters.ChatAdapter;
import com.stormx.agent.api.QwenApiClient;
import com.stormx.agent.config.ApiConfig;
import com.stormx.agent.databinding.ActivityChatBinding;
import com.stormx.agent.models.ChatMessage;
import com.stormx.agent.models.Conversation;
import com.stormx.agent.storage.ConversationStorage;
import com.stormx.agent.storage.MessageStorage;

public class ChatActivity extends AppCompatActivity {
    private static final String TAG = "ChatActivity";
    private ActivityChatBinding binding;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messages = new ArrayList<>();
    private QwenApiClient apiClient;
    private String conversationId;
    private String selectedModel = "qwen3-235b-a22b"; // Default model
    private boolean isTyping = false;
    private ConversationStorage conversationStorage;
    private MessageStorage messageStorage;
    private boolean isNewConversation = true;
    private ActivityResultLauncher<Intent> filePickerLauncher;

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

        initializeComponents();
        setupToolbar();
        setupRecyclerView();
        setupInputArea();
        setupFilePickerLauncher();
        loadMessages();
    }

    private void initializeComponents() {
        conversationStorage = new ConversationStorage(this);
        messageStorage = new MessageStorage(this);
        apiClient = new QwenApiClient(this);
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.chat);
        }
    }

    private void setupRecyclerView() {
        chatAdapter = new ChatAdapter(messages, this);
        binding.messagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.messagesRecyclerView.setAdapter(chatAdapter);
    }

    private void setupInputArea() {
        binding.messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.sendButton.setEnabled(!TextUtils.isEmpty(s.toString().trim()));
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.sendButton.setOnClickListener(v -> sendMessage());
        binding.attachButton.setOnClickListener(v -> openFilePicker());
    }

    private void setupFilePickerLauncher() {
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri fileUri = result.getData().getData();
                        if (fileUri != null) {
                            // Handle file attachment (simplified for now)
                            Toast.makeText(this, "File attached", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        filePickerLauncher.launch(intent);
    }

    private void loadMessages() {
        if (!isNewConversation) {
            messages = messageStorage.getMessages(conversationId);
            chatAdapter.notifyDataSetChanged();
            if (!messages.isEmpty()) {
                binding.messagesRecyclerView.scrollToPosition(messages.size() - 1);
            }
        }
    }

    private void sendMessage() {
        String messageText = binding.messageInput.getText().toString().trim();
        if (TextUtils.isEmpty(messageText)) {
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
        binding.messagesRecyclerView.scrollToPosition(messages.size() - 1);

        // Clear input
        binding.messageInput.setText("");
        binding.sendButton.setEnabled(false);

        // Show typing indicator
        showTypingIndicator();

        // Send to API
        apiClient.sendMessage(conversationId, selectedModel, messageText, new QwenApiClient.ChatCallback() {
            @Override
            public void onResponse(String response) {
                runOnUiThread(() -> {
                    hideTypingIndicator();
                    
                    // Add AI response
                    ChatMessage aiMessage = new ChatMessage(
                            UUID.randomUUID().toString(),
                            response,
                            ChatMessage.TYPE_AI,
                            System.currentTimeMillis()
                    );
                    messages.add(aiMessage);
                    chatAdapter.notifyItemInserted(messages.size() - 1);
                    binding.messagesRecyclerView.scrollToPosition(messages.size() - 1);

                    // Save conversation and messages
                    saveConversation(messageText, response);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    hideTypingIndicator();
                    Toast.makeText(ChatActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showTypingIndicator() {
        isTyping = true;
        binding.typingIndicator.setVisibility(View.VISIBLE);
        binding.messagesRecyclerView.scrollToPosition(messages.size() - 1);
    }

    private void hideTypingIndicator() {
        isTyping = false;
        binding.typingIndicator.setVisibility(View.GONE);
    }

    private void saveConversation(String userMessage, String aiResponse) {
        // Save messages
        messageStorage.saveMessages(conversationId, messages);

        // Save conversation metadata
        String title = generateConversationTitle(userMessage);
        Conversation conversation = new Conversation(
                conversationId,
                title,
                aiResponse,
                new Date(),
                selectedModel
        );
        conversationStorage.saveConversation(conversation);
    }

    private String generateConversationTitle(String firstMessage) {
        if (firstMessage.length() > 50) {
            return firstMessage.substring(0, 47) + "...";
        }
        return firstMessage;
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}