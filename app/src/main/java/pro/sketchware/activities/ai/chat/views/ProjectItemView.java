package pro.sketchware.activities.ai.chat.views;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.besome.sketch.design.DesignActivity;

import java.io.File;
import java.util.HashMap;

import a.a.a.lC;
import a.a.a.wq;
import a.a.a.yB;
import mod.hey.studios.project.ProjectSettings;
import mod.hey.studios.project.ProjectTracker;
import pro.sketchware.R;
import pro.sketchware.databinding.ChatProjectItemBinding;

public class ProjectItemView extends FrameLayout {
    private static final String TAG = "ProjectItemView";
    private static final long UPDATE_INTERVAL = 2000; // Check for updates every 2 seconds
    
    private ChatProjectItemBinding binding;
    private String projectId;
    private boolean isDeleted = false;
    private Handler updateHandler;
    private Runnable updateRunnable;
    private HashMap<String, Object> lastKnownProjectData;

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
        setupUpdateHandler();
    }
    
    private void setupUpdateHandler() {
        updateHandler = new Handler(Looper.getMainLooper());
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (projectId != null) {
                    updateProjectData();
                    // Schedule next update if not deleted
                    if (!isDeleted) {
                        updateHandler.postDelayed(this, UPDATE_INTERVAL);
                    }
                }
            }
        };
    }

    private void setupClickListeners() {
        binding.getRoot().setOnClickListener(v -> openProject());
        binding.openProjectButton.setOnClickListener(v -> openProject());
    }

    public void setProjectData(String projectId, String projectName, String appName, String packageName) {
        this.projectId = projectId;
        
        // Load fresh data immediately
        updateProjectData();
        
        // Start periodic updates
        startPeriodicUpdates();
    }
    
    public void setProjectId(String projectId) {
        this.projectId = projectId;
        updateProjectData();
        startPeriodicUpdates();
    }
    
    private void startPeriodicUpdates() {
        // Stop any existing updates
        stopPeriodicUpdates();
        
        // Start new update cycle
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.post(updateRunnable);
        }
    }
    
    private void stopPeriodicUpdates() {
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
    }
    
    private void updateProjectData() {
        if (projectId == null) {
            showDeletedState();
            return;
        }
        
        try {
            HashMap<String, Object> currentProjectData = lC.b(projectId);
            
            if (currentProjectData == null) {
                // Project has been deleted
                showDeletedState();
                return;
            }
            
            // Check if data has changed
            if (hasProjectDataChanged(currentProjectData)) {
                // Update UI with fresh data
                displayProjectData(currentProjectData);
                lastKnownProjectData = new HashMap<>(currentProjectData);
            }
            
            // Ensure we're not showing deleted state
            if (isDeleted) {
                showNormalState();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating project data for ID: " + projectId, e);
            showDeletedState();
        }
    }
    
    private boolean hasProjectDataChanged(HashMap<String, Object> newData) {
        if (lastKnownProjectData == null) {
            return true; // First time loading
        }
        
        // Check key fields for changes
        String[] keyFields = {"my_ws_name", "my_app_name", "my_sc_pkg_name", "custom_icon", "isIconAdaptive"};
        for (String field : keyFields) {
            Object oldValue = lastKnownProjectData.get(field);
            Object newValue = newData.get(field);
            
            if (oldValue == null && newValue != null) return true;
            if (oldValue != null && !oldValue.equals(newValue)) return true;
        }
        
        return false;
    }
    
    private void displayProjectData(HashMap<String, Object> projectData) {
        try {
            // Extract data with fallbacks
            String projectName = yB.c(projectData, "my_ws_name");
            String appName = yB.c(projectData, "my_app_name");
            String packageName = yB.c(projectData, "my_sc_pkg_name");
            
            // Update text fields
            binding.projectName.setText(projectName != null ? projectName : "Unknown Project");
            binding.appName.setText(appName != null ? appName : "Unknown App");
            binding.packageName.setText(packageName != null ? packageName : "com.unknown.package");
            
            // Load project icon
            loadProjectIcon(projectData);
            
            Log.d(TAG, "Updated project card for ID: " + projectId + " - " + projectName);
            
        } catch (Exception e) {
            Log.e(TAG, "Error displaying project data", e);
            showErrorState();
        }
    }
    
    private void showDeletedState() {
        if (!isDeleted) {
            isDeleted = true;
            
            // Update UI to show deleted state
            binding.projectName.setText("Project Deleted");
            binding.appName.setText("This project no longer exists");
            binding.packageName.setText("ID: " + (projectId != null ? projectId : "Unknown"));
            
            // Set deleted icon and styling
            binding.projectIcon.setImageResource(R.drawable.ic_delete_grey_48dp);
            binding.getRoot().setAlpha(0.6f);
            binding.openProjectButton.setEnabled(false);
            binding.openProjectButton.setText("Deleted");
            
            // Stop further updates
            stopPeriodicUpdates();
            
            Log.d(TAG, "Project marked as deleted: " + projectId);
        }
    }
    
    private void showNormalState() {
        if (isDeleted) {
            isDeleted = false;
            
            // Restore normal styling
            binding.getRoot().setAlpha(1.0f);
            binding.openProjectButton.setEnabled(true);
            binding.openProjectButton.setText("Open");
            
            Log.d(TAG, "Project restored to normal state: " + projectId);
        }
    }
    
    private void showErrorState() {
        binding.projectName.setText("Error Loading Project");
        binding.appName.setText("Unable to load project data");
        binding.packageName.setText("ID: " + (projectId != null ? projectId : "Unknown"));
        binding.projectIcon.setImageResource(R.drawable.ic_warning_96dp);
    }

    private void loadProjectIcon(HashMap<String, Object> projectData) {
        try {
            boolean hasCustomIcon = yB.a(projectData, "custom_icon");
            
            if (hasCustomIcon) {
                // Try to load custom icon
                File iconFile = getCustomIconFile();
                if (iconFile != null && iconFile.exists()) {
                    try {
                        android.net.Uri iconUri = FileProvider.getUriForFile(
                            getContext(),
                            getContext().getPackageName() + ".provider",
                            iconFile
                        );
                        binding.projectIcon.setImageURI(iconUri);
                        return;
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to load custom icon for project " + projectId, e);
                    }
                }
            }
            
            // Fallback to default icon
            binding.projectIcon.setImageResource(R.drawable.default_icon);
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading project icon", e);
            binding.projectIcon.setImageResource(R.drawable.default_icon);
        }
    }
    
    private File getCustomIconFile() {
        if (projectId == null) return null;
        
        try {
            String iconPath = wq.e() + File.separator + projectId + File.separator + "icon.png";
            return new File(iconPath);
        } catch (Exception e) {
            Log.e(TAG, "Error getting custom icon file path", e);
            return null;
        }
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // Clean up to prevent memory leaks
        stopPeriodicUpdates();
    }
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Resume updates when attached
        if (projectId != null && !isDeleted) {
            startPeriodicUpdates();
        }
    }
    
    // Public method to manually refresh the project data
    public void refreshProjectData() {
        if (projectId != null) {
            updateProjectData();
        }
    }
    
    // Public method to check if project is deleted
    public boolean isProjectDeleted() {
        return isDeleted;
    }
    
    // Public method to get current project ID
    public String getProjectId() {
        return projectId;
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