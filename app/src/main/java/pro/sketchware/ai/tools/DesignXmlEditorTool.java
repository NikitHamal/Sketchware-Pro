package pro.sketchware.ai.tools;

import android.content.Context;

import com.besome.sketch.beans.ViewBean;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Map;

import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.tools.ViewBeanParser;
import pro.sketchware.utility.GsonUtils;

public final class DesignXmlEditorTool {

    private static final String TYPE_REF = SketchwareViewBridge.buildTypeReference();

    private DesignXmlEditorTool() {}

    private static ToolResult success(String output) { return ToolResult.success(null, output); }
    private static ToolResult error(String msg) { return ToolResult.failure(null, msg); }

    private static String requireString(JsonObject args, String key) {
        if (!args.has(key) || args.get(key).isJsonNull()) return null;
        return args.get(key).getAsString().trim();
    }

    private static void addP(JsonObject props, String key, String type, String desc) {
        JsonObject p = new JsonObject(); p.addProperty("type", type); p.addProperty("description", desc);
        props.add(key, p);
    }

    public static class DescribeLayoutTool implements AgentTool {
        @Override public String getName() { return "describe_layout_xml"; }

        @Override
        public String getDescription() {
            return "Reads the current view layout of a Sketchware activity and returns both a "
                    + "human-readable tree description AND the raw ViewBean JSON for precise editing. "
                    + "Reads from jC in-memory cache (most accurate, includes unsaved changes) with "
                    + "disk fallback. ALWAYS call this before add_view_xml or edit_layout.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject(); schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            addP(props, "sc_id", "string", "Project ID");
            addP(props, "activity_name", "string", "Activity name (e.g. 'main')");
            schema.add("properties", props);
            JsonArray req = new JsonArray(); req.add("sc_id"); req.add("activity_name");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = requireString(args, "sc_id");
            String actName = requireString(args, "activity_name");
            if (scId == null || actName == null) return error("sc_id and activity_name required");
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);

            String xmlName = SketchwareViewBridge.normalizeXmlName(actName);
            ArrayList<ViewBean> beans = SketchwareViewBridge.getViewBeans(scId, xmlName);

            if (beans == null || beans.isEmpty()) {
                String raw = SketchwareViewBridge.readViewFile(scId);
                Map<String, List<String>> sections = SketchwareViewBridge.parseSections(raw);
                StringBuilder sb = new StringBuilder("No views found for " + xmlName + ".\n");
                sb.append("Available sections: ").append(sections.keySet()).append("\n\n");
                sb.append(TYPE_REF);
                return success(sb.toString());
            }

            StringBuilder sb = new StringBuilder();
            sb.append("=== Layout: ").append(xmlName).append(" (").append(beans.size()).append(" views) ===\n\n");
            sb.append(SketchwareViewBridge.buildViewTreeDescription(beans));
            sb.append("\n--- ViewBean JSON (for edit_layout) ---\n");
            for (ViewBean bean : beans) {
                sb.append(SketchwareViewBridge.viewBeanToJsonObject(bean)).append("\n");
            }
            sb.append("\n").append(TYPE_REF);
            return success(sb.toString());
        }
    }

    public static class AddViewXmlTool implements AgentTool {
        @Override public String getName() { return "add_view_xml"; }

        @Override
        public String getDescription() {
            return "PREFERRED method to add/replace views using raw Android XML. "
                    + "Uses ViewBeanParser (the same engine Sketchware uses internally) to convert "
                    + "XML to ViewBeans, then writes through jC and the encrypted view file. "
                    + "Set replace=true to replace entire layout, false (default) to merge/append. "
                    + "After applying, the Design Editor refreshes automatically.\n\n"
                    + "The XML should wrap all widgets in a root ViewGroup. Example:\n"
                    + "<LinearLayout android:orientation=\"vertical\" ...>\n"
                    + "  <TextView android:id=\"@+id/tv1\" android:text=\"Hello\" .../>\n"
                    + "</LinearLayout>";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject s = new JsonObject(); s.addProperty("type", "object");
            JsonObject p = new JsonObject();
            addP(p, "sc_id", "string", "Project ID");
            addP(p, "activity_name", "string", "Activity name (e.g. 'main')");
            addP(p, "xml", "string", "Android XML with root ViewGroup");
            addP(p, "replace", "boolean", "true=replace entire layout, false=append (default)");
            s.add("properties", p);
            JsonArray r = new JsonArray(); r.add("sc_id"); r.add("activity_name"); r.add("xml");
            s.add("required", r);
            return s;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = requireString(args, "sc_id");
            String actName = requireString(args, "activity_name");
            String xml = requireString(args, "xml");
            boolean replace = args.has("replace") && args.get("replace").getAsBoolean();

            if (scId == null || actName == null || xml == null)
                return error("sc_id, activity_name and xml are required");
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);

            String xmlName = SketchwareViewBridge.normalizeXmlName(actName);

            try {
                ViewBeanParser parser = new ViewBeanParser(xml);
                parser.setSkipRoot(true);
                ArrayList<ViewBean> newBeans = parser.parse();

                if (newBeans == null || newBeans.isEmpty())
                    return error("XML parse produced no views. Check that XML has android:id attributes.");

                ArrayList<ViewBean> finalBeans;
                if (replace) {
                    finalBeans = newBeans;
                } else {
                    ArrayList<ViewBean> existing = SketchwareViewBridge.getViewBeans(scId, xmlName);
                    if (existing == null) existing = new ArrayList<>();
                    java.util.Map<String, ViewBean> map = new java.util.LinkedHashMap<>();
                    for (ViewBean b : existing) if (b.id != null) map.put(b.id, b);
                    for (ViewBean b : newBeans) if (b.id != null) map.put(b.id, b);
                    finalBeans = new ArrayList<>(map.values());
                }

                String raw = SketchwareViewBridge.readViewFile(scId);
                Map<String, List<String>> sections = SketchwareViewBridge.parseSections(raw);
                List<String> beanLines = new ArrayList<>();
                for (ViewBean b : finalBeans) {
                    beanLines.add(GsonUtils.getGson().toJson(b));
                }
                sections.put(xmlName, beanLines);
                SketchwareViewBridge.ensureFabSection(sections, xmlName);

                boolean saved = SketchwareViewBridge.writeViewFile(scId, SketchwareViewBridge.serializeSections(sections));
                if (!saved) return error("Failed to save view file");

                SketchwareViewBridge.putViewBeansToMemory(scId, xmlName, finalBeans);
                SketchwareViewBridge.broadcastBothWithXml(ctx.getAppContext(), scId, xmlName, xml);

                return success((replace ? "Replaced" : "Added") + " " + newBeans.size()
                        + " view(s) to '" + actName + "'. Total: " + finalBeans.size()
                        + ". Design canvas reloaded.");
            } catch (Exception e) {
                return error("XML parse failed: " + e.getMessage());
            }
        }
    }

    public static class GenerateLayoutTool implements AgentTool {
        @Override public String getName() { return "generate_layout"; }

        @Override
        public String getDescription() {
            return "Generates a COMPLETE Android layout from a natural-language description "
                    + "and applies it via ViewBeanParser. Use for creating new screens or full redesigns. "
                    + "For PARTIAL edits, use add_view_xml with replace=false instead. "
                    + "For EDITING existing layout: call describe_layout first, then this with current_layout.\n\n"
                    + TYPE_REF;
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject s = new JsonObject(); s.addProperty("type", "object");
            JsonObject p = new JsonObject();
            addP(p, "sc_id", "string", "Project ID");
            addP(p, "activity_name", "string", "Activity name");
            addP(p, "description", "string", "What to create or change");
            addP(p, "current_layout", "string", "OPTIONAL: current layout from describe_layout");
            addP(p, "replace", "boolean", "Replace entire layout (default true)");
            s.add("properties", p);
            JsonArray r = new JsonArray(); r.add("sc_id"); r.add("activity_name"); r.add("description");
            s.add("required", r);
            return s;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = requireString(args, "sc_id");
            String actName = requireString(args, "activity_name");
            String desc = requireString(args, "description");
            if (scId == null || actName == null || desc == null)
                return error("sc_id, activity_name and description required");
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);

            return error("generate_layout requires the AI engine to produce XML first. "
                    + "Use add_view_xml with the generated XML instead.");
        }
    }
}