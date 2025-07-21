package pro.sketchware.activities.main.fragments.ai;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.transition.MaterialSharedAxis;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.activities.main.fragments.ai.adapters.ConversationAdapter;
import pro.sketchware.activities.main.fragments.ai.models.Conversation;
import pro.sketchware.databinding.FragmentAiBinding;
import pro.sketchware.activities.ai.chat.ChatActivity;

public class AiFragment extends Fragment {
    private FragmentAiBinding binding;
    private ConversationAdapter conversationAdapter;
    private List<Conversation> conversations = new ArrayList<>();

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
        
        setupRecyclerView();
        setupNewConversationButton();
        updateUI();
    }

    private void setupRecyclerView() {
        conversationAdapter = new ConversationAdapter(conversations, conversation -> {
            // Open chat activity with existing conversation
            Intent intent = new Intent(requireContext(), ChatActivity.class);
            intent.putExtra("conversation_id", conversation.getId());
            intent.putExtra("conversation_title", conversation.getTitle());
            startActivity(intent);
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
        // TODO: Load conversations from database/storage
        // For now, we'll keep it empty to show the empty state
        conversations.clear();
        conversationAdapter.notifyDataSetChanged();
        updateUI();
    }
}