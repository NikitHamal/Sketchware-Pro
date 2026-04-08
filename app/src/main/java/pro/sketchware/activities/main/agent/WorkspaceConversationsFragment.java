package pro.sketchware.activities.main.agent;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Comparator;

import mod.hey.studios.util.Helper;
import pro.sketchware.ai.AgentConversation;
import pro.sketchware.ai.AgentStorage;
import pro.sketchware.databinding.DialogCreateNewFileLayoutBinding;
import pro.sketchware.databinding.FragmentAgentWorkspaceConversationsBinding;
import pro.sketchware.utility.UI;

public class WorkspaceConversationsFragment extends Fragment implements ConversationListAdapter.OnConversationClickListener {
    private static final String ARG_WORKSPACE_ID = "workspace_id";

    private String workspaceId;
    private FragmentAgentWorkspaceConversationsBinding binding;
    private ConversationListAdapter adapter;
    private final AgentStorage storage = AgentStorage.getInstance();

    public static WorkspaceConversationsFragment newInstance(String workspaceId) {
        WorkspaceConversationsFragment fragment = new WorkspaceConversationsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_WORKSPACE_ID, workspaceId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        workspaceId = requireArguments().getString(ARG_WORKSPACE_ID);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAgentWorkspaceConversationsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        adapter = new ConversationListAdapter(this);
        binding.conversationsList.setAdapter(adapter);

        binding.createConversationFab.setOnClickListener(v -> showCreateConversationDialog());
        binding.createConversationEmpty.setOnClickListener(v -> showCreateConversationDialog());

        UI.addSystemWindowInsetToPadding(binding.conversationsList, false, false, false, true);
        UI.addSystemWindowInsetToMargin(binding.createConversationFab, false, false, true, true);
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        if (binding == null) {
            return;
        }
        ArrayList<AgentConversation> conversations = storage.getConversations(workspaceId);
        conversations.sort(Comparator.comparingLong((AgentConversation value) -> value.updatedAt).reversed());
        adapter.submit(conversations);
        boolean empty = conversations.isEmpty();
        binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.conversationsList.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void showCreateConversationDialog() {
        DialogCreateNewFileLayoutBinding dialogBinding = DialogCreateNewFileLayoutBinding.inflate(getLayoutInflater());
        dialogBinding.chipGroupTypes.setVisibility(View.GONE);
        dialogBinding.inputText.setHint("Conversation title");

        var dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("New conversation")
                .setView(dialogBinding.getRoot())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(v -> {
                String title = Helper.getText(dialogBinding.inputText).trim();
                if (title.isEmpty()) {
                    title = "New conversation";
                }
                AgentConversation conversation = storage.createConversation(workspaceId, title);
                dialog.dismiss();
                openConversation(conversation);
            });
        });
        dialog.show();
    }

    @Override
    public void onConversationClicked(AgentConversation conversation) {
        openConversation(conversation);
    }

    @Override
    public void onConversationLongClicked(AgentConversation conversation) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete conversation")
                .setMessage("This removes all messages in this conversation.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    storage.deleteConversation(workspaceId, conversation.id);
                    reload();
                })
                .show();
    }

    private void openConversation(AgentConversation conversation) {
        Intent intent = new Intent(requireContext(), AgentConversationActivity.class);
        intent.putExtra(AgentConversationActivity.EXTRA_WORKSPACE_ID, workspaceId);
        intent.putExtra(AgentConversationActivity.EXTRA_CONVERSATION_ID, conversation.id);
        startActivity(intent);
    }
}
