package pro.sketchware.ai.tools;

import android.content.Context;
import android.content.Intent;

import com.besome.sketch.beans.ViewBean;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;

import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.tools.ViewBeanParser;
import pro.sketchware.utility.GsonUtils;

public final class LayoutGeneratorTool implements AgentTool {

    public static final String ACTION_LIVE_LAYOUT_RELOAD =
            "pro.sketchware.ai.ACTION_LIVE_LAYOUT_RELOAD";

    @Override
    public String getName() {
        return "generate_layout_from_description";
    }

    @Override
    public String getDescription() {
        return "Generates a Sketchware-compatible Android XML layout from a natural language description "
                + "and applies it to the specified activity using ViewBeanParser. "
                + "Use this to create or replace an entire screen layout. "
                + "The AI generates XML, which is parsed by ViewBeanParser into proper ViewBeans, "
                + "then written to the encrypted view file and jC in-memory cache. "
                + "After applying, the design editor reloads the layout live.\n\n"
                + SketchwareViewBridge.buildTypeReference();
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();
        JsonObject scId = new JsonObject();
        scId.addProperty("type", "string");
        scId.addProperty("description", "Project ID (sc_id)");
        props.add("sc_id", scId);

        JsonObject activityName = new JsonObject();
        activityName.addProperty("type", "string");
        activityName.addProperty("description", "Activity name, e.g. 'main' for main.xml");
        props.add("activity_name", activityName);

        JsonObject description = new JsonObject();
        description.addProperty("type", "string");
        description.addProperty("description",
                "Natural language description of the desired layout. "
                        + "Be specific about views, their arrangement, colors, and purpose.");
        props.add("description", description);

        JsonObject xmlLayout = new JsonObject();
        xmlLayout.addProperty("type", "string");
        xmlLayout.addProperty("description",
                "Optional: Provide the complete Android XML layout directly if you already "
                        + "know exactly what XML to use. If provided, description is ignored.");
        props.add("xml_layout", xmlLayout);

        schema.add("properties", props);

        JsonObject required = new JsonObject();
        JsonArray req = new JsonArray();
        req.add("sc_id");
        req.add("activity_name");
        schema.add("required", req);

        return schema;
    }

    @Override
    public ToolResult execute(JsonObject arguments, ToolContext context) {
        String scId = arguments.has("sc_id") ? arguments.get("sc_id").getAsString().trim() : "";
        String actName = arguments.has("activity_name") ? arguments.get("activity_name").getAsString().trim() : "";
        String xmlLayout = arguments.has("xml_layout") ? arguments.get("xml_layout").getAsString().trim() : "";

        if (scId.isEmpty()) return ToolResult.failure(null, "sc_id is required.");
        if (actName.isEmpty()) return ToolResult.failure(null, "activity_name is required.");
        if (xmlLayout.isEmpty())
            return ToolResult.failure(null,
                    "xml_layout is required. Provide the Android XML layout directly. "
                    + "The AI should generate the XML and pass it here.");

        if (!context.isProjectAllowed(scId))
            return ToolResult.failure(null, "Access denied: project " + scId);

        String xmlName = SketchwareViewBridge.normalizeXmlName(actName);

        try {
            ViewBeanParser parser = new ViewBeanParser(xmlLayout);
            parser.setSkipRoot(true);
            ArrayList<ViewBean> parsedBeans = parser.parse();

            if (parsedBeans == null || parsedBeans.isEmpty())
                return ToolResult.failure(null,
                        "XML parse produced no views. Check that XML has android:id attributes "
                        + "and a ViewGroup root.");

            String raw = SketchwareViewBridge.readViewFile(scId);
            java.util.Map<String, java.util.List<String>> sections = SketchwareViewBridge.parseSections(raw);

            java.util.List<String> beanLines = new java.util.ArrayList<>();
            for (ViewBean b : parsedBeans) {
                beanLines.add(GsonUtils.getGson().toJson(b));
            }
            sections.put(xmlName, beanLines);
            SketchwareViewBridge.ensureFabSection(sections, xmlName);

            boolean saved = SketchwareViewBridge.writeViewFile(scId, SketchwareViewBridge.serializeSections(sections));
            if (!saved) return ToolResult.failure(null, "Failed to save view file.");

            SketchwareViewBridge.putViewBeansToMemory(scId, xmlName, parsedBeans);
            SketchwareViewBridge.broadcastBothWithXml(context.getAppContext(), scId, xmlName, xmlLayout);

            return ToolResult.success(null,
                    "Layout generated and applied!\n"
                    + "Activity: " + actName + "\n"
                    + "Views: " + parsedBeans.size() + "\n"
                    + "Design canvas reloaded automatically.");
        } catch (Exception e) {
            return ToolResult.failure(null, "Failed to generate layout: " + e.getMessage());
        }
    }
}