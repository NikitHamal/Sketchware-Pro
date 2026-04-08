package pro.sketchware.activities.main.fragments.agent;

import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.transition.MaterialFadeThrough;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import pro.sketchware.R;
import pro.sketchware.agent.data.AgentDatabase;
import pro.sketchware.agent.ui.settings.AgentSettingsActivity;
import pro.sketchware.agent.ui.workspace.WorkspaceActivity;
import pro.sketchware.databinding.FragmentAgentBinding;
import pro.sketchware.utility.UI;

public class AgentFragment extends Fragment {

    private FragmentAgentBinding binding;
    private AgentDatabase db;
    private WorkspaceAdapter adapter;
    private final List<AgentDatabase.Workspace> workspaces = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEnterTransition(new MaterialFadeThrough());
        setReturnTransition(new MaterialFadeThrough());
        setExitTransition(new MaterialFadeThrough());
        setReenterTransition(new MaterialFadeThrough());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAgentBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        db = AgentDatabase.getInstance(requireContext());

        adapter = new WorkspaceAdapter();
        binding.workspacesList.setAdapter(adapter);

        binding.btnCreateWorkspaceEmpty.setOnClickListener(v -> showCreateWorkspaceDialog());
        binding.btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), AgentSettingsActivity.class));
        });

        UI.addSystemWindowInsetToPadding(binding.titleContainer, true, false, true, false);
        UI.addSystemWindowInsetToPadding(binding.workspacesList, true, false, true, true);
        UI.addSystemWindowInsetToPadding(binding.emptyState, true, false, true, true);

        refreshWorkspaces();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshWorkspaces();
    }

    private void refreshWorkspaces() {
        workspaces.clear();
        workspaces.addAll(db.getAllWorkspaces());
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (workspaces.isEmpty()) {
            binding.emptyState.setVisibility(View.VISIBLE);
            binding.workspaceListContainer.setVisibility(View.GONE);
        } else {
            binding.emptyState.setVisibility(View.GONE);
            binding.workspaceListContainer.setVisibility(View.VISIBLE);
        }
    }

    private void showCreateWorkspaceDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_workspace, null);
        TextInputEditText nameInput = dialogView.findViewById(R.id.input_name);
        TextInputEditText descInput = dialogView.findViewById(R.id.input_description);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("New Workspace")
                .setView(dialogView)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
                    String desc = descInput.getText() != null ? descInput.getText().toString().trim() : "";
                    if (!name.isEmpty()) {
                        AgentDatabase.Workspace workspace = new AgentDatabase.Workspace();
                        workspace.id = UUID.randomUUID().toString();
                        workspace.name = name;
                        workspace.description = desc;
                        workspace.createdAt = System.currentTimeMillis();
                        workspace.updatedAt = System.currentTimeMillis();
                        db.insertWorkspace(workspace);
                        refreshWorkspaces();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openWorkspace(AgentDatabase.Workspace workspace) {
        Intent intent = new Intent(requireContext(), WorkspaceActivity.class);
        intent.putExtra("workspace_id", workspace.id);
        intent.putExtra("workspace_name", workspace.name);
        startActivity(intent);
    }

    private void showWorkspaceMenu(View anchor, AgentDatabase.Workspace workspace) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add("Rename");
        popup.getMenu().add("Delete");
        popup.setOnMenuItemClickListener(item -> {
            if ("Rename".equals(item.getTitle())) {
                showRenameDialog(workspace);
                return true;
            } else if ("Delete".equals(item.getTitle())) {
                showDeleteConfirmation(workspace);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showRenameDialog(AgentDatabase.Workspace workspace) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_workspace, null);
        TextInputEditText nameInput = dialogView.findViewById(R.id.input_name);
        TextInputEditText descInput = dialogView.findViewById(R.id.input_description);
        nameInput.setText(workspace.name);
        descInput.setText(workspace.description);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Rename Workspace")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
                    String desc = descInput.getText() != null ? descInput.getText().toString().trim() : "";
                    if (!name.isEmpty()) {
                        workspace.name = name;
                        workspace.description = desc;
                        db.updateWorkspace(workspace);
                        refreshWorkspaces();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteConfirmation(AgentDatabase.Workspace workspace) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Workspace")
                .setMessage("Are you sure you want to delete \"" + workspace.name + "\"? This will remove all conversations but won't delete the projects themselves.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    db.deleteWorkspace(workspace.id);
                    refreshWorkspaces();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private class WorkspaceAdapter extends RecyclerView.Adapter<WorkspaceAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_workspace, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AgentDatabase.Workspace workspace = workspaces.get(position);
            holder.name.setText(workspace.name);

            int projectCount = db.getWorkspaceProjectIds(workspace.id).size();
            int convCount = db.getConversationsForWorkspace(workspace.id).size();
            holder.description.setText(projectCount + " project" + (projectCount != 1 ? "s" : "") +
                    " · " + convCount + " conversation" + (convCount != 1 ? "s" : ""));

            CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                    workspace.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
            holder.updated.setText("Updated " + timeAgo);

            holder.itemView.setOnClickListener(v -> openWorkspace(workspace));
            holder.menu.setOnClickListener(v -> showWorkspaceMenu(v, workspace));
        }

        @Override
        public int getItemCount() {
            return workspaces.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, description, updated;
            ImageView menu;

            ViewHolder(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.workspace_name);
                description = itemView.findViewById(R.id.workspace_description);
                updated = itemView.findViewById(R.id.workspace_updated);
                menu = itemView.findViewById(R.id.btn_workspace_menu);
            }
        }
    }
}
