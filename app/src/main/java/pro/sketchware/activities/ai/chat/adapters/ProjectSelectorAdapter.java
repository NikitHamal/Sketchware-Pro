package pro.sketchware.activities.ai.chat.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.List;

import a.a.a.yB;
import pro.sketchware.R;

public class ProjectSelectorAdapter extends RecyclerView.Adapter<ProjectSelectorAdapter.ProjectViewHolder> {
    
    private final List<HashMap<String, Object>> projects;
    private final OnProjectClickListener listener;
    
    public interface OnProjectClickListener {
        void onProjectClick(String projectId, String appName);
    }
    
    public ProjectSelectorAdapter(List<HashMap<String, Object>> projects, OnProjectClickListener listener) {
        this.projects = projects;
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public ProjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_project_selector, parent, false);
        return new ProjectViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ProjectViewHolder holder, int position) {
        HashMap<String, Object> project = projects.get(position);
        
        String appName = yB.c(project, "my_app_name");
        String projectId = yB.c(project, "sc_id");
        
        holder.appNameText.setText(appName);
        holder.projectIdText.setText("Project ID: " + projectId);
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onProjectClick(projectId, appName);
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return projects.size();
    }
    
    static class ProjectViewHolder extends RecyclerView.ViewHolder {
        TextView appNameText;
        TextView projectIdText;
        
        public ProjectViewHolder(@NonNull View itemView) {
            super(itemView);
            appNameText = itemView.findViewById(R.id.app_name);
            projectIdText = itemView.findViewById(R.id.project_id);
        }
    }
}