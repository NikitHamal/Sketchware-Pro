package pro.sketchware.activities.ai.chat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import pro.sketchware.R;
import pro.sketchware.activities.ai.chat.adapters.ChatAdapter;
import pro.sketchware.activities.ai.chat.models.ChatMessage;
import pro.sketchware.activities.ai.chat.models.QwenModel;
import pro.sketchware.activities.ai.chat.ui.AttachedFilesManager;
import pro.sketchware.activities.ai.chat.ui.ProjectSelector;
import pro.sketchware.activities.ai.chat.viewmodel.ChatViewModel;
import pro.sketchware.databinding.ActivityChatBinding;

public class ChatActivity extends AppCompatActivity {
    private static final String TAG = "ChatActivity";
    private ActivityChatBinding binding;
    private ChatAdapter chatAdapter;
    private ChatViewModel viewModel;
    private String conversationId;
    private String selectedModel = "qwen3-235b-a22b"; // Default model
    private boolean thinkingEnabled = false;
    private boolean webSearchEnabled = false;
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private AttachedFilesManager attachedFilesManager;
    private ProjectSelector projectSelector;

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

        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        viewModel.init(conversationId);

        setupRecyclerView();
        setupInputArea();
        setupFilePickerLauncher();
        setupToolbar();
        setupModelSelector();
        observeViewModel();

        attachedFilesManager = new AttachedFilesManager(this, binding.attachedFilesContainer, new FileUploadManager(this));
        projectSelector = new ProjectSelector(this, binding.projectSelectorList, this::onProjectSelected);
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
        binding.modelSelectorText.setText(selectedModel);
        binding.modelSelectorButton.setOnClickListener(v -> showModelSelectionDialog());
    }

    private void showModelSelectionDialog() {
        // For simplicity, using a hardcoded list of models.
        // In a real app, this would be fetched from the view model.
        final String[] models = {"qwen3-235b-a22b", "qwen3-30b-a3b", "qwen3-32b", "qwen-max-latest"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Select AI Model")
                .setItems(models, (dialog, which) -> {
                    selectedModel = models[which];
                    binding.modelSelectorText.setText(selectedModel);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupRecyclerView() {
        chatAdapter = new ChatAdapter(new ArrayList<>(), this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.messagesRecyclerView.setLayoutManager(layoutManager);
        binding.messagesRecyclerView.setAdapter(chatAdapter);
    }

    private void observeViewModel() {
        viewModel.getMessages().observe(this, messages -> {
            chatAdapter.setMessages(messages);
            binding.messagesRecyclerView.scrollToPosition(messages.size() - 1);
        });

        viewModel.isTyping().observe(this, isTyping -> {
            if (isTyping) {
                showTypingIndicator();
            } else {
                hideTypingIndicator();
            }
        });
    }

    private void setupFilePickerLauncher() {
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri fileUri = result.getData().getData();
                        if (fileUri != null) {
                            attachedFilesManager.uploadFile(fileUri);
                        }
                    }
                }
        );
    }

    private void onProjectSelected(String projectId, String appName) {
        projectSelector.hide();
        String currentText = binding.messageInput.getText().toString();
        int cursorPosition = binding.messageInput.getSelectionStart();
        int atPosition = currentText.lastIndexOf('@', cursorPosition - 1);
        if (atPosition >= 0) {
            String beforeAt = currentText.substring(0, atPosition);
            String afterCursor = currentText.substring(cursorPosition);
            String newText = beforeAt + "@" + projectId + " " + afterCursor;
            binding.messageInput.setText(newText);
            binding.messageInput.setSelection(atPosition + projectId.length() + 2);
        }
    }

    private void setupInputArea() {
        binding.sendButton.setOnClickListener(v -> sendMessage());
        binding.chatOptionsButton.setOnClickListener(v -> showChatOptionsBottomSheet());

        binding.messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (count > 0 && s.charAt(start + count - 1) == '@') {
                    projectSelector.show();
                } else {
                    projectSelector.hide();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void showChatOptionsBottomSheet() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View bottomSheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_chat_options, null);
        bottomSheet.setContentView(bottomSheetView);

        bottomSheetView.findViewById(R.id.file_upload_option).setOnClickListener(v -> {
            bottomSheet.dismiss();
            openFilePicker();
        });

        Chip thinkingChip = bottomSheetView.findViewById(R.id.thinking_toggle_chip);
        thinkingChip.setChecked(thinkingEnabled);
        thinkingChip.setOnCheckedChangeListener((buttonView, isChecked) -> thinkingEnabled = isChecked);

        Chip webSearchChip = bottomSheetView.findViewById(R.id.web_search_toggle_chip);
        webSearchChip.setChecked(webSearchEnabled);
        webSearchChip.setOnCheckedChangeListener((buttonView, isChecked) -> webSearchEnabled = isChecked);

        bottomSheet.show();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        filePickerLauncher.launch(Intent.createChooser(intent, "Select File"));
    }

    private void sendMessage() {
        String messageText = binding.messageInput.getText().toString().trim();
        if (TextUtils.isEmpty(messageText)) {
            return;
        }

        viewModel.sendMessage(messageText, selectedModel, thinkingEnabled, webSearchEnabled, attachedFilesManager.getPendingFiles());
        binding.messageInput.setText("");
        attachedFilesManager.clearPendingFiles();
    }

    private void showTypingIndicator() {
        binding.sendButton.setEnabled(false);
        binding.typingIndicator.setVisibility(View.VISIBLE);
    }

    private void hideTypingIndicator() {
        binding.sendButton.setEnabled(true);
        binding.typingIndicator.setVisibility(View.GONE);
    }
}