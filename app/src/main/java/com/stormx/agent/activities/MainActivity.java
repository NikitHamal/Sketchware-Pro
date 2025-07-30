package com.stormx.agent.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.stormx.agent.R;
import com.stormx.agent.fragments.ConversationListFragment;
import com.stormx.agent.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar();
        setupNewChatButton();
        loadConversationListFragment();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
        }
    }

    private void setupNewChatButton() {
        binding.fabNewChat.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChatActivity.class);
            startActivity(intent);
        });
    }

    private void loadConversationListFragment() {
        Fragment fragment = new ConversationListFragment();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh conversation list when returning from chat
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragment instanceof ConversationListFragment) {
            ((ConversationListFragment) fragment).refreshConversations();
        }
    }
}