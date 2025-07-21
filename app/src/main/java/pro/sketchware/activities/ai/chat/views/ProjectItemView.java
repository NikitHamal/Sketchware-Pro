package pro.sketchware.activities.ai.chat.views;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.besome.sketch.design.DesignActivity;

import java.util.HashMap;

import a.a.a.lC;
import a.a.a.yB;
import mod.hey.studios.project.ProjectTracker;
import pro.sketchware.R;
import pro.sketchware.databinding.ChatProjectItemBinding;

public class ProjectItemView extends FrameLayout {
    private ChatProjectItemBinding binding;
    private String projectId;

    public ProjectItemView(@NonNull Context context) {
        super(context);
        init();
    }

    public ProjectItemView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ProjectItemView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        binding = ChatProjectItemBinding.inflate(LayoutInflater.from(getContext()), this, true);
        setupClickListeners();
    }

    private void setupClickListeners() {
        binding.getRoot().setOnClickListener(v -> openProject());
        binding.openProjectButton.setOnClickListener(v -> openProject());
    }

    public void setProjectData(String projectId, String projectName, String appName, String packageName) {
        this.projectId = projectId;
        
        binding.projectName.setText(projectName);
        binding.appName.setText(appName);
        binding.packageName.setText(packageName);
        
        // Set default project icon
        binding.projectIcon.setImageResource(R.drawable.default_icon);
        
        // Load custom icon if exists
        loadProjectIcon();
    }

    private void loadProjectIcon() {
        if (projectId != null) {
            try {
                HashMap<String, Object> projectData = lC.b(projectId);
                if (projectData != null && yB.a(projectData, "custom_icon")) {
                    // Load custom icon - implementation would depend on how icons are stored
                    // For now, use default
                }
            } catch (Exception e) {
                // Use default icon
            }
        }
    }

    private void openProject() {
        if (projectId != null) {
            Intent intent = new Intent(getContext(), DesignActivity.class);
            ProjectTracker.setScId(projectId);
            intent.putExtra("sc_id", projectId);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            getContext().startActivity(intent);
        }
    }
}