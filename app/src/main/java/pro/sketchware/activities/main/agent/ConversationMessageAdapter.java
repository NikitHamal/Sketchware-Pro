package pro.sketchware.activities.main.agent;

import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.ai.AgentMessage;
import pro.sketchware.databinding.ItemAgentMessageAssistantBinding;
import pro.sketchware.databinding.ItemAgentMessageToolBinding;
import pro.sketchware.databinding.ItemAgentMessageUserBinding;

public class ConversationMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_USER = 1;
    private static final int VIEW_ASSISTANT = 2;
    private static final int VIEW_TOOL = 3;

    private final List<AgentMessage> messages = new ArrayList<>();

    void submit(List<AgentMessage> items) {
        messages.clear();
        messages.addAll(items);
        notifyDataSetChanged();
    }

    void append(AgentMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    @Override
    public int getItemViewType(int position) {
        AgentMessage message = messages.get(position);
        if (AgentMessage.ROLE_USER.equals(message.role)) {
            return VIEW_USER;
        }
        if (AgentMessage.ROLE_TOOL.equals(message.role)) {
            return VIEW_TOOL;
        }
        return VIEW_ASSISTANT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_USER) {
            return new UserViewHolder(ItemAgentMessageUserBinding.inflate(inflater, parent, false));
        }
        if (viewType == VIEW_TOOL) {
            return new ToolViewHolder(ItemAgentMessageToolBinding.inflate(inflater, parent, false));
        }
        return new AssistantViewHolder(ItemAgentMessageAssistantBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        AgentMessage message = messages.get(position);
        if (holder instanceof UserViewHolder userViewHolder) {
            userViewHolder.binding.messageText.setText(message.content);
            userViewHolder.binding.messageText.setMovementMethod(LinkMovementMethod.getInstance());
        } else if (holder instanceof ToolViewHolder toolViewHolder) {
            toolViewHolder.binding.messageText.setText(message.content);
        } else if (holder instanceof AssistantViewHolder assistantViewHolder) {
            assistantViewHolder.binding.messageText.setText(message.content);
            assistantViewHolder.binding.messageText.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        final ItemAgentMessageUserBinding binding;

        UserViewHolder(ItemAgentMessageUserBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class AssistantViewHolder extends RecyclerView.ViewHolder {
        final ItemAgentMessageAssistantBinding binding;

        AssistantViewHolder(ItemAgentMessageAssistantBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class ToolViewHolder extends RecyclerView.ViewHolder {
        final ItemAgentMessageToolBinding binding;

        ToolViewHolder(ItemAgentMessageToolBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
