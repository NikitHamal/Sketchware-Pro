package pro.sketchware.ai.adapters;

import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.ai.models.Conversation;
import pro.sketchware.databinding.ItemConversationBinding;

public class ConversationsAdapter extends RecyclerView.Adapter<ConversationsAdapter.ViewHolder> {

    public interface OnConversationClickListener {
        void onConversationClick(Conversation conversation);
        void onConversationLongClick(Conversation conversation);
    }

    private final List<Conversation> conversations = new ArrayList<>();
    private final OnConversationClickListener listener;

    public ConversationsAdapter(@NonNull OnConversationClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemConversationBinding binding = ItemConversationBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Conversation conversation = conversations.get(position);
        holder.bind(conversation);
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    public void setConversations(@NonNull List<Conversation> newConversations) {
        conversations.clear();
        conversations.addAll(newConversations);
        notifyDataSetChanged();
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemConversationBinding binding;

        ViewHolder(@NonNull ItemConversationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull Conversation conversation) {
            binding.conversationTitle.setText(
                    TextUtils.isEmpty(conversation.getTitle())
                            ? "New Conversation"
                            : conversation.getTitle());

            String modelId = conversation.getModelId();
            binding.conversationModel.setText(
                    TextUtils.isEmpty(modelId) ? "No model selected" : modelId);

            long updatedAt = conversation.getUpdatedAt();
            if (updatedAt > 0) {
                CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(
                        updatedAt,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE);
                binding.conversationTime.setText(relativeTime);
            } else {
                binding.conversationTime.setText("");
            }

            binding.getRoot().setOnClickListener(v -> listener.onConversationClick(conversation));
            binding.getRoot().setOnLongClickListener(v -> {
                listener.onConversationLongClick(conversation);
                return true;
            });
        }
    }
}
