package pro.sketchware.ai.tools;

import android.os.Environment;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import pro.sketchware.ai.models.ToolResult;

/**
 * Contains tools for project-level operations: listing, creating, deleting,
 * duplicating, and retrieving project information.
 */
public final class ProjectTools {

    private ProjectTools() {
    }

    private static File getSketchwareDir() {
        return new File(Environment.getExternalStorageDirectory(), ".sketchware");
    }

    private static File getDataDir() {
        return new File(getSketchwareDir(), "data");
    }

    private static File getMyscListDir() {
        return new File(getSketchwareDir(), "mysc" + File.separator + "list");
    }

    private static File getMyscDir() {
        return new File(getSketchwareDir(), "mysc");
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

    private static void copyDirectory(File source, File target) throws IOException {
        if (!target.exists()) {
            target.mkdirs();
        }
        File[] files = source.listFiles();
        if (files == null) return;
        for (File file : files) {
            File dest = new File(target, file.getName());
            if (file.isDirectory()) {
                copyDirectory(file, dest);
            } else {
                copyFile(file, dest);
            }
        }
    }

    private static void copyFile(File source, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private static boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }

    private static String generateNewScId() {
        int maxId = 600;
        File dataDir = getDataDir();
        if (dataDir.exists()) {
            File[] files = dataDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        try {
                            int id = Integer.parseInt(file.getName());
                            if (id > maxId) {
                                maxId = id;
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }

        File myscListDir = getMyscListDir();
        if (myscListDir.exists()) {
            File[] files = myscListDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        try {
                            int id = Integer.parseInt(file.getName());
                            if (id > maxId) {
                                maxId = id;
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }

        return String.valueOf(maxId + 1);
    }

    private static ToolResult success(String output) {
        return new ToolResult(null, true, output, null);
    }

    private static ToolResult error(String message) {
        return new ToolResult(null, false, null, message);
    }

    /**
     * Lists all projects visible to the workspace.
     */
    public static class ListProjectsTool implements AgentTool {

        @Override
        public String getName() {
            return "list_projects";
        }

        @Override
        public String getDescription() {
            return "Lists all Sketchware Pro projects accessible in the current workspace. "
                    + "Returns project SC IDs, names, package names, and version names.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", new JsonObject());
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            try {
                JsonArray projects = new JsonArray();
                File dataDir = getDataDir();
                if (!dataDir.exists()) {
                    return success(projects.toString());
                }

                File[] projectDirs = dataDir.listFiles();
                if (projectDirs == null) {
                    return success(projects.toString());
                }

                for (File dir : projectDirs) {
                    if (!dir.isDirectory()) continue;

                    String scId = dir.getName();
                    if (!context.isProjectAllowed(scId)) continue;

                    File projectFile = new File(dir, "project");
                    if (!projectFile.exists()) continue;

                    try {
                        String content = readFileContent(projectFile);
                        JsonObject projectData = JsonParser.parseString(content).getAsJsonObject();

                        JsonObject entry = new JsonObject();
                        entry.addProperty("sc_id", scId);
                        entry.addProperty("name",
                                projectData.has("my_app_name")
                                        ? projectData.get("my_app_name").getAsString() : "");
                        entry.addProperty("package_name",
                                projectData.has("my_sc_pkg_name")
                                        ? projectData.get("my_sc_pkg_name").getAsString() : "");
                        entry.addProperty("version_name",
                                projectData.has("sc_ver_name")
                                        ? projectData.get("sc_ver_name").getAsString() : "");
                        projects.add(entry);
                    } catch (IOException | JsonSyntaxException e) {
                        // Skip unreadable projects
                    }
                }

                return success(projects.toString());
            } catch (Exception e) {
                return error("Failed to list projects: " + e.getMessage());
            }
        }
    }

    /**
     * Gets detailed information about a specific project.
     */
    public static class GetProjectInfoTool implements AgentTool {

        @Override
        public String getName() {
            return "get_project_info";
        }

        @Override
        public String getDescription() {
            return "Gets detailed metadata about a Sketchware Pro project including app name, "
                    + "package name, version info, and configuration.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID (e.g., \"601\")");
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

            File projectFile = new File(context.getProjectDataDir(scId), "project");
            if (!projectFile.exists()) {
                return error("Project not found: " + scId);
            }

            try {
                String content = readFileContent(projectFile);
                JsonObject projectData = JsonParser.parseString(content).getAsJsonObject();
                return success(projectData.toString());
            } catch (IOException e) {
                return error("Failed to read project file: " + e.getMessage());
            } catch (JsonSyntaxException e) {
                return error("Project file contains invalid JSON: " + e.getMessage());
            }
        }
    }

    /**
     * Creates a new Sketchware Pro project.
     */
    public static class CreateProjectTool implements AgentTool {

        @Override
        public String getName() {
            return "create_project";
        }

        @Override
        public String getDescription() {
            return "Creates a new Sketchware Pro project with the specified app name, package name, "
                    + "and optional version information. Returns the new project's SC ID.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject appNameProp = new JsonObject();
            appNameProp.addProperty("type", "string");
            appNameProp.addProperty("description", "The display name of the app");
            properties.add("app_name", appNameProp);

            JsonObject pkgNameProp = new JsonObject();
            pkgNameProp.addProperty("type", "string");
            pkgNameProp.addProperty("description", "The Java package name (e.g., \"com.example.myapp\")");
            properties.add("package_name", pkgNameProp);

            JsonObject projNameProp = new JsonObject();
            projNameProp.addProperty("type", "string");
            projNameProp.addProperty("description", "The internal project/workspace name. Defaults to app_name if not specified.");
            properties.add("project_name", projNameProp);

            JsonObject verNameProp = new JsonObject();
            verNameProp.addProperty("type", "string");
            verNameProp.addProperty("description", "Version name string (default: \"1.0\")");
            properties.add("version_name", verNameProp);

            JsonObject verCodeProp = new JsonObject();
            verCodeProp.addProperty("type", "integer");
            verCodeProp.addProperty("description", "Version code number (default: 1)");
            properties.add("version_code", verCodeProp);

            JsonArray required = new JsonArray();
            required.add("app_name");
            required.add("package_name");

            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", properties);
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            if (!arguments.has("app_name") || arguments.get("app_name").isJsonNull()) {
                return error("Missing required parameter: app_name");
            }
            if (!arguments.has("package_name") || arguments.get("package_name").isJsonNull()) {
                return error("Missing required parameter: package_name");
            }

            String appName = arguments.get("app_name").getAsString();
            String packageName = arguments.get("package_name").getAsString();
            String projectName = arguments.has("project_name") && !arguments.get("project_name").isJsonNull()
                    ? arguments.get("project_name").getAsString() : appName;
            String versionName = arguments.has("version_name") && !arguments.get("version_name").isJsonNull()
                    ? arguments.get("version_name").getAsString() : "1.0";
            int versionCode = arguments.has("version_code") && !arguments.get("version_code").isJsonNull()
                    ? arguments.get("version_code").getAsInt() : 1;

            try {
                String scId = generateNewScId();

                // Create project metadata
                JsonObject projectData = new JsonObject();
                projectData.addProperty("sc_id", scId);
                projectData.addProperty("my_app_name", appName);
                projectData.addProperty("my_sc_pkg_name", packageName);
                projectData.addProperty("my_ws_name", projectName);
                projectData.addProperty("sc_ver_name", versionName);
                projectData.addProperty("sc_ver_code", String.valueOf(versionCode));
                projectData.addProperty("sketchware_ver", 150);
                projectData.addProperty("color_accent", -1);
                projectData.addProperty("color_primary", -12627531);
                projectData.addProperty("color_primary_dark", -13615201);
                projectData.addProperty("color_control_highlight", 520093696);
                projectData.addProperty("color_control_normal", -5592406);
                projectData.addProperty("custom_icon", false);

                // Create directory structure
                File dataDir = new File(getDataDir(), scId);
                File myscListDir = new File(getMyscListDir(), scId);
                File myscDir = new File(getMyscDir(), scId);
                dataDir.mkdirs();
                myscListDir.mkdirs();
                myscDir.mkdirs();

                // Write project file
                writeFileContent(new File(dataDir, "project"), projectData.toString());

                // Create default main activity file entry
                JsonArray fileArray = new JsonArray();
                JsonObject mainActivity = new JsonObject();
                mainActivity.addProperty("fileName", "main");
                mainActivity.addProperty("fileType", 0);
                mainActivity.addProperty("keyboardSetting", 0);
                mainActivity.addProperty("orientation", 0);
                mainActivity.addProperty("options", 0);
                fileArray.add(mainActivity);
                writeFileContent(new File(dataDir, "file"), fileArray.toString());

                // Create empty logic, view, library, resource files
                writeFileContent(new File(dataDir, "logic"), "[]");
                writeFileContent(new File(dataDir, "view"), "[]");
                writeFileContent(new File(dataDir, "library"), "[]");
                writeFileContent(new File(dataDir, "resource"), "[]");

                JsonObject result = new JsonObject();
                result.addProperty("sc_id", scId);
                result.addProperty("app_name", appName);
                result.addProperty("package_name", packageName);
                result.addProperty("message", "Project created successfully");
                return success(result.toString());
            } catch (IOException e) {
                return error("Failed to create project: " + e.getMessage());
            }
        }
    }

    /**
     * Deletes a project and all its associated files.
     */
    public static class DeleteProjectTool implements AgentTool {

        @Override
        public String getName() {
            return "delete_project";
        }

        @Override
        public String getDescription() {
            return "Deletes a Sketchware Pro project and all its associated files. This action cannot be undone.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID to delete");
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

            File dataDir = context.getProjectDataDir(scId);
            if (!dataDir.exists()) {
                return error("Project not found: " + scId);
            }

            try {
                boolean allDeleted = true;

                // Delete data directory
                if (dataDir.exists()) {
                    allDeleted &= deleteRecursive(dataDir);
                }

                // Delete mysc/list directory
                File myscListDir = context.getProjectMyscListDir(scId);
                if (myscListDir.exists()) {
                    allDeleted &= deleteRecursive(myscListDir);
                }

                // Delete mysc directory
                File myscDir = context.getProjectMyscDir(scId);
                if (myscDir.exists()) {
                    allDeleted &= deleteRecursive(myscDir);
                }

                // Delete backup directory
                File bakDir = context.getProjectBackupDir(scId);
                if (bakDir.exists()) {
                    allDeleted &= deleteRecursive(bakDir);
                }

                if (allDeleted) {
                    JsonObject result = new JsonObject();
                    result.addProperty("sc_id", scId);
                    result.addProperty("message", "Project deleted successfully");
                    return success(result.toString());
                } else {
                    return error("Some project files could not be deleted for project: " + scId);
                }
            } catch (Exception e) {
                return error("Failed to delete project: " + e.getMessage());
            }
        }
    }

    /**
     * Duplicates an existing project under a new SC ID.
     */
    public static class DuplicateProjectTool implements AgentTool {

        @Override
        public String getName() {
            return "duplicate_project";
        }

        @Override
        public String getDescription() {
            return "Duplicates a Sketchware Pro project, creating a copy with a new SC ID. "
                    + "Optionally sets a new app name for the duplicate.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The SC ID of the project to duplicate");
            properties.add("sc_id", scIdProp);

            JsonObject newNameProp = new JsonObject();
            newNameProp.addProperty("type", "string");
            newNameProp.addProperty("description", "New app name for the duplicated project. If not specified, the original name is kept.");
            properties.add("new_app_name", newNameProp);

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
            String sourceScId = arguments.get("sc_id").getAsString();
            String newAppName = arguments.has("new_app_name") && !arguments.get("new_app_name").isJsonNull()
                    ? arguments.get("new_app_name").getAsString() : null;

            if (!context.isProjectAllowed(sourceScId)) {
                return error("Access denied: project " + sourceScId + " is not in the current workspace");
            }

            File sourceDataDir = context.getProjectDataDir(sourceScId);
            if (!sourceDataDir.exists()) {
                return error("Source project not found: " + sourceScId);
            }

            try {
                String newScId = generateNewScId();

                // Copy data directory
                File newDataDir = new File(getDataDir(), newScId);
                copyDirectory(sourceDataDir, newDataDir);

                // Copy mysc/list directory if exists
                File sourceMyscListDir = context.getProjectMyscListDir(sourceScId);
                if (sourceMyscListDir.exists()) {
                    File newMyscListDir = new File(getMyscListDir(), newScId);
                    copyDirectory(sourceMyscListDir, newMyscListDir);
                }

                // Copy mysc directory if exists
                File sourceMyscDir = context.getProjectMyscDir(sourceScId);
                if (sourceMyscDir.exists()) {
                    File newMyscDir = new File(getMyscDir(), newScId);
                    copyDirectory(sourceMyscDir, newMyscDir);
                }

                // Update the project file with new SC ID and optionally new name
                File newProjectFile = new File(newDataDir, "project");
                if (newProjectFile.exists()) {
                    String content = readFileContent(newProjectFile);
                    JsonObject projectData = JsonParser.parseString(content).getAsJsonObject();
                    projectData.addProperty("sc_id", newScId);
                    if (newAppName != null) {
                        projectData.addProperty("my_app_name", newAppName);
                    }
                    writeFileContent(newProjectFile, projectData.toString());
                }

                JsonObject result = new JsonObject();
                result.addProperty("sc_id", newScId);
                result.addProperty("source_sc_id", sourceScId);
                result.addProperty("message", "Project duplicated successfully");
                return success(result.toString());
            } catch (IOException e) {
                return error("Failed to duplicate project: " + e.getMessage());
            } catch (JsonSyntaxException e) {
                return error("Failed to parse project data: " + e.getMessage());
            }
        }
    }
}
