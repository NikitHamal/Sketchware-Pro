package com.stormx.agent;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.stormx.agent.ui.ChatAdapter;
import com.stormx.agent.model.ChatMessage;
import com.stormx.agent.api.QwenApiClient;
import com.stormx.agent.storage.ConversationStorage;
import com.stormx.agent.storage.MessageStorage;
import com.stormx.agent.databinding.ActivityChatBinding;

public class ChatActivity extends AppCompatActivity {

    private ActivityChatBinding binding;
    private final List<ChatMessage> messages = new ArrayList<>();
    private ChatAdapter chatAdapter;
    private QwenApiClient apiClient;
    private String conversationId;
    private ConversationStorage conversationStorage;
    private MessageStorage messageStorage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        conversationId = getIntent().getStringExtra("conversation_id");
        if (conversationId == null) {
            conversationId = UUID.randomUUID().toString();
        }

        conversationStorage = new ConversationStorage(this);
        messageStorage = new MessageStorage(this);
        apiClient = new QwenApiClient(this);

        setupRecyclerView();
        setupInputArea();
        loadMessages();
    }

    private void setupRecyclerView() {
        chatAdapter = new ChatAdapter(messages, this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.messagesRecyclerView.setLayoutManager(layoutManager);
        binding.messagesRecyclerView.setAdapter(chatAdapter);
    }

    private void setupInputArea() {
        binding.sendButton.setOnClickListener(v -> sendMessage());
    }

    private void loadMessages() {
        messages.clear();
        messages.addAll(messageStorage.getMessages(conversationId));
        chatAdapter.notifyDataSetChanged();
    }

    private void sendMessage() {
        String userText = binding.messageInput.getText().toString().trim();
        if (TextUtils.isEmpty(userText)) return;

        // Add user message locally
        ChatMessage userMessage = new ChatMessage(UUID.randomUUID().toString(), userText, ChatMessage.TYPE_USER, System.currentTimeMillis());
        messages.add(userMessage);
        chatAdapter.notifyItemInserted(messages.size() - 1);
        binding.messageInput.setText("");
        messageStorage.addMessage(conversationId, userMessage);

        // Placeholder AI message while waiting for response
        ChatMessage aiMessage = new ChatMessage(UUID.randomUUID().toString(), "", ChatMessage.TYPE_AI, System.currentTimeMillis());
        messages.add(aiMessage);
        chatAdapter.notifyItemInserted(messages.size() - 1);

        apiClient.sendMessage(conversationId, "qwen3-235b-a22b", userText, new QwenApiClient.ChatCallback() {
            private final StringBuilder streaming = new StringBuilder();

            @Override
            public void onResponse(String response) {
                runOnUiThread(() -> {
                    aiMessage.setContent(response);
                    chatAdapter.notifyItemChanged(messages.indexOf(aiMessage));
                    messageStorage.addMessage(conversationId, aiMessage);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> aiMessage.setContent("Error: " + error));
            }

            @Override
            public void onStreamingResponse(String partialResponse) {
                runOnUiThread(() -> {
                    streaming.append(partialResponse);
                    aiMessage.setContent(streaming.toString());
                    chatAdapter.notifyItemChanged(messages.indexOf(aiMessage));
                });
            }
        });
    }
}