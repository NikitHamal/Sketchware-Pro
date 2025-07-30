package com.stormx.agent.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import com.stormx.agent.R;
import com.stormx.agent.activities.ChatActivity;
import com.stormx.agent.adapters.ConversationAdapter;
import com.stormx.agent.databinding.FragmentConversationListBinding;
import com.stormx.agent.models.Conversation;
import com.stormx.agent.storage.ConversationStorage;
import com.stormx.agent.storage.MessageStorage;

public class ConversationListFragment extends Fragment {
    private FragmentConversationListBinding binding;
    private ConversationAdapter conversationAdapter;
    private List<Conversation> conversations = new ArrayList<>();
    private ConversationStorage conversationStorage;
    private MessageStorage messageStorage;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentConversationListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        conversationStorage = new ConversationStorage(requireContext());
        messageStorage = new MessageStorage(requireContext());
        setupRecyclerView();
        loadConversations();
    }

    private void setupRecyclerView() {
        conversationAdapter = new ConversationAdapter(conversations, new ConversationAdapter.OnConversationClickListener() {
            @Override
            public void onConversationClick(Conversation conversation) {
                // Open chat activity with existing conversation
                Intent intent = new Intent(requireContext(), ChatActivity.class);
                intent.putExtra("conversation_id", conversation.getId());
                intent.putExtra("conversation_title", conversation.getTitle());
                startActivity(intent);
            }

            @Override
            public void onConversationLongClick(Conversation conversation) {
                showConversationMenu(conversation);
            }
        });
        
        binding.conversationsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.conversationsRecyclerView.setAdapter(conversationAdapter);
    }

    public void refreshConversations() {
        loadConversations();
    }

    private void loadConversations() {
        conversations = conversationStorage.getAllConversations();
        conversationAdapter.notifyDataSetChanged();
        updateUI();
    }

    private void updateUI() {
        if (conversations.isEmpty()) {
            showEmptyState();
        } else {
            showConversationsList();
        }
    }

    private void showEmptyState() {
        binding.emptyStateLayout.setVisibility(View.VISIBLE);
        binding.conversationsRecyclerView.setVisibility(View.GONE);
    }

    private void showConversationsList() {
        binding.emptyStateLayout.setVisibility(View.GONE);
        binding.conversationsRecyclerView.setVisibility(View.VISIBLE);
    }

    private void showConversationMenu(Conversation conversation) {
        String[] options = {getString(R.string.rename), getString(R.string.delete)};
        
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.conversation_options)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showRenameDialog(conversation);
                            break;
                        case 1:
                            showDeleteDialog(conversation);
                            break;
                    }
                })
                .show();
    }

    private void showRenameDialog(Conversation conversation) {
        EditText input = new EditText(requireContext());
        input.setText(conversation.getTitle());
        
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.rename_conversation)
                .setView(input)
                .setPositiveButton(R.string.rename, (dialog, which) -> {
                    String newTitle = input.getText().toString().trim();
                    if (!newTitle.isEmpty()) {
                        conversationStorage.updateConversationTitle(conversation.getId(), newTitle);
                        loadConversations();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDeleteDialog(Conversation conversation) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_conversation)
                .setMessage(R.string.delete_conversation_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    conversationStorage.deleteConversation(conversation.getId());
                    messageStorage.deleteMessages(conversation.getId());
                    loadConversations();
                    Toast.makeText(requireContext(), R.string.conversation_deleted, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}