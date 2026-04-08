package pro.sketchware.ai.tools;

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

import pro.sketchware.ai.models.ToolResult;

/**
 * Contains tools for managing activities within Sketchware Pro projects.
 * Activities are stored in .sketchware/data/{sc_id}/file as a JSON array.
 */
public final class ActivityTools {

    private ActivityTools() {
    }

    private static ToolResult success(String output) {
        return new ToolResult(null, true, output, null);
    }

    private static ToolResult error(String message) {
        return new ToolResult(null, false, null, message);
    }

    private static String readFileContent(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
        }
        return sb.toString();
    }

    private static void writeFileContent(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    private static JsonArray readActivityArray(File fileFile) throws IOException, JsonSyntaxException {
        if (!fileFile.exists()) {
            return new JsonArray();
        }
        String content = readFileContent(fileFile);
        if (content.trim().isEmpty()) {
            return new JsonArray();
        }
        JsonElement element = JsonParser.parseString(content);
        if (element.isJsonArray()) {
            return element.getAsJsonArray();
        }
        return new JsonArray();
    }

    /**
     * Calculates the options integer from boolean flags.
     * Options is a bitmask: bit 0 = has toolbar, bit 1 = has FAB, bit 2 = has drawer
     */
    private static int calculateOptions(boolean hasToolbar, boolean hasFab, boolean hasDrawer) {
        int options = 0;
        if (hasToolbar) options |= 1;
        if (hasFab) options |= 2;
        if (hasDrawer) options |= 4;
        return options;
    }

    /**
     * Lists all activities in a project.
     */
    public static class ListActivitiesTool implements AgentTool {

        @Override
        public String getName() {
            return "list_activities";
        }

        @Override
        public String getDescription() {
            return "Lists all activities defined in a Sketchware Pro project, including their "
                    + "file name, type, orientation, keyboard setting, and options.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID");
            properties.add("sc_id", scIdProp);

            JsonArray required = new JsonArray();
            required.add("sc_id");

            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", properties);
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            if (!arguments.has("sc_id") || arguments.get("sc_id").isJsonNull()) {
                return error("Missing required parameter: sc_id");
            }

            String scId = arguments.get("sc_id").getAsString();

            if (!context.isProjectAllowed(scId)) {
                return error("Access denied: project " + scId + " is not in the current workspace");
            }

            File fileFile = new File(context.getProjectDataDir(scId), "file");

            try {
                JsonArray activities = readActivityArray(fileFile);

                JsonArray result = new JsonArray();
                for (JsonElement element : activities) {
                    if (!element.isJsonObject()) continue;
                    JsonObject activity = element.getAsJsonObject();

                    JsonObject entry = new JsonObject();
                    entry.addProperty("fileName",
                            activity.has("fileName") ? activity.get("fileName").getAsString() : "");
                    entry.addProperty("fileType",
                            activity.has("fileType") ? activity.get("fileType").getAsInt() : 0);
                    entry.addProperty("keyboardSetting",
                            activity.has("keyboardSetting") ? activity.get("keyboardSetting").getAsInt() : 0);
                    entry.addProperty("orientation",
                            activity.has("orientation") ? activity.get("orientation").getAsInt() : 0);
                    entry.addProperty("options",
                            activity.has("options") ? activity.get("options").getAsInt() : 0);

                    // Decode file type for readability
                    int fileType = entry.get("fileType").getAsInt();
                    String typeName;
                    switch (fileType) {
                        case 1:
                            typeName = "custom_view";
                            break;
                        case 2:
                            typeName = "drawer";
                            break;
                        default:
                            typeName = "activity";
                            break;
                    }
                    entry.addProperty("fileTypeName", typeName);

                    result.add(entry);
                }

                return success(result.toString());
            } catch (IOException e) {
                return error("Failed to read activities: " + e.getMessage());
            } catch (JsonSyntaxException e) {
                return error("Activities file contains invalid JSON: " + e.getMessage());
            }
        }
    }

    /**
     * Creates a new activity in a project.
     */
    public static class CreateActivityTool implements AgentTool {

        @Override
        public String getName() {
            return "create_activity";
        }

        @Override
        public String getDescription() {
            return "Creates a new activity in a Sketchware Pro project. Also creates the "
                    + "corresponding logic, view, and event data files for the activity.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID");
            properties.add("sc_id", scIdProp);

            JsonObject nameProp = new JsonObject();
            nameProp.addProperty("type", "string");
            nameProp.addProperty("description", "Activity name (e.g., \"main\", \"settings\", \"about\")");
            properties.add("activity_name", nameProp);

            JsonObject orientProp = new JsonObject();
            orientProp.addProperty("type", "integer");
            orientProp.addProperty("description", "Screen orientation: 0=both, 1=portrait, 2=landscape (default: 0)");
            properties.add("orientation", orientProp);

            JsonObject kbProp = new JsonObject();
            kbProp.addProperty("type", "integer");
            kbProp.addProperty("description", "Keyboard setting: 0=unspecified, 1=hidden, 2=visible (default: 0)");
            properties.add("keyboard_setting", kbProp);

            JsonObject toolbarProp = new JsonObject();
            toolbarProp.addProperty("type", "boolean");
            toolbarProp.addProperty("description", "Whether the activity has a toolbar (default: false)");
            properties.add("has_toolbar", toolbarProp);

            JsonObject fabProp = new JsonObject();
            fabProp.addProperty("type", "boolean");
            fabProp.addProperty("description", "Whether the activity has a floating action button (default: false)");
            properties.add("has_fab", fabProp);

            JsonObject drawerProp = new JsonObject();
            drawerProp.addProperty("type", "boolean");
            drawerProp.addProperty("description", "Whether the activity has a navigation drawer (default: false)");
            properties.add("has_drawer", drawerProp);

            JsonArray required = new JsonArray();
            required.add("sc_id");
            required.add("activity_name");

            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", properties);
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            if (!arguments.has("sc_id") || arguments.get("sc_id").isJsonNull()) {
                return error("Missing required parameter: sc_id");
            }
            if (!arguments.has("activity_name") || arguments.get("activity_name").isJsonNull()) {
                return error("Missing required parameter: activity_name");
            }

            String scId = arguments.get("sc_id").getAsString();
            String activityName = arguments.get("activity_name").getAsString();

            if (!context.isProjectAllowed(scId)) {
                return error("Access denied: project " + scId + " is not in the current workspace");
            }

            // Validate activity name
            if (!activityName.matches("[a-zA-Z][a-zA-Z0-9_]*")) {
                return error("Invalid activity name: must start with a letter and contain only letters, digits, and underscores");
            }

            int orientation = arguments.has("orientation") && !arguments.get("orientation").isJsonNull()
                    ? arguments.get("orientation").getAsInt() : 0;
            int keyboardSetting = arguments.has("keyboard_setting") && !arguments.get("keyboard_setting").isJsonNull()
                    ? arguments.get("keyboard_setting").getAsInt() : 0;
            boolean hasToolbar = arguments.has("has_toolbar") && !arguments.get("has_toolbar").isJsonNull()
                    && arguments.get("has_toolbar").getAsBoolean();
            boolean hasFab = arguments.has("has_fab") && !arguments.get("has_fab").isJsonNull()
                    && arguments.get("has_fab").getAsBoolean();
            boolean hasDrawer = arguments.has("has_drawer") && !arguments.get("has_drawer").isJsonNull()
                    && arguments.get("has_drawer").getAsBoolean();

            File dataDir = context.getProjectDataDir(scId);
            File fileFile = new File(dataDir, "file");

            try {
                // Read existing activities
                JsonArray activities = readActivityArray(fileFile);

                // Check for duplicate
                for (JsonElement element : activities) {
                    if (element.isJsonObject()) {
                        JsonObject existing = element.getAsJsonObject();
                        if (existing.has("fileName")
                                && existing.get("fileName").getAsString().equals(activityName)) {
                            return error("Activity already exists: " + activityName);
                        }
                    }
                }

                // Create new activity entry
                JsonObject newActivity = new JsonObject();
                newActivity.addProperty("fileName", activityName);
                newActivity.addProperty("fileType", 0);
                newActivity.addProperty("keyboardSetting", keyboardSetting);
                newActivity.addProperty("orientation", orientation);
                newActivity.addProperty("options", calculateOptions(hasToolbar, hasFab, hasDrawer));
                activities.add(newActivity);

                // Write updated file
                writeFileContent(fileFile, activities.toString());

                // Create corresponding logic file entry if logic file exists
                File logicFile = new File(dataDir, "logic");
                JsonArray logicArray;
                if (logicFile.exists()) {
                    String logicContent = readFileContent(logicFile);
                    if (!logicContent.trim().isEmpty()) {
                        logicArray = JsonParser.parseString(logicContent).getAsJsonArray();
                    } else {
                        logicArray = new JsonArray();
                    }
                } else {
                    logicArray = new JsonArray();
                }
                // Add empty logic entry for the new activity
                JsonObject logicEntry = new JsonObject();
                logicEntry.addProperty("name", activityName + ".java_onCreate_initializeLogic");
                logicEntry.add("blocks", new JsonArray());
                logicArray.add(logicEntry);
                writeFileContent(logicFile, logicArray.toString());

                // Create corresponding view file entry
                File viewFile = new File(dataDir, "view");
                JsonArray viewArray;
                if (viewFile.exists()) {
                    String viewContent = readFileContent(viewFile);
                    if (!viewContent.trim().isEmpty()) {
                        viewArray = JsonParser.parseString(viewContent).getAsJsonArray();
                    } else {
                        viewArray = new JsonArray();
                    }
                } else {
                    viewArray = new JsonArray();
                }
                // Add empty view entry for the new activity
                JsonObject viewEntry = new JsonObject();
                viewEntry.addProperty("id", activityName + ".xml");
                JsonObject rootView = new JsonObject();
                rootView.addProperty("adSize", "");
                rootView.addProperty("adUnitId", "");
                rootView.addProperty("alpha", 1.0f);
                rootView.addProperty("checked", 0);
                rootView.addProperty("choiceMode", 0);
                rootView.addProperty("clickable", 0);
                rootView.addProperty("customView", "");
                rootView.addProperty("dividerHeight", 0);
                rootView.addProperty("enabled", 1);
                rootView.addProperty("firstDayOfWeek", 1);
                rootView.addProperty("gravity", 0);
                rootView.addProperty("id", "root");
                rootView.addProperty("image.rotate", 0);
                rootView.addProperty("image.scaleType", "CENTER");
                rootView.addProperty("indeterminate", "false");
                rootView.addProperty("layout.backgroundColor", -1);
                rootView.addProperty("layout.gravity", 0);
                rootView.addProperty("layout.height", -2);
                rootView.addProperty("layout.marginBottom", 0);
                rootView.addProperty("layout.marginLeft", 0);
                rootView.addProperty("layout.marginRight", 0);
                rootView.addProperty("layout.marginTop", 0);
                rootView.addProperty("layout.orientation", 1);
                rootView.addProperty("layout.paddingBottom", 8);
                rootView.addProperty("layout.paddingLeft", 8);
                rootView.addProperty("layout.paddingRight", 8);
                rootView.addProperty("layout.paddingTop", 8);
                rootView.addProperty("layout.weight", 0);
                rootView.addProperty("layout.weightSum", 0);
                rootView.addProperty("layout.width", -1);
                rootView.addProperty("max", 100);
                rootView.addProperty("padding", 8);
                rootView.addProperty("parentType", 0);
                rootView.addProperty("preId", "");
                rootView.addProperty("preParent", "");
                rootView.addProperty("preParentType", 0);
                rootView.addProperty("progress", 0);
                rootView.addProperty("progressStyle", "?android:progressBarStyle");
                rootView.addProperty("scaleX", 1.0f);
                rootView.addProperty("scaleY", 1.0f);
                rootView.addProperty("spinnerMode", 1);
                rootView.addProperty("text.color", -16777216);
                rootView.addProperty("text.font", "default_font");
                rootView.addProperty("text.hint", "");
                rootView.addProperty("text.hintColor", -10453621);
                rootView.addProperty("text.imeOption", 0);
                rootView.addProperty("text.inputType", 0);
                rootView.addProperty("text.line", 0);
                rootView.addProperty("text.singleLine", 0);
                rootView.addProperty("text.text", "");
                rootView.addProperty("text.textSize", 12);
                rootView.addProperty("text.textType", 0);
                rootView.addProperty("translationX", 0);
                rootView.addProperty("translationY", 0);
                rootView.addProperty("type", 0);
                viewEntry.add("root", rootView);
                viewEntry.add("children", new JsonArray());
                viewArray.add(viewEntry);
                writeFileContent(viewFile, viewArray.toString());

                // Create event file entry
                File eventFile = new File(dataDir, "event");
                JsonArray eventArray;
                if (eventFile.exists()) {
                    String eventContent = readFileContent(eventFile);
                    if (!eventContent.trim().isEmpty()) {
                        eventArray = JsonParser.parseString(eventContent).getAsJsonArray();
                    } else {
                        eventArray = new JsonArray();
                    }
                } else {
                    eventArray = new JsonArray();
                }
                writeFileContent(eventFile, eventArray.toString());

                JsonObject result = new JsonObject();
                result.addProperty("activity_name", activityName);
                result.addProperty("message", "Activity created successfully");
                return success(result.toString());
            } catch (IOException e) {
                return error("Failed to create activity: " + e.getMessage());
            } catch (JsonSyntaxException e) {
                return error("Invalid JSON in project data: " + e.getMessage());
            }
        }
    }

    /**
     * Deletes an activity from a project.
     */
    public static class DeleteActivityTool implements AgentTool {

        @Override
        public String getName() {
            return "delete_activity";
        }

        @Override
        public String getDescription() {
            return "Deletes an activity from a Sketchware Pro project, removing its entry from "
                    + "the file list and associated logic/view/event data.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID");
            properties.add("sc_id", scIdProp);

            JsonObject nameProp = new JsonObject();
            nameProp.addProperty("type", "string");
            nameProp.addProperty("description", "Name of the activity to delete");
            properties.add("activity_name", nameProp);

            JsonArray required = new JsonArray();
            required.add("sc_id");
            required.add("activity_name");

            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", properties);
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            if (!arguments.has("sc_id") || arguments.get("sc_id").isJsonNull()) {
                return error("Missing required parameter: sc_id");
            }
            if (!arguments.has("activity_name") || arguments.get("activity_name").isJsonNull()) {
                return error("Missing required parameter: activity_name");
            }

            String scId = arguments.get("sc_id").getAsString();
            String activityName = arguments.get("activity_name").getAsString();

            if (!context.isProjectAllowed(scId)) {
                return error("Access denied: project " + scId + " is not in the current workspace");
            }

            File dataDir = context.getProjectDataDir(scId);
            File fileFile = new File(dataDir, "file");

            try {
                JsonArray activities = readActivityArray(fileFile);
                boolean found = false;
                JsonArray updated = new JsonArray();

                for (JsonElement element : activities) {
                    if (element.isJsonObject()) {
                        JsonObject activity = element.getAsJsonObject();
                        if (activity.has("fileName")
                                && activity.get("fileName").getAsString().equals(activityName)) {
                            found = true;
                            continue; // Skip this entry to remove it
                        }
                    }
                    updated.add(element);
                }

                if (!found) {
                    return error("Activity not found: " + activityName);
                }

                writeFileContent(fileFile, updated.toString());

                // Remove associated logic entries
                File logicFile = new File(dataDir, "logic");
                if (logicFile.exists()) {
                    try {
                        String logicContent = readFileContent(logicFile);
                        if (!logicContent.trim().isEmpty()) {
                            JsonArray logicArray = JsonParser.parseString(logicContent).getAsJsonArray();
                            JsonArray updatedLogic = new JsonArray();
                            String prefix = activityName + ".java_";
                            for (JsonElement element : logicArray) {
                                if (element.isJsonObject()) {
                                    JsonObject logicEntry = element.getAsJsonObject();
                                    if (logicEntry.has("name")
                                            && logicEntry.get("name").getAsString().startsWith(prefix)) {
                                        continue;
                                    }
                                }
                                updatedLogic.add(element);
                            }
                            writeFileContent(logicFile, updatedLogic.toString());
                        }
                    } catch (JsonSyntaxException ignored) {
                    }
                }

                // Remove associated view entries
                File viewFile = new File(dataDir, "view");
                if (viewFile.exists()) {
                    try {
                        String viewContent = readFileContent(viewFile);
                        if (!viewContent.trim().isEmpty()) {
                            JsonArray viewArray = JsonParser.parseString(viewContent).getAsJsonArray();
                            JsonArray updatedView = new JsonArray();
                            String viewId = activityName + ".xml";
                            for (JsonElement element : viewArray) {
                                if (element.isJsonObject()) {
                                    JsonObject viewEntry = element.getAsJsonObject();
                                    if (viewEntry.has("id")
                                            && viewEntry.get("id").getAsString().equals(viewId)) {
                                        continue;
                                    }
                                }
                                updatedView.add(element);
                            }
                            writeFileContent(viewFile, updatedView.toString());
                        }
                    } catch (JsonSyntaxException ignored) {
                    }
                }

                // Remove associated event entries
                File eventFile = new File(dataDir, "event");
                if (eventFile.exists()) {
                    try {
                        String eventContent = readFileContent(eventFile);
                        if (!eventContent.trim().isEmpty()) {
                            JsonArray eventArray = JsonParser.parseString(eventContent).getAsJsonArray();
                            JsonArray updatedEvents = new JsonArray();
                            String eventPrefix = activityName + "_";
                            for (JsonElement element : eventArray) {
                                if (element.isJsonObject()) {
                                    JsonObject eventEntry = element.getAsJsonObject();
                                    if (eventEntry.has("name")
                                            && eventEntry.get("name").getAsString().startsWith(eventPrefix)) {
                                        continue;
                                    }
                                }
                                updatedEvents.add(element);
                            }
                            writeFileContent(eventFile, updatedEvents.toString());
                        }
                    } catch (JsonSyntaxException ignored) {
                    }
                }

                JsonObject result = new JsonObject();
                result.addProperty("activity_name", activityName);
                result.addProperty("message", "Activity deleted successfully");
                return success(result.toString());
            } catch (IOException e) {
                return error("Failed to delete activity: " + e.getMessage());
            } catch (JsonSyntaxException e) {
                return error("Invalid JSON in activity file: " + e.getMessage());
            }
        }
    }
}
