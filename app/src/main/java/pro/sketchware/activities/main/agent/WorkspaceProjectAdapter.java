package pro.sketchware.activities.main.agent;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import a.a.a.yB;
import pro.sketchware.databinding.ItemAgentWorkspaceProjectBinding;

public class WorkspaceProjectAdapter extends RecyclerView.Adapter<WorkspaceProjectAdapter.ViewHolder> {
    interface OnProjectSelectionListener {
        void onProjectSelectionChanged(String scId, boolean selected);
    }

    private final List<HashMap<String, Object>> projects = new ArrayList<>();
    private final Set<String> selectedProjectIds;
    private final OnProjectSelectionListener listener;

    WorkspaceProjectAdapter(Set<String> selectedProjectIds, OnProjectSelectionListener listener) {
        this.selectedProjectIds = selectedProjectIds;
        this.listener = listener;
    }

    void submit(List<HashMap<String, Object>> allProjects) {
        projects.clear();
        projects.addAll(allProjects);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAgentWorkspaceProjectBinding binding = ItemAgentWorkspaceProjectBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HashMap<String, Object> project = projects.get(position);
        String scId = yB.c(project, "sc_id");
        holder.binding.projectName.setText(yB.c(project, "my_ws_name"));
        holder.binding.projectMeta.setText(
                yB.c(project, "my_sc_pkg_name") + " • " + yB.c(project, "sc_ver_name") + " (" + yB.c(project, "sc_ver_code") + ")"
        );

        holder.binding.projectSelected.setOnCheckedChangeListener(null);
        holder.binding.projectSelected.setChecked(selectedProjectIds.contains(scId));
        holder.binding.projectSelected.setOnCheckedChangeListener((buttonView, isChecked) ->
                listener.onProjectSelectionChanged(scId, isChecked));
        holder.binding.getRoot().setOnClickListener(v -> {
            boolean nextState = !holder.binding.projectSelected.isChecked();
            holder.binding.projectSelected.setChecked(nextState);
        });
    }

    @Override
    public int getItemCount() {
        return projects.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemAgentWorkspaceProjectBinding binding;

        ViewHolder(ItemAgentWorkspaceProjectBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
