package pro.sketchware.activities.main.fragments.ai.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import pro.sketchware.activities.main.fragments.ai.models.Conversation;
import pro.sketchware.databinding.ItemConversationBinding;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder> {
    private List<Conversation> conversations;
    private OnConversationClickListener listener;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());

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
        Conversation conversation = conversations.get(position);
        holder.bind(conversation);
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    public class ConversationViewHolder extends RecyclerView.ViewHolder {
        private ItemConversationBinding binding;

        public ConversationViewHolder(ItemConversationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Conversation conversation) {
            binding.conversationTitle.setText(conversation.getTitle());
            binding.conversationLastMessage.setText(conversation.getLastMessage());
            binding.conversationModel.setText(conversation.getModel());
            
            if (conversation.getLastMessageTime() != null) {
                binding.conversationTime.setText(dateFormat.format(conversation.getLastMessageTime()));
            }

                            binding.getRoot().setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onConversationClick(conversation);
                    }
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