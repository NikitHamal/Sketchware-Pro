package com.stormx.agent.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import com.stormx.agent.model.Conversation;
import com.stormx.agent.databinding.ItemConversationBinding;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder> {
    private final List<Conversation> conversations;
    private final OnConversationClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());

    public interface OnConversationClickListener {
        void onConversationClick(Conversation conversation);
        void onConversationLongClick(Conversation conversation);
    }

    public ConversationAdapter(List<Conversation> conversations, OnConversationClickListener listener) {
        this.conversations = conversations;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemConversationBinding binding = ItemConversationBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ConversationViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder holder, int position) {
        holder.bind(conversations.get(position));
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    class ConversationViewHolder extends RecyclerView.ViewHolder {
        private final ItemConversationBinding binding;
        ConversationViewHolder(ItemConversationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
        void bind(Conversation conversation) {
            binding.conversationTitle.setText(conversation.getTitle());
            binding.conversationLastMessage.setText(conversation.getLastMessage());
            binding.conversationModel.setText(conversation.getModel());
            if (conversation.getLastMessageTime() != null) {
                binding.conversationTime.setText(dateFormat.format(conversation.getLastMessageTime()));
            }
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onConversationClick(conversation);
            });
            binding.getRoot().setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onConversationLongClick(conversation);
                    return true;
                }
                return false;
            });
        }
    }
}