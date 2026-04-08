package pro.sketchware.agent.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.transition.MaterialFadeThrough;

import java.util.ArrayList;
import java.util.List;

import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.agent.AgentRepository;
import pro.sketchware.databinding.DialogInputLayoutBinding;
import pro.sketchware.databinding.FragmentAgentHomeBinding;
import pro.sketchware.databinding.ItemAgentWorkspaceBinding;

public class AgentHomeFragment extends Fragment {

    private FragmentAgentHomeBinding binding;
    private AgentRepository repository;
    private WorkspaceAdapter adapter;
    private MenuProvider menuProvider;
    private final ActivityResultLauncher<Intent> refreshOnReturn = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> bindWorkspaces());

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEnterTransition(new MaterialFadeThrough());
        setReturnTransition(new MaterialFadeThrough());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAgentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repository = new AgentRepository();
        adapter = new WorkspaceAdapter(items -> {
            Intent intent = new Intent(requireContext(), AgentWorkspaceActivity.class);
            intent.putExtra(AgentWorkspaceActivity.EXTRA_WORKSPACE_ID, items.workspace.id);
            refreshOnReturn.launch(intent);
        });

        binding.workspaceList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.workspaceList.setAdapter(adapter);
        binding.createWorkspaceButton.setOnClickListener(v -> showCreateWorkspaceDialog());
        binding.emptyStateButton.setOnClickListener(v -> showCreateWorkspaceDialog());

        View mainFab = requireActivity().findViewById(R.id.create_new_project);
        if (mainFab != null) {
            mainFab.setVisibility(View.GONE);
        }

        menuProvider = new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.agent_home_menu, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if (menuItem.getItemId() == R.id.action_agent_settings) {
                    refreshOnReturn.launch(new Intent(requireContext(), AgentSettingsActivity.class));
                    return true;
                }
                return false;
            }
        };
        requireActivity().addMenuProvider(menuProvider);
        bindWorkspaces();
    }

    @Override
    public void onResume() {
        super.onResume();
        bindWorkspaces();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (menuProvider == null || getActivity() == null) {
            return;
        }
        if (hidden) {
            requireActivity().removeMenuProvider(menuProvider);
        } else {
            requireActivity().addMenuProvider(menuProvider);
            bindWorkspaces();
        }
    }

    private void bindWorkspaces() {
        if (binding == null) {
            return;
        }
        ArrayList<AgentRepository.Workspace> workspaces = repository.getWorkspaces();
        ArrayList<WorkspaceRow> rows = new ArrayList<>();
        int totalConversations = 0;
        int totalProjects = 0;

        for (AgentRepository.Workspace workspace : workspaces) {
            int conversationCount = repository.getConversations(workspace.id).size();
            int projectCount = workspace.projectIds.size();
            totalConversations += conversationCount;
            totalProjects += projectCount;
            rows.add(new WorkspaceRow(workspace, conversationCount, projectCount));
        }

        adapter.submit(rows);
        binding.workspaceCount.setText(String.valueOf(workspaces.size()));
        binding.projectCount.setText(String.valueOf(totalProjects));
        binding.conversationCount.setText(String.valueOf(totalConversations));
        boolean empty = rows.isEmpty();
        binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.workspaceList.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void showCreateWorkspaceDialog() {
        DialogInputLayoutBinding dialogBinding = DialogInputLayoutBinding.inflate(getLayoutInflater());
        dialogBinding.renameOccurrencesCheckBox.setVisibility(View.GONE);
        dialogBinding.textInputLayout.setHint("Workspace name");

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Create workspace")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Create", (dialog, which) -> {
                    repository.createWorkspace(Helper.getText(dialogBinding.inputText));
                    bindWorkspaces();
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        if (menuProvider != null && getActivity() != null) {
            requireActivity().removeMenuProvider(menuProvider);
        }
        super.onDestroyView();
        binding = null;
    }

    private static class WorkspaceRow {
        final AgentRepository.Workspace workspace;
        final int conversationCount;
        final int projectCount;

        WorkspaceRow(AgentRepository.Workspace workspace, int conversationCount, int projectCount) {
            this.workspace = workspace;
            this.conversationCount = conversationCount;
            this.projectCount = projectCount;
        }
    }

    private static class WorkspaceAdapter extends RecyclerView.Adapter<WorkspaceAdapter.ViewHolder> {

        interface OnWorkspaceClickListener {
            void onWorkspaceClick(@NonNull WorkspaceRow row);
        }

        private final ArrayList<WorkspaceRow> items = new ArrayList<>();
        private final OnWorkspaceClickListener listener;

        WorkspaceAdapter(OnWorkspaceClickListener listener) {
            this.listener = listener;
        }

        void submit(@NonNull List<WorkspaceRow> rows) {
            items.clear();
            items.addAll(rows);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ItemAgentWorkspaceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            WorkspaceRow row = items.get(position);
            holder.binding.title.setText(row.workspace.name);
            holder.binding.projectsValue.setText(String.valueOf(row.projectCount));
            holder.binding.conversationsValue.setText(String.valueOf(row.conversationCount));
            holder.binding.updatedValue.setText(DateUtils.getRelativeTimeSpanString(
                    row.workspace.updatedAt > 0 ? row.workspace.updatedAt : row.workspace.createdAt));
            holder.binding.getRoot().setOnClickListener(v -> listener.onWorkspaceClick(row));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ItemAgentWorkspaceBinding binding;

            ViewHolder(ItemAgentWorkspaceBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
