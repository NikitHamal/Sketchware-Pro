package pro.sketchware.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import a.a.a.wq;
import a.a.a.yB;
import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import mod.hey.studios.build.BuildSettings;
import mod.pranav.dependency.resolver.DependencyResolver;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;

public class AgentToolExecutor {
    private final AgentStorage storage;
    private final AgentWorkspace workspace;
    private final AgentProjectManager projectManager;
    private final Gson gson = new Gson();

    public AgentToolExecutor(AgentStorage storage, AgentWorkspace workspace, AgentProjectManager projectManager) {
        this.storage = storage;
        this.workspace = workspace;
        this.projectManager = projectManager;
    }

    public JsonArray describeTools() {
        JsonArray tools = new JsonArray();
        tools.add(tool("list_workspace_projects", "List all projects currently attached to this workspace."));
        tools.add(tool("create_project", "Create a new Sketchware project and attach it to this workspace. Args: project_name, app_name, package_name."));
        tools.add(tool("delete_project", "Delete a project from disk. Args: sc_id."));
        tools.add(tool("duplicate_project", "Duplicate an existing project. Args: source_sc_id, new_project_name."));
        tools.add(tool("list_project_files", "List files in a project. Args: sc_id, path(optional), recursive(optional), max_results(optional)."));
        tools.add(tool("read_project_file", "Read a project file content. Args: sc_id, path, max_chars(optional)."));
        tools.add(tool("write_project_file", "Create or overwrite a project file. Args: sc_id, path, content."));
        tools.add(tool("delete_project_file", "Delete a file or directory in a project. Args: sc_id, path."));
        tools.add(tool("copy_project_file", "Copy file or directory in a project. Args: sc_id, from_path, to_path."));
        tools.add(tool("move_project_file", "Move file or directory in a project. Args: sc_id, from_path, to_path."));
        tools.add(tool("create_project_directory", "Create a directory in a project. Args: sc_id, path."));
        tools.add(tool("get_compile_logs", "Read last compile log from a project. Args: sc_id, max_chars(optional)."));
        tools.add(tool("download_library", "Download and attach a Maven library to project local libraries. Args: sc_id, dependency(group:artifact:version), skip_dependencies(optional)."));
        return tools;
    }

    public JsonObject execute(String name, JsonObject arguments) {
        try {
            return switch (name) {
                case "list_workspace_projects" -> listWorkspaceProjects();
                case "create_project" -> createProject(arguments);
                case "delete_project" -> deleteProject(arguments);
                case "duplicate_project" -> duplicateProject(arguments);
                case "list_project_files" -> listProjectFiles(arguments);
                case "read_project_file" -> readProjectFile(arguments);
                case "write_project_file" -> writeProjectFile(arguments);
                case "delete_project_file" -> deleteProjectFile(arguments);
                case "copy_project_file" -> copyProjectFile(arguments);
                case "move_project_file" -> moveProjectFile(arguments);
                case "create_project_directory" -> createProjectDirectory(arguments);
                case "get_compile_logs" -> getCompileLogs(arguments);
                case "download_library" -> downloadLibrary(arguments);
                default -> error("Unknown tool: " + name);
            };
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    private JsonObject listWorkspaceProjects() {
        JsonArray projects = new JsonArray();
        for (String scId : workspace.projectIds) {
            HashMap<String, Object> project = projectManager.getProject(scId);
            if (project == null) {
                continue;
            }
            projects.add(projectToJson(project));
        }
        JsonObject result = ok("Workspace project list loaded");
        result.add("projects", projects);
        return result;
    }

    private JsonObject createProject(JsonObject arguments) {
        String projectName = getString(arguments, "project_name");
        String appName = getString(arguments, "app_name");
        String packageName = getString(arguments, "package_name");
        HashMap<String, Object> created = projectManager.createProject(projectName, appName, packageName);
        String scId = yB.c(created, "sc_id");
        if (!workspace.projectIds.contains(scId)) {
            workspace.projectIds.add(scId);
            storage.updateWorkspace(workspace);
        }

        JsonObject result = ok("Project created");
        result.add("project", projectToJson(created));
        return result;
    }

    private JsonObject deleteProject(JsonObject arguments) {
        String scId = required(arguments, "sc_id");
        requireProjectInWorkspace(scId);
        projectManager.deleteProject(scId);
        workspace.projectIds.remove(scId);
        storage.updateWorkspace(workspace);
        return ok("Project deleted: " + scId);
    }

    private JsonObject duplicateProject(JsonObject arguments) throws IOException {
        String sourceScId = required(arguments, "source_sc_id");
        requireProjectInWorkspace(sourceScId);
        String newProjectName = getString(arguments, "new_project_name");
        HashMap<String, Object> duplicated = projectManager.duplicateProject(sourceScId, newProjectName);
        String newScId = yB.c(duplicated, "sc_id");
        if (!workspace.projectIds.contains(newScId)) {
            workspace.projectIds.add(newScId);
            storage.updateWorkspace(workspace);
        }

        JsonObject result = ok("Project duplicated");
        result.add("project", projectToJson(duplicated));
        return result;
    }

    private JsonObject listProjectFiles(JsonObject arguments) throws IOException {
        String scId = required(arguments, "sc_id");
        requireProjectInWorkspace(scId);

        String path = getString(arguments, "path");
        if (path == null || path.isEmpty()) {
            path = "files";
        }
        boolean recursive = getBoolean(arguments, "recursive", true);
        int maxResults = getInt(arguments, "max_results", 300);

        File target = resolveInProject(scId, path);
        if (!target.exists()) {
            return error("Path does not exist: " + path);
        }

        File root = new File(wq.b(scId));
        JsonArray files = new JsonArray();

        if (target.isFile()) {
            files.add(relativize(root, target));
        } else if (recursive) {
            ArrayDeque<File> queue = new ArrayDeque<>();
            queue.add(target);
            while (!queue.isEmpty() && files.size() < maxResults) {
                File current = queue.removeFirst();
                File[] children = current.listFiles();
                if (children == null) {
                    continue;
                }
                for (File child : children) {
                    files.add(relativize(root, child));
                    if (child.isDirectory()) {
                        queue.add(child);
                    }
                    if (files.size() >= maxResults) {
                        break;
                    }
                }
            }
        } else {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) {
                    files.add(relativize(root, child));
                    if (files.size() >= maxResults) {
                        break;
                    }
                }
            }
        }

        JsonObject result = ok("Project file list loaded");
        result.addProperty("sc_id", scId);
        result.add("files", files);
        return result;
    }

    private JsonObject readProjectFile(JsonObject arguments) throws IOException {
        String scId = required(arguments, "sc_id");
        requireProjectInWorkspace(scId);
        String path = required(arguments, "path");
        int maxChars = getInt(arguments, "max_chars", 120000);

        File file = resolveInProject(scId, path);
        if (!file.exists() || file.isDirectory()) {
            return error("File does not exist: " + path);
        }

        String content = FileUtil.readFile(file.getAbsolutePath());
        boolean truncated = false;
        if (content.length() > maxChars) {
            content = content.substring(0, maxChars);
            truncated = true;
        }

        JsonObject result = ok("Project file read");
        result.addProperty("sc_id", scId);
        result.addProperty("path", path);
        result.addProperty("content", content);
        result.addProperty("truncated", truncated);
        return result;
    }

    private JsonObject writeProjectFile(JsonObject arguments) throws IOException {
        String scId = required(arguments, "sc_id");
        requireProjectInWorkspace(scId);
        String path = required(arguments, "path");
        String content = required(arguments, "content");
        File file = resolveInProject(scId, path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        FileUtil.writeFile(file.getAbsolutePath(), content);

        JsonObject result = ok("Project file written");
        result.addProperty("sc_id", scId);
        result.addProperty("path", path);
        result.addProperty("size", content.length());
        return result;
    }

    private JsonObject deleteProjectFile(JsonObject arguments) throws IOException {
        String scId = required(arguments, "sc_id");
        requireProjectInWorkspace(scId);
        String path = required(arguments, "path");
        File file = resolveInProject(scId, path);
        if (!file.exists()) {
            return error("Path does not exist: " + path);
        }
        FileUtil.deleteFile(file.getAbsolutePath());
        return ok("Project path deleted: " + path);
    }

    private JsonObject copyProjectFile(JsonObject arguments) throws IOException {
        String scId = required(arguments, "sc_id");
        requireProjectInWorkspace(scId);
        String fromPath = required(arguments, "from_path");
        String toPath = required(arguments, "to_path");

        File from = resolveInProject(scId, fromPath);
        File to = resolveInProject(scId, toPath);
        if (!from.exists()) {
            return error("Source does not exist: " + fromPath);
        }

        if (from.isDirectory()) {
            if (to.exists()) {
                FileUtil.deleteFile(to.getAbsolutePath());
            }
            FileUtil.copyDirectory(from, to);
        } else {
            File parent = to.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            FileUtil.copyFile(from.getAbsolutePath(), to.getAbsolutePath());
        }
        return ok("Project path copied");
    }

    private JsonObject moveProjectFile(JsonObject arguments) throws IOException {
        String scId = required(arguments, "sc_id");
        requireProjectInWorkspace(scId);
        String fromPath = required(arguments, "from_path");
        String toPath = required(arguments, "to_path");

        File from = resolveInProject(scId, fromPath);
        File to = resolveInProject(scId, toPath);
        if (!from.exists()) {
            return error("Source does not exist: " + fromPath);
        }

        if (from.isDirectory()) {
            if (to.exists()) {
                FileUtil.deleteFile(to.getAbsolutePath());
            }
            FileUtil.copyDirectory(from, to);
            FileUtil.deleteFile(from.getAbsolutePath());
        } else {
            File parent = to.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            FileUtil.moveFile(from.getAbsolutePath(), to.getAbsolutePath());
        }
        return ok("Project path moved");
    }

    private JsonObject createProjectDirectory(JsonObject arguments) throws IOException {
        String scId = required(arguments, "sc_id");
        requireProjectInWorkspace(scId);
        String path = required(arguments, "path");
        File directory = resolveInProject(scId, path);
        FileUtil.makeDir(directory.getAbsolutePath());
        return ok("Directory created: " + path);
    }

    private JsonObject getCompileLogs(JsonObject arguments) {
        String scId = required(arguments, "sc_id");
        requireProjectInWorkspace(scId);
        int maxChars = getInt(arguments, "max_chars", 120000);
        String path = FilePathUtil.getLastCompileLogPath(scId);
        if (!FileUtil.isExistFile(path)) {
            return error("No compile log exists for project: " + scId);
        }

        String logs = FileUtil.readFile(path);
        boolean truncated = false;
        if (logs.length() > maxChars) {
            logs = logs.substring(0, maxChars);
            truncated = true;
        }

        JsonObject result = ok("Compile logs loaded");
        result.addProperty("sc_id", scId);
        result.addProperty("path", path);
        result.addProperty("logs", logs);
        result.addProperty("truncated", truncated);
        return result;
    }

    private JsonObject downloadLibrary(JsonObject arguments) {
        String scId = required(arguments, "sc_id");
        requireProjectInWorkspace(scId);
        String dependency = required(arguments, "dependency");
        boolean skipDependencies = getBoolean(arguments, "skip_dependencies", false);

        String[] split = dependency.split(":");
        if (split.length != 3) {
            return error("Invalid dependency format. Use group:artifact:version");
        }

        BuildSettings buildSettings = new BuildSettings(scId);
        DependencyResolver resolver = new DependencyResolver(split[0], split[1], split[2], skipDependencies, buildSettings);
        ArrayList<String> resolvedArtifacts = new ArrayList<>();
        AtomicReference<String> error = new AtomicReference<>(null);

        resolver.resolveDependency(new DependencyResolver.DependencyResolverCallback() {
            @Override
            public void onArtifactNotFound(org.cosmic.ide.dependency.resolver.api.Artifact artifact) {
                error.set("Artifact was not found: " + artifact);
            }

            @Override
            public void onDependenciesNotFound(org.cosmic.ide.dependency.resolver.api.Artifact artifact) {
                error.set("Dependencies were not found for: " + artifact);
            }

            @Override
            public void onDownloadError(org.cosmic.ide.dependency.resolver.api.Artifact artifact, Throwable throwable) {
                error.set("Failed to download " + artifact + ": " + throwable.getMessage());
            }

            @Override
            public void dexingFailed(org.cosmic.ide.dependency.resolver.api.Artifact artifact, Exception e) {
                error.set("Dexing failed for " + artifact + ": " + e.getMessage());
            }

            @Override
            public void onTaskCompleted(List<String> artifacts) {
                resolvedArtifacts.addAll(artifacts);
            }
        });

        if (error.get() != null) {
            return error(error.get());
        }

        ArrayList<HashMap<String, Object>> localLibraries = LocalLibrariesUtil.getLocalLibraries(scId);
        Set<String> existingNames = new HashSet<>();
        for (HashMap<String, Object> library : localLibraries) {
            existingNames.add(Objects.toString(library.get("name"), ""));
        }
        for (String name : resolvedArtifacts) {
            if (!existingNames.contains(name)) {
                localLibraries.add(LocalLibrariesUtil.createLibraryMap(name, dependency));
            }
        }
        LocalLibrariesUtil.rewriteLocalLibFile(scId, gson.toJson(localLibraries));

        JsonObject result = ok("Dependency resolved and attached to project");
        result.addProperty("sc_id", scId);
        result.addProperty("dependency", dependency);
        result.addProperty("resolved_count", resolvedArtifacts.size());
        return result;
    }

    private File resolveInProject(String scId, String relativePath) throws IOException {
        File root = new File(wq.b(scId));
        String sanitizedPath = relativePath.replace('\\', '/');
        while (sanitizedPath.startsWith("/")) {
            sanitizedPath = sanitizedPath.substring(1);
        }
        File target = new File(root, sanitizedPath);
        String rootCanonical = root.getCanonicalPath();
        String targetCanonical = target.getCanonicalPath();
        if (!targetCanonical.equals(rootCanonical) && !targetCanonical.startsWith(rootCanonical + File.separator)) {
            throw new IOException("Path escapes project root");
        }
        return target;
    }

    private String relativize(File root, File file) throws IOException {
        String rootPath = root.getCanonicalPath();
        String filePath = file.getCanonicalPath();
        if (filePath.equals(rootPath)) {
            return ".";
        }
        return filePath.substring(rootPath.length() + 1);
    }

    private void requireProjectInWorkspace(String scId) {
        if (!workspace.projectIds.contains(scId)) {
            throw new IllegalArgumentException("Project is not attached to this workspace: " + scId);
        }
    }

    private JsonObject tool(String name, String description) {
        JsonObject tool = new JsonObject();
        tool.addProperty("name", name);
        tool.addProperty("description", description);
        return tool;
    }

    private JsonObject ok(String message) {
        JsonObject object = new JsonObject();
        object.addProperty("success", true);
        object.addProperty("message", message);
        return object;
    }

    private JsonObject error(String message) {
        JsonObject object = new JsonObject();
        object.addProperty("success", false);
        object.addProperty("error", message);
        return object;
    }

    private JsonObject projectToJson(HashMap<String, Object> project) {
        JsonObject object = new JsonObject();
        object.addProperty("sc_id", yB.c(project, "sc_id"));
        object.addProperty("workspace_name", yB.c(project, "my_ws_name"));
        object.addProperty("app_name", yB.c(project, "my_app_name"));
        object.addProperty("package_name", yB.c(project, "my_sc_pkg_name"));
        object.addProperty("version_code", yB.c(project, "sc_ver_code"));
        object.addProperty("version_name", yB.c(project, "sc_ver_name"));
        return object;
    }

    private String required(JsonObject object, String key) {
        String value = getString(object, key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required argument: " + key);
        }
        return value;
    }

    private String getString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        return object.get(key).getAsString();
    }

    private boolean getBoolean(JsonObject object, String key, boolean fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).getAsBoolean();
    }

    private int getInt(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).getAsInt();
    }
}
