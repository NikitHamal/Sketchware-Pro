package pro.sketchware.activities.ai.chat.adapters;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import pro.sketchware.activities.ai.chat.models.ChatMessage;
import pro.sketchware.databinding.ItemChatMessageBinding;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatMessageViewHolder> {
    private static final String TAG = "ChatAdapter";
    private List<ChatMessage> messages;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public ChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
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
                
                binding.aiMessage.setText(message.getContent());
                binding.aiMessageTime.setText(timeFormat.format(new Date(message.getTimestamp())));
                
                Log.d(TAG, "Showing AI message: " + message.getContent());
            }
        }
    }
}