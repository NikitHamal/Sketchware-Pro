package pro.sketchware.ai.bottomsheet;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pro.sketchware.R;
import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolRegistry;

public final class AiToolsBottomSheet {

    public interface OnToolSelectedListener {
        void onToolSelected(@NonNull ToolEntry tool);
    }

    public static final class ToolEntry {
        public final String name;
        public final String description;
        public final String category;

        public ToolEntry(@NonNull String name, @NonNull String description, @NonNull String category) {
            this.name = name;
            this.description = description;
            this.category = category;
        }
    }

    private static String categorize(@NonNull String toolName) {
        if (toolName.contains("layout") || toolName.contains("view") || toolName.contains("screen")
                || toolName.contains("xml") || toolName.contains("widget"))
            return "UI Layout & Design";
        if (toolName.contains("file") || toolName.contains("search_in"))
            return "File Operations";
        if (toolName.contains("project") || toolName.contains("activity") || toolName.contains("template"))
            return "Project Management";
        if (toolName.contains("library") || toolName.contains("maven") || toolName.contains("dependency"))
            return "Library Management";
        if (toolName.contains("resource") || toolName.contains("string") || toolName.contains("color"))
            return "Resources";
        if (toolName.contains("build") || toolName.contains("compile") || toolName.contains("r8"))
            return "Build & Compile";
        if (toolName.contains("block") || toolName.contains("event"))
            return "Block Logic";
        if (toolName.contains("code") || toolName.contains("review") || toolName.contains("analyze")
                || toolName.contains("rtl") || toolName.contains("validate"))
            return "Code Analysis";
        if (toolName.contains("export") || toolName.contains("android_studio"))
            return "Export";
        if (toolName.contains("github"))
            return "GitHub Intelligence";
        if (toolName.contains("web_search") || toolName.contains("logcat") || toolName.contains("resource_optimizer"))
            return "Developer Utilities";
        return "Other";
    }

    public static void show(@NonNull Context context,
                            @NonNull ToolRegistry toolRegistry,
                            @NonNull OnToolSelectedListener listener) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);

        View root = LayoutInflater.from(context)
                .inflate(R.layout.bottom_sheet_ai_tools, null);
        dialog.setContentView(root);

        RecyclerView rv = root.findViewById(R.id.rv_tools);
        rv.setLayoutManager(new LinearLayoutManager(context));

        List<Object> items = buildItems(toolRegistry);
        ToolsAdapter adapter = new ToolsAdapter(items, tool -> {
            dialog.dismiss();
            listener.onToolSelected(tool);
        });
        rv.setAdapter(adapter);

        View btnClose = root.findViewById(R.id.btn_close_tools_sheet);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    @NonNull
    private static List<Object> buildItems(@NonNull ToolRegistry toolRegistry) {
        List<AgentTool> tools = toolRegistry.getAllTools();
        Map<String, List<ToolEntry>> categories = new LinkedHashMap<>();
        for (AgentTool tool : tools) {
            String cat = categorize(tool.getName());
            categories.computeIfAbsent(cat, k -> new ArrayList<>())
                    .add(new ToolEntry(tool.getName(), tool.getDescription(), cat));
        }

        List<Object> items = new ArrayList<>();
        for (Map.Entry<String, List<ToolEntry>> entry : categories.entrySet()) {
            items.add(entry.getKey());
            items.addAll(entry.getValue());
        }
        return items;
    }

    private static final class ToolsAdapter
            extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_HEADER = 0;
        private static final int TYPE_TOOL   = 1;

        private final List<Object>           items;
        private final OnToolSelectedListener listener;

        ToolsAdapter(@NonNull List<Object> items,
                     @NonNull OnToolSelectedListener listener) {
            this.items    = items;
            this.listener = listener;
        }

        @Override public int getItemViewType(int position) {
            return items.get(position) instanceof String ? TYPE_HEADER : TYPE_TOOL;
        }

        @Override public int getItemCount() { return items.size(); }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (type == TYPE_HEADER) {
                View v = inf.inflate(R.layout.item_tools_category_header, parent, false);
                return new HeaderVH(v);
            }
            View v = inf.inflate(R.layout.item_ai_tool_entry, parent, false);
            return new ToolVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
            if (holder instanceof HeaderVH) {
                ((HeaderVH) holder).bind((String) items.get(pos));
            } else {
                ((ToolVH) holder).bind((ToolEntry) items.get(pos), listener);
            }
        }
    }

    private static final class HeaderVH extends RecyclerView.ViewHolder {
        private final TextView tvCategory;

        HeaderVH(@NonNull View itemView) {
            super(itemView);
            tvCategory = itemView.findViewById(R.id.tv_category);
        }

        void bind(@NonNull String category) {
            tvCategory.setText(category);
        }
    }

    private static final class ToolVH extends RecyclerView.ViewHolder {
        private final TextView tvName;
        private final TextView tvDescription;

        ToolVH(@NonNull View itemView) {
            super(itemView);
            tvName        = itemView.findViewById(R.id.tv_tool_name);
            tvDescription = itemView.findViewById(R.id.tv_tool_description);
        }

        void bind(@NonNull ToolEntry tool, @NonNull OnToolSelectedListener listener) {
            tvName.setText(tool.name);
            tvDescription.setText(tool.description);
            itemView.setOnClickListener(v -> listener.onToolSelected(tool));
        }
    }
}