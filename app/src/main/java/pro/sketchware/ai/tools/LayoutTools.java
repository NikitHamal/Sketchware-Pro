package pro.sketchware.ai.tools;

import com.besome.sketch.beans.ViewBean;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import pro.sketchware.ai.models.ToolResult;

public final class LayoutTools {

    public static final String ACTION_LAYOUT_CHANGED = "pro.sketchware.ai.ACTION_LAYOUT_CHANGED";
    public static final String EXTRA_SC_ID = "sc_id";

    private static final String TYPE_REF = SketchwareViewBridge.buildTypeReference();

    private LayoutTools() {}

    private static ToolResult ok(String s) { return ToolResult.success(null, s); }
    private static ToolResult err(String s) { return ToolResult.failure(null, s); }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString().trim() : null;
    }
    private static int intVal(JsonObject o, String k, int def) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : def; }
        catch (Exception e) { return def; }
    }

    public static class DescribeLayoutTool implements AgentTool {

        @Override public String getName() { return "describe_layout"; }

        @Override
        public String getDescription() {
            return "Reads the current view layout of a Sketchware activity screen and returns "
                    + "a human-readable tree description. ALWAYS call this before editing a layout. "
                    + "Returns ViewBean details: id, type, parent, dimensions, text content, etc. "
                    + "Also shows the raw ViewBean JSON for precise editing reference.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject props = new JsonObject();
            JsonObject scP = new JsonObject(); scP.addProperty("type","string");
            scP.addProperty("description","Project SC ID"); props.add("sc_id", scP);
            JsonObject nP = new JsonObject(); nP.addProperty("type","string");
            nP.addProperty("description","Activity name (e.g. 'main' or 'main.xml')"); props.add("activity_name", nP);
            JsonObject schema = new JsonObject(); schema.addProperty("type","object");
            schema.add("properties", props);
            JsonArray req = new JsonArray(); req.add("sc_id"); req.add("activity_name");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = str(args, "sc_id");
            String actName = str(args, "activity_name");
            if (scId == null || actName == null) return err("sc_id and activity_name are required");
            if (!ctx.isProjectAllowed(scId)) return err("Access denied: project " + scId);

            String xmlName = SketchwareViewBridge.normalizeXmlName(actName);

            ArrayList<ViewBean> beans = SketchwareViewBridge.getViewBeans(scId, xmlName);
            if (beans == null || beans.isEmpty()) {
                String raw = SketchwareViewBridge.readViewFile(scId);
                Map<String, List<String>> sections = SketchwareViewBridge.parseSections(raw);
                StringBuilder sb = new StringBuilder("No views found for " + xmlName + ".\n");
                sb.append("Available sections: ").append(sections.keySet()).append("\n\n");
                sb.append(TYPE_REF);
                return ok(sb.toString());
            }

            StringBuilder sb = new StringBuilder();
            sb.append("=== Layout: ").append(xmlName).append(" ===\n");
            sb.append(SketchwareViewBridge.buildViewTreeDescription(beans));
            sb.append("\n--- Raw ViewBean JSON ---\n");
            for (ViewBean bean : beans) {
                sb.append(SketchwareViewBridge.viewBeanToJsonObject(bean)).append("\n");
            }
            sb.append("\n").append(TYPE_REF);
            return ok(sb.toString());
        }
    }

    public static class EditLayoutTool implements AgentTool {

        @Override public String getName() { return "edit_layout"; }

        @Override
        public String getDescription() {
            return "Edits the view layout of a Sketchware activity by performing operations on ViewBeans. "
                    + "Changes appear IMMEDIATELY in the Design Editor via live broadcast. "
                    + "Operations:\n"
                    + "  'add_view' — adds a new ViewBean (requires 'view' JSON object with id/type/parent/parentType/index)\n"
                    + "  'remove_view' — removes a view and all descendants by 'view_id'\n"
                    + "  'set_property' — updates a property: 'view_id', 'property' (dot-path: layout.width, text.text), 'value'\n"
                    + "  'reorder_view' — changes the index/parent of a view to move it\n\n"
                    + TYPE_REF;
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject props = new JsonObject();
            JsonObject scP = new JsonObject(); scP.addProperty("type","string");
            scP.addProperty("description","Project SC ID"); props.add("sc_id", scP);
            JsonObject nP = new JsonObject(); nP.addProperty("type","string");
            nP.addProperty("description","Activity name"); props.add("activity_name", nP);
            JsonObject opsP = new JsonObject(); opsP.addProperty("type","array");
            opsP.addProperty("description","Array of operations");
            JsonObject itemSch = new JsonObject(); itemSch.addProperty("type","object");
            opsP.add("items", itemSch); props.add("view_operations", opsP);
            JsonObject schema = new JsonObject(); schema.addProperty("type","object");
            schema.add("properties", props);
            JsonArray req = new JsonArray();
            req.add("sc_id"); req.add("activity_name"); req.add("view_operations");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = str(args, "sc_id");
            String actName = str(args, "activity_name");
            if (scId == null || actName == null) return err("sc_id and activity_name required");
            if (!args.has("view_operations") || !args.get("view_operations").isJsonArray())
                return err("view_operations array required");
            if (!ctx.isProjectAllowed(scId)) return err("Access denied: project " + scId);

            String xmlName = SketchwareViewBridge.normalizeXmlName(actName);
            JsonArray ops = args.getAsJsonArray("view_operations");

            String raw = SketchwareViewBridge.readViewFile(scId);
            Map<String, List<String>> sections = SketchwareViewBridge.parseSections(raw);
            List<String> lines = sections.get(xmlName);
            if (lines == null) {
                lines = new ArrayList<>();
                sections.put(xmlName, lines);
            }
            SketchwareViewBridge.ensureFabSection(sections, xmlName);

            JsonArray results = new JsonArray();
            for (JsonElement opEl : ops) {
                if (!opEl.isJsonObject()) {
                    JsonObject r = new JsonObject();
                    r.addProperty("success", false);
                    r.addProperty("error", "Operation must be a JSON object");
                    results.add(r); continue;
                }
                JsonObject op = opEl.getAsJsonObject();
                if (!op.has("type")) {
                    JsonObject r = new JsonObject();
                    r.addProperty("success", false);
                    r.addProperty("error", "Operation missing 'type' field");
                    results.add(r); continue;
                }
                String opType = op.get("type").getAsString();
                JsonObject r;
                switch (opType) {
                    case "add_view":     r = handleAdd(lines, op); break;
                    case "remove_view":  r = handleRemove(lines, op); break;
                    case "set_property": r = handleSetProperty(lines, op); break;
                    case "reorder_view": r = handleReorder(lines, op); break;
                    default:
                        r = new JsonObject();
                        r.addProperty("success", false);
                        r.addProperty("error", "Unknown operation: " + opType);
                }
                results.add(r);
            }

            sections.put(xmlName, lines);
            boolean saved = SketchwareViewBridge.writeViewFile(scId, SketchwareViewBridge.serializeSections(sections));
            if (!saved) return err("Failed to save view file");

            SketchwareViewBridge.broadcastBoth(ctx.getAppContext(), scId, xmlName);

            JsonObject result = new JsonObject();
            result.addProperty("activity_name", xmlName);
            result.add("operation_results", results);
            result.addProperty("message", "Layout updated. Design Editor refreshed.");
            return ok(result.toString());
        }

        private JsonObject handleAdd(List<String> lines, JsonObject op) {
            JsonObject r = new JsonObject();
            if (!op.has("view") || !op.get("view").isJsonObject()) {
                r.addProperty("success", false);
                r.addProperty("error", "add_view requires 'view' object");
                return r;
            }
            JsonObject newViewJson = op.getAsJsonObject("view");

            String id = newViewJson.has("id") ? newViewJson.get("id").getAsString() : null;
            if (id == null || id.isEmpty()) {
                r.addProperty("success", false);
                r.addProperty("error", "view must have 'id'");
                return r;
            }
            if (!newViewJson.has("type")) {
                r.addProperty("success", false);
                r.addProperty("error", "view must have 'type' (use ViewBean type constants)");
                return r;
            }
            for (String line : lines) {
                try {
                    JsonObject existing = JsonParser.parseString(line).getAsJsonObject();
                    if (id.equals(existing.has("id") ? existing.get("id").getAsString() : "")) {
                        r.addProperty("success", false);
                        r.addProperty("error", "View '" + id + "' already exists");
                        return r;
                    }
                } catch (Exception ignored) {}
            }

            ViewBean bean = SketchwareViewBridge.jsonObjectToViewBean(newViewJson);
            if (bean == null) {
                r.addProperty("success", false);
                r.addProperty("error", "Failed to parse ViewBean JSON");
                return r;
            }
            bean.id = id;
            if (!newViewJson.has("parent")) bean.parent = "root";
            if (!newViewJson.has("parentType")) bean.parentType = 0;
            if (!newViewJson.has("index")) bean.index = lines.size();
            boolean isRoot = "root".equals(bean.parent);
            if (!newViewJson.has("preId")) bean.preId = id;
            if (!newViewJson.has("preIndex")) bean.preIndex = isRoot ? -1 : bean.index;
            if (!newViewJson.has("preParent")) bean.preParent = isRoot ? "" : bean.parent;
            if (!newViewJson.has("preParentType")) bean.preParentType = isRoot ? -1 : bean.parentType;

            lines.add(SketchwareViewBridge.viewBeanToJsonObject(bean).toString());
            r.addProperty("success", true);
            r.addProperty("message", "View added: " + id + " type=" + SketchwareViewBridge.getViewTypeName(bean.type));
            return r;
        }

        private JsonObject handleRemove(List<String> lines, JsonObject op) {
            JsonObject r = new JsonObject();
            if (!op.has("view_id")) {
                r.addProperty("success", false);
                r.addProperty("error", "remove_view requires 'view_id'");
                return r;
            }
            String viewId = op.get("view_id").getAsString();

            ArrayList<ViewBean> beans = new ArrayList<>();
            for (String line : lines) {
                ViewBean b = SketchwareViewBridge.jsonObjectToViewBean(JsonParser.parseString(line).getAsJsonObject());
                if (b != null) beans.add(b);
            }

            List<String> descendantIds = SketchwareViewBridge.findDescendantIds(beans, viewId);
            if (descendantIds.isEmpty()) {
                r.addProperty("success", false);
                r.addProperty("error", "View '" + viewId + "' not found");
                return r;
            }

            lines.removeIf(line -> {
                try {
                    JsonObject b = JsonParser.parseString(line).getAsJsonObject();
                    return descendantIds.contains(b.has("id") ? b.get("id").getAsString() : "");
                } catch (Exception e) { return false; }
            });

            r.addProperty("success", true);
            r.addProperty("message", "Removed '" + viewId + "' and " + (descendantIds.size() - 1) + " descendant(s)");
            return r;
        }

        private JsonObject handleSetProperty(List<String> lines, JsonObject op) {
            JsonObject r = new JsonObject();
            if (!op.has("view_id") || !op.has("property") || !op.has("value")) {
                r.addProperty("success", false);
                r.addProperty("error", "set_property requires view_id, property, and value");
                return r;
            }
            String viewId = op.get("view_id").getAsString();
            String property = op.get("property").getAsString();
            JsonElement value = op.get("value");

            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                try {
                    JsonObject b = JsonParser.parseString(lines.get(i)).getAsJsonObject();
                    if (viewId.equals(b.has("id") ? b.get("id").getAsString() : "")) {
                        ViewBean bean = SketchwareViewBridge.jsonObjectToViewBean(b);
                        if (bean != null) {
                            SketchwareViewBridge.applyPropertyPatch(bean, property, value);
                            lines.set(i, SketchwareViewBridge.viewBeanToJsonObject(bean).toString());
                            found = true;
                        }
                        break;
                    }
                } catch (Exception e) {
                    r.addProperty("success", false);
                    r.addProperty("error", "Parse error: " + e.getMessage());
                    return r;
                }
            }
            if (!found) {
                r.addProperty("success", false);
                r.addProperty("error", "View '" + viewId + "' not found");
                return r;
            }
            r.addProperty("success", true);
            r.addProperty("message", "Property '" + property + "' updated on '" + viewId + "'");
            return r;
        }

        private JsonObject handleReorder(List<String> lines, JsonObject op) {
            JsonObject r = new JsonObject();
            if (!op.has("view_id")) {
                r.addProperty("success", false);
                r.addProperty("error", "reorder_view requires view_id");
                return r;
            }
            String viewId = op.get("view_id").getAsString();
            String newParent = op.has("new_parent") ? op.get("new_parent").getAsString() : null;
            int newIndex = op.has("new_index") ? op.get("new_index").getAsInt() : -1;

            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                try {
                    JsonObject b = JsonParser.parseString(lines.get(i)).getAsJsonObject();
                    if (viewId.equals(b.has("id") ? b.get("id").getAsString() : "")) {
                        if (newParent != null) b.addProperty("parent", newParent);
                        if (newIndex >= 0) b.addProperty("index", newIndex);
                        boolean isRoot = "root".equals(b.has("parent") ? b.get("parent").getAsString() : "root");
                        b.addProperty("preParent", isRoot ? "" : b.get("parent").getAsString());
                        b.addProperty("preParentType", isRoot ? -1 : (b.has("parentType") ? b.get("parentType").getAsInt() : 0));
                        lines.set(i, b.toString());
                        found = true;
                        break;
                    }
                } catch (Exception e) {
                    r.addProperty("success", false);
                    r.addProperty("error", "Parse error: " + e.getMessage());
                    return r;
                }
            }
            if (!found) {
                r.addProperty("success", false);
                r.addProperty("error", "View '" + viewId + "' not found");
                return r;
            }
            r.addProperty("success", true);
            r.addProperty("message", "View '" + viewId + "' reordered");
            return r;
        }
    }
}