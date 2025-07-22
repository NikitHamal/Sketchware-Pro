package pro.sketchware.activities.ai.chat.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.noties.markwon.Markwon;
import pro.sketchware.activities.ai.chat.models.ChatMessage;
import pro.sketchware.activities.ai.chat.views.ProjectItemView;
import pro.sketchware.activities.ai.chat.views.FixProposalView;
import pro.sketchware.databinding.ItemChatMessageBinding;
import pro.sketchware.R;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.BaseViewHolder> {
    private static final String TAG = "ChatAdapter";
    private static final int MAX_COLLAPSED_LENGTH = 225;
    
    private List<ChatMessage> messages;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private Markwon markwon;
    private OnProposalActionListener proposalActionListener;
    private Map<String, Boolean> expandedMessages = new HashMap<>();

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
    public BaseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == ChatMessage.TYPE_PROPOSAL) {
            // Inflate the proposal layout directly
            android.view.View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_proposal, parent, false);
            return new ProposalViewHolder(view);
        } else if (viewType == ChatMessage.TYPE_AI_SUCCESS) {
            // Use the same layout as proposal but configure differently
            android.view.View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_proposal, parent, false);
            return new SuccessViewHolder(view);
        } else {
            ItemChatMessageBinding binding = ItemChatMessageBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ChatMessageViewHolder(binding);
        }
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

    public void updateLastMessage(String content) {
        if (!messages.isEmpty()) {
            ChatMessage lastMessage = messages.get(messages.size() - 1);
            if (lastMessage.getType() == ChatMessage.TYPE_AI) {
                lastMessage.setContent(content);
                notifyItemChanged(messages.size() - 1);
            }
        }
    }
    
    private void setupThinkingContent(android.view.View thinkingLayout, String thinkingContent) {
        android.widget.TextView contentView = thinkingLayout.findViewById(R.id.thinking_content);
        android.widget.TextView statusView = thinkingLayout.findViewById(R.id.thinking_status);
        android.widget.LinearLayout headerView = thinkingLayout.findViewById(R.id.thinking_header);
        android.widget.LinearLayout contentContainer = thinkingLayout.findViewById(R.id.thinking_content_container);
        android.widget.ImageView expandIcon = thinkingLayout.findViewById(R.id.thinking_expand_icon);
        
        // Set thinking content
        contentView.setText(thinkingContent);
        statusView.setText("Thoughts");
        
        // Set up toggle functionality
        headerView.setOnClickListener(v -> {
            boolean isVisible = contentContainer.getVisibility() == android.view.View.VISIBLE;
            contentContainer.setVisibility(isVisible ? android.view.View.GONE : android.view.View.VISIBLE);
            
            // Rotate arrow icon
            expandIcon.setRotation(isVisible ? 0f : 180f);
        });
        
        // Initially collapsed
        contentContainer.setVisibility(android.view.View.GONE);
        expandIcon.setRotation(0f);
    }

    // Abstract base class for all view holders
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
            if (message.isUserMessage()) {
                binding.userMessageLayout.setVisibility(android.view.View.VISIBLE);
                binding.aiMessageLayout.setVisibility(android.view.View.GONE);
                
                // Handle expandable user message
                setupExpandableUserMessage(message);
                binding.userMessageTime.setText(timeFormat.format(new Date(message.getTimestamp())));
                
                // Handle attached files
                if (message.hasAttachedFiles()) {
                    binding.userAttachedFiles.setVisibility(android.view.View.VISIBLE);
                    binding.userAttachedFiles.removeAllViews();
                    
                    for (ChatMessage.AttachedFile file : message.getAttachedFiles()) {
                        android.view.View fileView = android.view.LayoutInflater.from(binding.getRoot().getContext())
                            .inflate(R.layout.item_attached_file, binding.userAttachedFiles, false);
                        
                        android.widget.TextView fileName = fileView.findViewById(R.id.file_name);
                        android.widget.TextView fileSize = fileView.findViewById(R.id.file_size);
                        android.widget.ImageView fileRemove = fileView.findViewById(R.id.file_remove);
                        
                        fileName.setText(file.getName());
                        fileSize.setText(file.getFormattedSize());
                        fileRemove.setVisibility(android.view.View.GONE); // Don't show remove for sent messages
                        
                        binding.userAttachedFiles.addView(fileView);
                    }
                } else {
                    binding.userAttachedFiles.setVisibility(android.view.View.GONE);
                }
            } else {
                binding.userMessageLayout.setVisibility(android.view.View.GONE);
                binding.aiMessageLayout.setVisibility(android.view.View.VISIBLE);
                
                // Handle thinking content
                if (message.hasThinkingContent()) {
                    binding.thinkingContentLayout.getRoot().setVisibility(android.view.View.VISIBLE);
                    setupThinkingContent(binding.thinkingContentLayout.getRoot(), message.getThinkingContent());
                } else {
                    binding.thinkingContentLayout.getRoot().setVisibility(android.view.View.GONE);
                }
                
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
        
        private void setupExpandableUserMessage(ChatMessage message) {
            String content = message.getContent();
            boolean isExpanded = expandedMessages.getOrDefault(message.getId(), false);
            
            if (content.length() > MAX_COLLAPSED_LENGTH && !isExpanded) {
                // Show truncated message with "Read More"
                String truncated = content.substring(0, MAX_COLLAPSED_LENGTH);
                binding.userMessage.setText(truncated);
                binding.userMessageExpandIndicator.setVisibility(android.view.View.VISIBLE);
                binding.userMessageExpandIndicator.setText("...Read More");
            } else {
                // Show full message
                binding.userMessage.setText(content);
                if (content.length() > MAX_COLLAPSED_LENGTH && isExpanded) {
                    // Show "Read Less" option
                    binding.userMessageExpandIndicator.setVisibility(android.view.View.VISIBLE);
                    binding.userMessageExpandIndicator.setText("Read Less");
                } else {
                    binding.userMessageExpandIndicator.setVisibility(android.view.View.GONE);
                }
            }
            
            // Set up click listeners for expansion/contraction
            android.view.View.OnClickListener expandClickListener = v -> {
                boolean currentlyExpanded = expandedMessages.getOrDefault(message.getId(), false);
                expandedMessages.put(message.getId(), !currentlyExpanded);
                notifyItemChanged(getAdapterPosition());
            };
            
            binding.userMessageExpandIndicator.setOnClickListener(expandClickListener);
            // Also make the message bubble clickable if truncated
            if (content.length() > MAX_COLLAPSED_LENGTH) {
                // Cast parent to View since we know it's the LinearLayout container
                if (binding.userMessage.getParent() instanceof android.view.View) {
                    ((android.view.View) binding.userMessage.getParent()).setOnClickListener(expandClickListener);
                }
            }
        }
    }

    public class ProposalViewHolder extends BaseViewHolder {
        private android.view.View proposalView;
        private android.widget.FrameLayout proposalContainer;

        public ProposalViewHolder(android.view.View view) {
            super(view);
            this.proposalView = view;
            this.proposalContainer = view.findViewById(R.id.proposalContainer);
        }

        @Override
        public void bind(ChatMessage message) {
            // Clear any existing proposal views
            proposalContainer.removeAllViews();

            if (message.hasProposalData()) {
                try {
                    org.json.JSONObject proposalData = new org.json.JSONObject(message.getProposalData());
                    
                    // Create and configure proposal view
                    FixProposalView fixProposalView = new FixProposalView(proposalView.getContext());
                    fixProposalView.setProposal(message.getExplanation(), proposalData);
                    
                    // Set up proposal action listener - bridge between FixProposalView and ChatAdapter listeners
                    fixProposalView.setOnProposalActionListener(new FixProposalView.OnProposalActionListener() {
                        @Override
                        public void onAccept(java.util.List<org.json.JSONObject> proposalDataList) {
                            if (proposalActionListener != null) {
                                proposalActionListener.onAcceptProposal(message);
                            }
                        }

                        @Override
                        public void onDiscard(java.util.List<org.json.JSONObject> proposalDataList) {
                            if (proposalActionListener != null) {
                                proposalActionListener.onDiscardProposal(message);
                            }
                        }
                    });
                    
                    // Add to container
                    proposalContainer.addView(fixProposalView);
                    
                } catch (org.json.JSONException e) {
                    Log.e(TAG, "Error parsing proposal data", e);
                }
            }
        }
    }

    // Success ViewHolder for showing success messages with affected files
    public class SuccessViewHolder extends BaseViewHolder {
        private final android.view.View successView;
        private final android.view.ViewGroup proposalContainer;

        public SuccessViewHolder(android.view.View view) {
            super(view);
            this.successView = view;
            this.proposalContainer = view.findViewById(R.id.proposalContainer);
        }

        @Override
        public void bind(ChatMessage message) {
            // Clear any existing views
            proposalContainer.removeAllViews();

            if (message.isSuccessMessage()) {
                try {
                    // Create and configure success view
                    FixProposalView successProposalView = new FixProposalView(successView.getContext());
                    
                    // Parse affected files if available
                    java.util.List<org.json.JSONObject> affectedFiles = new java.util.ArrayList<>();
                    if (message.hasAffectedFiles()) {
                        org.json.JSONArray filesArray = new org.json.JSONArray(message.getAffectedFiles());
                        for (int i = 0; i < filesArray.length(); i++) {
                            affectedFiles.add(filesArray.getJSONObject(i));
                        }
                    }
                    
                    // Show success state with affected files
                    successProposalView.showSuccessState(message.getContent(), affectedFiles);
                    
                    // Add to container
                    proposalContainer.addView(successProposalView);
                    
                } catch (org.json.JSONException e) {
                    Log.e(TAG, "Error parsing affected files for success message", e);
                    // Fallback: show success message without files
                    FixProposalView successProposalView = new FixProposalView(successView.getContext());
                    successProposalView.showSuccessState(message.getContent(), null);
                    proposalContainer.addView(successProposalView);
                }
            }
        }
    }
}