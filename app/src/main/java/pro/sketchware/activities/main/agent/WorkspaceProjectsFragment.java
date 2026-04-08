package pro.sketchware.activities.main.agent;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import a.a.a.yB;
import mod.hey.studios.util.Helper;
import pro.sketchware.ai.AgentProjectManager;
import pro.sketchware.ai.AgentStorage;
import pro.sketchware.ai.AgentWorkspace;
import pro.sketchware.databinding.DialogCreateNewFileLayoutBinding;
import pro.sketchware.databinding.FragmentAgentWorkspaceProjectsBinding;
import pro.sketchware.utility.UI;

public class WorkspaceProjectsFragment extends Fragment implements WorkspaceProjectAdapter.OnProjectSelectionListener {
    private static final String ARG_WORKSPACE_ID = "workspace_id";

    private String workspaceId;
    private AgentWorkspace workspace;
    private FragmentAgentWorkspaceProjectsBinding binding;
    private WorkspaceProjectAdapter adapter;
    private final AgentStorage storage = AgentStorage.getInstance();
    private AgentProjectManager projectManager;
    private final Set<String> selectedProjectIds = new HashSet<>();

    public static WorkspaceProjectsFragment newInstance(String workspaceId) {
        WorkspaceProjectsFragment fragment = new WorkspaceProjectsFragment();
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
        binding = FragmentAgentWorkspaceProjectsBinding.inflate(inflater, container, false);
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
        projectManager = new AgentProjectManager(requireContext());
        workspace = storage.getWorkspace(workspaceId);
        if (workspace == null) {
            return;
        }

        selectedProjectIds.clear();
        selectedProjectIds.addAll(workspace.projectIds);
        adapter = new WorkspaceProjectAdapter(selectedProjectIds, this);
        binding.projectsList.setAdapter(adapter);
        binding.createProjectButton.setOnClickListener(v -> showCreateProjectDialog());

        UI.addSystemWindowInsetToPadding(binding.projectsList, false, false, false, true);
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
        workspace = storage.getWorkspace(workspaceId);
        if (workspace == null) {
            return;
        }
        selectedProjectIds.clear();
        selectedProjectIds.addAll(workspace.projectIds);
        ArrayList<HashMap<String, Object>> allProjects = projectManager.listProjects();
        allProjects.sort(Comparator.comparingInt(value -> -parseInt(yB.c(value, "sc_id"), 0)));
        adapter.submit(allProjects);
    }

    @Override
    public void onProjectSelectionChanged(String scId, boolean selected) {
        if (selected) {
            selectedProjectIds.add(scId);
        } else {
            selectedProjectIds.remove(scId);
        }
        saveSelection();
    }

    private void saveSelection() {
        if (workspace == null) {
            return;
        }
        workspace.projectIds.clear();
        workspace.projectIds.addAll(selectedProjectIds);
        storage.updateWorkspace(workspace);
    }

    private void showCreateProjectDialog() {
        DialogCreateNewFileLayoutBinding dialogBinding = DialogCreateNewFileLayoutBinding.inflate(getLayoutInflater());
        dialogBinding.chipGroupTypes.setVisibility(View.GONE);
        dialogBinding.inputText.setHint("Project name");

        var dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Create project")
                .setView(dialogBinding.getRoot())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(v -> {
                String projectName = Helper.getText(dialogBinding.inputText).trim();
                if (projectName.isEmpty()) {
                    dialogBinding.textInputLayout.setError("Project name is required");
                    return;
                }
                dialogBinding.textInputLayout.setError(null);
                HashMap<String, Object> created = projectManager.createProject(projectName, projectName, null);
                String scId = yB.c(created, "sc_id");
                selectedProjectIds.add(scId);
                saveSelection();
                dialog.dismiss();
                reload();
            });
        });
        dialog.show();
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
