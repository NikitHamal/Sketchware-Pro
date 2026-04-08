package pro.sketchware.agent.tools;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import a.a.a.lC;
import a.a.a.wq;
import a.a.a.yB;
import pro.sketchware.agent.data.AgentDatabase;
import pro.sketchware.agent.provider.AIProvider;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;

public class AgentToolRegistry {

    private final Context context;
    private final AgentDatabase db;
    private final String workspaceId;
    private final Gson gson = new Gson();

    public AgentToolRegistry(Context context, String workspaceId) {
        this.context = context;
        this.db = AgentDatabase.getInstance(context);
        this.workspaceId = workspaceId;
    }

    public List<AIProvider.ToolDefinition> getToolDefinitions() {
        List<AIProvider.ToolDefinition> tools = new ArrayList<>();

        tools.add(new AIProvider.ToolDefinition(
                "list_workspace_projects",
                "List all projects in the current workspace with their details (ID, name, package name, version).",
                "{\"type\":\"object\",\"properties\":{},\"required\":[]}"
        ));

        tools.add(new AIProvider.ToolDefinition(
                "list_all_projects",
                "List all Sketchware Pro projects available on the device.",
                "{\"type\":\"object\",\"properties\":{},\"required\":[]}"
        ));

        tools.add(new AIProvider.ToolDefinition(
                "get_project_info",
                "Get detailed information about a specific project by its sc_id.",
                "{\"type\":\"object\",\"properties\":{\"sc_id\":{\"type\":\"string\",\"description\":\"The project ID (sc_id)\"}},\"required\":[\"sc_id\"]}"
        ));

        tools.add(new AIProvider.ToolDefinition(
                "create_project",
                "Create a new Sketchware Pro project with the given configuration. Returns the sc_id of the created project.",
                "{\"type\":\"object\",\"properties\":{" +
                        "\"app_name\":{\"type\":\"string\",\"description\":\"Display name of the app\"}," +
                        "\"package_name\":{\"type\":\"string\",\"description\":\"Java package name (e.g. com.example.myapp)\"}," +
                        "\"project_name\":{\"type\":\"string\",\"description\":\"Internal project/workspace name\"}," +
                        "\"version_code\":{\"type\":\"string\",\"description\":\"Version code (default: 1)\"}," +
                        "\"version_name\":{\"type\":\"string\",\"description\":\"Version name (default: 1.0)\"}," +
                        "\"color_primary\":{\"type\":\"string\",\"description\":\"Primary color hex (default: #FF2196F3)\"}," +
                        "\"color_primary_dark\":{\"type\":\"string\",\"description\":\"Primary dark color hex (default: #FF1976D2)\"}," +
                        "\"color_accent\":{\"type\":\"string\",\"description\":\"Accent color hex (default: #FF2196F3)\"}" +
                        "},\"required\":[\"app_name\",\"package_name\"]}"
        ));

        tools.add(new AIProvider.ToolDefinition(
                "delete_project",
                "Delete a Sketchware Pro project by its sc_id. This is irreversible.",
                "{\"type\":\"object\",\"properties\":{\"sc_id\":{\"type\":\"string\",\"description\":\"The project ID to delete\"}},\"required\":[\"sc_id\"]}"
        ));

        tools.add(new AIProvider.ToolDefinition(
                "add_project_to_workspace",
                "Add an existing project to the current workspace by its sc_id.",
                "{\"type\":\"object\",\"properties\":{\"sc_id\":{\"type\":\"string\",\"description\":\"The project ID to add\"}},\"required\":[\"sc_id\"]}"
        ));

        tools.add(new AIProvider.ToolDefinition(
                "read_file",
                "Read the contents of a file in a project's data directory. Use paths like 'files/java/MainActivity.java' or 'files/resource/layout/main.xml'.",
                "{\"type\":\"object\",\"properties\":{" +
                        "\"sc_id\":{\"type\":\"string\",\"description\":\"The project ID\"}," +
                        "\"path\":{\"type\":\"string\",\"description\":\"Relative path within the project data directory\"}" +
                        "},\"required\":[\"sc_id\",\"path\"]}"
        ));

        tools.add(new AIProvider.ToolDefinition(
                "write_file",
                "Write content to a file in a project's data directory. Creates parent directories if needed.",
                "{\"type\":\"object\",\"properties\":{" +
                        "\"sc_id\":{\"type\":\"string\",\"description\":\"The project ID\"}," +
                        "\"path\":{\"type\":\"string\",\"description\":\"Relative path within the project data directory\"}," +
                        "\"content\":{\"type\":\"string\",\"description\":\"File content to write\"}" +
                        "},\"required\":[\"sc_id\",\"path\",\"content\"]}"
        ));

        tools.add(new AIProvider.ToolDefinition(
                "delete_file",
                "Delete a file in a project's data directory.",
                "{\"type\":\"object\",\"properties\":{" +
                        "\"sc_id\":{\"type\":\"string\",\"description\":\"The project ID\"}," +
                        "\"path\":{\"type\":\"string\",\"description\":\"Relative path within the project data directory\"}" +
                        "},\"required\":[\"sc_id\",\"path\"]}"
        ));

        tools.add(new AIProvider.ToolDefinition(
                "list_files",
                "List files and directories in a project's data directory.",
                "{\"type\":\"object\",\"properties\":{" +
                        "\"sc_id\":{\"type\":\"string\",\"description\":\"The project ID\"}," +
                        "\"path\":{\"type\":\"string\",\"description\":\"Relative path within the project data directory (empty for root)\"}" +
                        "},\"required\":[\"sc_id\"]}"
        ));

        tools.add(new AIProvider.ToolDefinition(
                "copy_file",
                "Copy a file within a project's data directory.",
                "{\"type\":\"object\",\"properties\":{" +
                        "\"sc_id\":{\"type\":\"string\",\"description\":\"The project ID\"}," +
                        "\"source_path\":{\"type\":\"string\",\"description\":\"Source relative path\"}," +
                        "\"dest_path\":{\"type\":\"string\",\"description\":\"Destination relative path\"}" +
                        "},\"required\":[\"sc_id\",\"source_path\",\"dest_path\"]}"
        ));

        tools.add(new AIProvider.ToolDefinition(
                "move_file",
                "Move/rename a file within a project's data directory.",
                "{\"type\":\"object\",\"properties\":{" +
                        "\"sc_id\":{\"type\":\"string\",\"description\":\"The project ID\"}," +
                        "\"source_path\":{\"type\":\"string\",\"description\":\"Source relative path\"}," +
                        "\"dest_path\":{\"type\":\"string\",\"description\":\"Destination relative path\"}" +
                        "},\"required\":[\"sc_id\",\"source_path\",\"dest_path\"]}"
        ));

        tools.add(new AIProvider.ToolDefinition(
                "get_compile_log",
                "Get the last compilation error log for a project.",
                "{\"type\":\"object\",\"properties\":{\"sc_id\":{\"type\":\"string\",\"description\":\"The project ID\"}},\"required\":[\"sc_id\"]}"
        ));

        tools.add(new AIProvider.ToolDefinition(
                "duplicate_project",
                "Create a duplicate of an existing project with a new sc_id.",
                "{\"type\":\"object\",\"properties\":{\"sc_id\":{\"type\":\"string\",\"description\":\"The project ID to duplicate\"}},\"required\":[\"sc_id\"]}"
        ));

        tools.add(new AIProvider.ToolDefinition(
                "update_project_config",
                "Update project configuration (app name, package name, version, colors).",
                "{\"type\":\"object\",\"properties\":{" +
                        "\"sc_id\":{\"type\":\"string\",\"description\":\"The project ID\"}," +
                        "\"app_name\":{\"type\":\"string\",\"description\":\"New app name\"}," +
                        "\"package_name\":{\"type\":\"string\",\"description\":\"New package name\"}," +
                        "\"version_code\":{\"type\":\"string\",\"description\":\"New version code\"}," +
                        "\"version_name\":{\"type\":\"string\",\"description\":\"New version name\"}," +
                        "\"color_primary\":{\"type\":\"string\",\"description\":\"Primary color hex\"}," +
                        "\"color_primary_dark\":{\"type\":\"string\",\"description\":\"Primary dark color hex\"}," +
                        "\"color_accent\":{\"type\":\"string\",\"description\":\"Accent color hex\"}" +
                        "},\"required\":[\"sc_id\"]}"
        ));

        return tools;
    }

    public String executeTool(String toolName, String argsJson) {
        try {
            JsonObject args = argsJson != null && !argsJson.isEmpty()
                    ? JsonParser.parseString(argsJson).getAsJsonObject()
                    : new JsonObject();

            return switch (toolName) {
                case "list_workspace_projects" -> listWorkspaceProjects();
                case "list_all_projects" -> listAllProjects();
                case "get_project_info" -> getProjectInfo(args.get("sc_id").getAsString());
                case "create_project" -> createProject(args);
                case "delete_project" -> deleteProject(args.get("sc_id").getAsString());
                case "add_project_to_workspace" -> addProjectToWorkspace(args.get("sc_id").getAsString());
                case "read_file" -> readFile(args.get("sc_id").getAsString(), args.get("path").getAsString());
                case "write_file" -> writeFile(args.get("sc_id").getAsString(), args.get("path").getAsString(), args.get("content").getAsString());
                case "delete_file" -> deleteFile(args.get("sc_id").getAsString(), args.get("path").getAsString());
                case "list_files" -> listFiles(args.get("sc_id").getAsString(), args.has("path") ? args.get("path").getAsString() : "");
                case "copy_file" -> copyFile(args.get("sc_id").getAsString(), args.get("source_path").getAsString(), args.get("dest_path").getAsString());
                case "move_file" -> moveFile(args.get("sc_id").getAsString(), args.get("source_path").getAsString(), args.get("dest_path").getAsString());
                case "get_compile_log" -> getCompileLog(args.get("sc_id").getAsString());
                case "duplicate_project" -> duplicateProject(args.get("sc_id").getAsString());
                case "update_project_config" -> updateProjectConfig(args);
                default -> "{\"error\": \"Unknown tool: " + toolName + "\"}";
            };
        } catch (Exception e) {
            return "{\"error\": \"Tool execution failed: " + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    private boolean isProjectInWorkspace(String scId) {
        List<String> projectIds = db.getWorkspaceProjectIds(workspaceId);
        return projectIds.contains(scId);
    }

    private String listWorkspaceProjects() {
        List<String> projectIds = db.getWorkspaceProjectIds(workspaceId);
        JsonArray arr = new JsonArray();
        for (String scId : projectIds) {
            HashMap<String, Object> project = lC.b(scId);
            if (project != null) {
                arr.add(projectToJson(project));
            }
        }
        JsonObject result = new JsonObject();
        result.add("projects", arr);
        result.addProperty("count", arr.size());
        return result.toString();
    }

    private String listAllProjects() {
        List<HashMap<String, Object>> projects = lC.a();
        JsonArray arr = new JsonArray();
        for (HashMap<String, Object> p : projects) {
            arr.add(projectToJson(p));
        }
        JsonObject result = new JsonObject();
        result.add("projects", arr);
        result.addProperty("count", arr.size());
        return result.toString();
    }

    private String getProjectInfo(String scId) {
        HashMap<String, Object> project = lC.b(scId);
        if (project == null) {
            return "{\"error\": \"Project not found: " + scId + "\"}";
        }
        return projectToJson(project).toString();
    }

    private String createProject(JsonObject args) {
        String newScId = lC.b();
        String appName = args.get("app_name").getAsString();
        String packageName = args.get("package_name").getAsString();
        String projectName = args.has("project_name") ? args.get("project_name").getAsString() : appName.replaceAll("[^a-zA-Z0-9]", "");
        String versionCode = args.has("version_code") ? args.get("version_code").getAsString() : "1";
        String versionName = args.has("version_name") ? args.get("version_name").getAsString() : "1.0";
        String colorPrimary = args.has("color_primary") ? args.get("color_primary").getAsString() : "#FF2196F3";
        String colorPrimaryDark = args.has("color_primary_dark") ? args.get("color_primary_dark").getAsString() : "#FF1976D2";
        String colorAccent = args.has("color_accent") ? args.get("color_accent").getAsString() : "#FF2196F3";

        HashMap<String, Object> projectData = new HashMap<>();
        projectData.put("sc_id", newScId);
        projectData.put("my_app_name", appName);
        projectData.put("my_sc_pkg_name", packageName);
        projectData.put("my_ws_name", projectName);
        projectData.put("sc_ver_code", versionCode);
        projectData.put("sc_ver_name", versionName);
        projectData.put("color_primary", parseColor(colorPrimary));
        projectData.put("color_primary_dark", parseColor(colorPrimaryDark));
        projectData.put("color_accent", parseColor(colorAccent));
        projectData.put("color_control_highlight", parseColor("#20000000"));
        projectData.put("color_control_normal", parseColor("#FF757575"));
        projectData.put("sketchware_ver", 150);
        projectData.put("custom_icon", false);

        // Create project directories
        String projectDir = wq.c(newScId);
        new File(projectDir).mkdirs();

        // Save project file
        lC.a(newScId, projectData);

        // Add to workspace
        db.addProjectToWorkspace(workspaceId, newScId);

        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        result.addProperty("sc_id", newScId);
        result.addProperty("message", "Project '" + appName + "' created successfully with ID " + newScId);
        return result.toString();
    }

    private String deleteProject(String scId) {
        if (!isProjectInWorkspace(scId)) {
            return "{\"error\": \"Project " + scId + " is not in this workspace. Add it first to manage it.\"}";
        }
        HashMap<String, Object> project = lC.b(scId);
        if (project == null) {
            return "{\"error\": \"Project not found: " + scId + "\"}";
        }
        String appName = yB.c(project, "my_app_name");
        lC.a(context, scId);
        db.removeProjectFromWorkspace(workspaceId, scId);

        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        result.addProperty("message", "Project '" + appName + "' (ID: " + scId + ") deleted successfully");
        return result.toString();
    }

    private String addProjectToWorkspace(String scId) {
        HashMap<String, Object> project = lC.b(scId);
        if (project == null) {
            return "{\"error\": \"Project not found: " + scId + "\"}";
        }
        db.addProjectToWorkspace(workspaceId, scId);
        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        result.addProperty("message", "Project '" + yB.c(project, "my_app_name") + "' added to workspace");
        return result.toString();
    }

    private String readFile(String scId, String path) {
        if (!isProjectInWorkspace(scId)) {
            return "{\"error\": \"Project " + scId + " is not in this workspace.\"}";
        }
        String fullPath = wq.b(scId) + File.separator + path;
        File file = new File(fullPath);
        if (!file.exists()) {
            // Also try the mysc/list path
            fullPath = wq.c(scId) + File.separator + path;
            file = new File(fullPath);
            if (!file.exists()) {
                return "{\"error\": \"File not found: " + path + "\"}";
            }
        }
        try {
            String content = FileUtil.readFile(file.getAbsolutePath());
            JsonObject result = new JsonObject();
            result.addProperty("path", path);
            result.addProperty("content", content);
            result.addProperty("size", file.length());
            return result.toString();
        } catch (Exception e) {
            return "{\"error\": \"Failed to read file: " + e.getMessage() + "\"}";
        }
    }

    private String writeFile(String scId, String path, String content) {
        if (!isProjectInWorkspace(scId)) {
            return "{\"error\": \"Project " + scId + " is not in this workspace.\"}";
        }
        String fullPath = wq.b(scId) + File.separator + path;
        File file = new File(fullPath);
        file.getParentFile().mkdirs();
        try {
            FileUtil.writeFile(file.getAbsolutePath(), content);
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("path", path);
            result.addProperty("message", "File written successfully");
            return result.toString();
        } catch (Exception e) {
            return "{\"error\": \"Failed to write file: " + e.getMessage() + "\"}";
        }
    }

    private String deleteFile(String scId, String path) {
        if (!isProjectInWorkspace(scId)) {
            return "{\"error\": \"Project " + scId + " is not in this workspace.\"}";
        }
        String fullPath = wq.b(scId) + File.separator + path;
        File file = new File(fullPath);
        if (!file.exists()) {
            return "{\"error\": \"File not found: " + path + "\"}";
        }
        boolean deleted = file.delete();
        JsonObject result = new JsonObject();
        result.addProperty("success", deleted);
        result.addProperty("message", deleted ? "File deleted" : "Failed to delete file");
        return result.toString();
    }

    private String listFiles(String scId, String path) {
        if (!isProjectInWorkspace(scId)) {
            return "{\"error\": \"Project " + scId + " is not in this workspace.\"}";
        }
        String basePath = wq.b(scId);
        String fullPath = path.isEmpty() ? basePath : basePath + File.separator + path;
        File dir = new File(fullPath);

        // Also check in mysc/list path
        if (!dir.exists()) {
            basePath = wq.c(scId);
            fullPath = path.isEmpty() ? basePath : basePath + File.separator + path;
            dir = new File(fullPath);
        }

        if (!dir.exists() || !dir.isDirectory()) {
            return "{\"error\": \"Directory not found: " + path + "\"}";
        }

        File[] files = dir.listFiles();
        JsonArray arr = new JsonArray();
        if (files != null) {
            for (File f : files) {
                JsonObject entry = new JsonObject();
                entry.addProperty("name", f.getName());
                entry.addProperty("type", f.isDirectory() ? "directory" : "file");
                entry.addProperty("size", f.length());
                arr.add(entry);
            }
        }
        JsonObject result = new JsonObject();
        result.add("entries", arr);
        result.addProperty("count", arr.size());
        result.addProperty("path", path);
        return result.toString();
    }

    private String copyFile(String scId, String sourcePath, String destPath) {
        if (!isProjectInWorkspace(scId)) {
            return "{\"error\": \"Project " + scId + " is not in this workspace.\"}";
        }
        String basePath = wq.b(scId);
        File source = new File(basePath + File.separator + sourcePath);
        File dest = new File(basePath + File.separator + destPath);
        if (!source.exists()) {
            return "{\"error\": \"Source file not found: " + sourcePath + "\"}";
        }
        try {
            dest.getParentFile().mkdirs();
            String content = FileUtil.readFile(source.getAbsolutePath());
            FileUtil.writeFile(dest.getAbsolutePath(), content);
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", "File copied from " + sourcePath + " to " + destPath);
            return result.toString();
        } catch (Exception e) {
            return "{\"error\": \"Copy failed: " + e.getMessage() + "\"}";
        }
    }

    private String moveFile(String scId, String sourcePath, String destPath) {
        if (!isProjectInWorkspace(scId)) {
            return "{\"error\": \"Project " + scId + " is not in this workspace.\"}";
        }
        String basePath = wq.b(scId);
        File source = new File(basePath + File.separator + sourcePath);
        File dest = new File(basePath + File.separator + destPath);
        if (!source.exists()) {
            return "{\"error\": \"Source file not found: " + sourcePath + "\"}";
        }
        dest.getParentFile().mkdirs();
        boolean moved = source.renameTo(dest);
        JsonObject result = new JsonObject();
        result.addProperty("success", moved);
        result.addProperty("message", moved ? "File moved" : "Move failed");
        return result.toString();
    }

    private String getCompileLog(String scId) {
        if (!isProjectInWorkspace(scId)) {
            return "{\"error\": \"Project " + scId + " is not in this workspace.\"}";
        }
        String logPath = FilePathUtil.getLastCompileLogPath(scId);
        File logFile = new File(logPath);
        if (!logFile.exists()) {
            return "{\"log\": \"No compilation log found for project " + scId + "\"}";
        }
        try {
            String content = FileUtil.readFile(logPath);
            JsonObject result = new JsonObject();
            result.addProperty("log", content);
            return result.toString();
        } catch (Exception e) {
            return "{\"error\": \"Failed to read compile log: " + e.getMessage() + "\"}";
        }
    }

    private String duplicateProject(String scId) {
        HashMap<String, Object> original = lC.b(scId);
        if (original == null) {
            return "{\"error\": \"Project not found: " + scId + "\"}";
        }

        String newScId = lC.b();
        HashMap<String, Object> newProject = new HashMap<>(original);
        newProject.put("sc_id", newScId);
        newProject.put("my_ws_name", yB.c(original, "my_ws_name") + "_copy");

        // Create directory
        String newDir = wq.c(newScId);
        new File(newDir).mkdirs();

        // Save project metadata
        lC.a(newScId, newProject);

        // Copy data directory
        String sourceData = wq.b(scId);
        String destData = wq.b(newScId);
        if (new File(sourceData).exists()) {
            copyDirectory(new File(sourceData), new File(destData));
        }

        // Add to workspace
        db.addProjectToWorkspace(workspaceId, newScId);

        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        result.addProperty("sc_id", newScId);
        result.addProperty("message", "Project duplicated successfully. New ID: " + newScId);
        return result.toString();
    }

    private String updateProjectConfig(JsonObject args) {
        String scId = args.get("sc_id").getAsString();
        if (!isProjectInWorkspace(scId)) {
            return "{\"error\": \"Project " + scId + " is not in this workspace.\"}";
        }
        HashMap<String, Object> project = lC.b(scId);
        if (project == null) {
            return "{\"error\": \"Project not found: " + scId + "\"}";
        }

        HashMap<String, Object> updates = new HashMap<>();
        if (args.has("app_name")) updates.put("my_app_name", args.get("app_name").getAsString());
        if (args.has("package_name")) updates.put("my_sc_pkg_name", args.get("package_name").getAsString());
        if (args.has("version_code")) updates.put("sc_ver_code", args.get("version_code").getAsString());
        if (args.has("version_name")) updates.put("sc_ver_name", args.get("version_name").getAsString());
        if (args.has("color_primary")) updates.put("color_primary", parseColor(args.get("color_primary").getAsString()));
        if (args.has("color_primary_dark")) updates.put("color_primary_dark", parseColor(args.get("color_primary_dark").getAsString()));
        if (args.has("color_accent")) updates.put("color_accent", parseColor(args.get("color_accent").getAsString()));

        // Keep existing values
        updates.put("my_ws_name", project.getOrDefault("my_ws_name", ""));
        updates.put("sketchware_ver", project.getOrDefault("sketchware_ver", 150));
        updates.put("color_control_highlight", project.getOrDefault("color_control_highlight", 0));
        updates.put("color_control_normal", project.getOrDefault("color_control_normal", 0));

        // Merge
        for (Map.Entry<String, Object> e : updates.entrySet()) {
            project.put(e.getKey(), e.getValue());
        }

        lC.a(scId, project);

        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        result.addProperty("message", "Project configuration updated");
        return result.toString();
    }

    private JsonObject projectToJson(HashMap<String, Object> project) {
        JsonObject obj = new JsonObject();
        obj.addProperty("sc_id", yB.c(project, "sc_id"));
        obj.addProperty("app_name", yB.c(project, "my_app_name"));
        obj.addProperty("package_name", yB.c(project, "my_sc_pkg_name"));
        obj.addProperty("project_name", yB.c(project, "my_ws_name"));
        obj.addProperty("version_code", yB.c(project, "sc_ver_code"));
        obj.addProperty("version_name", yB.c(project, "sc_ver_name"));
        return obj;
    }

    private int parseColor(String hex) {
        try {
            if (hex.startsWith("#")) hex = hex.substring(1);
            return (int) Long.parseLong(hex, 16);
        } catch (Exception e) {
            return 0xFF2196F3;
        }
    }

    private void copyDirectory(File source, File dest) {
        if (!source.isDirectory()) return;
        dest.mkdirs();
        File[] files = source.listFiles();
        if (files == null) return;
        for (File f : files) {
            File destFile = new File(dest, f.getName());
            if (f.isDirectory()) {
                copyDirectory(f, destFile);
            } else {
                try {
                    String content = FileUtil.readFile(f.getAbsolutePath());
                    FileUtil.writeFile(destFile.getAbsolutePath(), content);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public String getSystemPrompt() {
        List<String> projectIds = db.getWorkspaceProjectIds(workspaceId);
        StringBuilder sb = new StringBuilder();
        sb.append("You are an AI agent integrated into Sketchware Pro, a mobile app builder for Android. ");
        sb.append("You can create, edit, and manage Android app projects using the available tools. ");
        sb.append("You have access to the file system of Sketchware Pro projects and can read, write, edit, and delete project files. ");
        sb.append("\n\nIMPORTANT CONTEXT:\n");
        sb.append("- Sketchware Pro projects are stored with numeric IDs called 'sc_id'.\n");
        sb.append("- Each project has a data directory containing its files.\n");
        sb.append("- Java source files are in 'files/java/' subdirectory.\n");
        sb.append("- XML layout resources are in 'files/resource/' subdirectory.\n");
        sb.append("- The 'project' file at root contains project metadata (JSON).\n");
        sb.append("- Compile logs help debug build errors.\n");
        sb.append("\nCurrent workspace has ").append(projectIds.size()).append(" project(s)");
        if (!projectIds.isEmpty()) {
            sb.append(": ");
            for (int i = 0; i < projectIds.size(); i++) {
                if (i > 0) sb.append(", ");
                HashMap<String, Object> p = lC.b(projectIds.get(i));
                if (p != null) {
                    sb.append(yB.c(p, "my_app_name")).append(" (ID: ").append(projectIds.get(i)).append(")");
                } else {
                    sb.append("ID: ").append(projectIds.get(i));
                }
            }
        }
        sb.append("\n\nAlways use tools to interact with projects. Be precise and careful when editing files. ");
        sb.append("When creating projects, use proper Android conventions. ");
        sb.append("When asked to create an app, create the project first, then add necessary files step by step.");
        return sb.toString();
    }
}
