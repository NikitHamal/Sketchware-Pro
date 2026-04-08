package pro.sketchware.ai.adapters;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.noties.markwon.Markwon;
import pro.sketchware.R;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.ToolCall;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.databinding.ItemChatMessageAssistantBinding;
import pro.sketchware.databinding.ItemChatMessageUserBinding;
import pro.sketchware.databinding.ItemChatToolCallBinding;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    static final int TYPE_USER = 0;
    static final int TYPE_ASSISTANT = 1;
    static final int TYPE_TOOL_CALL = 2;

    private static final int MAX_TOOL_ARG_LENGTH = 260;
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("h:mm a", Locale.getDefault());

    public static class ChatItem {
        final int type;
        @Nullable final ChatMessage message;
        @Nullable final ToolCall toolCall;
        @Nullable ToolResult toolResult;

        private ChatItem(int type, @Nullable ChatMessage message,
                         @Nullable ToolCall toolCall, @Nullable ToolResult toolResult) {
            this.type = type;
            this.message = message;
            this.toolCall = toolCall;
            this.toolResult = toolResult;
        }

        public static ChatItem userMessage(@NonNull ChatMessage msg) {
            return new ChatItem(TYPE_USER, msg, null, null);
        }

        public static ChatItem assistantMessage(@NonNull ChatMessage msg) {
            return new ChatItem(TYPE_ASSISTANT, msg, null, null);
        }

        public static ChatItem toolCall(@NonNull ToolCall tc) {
            return new ChatItem(TYPE_TOOL_CALL, null, tc, null);
        }
    }

    private final List<ChatItem> items = new ArrayList<>();
    private Markwon markwon;

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (markwon == null) {
            markwon = Markwon.create(parent.getContext());
        }
        switch (viewType) {
            case TYPE_USER: {
                ItemChatMessageUserBinding binding =
                        ItemChatMessageUserBinding.inflate(inflater, parent, false);
                return new UserViewHolder(binding);
            }
            case TYPE_ASSISTANT: {
                ItemChatMessageAssistantBinding binding =
                        ItemChatMessageAssistantBinding.inflate(inflater, parent, false);
                return new AssistantViewHolder(binding);
            }
            case TYPE_TOOL_CALL: {
                ItemChatToolCallBinding binding =
                        ItemChatToolCallBinding.inflate(inflater, parent, false);
                return new ToolCallViewHolder(binding);
            }
            default:
                throw new IllegalArgumentException("Unknown view type: " + viewType);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatItem item = items.get(position);
        switch (item.type) {
            case TYPE_USER:
                ((UserViewHolder) holder).bind(item);
                break;
            case TYPE_ASSISTANT:
                ((AssistantViewHolder) holder).bind(item, markwon);
                break;
            case TYPE_TOOL_CALL:
                ((ToolCallViewHolder) holder).bind(item);
                break;
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
        if (!shouldRenderAssistantMessage(msg)) {
            return;
        }
        items.add(ChatItem.assistantMessage(msg));
        notifyItemInserted(items.size() - 1);
    }

    public void updateLastAssistantMessage(@NonNull String chunk) {
        for (int i = items.size() - 1; i >= 0; i--) {
            ChatItem item = items.get(i);
            if (item.type == TYPE_ASSISTANT && item.message != null && item.message.isStreaming()) {
                item.message.appendContent(chunk);
                notifyItemChanged(i);
                return;
            }
        }
    }

    public void replaceStreamingAssistantMessage(@NonNull ChatMessage finalMessage) {
        int streamingIndex = -1;
        for (int i = items.size() - 1; i >= 0; i--) {
            ChatItem item = items.get(i);
            if (item.type == TYPE_ASSISTANT && item.message != null && item.message.isStreaming()) {
                streamingIndex = i;
                break;
            }
        }

        boolean renderFinal = shouldRenderAssistantMessage(finalMessage);
        if (streamingIndex >= 0) {
            if (renderFinal) {
                items.set(streamingIndex, ChatItem.assistantMessage(finalMessage));
                notifyItemChanged(streamingIndex);
            } else {
                items.remove(streamingIndex);
                notifyItemRemoved(streamingIndex);
            }
        } else if (renderFinal) {
            addAssistantMessage(finalMessage);
        }
    }

    public void removeLastStreamingAssistantMessage() {
        for (int i = items.size() - 1; i >= 0; i--) {
            ChatItem item = items.get(i);
            if (item.type == TYPE_ASSISTANT && item.message != null && item.message.isStreaming()) {
                items.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }

    public void addToolCall(@NonNull ToolCall tc) {
        items.add(ChatItem.toolCall(tc));
        notifyItemInserted(items.size() - 1);
    }

    public void updateToolCallResult(@NonNull String toolCallId, @NonNull ToolResult result) {
        for (int i = 0; i < items.size(); i++) {
            ChatItem item = items.get(i);
            if (item.type == TYPE_TOOL_CALL
                    && item.toolCall != null
                    && toolCallId.equals(item.toolCall.getId())) {
                item.toolResult = result;
                notifyItemChanged(i);
                return;
            }
        }
    }

    public void setMessages(@NonNull List<ChatMessage> messages) {
        items.clear();
        for (ChatMessage msg : messages) {
            String role = msg.getRole();
            if ("user".equals(role)) {
                items.add(ChatItem.userMessage(msg));
            } else if ("assistant".equals(role)) {
                if (shouldRenderAssistantMessage(msg)) {
                    items.add(ChatItem.assistantMessage(msg));
                }
                List<ToolCall> toolCalls = msg.getToolCalls();
                if (toolCalls != null) {
                    for (ToolCall tc : toolCalls) {
                        items.add(ChatItem.toolCall(tc));
                    }
                }
            } else if ("tool".equals(role)) {
                String toolCallId = msg.getToolCallId();
                if (toolCallId != null) {
                    String content = msg.getContent() != null ? msg.getContent() : "";
                    ToolResult result = content.startsWith("Error:")
                            ? new ToolResult(toolCallId, false, null, content.substring("Error:".length()).trim())
                            : new ToolResult(toolCallId, true, content, null);
                    for (int i = items.size() - 1; i >= 0; i--) {
                        ChatItem item = items.get(i);
                        if (item.type == TYPE_TOOL_CALL
                                && item.toolCall != null
                                && toolCallId.equals(item.toolCall.getId())) {
                            item.toolResult = result;
                            break;
                        }
                    }
                }
            }
        }
        notifyDataSetChanged();
    }

    private boolean shouldRenderAssistantMessage(@Nullable ChatMessage message) {
        return message != null && (message.hasVisibleAssistantContent() || message.isStreaming());
    }

    private static String formatToolArguments(@Nullable String arguments) {
        if (TextUtils.isEmpty(arguments)) {
            return "No arguments";
        }
        String trimmed = arguments.trim();
        if (trimmed.length() > MAX_TOOL_ARG_LENGTH) {
            return trimmed.substring(0, MAX_TOOL_ARG_LENGTH) + "...";
        }
        return trimmed;
    }

    private static String formatTimestamp(long timestamp) {
        return TIME_FORMAT.format(new Date(timestamp));
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

        void bind(@NonNull ChatItem item, @NonNull Markwon markwon) {
            ChatMessage message = item.message;
            String content = message != null ? message.getContent() : "";
            binding.messageMeta.setText(message != null ? formatTimestamp(message.getTimestamp()) : "");
            if (TextUtils.isEmpty(content)) {
                binding.messageContent.setText("");
            } else {
                markwon.setMarkdown(binding.messageContent, content);
            }
            binding.streamingBadge.setVisibility(message != null && message.isStreaming() ? View.VISIBLE : View.GONE);
        }
    }

    static class ToolCallViewHolder extends RecyclerView.ViewHolder {
        private final ItemChatToolCallBinding binding;

        ToolCallViewHolder(@NonNull ItemChatToolCallBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull ChatItem item) {
            ToolCall tc = item.toolCall;
            if (tc != null) {
                binding.toolName.setText(tc.getName() != null ? tc.getName() : "Unknown tool");
                binding.toolArguments.setText(formatToolArguments(tc.getArguments()));
            } else {
                binding.toolName.setText("Unknown tool");
                binding.toolArguments.setText("No arguments");
            }

            ToolResult result = item.toolResult;
            if (result != null) {
                binding.toolStatusIcon.setVisibility(View.VISIBLE);
                binding.toolResult.setVisibility(View.VISIBLE);
                binding.toolStatus.setVisibility(View.VISIBLE);
                if (result.isSuccess()) {
                    binding.toolStatusIcon.setImageResource(R.drawable.ic_mtrl_check);
                    binding.toolStatus.setText("Completed");
                    binding.toolResult.setText(result.getOutput() != null ? result.getOutput() : "Completed");
                } else {
                    binding.toolStatusIcon.setImageResource(R.drawable.ic_mtrl_warning);
                    binding.toolStatus.setText("Failed");
                    binding.toolResult.setText(result.getError() != null ? result.getError() : "Tool failed");
                }
            } else {
                binding.toolStatusIcon.setVisibility(View.INVISIBLE);
                binding.toolStatus.setVisibility(View.VISIBLE);
                binding.toolStatus.setText("Running");
                binding.toolResult.setVisibility(View.GONE);
            }
        }
    }
}
