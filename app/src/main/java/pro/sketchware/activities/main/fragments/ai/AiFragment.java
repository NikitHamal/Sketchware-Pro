package pro.sketchware.activities.main.fragments.ai;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.transition.MaterialSharedAxis;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.activities.main.fragments.ai.adapters.ConversationAdapter;
import pro.sketchware.activities.main.fragments.ai.models.Conversation;
import pro.sketchware.databinding.FragmentAiBinding;
import pro.sketchware.activities.ai.chat.ChatActivity;
import pro.sketchware.activities.ai.storage.ConversationStorage;
import pro.sketchware.activities.ai.storage.MessageStorage;
import pro.sketchware.R;

public class AiFragment extends Fragment {
    private FragmentAiBinding binding;
    private ConversationAdapter conversationAdapter;
    private List<Conversation> conversations = new ArrayList<>();
    private ConversationStorage conversationStorage;
    private MessageStorage messageStorage;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.X, true));
        setReturnTransition(new MaterialSharedAxis(MaterialSharedAxis.X, false));
        setExitTransition(new MaterialSharedAxis(MaterialSharedAxis.X, true));
        setReenterTransition(new MaterialSharedAxis(MaterialSharedAxis.X, false));
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAiBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        conversationStorage = new ConversationStorage(requireContext());
        messageStorage = new MessageStorage(requireContext());
        setupRecyclerView();
        setupNewConversationButton();
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

    private void setupNewConversationButton() {
        binding.newConversationButton.setOnClickListener(v -> {
            // Start new conversation
            Intent intent = new Intent(requireContext(), ChatActivity.class);
            startActivity(intent);
        });
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

    @Override
    public void onResume() {
        super.onResume();
        // Refresh conversations when returning to fragment
        loadConversations();
    }

    private void loadConversations() {
        conversations.clear();
        conversations.addAll(conversationStorage.getAllConversations());
        conversationAdapter.notifyDataSetChanged();
        updateUI();
    }

    private void showConversationMenu(Conversation conversation) {
        PopupMenu popupMenu = new PopupMenu(requireContext(), binding.conversationsRecyclerView);
        popupMenu.getMenuInflater().inflate(R.menu.conversation_menu, popupMenu.getMenu());
        
        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_rename) {
                showRenameDialog(conversation);
                return true;
            } else if (id == R.id.action_delete) {
                showDeleteDialog(conversation);
                return true;
            }
            return false;
        });
        
        popupMenu.show();
    }

    private void showRenameDialog(Conversation conversation) {
        EditText editText = new EditText(requireContext());
        editText.setText(conversation.getTitle());
        editText.setHint("Enter new title");
        
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Rename Conversation")
                .setView(editText)
                .setPositiveButton("Rename", (dialog, which) -> {
                    String newTitle = editText.getText().toString().trim();
                    if (!newTitle.isEmpty()) {
                        conversationStorage.updateConversationTitle(conversation.getId(), newTitle);
                        loadConversations();
                        Toast.makeText(requireContext(), "Conversation renamed", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteDialog(Conversation conversation) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Conversation")
                .setMessage("Are you sure you want to delete this conversation? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    conversationStorage.deleteConversation(conversation.getId());
                    messageStorage.deleteMessages(conversation.getId());
                    loadConversations();
                    Toast.makeText(requireContext(), "Conversation deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}