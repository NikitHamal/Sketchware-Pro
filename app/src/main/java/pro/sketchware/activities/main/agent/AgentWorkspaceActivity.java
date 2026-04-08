package pro.sketchware.activities.main.agent;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import com.besome.sketch.lib.base.BaseAppCompatActivity;

import pro.sketchware.ai.AgentStorage;
import pro.sketchware.ai.AgentWorkspace;
import pro.sketchware.databinding.ActivityAgentWorkspaceBinding;
import pro.sketchware.utility.UI;

public class AgentWorkspaceActivity extends BaseAppCompatActivity {
    public static final String EXTRA_WORKSPACE_ID = "workspace_id";

    private ActivityAgentWorkspaceBinding binding;
    private AgentWorkspace workspace;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityAgentWorkspaceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String workspaceId = getIntent().getStringExtra(EXTRA_WORKSPACE_ID);
        workspace = AgentStorage.getInstance().getWorkspace(workspaceId);
        if (workspace == null) {
            finish();
            return;
        }

        binding.toolbar.setTitle(workspace.name);
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());

        UI.addSystemWindowInsetToPadding(binding.toolbar, true, true, true, false);
        UI.addSystemWindowInsetToPadding(binding.viewPager, false, false, false, true);

        WorkspacePagerAdapter pagerAdapter = new WorkspacePagerAdapter(getSupportFragmentManager(), workspace.id);
        binding.viewPager.setAdapter(pagerAdapter);
        binding.tabs.setupWithViewPager(binding.viewPager);
    }

    private static class WorkspacePagerAdapter extends FragmentPagerAdapter {
        private final String workspaceId;

        WorkspacePagerAdapter(@NonNull FragmentManager fm, String workspaceId) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            this.workspaceId = workspaceId;
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            if (position == 0) {
                return WorkspaceConversationsFragment.newInstance(workspaceId);
            }
            return WorkspaceProjectsFragment.newInstance(workspaceId);
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            if (position == 0) {
                return "Conversations";
            }
            return "Projects";
        }
    }
}
