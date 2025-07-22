package pro.sketchware.activities.ai.chat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.json.JSONArray;

import pro.sketchware.activities.ai.chat.adapters.ChatAdapter;
import pro.sketchware.activities.ai.chat.adapters.ProjectSelectorAdapter;
import pro.sketchware.activities.ai.chat.models.ChatMessage;
import pro.sketchware.activities.ai.chat.models.QwenModel;
import pro.sketchware.activities.ai.chat.api.QwenApiClient;
import pro.sketchware.activities.ai.chat.api.AgenticQwenApiClient;
import pro.sketchware.activities.ai.storage.ConversationStorage;
import pro.sketchware.activities.ai.storage.MessageStorage;
import pro.sketchware.activities.main.fragments.ai.models.Conversation;
import pro.sketchware.databinding.ActivityChatBinding;
import pro.sketchware.R;

import org.json.JSONException;
import org.json.JSONObject;

import a.a.a.lC;
import a.a.a.yB;
import pro.sketchware.activities.ai.chat.FileUploadManager;

import pro.sketchware.activities.ai.chat.views.FixProposalView;
import pro.sketchware.activities.ai.chat.views.ProjectItemView;

public class ChatActivity extends AppCompatActivity {
    private static final String TAG = "ChatActivity";
    private ActivityChatBinding binding;
    private boolean isProcessingProposal = false;
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
    private FileUploadManager fileUploadManager;
    private List<ChatMessage.AttachedFile> pendingFiles = new ArrayList<>();
    private boolean thinkingEnabled = false;
    private boolean webSearchEnabled = false;
    private ActivityResultLauncher<Intent> filePickerLauncher;
    
    // Project selector functionality
    private ProjectSelectorAdapter projectSelectorAdapter;
    private List<HashMap<String, Object>> availableProjects = new ArrayList<>();
    private String selectedProjectId = null;
    private boolean isShowingProjectSelector = false;

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
        fileUploadManager = new FileUploadManager(this);
        // Set auth token for file uploads - this should come from your authentication system
        fileUploadManager.setAuthToken("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6IjhiYjQ1NjVmLTk3NjUtNDQwNi04OWQ5LTI3NmExMTIxMjBkNiIsImxhc3RfcGFzc3dvcmRfY2hhbmdlIjoxNzUwNjYwODczLCJleHAiOjE3NTU0MTY4MjJ9.jmyaxu5mrr1M1rvtRtpGi2DKyp6RM8xRZ1nEx-rHRgQ");
        
        setupFilePickerLauncher();
        setupToolbar();
        setupModelSelector();
        setupRecyclerView();
        setupProjectSelector();
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
        
        // Set up proposal action listener
        chatAdapter.setOnProposalActionListener(new ChatAdapter.OnProposalActionListener() {
            @Override
            public void onAcceptProposal(ChatMessage message) {
                handleProposalAccepted(message);
            }

            @Override
            public void onDiscardProposal(ChatMessage message) {
                handleProposalDiscarded(message);
            }
        });
        

    }

    private void loadMessages() {
        messages.clear();
        messages.addAll(messageStorage.getMessages(conversationId));
        if (chatAdapter != null) {
            chatAdapter.notifyDataSetChanged();
        }

    }

    private void setupFilePickerLauncher() {
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri fileUri = result.getData().getData();
                        if (fileUri != null) {
                            uploadFile(fileUri);
                        }
                    }
                }
        );
    }

    private void setupProjectSelector() {
        // Load available projects
        loadAvailableProjects();
        
        // Setup project selector RecyclerView
        projectSelectorAdapter = new ProjectSelectorAdapter(availableProjects, this::onProjectSelected);
        binding.projectSelectorList.setLayoutManager(new LinearLayoutManager(this));
        binding.projectSelectorList.setAdapter(projectSelectorAdapter);
    }
    
    private void loadAvailableProjects() {
        availableProjects.clear();
        availableProjects.addAll(lC.a()); // Load all projects
    }
    
    private void onProjectSelected(String projectId, String appName) {
        selectedProjectId = projectId;
        hideProjectSelector();
        
        // Insert @projectId into the text input
        String currentText = binding.messageInput.getText().toString();
        int cursorPosition = binding.messageInput.getSelectionStart();
        
        // Find the @ symbol position
        int atPosition = currentText.lastIndexOf('@', cursorPosition - 1);
        if (atPosition >= 0) {
            // Replace @... with @projectId
            String beforeAt = currentText.substring(0, atPosition);
            String afterCursor = currentText.substring(cursorPosition);
            String newText = beforeAt + "@" + projectId + " " + afterCursor;
            
            binding.messageInput.setText(newText);
            
            // Apply grey color to the @projectId part
            int endPosition = atPosition + projectId.length() + 1;
            applyProjectIdStyle(atPosition, endPosition);
            
            // Set cursor position after the inserted text
            binding.messageInput.setSelection(endPosition + 1);
        }
    }
    
    private void applyProjectIdStyle(int start, int end) {
        SpannableString spannableString = new SpannableString(binding.messageInput.getText());
        ForegroundColorSpan greySpan = new ForegroundColorSpan(
                ContextCompat.getColor(this, android.R.color.darker_gray));
        spannableString.setSpan(greySpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        binding.messageInput.setText(spannableString);
    }
    
    private void showProjectSelector() {
        if (!isShowingProjectSelector && !availableProjects.isEmpty()) {
            isShowingProjectSelector = true;
            binding.projectSelectorPopup.setVisibility(View.VISIBLE);
        }
    }
    
    private void hideProjectSelector() {
        if (isShowingProjectSelector) {
            isShowingProjectSelector = false;
            binding.projectSelectorPopup.setVisibility(View.GONE);
        }
    }

    private void setupInputArea() {
        binding.sendButton.setOnClickListener(v -> sendMessage());
        binding.chatOptionsButton.setOnClickListener(v -> showChatOptionsBottomSheet());
        
        binding.messageInput.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });
        
        // Add text watcher for @ symbol detection
        binding.messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (count > 0 && s.charAt(start + count - 1) == '@') {
                    // User just typed @, show project selector
                    loadAvailableProjects(); // Refresh project list
                    showProjectSelector();
                } else if (isShowingProjectSelector && (s.length() == 0 || 
                    (start < s.length() && s.charAt(start) != '@'))) {
                    // Hide project selector if @ is deleted or text becomes empty
                    hideProjectSelector();
                }
            }
            
            @Override
            public void afterTextChanged(Editable s) {
                // Check if we should hide project selector
                String text = s.toString();
                int cursorPosition = binding.messageInput.getSelectionStart();
                
                if (isShowingProjectSelector) {
                    // Find if there's still an @ near cursor
                    boolean hasAtNearCursor = false;
                    for (int i = Math.max(0, cursorPosition - 20); i < Math.min(text.length(), cursorPosition + 1); i++) {
                        if (text.charAt(i) == '@') {
                            hasAtNearCursor = true;
                            break;
                        }
                    }
                    
                    if (!hasAtNearCursor) {
                        hideProjectSelector();
                    }
                }
            }
        });
        
        updateAttachedFilesUI();
    }
    
    private void showChatOptionsBottomSheet() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View bottomSheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_chat_options, null);
        bottomSheet.setContentView(bottomSheetView);
        
        // File upload option
        bottomSheetView.findViewById(R.id.file_upload_option).setOnClickListener(v -> {
            bottomSheet.dismiss();
            openFilePicker();
        });
        
        // Thinking toggle
        Chip thinkingChip = bottomSheetView.findViewById(R.id.thinking_toggle_chip);
        thinkingChip.setChecked(thinkingEnabled);
        thinkingChip.setText(thinkingEnabled ? "ON" : "OFF");
        thinkingChip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            thinkingEnabled = isChecked;
            thinkingChip.setText(isChecked ? "ON" : "OFF");
        });
        
        // Web search toggle
        Chip webSearchChip = bottomSheetView.findViewById(R.id.web_search_toggle_chip);
        webSearchChip.setChecked(webSearchEnabled);
        webSearchChip.setText(webSearchEnabled ? "ON" : "OFF");
        webSearchChip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            webSearchEnabled = isChecked;
            webSearchChip.setText(isChecked ? "ON" : "OFF");
        });
        
        bottomSheet.show();
    }
    
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        filePickerLauncher.launch(Intent.createChooser(intent, "Select File"));
    }
    
    private void uploadFile(Uri fileUri) {
        fileUploadManager.uploadFile(fileUri, new FileUploadManager.FileUploadCallback() {
            @Override
            public void onUploadStart(String fileName) {
                Toast.makeText(ChatActivity.this, "Uploading " + fileName + "...", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onUploadProgress(String fileName, int progress) {
                // Could show progress dialog here
            }

            @Override
            public void onUploadSuccess(ChatMessage.AttachedFile attachedFile) {
                pendingFiles.add(attachedFile);
                updateAttachedFilesUI();
                Toast.makeText(ChatActivity.this, "File uploaded successfully", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onUploadError(String fileName, String error) {
                Toast.makeText(ChatActivity.this, "Upload failed: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }
    
    private void updateAttachedFilesUI() {
        if (pendingFiles.isEmpty()) {
            binding.attachedFilesContainer.setVisibility(View.GONE);
        } else {
            binding.attachedFilesContainer.setVisibility(View.VISIBLE);
            binding.attachedFilesContainer.removeAllViews();
            
            for (ChatMessage.AttachedFile file : pendingFiles) {
                View fileView = getLayoutInflater().inflate(R.layout.item_attached_file, binding.attachedFilesContainer, false);
                
                android.widget.TextView fileName = fileView.findViewById(R.id.file_name);
                android.widget.TextView fileSize = fileView.findViewById(R.id.file_size);
                android.widget.ImageView fileRemove = fileView.findViewById(R.id.file_remove);
                
                fileName.setText(file.getName());
                fileSize.setText(file.getFormattedSize());
                
                fileRemove.setOnClickListener(v -> {
                    pendingFiles.remove(file);
                    updateAttachedFilesUI();
                });
                
                binding.attachedFilesContainer.addView(fileView);
            }
        }
    }

    private void sendMessage() {
        String messageText = binding.messageInput.getText().toString().trim();
        if (TextUtils.isEmpty(messageText) || isTyping) {
            return;
        }
        
        // Extract project ID from @mentions and build enhanced message
        String enhancedMessage = buildEnhancedMessage(messageText);

        // Add user message with attached files
        ChatMessage userMessage = new ChatMessage(
            UUID.randomUUID().toString(),
            messageText,
            ChatMessage.TYPE_USER,
            System.currentTimeMillis()
        );
        
        // Add attached files to the message
        if (!pendingFiles.isEmpty()) {
            userMessage.setAttachedFiles(new ArrayList<>(pendingFiles));
        }
        
        messages.add(userMessage);
        chatAdapter.notifyItemInserted(messages.size() - 1);
        
        // Save user message immediately
        messageStorage.addMessage(conversationId, userMessage);
        
        // Clear input and attached files
        binding.messageInput.setText("");
        List<ChatMessage.AttachedFile> messagePendingFiles = new ArrayList<>(pendingFiles);
        pendingFiles.clear();
        updateAttachedFilesUI();
        
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
        
        // Send to API with agentic support including files and features
        apiClient.sendMessage(conversationId, selectedModel, enhancedMessage, messagePendingFiles, 
                            thinkingEnabled, webSearchEnabled, new AgenticQwenApiClient.AgenticChatCallback() {
            private StringBuilder streamingContent = new StringBuilder();
            
            @Override
            public void onResponse(String response) {
                runOnUiThread(() -> {
                    hideTypingIndicator();
                    
                    // Update the last AI message with final response
                    if (!messages.isEmpty()) {
                        ChatMessage lastMessage = messages.get(messages.size() - 1);
                        if (lastMessage.getType() == ChatMessage.TYPE_AI) {
                            String content = response;
                            String thinkingContent = null;
                            
                            // Try to parse as JSON response with thinking content
                            try {
                                JSONObject responseObj = new JSONObject(response);
                                if (responseObj.has("content")) {
                                    content = responseObj.getString("content");
                                }
                                if (responseObj.has("thinking_content")) {
                                    thinkingContent = responseObj.getString("thinking_content");
                                }
                            } catch (JSONException e) {
                                // If it's not JSON, use the response as-is
                                content = response;
                            }
                            
                            lastMessage.setContent(content);
                            
                            // Set thinking content if available
                            if (thinkingContent != null && !thinkingContent.trim().isEmpty()) {
                                lastMessage.setThinkingContent(thinkingContent);
                            }
                            
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
                runOnUiThread(() -> {
                    hideTypingIndicator();
                    
                    // Skip if we're processing a proposal (handled by proposal callback)
                    if (isProcessingProposal) {
                        isProcessingProposal = false; // Reset the flag
                        return;
                    }
                    
                    // Parse action result to determine if this is a file operation
                    boolean isFileOperation = false;
                    try {
                        JSONObject result = new JSONObject(actionResult);
                        String actionName = result.optString("action", "");
                        
                        // Show "Fix applied" message only for file operations
                        isFileOperation = actionName.contains("file") || 
                                        actionName.equals("fix_file_error") ||
                                        actionName.equals("edit_file") ||
                                        actionName.equals("create_file");
                    } catch (JSONException e) {
                        // If not JSON, check if it contains file-related keywords
                        isFileOperation = actionResult.toLowerCase().contains("file") ||
                                        actionResult.toLowerCase().contains("created") ||
                                        actionResult.toLowerCase().contains("edited");
                    }
                    
                    if (isFileOperation) {
                        // Show success message for file operations
                        ChatMessage successMessage = new ChatMessage(
                            UUID.randomUUID().toString(),
                            "✅ Fix applied successfully! The file has been updated. Please rebuild your project to see if the errors are resolved.",
                            ChatMessage.TYPE_AI,
                            System.currentTimeMillis()
                        );
                        messages.add(successMessage);
                        chatAdapter.notifyItemInserted(messages.size() - 1);
                        messageStorage.saveMessages(conversationId, messages);
                        binding.messagesRecyclerView.scrollToPosition(messages.size() - 1);
                    }
                    
                    // Show project card only if it's not a project creation (project creation is handled separately)
                    if (projectId != null && isFileOperation) {
                        showProjectCard(projectId);
                    }
                });
            }

            @Override
            public void onFixProposal(String explanation, String actionJson, String projectId) {

                
                runOnUiThread(() -> {
                    hideTypingIndicator();
                    
                    try {
                        JSONObject actionData = new JSONObject(actionJson);
                        JSONObject parameters = actionData.getJSONObject("parameters");
                        
                        // Create and show fix proposal view
                        showFixProposal(explanation, parameters, projectId);
                        
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing fix proposal", e);
                    }
                });
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
    }

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

    private void showFixProposal(String explanation, JSONObject actionData, String projectId) {
        
        // Create proposal message with proper type
        ChatMessage proposalMessage = new ChatMessage(
            UUID.randomUUID().toString(),
            "AI has proposed a fix for your compile error:",
            ChatMessage.TYPE_PROPOSAL,
            System.currentTimeMillis()
        );
        
        // Set proposal data
        proposalMessage.setProposalData(actionData.toString());
        proposalMessage.setExplanation(explanation);
        
        messages.add(proposalMessage);
        chatAdapter.notifyItemInserted(messages.size() - 1);
        messageStorage.saveMessages(conversationId, messages);
        binding.messagesRecyclerView.scrollToPosition(messages.size() - 1);
    }
    
    private void showProjectCard(String projectId) {
        try {
            HashMap<String, Object> projectData = lC.b(projectId);
            if (projectData != null) {
                ChatMessage projectMessage = new ChatMessage(
                    UUID.randomUUID().toString(),
                    "Project updated successfully:",
                    ChatMessage.TYPE_AI,
                    System.currentTimeMillis()
                );
                
                // Set project data for interactive display
                projectMessage.setProjectId(projectId);
                projectMessage.setProjectName(yB.c(projectData, "my_ws_name"));
                projectMessage.setAppName(yB.c(projectData, "my_app_name"));
                projectMessage.setPackageName(yB.c(projectData, "my_sc_pkg_name"));
                
                messages.add(projectMessage);
                chatAdapter.notifyItemInserted(messages.size() - 1);
                messageStorage.saveMessages(conversationId, messages);
                binding.messagesRecyclerView.scrollToPosition(messages.size() - 1);
            }
                 } catch (Exception e) {
             Log.e(TAG, "Error showing project card", e);
         }
     }

    private void handleProposalAccepted(ChatMessage message) {
        try {
            JSONObject proposalData = new JSONObject(message.getProposalData());
            Log.d("ChatActivity", "Proposal data: " + proposalData.toString());
            
            // The proposal data is actually just the parameters, not the full action structure
            // We need to recreate the action structure for execution
            JSONObject actionJson = new JSONObject();
            actionJson.put("action", "fix_file_error");
            actionJson.put("parameters", proposalData);
            
            Log.d("ChatActivity", "Reconstructed action: " + actionJson.toString());
            
            // Hide the proposal by changing its type to AI message
            message.setType(ChatMessage.TYPE_AI);
            message.setContent("✅ Fix approved. Applying changes...");
            chatAdapter.notifyItemChanged(messages.indexOf(message));
            
            // Store proposalData for access in callbacks
            final JSONObject finalProposalData = proposalData;
            
            // Set flag to prevent duplicate message from main callback
            isProcessingProposal = true;
            
            apiClient.executeApprovedAction(conversationId, actionJson, new AgenticQwenApiClient.AgenticChatCallback() {
                @Override
                public void onResponse(String response) {
                    // Handle in onActionExecuted
                }

                @Override
                public void onStreamingResponse(String partialResponse) {
                    // Not needed for action execution
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Log.e(TAG, "Action execution error: " + error);
                        // Reset proposal processing flag on error
                        isProcessingProposal = false;
                        
                        // Show error message
                        ChatMessage errorMessage = new ChatMessage(
                            UUID.randomUUID().toString(),
                            "❌ Error applying fix: " + error + "\n\nPlease check the logs for more details.",
                            ChatMessage.TYPE_AI,
                            System.currentTimeMillis()
                        );
                        messages.add(errorMessage);
                        chatAdapter.notifyItemInserted(messages.size() - 1);
                        messageStorage.saveMessages(conversationId, messages);
                    });
                }

                @Override
                public void onActionExecuted(String actionResult, String projectId) {
                    runOnUiThread(() -> {
                        // Parse action result to get affected files
                        List<JSONObject> affectedFiles = new ArrayList<>();
                        try {
                            JSONObject result = new JSONObject(actionResult);
                            if (result.optBoolean("success", false)) {
                                // Create affected file data from the action
                                JSONObject affectedFile = new JSONObject();
                                affectedFile.put("file_path", finalProposalData.optString("file_path", ""));
                                affectedFile.put("action", finalProposalData.optString("action", "edit_file"));
                                affectedFile.put("content", finalProposalData.optString("content", ""));
                                affectedFiles.add(affectedFile);
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Error parsing action result for affected files", e);
                        }
                        
                        // Show success message with affected files
                        ChatMessage successMessage = new ChatMessage(
                            UUID.randomUUID().toString(),
                            "✅ Fix applied successfully! The file has been updated. Please rebuild your project to see if the errors are resolved.",
                            ChatMessage.TYPE_AI_SUCCESS,
                            System.currentTimeMillis()
                        );
                        
                        // Store affected files for display
                        if (!affectedFiles.isEmpty()) {
                            try {
                                JSONArray affectedFilesArray = new JSONArray();
                                for (JSONObject file : affectedFiles) {
                                    affectedFilesArray.put(file);
                                }
                                successMessage.setAffectedFiles(affectedFilesArray.toString());
                            } catch (Exception e) {
                                Log.w(TAG, "Error storing affected files", e);
                            }
                        }
                        
                        messages.add(successMessage);
                        chatAdapter.notifyItemInserted(messages.size() - 1);
                        messageStorage.saveMessages(conversationId, messages);
                        binding.messagesRecyclerView.scrollToPosition(messages.size() - 1);
                        
                        // Show project card
                        if (projectId != null) {
                            showProjectCard(projectId);
                        }
                    });
                }

                @Override
                public void onProjectCreated(String projectId, String projectName) {
                    // Not needed for fix actions
                }

                @Override
                public void onFixProposal(String explanation, String actionJson, String projectId) {
                    // Not needed for approved actions
                }
            });
            
        } catch (JSONException e) {
            Log.e(TAG, "Error creating action JSON", e);
            // Show error message to user
            ChatMessage errorMessage = new ChatMessage(
                UUID.randomUUID().toString(),
                "❌ Error processing proposal: " + e.getMessage(),
                ChatMessage.TYPE_AI,
                System.currentTimeMillis()
            );
            messages.add(errorMessage);
            chatAdapter.notifyItemInserted(messages.size() - 1);
            messageStorage.saveMessages(conversationId, messages);
        }
    }

    private void handleProposalDiscarded(ChatMessage message) {
        // Change proposal to AI message showing discard
        message.setType(ChatMessage.TYPE_AI);
        message.setContent("❌ Fix proposal discarded. The file will not be modified.");
        chatAdapter.notifyItemChanged(messages.indexOf(message));
        messageStorage.saveMessages(conversationId, messages);
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
    
    /**
     * Build enhanced message with project context when @projectId is mentioned
     */
    private String buildEnhancedMessage(String originalMessage) {
        // Extract project IDs from @mentions
        List<String> mentionedProjectIds = extractProjectIds(originalMessage);
        
        if (mentionedProjectIds.isEmpty()) {
            return originalMessage;
        }
        
        StringBuilder enhancedMessage = new StringBuilder();
        
        // Add project context for each mentioned project
        for (String projectId : mentionedProjectIds) {
            HashMap<String, Object> projectData = lC.b(projectId);
            if (projectData != null) {
                enhancedMessage.append("PROJECT_CONTEXT_").append(projectId).append(":\n");
                enhancedMessage.append(buildDetailedProjectContext(projectId, projectData));
                enhancedMessage.append("\n\n");
            }
        }
        
        // Add the original user message
        enhancedMessage.append("USER_REQUEST: ").append(originalMessage);
        
        return enhancedMessage.toString();
    }
    
    /**
     * Extract project IDs from @mentions in the message
     */
    private List<String> extractProjectIds(String message) {
        List<String> projectIds = new ArrayList<>();
        
        // Find all @projectId patterns
        int index = 0;
        while ((index = message.indexOf('@', index)) != -1) {
            int endIndex = index + 1;
            
            // Find the end of the project ID (until space or end of string)
            while (endIndex < message.length() && 
                   Character.isDigit(message.charAt(endIndex))) {
                endIndex++;
            }
            
            if (endIndex > index + 1) {
                String projectId = message.substring(index + 1, endIndex);
                if (!projectIds.contains(projectId)) {
                    projectIds.add(projectId);
                }
            }
            
            index = endIndex;
        }
        
        return projectIds;
    }
    
    /**
     * Build detailed project context including settings and file structure
     */
    private String buildDetailedProjectContext(String projectId, HashMap<String, Object> projectData) {
        StringBuilder context = new StringBuilder();
        
        // Basic project information
        context.append("- Project Name: ").append(yB.c(projectData, "my_ws_name")).append("\n");
        context.append("- App Name: ").append(yB.c(projectData, "my_app_name")).append("\n");
        context.append("- Package Name: ").append(yB.c(projectData, "my_sc_pkg_name")).append("\n");
        context.append("- Version Code: ").append(yB.c(projectData, "sc_ver_code")).append("\n");
        context.append("- Version Name: ").append(yB.c(projectData, "sc_ver_name")).append("\n");
        
        // Project settings
        try {
            mod.hey.studios.project.ProjectSettings settings = new mod.hey.studios.project.ProjectSettings(projectId);
            context.append("- Minimum SDK: ").append(settings.getMinSdkVersion()).append("\n");
            context.append("- Target SDK: ").append(settings.getValue(mod.hey.studios.project.ProjectSettings.SETTING_TARGET_SDK_VERSION, "34")).append("\n");
            context.append("- Application Class: ").append(settings.getValue(mod.hey.studios.project.ProjectSettings.SETTING_APPLICATION_CLASS, ".SketchApplication")).append("\n");
            context.append("- View Binding Enabled: ").append(settings.getValue(mod.hey.studios.project.ProjectSettings.SETTING_ENABLE_VIEWBINDING, "false")).append("\n");
            context.append("- Remove Old Methods: ").append(settings.getValue(mod.hey.studios.project.ProjectSettings.SETTING_DISABLE_OLD_METHODS, "false")).append("\n");
            context.append("- Material Components: ").append(settings.getValue(mod.hey.studios.project.ProjectSettings.SETTING_ENABLE_BRIDGELESS_THEMES, "false")).append("\n");
        } catch (Exception e) {
            Log.w(TAG, "Could not load project settings for " + projectId, e);
        }
        
        // Project file structure and paths
        context.append("\nPROJECT_PATHS:\n");
        String basePath = "/storage/emulated/0/.sketchware/data/" + projectId + "/";
        context.append("- Base Path: ").append(basePath).append("\n");
        context.append("- Java Files: ").append(basePath).append("files/java/").append(yB.c(projectData, "my_sc_pkg_name").replace(".", "/")).append("/\n");
        context.append("- Layout Files: ").append(basePath).append("files/resource/layout/\n");
        context.append("- Drawable Files: ").append(basePath).append("files/resource/drawable/\n");
        context.append("- Values Files: ").append(basePath).append("files/resource/values/\n");
        context.append("- Assets: ").append(basePath).append("files/assets/\n");
        
        // Available actions for this project
        context.append("\nAVAILABLE_ACTIONS_FOR_PROJECT:\n");
        context.append("- update_project_settings: Change project name, package name, SDK versions, etc.\n");
        context.append("- create_java_file: Create Java classes in the project\n");
        context.append("- create_xml_resource: Create layouts, drawables, values files\n");
        context.append("- edit_file: Modify existing project files\n");
        context.append("- read_file: Read project file contents\n");
        context.append("- list_directory: List project directory contents\n");
        
        return context.toString();
    }
}