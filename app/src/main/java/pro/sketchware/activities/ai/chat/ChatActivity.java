package pro.sketchware.activities.ai.chat;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import pro.sketchware.activities.ai.chat.adapters.ChatAdapter;
import pro.sketchware.activities.ai.chat.models.ChatMessage;
import pro.sketchware.activities.ai.chat.models.QwenModel;
import pro.sketchware.activities.ai.chat.api.QwenApiClient;
import pro.sketchware.activities.ai.chat.api.AgenticQwenApiClient;
import pro.sketchware.activities.ai.storage.ConversationStorage;
import pro.sketchware.activities.ai.storage.MessageStorage;
import pro.sketchware.activities.main.fragments.ai.models.Conversation;
import pro.sketchware.databinding.ActivityChatBinding;

import org.json.JSONException;
import org.json.JSONObject;

import a.a.a.lC;
import a.a.a.yB;

public class ChatActivity extends AppCompatActivity {
    private static final String TAG = "ChatActivity";
    private ActivityChatBinding binding;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messages = new ArrayList<>();
    private AgenticQwenApiClient apiClient;
    private String conversationId;
    private String selectedModel = "qwen3-235b-a22b"; // Default model
    private boolean isTyping = false;
    private ConversationStorage conversationStorage;
    private MessageStorage messageStorage;
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
        messageStorage = new MessageStorage(this);
        setupToolbar();
        setupModelSelector();
        setupRecyclerView();
        setupInputArea();
        loadMessages();
        
        apiClient = new AgenticQwenApiClient(this);
        
        // Handle auto_message from error analysis
        handleAutoMessage();
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
        chatAdapter = new ChatAdapter(messages, this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        
        binding.messagesRecyclerView.setLayoutManager(layoutManager);
        binding.messagesRecyclerView.setAdapter(chatAdapter);
        

    }

    private void loadMessages() {
        messages.clear();
        messages.addAll(messageStorage.getMessages(conversationId));
        if (chatAdapter != null) {
            chatAdapter.notifyDataSetChanged();
        }

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
        
        // Save user message immediately
        messageStorage.addMessage(conversationId, userMessage);
        
        // Clear input
        binding.messageInput.setText("");
        
        // Scroll to bottom
        binding.messagesRecyclerView.scrollToPosition(messages.size() - 1);
        
        // Add empty AI message for streaming
        ChatMessage aiMessage = new ChatMessage(
            UUID.randomUUID().toString(),
            "",
            ChatMessage.TYPE_AI,
            System.currentTimeMillis()
        );
        messages.add(aiMessage);
        chatAdapter.notifyItemInserted(messages.size() - 1);
        binding.messagesRecyclerView.scrollToPosition(messages.size() - 1);
        
        // Show typing indicator
        showTypingIndicator();
        
        // Send to API with agentic support
        apiClient.sendMessage(conversationId, selectedModel, messageText, new AgenticQwenApiClient.AgenticChatCallback() {
            private StringBuilder streamingContent = new StringBuilder();
            
            @Override
            public void onResponse(String response) {
                runOnUiThread(() -> {
                    hideTypingIndicator();
                    
                    // Update the last AI message with final response
                    if (!messages.isEmpty()) {
                        ChatMessage lastMessage = messages.get(messages.size() - 1);
                        if (lastMessage.getType() == ChatMessage.TYPE_AI) {
                            lastMessage.setContent(response);
                            chatAdapter.notifyItemChanged(messages.size() - 1);
                            
                            // Save final AI message
                            messageStorage.saveMessages(conversationId, messages);
                        }
                    }
                    
                    binding.messagesRecyclerView.scrollToPosition(messages.size() - 1);
                    
                    // Save/update conversation
                    saveConversation(messageText, response);
                });
            }
            
            @Override
            public void onStreamingResponse(String partialResponse) {
                runOnUiThread(() -> {
                    // Update the streaming content
                    streamingContent.append(partialResponse);
                    
                    // Update the last AI message with streaming content
                    if (!messages.isEmpty()) {
                        ChatMessage lastMessage = messages.get(messages.size() - 1);
                        if (lastMessage.getType() == ChatMessage.TYPE_AI) {
                            chatAdapter.updateLastMessage(streamingContent.toString());
                            binding.messagesRecyclerView.scrollToPosition(messages.size() - 1);
                        }
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    hideTypingIndicator();
                    
                    // Update the last AI message with error
                    if (!messages.isEmpty()) {
                        ChatMessage lastMessage = messages.get(messages.size() - 1);
                        if (lastMessage.getType() == ChatMessage.TYPE_AI) {
                            String errorMsg = "Sorry, I encountered an error: " + error;
                            lastMessage.setContent(errorMsg);
                            chatAdapter.notifyItemChanged(messages.size() - 1);
                            
                            // Save error message
                            messageStorage.saveMessages(conversationId, messages);
                        }
                    }
                    
                    binding.messagesRecyclerView.scrollToPosition(messages.size() - 1);
                });
            }

            @Override
            public void onActionExecuted(String actionResult, String projectId) {
                Log.d(TAG, "Action executed: " + actionResult);
                // Don't handle UI updates here - let onProjectCreated handle it for project creation
                // This is called first, then onProjectCreated is called for project-specific actions
            }

            @Override
            public void onProjectCreated(String projectId, String projectName) {
                runOnUiThread(() -> {
                    hideTypingIndicator();
                    
                    try {
                        // Get project details from the project system
                        HashMap<String, Object> projectData = lC.b(projectId);
                        if (projectData != null) {
                            // Create a new AI message with project data
                            ChatMessage projectMessage = new ChatMessage(
                                UUID.randomUUID().toString(),
                                "",  // We'll set the content below
                                ChatMessage.TYPE_AI,
                                System.currentTimeMillis()
                            );
                            
                            // Set project data for interactive display
                            projectMessage.setProjectId(projectId);
                            projectMessage.setProjectName(yB.c(projectData, "my_ws_name"));
                            projectMessage.setAppName(yB.c(projectData, "my_app_name"));
                            projectMessage.setPackageName(yB.c(projectData, "my_sc_pkg_name"));
                            
                            // Set a user-friendly message
                            String projectDisplayName = yB.c(projectData, "my_ws_name");
                            String message = "Perfect! I've successfully created your '" + projectDisplayName + "' project. You can click on the project card below to open it in the design editor.";
                            projectMessage.setContent(message);
                            
                            // Add the project message to the conversation
                            messages.add(projectMessage);
                            chatAdapter.notifyItemInserted(messages.size() - 1);
                            messageStorage.saveMessages(conversationId, messages);
                            binding.messagesRecyclerView.scrollToPosition(messages.size() - 1);
                            
                            // Save/update conversation with the project creation message
                            saveConversation(messageText, message);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error creating project message", e);
                        // Create a simple success message
                        ChatMessage projectMessage = new ChatMessage(
                            UUID.randomUUID().toString(),
                            "Project '" + projectName + "' created successfully! You can find it in your projects list.",
                            ChatMessage.TYPE_AI,
                            System.currentTimeMillis()
                        );
                        messages.add(projectMessage);
                        chatAdapter.notifyItemInserted(messages.size() - 1);
                        messageStorage.saveMessages(conversationId, messages);
                        saveConversation(messageText, projectMessage.getContent());
                    }
                });
            }
        });

    private void handleAutoMessage() {
        String autoMessage = getIntent().getStringExtra("auto_message");
        String contextType = getIntent().getStringExtra("context_type");
        
        if (autoMessage != null && !autoMessage.trim().isEmpty()) {
            // Add some delay to ensure UI is ready
            binding.messageInput.postDelayed(() -> {
                if ("error_analysis".equals(contextType)) {
                    // Set the title for error analysis
                    String projectId = getIntent().getStringExtra("project_id");
                    int errorCount = getIntent().getIntExtra("error_count", 0);
                    setTitle("AI Error Analysis - " + errorCount + " errors");
                    
                    // Add a visual indicator that this is error analysis
                    showErrorAnalysisHeader(projectId, errorCount);
                }
                
                // Set the message and send automatically
                binding.messageInput.setText(autoMessage);
                sendMessage();
            }, 500);
        }
    }
    
         private void showErrorAnalysisHeader(String projectId, int errorCount) {
         // This could be enhanced with a header view showing project context
         // For now, we'll just update the title
         setTitle("🔧 AI Error Fixer - " + errorCount + " errors");
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