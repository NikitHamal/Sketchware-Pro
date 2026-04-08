package pro.sketchware.agent.ui.workspace;

import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import a.a.a.lC;
import a.a.a.yB;
import pro.sketchware.R;
import pro.sketchware.agent.data.AgentDatabase;
import pro.sketchware.agent.ui.chat.ChatActivity;
import pro.sketchware.databinding.ActivityWorkspaceBinding;

public class WorkspaceActivity extends BaseAppCompatActivity {

    private ActivityWorkspaceBinding binding;
    private AgentDatabase db;
    private String workspaceId;
    private String workspaceName;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityWorkspaceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = AgentDatabase.getInstance(this);
        workspaceId = getIntent().getStringExtra("workspace_id");
        workspaceName = getIntent().getStringExtra("workspace_name");

        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(workspaceName);
        }

        WorkspacePagerAdapter pagerAdapter = new WorkspacePagerAdapter(this);
        binding.viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(binding.tabLayout, binding.viewPager,
                (tab, position) -> {
                    switch (position) {
                        case 0 -> tab.setText("Conversations");
                        case 1 -> tab.setText("Projects");
                    }
                }).attach();

        binding.fabAction.setOnClickListener(v -> {
            int currentTab = binding.viewPager.getCurrentItem();
            if (currentTab == 0) {
                showCreateConversationDialog();
            } else {
                showAddProjectDialog();
            }
        });

        binding.viewPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position == 0) {
                    binding.fabAction.setText("New Conversation");
                    binding.fabAction.setIconResource(R.drawable.ic_chat);
                } else {
                    binding.fabAction.setText("Add Project");
                    binding.fabAction.setIconResource(R.drawable.ic_mtrl_add);
                }
            }
        });
    }

    private void showCreateConversationDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_workspace, null);
        TextInputEditText nameInput = dialogView.findViewById(R.id.input_name);
        dialogView.findViewById(R.id.input_description).setVisibility(View.GONE);
        ((View) dialogView.findViewById(R.id.input_description).getParent()).setVisibility(View.GONE);

        new MaterialAlertDialogBuilder(this)
                .setTitle("New Conversation")
                .setView(dialogView)
                .setPositiveButton("Create", (dialog, which) -> {
                    String title = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
                    if (title.isEmpty()) title = "New conversation";

                    AgentDatabase.Conversation conv = new AgentDatabase.Conversation();
                    conv.id = UUID.randomUUID().toString();
                    conv.workspaceId = workspaceId;
                    conv.title = title;
                    conv.provider = "";
                    conv.model = "";
                    conv.createdAt = System.currentTimeMillis();
                    conv.updatedAt = System.currentTimeMillis();
                    db.insertConversation(conv);

                    openConversation(conv);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddProjectDialog() {
        List<HashMap<String, Object>> allProjects = lC.a();
        List<String> existingIds = db.getWorkspaceProjectIds(workspaceId);

        List<HashMap<String, Object>> available = new ArrayList<>();
        for (HashMap<String, Object> p : allProjects) {
            String scId = yB.c(p, "sc_id");
            if (!existingIds.contains(scId)) {
                available.add(p);
            }
        }

        if (available.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("No projects available")
                    .setMessage("All projects are already added to this workspace, or no projects exist yet.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        String[] names = new String[available.size()];
        boolean[] checked = new boolean[available.size()];
        for (int i = 0; i < available.size(); i++) {
            HashMap<String, Object> p = available.get(i);
            names[i] = yB.c(p, "my_app_name") + " (" + yB.c(p, "sc_id") + ")";
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Add Projects")
                .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Add", (dialog, which) -> {
                    for (int i = 0; i < available.size(); i++) {
                        if (checked[i]) {
                            String scId = yB.c(available.get(i), "sc_id");
                            db.addProjectToWorkspace(workspaceId, scId);
                        }
                    }
                    refreshFragments();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openConversation(AgentDatabase.Conversation conv) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("conversation_id", conv.id);
        intent.putExtra("workspace_id", workspaceId);
        intent.putExtra("workspace_name", workspaceName);
        startActivity(intent);
    }

    private void refreshFragments() {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof Refreshable) {
                ((Refreshable) fragment).refresh();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshFragments();
    }

    public interface Refreshable {
        void refresh();
    }

    private class WorkspacePagerAdapter extends FragmentStateAdapter {
        WorkspacePagerAdapter(FragmentActivity activity) {
            super(activity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return switch (position) {
                case 0 -> ConversationsFragment.newInstance(workspaceId, workspaceName);
                case 1 -> ProjectsTabFragment.newInstance(workspaceId);
                default -> throw new IllegalArgumentException();
            };
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }

    // Conversations Tab Fragment
    public static class ConversationsFragment extends Fragment implements Refreshable {
        private RecyclerView recyclerView;
        private View emptyState;
        private AgentDatabase db;
        private String workspaceId;
        private String workspaceName;
        private final List<AgentDatabase.Conversation> conversations = new ArrayList<>();
        private ConversationAdapter adapter;

        static ConversationsFragment newInstance(String workspaceId, String workspaceName) {
            ConversationsFragment f = new ConversationsFragment();
            Bundle args = new Bundle();
            args.putString("workspace_id", workspaceId);
            args.putString("workspace_name", workspaceName);
            f.setArguments(args);
            return f;
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_workspace_conversations, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            workspaceId = requireArguments().getString("workspace_id");
            workspaceName = requireArguments().getString("workspace_name");
            db = AgentDatabase.getInstance(requireContext());

            recyclerView = view.findViewById(R.id.conversations_list);
            emptyState = view.findViewById(R.id.empty_state);

            adapter = new ConversationAdapter();
            recyclerView.setAdapter(adapter);

            refresh();
        }

        @Override
        public void refresh() {
            if (db == null || !isAdded()) return;
            conversations.clear();
            conversations.addAll(db.getConversationsForWorkspace(workspaceId));
            if (adapter != null) adapter.notifyDataSetChanged();
            updateEmptyState();
        }

        @Override
        public void onResume() {
            super.onResume();
            refresh();
        }

        private void updateEmptyState() {
            if (conversations.isEmpty()) {
                emptyState.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                emptyState.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        }

        private class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.VH> {
            @NonNull
            @Override
            public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_conversation, parent, false);
                return new VH(v);
            }

            @Override
            public void onBindViewHolder(@NonNull VH holder, int position) {
                AgentDatabase.Conversation conv = conversations.get(position);
                holder.title.setText(conv.title);

                String providerName = conv.provider.isEmpty() ? "Not set" : conv.provider;
                String modelName = conv.model.isEmpty() ? "" : " · " + conv.model;
                CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                        conv.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
                holder.info.setText(providerName + modelName + " · " + timeAgo);

                holder.itemView.setOnClickListener(v -> {
                    Intent intent = new Intent(requireContext(), ChatActivity.class);
                    intent.putExtra("conversation_id", conv.id);
                    intent.putExtra("workspace_id", workspaceId);
                    intent.putExtra("workspace_name", workspaceName);
                    startActivity(intent);
                });

                holder.menu.setOnClickListener(v -> {
                    PopupMenu popup = new PopupMenu(requireContext(), v);
                    popup.getMenu().add("Rename");
                    popup.getMenu().add("Delete");
                    popup.setOnMenuItemClickListener(item -> {
                        if ("Delete".equals(item.getTitle())) {
                            new MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Delete Conversation")
                                    .setMessage("Delete \"" + conv.title + "\"?")
                                    .setPositiveButton("Delete", (d, w) -> {
                                        db.deleteConversation(conv.id);
                                        refresh();
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                            return true;
                        } else if ("Rename".equals(item.getTitle())) {
                            View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_workspace, null);
                            TextInputEditText nameInput = dialogView.findViewById(R.id.input_name);
                            nameInput.setText(conv.title);
                            dialogView.findViewById(R.id.input_description).setVisibility(View.GONE);
                            ((View) dialogView.findViewById(R.id.input_description).getParent()).setVisibility(View.GONE);

                            new MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Rename")
                                    .setView(dialogView)
                                    .setPositiveButton("Save", (d, w) -> {
                                        String newTitle = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
                                        if (!newTitle.isEmpty()) {
                                            conv.title = newTitle;
                                            db.updateConversation(conv);
                                            refresh();
                                        }
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                            return true;
                        }
                        return false;
                    });
                    popup.show();
                });
            }

            @Override
            public int getItemCount() {
                return conversations.size();
            }

            class VH extends RecyclerView.ViewHolder {
                TextView title, info;
                ImageView menu;

                VH(View itemView) {
                    super(itemView);
                    title = itemView.findViewById(R.id.conversation_title);
                    info = itemView.findViewById(R.id.conversation_info);
                    menu = itemView.findViewById(R.id.btn_conv_menu);
                }
            }
        }
    }

    // Projects Tab Fragment
    public static class ProjectsTabFragment extends Fragment implements Refreshable {
        private RecyclerView recyclerView;
        private View emptyState;
        private View projectListContainer;
        private AgentDatabase db;
        private String workspaceId;
        private final List<ProjectItem> projects = new ArrayList<>();
        private ProjectAdapter adapter;

        static class ProjectItem {
            String scId;
            String appName;
            String packageName;
        }

        static ProjectsTabFragment newInstance(String workspaceId) {
            ProjectsTabFragment f = new ProjectsTabFragment();
            Bundle args = new Bundle();
            args.putString("workspace_id", workspaceId);
            f.setArguments(args);
            return f;
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_workspace_projects, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            workspaceId = requireArguments().getString("workspace_id");
            db = AgentDatabase.getInstance(requireContext());

            recyclerView = view.findViewById(R.id.projects_list);
            emptyState = view.findViewById(R.id.empty_state);
            projectListContainer = view.findViewById(R.id.project_list_container);

            adapter = new ProjectAdapter();
            recyclerView.setAdapter(adapter);

            view.findViewById(R.id.btn_add_project).setOnClickListener(v -> {
                if (getActivity() instanceof WorkspaceActivity) {
                    ((WorkspaceActivity) getActivity()).showAddProjectDialog();
                }
            });

            refresh();
        }

        @Override
        public void refresh() {
            if (db == null || !isAdded()) return;
            projects.clear();
            List<String> ids = db.getWorkspaceProjectIds(workspaceId);
            for (String scId : ids) {
                HashMap<String, Object> p = lC.b(scId);
                if (p != null) {
                    ProjectItem item = new ProjectItem();
                    item.scId = scId;
                    item.appName = yB.c(p, "my_app_name");
                    item.packageName = yB.c(p, "my_sc_pkg_name");
                    projects.add(item);
                }
            }
            if (adapter != null) adapter.notifyDataSetChanged();
            updateEmptyState();
        }

        @Override
        public void onResume() {
            super.onResume();
            refresh();
        }

        private void updateEmptyState() {
            if (projects.isEmpty()) {
                emptyState.setVisibility(View.VISIBLE);
                projectListContainer.setVisibility(View.GONE);
            } else {
                emptyState.setVisibility(View.GONE);
                projectListContainer.setVisibility(View.VISIBLE);
            }
        }

        private class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.VH> {
            @NonNull
            @Override
            public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_workspace_project, parent, false);
                return new VH(v);
            }

            @Override
            public void onBindViewHolder(@NonNull VH holder, int position) {
                ProjectItem item = projects.get(position);
                holder.name.setText(item.appName);
                holder.pkg.setText(item.packageName + " · ID: " + item.scId);
                holder.remove.setOnClickListener(v -> {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Remove Project")
                            .setMessage("Remove \"" + item.appName + "\" from this workspace? The project itself won't be deleted.")
                            .setPositiveButton("Remove", (d, w) -> {
                                db.removeProjectFromWorkspace(workspaceId, item.scId);
                                refresh();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });
            }

            @Override
            public int getItemCount() {
                return projects.size();
            }

            class VH extends RecyclerView.ViewHolder {
                TextView name, pkg;
                ImageView remove;

                VH(View itemView) {
                    super(itemView);
                    name = itemView.findViewById(R.id.project_name);
                    pkg = itemView.findViewById(R.id.project_package);
                    remove = itemView.findViewById(R.id.btn_remove_project);
                }
            }
        }
    }
}
