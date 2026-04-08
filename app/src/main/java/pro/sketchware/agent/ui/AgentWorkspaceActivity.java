package pro.sketchware.agent.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.design.DesignActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import a.a.a.lC;
import a.a.a.yB;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.agent.AgentProjectManager;
import pro.sketchware.agent.AgentProvider;
import pro.sketchware.agent.AgentRepository;
import pro.sketchware.databinding.ActivityAgentWorkspaceBinding;
import pro.sketchware.databinding.DialogAgentCreateProjectBinding;
import pro.sketchware.databinding.DialogInputLayoutBinding;
import pro.sketchware.databinding.ItemAgentConversationBinding;
import pro.sketchware.databinding.ItemAgentWorkspaceProjectBinding;

public class AgentWorkspaceActivity extends AppCompatActivity {

    public static final String EXTRA_WORKSPACE_ID = "workspace_id";

    private ActivityAgentWorkspaceBinding binding;
    private AgentRepository repository;
    private AgentProjectManager projectManager;
    private AgentRepository.Workspace workspace;
    private ConversationAdapter conversationAdapter;
    private WorkspaceProjectAdapter projectAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAgentWorkspaceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new AgentRepository();
        projectManager = new AgentProjectManager(this, repository);
        workspace = repository.getWorkspace(getIntent().getStringExtra(EXTRA_WORKSPACE_ID));
        if (workspace == null) {
            finish();
            return;
        }

        binding.topAppBar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.topAppBar.inflateMenu(R.menu.agent_workspace_menu);
        binding.topAppBar.setOnMenuItemClickListener(this::onToolbarItemSelected);

        binding.conversationList.setLayoutManager(new LinearLayoutManager(this));
        conversationAdapter = new ConversationAdapter(item -> {
            Intent intent = new Intent(this, AgentChatActivity.class);
            intent.putExtra(AgentChatActivity.EXTRA_WORKSPACE_ID, workspace.id);
            intent.putExtra(AgentChatActivity.EXTRA_CONVERSATION_ID, item.conversation.id);
            startActivity(intent);
        });
        binding.conversationList.setAdapter(conversationAdapter);

        binding.projectList.setLayoutManager(new LinearLayoutManager(this));
        projectAdapter = new WorkspaceProjectAdapter(new WorkspaceProjectAdapter.Listener() {
            @Override
            public void onOpenProject(@NonNull HashMap<String, Object> project) {
                Intent intent = new Intent(AgentWorkspaceActivity.this, DesignActivity.class);
                intent.putExtra("sc_id", yB.c(project, "sc_id"));
                startActivity(intent);
            }

            @Override
            public void onShowProjectMenu(@NonNull View anchor, @NonNull HashMap<String, Object> project) {
                showProjectMenu(anchor, project);
            }
        });
        binding.projectList.setAdapter(projectAdapter);

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Conversations"), true);
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Projects"));
        binding.tabLayout.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                showTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {
            }
        });

        binding.createConversationButton.setOnClickListener(v -> showCreateConversationDialog());
        binding.emptyConversationButton.setOnClickListener(v -> showCreateConversationDialog());
        binding.addExistingProjectButton.setOnClickListener(v -> showAddExistingProjectsDialog());
        binding.createBlankProjectButton.setOnClickListener(v -> showCreateProjectDialog());
        binding.emptyProjectAddExistingButton.setOnClickListener(v -> showAddExistingProjectsDialog());
        binding.emptyProjectCreateButton.setOnClickListener(v -> showCreateProjectDialog());

        showTab(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        workspace = repository.getWorkspace(workspace.id);
        if (workspace == null) {
            finish();
            return;
        }
        bindWorkspace();
    }

    private boolean onToolbarItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_rename_workspace) {
            showRenameWorkspaceDialog();
            return true;
        }
        if (itemId == R.id.action_delete_workspace) {
            confirmDeleteWorkspace();
            return true;
        }
        if (itemId == R.id.action_agent_settings) {
            startActivity(new Intent(this, AgentSettingsActivity.class));
            return true;
        }
        return false;
    }

    private void bindWorkspace() {
        binding.topAppBar.setTitle(workspace.name);
        binding.workspaceSummary.setText(workspace.projectIds.size() + " projects • "
                + repository.getConversations(workspace.id).size() + " conversations");

        ArrayList<AgentRepository.Conversation> conversations = repository.getConversations(workspace.id);
        conversationAdapter.submit(conversations);
        boolean noConversations = conversations.isEmpty();
        binding.emptyConversationState.setVisibility(noConversations ? View.VISIBLE : View.GONE);
        binding.conversationList.setVisibility(noConversations ? View.GONE : View.VISIBLE);

        List<HashMap<String, Object>> projects = projectManager.listWorkspaceProjects(workspace);
        projectAdapter.submit(projects);
        boolean noProjects = projects.isEmpty();
        binding.emptyProjectState.setVisibility(noProjects ? View.VISIBLE : View.GONE);
        binding.projectList.setVisibility(noProjects ? View.GONE : View.VISIBLE);
    }

    private void showTab(int index) {
        boolean conversations = index == 0;
        binding.conversationContainer.setVisibility(conversations ? View.VISIBLE : View.GONE);
        binding.projectContainer.setVisibility(conversations ? View.GONE : View.VISIBLE);
    }

    private void showCreateConversationDialog() {
        DialogInputLayoutBinding dialogBinding = DialogInputLayoutBinding.inflate(getLayoutInflater());
        dialogBinding.renameOccurrencesCheckBox.setVisibility(View.GONE);
        dialogBinding.textInputLayout.setHint("Conversation title");
        dialogBinding.inputText.setText("New conversation");

        new MaterialAlertDialogBuilder(this)
                .setTitle("Create conversation")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Create", (dialog, which) -> {
                    String providerId = defaultProviderId();
                    AgentRepository.Conversation conversation = repository.createConversation(
                            workspace.id,
                            Helper.getText(dialogBinding.inputText),
                            providerId,
                            defaultModelId(providerId));
                    Intent intent = new Intent(this, AgentChatActivity.class);
                    intent.putExtra(AgentChatActivity.EXTRA_WORKSPACE_ID, workspace.id);
                    intent.putExtra(AgentChatActivity.EXTRA_CONVERSATION_ID, conversation.id);
                    startActivity(intent);
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void showAddExistingProjectsDialog() {
        ArrayList<HashMap<String, Object>> allProjects = lC.a();
        ArrayList<HashMap<String, Object>> availableProjects = new ArrayList<>();
        for (HashMap<String, Object> project : allProjects) {
            if (!workspace.projectIds.contains(yB.c(project, "sc_id"))) {
                availableProjects.add(project);
            }
        }

        if (availableProjects.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setMessage("Every existing project is already attached to this workspace.")
                    .setPositiveButton(R.string.common_word_ok, null)
                    .show();
            return;
        }

        String[] labels = new String[availableProjects.size()];
        boolean[] checked = new boolean[availableProjects.size()];
        for (int i = 0; i < availableProjects.size(); i++) {
            HashMap<String, Object> project = availableProjects.get(i);
            labels[i] = yB.c(project, "my_ws_name") + " (" + yB.c(project, "sc_id") + ")";
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Attach existing projects")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Attach", (dialog, which) -> {
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) {
                            workspace.projectIds.add(yB.c(availableProjects.get(i), "sc_id"));
                        }
                    }
                    workspace.updatedAt = System.currentTimeMillis();
                    repository.saveWorkspace(workspace);
                    bindWorkspace();
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void showRenameWorkspaceDialog() {
        DialogInputLayoutBinding dialogBinding = DialogInputLayoutBinding.inflate(getLayoutInflater());
        dialogBinding.renameOccurrencesCheckBox.setVisibility(View.GONE);
        dialogBinding.textInputLayout.setHint("Workspace name");
        dialogBinding.inputText.setText(workspace.name);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Rename workspace")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Save", (dialog, which) -> {
                    workspace.name = AgentRepository.sanitizeTitle(Helper.getText(dialogBinding.inputText), "New workspace");
                    workspace.updatedAt = System.currentTimeMillis();
                    repository.saveWorkspace(workspace);
                    bindWorkspace();
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void confirmDeleteWorkspace() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete workspace")
                .setMessage("Delete this workspace and all of its saved conversations? Projects will stay on device.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    repository.deleteWorkspace(workspace.id);
                    finish();
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void showCreateProjectDialog() {
        DialogAgentCreateProjectBinding dialogBinding = DialogAgentCreateProjectBinding.inflate(getLayoutInflater());
        dialogBinding.projectName.setText("NewProject");

        new MaterialAlertDialogBuilder(this)
                .setTitle("Create blank project")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Create", (dialog, which) -> {
                    try {
                        projectManager.createProject(workspace,
                                Helper.getText(dialogBinding.projectName),
                                nullable(Helper.getText(dialogBinding.appName)),
                                nullable(Helper.getText(dialogBinding.packageName)),
                                "1.0",
                                "1");
                        bindWorkspace();
                    } catch (Exception e) {
                        showError(e.getMessage());
                    }
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void showProjectMenu(@NonNull View anchor, @NonNull HashMap<String, Object> project) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Open");
        menu.getMenu().add("Remove from workspace");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if ("Open".equals(title)) {
                Intent intent = new Intent(this, DesignActivity.class);
                intent.putExtra("sc_id", yB.c(project, "sc_id"));
                startActivity(intent);
                return true;
            }
            if ("Remove from workspace".equals(title)) {
                workspace.projectIds.remove(yB.c(project, "sc_id"));
                workspace.updatedAt = System.currentTimeMillis();
                repository.saveWorkspace(workspace);
                bindWorkspace();
                return true;
            }
            if ("Duplicate".equals(title)) {
                showDuplicateProjectDialog(project);
                return true;
            }
            if ("Delete project".equals(title)) {
                confirmDeleteProject(project);
                return true;
            }
            return false;
        });
        menu.getMenu().add("Duplicate");
        menu.getMenu().add("Delete project");
        menu.show();
    }

    private void showDuplicateProjectDialog(@NonNull HashMap<String, Object> project) {
        DialogInputLayoutBinding dialogBinding = DialogInputLayoutBinding.inflate(getLayoutInflater());
        dialogBinding.renameOccurrencesCheckBox.setVisibility(View.GONE);
        dialogBinding.textInputLayout.setHint("Duplicated project name");
        dialogBinding.inputText.setText(yB.c(project, "my_ws_name") + " Copy");

        new MaterialAlertDialogBuilder(this)
                .setTitle("Duplicate project")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Duplicate", (dialog, which) -> {
                    try {
                        projectManager.duplicateProject(workspace,
                                yB.c(project, "sc_id"),
                                Helper.getText(dialogBinding.inputText),
                                null);
                        bindWorkspace();
                    } catch (Exception e) {
                        showError(e.getMessage());
                    }
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void confirmDeleteProject(@NonNull HashMap<String, Object> project) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete project")
                .setMessage("Delete " + yB.c(project, "my_ws_name") + " from Sketchware Pro? This removes the project files from device storage.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    projectManager.deleteProject(workspace, yB.c(project, "sc_id"));
                    bindWorkspace();
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void showError(String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Action failed")
                .setMessage(message)
                .setPositiveButton(R.string.common_word_ok, null)
                .show();
    }

    @NonNull
    private String defaultProviderId() {
        for (AgentProvider provider : AgentProvider.values()) {
            if (!repository.getProviderState(provider).cachedModels.isEmpty()) {
                return provider.id;
            }
        }
        return AgentProvider.GEMINI.id;
    }

    private String defaultModelId(@NonNull String providerId) {
        ArrayList<AgentRepository.ModelInfo> models = repository.getProviderState(AgentProvider.fromId(providerId)).cachedModels;
        return models.isEmpty() ? null : models.get(0).id;
    }

    private String nullable(String value) {
        return TextUtils.isEmpty(value) ? null : value.trim();
    }

    private static class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {

        interface OnConversationClickListener {
            void onConversationClick(@NonNull ConversationRow row);
        }

        private final ArrayList<ConversationRow> items = new ArrayList<>();
        private final OnConversationClickListener listener;

        ConversationAdapter(OnConversationClickListener listener) {
            this.listener = listener;
        }

        void submit(@NonNull List<AgentRepository.Conversation> conversations) {
            items.clear();
            for (AgentRepository.Conversation conversation : conversations) {
                items.add(new ConversationRow(conversation));
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ItemAgentConversationBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ConversationRow item = items.get(position);
            holder.binding.title.setText(item.conversation.title);
            holder.binding.subtitle.setText(AgentProvider.fromId(item.conversation.providerId).displayName
                    + " • " + (TextUtils.isEmpty(item.conversation.modelId) ? "No model selected" : item.conversation.modelId));
            holder.binding.updatedValue.setText(DateUtils.getRelativeTimeSpanString(item.conversation.updatedAt));
            String preview = item.conversation.messages.isEmpty()
                    ? "No messages yet"
                    : item.conversation.messages.get(item.conversation.messages.size() - 1).content;
            holder.binding.preview.setText(preview);
            holder.binding.getRoot().setOnClickListener(v -> listener.onConversationClick(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ItemAgentConversationBinding binding;

            ViewHolder(ItemAgentConversationBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    private static class ConversationRow {
        final AgentRepository.Conversation conversation;

        ConversationRow(AgentRepository.Conversation conversation) {
            this.conversation = conversation;
        }
    }

    private static class WorkspaceProjectAdapter extends RecyclerView.Adapter<WorkspaceProjectAdapter.ViewHolder> {

        interface Listener {
            void onOpenProject(@NonNull HashMap<String, Object> project);

            void onShowProjectMenu(@NonNull View anchor, @NonNull HashMap<String, Object> project);
        }

        private final ArrayList<HashMap<String, Object>> items = new ArrayList<>();
        private final Listener listener;

        WorkspaceProjectAdapter(Listener listener) {
            this.listener = listener;
        }

        void submit(@NonNull List<HashMap<String, Object>> projects) {
            items.clear();
            items.addAll(projects);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ItemAgentWorkspaceProjectBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            HashMap<String, Object> project = items.get(position);
            holder.binding.title.setText(yB.c(project, "my_ws_name"));
            holder.binding.subtitle.setText(yB.c(project, "my_app_name"));
            holder.binding.projectId.setText(yB.c(project, "sc_id"));
            holder.binding.packageName.setText(yB.c(project, "my_sc_pkg_name"));
            holder.binding.openProjectButton.setOnClickListener(v -> listener.onOpenProject(project));
            holder.binding.moreButton.setOnClickListener(v -> listener.onShowProjectMenu(v, project));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ItemAgentWorkspaceProjectBinding binding;

            ViewHolder(ItemAgentWorkspaceProjectBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
