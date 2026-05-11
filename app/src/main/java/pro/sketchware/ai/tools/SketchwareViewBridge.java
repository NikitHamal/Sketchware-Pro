package pro.sketchware.ai.tools;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.besome.sketch.beans.ImageBean;
import com.besome.sketch.beans.LayoutBean;
import com.besome.sketch.beans.TextBean;
import com.besome.sketch.beans.ViewBean;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import mod.agus.jcoderz.beans.ViewBeans;
import pro.sketchware.util.SketchwareFileDecryptor;
import pro.sketchware.util.SketchwareFileEncryptor;

public final class SketchwareViewBridge {

    private static final String TAG = "SketchwareViewBridge";
    public static final String ACTION_LAYOUT_CHANGED = "pro.sketchware.ai.ACTION_LAYOUT_CHANGED";
    public static final String ACTION_LIVE_LAYOUT_RELOAD = "pro.sketchware.ai.ACTION_LIVE_LAYOUT_RELOAD";
    public static final String EXTRA_SC_ID = "sc_id";
    public static final String EXTRA_ACTIVITY_XML = "activity_xml";

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    private SketchwareViewBridge() {}

    public static String normalizeXmlName(String name) {
        if (name == null) return null;
        name = name.trim();
        return name.endsWith(".xml") ? name : name + ".xml";
    }

    public static Map<String, List<String>> parseSections(String raw) {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return sections;
        String cur = null;
        for (String line : raw.split("\\r?\\n")) {
            String t = line.trim();
            if (t.startsWith("@")) {
                cur = t.substring(1).trim();
                sections.putIfAbsent(cur, new ArrayList<>());
            } else if (cur != null && !t.isEmpty()) {
                sections.get(cur).add(t);
            }
        }
        return sections;
    }

    public static String serializeSections(Map<String, List<String>> sections) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> e : sections.entrySet()) {
            sb.append('@').append(e.getKey()).append('\n');
            for (String line : e.getValue()) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    public static String readViewFile(String scId) {
        String content = SketchwareFileDecryptor.decryptFile(scId, "view");
        return content != null ? content : "";
    }

    public static boolean writeViewFile(String scId, String content) {
        boolean saved = SketchwareFileEncryptor.encryptAndSaveFile(scId, "view", content);
        if (saved) {
            flushCache(scId);
        }
        return saved;
    }

    public static void flushCache(String scId) {
        try {
            a.a.a.jC.b();
            a.a.a.jC.a(scId, true);
        } catch (Throwable ignored) {}
    }

    public static ArrayList<ViewBean> getViewBeansFromMemory(String scId, String xmlName) {
        try {
            ArrayList<ViewBean> beans = a.a.a.jC.a(scId).d(xmlName);
            if (beans != null && !beans.isEmpty()) {
                return new ArrayList<>(beans);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static void putViewBeansToMemory(String scId, String xmlName, ArrayList<ViewBean> beans) {
        try {
            a.a.a.jC.a(scId).c.remove(xmlName);
            a.a.a.jC.a(scId).c.put(xmlName, beans);
        } catch (Throwable ignored) {}
    }

    public static ArrayList<ViewBean> parseViewBeansFromDisk(String scId, String xmlName) {
        String raw = readViewFile(scId);
        Map<String, List<String>> sections = parseSections(raw);
        List<String> lines = sections.get(xmlName);
        if (lines == null || lines.isEmpty()) return new ArrayList<>();

        ArrayList<ViewBean> beans = new ArrayList<>();
        for (String line : lines) {
            try {
                ViewBean bean = GSON.fromJson(line, ViewBean.class);
                if (bean != null) beans.add(bean);
            } catch (Exception e) {
                Log.w(TAG, "Failed to parse ViewBean line: " + e.getMessage());
            }
        }
        return beans;
    }

    public static ArrayList<ViewBean> getViewBeans(String scId, String xmlName) {
        ArrayList<ViewBean> fromMemory = getViewBeansFromMemory(scId, xmlName);
        if (fromMemory != null && !fromMemory.isEmpty()) return fromMemory;
        return parseViewBeansFromDisk(scId, xmlName);
    }

    public static String serializeViewBeans(ArrayList<ViewBean> beans) {
        JsonArray arr = new JsonArray();
        for (ViewBean bean : beans) {
            arr.add(GSON.toJsonTree(bean));
        }
        return GSON.toJson(arr);
    }

    public static void ensureFabSection(Map<String, List<String>> sections, String xmlName) {
        String fabKey = xmlName + "_fab";
        if (!sections.containsKey(fabKey)) {
            sections.put(fabKey, new ArrayList<>(List.of(defaultFabBeanJson())));
        }
    }

    public static String defaultFabBeanJson() {
        ViewBean fab = new ViewBean("_fab", ViewBean.VIEW_TYPE_WIDGET_FAB);
        fab.parent = "root";
        fab.parentType = ViewBean.VIEW_TYPE_LAYOUT_LINEAR;
        fab.index = 0;
        fab.preId = "_fab";
        fab.preIndex = -1;
        fab.preParent = "";
        fab.preParentType = -1;
        fab.layout.width = LayoutBean.LAYOUT_MATCH_PARENT;
        fab.layout.height = LayoutBean.LAYOUT_WRAP_CONTENT;
        return GSON.toJson(fab);
    }

    public static void broadcastLayoutChanged(Context ctx, String scId, String xmlName) {
        try {
            Intent i = new Intent(ACTION_LAYOUT_CHANGED);
            i.putExtra(EXTRA_SC_ID, scId);
            i.putExtra(EXTRA_ACTIVITY_XML, xmlName);
            ctx.sendBroadcast(i);
        } catch (Exception ignored) {}
    }

    public static void broadcastLayoutChangedWithXml(Context ctx, String scId, String xmlName, String layoutXml) {
        try {
            Intent i = new Intent(ACTION_LAYOUT_CHANGED);
            i.putExtra(EXTRA_SC_ID, scId);
            i.putExtra(EXTRA_ACTIVITY_XML, xmlName);
            if (layoutXml != null && !layoutXml.isEmpty()) {
                i.putExtra("layout_xml", layoutXml);
            }
            ctx.sendBroadcast(i);
        } catch (Exception ignored) {}
    }

    public static void broadcastLiveReload(Context ctx, String scId, String xmlName) {
        try {
            Intent i = new Intent(ACTION_LIVE_LAYOUT_RELOAD);
            i.putExtra(EXTRA_SC_ID, scId);
            i.putExtra(EXTRA_ACTIVITY_XML, xmlName);
            ctx.sendBroadcast(i);
        } catch (Exception ignored) {}
    }

    public static void broadcastBoth(Context ctx, String scId, String xmlName) {
        broadcastLayoutChanged(ctx, scId, xmlName);
        broadcastLiveReload(ctx, scId, xmlName);
    }

    public static void broadcastBothWithXml(Context ctx, String scId, String xmlName, String layoutXml) {
        broadcastLayoutChangedWithXml(ctx, scId, xmlName, layoutXml);
        broadcastLiveReload(ctx, scId, xmlName);
    }

    public static int getViewTypeByTypeName(String typeName) {
        return ViewBean.getViewTypeByTypeName(typeName);
    }

    public static String getViewTypeName(int type) {
        return ViewBean.getViewTypeName(type);
    }

    public static ViewBean createDefaultViewBean(String id, int type, String parentId, int parentType) {
        ViewBean bean = new ViewBean(id, type);
        bean.parent = parentId;
        bean.parentType = parentType;
        boolean isRoot = "root".equals(parentId);
        bean.preId = id;
        bean.preIndex = isRoot ? -1 : 0;
        bean.preParent = isRoot ? "" : parentId;
        bean.preParentType = isRoot ? -1 : parentType;
        return bean;
    }

    public static JsonObject viewBeanToJsonObject(ViewBean bean) {
        return GSON.toJsonTree(bean).getAsJsonObject();
    }

    public static ViewBean jsonObjectToViewBean(JsonObject json) {
        try {
            return GSON.fromJson(json, ViewBean.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static String describeViewBean(ViewBean bean) {
        StringBuilder sb = new StringBuilder();
        sb.append("id=").append(bean.id);
        sb.append(" type=").append(getViewTypeName(bean.type)).append("(").append(bean.type).append(")");
        sb.append(" parent=").append(bean.parent != null ? bean.parent : "root");
        sb.append(" idx=").append(bean.index);
        if (bean.layout != null) {
            sb.append(" w=").append(dimStr(bean.layout.width));
            sb.append(" h=").append(dimStr(bean.layout.height));
            if (bean.layout.orientation >= 0) {
                sb.append(" ori=").append(bean.layout.orientation == 0 ? "H" : "V");
            }
            if (bean.layout.gravity != 0) sb.append(" grav=").append(bean.layout.gravity);
            if (bean.layout.weight != 0) sb.append(" wt=").append(bean.layout.weight);
        }
        if (bean.text != null && bean.text.text != null && !bean.text.text.isEmpty()) {
            String txt = bean.text.text;
            sb.append(" text=\"").append(txt.length() > 40 ? txt.substring(0, 40) + "..." : txt).append("\"");
        }
        if (bean.inject != null && !bean.inject.isEmpty()) {
            sb.append(" [+inject]");
        }
        return sb.toString();
    }

    public static String describeViewBeanList(ArrayList<ViewBean> beans) {
        if (beans == null || beans.isEmpty()) return "(empty layout)";
        StringBuilder sb = new StringBuilder();
        sb.append(beans.size()).append(" view(s):\n");
        for (ViewBean bean : beans) {
            sb.append("  ").append(describeViewBean(bean)).append("\n");
        }
        return sb.toString();
    }

    public static String dimStr(int val) {
        if (val == LayoutBean.LAYOUT_MATCH_PARENT) return "match";
        if (val == LayoutBean.LAYOUT_WRAP_CONTENT) return "wrap";
        if (val == LayoutBean.LAYOUT_NOTUSED) return "0";
        return val + "dp";
    }

    public static String buildViewTreeDescription(ArrayList<ViewBean> beans) {
        if (beans == null || beans.isEmpty()) return "(empty layout)";
        Map<String, ViewBean> idMap = new HashMap<>();
        for (ViewBean b : beans) {
            if (b.id != null) idMap.put(b.id, b);
        }
        StringBuilder sb = new StringBuilder();
        for (ViewBean b : beans) {
            if ("root".equals(b.parent)) {
                buildTreeLine(b, idMap, "", sb);
            }
        }
        List<ViewBean> orphans = new ArrayList<>();
        for (ViewBean b : beans) {
            if (!"root".equals(b.parent) && !idMap.containsKey(b.parent)) {
                orphans.add(b);
            }
        }
        if (!orphans.isEmpty()) {
            sb.append("\n[Orphaned views]\n");
            for (ViewBean b : orphans) {
                sb.append("  ").append(describeViewBean(b)).append("\n");
            }
        }
        return sb.toString();
    }

    private static void buildTreeLine(ViewBean bean, Map<String, ViewBean> idMap, String indent, StringBuilder sb) {
        sb.append(indent).append(describeViewBean(bean)).append("\n");
        List<ViewBean> children = new ArrayList<>();
        for (ViewBean b : idMap.values()) {
            if (bean.id.equals(b.parent)) children.add(b);
        }
        children.sort((a, b) -> Integer.compare(a.index, b.index));
        for (ViewBean child : children) {
            buildTreeLine(child, idMap, indent + "  ", sb);
        }
    }

    public static ViewBean findViewById(ArrayList<ViewBean> beans, String id) {
        if (beans == null || id == null) return null;
        for (ViewBean b : beans) {
            if (id.equals(b.id)) return b;
        }
        return null;
    }

    public static List<ViewBean> findChildren(ArrayList<ViewBean> beans, String parentId) {
        List<ViewBean> children = new ArrayList<>();
        if (beans == null || parentId == null) return children;
        for (ViewBean b : beans) {
            if (parentId.equals(b.parent)) children.add(b);
        }
        return children;
    }

    public static List<String> findDescendantIds(ArrayList<ViewBean> beans, String rootId) {
        List<String> ids = new ArrayList<>();
        ids.add(rootId);
        boolean added = true;
        while (added) {
            added = false;
            for (ViewBean b : beans) {
                if (ids.contains(b.parent) && !ids.contains(b.id)) {
                    ids.add(b.id);
                    added = true;
                }
            }
        }
        return ids;
    }

    public static boolean applyPropertyPatch(ViewBean bean, String property, JsonElement value) {
        if (bean == null || property == null) return false;
        if (property.contains(".")) {
            String[] parts = property.split("\\.", 2);
            String subObj = parts[0];
            String subKey = parts[1];
            switch (subObj) {
                case "layout":
                    return applyLayoutProperty(bean.layout, subKey, value);
                case "text":
                    return applyTextProperty(bean.text, subKey, value);
                case "image":
                    return applyImageProperty(bean.image, subKey, value);
                default:
                    return false;
            }
        } else {
            return applyDirectProperty(bean, property, value);
        }
    }

    private static boolean applyDirectProperty(ViewBean bean, String key, JsonElement value) {
        try {
            switch (key) {
                case "alpha": bean.alpha = value.getAsFloat(); return true;
                case "clickable": bean.clickable = value.getAsInt(); return true;
                case "enabled": bean.enabled = value.getAsInt(); return true;
                case "checked": bean.checked = value.getAsInt(); return true;
                case "inject": bean.inject = value.getAsString(); return true;
                case "convert": bean.convert = value.getAsString(); return true;
                case "customView": bean.customView = value.getAsString(); return true;
                case "scaleX": bean.scaleX = value.getAsFloat(); return true;
                case "scaleY": bean.scaleY = value.getAsFloat(); return true;
                case "translationX": bean.translationX = value.getAsFloat(); return true;
                case "translationY": bean.translationY = value.getAsFloat(); return true;
                case "progress": bean.progress = value.getAsInt(); return true;
                case "max": bean.max = value.getAsInt(); return true;
                case "spinnerMode": bean.spinnerMode = value.getAsInt(); return true;
                case "dividerHeight": bean.dividerHeight = value.getAsInt(); return true;
                case "choiceMode": bean.choiceMode = value.getAsInt(); return true;
                case "firstDayOfWeek": bean.firstDayOfWeek = value.getAsInt(); return true;
                case "adSize": bean.adSize = value.getAsString(); return true;
                case "adUnitId": bean.adUnitId = value.getAsString(); return true;
                case "indeterminate": bean.indeterminate = value.getAsString(); return true;
                case "progressStyle": bean.progressStyle = value.getAsString(); return true;
                default: return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean applyLayoutProperty(LayoutBean layout, String key, JsonElement value) {
        if (layout == null) return false;
        try {
            switch (key) {
                case "width": layout.width = value.getAsInt(); return true;
                case "height": layout.height = value.getAsInt(); return true;
                case "orientation": layout.orientation = value.getAsInt(); return true;
                case "gravity": layout.gravity = value.getAsInt(); return true;
                case "layoutGravity": layout.layoutGravity = value.getAsInt(); return true;
                case "paddingLeft": layout.paddingLeft = value.getAsInt(); return true;
                case "paddingRight": layout.paddingRight = value.getAsInt(); return true;
                case "paddingTop": layout.paddingTop = value.getAsInt(); return true;
                case "paddingBottom": layout.paddingBottom = value.getAsInt(); return true;
                case "marginLeft": layout.marginLeft = value.getAsInt(); return true;
                case "marginRight": layout.marginRight = value.getAsInt(); return true;
                case "marginTop": layout.marginTop = value.getAsInt(); return true;
                case "marginBottom": layout.marginBottom = value.getAsInt(); return true;
                case "weight": layout.weight = value.getAsInt(); return true;
                case "weightSum": layout.weightSum = value.getAsInt(); return true;
                case "backgroundColor": layout.backgroundColor = value.getAsInt(); layout.hasBackgroundColor = true; return true;
                case "borderColor": layout.borderColor = value.getAsInt(); return true;
                case "backgroundResource": layout.backgroundResource = value.getAsString(); return true;
                case "backgroundResColor": layout.backgroundResColor = value.getAsString(); return true;
                default: return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean applyTextProperty(TextBean text, String key, JsonElement value) {
        if (text == null) return false;
        try {
            switch (key) {
                case "text": text.text = value.getAsString(); return true;
                case "textSize": text.textSize = value.getAsInt(); return true;
                case "textColor": text.textColor = value.getAsInt(); text.hasTextColor = true; return true;
                case "textType": text.textType = value.getAsInt(); return true;
                case "textFont": text.textFont = value.getAsString(); return true;
                case "hint": text.hint = value.getAsString(); return true;
                case "hintColor": text.hintColor = value.getAsInt(); text.hasHintColor = true; return true;
                case "inputType": text.inputType = value.getAsInt(); return true;
                case "imeOption": text.imeOption = value.getAsInt(); return true;
                case "singleLine": text.singleLine = value.getAsInt(); return true;
                case "line": text.line = value.getAsInt(); return true;
                case "resTextColor": text.resTextColor = value.getAsString(); return true;
                case "resHintColor": text.resHintColor = value.getAsString(); return true;
                default: return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean applyImageProperty(ImageBean image, String key, JsonElement value) {
        if (image == null) return false;
        try {
            switch (key) {
                case "resName": image.resName = value.getAsString(); return true;
                case "scaleType": image.scaleType = value.getAsString(); return true;
                case "rotate": image.rotate = value.getAsInt(); return true;
                case "useCompatSrc": image.useCompatSrc = value.getAsBoolean(); return true;
                default: return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public static String buildTypeReference() {
        StringBuilder sb = new StringBuilder();
        sb.append("ViewBean type constants (use these integer values for 'type'):\n");
        sb.append("  0=LinearLayout, 1=RelativeLayout, 2=HorizontalScrollView,\n");
        sb.append("  3=Button, 4=TextView, 5=EditText, 6=ImageView, 7=WebView,\n");
        sb.append("  8=ProgressBar, 9=ListView, 10=Spinner, 11=CheckBox,\n");
        sb.append("  12=ScrollView(VScrollView), 13=Switch, 14=SeekBar,\n");
        sb.append("  15=CalendarView, 16=FAB, 17=AdView, 18=MapView,\n");
        sb.append("  19=RadioButton, 20=RatingBar, 21=VideoView, 22=SearchView,\n");
        sb.append("  23=AutoCompleteTextView, 24=MultiAutoCompleteTextView,\n");
        sb.append("  25=GridView, 30=TabLayout, 31=ViewPager,\n");
        sb.append("  32=BottomNavigationView, 36=CardView, 37=CollapsingToolbarLayout,\n");
        sb.append("  38=TextInputLayout, 39=SwipeRefreshLayout, 40=RadioGroup,\n");
        sb.append("  41=MaterialButton, 43=CircleImageView, 48=RecyclerView\n\n");
        sb.append("Layout dimension constants:\n");
        sb.append("  width/height: -1=MATCH_PARENT, -2=WRAP_CONTENT, N=dp value\n");
        sb.append("  orientation: 0=horizontal, 1=vertical, -1=none\n");
        sb.append("  gravity: 0=none, 17=center, 16=center_horizontal, 5=center_vertical, 48=top\n\n");
        sb.append("ViewBean format: {\"id\",\"type\",\"parent\",\"parentType\",\"index\",\n");
        sb.append("  \"layout\":{width,height,orientation,gravity,layoutGravity,marginLeft/Right/Top/Bottom,\n");
        sb.append("    paddingLeft/Right/Top/Bottom,weight,weightSum,backgroundColor,borderColor,\n");
        sb.append("    backgroundResource,backgroundResColor,hasBackgroundColor},\n");
        sb.append("  \"text\":{text,textSize,textColor,hasTextColor,textType,textFont,hint,hintColor,\n");
        sb.append("    hasHintColor,inputType,imeOption,singleLine,line,resTextColor,resHintColor},\n");
        sb.append("  \"image\":{resName,scaleType,rotate,useCompatSrc},\n");
        sb.append("  \"alpha\",\"clickable\",\"enabled\",\"checked\",\"inject\",\"convert\",\"customView\",\n");
        sb.append("  \"scaleX\",\"scaleY\",\"translationX\",\"translationY\",\"progress\",\"max\",\n");
        sb.append("  \"spinnerMode\",\"dividerHeight\",\"choiceMode\",\"firstDayOfWeek\",\n");
        sb.append("  \"adSize\",\"adUnitId\",\"indeterminate\",\"progressStyle\",\n");
        sb.append("  \"preId\",\"preIndex\",\"preParent\",\"preParentType\"}\n");
        sb.append("  Root views: parent=\"root\", preIndex=-1, preParent=\"\", preParentType=-1\n");
        sb.append("  Non-root: preId=id, preIndex=index, preParent=parent, preParentType=parentType\n\n");
        sb.append("CRITICAL RULES:\n");
        sb.append("  - type=2 is HorizontalScrollView, NOT TextView (type=4 is TextView)\n");
        sb.append("  - Every screen needs a _fab section (auto-created if omitted)\n");
        sb.append("  - In horizontal LinearLayout children that fill: width=0, weight=1\n");
        sb.append("  - Colors are ARGB signed ints: -1=white, -16777216=black, 0=transparent\n");
        return sb.toString();
    }
}