package pro.sketchware.ai.tools;

import android.content.Context;
import android.content.Intent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;

import pro.sketchware.ai.models.ToolResult;

/**
 * Tools for managing view layouts within Sketchware Pro projects.
 *
 * بعد كل edit_layout، يُرسل broadcast بـ ACTION_LAYOUT_CHANGED.
 * DesignActivity يستقبل هذا الـ broadcast ويُعيد تحميل الـ view مباشرةً
 * في Design Editor — بدون إغلاق وإعادة فتح المشروع.
 */
public final class LayoutTools {

    /**
     * Action يُرسله LayoutTools بعد تعديل layout.
     * DesignActivity يسجّل Receiver لهذا الـ action ويُعيد initialize.
     */
    public static final String ACTION_LAYOUT_CHANGED =
            "pro.sketchware.ai.ACTION_LAYOUT_CHANGED";

    public static final String EXTRA_SC_ID = "sc_id";
    public static final String EXTRA_ACTIVITY_NAME = "activity_name";

    private LayoutTools() {}

    private static ToolResult success(String output) {
        return new ToolResult(null, true, output, null);
    }

    private static ToolResult error(String message) {
        return new ToolResult(null, false, null, message);
    }

    private static String readFileContent(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            char[] buf = new char[4096];
            int read;
            while ((read = reader.read(buf)) != -1) sb.append(buf, 0, read);
        }
        return sb.toString();
    }

    private static void writeFileContent(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    private static JsonArray readViewArray(File viewFile) throws IOException, JsonSyntaxException {
        if (!viewFile.exists()) return new JsonArray();
        String content = readFileContent(viewFile);
        if (content.trim().isEmpty()) return new JsonArray();
        JsonElement element = JsonParser.parseString(content);
        return element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    private static JsonObject findViewEntry(JsonArray viewArray, String activityName) {
        String viewId = activityName + ".xml";
        for (JsonElement element : viewArray) {
            if (element.isJsonObject()) {
                JsonObject entry = element.getAsJsonObject();
                if (entry.has("id") && entry.get("id").getAsString().equals(viewId))
                    return entry;
            }
        }
        return null;
    }

    /**
     * يُرسل broadcast لـ DesignActivity لتحديث الـ view مباشرةً بعد الكتابة.
     */
    private static void notifyLayoutChanged(Context ctx, String scId, String activityName) {
        try {
            Intent intent = new Intent(ACTION_LAYOUT_CHANGED);
            intent.putExtra(EXTRA_SC_ID, scId);
            intent.putExtra(EXTRA_ACTIVITY_NAME, activityName);
            ctx.sendBroadcast(intent);
        } catch (Exception ignored) {}
    }

    // ────────────────────────────────────────────────────────────────
    // Tool: get_layout
    // ────────────────────────────────────────────────────────────────
    public static class GetLayoutTool implements AgentTool {

        @Override public String getName() { return "get_layout"; }

        @Override
        public String getDescription() {
            return "Gets the view hierarchy (layout) of an activity in a Sketchware Pro project. "
                    + "Returns the root view and all child views as a JSON structure.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject props = new JsonObject();
            JsonObject scIdP = new JsonObject();
            scIdP.addProperty("type", "string");
            scIdP.addProperty("description", "The project SC ID");
            props.add("sc_id", scIdP);

            JsonObject nameP = new JsonObject();
            nameP.addProperty("type", "string");
            nameP.addProperty("description", "Activity name (e.g., \"main\")");
            props.add("activity_name", nameP);

            JsonArray req = new JsonArray();
            req.add("sc_id");
            req.add("activity_name");

            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", props);
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            if (!args.has("sc_id") || args.get("sc_id").isJsonNull())
                return error("Missing required parameter: sc_id");
            if (!args.has("activity_name") || args.get("activity_name").isJsonNull())
                return error("Missing required parameter: activity_name");

            String scId = args.get("sc_id").getAsString();
            String actName = args.get("activity_name").getAsString();

            if (!ctx.isProjectAllowed(scId))
                return error("Access denied: project " + scId + " is not in the current workspace");

            File viewFile = new File(ctx.getProjectDataDir(scId), "view");
            try {
                JsonArray viewArray = readViewArray(viewFile);
                JsonObject entry = findViewEntry(viewArray, actName);
                if (entry == null) return error("Layout not found for activity: " + actName);
                return success(entry.toString());
            } catch (IOException e) {
                return error("Failed to read layout: " + e.getMessage());
            } catch (JsonSyntaxException e) {
                return error("View file contains invalid JSON: " + e.getMessage());
            }
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Tool: edit_layout — يكتب في الـ view ثم يُحدّث Design Editor
    // ────────────────────────────────────────────────────────────────
    public static class EditLayoutTool implements AgentTool {

        @Override public String getName() { return "edit_layout"; }

        @Override
        public String getDescription() {
            return "Edits the view layout of an activity by performing operations like "
                    + "adding views, removing views, or setting view properties. "
                    + "Changes are reflected IMMEDIATELY in the Design Editor view — "
                    + "no need to close or reopen the project. "
                    + "Operations: 'add_view' (requires 'view' object), "
                    + "'remove_view' (requires 'view_id'), "
                    + "'set_property' (requires 'view_id', 'property', 'value').";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject props = new JsonObject();

            JsonObject scIdP = new JsonObject();
            scIdP.addProperty("type", "string");
            scIdP.addProperty("description", "The project SC ID");
            props.add("sc_id", scIdP);

            JsonObject nameP = new JsonObject();
            nameP.addProperty("type", "string");
            nameP.addProperty("description", "Activity name (e.g., \"main\")");
            props.add("activity_name", nameP);

            JsonObject opsP = new JsonObject();
            opsP.addProperty("type", "array");
            opsP.addProperty("description",
                    "Array of operations. Each has a 'type' field: "
                            + "'add_view' requires 'view' object; "
                            + "'remove_view' requires 'view_id'; "
                            + "'set_property' requires 'view_id', 'property', 'value'.");
            JsonObject itemSchema = new JsonObject();
            itemSchema.addProperty("type", "object");
            opsP.add("items", itemSchema);
            props.add("view_operations", opsP);

            JsonArray req = new JsonArray();
            req.add("sc_id");
            req.add("activity_name");
            req.add("view_operations");

            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", props);
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            if (!args.has("sc_id") || args.get("sc_id").isJsonNull())
                return error("Missing required parameter: sc_id");
            if (!args.has("activity_name") || args.get("activity_name").isJsonNull())
                return error("Missing required parameter: activity_name");
            if (!args.has("view_operations") || !args.get("view_operations").isJsonArray())
                return error("Missing required parameter: view_operations (must be an array)");

            String scId = args.get("sc_id").getAsString();
            String actName = args.get("activity_name").getAsString();
            JsonArray ops = args.getAsJsonArray("view_operations");

            if (!ctx.isProjectAllowed(scId))
                return error("Access denied: project " + scId + " is not in the current workspace");

            File viewFile = new File(ctx.getProjectDataDir(scId), "view");
            try {
                JsonArray viewArray = readViewArray(viewFile);
                JsonObject viewEntry = findViewEntry(viewArray, actName);
                if (viewEntry == null) return error("Layout not found for activity: " + actName);

                JsonArray results = new JsonArray();
                for (JsonElement opEl : ops) {
                    if (!opEl.isJsonObject()) {
                        JsonObject r = new JsonObject();
                        r.addProperty("success", false);
                        r.addProperty("error", "Operation must be a JSON object");
                        results.add(r);
                        continue;
                    }
                    JsonObject op = opEl.getAsJsonObject();
                    if (!op.has("type")) {
                        JsonObject r = new JsonObject();
                        r.addProperty("success", false);
                        r.addProperty("error", "Operation missing 'type' field");
                        results.add(r);
                        continue;
                    }
                    String opType = op.get("type").getAsString();
                    JsonObject r;
                    switch (opType) {
                        case "add_view":    r = handleAddView(viewEntry, op);    break;
                        case "remove_view": r = handleRemoveView(viewEntry, op); break;
                        case "set_property":r = handleSetProperty(viewEntry, op);break;
                        default:
                            r = new JsonObject();
                            r.addProperty("success", false);
                            r.addProperty("error", "Unknown operation type: " + opType);
                    }
                    results.add(r);
                }

                // 1. كتابة الملف
                writeFileContent(viewFile, viewArray.toString());

                // 2. إشعار DesignActivity بالتحديث المباشر
                notifyLayoutChanged(ctx.getAppContext(), scId, actName);

                JsonObject result = new JsonObject();
                result.addProperty("activity_name", actName);
                result.add("operation_results", results);
                result.addProperty("message",
                        "Layout updated. Design Editor refreshed automatically.");
                return success(result.toString());

            } catch (IOException e) {
                return error("Failed to edit layout: " + e.getMessage());
            } catch (JsonSyntaxException e) {
                return error("View file contains invalid JSON: " + e.getMessage());
            }
        }

        private JsonObject handleAddView(JsonObject viewEntry, JsonObject op) {
            JsonObject r = new JsonObject();
            if (!op.has("view") || !op.get("view").isJsonObject()) {
                r.addProperty("success", false);
                r.addProperty("error", "add_view requires a 'view' object");
                return r;
            }
            JsonObject newView = op.getAsJsonObject("view");
            if (!viewEntry.has("children")) viewEntry.add("children", new JsonArray());
            viewEntry.getAsJsonArray("children").add(newView);
            String id = newView.has("id") ? newView.get("id").getAsString() : "unknown";
            r.addProperty("success", true);
            r.addProperty("message", "View added: " + id);
            return r;
        }

        private JsonObject handleRemoveView(JsonObject viewEntry, JsonObject op) {
            JsonObject r = new JsonObject();
            if (!op.has("view_id")) {
                r.addProperty("success", false);
                r.addProperty("error", "remove_view requires 'view_id'");
                return r;
            }
            String viewId = op.get("view_id").getAsString();
            if (!viewEntry.has("children") || !viewEntry.get("children").isJsonArray()) {
                r.addProperty("success", false);
                r.addProperty("error", "No children array in layout");
                return r;
            }
            JsonArray children = viewEntry.getAsJsonArray("children");
            boolean removed = false;
            Iterator<JsonElement> it = children.iterator();
            while (it.hasNext()) {
                JsonElement child = it.next();
                if (child.isJsonObject()) {
                    JsonObject c = child.getAsJsonObject();
                    if (c.has("id") && c.get("id").getAsString().equals(viewId)) {
                        it.remove();
                        removed = true;
                        break;
                    }
                }
            }
            if (removed) {
                r.addProperty("success", true);
                r.addProperty("message", "View removed: " + viewId);
            } else {
                r.addProperty("success", false);
                r.addProperty("error", "View not found: " + viewId);
            }
            return r;
        }

        private JsonObject handleSetProperty(JsonObject viewEntry, JsonObject op) {
            JsonObject r = new JsonObject();
            if (!op.has("view_id") || !op.has("property") || !op.has("value")) {
                r.addProperty("success", false);
                r.addProperty("error", "set_property requires 'view_id', 'property', and 'value'");
                return r;
            }
            String viewId = op.get("view_id").getAsString();
            String property = op.get("property").getAsString();
            JsonElement value = op.get("value");

            if (viewEntry.has("root") && viewEntry.get("root").isJsonObject()) {
                JsonObject root = viewEntry.getAsJsonObject("root");
                if (root.has("id") && root.get("id").getAsString().equals(viewId)) {
                    root.add(property, value);
                    r.addProperty("success", true);
                    r.addProperty("message", "Property set on root view: " + property);
                    return r;
                }
            }
            if (viewEntry.has("children") && viewEntry.get("children").isJsonArray()) {
                for (JsonElement child : viewEntry.getAsJsonArray("children")) {
                    if (child.isJsonObject()) {
                        JsonObject c = child.getAsJsonObject();
                        if (c.has("id") && c.get("id").getAsString().equals(viewId)) {
                            c.add(property, value);
                            r.addProperty("success", true);
                            r.addProperty("message", "Property set on view " + viewId + ": " + property);
                            return r;
                        }
                    }
                }
            }
            r.addProperty("success", false);
            r.addProperty("error", "View not found: " + viewId);
            return r;
        }
    }
}
