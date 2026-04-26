package pro.sketchware.ai.bottomsheet;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.ai.activities.AiSettingsActivity;
import pro.sketchware.ai.adapters.ChatAdapter;
import pro.sketchware.ai.adapters.ModelSelectorAdapter;
import pro.sketchware.ai.engine.AgentExecutor;
import pro.sketchware.ai.integration.AiProjectIntegrationHelper;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.Conversation;
import pro.sketchware.ai.models.ModelInfo;
import pro.sketchware.ai.models.ToolCall;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.ai.models.Workspace;
import pro.sketchware.ai.storage.AiPreferences;
import pro.sketchware.ai.storage.ConversationManager;
import pro.sketchware.ai.storage.WorkspaceManager;

/**
 * AI Assistant panel embedded inside DesignActivity.
 *
 * Layout is a vertical LinearLayout (no BottomSheetBehavior needed):
 *   [Handle row — drag target]
 *   [Header 52dp fixed]
 *   [Divider]
 *   [Messages RecyclerView  weight=1  ← shrinks when keyboard appears]
 *   [Input bar  wrap_content          ← always visible above keyboard]
 *
 * Three snap states:
 *   HIDDEN   → fully below screen
 *   HALF     → ~60 % of sheet visible  (≈ 50 % of parent height)
 *   EXPANDED → fully visible (sheet top at translationY=0)
 *
 * Drag: the handle row supports both fling AND live drag (finger tracking),
 * so the user can pull the sheet up/down smoothly.
 *
 * Keyboard: ViewTreeObserver measures visible window frame.
 * When keyboard opens the sheet translates up by keyboard height.
 */
public class AiProjectBottomSheet
        implements AgentExecutor.AgentCallback, ChatAdapter.OnArtifactActionListener {

    public static final int STATE_HIDDEN   = 0;
    public static final int STATE_HALF     = 1;
    public static final int STATE_EXPANDED = 2;

    private static final int ANIM_MS = 280;

    private final Context context;
    private final String  scId;

    private View    sheetRoot;
    private int     parentHeight;
    private int     currentState    = STATE_HIDDEN;
    private int     lastKeyboardH   = 0;

    // Views
    private RecyclerView      messagesList;
    private boolean           userScrolledUp = false;
    private TextView          titleView;
    private TextView          subtitleView;
    private TextView          modelChipView;
    private LinearLayout      typingIndicator;
    private TextView          typingText;
    private LinearLayout      emptyState;
    private TextInputEditText inputView;
    private MaterialButton    btnSend;

    // AI
    private ChatAdapter         chatAdapter;
    private AgentExecutor       agentExecutor;
    private AiPreferences       preferences;
    private ConversationManager conversationManager;
    private WorkspaceManager    workspaceManager;

    private String       workspaceId;
    private String       conversationId;
    private Workspace    workspace;
    private Conversation conversation;
    private AiProvider   currentProvider;
    private String       currentModelId;
    private boolean      isAgentRunning = false;

    // Speech + file picker bridge
    private SpeechRecognizer speechRecognizer;
    private androidx.activity.result.ActivityResultLauncher<Intent> fileLauncher;

    // ─────────────────────────────────────────────────────────────────────────

    public AiProjectBottomSheet(@NonNull Context context, @NonNull String scId) {
        this.context = context;
        this.scId    = scId;
    }

    /** Inflate and attach the sheet to parent. Call from DesignActivity.onCreate(). */
    public void attachToParent(@NonNull ViewGroup parent, int parentHeight) {
        this.parentHeight = parentHeight;

        sheetRoot = android.view.LayoutInflater.from(context)
                .inflate(R.layout.design_ai_bottom_sheet, parent, false);

        // Fixed height = 82 % of parent; positioned at bottom via RelativeLayout params
        int sheetH = (int) (parentHeight * 0.82f);

        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, sheetH);
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);

        // Start off-screen
        sheetRoot.setTranslationY(sheetH);
        parent.addView(sheetRoot, lp);

        applyBackground();
        bindViews();
        setupAi();
        setupInput();
        setupButtons();
        setupDragAndSwipe();
        setupKeyboardListener();
        setupScrollToBottomFab();
    }

    // ── Background ────────────────────────────────────────────────────────

    private void applyBackground() {
        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorSurface, tv, true);
        sheetRoot.setBackgroundColor(tv.data);
        sheetRoot.setClipToOutline(true);
        sheetRoot.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                float r = v.getResources().getDisplayMetrics().density * 20;
                o.setRoundRect(0, 0, v.getWidth(), (int)(v.getHeight() + r), r);
            }
        });
    }

    // ── Bind views ────────────────────────────────────────────────────────

    private void bindViews() {
        titleView       = sheetRoot.findViewById(R.id.ai_sheet_title);
        subtitleView    = sheetRoot.findViewById(R.id.ai_sheet_subtitle);
        modelChipView   = sheetRoot.findViewById(R.id.ai_sheet_model_chip);
        typingIndicator = sheetRoot.findViewById(R.id.ai_sheet_typing_indicator);
        typingText      = sheetRoot.findViewById(R.id.ai_sheet_typing_text);
        emptyState      = sheetRoot.findViewById(R.id.ai_sheet_empty);
        inputView       = sheetRoot.findViewById(R.id.ai_sheet_input);
        btnSend         = sheetRoot.findViewById(R.id.ai_sheet_btn_send);
        messagesList    = sheetRoot.findViewById(R.id.ai_sheet_messages);

        String name = AiProjectIntegrationHelper.resolveProjectName(scId, null);
        titleView.setText("AI \u2014 " + name);
        subtitleView.setText("Project " + scId);
    }

    // ── Keyboard handling ─────────────────────────────────────────────────
    /**
     * Keyboard-awareness — two-step fix:
     *
     * 1. The sheet root gets a bottom padding equal to the IME height so that the
     *    inner LinearLayout's content (including the input bar) is pushed UP inside
     *    the fixed-height container and stays visible.
     *
     * 2. The sheet itself is also animated upward so the full input area clears the
     *    soft keyboard edge.
     *
     * This avoids the previous bug where the sheet translated up but the input bar
     * was still hidden because the CONTENT inside the fixed-height sheet didn't move.
     */
    private void setupKeyboardListener() {
        sheetRoot.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                if (sheetRoot == null || sheetRoot.getParent() == null) return;

                Rect visibleFrame = new Rect();
                sheetRoot.getRootView().getWindowVisibleDisplayFrame(visibleFrame);

                int screenH   = sheetRoot.getRootView().getHeight();
                int keyboardH = screenH - visibleFrame.bottom;

                // Clamp: navigation bar inset is usually < 150 px — ignore it
                if (keyboardH < 0) keyboardH = 0;
                if (Math.abs(keyboardH - lastKeyboardH) < 50) return;
                lastKeyboardH = keyboardH;

                float baseY = targetTranslationY(currentState);

                if (keyboardH > 150) {
                    // Step 1 — push content inside the sheet up via padding
                    sheetRoot.setPadding(
                            sheetRoot.getPaddingLeft(),
                            sheetRoot.getPaddingTop(),
                            sheetRoot.getPaddingRight(),
                            keyboardH);

                    // Step 2 — also slide the whole sheet up so the top of
                    // the keyboard doesn't overlap the input bar
                    float newY = baseY - keyboardH;
                    // Don't go above screen top
                    if (newY < 0) newY = 0;
                    sheetRoot.animate()
                            .translationY(newY)
                            .setDuration(180)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                } else {
                    // Keyboard closed — remove padding and restore position
                    sheetRoot.setPadding(
                            sheetRoot.getPaddingLeft(),
                            sheetRoot.getPaddingTop(),
                            sheetRoot.getPaddingRight(),
                            0);
                    sheetRoot.animate()
                            .translationY(baseY)
                            .setDuration(180)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                }
            }
        });
    }

    // ── Snap animation ────────────────────────────────────────────────────

    private int sheetHeight() {
        return sheetRoot.getHeight() > 0 ? sheetRoot.getHeight()
                : (int) (parentHeight * 0.82f);
    }

    private float targetTranslationY(int state) {
        int h = sheetHeight();
        switch (state) {
            // HALF: 40 % of sheet height = sheet peek is 60 % visible
            // This means ~50 % of parent is occupied (0.82 * 0.60 ≈ 0.49)
            case STATE_HALF:     return h * 0.40f;
            case STATE_EXPANDED: return 0f;
            default:             return h;  // fully hidden
        }
    }

    private void animateTo(int state) {
        currentState = state;
        lastKeyboardH = 0;
        float target = targetTranslationY(state);
        ValueAnimator anim = ValueAnimator.ofFloat(sheetRoot.getTranslationY(), target);
        anim.setDuration(ANIM_MS);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> sheetRoot.setTranslationY((float) a.getAnimatedValue()));
        anim.start();
    }

    // ── Drag + Swipe gesture on handle row ────────────────────────────────

    private void setupDragAndSwipe() {
        View handleRow = sheetRoot.findViewById(R.id.ai_sheet_handle_row);

        // Tap: toggle between states
        handleRow.setOnClickListener(v -> toggle());

        // Track drag start Y and current translationY when drag begins
        final float[] dragStartY     = {0f};
        final float[] sheetStartTransY = {0f};
        final boolean[] isDragging   = {false};

        GestureDetector gestureDetector = new GestureDetector(context,
            new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                    if (e1 == null || e2 == null) return false;
                    float dy = e2.getRawY() - e1.getRawY();
                    if (Math.abs(dy) < 40 || Math.abs(vY) < 100) return false;
                    if (dy < 0) { // swipe up
                        if      (currentState == STATE_HIDDEN) animateTo(STATE_HALF);
                        else if (currentState == STATE_HALF)   animateTo(STATE_EXPANDED);
                    } else {     // swipe down
                        if      (currentState == STATE_EXPANDED) animateTo(STATE_HALF);
                        else                                     animateTo(STATE_HIDDEN);
                    }
                    return true;
                }
            });

        handleRow.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);

            int h = sheetHeight();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragStartY[0]       = event.getRawY();
                    sheetStartTransY[0] = sheetRoot.getTranslationY();
                    isDragging[0]       = false;
                    break;

                case MotionEvent.ACTION_MOVE:
                    float dy = event.getRawY() - dragStartY[0];
                    if (!isDragging[0] && Math.abs(dy) > 8) isDragging[0] = true;
                    if (isDragging[0]) {
                        float newY = sheetStartTransY[0] + dy;
                        // Clamp: don't allow going above top or below full-hidden
                        newY = Math.max(0f, Math.min(newY, (float) h));
                        sheetRoot.setTranslationY(newY);
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isDragging[0]) {
                        // Snap to nearest state based on final position
                        float finalY = sheetRoot.getTranslationY();
                        float halfY  = targetTranslationY(STATE_HALF);
                        float expandY = targetTranslationY(STATE_EXPANDED);
                        float hiddenY = (float) h;

                        // Determine closest snap point
                        float distHalf   = Math.abs(finalY - halfY);
                        float distExpand = Math.abs(finalY - expandY);
                        float distHidden = Math.abs(finalY - hiddenY);

                        int snapTo;
                        if (distExpand <= distHalf && distExpand <= distHidden) {
                            snapTo = STATE_EXPANDED;
                        } else if (distHalf <= distHidden) {
                            snapTo = STATE_HALF;
                        } else {
                            snapTo = STATE_HIDDEN;
                        }
                        animateTo(snapTo);
                    } else {
                        // It was a tap, not a drag — performClick handled by onClickListener
                        v.performClick();
                    }
                    isDragging[0] = false;
                    break;
            }
            return true;
        });
    }

    // ── Public controls ───────────────────────────────────────────────────

    public void toggle() {
        switch (currentState) {
            case STATE_HIDDEN:    animateTo(STATE_HALF);     break;
            case STATE_HALF:      animateTo(STATE_EXPANDED); break;
            default:              animateTo(STATE_HIDDEN);   break;
        }
    }

    public boolean isVisible()       { return currentState != STATE_HIDDEN; }
    public int     getCurrentState() { return currentState; }

    /** Call from DesignActivity to wire the file picker result back to this sheet. */
    public void setFileLauncher(
            @NonNull androidx.activity.result.ActivityResultLauncher<Intent> launcher) {
        this.fileLauncher = launcher;
    }

    /** Called by DesignActivity when a file was picked via fileLauncher. */
    public void onFileSelected(@NonNull Uri uri) {
        try {
            java.io.InputStream is = context.getContentResolver().openInputStream(uri);
            if (is == null) return;
            java.io.BufferedReader reader =
                    new java.io.BufferedReader(new java.io.InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            int charCount = 0;
            while ((line = reader.readLine()) != null && charCount < 8000) {
                sb.append(line).append("\n");
                charCount += line.length();
            }
            reader.close();
            String fileName = uri.getLastPathSegment();
            String fileContent = "```\n// File: " + fileName + "\n"
                    + sb.toString().trim() + "\n```";
            String current = inputView.getText() != null
                    ? inputView.getText().toString().trim() : "";
            inputView.setText(current.isEmpty() ? fileContent : current + "\n\n" + fileContent);
            // Toast removed: "File attached: " + fileName
        } catch (Exception e) {
            // Toast removed: "Could not read file: " + e.getMessage()
        }
    }

    // ── AI setup ──────────────────────────────────────────────────────────

    private void setupAi() {
        preferences         = AiPreferences.getInstance(context);
        conversationManager = new ConversationManager(context);
        workspaceManager    = new WorkspaceManager(context);

        workspace   = AiProjectIntegrationHelper.ensureProjectWorkspace(context, scId, null);
        workspaceId = workspace.getId();

        List<Conversation> existing = conversationManager.getConversationsForWorkspace(workspaceId);
        List<ChatMessage>  history  = new ArrayList<>();

        if (!existing.isEmpty()) {
            conversation   = existing.get(existing.size() - 1);
            conversationId = conversation.getId();
            history        = conversationManager.getMessages(conversationId);
        } else {
            conversation   = AiProjectIntegrationHelper.createConversation(
                    context, workspace, "New Chat");
            conversationId = conversation.getId();
        }

        currentProvider = preferences.getSelectedProvider();
        currentModelId  = preferences.getSelectedModel(currentProvider);
        if (currentModelId == null) {
            List<ModelInfo> m = preferences.getCachedModels(currentProvider);
            if (m != null && !m.isEmpty()) currentModelId = m.get(0).getId();
        }
        updateModelChip();

        LinearLayoutManager lm = new LinearLayoutManager(context);
        lm.setStackFromEnd(true);
        messagesList.setLayoutManager(lm);
        chatAdapter = new ChatAdapter();
        chatAdapter.setArtifactActionListener(this);
        messagesList.setAdapter(chatAdapter);

        if (!history.isEmpty()) {
            chatAdapter.setMessages(history);
            emptyState.setVisibility(View.GONE);
            scrollToBottom();
        } else {
            emptyState.setVisibility(View.VISIBLE);
        }
    }

    private void updateModelChip() {
        if (currentModelId == null) { modelChipView.setText("Select model"); return; }
        String label = currentModelId.contains("/")
                ? currentModelId.substring(currentModelId.lastIndexOf('/') + 1)
                : currentModelId;
        modelChipView.setText(label);
    }

    // ── Input ─────────────────────────────────────────────────────────────

    private void setupInput() {
        inputView.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                refreshSendState();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        refreshSendState();
    }

    private void setupButtons() {
        btnSend.setOnClickListener(v -> {
            if (isAgentRunning) stopAgent(); else sendMessage();
        });
        modelChipView.setOnClickListener(v -> showModelSelector());
        sheetRoot.findViewById(R.id.ai_sheet_btn_settings).setOnClickListener(v -> {
            Intent i = new Intent(context, AiSettingsActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        });
        sheetRoot.findViewById(R.id.ai_sheet_btn_close)
                .setOnClickListener(v -> animateTo(STATE_HIDDEN));
        sheetRoot.findViewById(R.id.ai_sheet_handle_row)
                .setOnClickListener(v -> toggle());

        // Mic button — direct SpeechRecognizer (no onActivityResult needed)
        sheetRoot.findViewById(R.id.ai_sheet_btn_mic).setOnClickListener(v -> startListening());

        // Clear button
        sheetRoot.findViewById(R.id.ai_sheet_btn_clear).setOnClickListener(v ->
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle("Clear conversation")
                .setMessage("This will delete all messages in this conversation. This cannot be undone.")
                .setPositiveButton("Clear", (d, w) -> {
                    conversationManager.deleteMessages(conversationId);
                    chatAdapter.setMessages(new ArrayList<>());
                    emptyState.setVisibility(View.VISIBLE);
                    // Toast removed: "Conversation cleared."
                })
                .setNegativeButton("Cancel", null)
                .show()
        );

        // Attach button — use fileLauncher bridge registered in DesignActivity
        sheetRoot.findViewById(R.id.ai_sheet_btn_attach).setOnClickListener(v -> {
            if (fileLauncher != null) {
                Intent pickIntent = new Intent(Intent.ACTION_GET_CONTENT);
                pickIntent.setType("*/*");
                pickIntent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                        "text/plain", "application/json", "text/x-java-source", "text/xml"
                });
                pickIntent.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    fileLauncher.launch(Intent.createChooser(pickIntent, "Select file to attach"));
                } catch (android.content.ActivityNotFoundException e) {
                    // Toast removed: "No file manager found."
                }
            } else {
                // Toast removed: "File picker not ready yet."
            }
        });
    }

    // ── Direct SpeechRecognizer ───────────────────────────────────────────

    private void startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            // Toast removed: "Speech recognition not available on this device."
            return;
        }
        stopListening();
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                // Toast removed: "Listening…"
            }
            @Override public void onResults(Bundle results) {
                java.util.ArrayList<String> matches =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String spoken = matches.get(0);
                    String current = inputView.getText() != null
                            ? inputView.getText().toString() : "";
                    inputView.setText(current.isEmpty() ? spoken : current + " " + spoken);
                    inputView.setSelection(inputView.getText().length());
                }
                stopListening();
            }
            @Override public void onError(int error) {
                stopListening();
                if (error != SpeechRecognizer.ERROR_NO_MATCH
                        && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    // Toast removed: "Speech error: " + error
                }
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        speechRecognizer.startListening(intent);
    }

    private void stopListening() {
        if (speechRecognizer != null) {
            try { speechRecognizer.destroy(); } catch (Exception ignored) {}
            speechRecognizer = null;
        }
    }

    private void refreshSendState() {
        if (isAgentRunning) {
            btnSend.setEnabled(true);
            btnSend.setIconResource(R.drawable.ic_mtrl_cancel);
        } else {
            boolean has = inputView.getText() != null && inputView.getText().length() > 0;
            btnSend.setEnabled(has);
            btnSend.setIconResource(R.drawable.ic_send);
        }
    }

    // ── Send / Stop ───────────────────────────────────────────────────────

    private void sendMessage() {
        if (inputView.getText() == null) return;
        String text = inputView.getText().toString().trim();
        if (text.isEmpty() || isAgentRunning) return;

        if (currentModelId == null || currentModelId.isEmpty()) {
            // Toast removed: "Please select a model first"
            showModelSelector();
            return;
        }
        String apiKey = preferences.getApiKey(currentProvider);
        if (currentProvider.requiresApiKey() && (apiKey == null || apiKey.isEmpty())) {
            // Toast removed: "No API key for " + currentProvider.getDisplayName()
            Intent i = new Intent(context, AiSettingsActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
            return;
        }

        if (currentState == STATE_HIDDEN) animateTo(STATE_HALF);
        inputView.setText("");
        emptyState.setVisibility(View.GONE);

        ChatMessage userMsg = new ChatMessage(conversationId, text);
        conversationManager.saveMessage(conversationId, userMsg);
        chatAdapter.addUserMessage(userMsg);
        scrollToBottom();

        if ("New Chat".equals(conversation.getTitle())) {
            conversation.setTitle(text.length() > 50
                    ? text.substring(0, 50) + "\u2026" : text);
            conversationManager.saveConversation(conversation);
        }

        ChatMessage placeholder = new ChatMessage(conversationId, "");
        placeholder.setStreaming(true);
        chatAdapter.addAssistantMessage(placeholder);
        scrollToBottom();

        setAgentRunning(true);

        List<ChatMessage> history    = conversationManager.getMessages(conversationId);
        List<String>      projectIds = workspace.getProjectIds();
        agentExecutor = new AgentExecutor(context, projectIds, workspaceId);
        agentExecutor.execute(history, currentModelId, currentProvider,
                preferences.getSystemPrompt(), projectIds, workspaceId, this);
    }

    private void stopAgent() {
        if (agentExecutor != null) agentExecutor.cancel();
        setAgentRunning(false);
    }

    private void setAgentRunning(boolean running) {
        isAgentRunning = running;
        inputView.setEnabled(!running);
        typingIndicator.setVisibility(running ? View.VISIBLE : View.GONE);
        refreshSendState();
    }

    // ── Model Selector ────────────────────────────────────────────────────

    private void showModelSelector() {
        List<ModelInfo> all = new ArrayList<>();
        for (AiProvider p : AiProvider.values()) {
            if (!preferences.prefs().getBoolean("provider_enabled_" + p.name(), true)) continue;
            if (p.requiresApiKey() && !preferences.hasApiKey(p)
                    && p != AiProvider.LOCAL_LLM) continue;
            List<ModelInfo> models = preferences.getCachedModels(p);
            if (models != null) all.addAll(models);
        }
        if (all.isEmpty()) {
            // Toast removed: "No models loaded. Open AI Settings first."
            context.startActivity(new Intent(context, AiSettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        RecyclerView rv = new RecyclerView(context);
        rv.setLayoutManager(new LinearLayoutManager(context));
        rv.setPadding(0, 16, 0, 32);
        ModelSelectorAdapter adapter = new ModelSelectorAdapter(model -> {
            currentProvider = model.getProvider();
            currentModelId  = model.getId();
            preferences.setSelectedModel(currentProvider, currentModelId);
            preferences.setSelectedProvider(currentProvider);
            updateModelChip();
            conversation.setModelId(currentModelId);
            conversation.setProviderName(currentProvider.name());
            conversationManager.saveConversation(conversation);
            dialog.dismiss();
        });
        adapter.setOnModelLongClickListener(model -> {
            dialog.dismiss(); showModelInfo(model); return true;
        });
        adapter.setSelectedModelId(currentModelId);
        adapter.setModels(all);
        rv.setAdapter(adapter);
        dialog.setContentView(rv);
        dialog.show();
    }

    private void showModelInfo(ModelInfo model) {
        AiProvider p   = model.getProvider();
        String     name = (model.getName() != null && !model.getName().isEmpty())
                ? model.getName() : model.getId();
        StringBuilder sb = new StringBuilder();
        sb.append("Provider: ").append(p.getSelectorLabel()).append("\n\n");
        if (model.getContextLength() > 0) {
            long ctx = model.getContextLength();
            sb.append("Context: ").append(ctx >= 1000 ? (ctx/1000)+"k" : ctx)
              .append(" tokens\n\n");
        }
        sb.append(p.getDescription());
        new MaterialAlertDialogBuilder(context)
                .setTitle(name).setMessage(sb.toString())
                .setPositiveButton("Use this model", (d, w) -> {
                    currentProvider = p; currentModelId = model.getId();
                    preferences.setSelectedModel(currentProvider, currentModelId);
                    preferences.setSelectedProvider(currentProvider);
                    updateModelChip();
                    conversation.setModelId(currentModelId);
                    conversation.setProviderName(currentProvider.name());
                    conversationManager.saveConversation(conversation);
                })
                .setNegativeButton("Cancel", null).show();
    }

    // ── AgentCallback ─────────────────────────────────────────────────────

    @Override public void onStreamingChunk(String chunk) {
        chatAdapter.updateLastAssistantMessage(chunk); scrollToBottom();
    }
    @Override public void onAssistantMessage(ChatMessage msg) {
        conversationManager.saveMessage(conversationId, msg);
        conversation.setUpdatedAt(System.currentTimeMillis());
        conversationManager.saveConversation(conversation);
        chatAdapter.replaceStreamingAssistantMessage(msg); scrollToBottom();
    }
    @Override public void onToolCallStarted(ToolCall tc) {
        chatAdapter.addToolCall(tc); scrollToBottom();
    }
    @Override public void onToolCallProgress(String id, String status,
                                             int progress, boolean indeterminate) {
        chatAdapter.updateToolCallProgress(id, status, progress, indeterminate);
        if (status != null && !status.isEmpty()) typingText.setText(status);
    }
    @Override public void onToolCallCompleted(ToolCall tc, ToolResult r) {
        chatAdapter.updateToolCallResult(tc.getId(), r); scrollToBottom();
    }
    @Override public void onToolMessage(ChatMessage msg) {
        conversationManager.saveMessage(conversationId, msg);
    }
    @Override public void onResponseComplete(ChatMessage msg) { setAgentRunning(false); }
    @Override public void onCancelled() {
        setAgentRunning(false); typingText.setText("Stopped");
    }
    @Override public void onError(String error) {
        setAgentRunning(false);
        // Build user-friendly message — shown inline in the chat, no Toast
        String displayError = (error != null && !error.isEmpty()) ? error : "An unexpected error occurred.";
        String hint = null;
        if (displayError.contains("403") || displayError.contains("rate limit") || displayError.contains("Rate Limit"))
            hint = "\uD83D\uDCA1 Tip: Switch to Groq \u221e (unlimited) or AirForce \uD83C\uDD13 \u2014 tap the model chip.";
        else if (displayError.contains("401") || displayError.contains("Invalid API Key"))
            hint = "\uD83D\uDCA1 Check your API key in AI Settings.";
        else if (displayError.contains("timeout") || displayError.contains("failed to connect")
                || displayError.contains("Unable to resolve host"))
            hint = "\uD83D\uDCA1 Check your internet connection and try again.";
        else if (displayError.contains("404") || displayError.contains("Model Not Found"))
            hint = "\uD83D\uDCA1 The selected model may be unavailable. Try refreshing models in AI Settings.";
        else if (displayError.contains("503") || displayError.contains("Service Unavailable"))
            hint = "\uD83D\uDCA1 The AI provider is temporarily overloaded. Please try again in a moment.";

        StringBuilder msgBuilder = new StringBuilder("\u26a0\ufe0f ").append(displayError);
        if (hint != null) msgBuilder.append("\n\n").append(hint);

        ChatMessage err = new ChatMessage(conversationId, msgBuilder.toString());
        conversationManager.saveMessage(conversationId, err);
        chatAdapter.replaceStreamingAssistantMessage(err);
        scrollToBottom();
        // No Toast — error is displayed inline in the chat for a cleaner UX
    }
    @Override public void onThinking(String status) { typingText.setText(status); }

    // ── OnArtifactActionListener ──────────────────────────────────────────

    @Override public void onInstallArtifact(@NonNull String artifactPath) {
        try {
            File apk = new File(artifactPath);
            Uri  uri = FileProvider.getUriForFile(
                    context, context.getPackageName() + ".provider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            context.startActivity(intent);
        } catch (Exception e) {
            // Show error inline in chat instead of Toast
            String errMsg = "\u26a0\ufe0f Cannot install APK: "
                    + (e.getMessage() != null ? e.getMessage() : "Unknown error")
                    + "\n\n\uD83D\uDCA1 Make sure you have allowed installing from unknown sources.";
            ChatMessage errChat = new ChatMessage(conversationId, errMsg);
            chatAdapter.addAssistantMessage(errChat);
            scrollToBottom();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    // ── Scroll-to-bottom FAB ──────────────────────────────────────────────
    private com.google.android.material.floatingactionbutton.FloatingActionButton fabScrollDown;

    private void setupScrollToBottomFab() {
        if (!(messagesList.getParent() instanceof android.widget.RelativeLayout)) return;
        android.widget.RelativeLayout container =
                (android.widget.RelativeLayout) messagesList.getParent();

        fabScrollDown = new com.google.android.material.floatingactionbutton.FloatingActionButton(context);
        fabScrollDown.setImageResource(android.R.drawable.arrow_down_float);
        fabScrollDown.setSize(com.google.android.material.floatingactionbutton.FloatingActionButton.SIZE_MINI);
        fabScrollDown.setVisibility(View.GONE);
        fabScrollDown.setContentDescription("Scroll to latest");

        android.widget.RelativeLayout.LayoutParams lp =
                new android.widget.RelativeLayout.LayoutParams(
                        android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
        lp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM);
        lp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END);
        int dp12 = (int) (12 * context.getResources().getDisplayMetrics().density);
        lp.bottomMargin = dp12;
        lp.rightMargin  = dp12;
        container.addView(fabScrollDown, lp);

        fabScrollDown.setOnClickListener(v -> scrollToBottom());

        messagesList.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@androidx.annotation.NonNull
                                   androidx.recyclerview.widget.RecyclerView rv, int dx, int dy) {
                androidx.recyclerview.widget.LinearLayoutManager lm =
                        (androidx.recyclerview.widget.LinearLayoutManager) rv.getLayoutManager();
                if (lm == null || fabScrollDown == null) return;
                int last  = lm.findLastCompletelyVisibleItemPosition();
                int total = chatAdapter != null ? chatAdapter.getItemCount() : 0;
                if (dy < 0) userScrolledUp = true;
                if (last >= total - 1) userScrolledUp = false;
                fabScrollDown.setVisibility((last >= total - 1) ? View.GONE : View.VISIBLE);
            }
        });
    }

    private void scrollToBottom() {
        if (!userScrolledUp && chatAdapter.getItemCount() > 0)
            messagesList.post(() ->
                    messagesList.smoothScrollToPosition(chatAdapter.getItemCount() - 1));
    }

    /** Call from DesignActivity.onResume() */
    public void onResume() {
        currentProvider = preferences.getSelectedProvider();
        String saved    = preferences.getSelectedModel(currentProvider);
        if (saved != null) currentModelId = saved;
        updateModelChip();
    }

    /** Call from DesignActivity.onDestroy() */
    public void onDestroy() {
        if (agentExecutor != null) { agentExecutor.shutdown(); agentExecutor = null; }
        stopListening();
    }
}
