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
import java.util.Iterator;

import pro.sketchware.ai.models.ToolResult;

/**
 * Contains tools for managing view layouts within Sketchware Pro projects.
 * Layouts are stored in .sketchware/data/{sc_id}/view as a JSON array where
 * each entry represents an activity's view hierarchy.
 */
public final class LayoutTools {

    private LayoutTools() {
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

    private static JsonArray readViewArray(File viewFile) throws IOException, JsonSyntaxException {
        if (!viewFile.exists()) {
            return new JsonArray();
        }
        String content = readFileContent(viewFile);
        if (content.trim().isEmpty()) {
            return new JsonArray();
        }
        JsonElement element = JsonParser.parseString(content);
        return element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    /**
     * Finds the view entry for a given activity name within the view array.
     */
    private static JsonObject findViewEntry(JsonArray viewArray, String activityName) {
        String viewId = activityName + ".xml";
        for (JsonElement element : viewArray) {
            if (element.isJsonObject()) {
                JsonObject entry = element.getAsJsonObject();
                if (entry.has("id") && entry.get("id").getAsString().equals(viewId)) {
                    return entry;
                }
            }
        }
        return null;
    }

    /**
     * Gets a layout's view hierarchy.
     */
    public static class GetLayoutTool implements AgentTool {

        @Override
        public String getName() {
            return "get_layout";
        }

        @Override
        public String getDescription() {
            return "Gets the view hierarchy (layout) of an activity in a Sketchware Pro project. "
                    + "Returns the root view and all child views as a JSON structure.";
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
            nameProp.addProperty("description", "Activity name (e.g., \"main\")");
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

            File viewFile = new File(context.getProjectDataDir(scId), "view");

            try {
                JsonArray viewArray = readViewArray(viewFile);
                JsonObject viewEntry = findViewEntry(viewArray, activityName);

                if (viewEntry == null) {
                    return error("Layout not found for activity: " + activityName);
                }

                return success(viewEntry.toString());
            } catch (IOException e) {
                return error("Failed to read layout: " + e.getMessage());
            } catch (JsonSyntaxException e) {
                return error("View file contains invalid JSON: " + e.getMessage());
            }
        }
    }

    /**
     * Edits a layout by performing view operations (add, remove, set property).
     */
    public static class EditLayoutTool implements AgentTool {

        @Override
        public String getName() {
            return "edit_layout";
        }

        @Override
        public String getDescription() {
            return "Edits the view layout of an activity by performing operations like "
                    + "adding views, removing views, or setting view properties. "
                    + "Each operation is an object with a 'type' field: "
                    + "'add_view' (adds a child view), "
                    + "'remove_view' (removes a view by ID), "
                    + "'set_property' (sets a property on a view).";
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
            nameProp.addProperty("description", "Activity name (e.g., \"main\")");
            properties.add("activity_name", nameProp);

            JsonObject opsProp = new JsonObject();
            opsProp.addProperty("type", "array");
            opsProp.addProperty("description",
                    "Array of operations. Each operation has a 'type' field: "
                            + "'add_view' requires 'parent_id', 'view' (full view object); "
                            + "'remove_view' requires 'view_id'; "
                            + "'set_property' requires 'view_id', 'property', 'value'.");

            JsonObject itemSchema = new JsonObject();
            itemSchema.addProperty("type", "object");
            opsProp.add("items", itemSchema);

            properties.add("view_operations", opsProp);

            JsonArray required = new JsonArray();
            required.add("sc_id");
            required.add("activity_name");
            required.add("view_operations");

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
            if (!arguments.has("view_operations") || !arguments.get("view_operations").isJsonArray()) {
                return error("Missing required parameter: view_operations (must be an array)");
            }

            String scId = arguments.get("sc_id").getAsString();
            String activityName = arguments.get("activity_name").getAsString();
            JsonArray operations = arguments.getAsJsonArray("view_operations");

            if (!context.isProjectAllowed(scId)) {
                return error("Access denied: project " + scId + " is not in the current workspace");
            }

            File viewFile = new File(context.getProjectDataDir(scId), "view");

            try {
                JsonArray viewArray = readViewArray(viewFile);
                JsonObject viewEntry = findViewEntry(viewArray, activityName);

                if (viewEntry == null) {
                    return error("Layout not found for activity: " + activityName);
                }

                JsonArray resultsArray = new JsonArray();

                for (JsonElement opElement : operations) {
                    if (!opElement.isJsonObject()) {
                        JsonObject opResult = new JsonObject();
                        opResult.addProperty("success", false);
                        opResult.addProperty("error", "Operation must be a JSON object");
                        resultsArray.add(opResult);
                        continue;
                    }

                    JsonObject operation = opElement.getAsJsonObject();
                    if (!operation.has("type")) {
                        JsonObject opResult = new JsonObject();
                        opResult.addProperty("success", false);
                        opResult.addProperty("error", "Operation missing 'type' field");
                        resultsArray.add(opResult);
                        continue;
                    }

                    String opType = operation.get("type").getAsString();
                    JsonObject opResult = new JsonObject();

                    switch (opType) {
                        case "add_view":
                            opResult = handleAddView(viewEntry, operation);
                            break;
                        case "remove_view":
                            opResult = handleRemoveView(viewEntry, operation);
                            break;
                        case "set_property":
                            opResult = handleSetProperty(viewEntry, operation);
                            break;
                        default:
                            opResult.addProperty("success", false);
                            opResult.addProperty("error", "Unknown operation type: " + opType);
                            break;
                    }
                    resultsArray.add(opResult);
                }

                // Write back the modified view array
                writeFileContent(viewFile, viewArray.toString());

                JsonObject result = new JsonObject();
                result.addProperty("activity_name", activityName);
                result.add("operation_results", resultsArray);
                result.addProperty("message", "Layout operations completed");
                return success(result.toString());
            } catch (IOException e) {
                return error("Failed to edit layout: " + e.getMessage());
            } catch (JsonSyntaxException e) {
                return error("View file contains invalid JSON: " + e.getMessage());
            }
        }

        private JsonObject handleAddView(JsonObject viewEntry, JsonObject operation) {
            JsonObject result = new JsonObject();

            if (!operation.has("view") || !operation.get("view").isJsonObject()) {
                result.addProperty("success", false);
                result.addProperty("error", "add_view requires a 'view' object");
                return result;
            }

            JsonObject newView = operation.getAsJsonObject("view");

            // Add to children array
            if (!viewEntry.has("children")) {
                viewEntry.add("children", new JsonArray());
            }
            JsonArray children = viewEntry.getAsJsonArray("children");
            children.add(newView);

            String viewId = newView.has("id") ? newView.get("id").getAsString() : "unknown";
            result.addProperty("success", true);
            result.addProperty("message", "View added: " + viewId);
            return result;
        }

        private JsonObject handleRemoveView(JsonObject viewEntry, JsonObject operation) {
            JsonObject result = new JsonObject();

            if (!operation.has("view_id")) {
                result.addProperty("success", false);
                result.addProperty("error", "remove_view requires a 'view_id'");
                return result;
            }

            String viewId = operation.get("view_id").getAsString();

            if (!viewEntry.has("children") || !viewEntry.get("children").isJsonArray()) {
                result.addProperty("success", false);
                result.addProperty("error", "No children array in layout");
                return result;
            }

            JsonArray children = viewEntry.getAsJsonArray("children");
            boolean removed = false;

            Iterator<JsonElement> iterator = children.iterator();
            while (iterator.hasNext()) {
                JsonElement child = iterator.next();
                if (child.isJsonObject()) {
                    JsonObject childObj = child.getAsJsonObject();
                    if (childObj.has("id") && childObj.get("id").getAsString().equals(viewId)) {
                        iterator.remove();
                        removed = true;
                        break;
                    }
                }
            }

            if (removed) {
                result.addProperty("success", true);
                result.addProperty("message", "View removed: " + viewId);
            } else {
                result.addProperty("success", false);
                result.addProperty("error", "View not found: " + viewId);
            }
            return result;
        }

        private JsonObject handleSetProperty(JsonObject viewEntry, JsonObject operation) {
            JsonObject result = new JsonObject();

            if (!operation.has("view_id") || !operation.has("property") || !operation.has("value")) {
                result.addProperty("success", false);
                result.addProperty("error", "set_property requires 'view_id', 'property', and 'value'");
                return result;
            }

            String viewId = operation.get("view_id").getAsString();
            String property = operation.get("property").getAsString();
            JsonElement value = operation.get("value");

            // Check if it's the root view
            if (viewEntry.has("root") && viewEntry.get("root").isJsonObject()) {
                JsonObject root = viewEntry.getAsJsonObject("root");
                if (root.has("id") && root.get("id").getAsString().equals(viewId)) {
                    root.add(property, value);
                    result.addProperty("success", true);
                    result.addProperty("message", "Property set on root view: " + property);
                    return result;
                }
            }

            // Search in children
            if (viewEntry.has("children") && viewEntry.get("children").isJsonArray()) {
                JsonArray children = viewEntry.getAsJsonArray("children");
                for (JsonElement child : children) {
                    if (child.isJsonObject()) {
                        JsonObject childObj = child.getAsJsonObject();
                        if (childObj.has("id") && childObj.get("id").getAsString().equals(viewId)) {
                            childObj.add(property, value);
                            result.addProperty("success", true);
                            result.addProperty("message", "Property set on view " + viewId + ": " + property);
                            return result;
                        }
                    }
                }
            }

            result.addProperty("success", false);
            result.addProperty("error", "View not found: " + viewId);
            return result;
        }
    }
}
