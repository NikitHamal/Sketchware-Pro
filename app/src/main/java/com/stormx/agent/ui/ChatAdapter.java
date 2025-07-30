package com.stormx.agent.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.stormx.agent.databinding.ItemChatMessageBinding;
import com.stormx.agent.model.ChatMessage;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageViewHolder>{
    private final List<ChatMessage> messages;
    public ChatAdapter(List<ChatMessage> messages){this.messages=messages;}

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemChatMessageBinding binding = ItemChatMessageBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false);
        return new MessageViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        holder.bind(messages.get(position));
    }

    @Override
    public int getItemCount(){return messages.size();}

    static class MessageViewHolder extends RecyclerView.ViewHolder{
        private final ItemChatMessageBinding binding;
        MessageViewHolder(ItemChatMessageBinding binding){super(binding.getRoot());this.binding=binding;}
        void bind(ChatMessage msg){
            if(msg.getType()==ChatMessage.TYPE_USER){
                binding.userMessage.setText(msg.getContent());
                binding.userMessage.setVisibility(android.view.View.VISIBLE);
                binding.aiMessage.setVisibility(android.view.View.GONE);
            }else{
                binding.aiMessage.setText(msg.getContent());
                binding.aiMessage.setVisibility(android.view.View.VISIBLE);
                binding.userMessage.setVisibility(android.view.View.GONE);
            }
        }
    }
}