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
import pro.sketchware.activities.ai.chat.views.ProjectItemView;
import pro.sketchware.activities.ai.chat.views.FixProposalView;
import pro.sketchware.databinding.ItemChatMessageBinding;
import pro.sketchware.databinding.ItemChatProposalBinding;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatMessageViewHolder> {
    private static final String TAG = "ChatAdapter";
    private List<ChatMessage> messages;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private Markwon markwon;
    private FixProposalView.OnProposalActionListener proposalActionListener;

    public interface OnProposalActionListener {
        void onAcceptProposal(ChatMessage message);
        void onDiscardProposal(ChatMessage message);
    }

    public ChatAdapter(List<ChatMessage> messages, Context context) {
        this.messages = messages;
        this.markwon = Markwon.create(context);
    }

    public void setOnProposalActionListener(OnProposalActionListener listener) {
        this.proposalActionListener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getType();
    }

    @NonNull
    @Override
    public ChatMessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == ChatMessage.TYPE_PROPOSAL) {
            ItemChatProposalBinding binding = ItemChatProposalBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ProposalViewHolder(binding);
        } else {
            ItemChatMessageBinding binding = ItemChatMessageBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ChatMessageViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ChatMessageViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        holder.bind(message);
    }

    @Override
    public int getItemCount() {
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
            if (message.isUserMessage()) {
                binding.userMessageLayout.setVisibility(android.view.View.VISIBLE);
                binding.aiMessageLayout.setVisibility(android.view.View.GONE);
                
                binding.userMessage.setText(message.getContent());
                binding.userMessageTime.setText(timeFormat.format(new Date(message.getTimestamp())));
            } else {
                binding.userMessageLayout.setVisibility(android.view.View.GONE);
                binding.aiMessageLayout.setVisibility(android.view.View.VISIBLE);
                
                // Use Markwon to render markdown in AI messages
                markwon.setMarkdown(binding.aiMessage, message.getContent());
                binding.aiMessageTime.setText(timeFormat.format(new Date(message.getTimestamp())));
                
                // Handle project item display
                if (message.hasProjectData()) {
                    binding.projectItemContainer.setVisibility(android.view.View.VISIBLE);
                    binding.projectItemContainer.removeAllViews();
                    
                    ProjectItemView projectView = new ProjectItemView(binding.getRoot().getContext());
                    projectView.setProjectData(
                        message.getProjectId(),
                        message.getProjectName(),
                        message.getAppName(),
                        message.getPackageName()
                    );
                    
                    binding.projectItemContainer.addView(projectView);
                } else {
                    binding.projectItemContainer.setVisibility(android.view.View.GONE);
                }
            }
        }
    }

    public class ProposalViewHolder extends ChatMessageViewHolder {
        private ItemChatProposalBinding proposalBinding;

        public ProposalViewHolder(ItemChatProposalBinding binding) {
            super(null);
            this.proposalBinding = binding;
        }

        @Override
        public void bind(ChatMessage message) {
            // Set timestamp
            proposalBinding.tvTime.setText(timeFormat.format(new Date(message.getTimestamp())));

            // Clear any existing proposal views
            proposalBinding.proposalContainer.removeAllViews();

            if (message.hasProposalData()) {
                try {
                    org.json.JSONObject proposalData = new org.json.JSONObject(message.getProposalData());
                    
                    // Create and configure proposal view
                    FixProposalView proposalView = new FixProposalView(proposalBinding.getRoot().getContext());
                    proposalView.setProposal(message.getExplanation(), proposalData);
                    
                    // Set up proposal action listener
                    proposalView.setOnProposalActionListener(new FixProposalView.OnProposalActionListener() {
                        @Override
                        public void onAccept(org.json.JSONObject proposalData) {
                            if (proposalActionListener != null) {
                                proposalActionListener.onAcceptProposal(message);
                            }
                        }

                        @Override
                        public void onDiscard(org.json.JSONObject proposalData) {
                            if (proposalActionListener != null) {
                                proposalActionListener.onDiscardProposal(message);
                            }
                        }
                    });
                    
                    // Add to container
                    proposalBinding.proposalContainer.addView(proposalView);
                    
                } catch (org.json.JSONException e) {
                    Log.e(TAG, "Error parsing proposal data", e);
                }
            }
        }
    }
}