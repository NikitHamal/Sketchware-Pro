package pro.sketchware.ai.adapters;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.noties.markwon.Markwon;
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

    private static final int MAX_TOOL_ARG_LENGTH = 200;

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
        items.add(ChatItem.assistantMessage(msg));
        notifyItemInserted(items.size() - 1);
    }

    public void updateLastAssistantMessage(@NonNull String chunk) {
        for (int i = items.size() - 1; i >= 0; i--) {
            ChatItem item = items.get(i);
            if (item.type == TYPE_ASSISTANT && item.message != null) {
                item.message.appendContent(chunk);
                notifyItemChanged(i);
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
                items.add(ChatItem.assistantMessage(msg));
                List<ToolCall> toolCalls = msg.getToolCalls();
                if (toolCalls != null) {
                    for (ToolCall tc : toolCalls) {
                        items.add(ChatItem.toolCall(tc));
                    }
                }
            } else if ("tool".equals(role)) {
                String toolCallId = msg.getToolCallId();
                if (toolCallId != null) {
                    ToolResult result = new ToolResult(
                            toolCallId, true, msg.getContent(), null);
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

    private static String formatToolArguments(@Nullable String arguments) {
        if (TextUtils.isEmpty(arguments)) {
            return "";
        }
        String trimmed = arguments.trim();
        if (trimmed.length() > MAX_TOOL_ARG_LENGTH) {
            return trimmed.substring(0, MAX_TOOL_ARG_LENGTH) + "...";
        }
        return trimmed;
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
        }
    }

    static class AssistantViewHolder extends RecyclerView.ViewHolder {
        private final ItemChatMessageAssistantBinding binding;

        AssistantViewHolder(@NonNull ItemChatMessageAssistantBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull ChatItem item, @NonNull Markwon markwon) {
            String content = item.message != null ? item.message.getContent() : "";
            if (TextUtils.isEmpty(content)) {
                binding.messageContent.setText("");
            } else {
                markwon.setMarkdown(binding.messageContent, content);
            }
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
                binding.toolArguments.setText("");
            }

            ToolResult result = item.toolResult;
            if (result != null) {
                binding.toolStatusIcon.setVisibility(View.VISIBLE);
                binding.toolResult.setVisibility(View.VISIBLE);
                if (result.isSuccess()) {
                    String output = result.getOutput();
                    binding.toolResult.setText(output != null ? output : "");
                } else {
                    String error = result.getError();
                    binding.toolResult.setText(error != null ? "Error: " + error : "Error");
                }
            } else {
                binding.toolStatusIcon.setVisibility(View.INVISIBLE);
                binding.toolResult.setVisibility(View.GONE);
            }
        }
    }
}
