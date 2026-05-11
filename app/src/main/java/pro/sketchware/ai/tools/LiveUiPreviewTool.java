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

public final class LiveUiPreviewTool {

    public static final String ACTION_LIVE_LAYOUT_RELOAD =
            "pro.sketchware.ai.ACTION_LIVE_LAYOUT_RELOAD";

    private LiveUiPreviewTool() {}

    private static ToolResult ok(String s) { return ToolResult.success(null, s); }
    private static ToolResult err(String s) { return ToolResult.failure(null, s); }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString().trim() : null;
    }
    private static int getInt(JsonObject o, String k, int def) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : def; }
        catch (Exception e) { return def; }
    }

    private static void addStr(JsonObject p, String k, String d) {
        JsonObject o = new JsonObject(); o.addProperty("type","string"); o.addProperty("description",d);
        p.add(k, o);
    }
    private static void addIntP(JsonObject p, String k, String d) {
        JsonObject o = new JsonObject(); o.addProperty("type","integer"); o.addProperty("description",d);
        p.add(k, o);
    }
    private static JsonObject schema(JsonObject props) {
        JsonObject s = new JsonObject(); s.addProperty("type","object"); s.add("properties", props); return s;
    }
    private static void req(JsonObject s, String... keys) {
        JsonArray r = new JsonArray(); for (String k : keys) r.add(k); s.add("required", r);
    }

    public static class DescribeLayoutLiveTool implements AgentTool {

        @Override public String getName() { return "describe_layout_live"; }

        @Override
        public String getDescription() {
            return "Reads the current ViewBean layout for a Sketchware activity. Uses jC in-memory "
                    + "cache (most accurate, includes unsaved AI changes) with encrypted disk fallback. "
                    + "Returns a human-readable tree + raw ViewBean JSON for editing. "
                    + "ALWAYS call before build_screen_layout or modify_view_live.\n\n"
                    + SketchwareViewBridge.buildTypeReference();
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject props = new JsonObject();
            JsonObject sc = new JsonObject(); sc.addProperty("type","string");
            sc.addProperty("description","Project ID (sc_id)"); props.add("sc_id", sc);
            JsonObject n = new JsonObject(); n.addProperty("type","string");
            n.addProperty("description","Activity name e.g. 'main' or 'main.xml'"); props.add("activity_xml", n);
            JsonObject s = new JsonObject(); s.addProperty("type","object"); s.add("properties", props);
            JsonArray r = new JsonArray(); r.add("sc_id"); r.add("activity_xml"); s.add("required", r);
            return s;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = str(args, "sc_id");
            String xml = SketchwareViewBridge.normalizeXmlName(str(args, "activity_xml"));
            if (scId == null || xml == null) return err("sc_id and activity_xml required");
            if (!ctx.isProjectAllowed(scId)) return err("Project not in workspace");

            ArrayList<ViewBean> beans = SketchwareViewBridge.getViewBeans(scId, xml);
            if (beans == null || beans.isEmpty()) {
                String raw = SketchwareViewBridge.readViewFile(scId);
                Map<String, List<String>> sections = SketchwareViewBridge.parseSections(raw);
                StringBuilder sb = new StringBuilder("No views found for " + xml + ".\n");
                sb.append("Available sections: ").append(sections.keySet()).append("\n\n");
                sb.append(SketchwareViewBridge.buildTypeReference());
                return ok(sb.toString());
            }

            StringBuilder sb = new StringBuilder();
            sb.append("=== Layout: ").append(xml).append(" (").append(beans.size()).append(" views) ===\n\n");
            sb.append(SketchwareViewBridge.buildViewTreeDescription(beans));
            sb.append("\n--- Raw ViewBean JSON ---\n");
            for (ViewBean bean : beans) {
                sb.append(SketchwareViewBridge.viewBeanToJsonObject(bean)).append("\n");
            }
            sb.append("\n").append(SketchwareViewBridge.buildTypeReference());
            return ok(sb.toString());
        }
    }

    public static class BuildScreenLayoutTool implements AgentTool {

        @Override public String getName() { return "build_screen_layout"; }

        @Override
        public String getDescription() {
            return "Replaces an entire activity screen layout with a new ViewBean array. "
                    + "This is the PRIMARY tool for building UI screens from scratch. "
                    + "Provide a JSON array of ViewBean objects. Each bean must follow SK format:\n\n"
                    + SketchwareViewBridge.buildTypeReference();
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject props = new JsonObject();
            JsonObject sc = new JsonObject(); sc.addProperty("type","string");
            sc.addProperty("description","Project ID"); props.add("sc_id", sc);
            JsonObject n = new JsonObject(); n.addProperty("type","string");
            n.addProperty("description","Activity name e.g. 'main'"); props.add("activity_xml", n);
            JsonObject viewsP = new JsonObject(); viewsP.addProperty("type","array");
            viewsP.addProperty("description","Array of ViewBean JSON objects"); props.add("views", viewsP);
            JsonObject fabP = new JsonObject(); fabP.addProperty("type","string");
            fabP.addProperty("description","Optional FAB ViewBean JSON (type=16). Default placeholder used if omitted.");
            props.add("fab_json", fabP);
            JsonObject s = new JsonObject(); s.addProperty("type","object"); s.add("properties", props);
            JsonArray r = new JsonArray(); r.add("sc_id"); r.add("activity_xml"); r.add("views");
            s.add("required", r);
            return s;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = str(args, "sc_id");
            String xml = SketchwareViewBridge.normalizeXmlName(str(args, "activity_xml"));
            if (scId == null || xml == null) return err("sc_id and activity_xml required");
            if (!ctx.isProjectAllowed(scId)) return err("Project not in workspace");
            if (!args.has("views") || args.get("views").isJsonNull())
                return err("views array is required");

            try {
                JsonArray viewsArr = args.get("views").getAsJsonArray();
                if (viewsArr.size() == 0) return err("views array is empty");

                ArrayList<ViewBean> beans = new ArrayList<>();
                for (int i = 0; i < viewsArr.size(); i++) {
                    JsonObject beanJson = viewsArr.get(i).getAsJsonObject();
                    ViewBean bean = SketchwareViewBridge.jsonObjectToViewBean(beanJson);
                    if (bean == null) return err("Failed to parse ViewBean at index " + i);
                    if (bean.id == null || bean.id.isEmpty()) return err("ViewBean at index " + i + " missing 'id'");
                    if (beanJson.has("index")) {
                        bean.index = beanJson.get("index").getAsInt();
                    } else {
                        bean.index = i;
                    }
                    boolean isRoot = "root".equals(bean.parent);
                    if (!beanJson.has("preId")) bean.preId = bean.id;
                    if (!beanJson.has("preIndex")) bean.preIndex = isRoot ? -1 : bean.index;
                    if (!beanJson.has("preParent")) bean.preParent = isRoot ? "" : bean.parent;
                    if (!beanJson.has("preParentType")) bean.preParentType = isRoot ? -1 : bean.parentType;
                    beans.add(bean);
                }

                List<String> beanLines = new ArrayList<>();
                for (ViewBean b : beans) {
                    beanLines.add(SketchwareViewBridge.viewBeanToJsonObject(b).toString());
                }

                String raw = SketchwareViewBridge.readViewFile(scId);
                Map<String, List<String>> sections = SketchwareViewBridge.parseSections(raw);
                sections.put(xml, beanLines);

                String fabKey = xml + "_fab";
                if (args.has("fab_json") && !args.get("fab_json").isJsonNull()) {
                    String fabStr = args.get("fab_json").getAsString().trim();
                    sections.put(fabKey, new ArrayList<>(List.of(fabStr)));
                } else {
                    SketchwareViewBridge.ensureFabSection(sections, xml);
                }

                boolean saved = SketchwareViewBridge.writeViewFile(scId, SketchwareViewBridge.serializeSections(sections));
                if (!saved) return err("Failed to save view file");

                SketchwareViewBridge.putViewBeansToMemory(scId, xml, beans);
                SketchwareViewBridge.broadcastBoth(ctx.getAppContext(), scId, xml);

                return ok("Screen layout built!\nActivity: " + xml + "\nViews: " + beans.size()
                        + "\n_fab section: present\nLive reload: sent");
            } catch (Exception e) {
                return err("build_screen_layout failed: " + e.getMessage());
            }
        }
    }

    public static class AddViewLiveTool implements AgentTool {

        @Override public String getName() { return "add_view_live"; }

        @Override
        public String getDescription() {
            return "Adds a single ViewBean to an activity layout and triggers live reload. "
                    + "Uses the correct Sketchware ViewBean format with all required fields. "
                    + "Triggers DesignActivity live reload via broadcast.\n\n"
                    + SketchwareViewBridge.buildTypeReference();
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject props = new JsonObject();
            addStr(props, "sc_id", "Project ID");
            addStr(props, "activity_xml", "Activity name e.g. 'main'");
            addStr(props, "parent_id", "Parent container id, or 'root'");
            addIntP(props, "parent_type", "Parent type: 0=LinearLayout 1=RelativeLayout 12=ScrollView");
            addStr(props, "view_id", "Unique ID for new widget");
            addIntP(props, "type", "Widget type constant (e.g. 4=TextView, 3=Button, 0=LinearLayout)");
            addIntP(props, "width", "Width: -1=match_parent -2=wrap_content N=dp");
            addIntP(props, "height", "Height: -1=match_parent -2=wrap_content N=dp");
            addIntP(props, "orientation", "LinearLayout orientation: 0=horizontal 1=vertical -1=none");
            addIntP(props, "background_color", "Background ARGB signed int");
            addIntP(props, "gravity", "Gravity: 0=none 17=center 16=center_h 5=center_v");
            addIntP(props, "padding_left", "Left padding dp");
            addIntP(props, "padding_right", "Right padding dp");
            addIntP(props, "padding_top", "Top padding dp");
            addIntP(props, "padding_bottom", "Bottom padding dp");
            addIntP(props, "margin_left", "Left margin dp");
            addIntP(props, "margin_right", "Right margin dp");
            addIntP(props, "margin_top", "Top margin dp");
            addIntP(props, "margin_bottom", "Bottom margin dp");
            addIntP(props, "weight", "Layout weight");
            addStr(props, "text", "Display text");
            addIntP(props, "text_size", "Text size sp");
            addIntP(props, "text_color", "Text color ARGB signed int");
            addIntP(props, "text_type", "0=normal 1=bold 2=italic 3=bold+italic");
            addStr(props, "hint", "Hint text for EditText");
            addStr(props, "res_name", "Image resource name for ImageView");
            JsonObject s = new JsonObject(); s.addProperty("type","object"); s.add("properties", props);
            JsonArray r = new JsonArray(); r.add("sc_id"); r.add("activity_xml"); r.add("parent_id"); r.add("view_id"); r.add("type");
            s.add("required", r);
            return s;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = str(args, "sc_id");
            String xml = SketchwareViewBridge.normalizeXmlName(str(args, "activity_xml"));
            String parentId = str(args, "parent_id");
            String viewId = str(args, "view_id");
            if (scId == null || xml == null || parentId == null || viewId == null)
                return err("Required: sc_id, activity_xml, parent_id, view_id, type");
            if (!ctx.isProjectAllowed(scId)) return err("Project not in workspace");

            int type = getInt(args, "type", 4);
            if (type == 2) return err("type=2 is HorizontalScrollView NOT TextView. Use type=4.");

            ViewBean bean = SketchwareViewBridge.createDefaultViewBean(viewId, type, parentId, getInt(args, "parent_type", 0));
            bean.index = getInt(args, "index", 0);
            bean.layout.width = getInt(args, "width", -1);
            bean.layout.height = getInt(args, "height", -2);
            bean.layout.orientation = getInt(args, "orientation", type == 0 ? 1 : -1);
            bean.layout.gravity = getInt(args, "gravity", 0);
            bean.layout.layoutGravity = getInt(args, "layoutGravity", 0);
            bean.layout.paddingLeft = getInt(args, "padding_left", 0);
            bean.layout.paddingRight = getInt(args, "padding_right", 0);
            bean.layout.paddingTop = getInt(args, "padding_top", 0);
            bean.layout.paddingBottom = getInt(args, "padding_bottom", 0);
            bean.layout.marginLeft = getInt(args, "margin_left", 0);
            bean.layout.marginRight = getInt(args, "margin_right", 0);
            bean.layout.marginTop = getInt(args, "margin_top", 0);
            bean.layout.marginBottom = getInt(args, "margin_bottom", 0);
            bean.layout.weight = getInt(args, "weight", 0);
            int bgColor = getInt(args, "background_color", 0);
            if (bgColor != 0) { bean.layout.backgroundColor = bgColor; bean.layout.hasBackgroundColor = true; }

            if (args.has("text") && !args.get("text").isJsonNull()) bean.text.text = args.get("text").getAsString();
            bean.text.textSize = getInt(args, "text_size", 14);
            int textColor = getInt(args, "text_color", -16777216);
            if (textColor != 0) { bean.text.textColor = textColor; bean.text.hasTextColor = true; }
            bean.text.textType = getInt(args, "text_type", 0);
            if (args.has("hint") && !args.get("hint").isJsonNull()) bean.text.hint = args.get("hint").getAsString();
            if (args.has("res_name") && !args.get("res_name").isJsonNull()) bean.image.resName = args.get("res_name").getAsString();

            try {
                String raw = SketchwareViewBridge.readViewFile(scId);
                Map<String, List<String>> sections = SketchwareViewBridge.parseSections(raw);
                SketchwareViewBridge.ensureFabSection(sections, xml);
                List<String> lines = sections.get(xml);

                for (String line : lines) {
                    try {
                        JsonObject b = JsonParser.parseString(line).getAsJsonObject();
                        if (viewId.equals(b.has("id") ? b.get("id").getAsString() : ""))
                            return err("View '" + viewId + "' already exists");
                    } catch (Exception ignored) {}
                }

                lines.add(SketchwareViewBridge.viewBeanToJsonObject(bean).toString());
                sections.put(xml, lines);

                boolean saved = SketchwareViewBridge.writeViewFile(scId, SketchwareViewBridge.serializeSections(sections));
                if (!saved) return err("Failed to save view file");

                ArrayList<ViewBean> allBeans = SketchwareViewBridge.getViewBeans(scId, xml);
                if (allBeans != null) {
                    allBeans.add(bean);
                    SketchwareViewBridge.putViewBeansToMemory(scId, xml, allBeans);
                }
                SketchwareViewBridge.broadcastBoth(ctx.getAppContext(), scId, xml);

                return ok("Added: id=" + viewId + " type=" + SketchwareViewBridge.getViewTypeName(type)
                        + " parent=" + parentId + " index=" + bean.index + "\nLive reload sent.");
            } catch (Exception e) {
                return err("add_view_live failed: " + e.getMessage());
            }
        }
    }

    public static class ModifyViewLiveTool implements AgentTool {

        @Override public String getName() { return "modify_view_live"; }

        @Override
        public String getDescription() {
            return "Updates properties of an existing ViewBean using dot-path notation. "
                    + "Reads encrypted view file, applies patch, saves, and broadcasts reload. "
                    + "Patch keys: 'text.text', 'text.textSize', 'text.textColor', "
                    + "'layout.width', 'layout.height', 'layout.backgroundColor', "
                    + "'layout.orientation', 'layout.gravity', 'layout.weight', "
                    + "'image.resName', 'image.scaleType', 'clickable', 'enabled'.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject props = new JsonObject();
            addStr(props, "sc_id", "Project ID");
            addStr(props, "activity_xml", "Activity name e.g. 'main'");
            addStr(props, "view_id", "ID of view to modify");
            JsonObject patch = new JsonObject();
            patch.addProperty("type", "object");
            patch.addProperty("description", "Properties to update. Use dot-path: 'layout.width', 'text.text'");
            props.add("properties", patch);
            JsonObject s = new JsonObject(); s.addProperty("type","object"); s.add("properties", props);
            JsonArray r = new JsonArray(); r.add("sc_id"); r.add("activity_xml"); r.add("view_id"); r.add("properties");
            s.add("required", r);
            return s;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = str(args, "sc_id");
            String xml = SketchwareViewBridge.normalizeXmlName(str(args, "activity_xml"));
            String viewId = str(args, "view_id");
            if (scId == null || xml == null || viewId == null)
                return err("Required: sc_id, activity_xml, view_id, properties");
            if (!args.has("properties") || args.get("properties").isJsonNull())
                return err("properties object required");
            if (!ctx.isProjectAllowed(scId)) return err("Project not in workspace");

            try {
                String raw = SketchwareViewBridge.readViewFile(scId);
                Map<String, List<String>> sections = SketchwareViewBridge.parseSections(raw);
                List<String> lines = sections.get(xml);
                if (lines == null) return err("Activity '" + xml + "' not found in view file");

                JsonObject patch = args.getAsJsonObject("properties");
                boolean found = false;
                List<String> updated = new ArrayList<>();

                for (String line : lines) {
                    JsonObject beanJson = JsonParser.parseString(line).getAsJsonObject();
                    if (viewId.equals(beanJson.has("id") ? beanJson.get("id").getAsString() : "")) {
                        found = true;
                        ViewBean bean = SketchwareViewBridge.jsonObjectToViewBean(beanJson);
                        if (bean == null) return err("Failed to parse ViewBean for '" + viewId + "'");
                        for (Map.Entry<String, JsonElement> e : patch.entrySet()) {
                            SketchwareViewBridge.applyPropertyPatch(bean, e.getKey(), e.getValue());
                        }
                        updated.add(SketchwareViewBridge.viewBeanToJsonObject(bean).toString());
                    } else {
                        updated.add(line);
                    }
                }

                if (!found) return err("View '" + viewId + "' not found in " + xml);
                sections.put(xml, updated);

                boolean saved = SketchwareViewBridge.writeViewFile(scId, SketchwareViewBridge.serializeSections(sections));
                if (!saved) return err("Failed to save view file");

                SketchwareViewBridge.broadcastBoth(ctx.getAppContext(), scId, xml);
                return ok("View '" + viewId + "' updated. Live reload sent.");
            } catch (Exception e) {
                return err("modify_view_live failed: " + e.getMessage());
            }
        }
    }

    public static class RemoveViewLiveTool implements AgentTool {

        @Override public String getName() { return "remove_view_live"; }

        @Override
        public String getDescription() {
            return "Removes a ViewBean by ID (and all its descendants) from an activity layout. "
                    + "Reads encrypted view file, removes beans, saves, and broadcasts reload.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject props = new JsonObject();
            addStr(props, "sc_id", "Project ID");
            addStr(props, "activity_xml", "Activity name e.g. 'main'");
            addStr(props, "view_id", "ID of view to remove");
            JsonObject s = new JsonObject(); s.addProperty("type","object"); s.add("properties", props);
            JsonArray r = new JsonArray(); r.add("sc_id"); r.add("activity_xml"); r.add("view_id");
            s.add("required", r);
            return s;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = str(args, "sc_id");
            String xml = SketchwareViewBridge.normalizeXmlName(str(args, "activity_xml"));
            String viewId = str(args, "view_id");
            if (scId == null || xml == null || viewId == null)
                return err("Required: sc_id, activity_xml, view_id");
            if (!ctx.isProjectAllowed(scId)) return err("Project not in workspace");

            try {
                ArrayList<ViewBean> beans = SketchwareViewBridge.getViewBeans(scId, xml);
                if (beans == null || beans.isEmpty()) return err("No views found for " + xml);

                List<String> descendantIds = SketchwareViewBridge.findDescendantIds(beans, viewId);
                if (descendantIds.isEmpty()) return err("View '" + viewId + "' not found in " + xml);

                beans.removeIf(b -> descendantIds.contains(b.id));

                List<String> beanLines = new ArrayList<>();
                for (ViewBean b : beans) {
                    beanLines.add(SketchwareViewBridge.viewBeanToJsonObject(b).toString());
                }

                String raw = SketchwareViewBridge.readViewFile(scId);
                Map<String, List<String>> sections = SketchwareViewBridge.parseSections(raw);
                sections.put(xml, beanLines);

                boolean saved = SketchwareViewBridge.writeViewFile(scId, SketchwareViewBridge.serializeSections(sections));
                if (!saved) return err("Failed to save view file");

                SketchwareViewBridge.putViewBeansToMemory(scId, xml, beans);
                SketchwareViewBridge.broadcastBoth(ctx.getAppContext(), scId, xml);

                return ok("Removed '" + viewId + "' and " + (descendantIds.size() - 1) + " descendant(s). Live reload sent.");
            } catch (Exception e) {
                return err("remove_view_live failed: " + e.getMessage());
            }
        }
    }
}