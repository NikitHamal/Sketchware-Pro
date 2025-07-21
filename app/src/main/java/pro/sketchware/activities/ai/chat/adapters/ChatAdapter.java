package pro.sketchware.activities.ai.chat.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.noties.markwon.Markwon;
import pro.sketchware.activities.ai.chat.models.ChatMessage;
import pro.sketchware.databinding.ItemChatMessageBinding;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatMessageViewHolder> {
    private static final String TAG = "ChatAdapter";
    private List<ChatMessage> messages;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private Markwon markwon;

    public ChatAdapter(List<ChatMessage> messages, Context context) {
        this.messages = messages;
        this.markwon = Markwon.create(context);
    }

    @NonNull
    @Override
    public ChatMessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemChatMessageBinding binding = ItemChatMessageBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ChatMessageViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatMessageViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        Log.d(TAG, "Binding message at position " + position + ": " + message.getContent() + " (type: " + message.getType() + ")");
        holder.bind(message);
    }

    @Override
    public int getItemCount() {
        Log.d(TAG, "getItemCount: " + messages.size());
        return messages.size();
    }

    public void updateLastMessage(String content) {
        if (!messages.isEmpty()) {
            ChatMessage lastMessage = messages.get(messages.size() - 1);
            if (lastMessage.getType() == ChatMessage.TYPE_AI) {
                lastMessage.setContent(content);
                notifyItemChanged(messages.size() - 1);
            }
        }
    }

    public class ChatMessageViewHolder extends RecyclerView.ViewHolder {
        private ItemChatMessageBinding binding;

        public ChatMessageViewHolder(ItemChatMessageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(ChatMessage message) {
            Log.d(TAG, "Binding message: " + message.getContent() + ", isUser: " + message.isUserMessage());
            
            if (message.isUserMessage()) {
                binding.userMessageLayout.setVisibility(android.view.View.VISIBLE);
                binding.aiMessageLayout.setVisibility(android.view.View.GONE);
                
                binding.userMessage.setText(message.getContent());
                binding.userMessageTime.setText(timeFormat.format(new Date(message.getTimestamp())));
                
                Log.d(TAG, "Showing user message: " + message.getContent());
            } else {
                binding.userMessageLayout.setVisibility(android.view.View.GONE);
                binding.aiMessageLayout.setVisibility(android.view.View.VISIBLE);
                
                // Use Markwon to render markdown in AI messages
                markwon.setMarkdown(binding.aiMessage, message.getContent());
                binding.aiMessageTime.setText(timeFormat.format(new Date(message.getTimestamp())));
                
                Log.d(TAG, "Showing AI message: " + message.getContent());
            }
        }
    }
}