package pro.sketchware.ai.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import dev.aldi.sayuti.editor.manage.LocalLibrary;
import mod.hey.studios.build.BuildSettings;
import mod.jbk.build.BuiltInLibraries;
import mod.jbk.editor.manage.library.EnableBuiltInLibrariesActivity;
import mod.jbk.editor.manage.library.ExcludeBuiltInLibrariesActivity;
import mod.pranav.dependency.resolver.DependencyResolver;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.util.library.BuiltInLibraryCompatibilityMatrix;

public class LibraryTools {

    private static final Gson GSON = new Gson();

    private static ToolResult success(String output) {
        return ToolResult.success(null, output);
    }

    private static ToolResult error(String message) {
        return ToolResult.failure(null, message);
    }

    private static ToolResult validateProject(String scId, ToolContext context) {
        if (scId == null || scId.isEmpty()) {
            return error("sc_id is required");
        }
        if (!context.isProjectAllowed(scId)) {
            return error("Project not in workspace");
        }
        return null;
    }

    private static String readFile(File file) {
        if (!file.exists()) return null;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
            return sb.toString().trim();
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeFile(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    @SuppressWarnings("unchecked")
    private static ArrayList<HashMap<String, Object>> getAttachedLocalLibraries(String scId) {
        return LocalLibrariesUtil.getLocalLibraries(scId);
    }

    private static void saveAttachedLocalLibraries(String scId, ArrayList<HashMap<String, Object>> libs) {
        LocalLibrariesUtil.rewriteLocalLibFile(scId, GSON.toJson(libs));
    }

    private static boolean hasAttachedLibrary(ArrayList<HashMap<String, Object>> libs, String name) {
        for (HashMap<String, Object> entry : libs) {
            Object value = entry.get("name");
            if (value != null && name.equals(value.toString())) {
                return true;
            }
        }
        return false;
    }

    private static JsonObject buildLocalLibraryEntry(HashMap<String, Object> entry) {
        JsonObject object = new JsonObject();
        for (Map.Entry<String, Object> item : entry.entrySet()) {
            if (item.getValue() != null) {
                object.addProperty(item.getKey(), item.getValue().toString());
            }
        }
        return object;
    }

    public static class ListLibrariesTool implements AgentTool {
        @Override
        public String getName() {
            return "list_libraries";
        }

        @Override
        public String getDescription() {
            return "Lists built-in libraries, attached local libraries, and downloaded local libraries available to a project.";
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
            ToolResult validation = validateProject(scId, context);
            if (validation != null) return validation;

            JsonObject result = new JsonObject();
            result.addProperty("sc_id", scId);

            File libraryFile = new File(context.getSketchwareDir(), "data/" + scId + "/library");
            if (libraryFile.exists()) {
                String content = readFile(libraryFile);
                if (content != null && !content.trim().isEmpty()) {
                    try {
                        result.add("built_in_libraries", JsonParser.parseString(content));
                    } catch (Exception e) {
                        result.addProperty("built_in_libraries_raw", content);
                    }
                }
            }

            JsonArray manuallyEnabledBuiltIns = new JsonArray();
            for (BuiltInLibraries.BuiltInLibrary library : EnableBuiltInLibrariesActivity.getEnabledLibraries(scId)) {
                manuallyEnabledBuiltIns.add(library.getName());
            }
            result.add("manually_enabled_built_in_libraries", manuallyEnabledBuiltIns);

            JsonArray excludedBuiltIns = new JsonArray();
            for (BuiltInLibraries.BuiltInLibrary library : ExcludeBuiltInLibrariesActivity.getExcludedLibraries(scId)) {
                excludedBuiltIns.add(library.getName());
            }
            result.add("excluded_built_in_libraries", excludedBuiltIns);

            JsonArray attachedLocalLibraries = new JsonArray();
            for (HashMap<String, Object> entry : getAttachedLocalLibraries(scId)) {
                attachedLocalLibraries.add(buildLocalLibraryEntry(entry));
            }
            result.add("attached_local_libraries", attachedLocalLibraries);

            JsonArray downloaded = new JsonArray();
            for (LocalLibrary library : LocalLibrariesUtil.getAllLocalLibraries()) {
                JsonObject item = new JsonObject();
                item.addProperty("name", library.getName());
                item.addProperty("size", library.getSize());
                item.addProperty("attached", hasAttachedLibrary(getAttachedLocalLibraries(scId), library.getName()));
                downloaded.add(item);
            }
            result.add("downloaded_local_libraries", downloaded);
            return success(result.toString());
        }
    }

    public static class AddLibraryTool implements AgentTool {
        @Override
        public String getName() {
            return "add_library";
        }

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
            ToolResult validation = validateProject(scId, context);
            if (validation != null) return validation;
            if (libName == null || libName.isEmpty()) return error("library_name is required");

            File libraryFile = new File(context.getSketchwareDir(), "data/" + scId + "/library");
            JsonArray libraries;
            if (libraryFile.exists()) {
                String content = readFile(libraryFile);
                try {
                    libraries = (content != null && !content.isEmpty()) ? JsonParser.parseString(content).getAsJsonArray() : new JsonArray();
                } catch (Exception e) {
                    libraries = new JsonArray();
                }
            } else {
                libraries = new JsonArray();
            }

            for (JsonElement el : libraries) {
                if (el.isJsonObject() && el.getAsJsonObject().has("name") &&
                        el.getAsJsonObject().get("name").getAsString().equals(libName)) {
                    el.getAsJsonObject().addProperty("useYn", "Y");
                    try {
                        writeFile(libraryFile, libraries.toString());
                        return success("{\"library_name\":\"" + libName + "\",\"status\":\"enabled\"}");
                    } catch (IOException e) {
                        return error("Write failed: " + e.getMessage());
                    }
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

            try {
                writeFile(libraryFile, libraries.toString());
                return success("{\"library_name\":\"" + libName + "\",\"status\":\"added\"}");
            } catch (IOException e) {
                return error("Write failed: " + e.getMessage());
            }
        }
    }

    public static class RemoveLibraryTool implements AgentTool {
        @Override
        public String getName() {
            return "remove_library";
        }

        @Override
        public String getDescription() {
            return "Disables a built-in library from a project.";
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
            ToolResult validation = validateProject(scId, context);
            if (validation != null) return validation;
            if (libName == null || libName.isEmpty()) return error("library_name is required");

            File libraryFile = new File(context.getSketchwareDir(), "data/" + scId + "/library");
            if (!libraryFile.exists()) return success("{\"library_name\":\"" + libName + "\",\"status\":\"not_configured\"}");

            String content = readFile(libraryFile);
            if (content == null || content.trim().isEmpty()) return success("{\"library_name\":\"" + libName + "\",\"status\":\"empty\"}");

            try {
                JsonArray libraries = JsonParser.parseString(content).getAsJsonArray();
                for (JsonElement el : libraries) {
                    if (el.isJsonObject() && el.getAsJsonObject().has("name") &&
                            el.getAsJsonObject().get("name").getAsString().equals(libName)) {
                        el.getAsJsonObject().addProperty("useYn", "N");
                        writeFile(libraryFile, libraries.toString());
                        return success("{\"library_name\":\"" + libName + "\",\"status\":\"disabled\"}");
                    }
                }
                return success("{\"library_name\":\"" + libName + "\",\"status\":\"not_found\"}");
            } catch (Exception e) {
                return error("Failed: " + e.getMessage());
            }
        }
    }

    public static class AttachLocalLibraryTool implements AgentTool {
        @Override
        public String getName() {
            return "attach_local_library";
        }

        @Override
        public String getDescription() {
            return "Attaches an already-downloaded local library to a project so it is included in builds.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            JsonObject scId = new JsonObject();
            scId.addProperty("type", "string");
            props.add("sc_id", scId);
            JsonObject name = new JsonObject();
            name.addProperty("type", "string");
            name.addProperty("description", "Downloaded local library directory name");
            props.add("library_name", name);
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
            String libraryName = arguments.has("library_name") ? arguments.get("library_name").getAsString() : null;
            ToolResult validation = validateProject(scId, context);
            if (validation != null) return validation;
            if (libraryName == null || libraryName.isEmpty()) return error("library_name is required");

            File libraryFolder = new File(context.getSketchwareDir(), "libs/local_libs/" + libraryName);
            if (!libraryFolder.exists() || !libraryFolder.isDirectory()) {
                return error("Downloaded local library not found: " + libraryName);
            }

            ArrayList<HashMap<String, Object>> attached = getAttachedLocalLibraries(scId);
            if (!hasAttachedLibrary(attached, libraryName)) {
                attached.add(LocalLibrariesUtil.createLibraryMap(libraryName, null));
                saveAttachedLocalLibraries(scId, attached);
            }

            JsonObject result = new JsonObject();
            result.addProperty("library_name", libraryName);
            result.addProperty("status", "attached");
            return success(result.toString());
        }
    }

    public static class DetachLocalLibraryTool implements AgentTool {
        @Override
        public String getName() {
            return "detach_local_library";
        }

        @Override
        public String getDescription() {
            return "Detaches a local library from the current project without deleting the downloaded library from storage.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            JsonObject scId = new JsonObject();
            scId.addProperty("type", "string");
            props.add("sc_id", scId);
            JsonObject name = new JsonObject();
            name.addProperty("type", "string");
            props.add("library_name", name);
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
            String libraryName = arguments.has("library_name") ? arguments.get("library_name").getAsString() : null;
            ToolResult validation = validateProject(scId, context);
            if (validation != null) return validation;
            if (libraryName == null || libraryName.isEmpty()) return error("library_name is required");

            ArrayList<HashMap<String, Object>> attached = getAttachedLocalLibraries(scId);
            attached.removeIf(entry -> {
                Object value = entry.get("name");
                return value != null && libraryName.equals(value.toString());
            });
            saveAttachedLocalLibraries(scId, attached);

            JsonObject result = new JsonObject();
            result.addProperty("library_name", libraryName);
            result.addProperty("status", "detached");
            return success(result.toString());
        }
    }

    public static class DownloadDependencyTool implements AgentTool {
        @Override
        public String getName() {
            return "download_dependency";
        }

        @Override
        public String getDescription() {
            return "Downloads a Maven dependency into Sketchware local libraries, dexes it, and attaches the resolved libraries to the project.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            JsonObject scId = new JsonObject();
            scId.addProperty("type", "string");
            props.add("sc_id", scId);
            JsonObject dependency = new JsonObject();
            dependency.addProperty("type", "string");
            dependency.addProperty("description", "Maven coordinate in group:artifact:version format");
            props.add("dependency", dependency);
            JsonObject includeTransitives = new JsonObject();
            includeTransitives.addProperty("type", "boolean");
            includeTransitives.addProperty("description", "Whether transitive dependencies should also be downloaded. Defaults to true.");
            props.add("include_transitives", includeTransitives);
            schema.add("properties", props);
            JsonArray req = new JsonArray();
            req.add("sc_id");
            req.add("dependency");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            String scId = arguments.has("sc_id") ? arguments.get("sc_id").getAsString() : null;
            String dependency = arguments.has("dependency") ? arguments.get("dependency").getAsString() : null;
            boolean includeTransitives = !arguments.has("include_transitives") || arguments.get("include_transitives").getAsBoolean();
            ToolResult validation = validateProject(scId, context);
            if (validation != null) return validation;
            if (dependency == null || dependency.isEmpty()) return error("dependency is required");

            String[] parts = dependency.split(":");
            if (parts.length != 3) {
                return error("dependency must be in group:artifact:version format");
            }

            Set<String> resolvedLibraries = new LinkedHashSet<>();
            BuildSettings buildSettings = new BuildSettings(scId);
            context.reportProgress("Resolving dependency…", 5);

            try {
                DependencyResolver resolver = new DependencyResolver(parts[0], parts[1], parts[2], !includeTransitives, buildSettings);
                resolver.resolveDependency(new DependencyResolver.DependencyResolverCallback() {
                    @Override
                    public void onDownloadStart(org.cosmic.ide.dependency.resolver.api.Artifact artifact) {
                        context.reportProgress("Downloading " + artifact.getArtifactId() + ":" + artifact.getVersion() + "…", 20, true);
                    }

                    @Override
                    public void onDownloadEnd(org.cosmic.ide.dependency.resolver.api.Artifact artifact) {
                        context.reportProgress("Downloaded " + artifact.getArtifactId() + ":" + artifact.getVersion(), 45);
                    }

                    @Override
                    public void unzipping(org.cosmic.ide.dependency.resolver.api.Artifact artifact) {
                        context.reportProgress("Extracting " + artifact.getArtifactId() + "…", 60);
                    }

                    @Override
                    public void dexing(org.cosmic.ide.dependency.resolver.api.Artifact artifact) {
                        context.reportProgress("Dexing " + artifact.getArtifactId() + "…", 75);
                    }

                    @Override
                    public void onTaskCompleted(List<String> artifacts) {
                        resolvedLibraries.addAll(artifacts);
                        context.reportProgress("Dependency ready", 95);
                    }
                });

                if (context.isCancelled()) {
                    return error("Dependency download cancelled");
                }

                ArrayList<HashMap<String, Object>> attached = getAttachedLocalLibraries(scId);
                JsonArray attachedNow = new JsonArray();
                for (String libraryName : resolvedLibraries) {
                    if (!hasAttachedLibrary(attached, libraryName)) {
                        attached.add(LocalLibrariesUtil.createLibraryMap(libraryName, dependency));
                    }
                    attachedNow.add(libraryName);
                }
                saveAttachedLocalLibraries(scId, attached);

                JsonObject result = new JsonObject();
                result.addProperty("dependency", dependency);
                result.add("attached_libraries", attachedNow);
                result.addProperty("status", "downloaded_and_attached");
                result.addProperty("message", resolvedLibraries.isEmpty()
                        ? "No downloadable artifacts were resolved"
                        : "Dependency downloaded and attached successfully");
                return success(result.toString());
            } catch (Exception e) {
                return error("Failed to download dependency: " + e.getMessage());
            }
        }
    }

    public static class ValidateLibrariesTool implements AgentTool {
        @Override
        public String getName() {
            return "validate_libraries";
        }

        @Override
        public String getDescription() {
            return "Validates the project's built-in and local library configuration and returns dependency health details.";
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
            JsonArray required = new JsonArray();
            required.add("sc_id");
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            String scId = arguments.has("sc_id") ? arguments.get("sc_id").getAsString() : null;
            ToolResult validation = validateProject(scId, context);
            if (validation != null) return validation;

            BuiltInLibraryCompatibilityMatrix.ValidationResult validationResult =
                    BuiltInLibraryCompatibilityMatrix.validate(scId);
            JsonObject result = new JsonObject();
            result.addProperty("sc_id", scId);
            result.addProperty("valid", validationResult.isValid());
            JsonArray errors = new JsonArray();
            for (String error : validationResult.getErrors()) {
                errors.add(error);
            }
            result.add("errors", errors);
            JsonArray requiredLibraries = new JsonArray();
            for (String library : validationResult.getRequiredLibraries()) {
                requiredLibraries.add(library);
            }
            result.add("required_libraries", requiredLibraries);
            result.addProperty("attached_local_library_count", getAttachedLocalLibraries(scId).size());
            return success(result.toString());
        }
    }

}
