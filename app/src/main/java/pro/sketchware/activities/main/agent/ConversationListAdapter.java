package pro.sketchware.activities.main.agent;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import pro.sketchware.ai.AgentConversation;
import pro.sketchware.ai.AgentProvider;
import pro.sketchware.databinding.ItemAgentConversationBinding;

public class ConversationListAdapter extends RecyclerView.Adapter<ConversationListAdapter.ViewHolder> {
    interface OnConversationClickListener {
        void onConversationClicked(AgentConversation conversation);

        void onConversationLongClicked(AgentConversation conversation);
    }

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
    private final List<AgentConversation> conversations = new ArrayList<>();
    private final OnConversationClickListener listener;

    ConversationListAdapter(OnConversationClickListener listener) {
        this.listener = listener;
    }

    void submit(List<AgentConversation> items) {
        conversations.clear();
        conversations.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAgentConversationBinding binding = ItemAgentConversationBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AgentConversation conversation = conversations.get(position);
        String title = (conversation.title == null || conversation.title.trim().isEmpty())
                ? "Conversation"
                : conversation.title;
        holder.binding.conversationTitle.setText(title);

        String provider = conversation.provider == null ? "No provider" : AgentProvider.getDisplayName(conversation.provider);
        String updatedTime = dateFormat.format(new Date(conversation.updatedAt));
        holder.binding.conversationMeta.setText(provider + " • " + updatedTime);

        holder.binding.getRoot().setOnClickListener(v -> listener.onConversationClicked(conversation));
        holder.binding.getRoot().setOnLongClickListener(v -> {
            listener.onConversationLongClicked(conversation);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemAgentConversationBinding binding;

        ViewHolder(ItemAgentConversationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
