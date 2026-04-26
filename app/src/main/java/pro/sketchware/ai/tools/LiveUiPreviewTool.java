package pro.sketchware.ai.tools;

import android.content.Context;
import android.content.Intent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.util.SketchwareFileDecryptor;
import pro.sketchware.util.SketchwareFileEncryptor;

/**
 * LiveUiPreviewTool — AI ↔ Sketchware View File Live Editing.
 *
 * ═══════════════════════════════════════════════════════════════════
 * ARCHITECTURE (matching Sketchware-IA's proven approach)
 * ═══════════════════════════════════════════════════════════════════
 *
 * Uses SketchwareFileDecryptor / SketchwareFileEncryptor from Sketchware-IA
 * which correctly handle:
 *   - AES/CBC/PKCS5Padding with KEY = IV = "sketchwaresecure"
 *   - Raw ciphertext only (no IV prefix, no Base64)
 *   - Plain-text fallback for new files
 *
 * The view file uses a SECTION-BASED TEXT FORMAT (not JSON array!):
 *   @main.xml
 *   {"id":"main","parent":"root","parentType":0,"index":0,"type":0,...}
 *   {"id":"tv_hello","parent":"main","parentType":0,"index":0,"type":4,...}
 *   @main.xml_fab
 *   {"id":"_fab","parent":"root","parentType":0,"index":0,"type":16,...}
 *   @second.xml
 *   {...}
 *   @second.xml_fab
 *   {...}
 *
 * ═══════════════════════════════════════════════════════════════════
 * SK.txt RULES ENFORCED
 * ═══════════════════════════════════════════════════════════════════
 * Rule 5:  type=4 for TextView (NOT type=2 which is HorizontalScrollView!)
 * Rule 8:  Every screen MUST have a _fab section
 * Rule 9:  Root bean: preIndex=-1, preParent="", preParentType=-1
 * Rule 6:  In horizontal LinearLayout, fill child: width=0 weight=1
 * Width:   -1=match_parent  -2=wrap_content  N=dp
 * Gravity: 0=none  17=center  16=center_h  5=center_v  48=top
 */
public final class LiveUiPreviewTool {

    public static final String ACTION_LIVE_LAYOUT_RELOAD =
            "pro.sketchware.ai.ACTION_LIVE_LAYOUT_RELOAD";

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    private LiveUiPreviewTool() {}

    // ─────────────────────────────────────────────────────────────────
    // Section-format parser — the key to matching IA's behaviour
    // ─────────────────────────────────────────────────────────────────

    /**
     * Parses the decrypted view file text into an ordered map:
     *   sectionName → list of JSON-object lines
     *
     * Input example:
     *   "@main.xml\n{...}\n{...}\n@main.xml_fab\n{...}\n"
     *
     * Output:
     *   "main.xml"      → ["{...}", "{...}"]
     *   "main.xml_fab"  → ["{...}"]
     */
    static Map<String, List<String>> parseSections(String raw) {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return sections;
        String currentSection = null;
        for (String line : raw.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("@")) {
                currentSection = trimmed.substring(1).trim();
                sections.putIfAbsent(currentSection, new ArrayList<>());
            } else if (currentSection != null && !trimmed.isEmpty()) {
                sections.get(currentSection).add(trimmed);
            }
        }
        return sections;
    }

    /**
     * Serialises the section map back to the @ section text format.
     * Each section starts with @sectionName, followed by one JSON per line.
     */
    static String serialiseSections(Map<String, List<String>> sections) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : sections.entrySet()) {
            sb.append('@').append(entry.getKey()).append('\n');
            for (String line : entry.getValue()) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    /** Normalises "main" → "main.xml", leaves "main.xml" unchanged. */
    static String toXmlName(String name) {
        if (name == null) return null;
        name = name.trim();
        return name.endsWith(".xml") ? name : name + ".xml";
    }

    /**
     * Default FAB placeholder — required in every screen's _fab section.
     * Per SK.txt Rule 8: even if you don't use FAB, the section must exist.
     */
    static String defaultFabBean() {
        return "{\"adSize\":\"\",\"adUnitId\":\"\",\"alpha\":1.0,"
                + "\"checked\":0,\"choiceMode\":0,\"clickable\":1,"
                + "\"convert\":\"\",\"customView\":\"\",\"dividerHeight\":1,"
                + "\"enabled\":1,\"firstDayOfWeek\":1,\"id\":\"_fab\","
                + "\"image\":{\"resName\":\"default_image\",\"rotate\":0,\"scaleType\":\"CENTER\"},"
                + "\"indeterminate\":\"false\",\"index\":0,\"inject\":\"\","
                + "\"layout\":{\"backgroundColor\":-13730510,\"borderColor\":-3617307,"
                + "\"gravity\":0,\"height\":-1,\"layoutGravity\":0,"
                + "\"marginBottom\":0,\"marginLeft\":0,\"marginRight\":0,\"marginTop\":0,"
                + "\"orientation\":-1,\"paddingBottom\":0,\"paddingLeft\":0,"
                + "\"paddingRight\":0,\"paddingTop\":0,\"weight\":0,\"weightSum\":0,\"width\":-1},"
                + "\"max\":100,\"parent\":\"root\",\"parentType\":0,"
                + "\"preId\":\"_fab\",\"preIndex\":-1,\"preParent\":\"\",\"preParentType\":-1,"
                + "\"progress\":0,\"progressStyle\":\"?android:progressBarStyle\","
                + "\"scaleX\":1.0,\"scaleY\":1.0,\"spinnerMode\":1,"
                + "\"text\":{\"hint\":\"\",\"hintColor\":-10453621,\"imeOption\":0,"
                + "\"inputType\":1,\"line\":0,\"singleLine\":0,\"text\":\"\","
                + "\"textColor\":-16777216,\"textFont\":\"default_font\","
                + "\"textSize\":12,\"textType\":0},"
                + "\"translationX\":0.0,\"translationY\":0.0,\"type\":16}";
    }

    /**
     * Ensures both the main section and _fab section exist for an activity.
     * Creates them with defaults if missing. Per SK.txt Rule 8.
     */
    static void ensureSections(Map<String, List<String>> sections, String xmlName) {
        sections.putIfAbsent(xmlName, new ArrayList<>());
        String fabKey = xmlName + "_fab";
        if (!sections.containsKey(fabKey)) {
            sections.put(fabKey, new ArrayList<>(List.of(defaultFabBean())));
        }
    }

    /** Flushes jC view cache then reloads from disk. */
    static void flushCache(String scId) {
        try {
            a.a.a.jC.b();
            a.a.a.jC.a(scId, true);
        } catch (Throwable ignored) {}
    }

    /** Sends a live-reload broadcast to DesignActivity. */
    static void broadcast(Context ctx, String scId, String activityXml) {
        try {
            Intent i = new Intent(ACTION_LIVE_LAYOUT_RELOAD);
            i.putExtra("sc_id", scId);
            i.putExtra("activity_xml", toXmlName(activityXml));
            ctx.sendBroadcast(i);
        } catch (Exception ignored) {}
    }

    /** Reads and decrypts the view file using IA's proven decryptor. */
    static String readView(String scId) {
        String content = SketchwareFileDecryptor.decryptFile(scId, "view");
        return content != null ? content : "";
    }

    /**
     * Encrypts and saves the view file using IA's proven encryptor.
     * Accepts the full serialised @ section text.
     */
    static boolean writeView(String scId, String content) {
        return SketchwareFileEncryptor.encryptAndSaveFile(scId, "view", content);
    }

    // ─────────────────────────────────────────────────────────────────
    // ViewBean normaliser — enforces SK.txt rules on every bean
    // ─────────────────────────────────────────────────────────────────

    /**
     * Ensures a ViewBean JSON object has all required fields.
     * Applies SK.txt root-bean rules (preIndex=-1, preParent="", preParentType=-1).
     * Validates type constants (rejects type=2 used as TextView).
     *
     * @throws IllegalArgumentException if the bean violates a hard SK.txt rule
     */
    static void normaliseBean(JsonObject bean, int defaultIndex) {
        // ── Type validation ──────────────────────────────────────────
        int type = getInt(bean, "type", 4);
        // SK.txt Rule 5: type=2 is HorizontalScrollView, NOT TextView!
        if (type == 2 && bean.has("text")) {
            // If bean has text properties and type=2, it's likely a mistake
            JsonObject text = bean.getAsJsonObject("text");
            if (text != null && text.has("text") && !text.get("text").getAsString().isEmpty()) {
                throw new IllegalArgumentException(
                        "type=2 is HorizontalScrollView — NOT TextView. Use type=4 for TextView. (SK.txt Rule 5)");
            }
        }

        // ── Scalar defaults ──────────────────────────────────────────
        if (!bean.has("index"))          bean.addProperty("index", defaultIndex);
        if (!bean.has("alpha"))          bean.addProperty("alpha", 1.0);
        if (!bean.has("checked"))        bean.addProperty("checked", 0);
        if (!bean.has("choiceMode"))     bean.addProperty("choiceMode", 0);
        if (!bean.has("clickable"))      bean.addProperty("clickable", 1);
        if (!bean.has("convert"))        bean.addProperty("convert", "");
        if (!bean.has("customView"))     bean.addProperty("customView", "");
        if (!bean.has("dividerHeight"))  bean.addProperty("dividerHeight", 1);
        if (!bean.has("enabled"))        bean.addProperty("enabled", 1);
        if (!bean.has("firstDayOfWeek")) bean.addProperty("firstDayOfWeek", 1);
        if (!bean.has("indeterminate"))  bean.addProperty("indeterminate", "false");
        if (!bean.has("inject"))         bean.addProperty("inject", "");
        if (!bean.has("max"))            bean.addProperty("max", 100);
        if (!bean.has("progress"))       bean.addProperty("progress", 0);
        if (!bean.has("progressStyle"))  bean.addProperty("progressStyle", "?android:progressBarStyle");
        if (!bean.has("scaleX"))         bean.addProperty("scaleX", 1.0);
        if (!bean.has("scaleY"))         bean.addProperty("scaleY", 1.0);
        if (!bean.has("spinnerMode"))    bean.addProperty("spinnerMode", 1);
        if (!bean.has("translationX"))   bean.addProperty("translationX", 0.0);
        if (!bean.has("translationY"))   bean.addProperty("translationY", 0.0);
        if (!bean.has("adSize"))         bean.addProperty("adSize", "");
        if (!bean.has("adUnitId"))       bean.addProperty("adUnitId", "");

        // ── SK.txt pre* rules ────────────────────────────────────────
        String id         = getStr(bean, "id", "");
        String parent     = getStr(bean, "parent", "root");
        int    index      = getInt(bean, "index", defaultIndex);
        int    parentType = getInt(bean, "parentType", 0);
        boolean isRoot    = "root".equals(parent);

        // Rule 9: Root bean gets special pre* values
        if (!bean.has("preId"))         bean.addProperty("preId", id);
        if (!bean.has("preIndex"))      bean.addProperty("preIndex", isRoot ? -1 : index);
        if (!bean.has("preParent"))     bean.addProperty("preParent", isRoot ? "" : parent);
        if (!bean.has("preParentType")) bean.addProperty("preParentType", isRoot ? -1 : parentType);

        // ── layout sub-object ────────────────────────────────────────
        if (!bean.has("layout")) bean.add("layout", new JsonObject());
        JsonObject layout = bean.getAsJsonObject("layout");
        if (!layout.has("width"))           layout.addProperty("width", -1);
        if (!layout.has("height"))          layout.addProperty("height", -2);
        if (!layout.has("orientation"))     layout.addProperty("orientation", -1);
        if (!layout.has("backgroundColor")) layout.addProperty("backgroundColor", 0);
        if (!layout.has("borderColor"))     layout.addProperty("borderColor", -3617307);
        if (!layout.has("gravity"))         layout.addProperty("gravity", 0);
        if (!layout.has("layoutGravity"))   layout.addProperty("layoutGravity", 0);
        if (!layout.has("weight"))          layout.addProperty("weight", 0);
        if (!layout.has("weightSum"))       layout.addProperty("weightSum", 0);
        for (String f : new String[]{"marginLeft","marginRight","marginTop","marginBottom",
                "paddingLeft","paddingRight","paddingTop","paddingBottom"}) {
            if (!layout.has(f)) layout.addProperty(f, 0);
        }

        // ── text sub-object ──────────────────────────────────────────
        if (!bean.has("text")) bean.add("text", new JsonObject());
        JsonObject text = bean.getAsJsonObject("text");
        if (!text.has("text"))       text.addProperty("text", "");
        if (!text.has("hint"))       text.addProperty("hint", "");
        if (!text.has("textColor"))  text.addProperty("textColor", -16777216);
        if (!text.has("hintColor"))  text.addProperty("hintColor", -10453621);
        if (!text.has("textSize"))   text.addProperty("textSize", 12);
        if (!text.has("textType"))   text.addProperty("textType", 0);
        if (!text.has("textFont"))   text.addProperty("textFont", "default_font");
        if (!text.has("singleLine")) text.addProperty("singleLine", 0);
        if (!text.has("inputType"))  text.addProperty("inputType", 1);
        if (!text.has("imeOption"))  text.addProperty("imeOption", 0);
        if (!text.has("line"))       text.addProperty("line", 0);

        // ── image sub-object ─────────────────────────────────────────
        if (!bean.has("image")) bean.add("image", new JsonObject());
        JsonObject image = bean.getAsJsonObject("image");
        if (!image.has("resName"))   image.addProperty("resName", "default_image");
        if (!image.has("rotate"))    image.addProperty("rotate", 0);
        if (!image.has("scaleType")) image.addProperty("scaleType", "CENTER");
    }

    // ─────────────────────────────────────────────────────────────────
    // Tool: describe_layout_live
    // ─────────────────────────────────────────────────────────────────

    public static class DescribeLayoutLiveTool implements AgentTool {

        @Override public String getName() { return "describe_layout_live"; }

        @Override
        public String getDescription() {
            return "Reads and decrypts the Sketchware view file, then returns the current ViewBean "
                    + "list for a specific activity as human-readable text. "
                    + "ALWAYS call this before add_view_live, modify_view_live, or build_screen_layout "
                    + "to understand the existing structure. "
                    + "Also lists ALL sections present in the file so you know which activities exist. "
                    + "Type reference: 0=LinearLayout 3=Button 4=TextView 5=EditText "
                    + "6=ImageView 9=ListView 12=ScrollView 13=Switch 16=FAB";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject props = new JsonObject();
            addStr(props, "sc_id",         "Project ID (sc_id)");
            addStr(props, "activity_xml",  "Activity name e.g. 'main' or 'main.xml'");
            JsonObject s = schema(props); req(s, "sc_id", "activity_xml"); return s;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = str(args, "sc_id");
            String xml  = toXmlName(str(args, "activity_xml"));
            if (scId == null || xml == null) return err("sc_id and activity_xml required");
            if (!ctx.isProjectAllowed(scId)) return err("Project not in workspace");

            String raw = readView(scId);
            Map<String, List<String>> sections = parseSections(raw);

            List<String> viewLines = sections.getOrDefault(xml, new ArrayList<>());
            List<String> fabLines  = sections.getOrDefault(xml + "_fab", new ArrayList<>());

            StringBuilder sb = new StringBuilder("=== Layout: " + xml + " ===\n");
            sb.append("Views: ").append(viewLines.size()).append("\n");
            for (String line : viewLines) {
                try {
                    JsonObject b = JsonParser.parseString(line).getAsJsonObject();
                    JsonObject l = b.has("layout") ? b.getAsJsonObject("layout") : new JsonObject();
                    sb.append("  id=").append(getStr(b, "id", "?"))
                      .append(" type=").append(getInt(b, "type", -1))
                      .append(" parent=").append(getStr(b, "parent", "?"))
                      .append(" idx=").append(getInt(b, "index", -1))
                      .append(" w=").append(getInt(l, "width", -2))
                      .append(" h=").append(getInt(l, "height", -2));
                    int ori = getInt(l, "orientation", -1);
                    if (ori >= 0) sb.append(" ori=").append(ori == 0 ? "H" : "V");
                    String txt = b.has("text")
                            ? getStr(b.getAsJsonObject("text"), "text", "") : "";
                    if (!txt.isEmpty()) sb.append(" text=\"").append(
                            txt.length() > 30 ? txt.substring(0, 30) + "…" : txt).append("\"");
                    sb.append("\n");
                } catch (Exception e) {
                    sb.append("  [parse error]: ").append(line, 0, Math.min(80, line.length())).append("\n");
                }
            }
            sb.append("FAB section: ").append(fabLines.size()).append(" item(s)\n");
            sb.append("\nAll sections in file:\n");
            for (String key : sections.keySet()) sb.append("  @").append(key).append("\n");
            if (sections.isEmpty()) sb.append("  (file is empty or not found)\n");

            return ok(sb.toString());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Tool: build_screen_layout  (PRIMARY creation tool)
    // ─────────────────────────────────────────────────────────────────

    public static class BuildScreenLayoutTool implements AgentTool {

        @Override public String getName() { return "build_screen_layout"; }

        @Override
        public String getDescription() {
            return "Replaces an entire activity screen layout with a new ViewBean array. "
                    + "This is the PRIMARY tool for building UI screens from scratch. "
                    + "Provide a JSON array of ViewBean objects. Each bean must follow SK.txt format:\n"
                    + "  Required: id, parent, parentType, index, type\n"
                    + "  layout: {width, height, orientation, backgroundColor, gravity, weight, "
                    + "marginLeft/Right/Top/Bottom, paddingLeft/Right/Top/Bottom}\n"
                    + "  text: {text, textSize, textColor, textType, hint, inputType, singleLine}\n"
                    + "  image: {resName, rotate, scaleType}\n"
                    + "  preId=id, preIndex (root=-1, other=index), preParent (root='', other=parent), "
                    + "preParentType (root=-1, other=parentType)\n\n"
                    + "TYPE CONSTANTS: 0=LinearLayout 3=Button 4=TextView 5=EditText "
                    + "6=ImageView 9=ListView 12=ScrollView 13=Switch\n"
                    + "WIDTH/HEIGHT: -1=match_parent -2=wrap_content N=dp\n"
                    + "GRAVITY: 0=none 17=center 16=center_h 5=center_v 48=top|center_h\n"
                    + "COLORS (ARGB signed int): -1=white -16777216=black -13730510=#3F51B5\n"
                    + "CRITICAL: type=2 is HorizontalScrollView NOT TextView! Use type=4.\n"
                    + "CRITICAL: FAB (type=16) must go in fab_json param not views array.\n"
                    + "CRITICAL: Every screen needs a _fab section (auto-created if fab_json omitted).";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject props = new JsonObject();
            addStr(props, "sc_id",        "Project ID (sc_id)");
            addStr(props, "activity_xml", "Activity name e.g. 'main' or 'main.xml'");
            JsonObject viewsP = new JsonObject();
            viewsP.addProperty("type", "array");
            viewsP.addProperty("description",
                    "Array of ViewBean JSON objects. Each must have id, parent, parentType, index, type. "
                    + "Root bean: preIndex=-1, preParent='', preParentType=-1. "
                    + "Other beans: preId=id, preIndex=index, preParent=parent, preParentType=parentType.");
            props.add("views", viewsP);
            JsonObject fabP = new JsonObject();
            fabP.addProperty("type", "string");
            fabP.addProperty("description",
                    "Optional FAB ViewBean JSON string (type=16). If omitted, default placeholder used.");
            props.add("fab_json", fabP);
            JsonObject s = schema(props); req(s, "sc_id", "activity_xml", "views"); return s;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = str(args, "sc_id");
            String xml  = toXmlName(str(args, "activity_xml"));
            if (scId == null || xml == null) return err("sc_id and activity_xml required");
            if (!ctx.isProjectAllowed(scId)) return err("Project not in workspace");
            if (!args.has("views") || args.get("views").isJsonNull())
                return err("views array is required");

            try {
                JsonArray viewsArr = args.get("views").getAsJsonArray();
                if (viewsArr.size() == 0) return err("views array is empty");

                // Validate and normalise each bean
                List<String> newLines = new ArrayList<>();
                for (int i = 0; i < viewsArr.size(); i++) {
                    JsonObject bean = viewsArr.get(i).getAsJsonObject();
                    // Validate type=2 mistake (SK.txt Rule 5)
                    try { normaliseBean(bean, i); }
                    catch (IllegalArgumentException e) {
                        return err("Bean[" + i + "] error: " + e.getMessage());
                    }
                    newLines.add(GSON.toJson(bean));
                }

                // Read current file and update sections
                String raw = readView(scId);
                Map<String, List<String>> sections = parseSections(raw);
                sections.put(xml, newLines);

                // FAB section — use provided or default
                String fabKey = xml + "_fab";
                if (args.has("fab_json") && !args.get("fab_json").isJsonNull()) {
                    String fabStr = args.get("fab_json").getAsString().trim();
                    sections.put(fabKey, new ArrayList<>(List.of(fabStr)));
                } else {
                    sections.putIfAbsent(fabKey, new ArrayList<>(List.of(defaultFabBean())));
                }

                String serialised = serialiseSections(sections);
                boolean saved = writeView(scId, serialised);
                if (!saved) return err("Failed to save view file. Check storage permissions.");

                flushCache(scId);
                broadcast(ctx.getAppContext(), scId, xml);

                return ok("Screen layout built successfully!\n"
                        + "Activity: " + xml + "\n"
                        + "Views: " + newLines.size() + "\n"
                        + "_fab section: present\n"
                        + "Live reload: sent");
            } catch (Exception e) {
                return err("build_screen_layout failed: " + e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Tool: add_view_live
    // ─────────────────────────────────────────────────────────────────

    public static class AddViewLiveTool implements AgentTool {

        @Override public String getName() { return "add_view_live"; }

        @Override
        public String getDescription() {
            return "Adds a single ViewBean to an activity layout and triggers live reload. "
                    + "Reads the current encrypted view file, appends the new bean to the correct "
                    + "@section, encrypts and saves, then broadcasts reload to DesignActivity.\n"
                    + "Type: 0=LinearLayout 3=Button 4=TextView 5=EditText "
                    + "6=ImageView 9=ListView 12=ScrollView 13=Switch (NOT 16=FAB here!)\n"
                    + "parent='root' for the top-level container.\n"
                    + "width=-1=match_parent -2=wrap_content N=dp\n"
                    + "In horizontal LinearLayout with weight: use width=0, weight=1 (SK.txt Rule 6)\n"
                    + "backgroundColor is ARGB signed int: -1=transparent/white 0=no color";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject props = new JsonObject();
            addStr(props, "sc_id",          "Project ID");
            addStr(props, "activity_xml",   "Activity name e.g. 'main'");
            addStr(props, "parent_id",      "Parent container id, or 'root'");
            addIntP(props, "parent_type",   "Parent type: 0=LinearLayout 12=ScrollView 1=RelativeLayout");
            addStr(props, "view_id",        "Unique ID for new widget");
            addIntP(props, "type",          "Widget type: 0=LinearLayout 3=Button 4=TextView 5=EditText 6=ImageView 9=ListView 12=ScrollView 13=Switch");
            addIntP(props, "width",         "Width: -1=match_parent -2=wrap_content N=dp");
            addIntP(props, "height",        "Height: -1=match_parent -2=wrap_content N=dp");
            addIntP(props, "orientation",   "For LinearLayout: 0=horizontal 1=vertical");
            addIntP(props, "background_color", "Background ARGB signed int");
            addIntP(props, "gravity",       "Gravity: 0=none 17=center 16=center_h 5=center_v");
            addIntP(props, "padding_left",  "Left padding dp"); addIntP(props, "padding_right", "Right padding dp");
            addIntP(props, "padding_top",   "Top padding dp");  addIntP(props, "padding_bottom", "Bottom padding dp");
            addIntP(props, "margin_left",   "Left margin dp");  addIntP(props, "margin_right", "Right margin dp");
            addIntP(props, "margin_top",    "Top margin dp");   addIntP(props, "margin_bottom", "Bottom margin dp");
            addIntP(props, "weight",        "Layout weight (use with width=0 for fill in horizontal LL)");
            addStr(props, "text",           "Display text");
            addIntP(props, "text_size",     "Text size sp");
            addIntP(props, "text_color",    "Text color ARGB signed int");
            addIntP(props, "text_type",     "0=normal 1=bold 2=italic 3=bold+italic");
            addStr(props, "hint",           "Hint text for EditText");
            addStr(props, "res_name",       "Image resource for ImageView");
            addStr(props, "custom_view",    "For ListView: custom view name");
            JsonObject s = schema(props);
            req(s, "sc_id", "activity_xml", "parent_id", "view_id", "type");
            return s;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId     = str(args, "sc_id");
            String xml      = toXmlName(str(args, "activity_xml"));
            String parentId = str(args, "parent_id");
            String viewId   = str(args, "view_id");
            if (scId == null || xml == null || parentId == null || viewId == null)
                return err("Required: sc_id, activity_xml, parent_id, view_id, type");
            if (!ctx.isProjectAllowed(scId)) return err("Project not in workspace");

            int type       = getInt(args, "type",            4);
            int width      = getInt(args, "width",          -1);
            int height     = getInt(args, "height",         -2);
            int ori        = getInt(args, "orientation",    type == 0 ? 1 : -1);
            int bg         = getInt(args, "background_color", 0);
            int grav       = getInt(args, "gravity",         0);
            int pl = getInt(args,"padding_left",0), pr = getInt(args,"padding_right",0);
            int pt = getInt(args,"padding_top",0),  pb = getInt(args,"padding_bottom",0);
            int ml = getInt(args,"margin_left",0),  mr = getInt(args,"margin_right",0);
            int mt = getInt(args,"margin_top",0),   mb = getInt(args,"margin_bottom",0);
            int weight     = getInt(args, "weight",          0);
            int parentType = getInt(args, "parent_type",     0);
            String text    = str(args, "text"); if (text == null) text = "";
            int textSize   = getInt(args, "text_size",      14);
            int textColor  = getInt(args, "text_color",     -16777216);
            int textType   = getInt(args, "text_type",       0);
            String hint    = str(args, "hint"); if (hint == null) hint = "";
            String res     = str(args, "res_name"); if (res == null) res = "default_image";
            String cv      = str(args, "custom_view"); if (cv == null) cv = "";

            // SK.txt Rule 5
            if (type == 2) return err("type=2 is HorizontalScrollView NOT TextView. Use type=4. (SK.txt Rule 5)");

            try {
                String raw = readView(scId);
                Map<String, List<String>> sections = parseSections(raw);
                ensureSections(sections, xml);
                List<String> viewLines = sections.get(xml);
                int index = viewLines.size();
                boolean isRoot = "root".equals(parentId);

                JsonObject bean = new JsonObject();
                bean.addProperty("adSize", ""); bean.addProperty("adUnitId", "");
                bean.addProperty("alpha", 1.0); bean.addProperty("checked", 0);
                bean.addProperty("choiceMode", 0); bean.addProperty("clickable", 1);
                bean.addProperty("convert", ""); bean.addProperty("customView", cv);
                bean.addProperty("dividerHeight", 1); bean.addProperty("enabled", 1);
                bean.addProperty("firstDayOfWeek", 1); bean.addProperty("id", viewId);
                JsonObject img = new JsonObject();
                img.addProperty("resName", res); img.addProperty("rotate", 0);
                img.addProperty("scaleType", "CENTER"); bean.add("image", img);
                bean.addProperty("indeterminate", "false"); bean.addProperty("index", index);
                bean.addProperty("inject", "");
                JsonObject layout = new JsonObject();
                layout.addProperty("backgroundColor", bg); layout.addProperty("borderColor", -3617307);
                layout.addProperty("gravity", grav); layout.addProperty("height", height);
                layout.addProperty("layoutGravity", 0);
                layout.addProperty("marginBottom", mb); layout.addProperty("marginLeft", ml);
                layout.addProperty("marginRight", mr);  layout.addProperty("marginTop", mt);
                layout.addProperty("orientation", ori);
                layout.addProperty("paddingBottom", pb); layout.addProperty("paddingLeft", pl);
                layout.addProperty("paddingRight", pr);  layout.addProperty("paddingTop", pt);
                layout.addProperty("weight", weight); layout.addProperty("weightSum", 0);
                layout.addProperty("width", width); bean.add("layout", layout);
                bean.addProperty("max", 100); bean.addProperty("parent", parentId);
                bean.addProperty("parentType", parentType);
                bean.addProperty("preId", viewId);
                bean.addProperty("preIndex",      isRoot ? -1 : index);
                bean.addProperty("preParent",     isRoot ? "" : parentId);
                bean.addProperty("preParentType", isRoot ? -1 : parentType);
                bean.addProperty("progress", 0);
                bean.addProperty("progressStyle", "?android:progressBarStyle");
                bean.addProperty("scaleX", 1.0); bean.addProperty("scaleY", 1.0);
                bean.addProperty("spinnerMode", 1);
                JsonObject textObj = new JsonObject();
                textObj.addProperty("hint", hint); textObj.addProperty("hintColor", -10453621);
                textObj.addProperty("imeOption", 0); textObj.addProperty("inputType", 1);
                textObj.addProperty("line", 0); textObj.addProperty("singleLine", 0);
                textObj.addProperty("text", text); textObj.addProperty("textColor", textColor);
                textObj.addProperty("textFont", "default_font");
                textObj.addProperty("textSize", textSize); textObj.addProperty("textType", textType);
                bean.add("text", textObj);
                bean.addProperty("translationX", 0.0); bean.addProperty("translationY", 0.0);
                bean.addProperty("type", type);

                viewLines.add(GSON.toJson(bean));
                sections.put(xml, viewLines);

                boolean saved = writeView(scId, serialiseSections(sections));
                if (!saved) return err("Failed to save view file");
                flushCache(scId);
                broadcast(ctx.getAppContext(), scId, xml);

                return ok("Added: id=" + viewId + " type=" + type
                        + " parent=" + parentId + " index=" + index + "\nLive reload sent.");
            } catch (Exception e) {
                return err("add_view_live failed: " + e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Tool: modify_view_live
    // ─────────────────────────────────────────────────────────────────

    public static class ModifyViewLiveTool implements AgentTool {

        @Override public String getName() { return "modify_view_live"; }

        @Override
        public String getDescription() {
            return "Updates properties of an existing ViewBean using dot-path notation. "
                    + "Reads the encrypted view file, applies the patch, saves, and broadcasts reload. "
                    + "Patch keys: 'text.text', 'text.textSize', 'text.textColor', "
                    + "'layout.width', 'layout.height', 'layout.backgroundColor', "
                    + "'layout.orientation', 'layout.gravity', 'layout.weight', "
                    + "'image.resName', 'image.scaleType', 'clickable', 'enabled'. "
                    + "Example: {\"text.text\":\"Hello\", \"layout.backgroundColor\":-13730510}";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject props = new JsonObject();
            addStr(props, "sc_id",        "Project ID");
            addStr(props, "activity_xml", "Activity name e.g. 'main'");
            addStr(props, "view_id",      "ID of view to modify");
            JsonObject patch = new JsonObject();
            patch.addProperty("type", "object");
            patch.addProperty("description",
                    "Properties to update. Use dot-path for nested: 'layout.width', 'text.text'.");
            props.add("properties", patch);
            JsonObject s = schema(props);
            req(s, "sc_id", "activity_xml", "view_id", "properties");
            return s;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId   = str(args, "sc_id");
            String xml    = toXmlName(str(args, "activity_xml"));
            String viewId = str(args, "view_id");
            if (scId == null || xml == null || viewId == null)
                return err("Required: sc_id, activity_xml, view_id, properties");
            if (!args.has("properties") || args.get("properties").isJsonNull())
                return err("properties object required");
            if (!ctx.isProjectAllowed(scId)) return err("Project not in workspace");

            try {
                String raw = readView(scId);
                Map<String, List<String>> sections = parseSections(raw);
                List<String> lines = sections.get(xml);
                if (lines == null) return err("Activity '" + xml + "' not found in view file");

                JsonObject patch = args.getAsJsonObject("properties");
                boolean found = false;
                List<String> updated = new ArrayList<>();

                for (String line : lines) {
                    JsonObject bean = JsonParser.parseString(line).getAsJsonObject();
                    if (viewId.equals(getStr(bean, "id", ""))) {
                        found = true;
                        for (Map.Entry<String, JsonElement> e : patch.entrySet()) {
                            String key = e.getKey();
                            JsonElement val = e.getValue();
                            if (key.contains(".")) {
                                String[] p = key.split("\\.", 2);
                                if (!bean.has(p[0])) bean.add(p[0], new JsonObject());
                                bean.getAsJsonObject(p[0]).add(p[1], val);
                            } else {
                                bean.add(key, val);
                            }
                        }
                    }
                    updated.add(GSON.toJson(bean));
                }

                if (!found) return err("View '" + viewId + "' not found in " + xml);
                sections.put(xml, updated);

                boolean saved = writeView(scId, serialiseSections(sections));
                if (!saved) return err("Failed to save view file");
                flushCache(scId);
                broadcast(ctx.getAppContext(), scId, xml);

                return ok("View '" + viewId + "' updated. Live reload sent.");
            } catch (Exception e) {
                return err("modify_view_live failed: " + e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Tool: remove_view_live
    // ─────────────────────────────────────────────────────────────────

    public static class RemoveViewLiveTool implements AgentTool {

        @Override public String getName() { return "remove_view_live"; }

        @Override
        public String getDescription() {
            return "Removes a ViewBean by ID (and all its descendants) from an activity layout. "
                    + "Reads the encrypted view file, removes the bean(s), saves, and broadcasts reload.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject props = new JsonObject();
            addStr(props, "sc_id",        "Project ID");
            addStr(props, "activity_xml", "Activity name e.g. 'main'");
            addStr(props, "view_id",      "ID of view to remove");
            JsonObject s = schema(props); req(s, "sc_id", "activity_xml", "view_id"); return s;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId   = str(args, "sc_id");
            String xml    = toXmlName(str(args, "activity_xml"));
            String viewId = str(args, "view_id");
            if (scId == null || xml == null || viewId == null)
                return err("Required: sc_id, activity_xml, view_id");
            if (!ctx.isProjectAllowed(scId)) return err("Project not in workspace");

            try {
                String raw = readView(scId);
                Map<String, List<String>> sections = parseSections(raw);
                List<String> lines = sections.get(xml);
                if (lines == null) return err("Activity '" + xml + "' not found");

                // Collect IDs to remove (target + all descendants)
                Set<String> toRemove = new java.util.HashSet<>();
                java.util.Queue<String> queue = new java.util.LinkedList<>();
                for (String line : lines) {
                    try {
                        JsonObject b = JsonParser.parseString(line).getAsJsonObject();
                        if (viewId.equals(getStr(b, "id", ""))) {
                            toRemove.add(viewId); queue.add(viewId); break;
                        }
                    } catch (Exception ignored) {}
                }
                if (toRemove.isEmpty()) return err("View '" + viewId + "' not found in " + xml);
                while (!queue.isEmpty()) {
                    String pid = queue.poll();
                    for (String line : lines) {
                        try {
                            JsonObject b = JsonParser.parseString(line).getAsJsonObject();
                            String bid = getStr(b, "id", "");
                            if (pid.equals(getStr(b, "parent", "")) && !toRemove.contains(bid)) {
                                toRemove.add(bid); queue.add(bid);
                            }
                        } catch (Exception ignored) {}
                    }
                }

                List<String> filtered = new ArrayList<>();
                for (String line : lines) {
                    try {
                        JsonObject b = JsonParser.parseString(line).getAsJsonObject();
                        if (!toRemove.contains(getStr(b, "id", ""))) filtered.add(line);
                    } catch (Exception ignored) { filtered.add(line); }
                }
                sections.put(xml, filtered);

                boolean saved = writeView(scId, serialiseSections(sections));
                if (!saved) return err("Failed to save view file");
                flushCache(scId);
                broadcast(ctx.getAppContext(), scId, xml);

                return ok("Removed '" + viewId + "' and " + (toRemove.size() - 1)
                        + " descendants. Live reload sent.");
            } catch (Exception e) {
                return err("remove_view_live failed: " + e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    private static ToolResult ok(String s)  { return ToolResult.success(null, s); }
    private static ToolResult err(String s) { return ToolResult.failure(null, s); }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString().trim() : null;
    }
    private static String getStr(JsonObject o, String k, String def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
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
}
