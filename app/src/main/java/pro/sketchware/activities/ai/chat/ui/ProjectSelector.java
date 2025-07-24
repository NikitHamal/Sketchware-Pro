package pro.sketchware.activities.ai.chat.ui;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import a.a.a.lC;
import a.a.a.yB;
import pro.sketchware.activities.ai.chat.adapters.ProjectSelectorAdapter;

public class ProjectSelector {

    private final RecyclerView recyclerView;
    private final OnProjectSelectedListener listener;
    private final ProjectSelectorAdapter adapter;
    private final List<HashMap<String, Object>> availableProjects = new ArrayList<>();

    public interface OnProjectSelectedListener {
        void onProjectSelected(String projectId, String appName);
    }

    public ProjectSelector(Context context, RecyclerView recyclerView, OnProjectSelectedListener listener) {
        this.recyclerView = recyclerView;
        this.listener = listener;
        this.adapter = new ProjectSelectorAdapter(availableProjects, (projectId, appName) -> {
            if (this.listener != null) {
                this.listener.onProjectSelected(projectId, appName);
            }
        });
        this.recyclerView.setLayoutManager(new LinearLayoutManager(context));
        this.recyclerView.setAdapter(adapter);
        loadAvailableProjects();
    }

    private void loadAvailableProjects() {
        availableProjects.clear();
        List<HashMap<String, Object>> allProjects = lC.a(); // Load all projects

        // Sort projects by ID (latest first - higher ID = newer project)
        allProjects.sort((p1, p2) -> {
            try {
                int id1 = Integer.parseInt(yB.c(p1, "sc_id"));
                int id2 = Integer.parseInt(yB.c(p2, "sc_id"));
                return Integer.compare(id2, id1); // Descending order
            } catch (NumberFormatException e) {
                return 0;
            }
        });

        // Limit to 3 projects for compact display
        int maxProjects = Math.min(3, allProjects.size());
        for (int i = 0; i < maxProjects; i++) {
            availableProjects.add(allProjects.get(i));
        }
        adapter.notifyDataSetChanged();
    }

    public void show() {
        recyclerView.setVisibility(View.VISIBLE);
    }

    public void hide() {
        recyclerView.setVisibility(View.GONE);
    }
}
