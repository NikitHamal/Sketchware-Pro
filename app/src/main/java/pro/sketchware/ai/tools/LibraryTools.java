package pro.sketchware.ai.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import pro.sketchware.ai.models.ToolResult;

public class LibraryTools {

    public static class ListLibrariesTool implements AgentTool {
        @Override
        public String getName() { return "list_libraries"; }

        @Override
        public String getDescription() {
            return "Lists all libraries used by a project, including built-in and local libraries.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            JsonObject scId = new JsonObject();
            scId.addProperty("type", "string");
            scId.addProperty("description", "The project SC ID");
            props.add("sc_id", scId);
            schema.add("properties", props);
            JsonArray req = new JsonArray();
            req.add("sc_id");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            String scId = arguments.has("sc_id") ? arguments.get("sc_id").getAsString() : null;
            if (scId == null || scId.isEmpty()) return new ToolResult("", false, null, "sc_id is required");
            if (!context.isProjectAllowed(scId)) return new ToolResult("", false, null, "Project not in workspace");

            JsonObject result = new JsonObject();
            File libraryFile = new File(context.getSketchwareDir(), "data/" + scId + "/library");
            if (libraryFile.exists()) {
                String content = readFile(libraryFile);
                if (content != null && !content.trim().isEmpty()) {
                    try { result.add("built_in_libraries", JsonParser.parseString(content)); }
                    catch (Exception e) { result.addProperty("built_in_libraries_raw", content); }
                }
            }

            File localLibsDir = new File(context.getSketchwareDir(), "data/" + scId + "/local_library");
            if (localLibsDir.exists() && localLibsDir.isDirectory()) {
                JsonArray localLibs = new JsonArray();
                File[] libDirs = localLibsDir.listFiles();
                if (libDirs != null) {
                    for (File libDir : libDirs) {
                        if (libDir.isDirectory()) {
                            JsonObject lib = new JsonObject();
                            lib.addProperty("name", libDir.getName());
                            localLibs.add(lib);
                        }
                    }
                }
                result.add("local_libraries", localLibs);
            }
            return new ToolResult("", true, result.toString(), null);
        }
    }

    public static class AddLibraryTool implements AgentTool {
        @Override
        public String getName() { return "add_library"; }

        @Override
        public String getDescription() {
            return "Adds or enables a built-in library for a project. Available: compat, material3, firebase, admob, googlemap.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            JsonObject scId = new JsonObject();
            scId.addProperty("type", "string");
            props.add("sc_id", scId);
            JsonObject libName = new JsonObject();
            libName.addProperty("type", "string");
            libName.addProperty("description", "Library name: compat, material3, firebase, admob, googlemap");
            props.add("library_name", libName);
            schema.add("properties", props);
            JsonArray req = new JsonArray();
            req.add("sc_id");
            req.add("library_name");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            String scId = arguments.has("sc_id") ? arguments.get("sc_id").getAsString() : null;
            String libName = arguments.has("library_name") ? arguments.get("library_name").getAsString() : null;
            if (scId == null) return new ToolResult("", false, null, "sc_id is required");
            if (libName == null) return new ToolResult("", false, null, "library_name is required");
            if (!context.isProjectAllowed(scId)) return new ToolResult("", false, null, "Project not in workspace");

            File libraryFile = new File(context.getSketchwareDir(), "data/" + scId + "/library");
            JsonArray libraries;
            if (libraryFile.exists()) {
                String content = readFile(libraryFile);
                try { libraries = (content != null && !content.isEmpty()) ? JsonParser.parseString(content).getAsJsonArray() : new JsonArray(); }
                catch (Exception e) { libraries = new JsonArray(); }
            } else {
                libraries = new JsonArray();
            }

            for (JsonElement el : libraries) {
                if (el.isJsonObject() && el.getAsJsonObject().has("name") &&
                    el.getAsJsonObject().get("name").getAsString().equals(libName)) {
                    el.getAsJsonObject().addProperty("useYn", "Y");
                    try { writeFile(libraryFile, libraries.toString()); return new ToolResult("", true, "Library " + libName + " enabled", null); }
                    catch (IOException e) { return new ToolResult("", false, null, "Write failed: " + e.getMessage()); }
                }
            }

            JsonObject newLib = new JsonObject();
            newLib.addProperty("adUnits", "");
            newLib.addProperty("data", "");
            newLib.addProperty("libType", 0);
            newLib.addProperty("name", libName);
            newLib.addProperty("reserved1", "");
            newLib.addProperty("reserved2", "");
            newLib.addProperty("reserved3", "");
            newLib.addProperty("testDevices", "");
            newLib.addProperty("useYn", "Y");
            libraries.add(newLib);

            try { writeFile(libraryFile, libraries.toString()); return new ToolResult("", true, "Library " + libName + " added", null); }
            catch (IOException e) { return new ToolResult("", false, null, "Write failed: " + e.getMessage()); }
        }
    }

    public static class RemoveLibraryTool implements AgentTool {
        @Override
        public String getName() { return "remove_library"; }

        @Override
        public String getDescription() { return "Disables a built-in library from a project."; }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            JsonObject scId = new JsonObject();
            scId.addProperty("type", "string");
            props.add("sc_id", scId);
            JsonObject libName = new JsonObject();
            libName.addProperty("type", "string");
            props.add("library_name", libName);
            schema.add("properties", props);
            JsonArray req = new JsonArray();
            req.add("sc_id");
            req.add("library_name");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            String scId = arguments.has("sc_id") ? arguments.get("sc_id").getAsString() : null;
            String libName = arguments.has("library_name") ? arguments.get("library_name").getAsString() : null;
            if (scId == null) return new ToolResult("", false, null, "sc_id is required");
            if (libName == null) return new ToolResult("", false, null, "library_name is required");
            if (!context.isProjectAllowed(scId)) return new ToolResult("", false, null, "Project not in workspace");

            File libraryFile = new File(context.getSketchwareDir(), "data/" + scId + "/library");
            if (!libraryFile.exists()) return new ToolResult("", true, "No libraries configured", null);

            String content = readFile(libraryFile);
            if (content == null || content.trim().isEmpty()) return new ToolResult("", true, "No libraries", null);

            try {
                JsonArray libraries = JsonParser.parseString(content).getAsJsonArray();
                for (JsonElement el : libraries) {
                    if (el.isJsonObject() && el.getAsJsonObject().has("name") &&
                        el.getAsJsonObject().get("name").getAsString().equals(libName)) {
                        el.getAsJsonObject().addProperty("useYn", "N");
                        writeFile(libraryFile, libraries.toString());
                        return new ToolResult("", true, "Library " + libName + " disabled", null);
                    }
                }
                return new ToolResult("", true, "Library " + libName + " not found", null);
            } catch (Exception e) {
                return new ToolResult("", false, null, "Failed: " + e.getMessage());
            }
        }
    }

    private static String readFile(File file) {
        if (!file.exists()) return null;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
            return sb.toString().trim();
        } catch (IOException e) { return null; }
    }

    private static void writeFile(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileWriter writer = new FileWriter(file)) { writer.write(content); }
    }
}
