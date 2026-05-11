package pro.sketchware.ai.chat.coordinator;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import pro.sketchware.ai.chat.adapter.ChatMessageAdapter;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.offline.OfflineModeController;

/**
 * ChatCoordinator — The ONLY bridge between the Chat UI and the AI/logic layers.
 *
 * <p><b>Architecture (MANDATORY — DO NOT BYPASS):</b>
 * <pre>
 * UI ──► ChatCoordinator ──► AIOrchestrator ──► ToolManager ──► Tool.execute()
 *  ▲               ▲               │                                  │
 *  │               └───────────────┘◄─────────────── callbacks ───────┘
 *  └── OfflineModeController (direct tool path when AI unavailable)
 * </pre>
 *
 * <p><b>Stage 2 additions:</b>
 * <ul>
 *   <li>{@link #addToolResultMessage} — inserts a TOOL-type message from tool execution.</li>
 *   <li>{@link #addInternalAssistantMessage} — inserts an INTERNAL_ASSISTANT message.</li>
 *   <li>{@link #addSystemMessage} — public version for offline mode controller.</li>
 *   <li>{@link #setOfflineMessageHandler} — hooks in the OfflineModeController.</li>
 * </ul>
 *
 * <p><b>What this class owns:</b>
 * <ul>
 *   <li>The canonical in-memory message list (single source of truth).</li>
 *   <li>All RecyclerView / adapter update calls.</li>
 *   <li>Typing indicator show/hide logic.</li>
 *   <li>Auto-scroll behavior.</li>
 *   <li>Batching of rapid UI updates.</li>
 *   <li>Copy / Share action routing.</li>
 * </ul>
 *
 * <p><b>What this class does NOT own:</b>
 * <ul>
 *   <li>AI model calls — AIOrchestrator.</li>
 *   <li>Tool execution — ToolManager / Tool.</li>
 *   <li>Conversation persistence — ConversationManager (Stage 3).</li>
 * </ul>
 */
public class ChatCoordinator implements ChatMessageAdapter.ChatMessageListener {

    private static final String TAG = "ChatCoordinator";

    private static final long STREAMING_BATCH_INTERVAL_MS = 80L;

    // ─── Threading ────────────────────────────────────────────────────────────

    private final ExecutorService backgroundExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "ChatCoordinator-BG");
                t.setDaemon(true);
                return t;
            });

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ─── State ────────────────────────────────────────────────────────────────

    private final AtomicBoolean isAiResponding = new AtomicBoolean(false);

    @NonNull
    private final List<ChatMessage> messages = new ArrayList<>();

    @Nullable
    private ChatMessage streamingMessage;

    @Nullable
    private Runnable pendingBatchUpdate;

    private long lastStreamingUpdateMs = 0L;

    // ─── Attached views ───────────────────────────────────────────────────────

    @Nullable private ChatMessageAdapter adapter;
    @Nullable private RecyclerView recyclerView;
    @Nullable private android.view.View typingIndicator;
    @Nullable private android.view.View emptyStateView;
    @Nullable private android.view.View scrollToBottomFab;

    // ─── Context ──────────────────────────────────────────────────────────────

    @NonNull
    private final Context applicationContext;

    @Nullable private CoordinatorListener coordinatorListener;

    @Nullable private OfflineModeController offlineController;

    // ─── Interfaces ───────────────────────────────────────────────────────────

    public interface CoordinatorListener {
        void onAiStarted();
        void onAiFinished();
        void onAiError(@NonNull String errorMessage);
        void onMessageCountChanged(int count);
    }

    // ─── Constructor ──────────────────────────────────────────────────────────

    public ChatCoordinator(@NonNull Context context) {
        this.applicationContext = context.getApplicationContext();
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @MainThread
    public void attach(
            @NonNull ChatMessageAdapter adapter,
            @NonNull RecyclerView recyclerView,
            @Nullable android.view.View typingIndicator,
            @Nullable android.view.View emptyStateView,
            @Nullable android.view.View scrollToBottomFab
    ) {
        this.adapter         = adapter;
        this.recyclerView    = recyclerView;
        this.typingIndicator = typingIndicator;
        this.emptyStateView  = emptyStateView;
        this.scrollToBottomFab = scrollToBottomFab;

        adapter.setListener(this);

        if (!messages.isEmpty()) {
            adapter.submitList(new ArrayList<>(messages));
            updateEmptyState();
        }

        if (scrollToBottomFab != null) {
            recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                    scrollToBottomFab.setVisibility(
                            isAtBottom(rv) ? android.view.View.GONE : android.view.View.VISIBLE);
                }
            });
            scrollToBottomFab.setOnClickListener(v -> scrollToBottom(true));
        }
    }

    @MainThread
    public void detach() {
        if (adapter != null) adapter.setListener(null);
        adapter = null;
        recyclerView = null;
        typingIndicator = null;
        emptyStateView = null;
        scrollToBottomFab = null;

        if (pendingBatchUpdate != null) {
            mainHandler.removeCallbacks(pendingBatchUpdate);
            pendingBatchUpdate = null;
        }
    }

    public void destroy() {
        detach();
        backgroundExecutor.shutdownNow();
    }

    // ─── Configuration ────────────────────────────────────────────────────────

    public void setCoordinatorListener(@Nullable CoordinatorListener listener) {
        this.coordinatorListener = listener;
    }

    public void setOfflineController(@Nullable OfflineModeController controller) {
        this.offlineController = controller;
    }

    // ─── User actions ─────────────────────────────────────────────────────────

    /**
     * Primary entry point: user submits a message.
     *
     * <p>Stage 2 flow:
     * <ol>
     *   <li>Validate input.</li>
     *   <li>Add USER message to the list → adapter.</li>
     *   <li>Check offline handler (OfflineModeController) — if handled, stop here.</li>
     *   <li>Show typing indicator.</li>
     *   <li>Delegate to AIOrchestrator (via AiDelegate).</li>
     * </ol>
     */
    public void sendUserMessage(@NonNull String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return;
        if (isAiResponding.get()) {
            Log.w(TAG, "sendUserMessage: AI is still responding, ignoring.");
            return;
        }

        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> sendUserMessage(trimmed));
            return;
        }

        ChatMessage userMessage = ChatMessage.user(trimmed);
        addMessageInternal(userMessage);

        if (offlineController != null && offlineController.isOfflineModeActive()) {
            Log.d(TAG, "Message handled offline — skipping AI.");
            offlineController.handleOfflineMessage(userMessage);
            return;
        }

        showTypingIndicator(true);
        isAiResponding.set(true);

        if (coordinatorListener != null) coordinatorListener.onAiStarted();

        List<ChatMessage> historyCopy =
                Collections.unmodifiableList(new ArrayList<>(messages));

        backgroundExecutor.submit(() -> {
            runEchoResponse(trimmed);
        });
    }

    public void cancelCurrentResponse() {
        if (!isAiResponding.get()) return;
        mainHandler.post(() -> {
            isAiResponding.set(false);
            showTypingIndicator(false);

            if (streamingMessage != null) {
                streamingMessage.setStreaming(false);
                streamingMessage.setStatus(ChatMessage.MessageStatus.SENT);
                if (adapter != null) adapter.updateMessage(streamingMessage);
                streamingMessage = null;
            }

            if (coordinatorListener != null) coordinatorListener.onAiFinished();
        });
    }

    @MainThread
    public void clearConversation() {
        messages.clear();
        streamingMessage = null;
        isAiResponding.set(false);

        if (adapter != null) adapter.clearMessages();
        updateEmptyState();

        addMessageInternal(ChatMessage.system("Conversation cleared."));
    }

    @MainThread
    public void loadMessages(@NonNull List<ChatMessage> existingMessages) {
        messages.clear();
        messages.addAll(existingMessages);

        if (adapter != null) adapter.submitList(new ArrayList<>(messages));
        updateEmptyState();
        scrollToBottom(false);
    }

    // ─── Stage 2: Public message injection methods ────────────────────────────

    /**
     * Adds a TOOL-type message with the tool result.
     * Called by OfflineModeController after a tool executes successfully.
     * Safe to call from any thread.
     *
     * @param toolName the name of the tool that produced the result
     * @param content  the tool's output content
     */
    public void addToolResultMessage(@NonNull String toolName, @NonNull String content) {
        // Generate a simple toolCallId for linking
        String toolCallId = toolName + "_" + System.currentTimeMillis();
        ChatMessage toolMsg = ChatMessage.tool(toolName, toolCallId, content);
        runOnMain(() -> addMessageInternal(toolMsg));
    }

    /**
     * Adds an INTERNAL_ASSISTANT message.
     * Called by OfflineModeController for status updates and hints.
     * Safe to call from any thread.
     *
     * @param content the internal assistant message content (supports Markdown)
     */
    public void addInternalAssistantMessage(@NonNull String content) {
        ChatMessage msg = ChatMessage.internalAssistant(content);
        runOnMain(() -> addMessageInternal(msg));
    }

    /**
     * Adds a SYSTEM message.
     * Used for notifications (offline mode toggled, conversation cleared, etc.)
     * Safe to call from any thread.
     *
     * @param content the system notification text
     */
    public void addSystemMessage(@NonNull String content) {
        ChatMessage msg = ChatMessage.system(content);
        runOnMain(() -> addMessageInternal(msg));
    }

    // ─── ChatMessageAdapter.ChatMessageListener ───────────────────────────────

    @Override
    public void onCopyMessage(@NonNull ChatMessage message) {
        String text = message.getText();
        if (text == null || text.isEmpty()) return;

        ClipboardManager clipboard =
                (ClipboardManager) applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("ChatMessage", text));
        }
        mainHandler.post(() -> android.widget.Toast.makeText(
                applicationContext, "Copied", android.widget.Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onShareMessage(@NonNull ChatMessage message) {
        String text = message.getText();
        if (text == null || text.isEmpty()) return;

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Intent chooser = Intent.createChooser(shareIntent, "Share message");
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        applicationContext.startActivity(chooser);
    }

    @Override
    public void onLongPressMessage(@NonNull ChatMessage message) {
        // Stage 3: custom BottomSheetDialog with Copy/Share/Select All/Cancel.
        onCopyMessage(message);
    }

    @Override
    public void onToggleExpand(@NonNull ChatMessage message, boolean isExpanded) {
        Log.d(TAG, "Message expanded=" + isExpanded + " id=" + message.getId());
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    @MainThread
    private void addMessageInternal(@NonNull ChatMessage message) {
        messages.add(message);
        if (adapter != null) adapter.addMessage(message);
        updateEmptyState();
        scrollToBottom(true);
    }

    @MainThread
    private void showTypingIndicator(boolean show) {
        if (typingIndicator == null) return;
        typingIndicator.setVisibility(
                show ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    @MainThread
    private void updateEmptyState() {
        if (emptyStateView == null) return;
        emptyStateView.setVisibility(
                messages.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);

        if (coordinatorListener != null) {
            coordinatorListener.onMessageCountChanged(messages.size());
        }
    }

    @MainThread
    private void scrollToBottom(boolean smooth) {
        if (recyclerView == null || messages.isEmpty()) return;
        int last = messages.size() - 1;
        if (smooth) recyclerView.smoothScrollToPosition(last);
        else        recyclerView.scrollToPosition(last);
    }

    private boolean isAtBottom(@NonNull RecyclerView rv) {
        LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
        if (lm == null) return true;
        return lm.findLastCompletelyVisibleItemPosition() >= lm.getItemCount() - 1;
    }

    private void runOnMain(@NonNull Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else mainHandler.post(r);
    }

    private void scheduleBatchUpdate() {
        if (pendingBatchUpdate != null) mainHandler.removeCallbacks(pendingBatchUpdate);
        pendingBatchUpdate = () -> {
            pendingBatchUpdate = null;
            if (streamingMessage != null && adapter != null) {
                adapter.updateMessage(streamingMessage);
                if (recyclerView != null && isAtBottom(recyclerView)) {
                    scrollToBottom(false);
                }
            }
        };
        mainHandler.post(pendingBatchUpdate);
    }

    private void cancelPendingBatchUpdate() {
        if (pendingBatchUpdate != null) {
            mainHandler.removeCallbacks(pendingBatchUpdate);
            pendingBatchUpdate = null;
        }
    }

    // ─── Stage 1 echo placeholder ─────────────────────────────────────────────

    private void runEchoResponse(@NonNull String userText) {
        addSystemMessage("AI is not connected. Configure an AI provider in Settings to enable responses.");
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    @NonNull
    public List<ChatMessage> getMessages() {
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }

    public boolean isAiResponding() { return isAiResponding.get(); }

    public int getMessageCount() { return messages.size(); }
}
