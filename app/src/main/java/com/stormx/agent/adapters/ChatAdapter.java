package com.stormx.agent.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import com.stormx.agent.models.ChatMessage;
import com.stormx.agent.databinding.ItemChatMessageBinding;
import com.stormx.agent.R;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.BaseViewHolder> {
    private List<ChatMessage> messages;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public ChatAdapter(List<ChatMessage> messages, Context context) {
        this.messages = messages;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getType();
    }

    @NonNull
    @Override
    public BaseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemChatMessageBinding binding = ItemChatMessageBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ChatMessageViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull BaseViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        holder.bind(message);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public abstract class BaseViewHolder extends RecyclerView.ViewHolder {
        public BaseViewHolder(@NonNull android.view.View itemView) {
            super(itemView);
        }

        public abstract void bind(ChatMessage message);
    }

    public class ChatMessageViewHolder extends BaseViewHolder {
        private ItemChatMessageBinding binding;

        public ChatMessageViewHolder(ItemChatMessageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        @Override
        public void bind(ChatMessage message) {
            binding.messageText.setText(message.getContent());
            binding.messageTime.setText(timeFormat.format(new java.util.Date(message.getTimestamp())));

            if (message.isUserMessage()) {
                binding.userMessageLayout.setVisibility(android.view.View.VISIBLE);
                binding.aiMessageLayout.setVisibility(android.view.View.GONE);
                binding.userMessageText.setText(message.getContent());
                binding.userMessageTime.setText(timeFormat.format(new java.util.Date(message.getTimestamp())));
            } else {
                binding.userMessageLayout.setVisibility(android.view.View.GONE);
                binding.aiMessageLayout.setVisibility(android.view.View.VISIBLE);
                binding.aiMessageText.setText(message.getContent());
                binding.aiMessageTime.setText(timeFormat.format(new java.util.Date(message.getTimestamp())));
            }
        }
    }
}