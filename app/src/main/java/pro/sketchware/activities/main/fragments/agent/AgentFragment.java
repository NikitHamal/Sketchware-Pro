package pro.sketchware.activities.main.fragments.agent;

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

import mod.hey.studios.util.Helper;
import pro.sketchware.activities.main.agent.AgentWorkspaceActivity;
import pro.sketchware.ai.AgentStorage;
import pro.sketchware.ai.AgentWorkspace;
import pro.sketchware.databinding.DialogCreateNewFileLayoutBinding;
import pro.sketchware.databinding.FragmentAgentBinding;
import pro.sketchware.utility.UI;

public class AgentFragment extends Fragment implements WorkspaceListAdapter.OnWorkspaceClickListener {
    private FragmentAgentBinding binding;
    private WorkspaceListAdapter adapter;
    private final AgentStorage storage = AgentStorage.getInstance();

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
        super.onViewCreated(view, savedInstanceState);
        adapter = new WorkspaceListAdapter(this);
        binding.workspacesList.setAdapter(adapter);

        binding.createWorkspaceFab.setOnClickListener(v -> showCreateWorkspaceDialog());
        binding.createWorkspaceButtonEmpty.setOnClickListener(v -> showCreateWorkspaceDialog());

        UI.addSystemWindowInsetToPadding(binding.workspacesList, true, false, true, true);
        UI.addSystemWindowInsetToMargin(binding.createWorkspaceFab, false, false, true, true);
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
        ArrayList<AgentWorkspace> workspaces = storage.getWorkspaces();
        adapter.submit(workspaces);
        boolean empty = workspaces.isEmpty();
        binding.agentEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.workspacesList.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void showCreateWorkspaceDialog() {
        DialogCreateNewFileLayoutBinding dialogBinding = DialogCreateNewFileLayoutBinding.inflate(getLayoutInflater());
        dialogBinding.chipGroupTypes.setVisibility(View.GONE);
        dialogBinding.inputText.setHint("Workspace name");
        dialogBinding.inputText.requestFocus();

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Create workspace")
                .setView(dialogBinding.getRoot())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", null);

        var dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(v -> {
                String workspaceName = Helper.getText(dialogBinding.inputText).trim();
                if (workspaceName.isEmpty()) {
                    dialogBinding.textInputLayout.setError("Workspace name is required");
                    return;
                }
                dialogBinding.textInputLayout.setError(null);
                storage.createWorkspace(workspaceName);
                dialog.dismiss();
                reload();
            });
        });
        dialog.show();
    }

    @Override
    public void onWorkspaceClicked(AgentWorkspace workspace) {
        Intent intent = new Intent(requireContext(), AgentWorkspaceActivity.class);
        intent.putExtra(AgentWorkspaceActivity.EXTRA_WORKSPACE_ID, workspace.id);
        startActivity(intent);
    }

    @Override
    public void onWorkspaceLongClicked(AgentWorkspace workspace) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete workspace")
                .setMessage("This removes the workspace and all agent conversations under it.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    storage.deleteWorkspace(workspace.id);
                    reload();
                })
                .show();
    }
}
