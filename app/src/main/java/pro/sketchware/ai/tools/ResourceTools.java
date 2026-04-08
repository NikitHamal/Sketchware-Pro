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
 * Contains tools for managing string and color resources in Sketchware Pro projects.
 * Resources are stored in .sketchware/data/{sc_id}/resource as a JSON array.
 */
public final class ResourceTools {

    private ResourceTools() {
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

    private static JsonArray readResourceArray(File resourceFile) throws IOException, JsonSyntaxException {
        if (!resourceFile.exists()) {
            return new JsonArray();
        }
        String content = readFileContent(resourceFile);
        if (content.trim().isEmpty()) {
            return new JsonArray();
        }
        JsonElement element = JsonParser.parseString(content);
        return element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    /**
     * Adds a string resource to a project.
     */
    public static class AddStringResourceTool implements AgentTool {

        @Override
        public String getName() {
            return "add_string_resource";
        }

        @Override
        public String getDescription() {
            return "Adds a string resource (key-value pair) to a Sketchware Pro project. "
                    + "If a resource with the same key already exists, it will be updated.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID");
            properties.add("sc_id", scIdProp);

            JsonObject keyProp = new JsonObject();
            keyProp.addProperty("type", "string");
            keyProp.addProperty("description", "The string resource key (e.g., \"app_name\", \"welcome_message\")");
            properties.add("key", keyProp);

            JsonObject valueProp = new JsonObject();
            valueProp.addProperty("type", "string");
            valueProp.addProperty("description", "The string value");
            properties.add("value", valueProp);

            JsonArray required = new JsonArray();
            required.add("sc_id");
            required.add("key");
            required.add("value");

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
            if (!arguments.has("key") || arguments.get("key").isJsonNull()) {
                return error("Missing required parameter: key");
            }
            if (!arguments.has("value") || arguments.get("value").isJsonNull()) {
                return error("Missing required parameter: value");
            }

            String scId = arguments.get("sc_id").getAsString();
            String key = arguments.get("key").getAsString();
            String value = arguments.get("value").getAsString();

            if (!context.isProjectAllowed(scId)) {
                return error("Access denied: project " + scId + " is not in the current workspace");
            }

            File resourceFile = new File(context.getProjectDataDir(scId), "resource");

            try {
                JsonArray resources = readResourceArray(resourceFile);
                boolean updated = false;

                // Check if key already exists
                for (JsonElement element : resources) {
                    if (element.isJsonObject()) {
                        JsonObject res = element.getAsJsonObject();
                        if (res.has("resType") && "string".equals(res.get("resType").getAsString())
                                && res.has("resName") && res.get("resName").getAsString().equals(key)) {
                            res.addProperty("resValue", value);
                            updated = true;
                            break;
                        }
                    }
                }

                if (!updated) {
                    JsonObject newResource = new JsonObject();
                    newResource.addProperty("resType", "string");
                    newResource.addProperty("resName", key);
                    newResource.addProperty("resValue", value);
                    resources.add(newResource);
                }

                writeFileContent(resourceFile, resources.toString());

                JsonObject result = new JsonObject();
                result.addProperty("key", key);
                result.addProperty("value", value);
                result.addProperty("action", updated ? "updated" : "added");
                result.addProperty("message", "String resource " + (updated ? "updated" : "added") + " successfully");
                return success(result.toString());
            } catch (IOException e) {
                return error("Failed to add string resource: " + e.getMessage());
            } catch (JsonSyntaxException e) {
                return error("Resource file contains invalid JSON: " + e.getMessage());
            }
        }
    }

    /**
     * Adds a color resource to a project.
     */
    public static class AddColorResourceTool implements AgentTool {

        @Override
        public String getName() {
            return "add_color_resource";
        }

        @Override
        public String getDescription() {
            return "Adds a color resource to a Sketchware Pro project. "
                    + "The color value should be a hex color string (e.g., \"#FF5722\") or an integer color value. "
                    + "If a resource with the same key exists, it will be updated.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID");
            properties.add("sc_id", scIdProp);

            JsonObject keyProp = new JsonObject();
            keyProp.addProperty("type", "string");
            keyProp.addProperty("description", "The color resource key (e.g., \"primary_color\", \"accent_color\")");
            properties.add("key", keyProp);

            JsonObject valueProp = new JsonObject();
            valueProp.addProperty("type", "string");
            valueProp.addProperty("description", "The color value as hex string (e.g., \"#FF5722\") or integer");
            properties.add("value", valueProp);

            JsonArray required = new JsonArray();
            required.add("sc_id");
            required.add("key");
            required.add("value");

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
            if (!arguments.has("key") || arguments.get("key").isJsonNull()) {
                return error("Missing required parameter: key");
            }
            if (!arguments.has("value") || arguments.get("value").isJsonNull()) {
                return error("Missing required parameter: value");
            }

            String scId = arguments.get("sc_id").getAsString();
            String key = arguments.get("key").getAsString();
            String value = arguments.get("value").getAsString();

            if (!context.isProjectAllowed(scId)) {
                return error("Access denied: project " + scId + " is not in the current workspace");
            }

            File resourceFile = new File(context.getProjectDataDir(scId), "resource");

            try {
                JsonArray resources = readResourceArray(resourceFile);
                boolean updated = false;

                // Check if key already exists
                for (JsonElement element : resources) {
                    if (element.isJsonObject()) {
                        JsonObject res = element.getAsJsonObject();
                        if (res.has("resType") && "color".equals(res.get("resType").getAsString())
                                && res.has("resName") && res.get("resName").getAsString().equals(key)) {
                            res.addProperty("resValue", value);
                            updated = true;
                            break;
                        }
                    }
                }

                if (!updated) {
                    JsonObject newResource = new JsonObject();
                    newResource.addProperty("resType", "color");
                    newResource.addProperty("resName", key);
                    newResource.addProperty("resValue", value);
                    resources.add(newResource);
                }

                writeFileContent(resourceFile, resources.toString());

                JsonObject result = new JsonObject();
                result.addProperty("key", key);
                result.addProperty("value", value);
                result.addProperty("action", updated ? "updated" : "added");
                result.addProperty("message", "Color resource " + (updated ? "updated" : "added") + " successfully");
                return success(result.toString());
            } catch (IOException e) {
                return error("Failed to add color resource: " + e.getMessage());
            } catch (JsonSyntaxException e) {
                return error("Resource file contains invalid JSON: " + e.getMessage());
            }
        }
    }

    /**
     * Lists resources in a project, optionally filtered by type.
     */
    public static class ListResourcesTool implements AgentTool {

        @Override
        public String getName() {
            return "list_resources";
        }

        @Override
        public String getDescription() {
            return "Lists all resources in a Sketchware Pro project, optionally filtered by type "
                    + "(\"string\", \"color\"). Returns key-value pairs for each resource.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID");
            properties.add("sc_id", scIdProp);

            JsonObject typeProp = new JsonObject();
            typeProp.addProperty("type", "string");
            typeProp.addProperty("description", "Filter by resource type: \"string\", \"color\". If not specified, all resources are returned.");
            properties.add("resource_type", typeProp);

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
            String resourceType = arguments.has("resource_type") && !arguments.get("resource_type").isJsonNull()
                    ? arguments.get("resource_type").getAsString() : null;

            if (!context.isProjectAllowed(scId)) {
                return error("Access denied: project " + scId + " is not in the current workspace");
            }

            File resourceFile = new File(context.getProjectDataDir(scId), "resource");

            try {
                JsonArray resources = readResourceArray(resourceFile);
                JsonArray result = new JsonArray();

                for (JsonElement element : resources) {
                    if (!element.isJsonObject()) continue;
                    JsonObject res = element.getAsJsonObject();

                    if (resourceType != null) {
                        String type = res.has("resType") ? res.get("resType").getAsString() : "";
                        if (!type.equals(resourceType)) continue;
                    }

                    JsonObject entry = new JsonObject();
                    entry.addProperty("type",
                            res.has("resType") ? res.get("resType").getAsString() : "unknown");
                    entry.addProperty("key",
                            res.has("resName") ? res.get("resName").getAsString() : "");
                    entry.addProperty("value",
                            res.has("resValue") ? res.get("resValue").getAsString() : "");
                    result.add(entry);
                }

                return success(result.toString());
            } catch (IOException e) {
                return error("Failed to list resources: " + e.getMessage());
            } catch (JsonSyntaxException e) {
                return error("Resource file contains invalid JSON: " + e.getMessage());
            }
        }
    }
}
