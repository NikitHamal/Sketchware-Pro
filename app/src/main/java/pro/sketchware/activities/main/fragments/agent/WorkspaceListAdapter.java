package pro.sketchware.activities.main.fragments.agent;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.ai.AgentStorage;
import pro.sketchware.ai.AgentWorkspace;
import pro.sketchware.databinding.ItemAgentWorkspaceBinding;

public class WorkspaceListAdapter extends RecyclerView.Adapter<WorkspaceListAdapter.ViewHolder> {
    private final AgentStorage storage = AgentStorage.getInstance();
    private final List<AgentWorkspace> workspaces = new ArrayList<>();
    private final OnWorkspaceClickListener listener;

    interface OnWorkspaceClickListener {
        void onWorkspaceClicked(AgentWorkspace workspace);

        void onWorkspaceLongClicked(AgentWorkspace workspace);
    }

    WorkspaceListAdapter(OnWorkspaceClickListener listener) {
        this.listener = listener;
    }

    void submit(List<AgentWorkspace> items) {
        workspaces.clear();
        workspaces.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAgentWorkspaceBinding binding = ItemAgentWorkspaceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AgentWorkspace workspace = workspaces.get(position);
        int conversationCount = storage.getConversations(workspace.id).size();
        int projectCount = workspace.projectIds.size();

        holder.binding.workspaceName.setText(workspace.name);
        holder.binding.workspaceMeta.setText(projectCount + " projects • " + conversationCount + " conversations");
        holder.binding.getRoot().setOnClickListener(v -> listener.onWorkspaceClicked(workspace));
        holder.binding.getRoot().setOnLongClickListener(v -> {
            listener.onWorkspaceLongClicked(workspace);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return workspaces.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemAgentWorkspaceBinding binding;

        ViewHolder(ItemAgentWorkspaceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
