package pro.sketchware.ai.adapters;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.noties.markwon.Markwon;
import pro.sketchware.R;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.ToolCall;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.databinding.ItemChatMessageAssistantBinding;
import pro.sketchware.databinding.ItemChatMessageUserBinding;
import pro.sketchware.databinding.ItemChatToolCallBinding;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnArtifactActionListener {
        void onInstallArtifact(@NonNull String artifactPath);
    }

    static final int TYPE_USER = 0;
    static final int TYPE_ASSISTANT = 1;

    private static final int MAX_TOOL_PREVIEW_LENGTH = 220;
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("h:mm a", Locale.getDefault());

    private static class ToolUiState {
        @NonNull final ToolCall toolCall;
        @Nullable ToolResult toolResult;
        @Nullable String status;
        int progress = -1;
        boolean indeterminate = true;

        ToolUiState(@NonNull ToolCall toolCall) {
            this.toolCall = toolCall;
        }
    }

    public static class ChatItem {
        final int type;
        @Nullable ChatMessage message;
        @NonNull final List<ToolUiState> toolStates;

        private ChatItem(int type, @Nullable ChatMessage message) {
            this.type = type;
            this.message = message;
            this.toolStates = new ArrayList<>();
        }

        public static ChatItem userMessage(@NonNull ChatMessage msg) {
            return new ChatItem(TYPE_USER, msg);
        }

        public static ChatItem assistantMessage(@NonNull ChatMessage msg) {
            ChatItem item = new ChatItem(TYPE_ASSISTANT, msg);
            List<ToolCall> toolCalls = msg.getToolCalls();
            if (toolCalls != null) {
                for (ToolCall toolCall : toolCalls) {
                    item.toolStates.add(new ToolUiState(toolCall));
                }
            }
            return item;
        }
    }

    private final List<ChatItem> items = new ArrayList<>();
    @Nullable private OnArtifactActionListener artifactActionListener;
    private Markwon markwon;

    public void setArtifactActionListener(@Nullable OnArtifactActionListener artifactActionListener) {
        this.artifactActionListener = artifactActionListener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (markwon == null) {
            markwon = Markwon.create(parent.getContext());
        }
        if (viewType == TYPE_USER) {
            return new UserViewHolder(ItemChatMessageUserBinding.inflate(inflater, parent, false));
        }
        return new AssistantViewHolder(ItemChatMessageAssistantBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatItem item = items.get(position);
        if (item.type == TYPE_USER) {
            ((UserViewHolder) holder).bind(item);
        } else {
            ((AssistantViewHolder) holder).bind(item, markwon, artifactActionListener);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    public void addUserMessage(@NonNull ChatMessage msg) {
        items.add(ChatItem.userMessage(msg));
        notifyItemInserted(items.size() - 1);
    }

    public void addAssistantMessage(@NonNull ChatMessage msg) {
        if (!shouldRenderAssistantMessage(ChatItem.assistantMessage(msg))) {
            return;
        }
        items.add(ChatItem.assistantMessage(msg));
        notifyItemInserted(items.size() - 1);
    }

    public void updateLastAssistantMessage(@NonNull String chunk) {
        ChatItem item = findLastAssistantItem();
        if (item != null && item.message != null && item.message.isStreaming()) {
            item.message.appendContent(chunk);
            notifyItemChanged(items.indexOf(item));
        }
    }

    public void replaceStreamingAssistantMessage(@NonNull ChatMessage finalMessage) {
        int streamingIndex = findStreamingAssistantIndex();
        ChatItem finalItem = ChatItem.assistantMessage(finalMessage);
        if (streamingIndex >= 0) {
            ChatItem existing = items.get(streamingIndex);
            mergeToolStates(existing, finalItem);
            if (shouldRenderAssistantMessage(finalItem)) {
                items.set(streamingIndex, finalItem);
                notifyItemChanged(streamingIndex);
            } else {
                items.remove(streamingIndex);
                notifyItemRemoved(streamingIndex);
            }
        } else if (shouldRenderAssistantMessage(finalItem)) {
            items.add(finalItem);
            notifyItemInserted(items.size() - 1);
        }
    }

    public void addToolCall(@NonNull ToolCall tc) {
        ChatItem item = findLastAssistantItem();
        if (item == null) {
            ChatMessage placeholder = ChatMessage.assistantMessage("", null);
            placeholder.setStreaming(true);
            item = ChatItem.assistantMessage(placeholder);
            items.add(item);
            notifyItemInserted(items.size() - 1);
        }
        if (findToolState(item, tc.getId()) == null) {
            item.toolStates.add(new ToolUiState(tc));
            notifyItemChanged(items.indexOf(item));
        }
    }

    public void updateToolCallProgress(@NonNull String toolCallId, @Nullable String status,
                                       int progress, boolean indeterminate) {
        int index = findAssistantIndexForTool(toolCallId);
        if (index < 0) return;
        ToolUiState state = findToolState(items.get(index), toolCallId);
        if (state == null) return;
        state.status = status;
        state.progress = progress;
        state.indeterminate = indeterminate;
        notifyItemChanged(index);
    }

    public void updateToolCallResult(@NonNull String toolCallId, @NonNull ToolResult result) {
        int index = findAssistantIndexForTool(toolCallId);
        if (index < 0) return;
        ToolUiState state = findToolState(items.get(index), toolCallId);
        if (state == null) return;
        state.toolResult = result;
        state.status = result.isSuccess() ? "Completed" : "Failed";
        state.progress = result.isSuccess() ? 100 : -1;
        state.indeterminate = false;
        notifyItemChanged(index);
    }

    public void setMessages(@NonNull List<ChatMessage> messages) {
        items.clear();
        for (ChatMessage msg : messages) {
            String role = msg.getRole();
            if ("user".equals(role)) {
                items.add(ChatItem.userMessage(msg));
            } else if ("assistant".equals(role)) {
                ChatItem assistantItem = ChatItem.assistantMessage(msg);
                if (shouldRenderAssistantMessage(assistantItem)) {
                    items.add(assistantItem);
                }
            } else if ("tool".equals(role)) {
                applyPersistedToolResult(msg);
            }
        }
        notifyDataSetChanged();
    }

    private void applyPersistedToolResult(@NonNull ChatMessage msg) {
        String toolCallId = msg.getToolCallId();
        if (toolCallId == null) return;
        int assistantIndex = findAssistantIndexForTool(toolCallId);
        if (assistantIndex < 0) return;

        String content = msg.getContent() != null ? msg.getContent() : "";
        ToolResult result = content.startsWith("Error:")
                ? ToolResult.failure(toolCallId, content.substring("Error:".length()).trim())
                : ToolResult.success(toolCallId, content);
        ToolUiState state = findToolState(items.get(assistantIndex), toolCallId);
        if (state != null) {
            state.toolResult = result;
            state.status = result.isSuccess() ? "Completed" : "Failed";
            state.progress = result.isSuccess() ? 100 : -1;
            state.indeterminate = false;
        }
    }

    private void mergeToolStates(@NonNull ChatItem existing, @NonNull ChatItem replacement) {
        Map<String, ToolUiState> previousById = new LinkedHashMap<>();
        for (ToolUiState state : existing.toolStates) {
            previousById.put(state.toolCall.getId(), state);
        }

        if (replacement.toolStates.isEmpty() && !existing.toolStates.isEmpty()) {
            replacement.toolStates.addAll(existing.toolStates);
            return;
        }

        for (int i = 0; i < replacement.toolStates.size(); i++) {
            ToolUiState replacementState = replacement.toolStates.get(i);
            ToolUiState previousState = previousById.get(replacementState.toolCall.getId());
            if (previousState != null) {
                replacementState.toolResult = previousState.toolResult;
                replacementState.status = previousState.status;
                replacementState.progress = previousState.progress;
                replacementState.indeterminate = previousState.indeterminate;
            }
        }
    }

    private boolean shouldRenderAssistantMessage(@Nullable ChatItem item) {
        if (item == null || item.message == null) {
            return false;
        }
        return item.message.hasVisibleAssistantContent()
                || item.message.isStreaming()
                || !item.toolStates.isEmpty();
    }

    @Nullable
    private ChatItem findLastAssistantItem() {
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).type == TYPE_ASSISTANT) {
                return items.get(i);
            }
        }
        return null;
    }

    private int findStreamingAssistantIndex() {
        for (int i = items.size() - 1; i >= 0; i--) {
            ChatItem item = items.get(i);
            if (item.type == TYPE_ASSISTANT && item.message != null && item.message.isStreaming()) {
                return i;
            }
        }
        return -1;
    }

    private int findAssistantIndexForTool(@NonNull String toolCallId) {
        for (int i = items.size() - 1; i >= 0; i--) {
            ChatItem item = items.get(i);
            if (item.type == TYPE_ASSISTANT && findToolState(item, toolCallId) != null) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    private ToolUiState findToolState(@NonNull ChatItem item, @NonNull String toolCallId) {
        for (ToolUiState state : item.toolStates) {
            if (toolCallId.equals(state.toolCall.getId())) {
                return state;
            }
        }
        return null;
    }

    private static String formatTimestamp(long timestamp) {
        return TIME_FORMAT.format(new Date(timestamp));
    }

    private static String summarizeToolArguments(@Nullable String arguments) {
        return summarizeStructuredPayload(arguments, true);
    }

    private static String summarizeToolResult(@Nullable ToolResult result) {
        if (result == null) {
            return "";
        }
        return summarizeStructuredPayload(result.isSuccess() ? result.getOutput() : result.getError(), false);
    }

    private static String summarizeStructuredPayload(@Nullable String payload, boolean compact) {
        if (TextUtils.isEmpty(payload)) {
            return compact ? "No details" : "";
        }
        try {
            JsonElement element = JsonParser.parseString(payload.trim());
            if (element.isJsonObject()) {
                return summarizeObject(element.getAsJsonObject(), compact);
            }
            if (element.isJsonArray()) {
                return summarizeArray(element.getAsJsonArray(), compact);
            }
        } catch (Exception ignored) {
        }
        String normalized = payload.trim().replace("\n", " ").replaceAll("\\s+", " ");
        if (normalized.length() > MAX_TOOL_PREVIEW_LENGTH) {
            normalized = normalized.substring(0, MAX_TOOL_PREVIEW_LENGTH) + "…";
        }
        return normalized;
    }

    private static String summarizeObject(@NonNull JsonObject object, boolean compact) {
        List<String> lines = new ArrayList<>();
        addLineIfPresent(lines, object, "message");
        addLineIfPresent(lines, object, "status");
        addLineIfPresent(lines, object, "sc_id");
        addLineIfPresent(lines, object, "file_path");
        addLineIfPresent(lines, object, "artifact_path");
        addLineIfPresent(lines, object, "compile_log_path");
        addLineIfPresent(lines, object, "dependency");
        addLineIfPresent(lines, object, "library_name");
        addLineIfPresent(lines, object, "root");

        if (object.has("attached_libraries") && object.get("attached_libraries").isJsonArray()) {
            lines.add("libraries: " + summarizeArray(object.getAsJsonArray("attached_libraries"), true));
        }
        if (object.has("content") && object.get("content").isJsonPrimitive()) {
            String preview = object.get("content").getAsString().trim().replaceAll("\\s+", " ");
            if (preview.length() > MAX_TOOL_PREVIEW_LENGTH) {
                preview = preview.substring(0, MAX_TOOL_PREVIEW_LENGTH) + "…";
            }
            lines.add("preview: " + preview);
        }

        if (lines.isEmpty()) {
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (lines.size() >= (compact ? 4 : 6)) break;
                if (entry.getValue().isJsonPrimitive()) {
                    lines.add(entry.getKey() + ": " + entry.getValue().getAsString());
                }
            }
        }
        return TextUtils.join("\n", lines);
    }

    private static void addLineIfPresent(@NonNull List<String> lines, @NonNull JsonObject object, @NonNull String key) {
        if (object.has(key) && object.get(key).isJsonPrimitive()) {
            lines.add(key.replace('_', ' ') + ": " + object.get(key).getAsString());
        }
    }

    private static String summarizeArray(@NonNull JsonArray array, boolean compact) {
        List<String> previews = new ArrayList<>();
        int limit = compact ? 3 : 5;
        for (int i = 0; i < array.size() && i < limit; i++) {
            JsonElement element = array.get(i);
            if (element.isJsonPrimitive()) {
                previews.add(element.getAsString());
            } else if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                if (object.has("name") && object.get("name").isJsonPrimitive()) {
                    previews.add(object.get("name").getAsString());
                } else {
                    previews.add("item " + (i + 1));
                }
            }
        }
        if (array.size() > limit) {
            previews.add("+" + (array.size() - limit) + " more");
        }
        return previews.isEmpty() ? "No items" : TextUtils.join(", ", previews);
    }

    @Nullable
    private static String extractInstallableArtifactPath(@Nullable ToolResult result) {
        if (result == null || !result.isSuccess() || TextUtils.isEmpty(result.getOutput())) {
            return null;
        }
        try {
            JsonObject object = JsonParser.parseString(result.getOutput()).getAsJsonObject();
            boolean installable = object.has("installable")
                    && object.get("installable").isJsonPrimitive()
                    && object.get("installable").getAsBoolean();
            if (!installable) return null;
            if (!object.has("artifact_path") || !object.get("artifact_path").isJsonPrimitive()) {
                return null;
            }
            String path = object.get("artifact_path").getAsString();
            return path.endsWith(".apk") ? path : null;
        } catch (Exception e) {
            return null;
        }
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        private final ItemChatMessageUserBinding binding;

        UserViewHolder(@NonNull ItemChatMessageUserBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull ChatItem item) {
            String content = item.message != null ? item.message.getContent() : "";
            binding.messageContent.setText(content != null ? content : "");
            binding.messageMeta.setText(item.message != null ? formatTimestamp(item.message.getTimestamp()) : "");
        }
    }

    static class AssistantViewHolder extends RecyclerView.ViewHolder {
        private final ItemChatMessageAssistantBinding binding;

        AssistantViewHolder(@NonNull ItemChatMessageAssistantBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull ChatItem item, @NonNull Markwon markwon,
                  @Nullable OnArtifactActionListener artifactActionListener) {
            ChatMessage message = item.message;
            String content = message != null ? message.getContent() : "";
            binding.messageMeta.setText(message != null ? formatTimestamp(message.getTimestamp()) : "");
            binding.streamingBadge.setVisibility(message != null && message.isStreaming() ? View.VISIBLE : View.GONE);

            boolean hasMessageContent = !TextUtils.isEmpty(content);
            binding.messageContent.setVisibility(hasMessageContent ? View.VISIBLE : View.GONE);
            if (hasMessageContent) {
                markwon.setMarkdown(binding.messageContent, content);
            }

            binding.toolsContainer.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(binding.getRoot().getContext());
            for (ToolUiState state : item.toolStates) {
                ItemChatToolCallBinding toolBinding = ItemChatToolCallBinding.inflate(inflater, binding.toolsContainer, false);
                bindToolCard(toolBinding, state, artifactActionListener);
                binding.toolsContainer.addView(toolBinding.getRoot());
            }
            binding.toolsContainer.setVisibility(item.toolStates.isEmpty() ? View.GONE : View.VISIBLE);
        }

        private void bindToolCard(@NonNull ItemChatToolCallBinding binding,
                                  @NonNull ToolUiState state,
                                  @Nullable OnArtifactActionListener artifactActionListener) {
            ToolCall toolCall = state.toolCall;
            binding.toolName.setText(toolCall.getName() != null ? toolCall.getName() : "Tool");
            binding.toolArguments.setText(summarizeToolArguments(toolCall.getArguments()));

            ToolResult result = state.toolResult;
            if (result == null) {
                binding.toolStatusIcon.setImageResource(R.drawable.ic_mtrl_sync);
                binding.toolStatus.setText(!TextUtils.isEmpty(state.status) ? state.status : "Running");
                binding.toolResult.setVisibility(View.GONE);
            } else if (result.isSuccess()) {
                binding.toolStatusIcon.setImageResource(R.drawable.ic_mtrl_check);
                binding.toolStatus.setText(!TextUtils.isEmpty(state.status) ? state.status : "Completed");
                String summary = summarizeToolResult(result);
                binding.toolResult.setVisibility(TextUtils.isEmpty(summary) ? View.GONE : View.VISIBLE);
                binding.toolResult.setText(summary);
            } else {
                binding.toolStatusIcon.setImageResource(R.drawable.ic_mtrl_warning);
                binding.toolStatus.setText(!TextUtils.isEmpty(state.status) ? state.status : "Failed");
                binding.toolResult.setVisibility(View.VISIBLE);
                binding.toolResult.setText(summarizeToolResult(result));
            }

            LinearProgressIndicator progress = binding.toolProgress;
            if (result == null || state.indeterminate || (state.progress >= 0 && state.progress < 100)) {
                progress.setVisibility(View.VISIBLE);
                progress.setIndeterminate(result == null || state.indeterminate || state.progress < 0);
                if (!progress.isIndeterminate() && state.progress >= 0) {
                    progress.setProgress(state.progress);
                }
            } else {
                progress.setVisibility(View.GONE);
            }

            MaterialButton installButton = binding.btnInstallArtifact;
            String artifactPath = extractInstallableArtifactPath(result);
            if (!TextUtils.isEmpty(artifactPath) && artifactActionListener != null) {
                installButton.setVisibility(View.VISIBLE);
                installButton.setOnClickListener(v -> artifactActionListener.onInstallArtifact(artifactPath));
            } else {
                installButton.setVisibility(View.GONE);
                installButton.setOnClickListener(null);
            }
        }
    }
}
