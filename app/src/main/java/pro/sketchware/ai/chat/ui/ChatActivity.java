package pro.sketchware.ai.chat.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;

import pro.sketchware.R;
import pro.sketchware.ai.chat.adapter.ChatMessageAdapter;
import pro.sketchware.ai.chat.coordinator.ChatCoordinator;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.file.FileAttachManager;

public class ChatActivity extends AppCompatActivity
        implements ChatCoordinator.CoordinatorListener,
                   ChatMessageAdapter.ChatMessageListener {

    @NonNull  private ChatCoordinator coordinator;
    @NonNull  private ChatMessageAdapter adapter;
    @Nullable private FileAttachManager fileAttachManager;

    @Nullable private RecyclerView recyclerView;
    @Nullable private View typingIndicator;
    @Nullable private View emptyStateView;
    @Nullable private View scrollToBottomFab;
    @Nullable private TextInputEditText inputEditText;
    @Nullable private View btnSend;
    @Nullable private View btnMic;
    @Nullable private View btnAttach;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_ui);

        fileAttachManager = new FileAttachManager(this);
        fileAttachManager.registerLauncher(this);
        fileAttachManager.setCallback(result -> {
            coordinator.sendUserMessage(result.getChatText());
        });

        coordinator = new ChatCoordinator(this);
        adapter = new ChatMessageAdapter(this);
        adapter.setListener(this);
        coordinator.setCoordinatorListener(this);

        bindViews();
        setupRecyclerView();
        setupInputArea();

        coordinator.attach(adapter, recyclerView, typingIndicator,
                emptyStateView, scrollToBottomFab);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        coordinator.destroy();
        if (fileAttachManager != null) fileAttachManager.destroy();
    }

    private void bindViews() {
        recyclerView    = findViewById(R.id.chat_recycler_view);
        typingIndicator = findViewById(R.id.chat_typing_indicator_container);
        emptyStateView  = findViewById(R.id.chat_empty_state);
        scrollToBottomFab = findViewById(R.id.chat_scroll_to_bottom_fab);
        inputEditText   = findViewById(R.id.chat_input_edit_text);
        btnSend         = findViewById(R.id.chat_btn_send);
        btnMic          = findViewById(R.id.chat_btn_mic);
        btnAttach       = findViewById(R.id.chat_btn_attach);
    }

    private void setupRecyclerView() {
        if (recyclerView == null) return;

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        recyclerView.setItemViewCacheSize(20);
        recyclerView.setHasFixedSize(false);
    }

    private void setupInputArea() {
        if (btnSend != null) {
            btnSend.setOnClickListener(v -> submitInput());
        }

        if (btnMic != null) {
            btnMic.setOnClickListener(v -> {
                coordinator.addSystemMessage("Voice input coming soon.");
            });
        }

        if (btnAttach != null) {
            btnAttach.setOnClickListener(v -> {
                if (fileAttachManager != null) fileAttachManager.openFilePicker();
            });
        }

        if (inputEditText != null) {
            inputEditText.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                    submitInput();
                    return true;
                }
                return false;
            });

            inputEditText.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    boolean hasText = s != null && s.length() > 0;
                    if (btnSend != null) btnSend.setVisibility(hasText ? View.VISIBLE : View.GONE);
                    if (btnMic  != null) btnMic.setVisibility(hasText  ? View.GONE    : View.VISIBLE);
                }
            });
        }
    }

    private void submitInput() {
        if (inputEditText == null) return;
        String text = inputEditText.getText() != null
                ? inputEditText.getText().toString().trim() : "";
        if (text.isEmpty()) return;

        inputEditText.setText("");
        coordinator.sendUserMessage(text);
    }

    @Override
    public void onCopyMessage(@NonNull ChatMessage message) {
        coordinator.onCopyMessage(message);
    }

    @Override
    public void onShareMessage(@NonNull ChatMessage message) {
        coordinator.onShareMessage(message);
    }

    @Override
    public void onLongPressMessage(@NonNull ChatMessage message) {
        MessageActionsBottomSheet sheet =
                MessageActionsBottomSheet.show(getSupportFragmentManager(), message);
        sheet.setOnSelectAllListener(messageId -> coordinator.onCopyMessage(message));
    }

    @Override
    public void onToggleExpand(@NonNull ChatMessage message, boolean isExpanded) {
    }

    @Override
    public void onAiStarted() {
        if (btnSend != null) btnSend.setEnabled(false);
    }

    @Override
    public void onAiFinished() {
        if (btnSend != null) btnSend.setEnabled(true);
    }

    @Override
    public void onAiError(@NonNull String errorMessage) {
        if (btnSend != null) btnSend.setEnabled(true);
    }

    @Override
    public void onMessageCountChanged(int count) {
    }
}