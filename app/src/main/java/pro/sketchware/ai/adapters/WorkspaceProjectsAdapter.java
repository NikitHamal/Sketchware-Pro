package pro.sketchware.ai.adapters;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.databinding.ItemWorkspaceProjectBinding;

public class WorkspaceProjectsAdapter extends RecyclerView.Adapter<WorkspaceProjectsAdapter.ViewHolder> {

    public interface OnProjectActionListener {
        void onRemoveProject(String scId);
    }

    public static class ProjectInfo {
        private final String scId;
        private final String name;
        private final String packageName;

        public ProjectInfo(@NonNull String scId, @NonNull String name, @Nullable String packageName) {
            this.scId = scId;
            this.name = name;
            this.packageName = packageName;
        }

        @NonNull
        public String getScId() {
            return scId;
        }

        @NonNull
        public String getName() {
            return name;
        }

        @Nullable
        public String getPackageName() {
            return packageName;
        }
    }

    private final List<ProjectInfo> projects = new ArrayList<>();
    private final OnProjectActionListener listener;

    public WorkspaceProjectsAdapter(@NonNull OnProjectActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemWorkspaceProjectBinding binding = ItemWorkspaceProjectBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ProjectInfo project = projects.get(position);
        holder.bind(project);
    }

    @Override
    public int getItemCount() {
        return projects.size();
    }

    public void setProjects(@NonNull List<ProjectInfo> newProjects) {
        projects.clear();
        projects.addAll(newProjects);
        notifyDataSetChanged();
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemWorkspaceProjectBinding binding;

        ViewHolder(@NonNull ItemWorkspaceProjectBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull ProjectInfo project) {
            binding.projectName.setText(project.getName());
            binding.projectPackage.setText(
                    TextUtils.isEmpty(project.getPackageName())
                            ? ""
                            : project.getPackageName());

            binding.projectIcon.setImageDrawable(null);

            binding.btnRemoveProject.setOnClickListener(
                    v -> listener.onRemoveProject(project.getScId()));
        }
    }
}
