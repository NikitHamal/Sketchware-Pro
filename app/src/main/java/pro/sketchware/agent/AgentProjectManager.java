package pro.sketchware.agent;

import static mod.hey.studios.util.ProjectFile.COLOR_ACCENT;
import static mod.hey.studios.util.ProjectFile.COLOR_CONTROL_HIGHLIGHT;
import static mod.hey.studios.util.ProjectFile.COLOR_CONTROL_NORMAL;
import static mod.hey.studios.util.ProjectFile.COLOR_PRIMARY;
import static mod.hey.studios.util.ProjectFile.COLOR_PRIMARY_DARK;
import static mod.hey.studios.util.ProjectFile.getDefaultColor;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import a.a.a.GB;
import a.a.a.lC;
import a.a.a.nB;
import a.a.a.wq;
import a.a.a.yB;
import mod.hey.studios.project.ProjectSettings;
import pro.sketchware.utility.FileUtil;

public class AgentProjectManager {

    private static final int DEFAULT_READ_LENGTH = 12000;

    private final Context context;
    private final AgentRepository repository;

    public AgentProjectManager(@NonNull Context context, @NonNull AgentRepository repository) {
        this.context = context.getApplicationContext();
        this.repository = repository;
    }

    @NonNull
    public List<HashMap<String, Object>> listWorkspaceProjects(@NonNull AgentRepository.Workspace workspace) {
        ArrayList<HashMap<String, Object>> projects = new ArrayList<>();
        ArrayList<String> validProjectIds = new ArrayList<>();
        for (String projectId : workspace.projectIds) {
            HashMap<String, Object> project = lC.b(projectId);
            if (project != null) {
                projects.add(project);
                validProjectIds.add(projectId);
            }
        }
        if (validProjectIds.size() != workspace.projectIds.size()) {
            workspace.projectIds.clear();
            workspace.projectIds.addAll(validProjectIds);
            workspace.updatedAt = System.currentTimeMillis();
            repository.saveWorkspace(workspace);
        }
        return projects;
    }

    @NonNull
    public HashMap<String, Object> createProject(@NonNull AgentRepository.Workspace workspace, @NonNull String projectName,
                                                 @Nullable String appName, @Nullable String packageName,
                                                 @Nullable String versionName, @Nullable String versionCode) {
        String scId = lC.b();
        HashMap<String, Object> metadata = new HashMap<>();
        metadata.put("sc_id", scId);
        metadata.put("proj_type", 1);
        metadata.put("my_sc_pkg_name", sanitizePackageName(packageName, projectName));
        metadata.put("my_ws_name", sanitizeProjectName(projectName));
        metadata.put("my_app_name", TextUtils.isEmpty(appName) ? sanitizeProjectName(projectName) : appName.trim());
        metadata.put("my_sc_reg_dt", new nB().a("yyyyMMddHHmmss"));
        metadata.put("custom_icon", false);
        metadata.put("isIconAdaptive", false);
        metadata.put("sc_ver_code", TextUtils.isEmpty(versionCode) ? "1" : versionCode.trim());
        metadata.put("sc_ver_name", TextUtils.isEmpty(versionName) ? "1.0" : versionName.trim());
        metadata.put("sketchware_ver", GB.d(context));
        metadata.put(COLOR_ACCENT, getDefaultColor(COLOR_ACCENT));
        metadata.put(COLOR_PRIMARY, getDefaultColor(COLOR_PRIMARY));
        metadata.put(COLOR_PRIMARY_DARK, getDefaultColor(COLOR_PRIMARY_DARK));
        metadata.put(COLOR_CONTROL_HIGHLIGHT, getDefaultColor(COLOR_CONTROL_HIGHLIGHT));
        metadata.put(COLOR_CONTROL_NORMAL, getDefaultColor(COLOR_CONTROL_NORMAL));
        lC.a(scId, metadata);

        FileUtil.makeDir(wq.b(scId));
        FileUtil.makeDir(new File(wq.b(scId), "files").getAbsolutePath());
        FileUtil.makeDir(new File(wq.b(scId), "files/java").getAbsolutePath());
        FileUtil.makeDir(new File(wq.b(scId), "files/resource").getAbsolutePath());
        FileUtil.makeDir(new File(wq.b(scId), "files/resource/layout").getAbsolutePath());
        FileUtil.makeDir(new File(wq.b(scId), "files/resource/values").getAbsolutePath());
        FileUtil.makeDir(new File(wq.b(scId), "files/assets").getAbsolutePath());
        FileUtil.makeDir(new File(wq.e(), scId).getAbsolutePath());
        FileUtil.makeDir(new File(wq.g(), scId).getAbsolutePath());
        FileUtil.makeDir(new File(wq.t(), scId).getAbsolutePath());
        FileUtil.makeDir(new File(wq.d(), scId).getAbsolutePath());
        FileUtil.makeDir(new File(wq.a(scId)).getAbsolutePath());

        wq.a(context, scId);
        ProjectSettings projectSettings = new ProjectSettings(scId);
        projectSettings.setValue(ProjectSettings.SETTING_NEW_XML_COMMAND, ProjectSettings.SETTING_GENERIC_VALUE_TRUE);
        projectSettings.setValue(ProjectSettings.SETTING_ENABLE_VIEWBINDING, ProjectSettings.SETTING_GENERIC_VALUE_TRUE);

        workspace.projectIds.add(scId);
        workspace.updatedAt = System.currentTimeMillis();
        repository.saveWorkspace(workspace);

        return metadata;
    }

    @NonNull
    public HashMap<String, Object> duplicateProject(@NonNull AgentRepository.Workspace workspace, @NonNull String sourceProjectId,
                                                    @NonNull String newProjectName, @Nullable String newAppName) throws IOException {
        HashMap<String, Object> sourceMetadata = lC.b(sourceProjectId);
        if (sourceMetadata == null) {
            throw new IllegalArgumentException("Project " + sourceProjectId + " was not found");
        }

        String newProjectId = lC.b();
        HashMap<String, Object> newMetadata = new HashMap<>(sourceMetadata);
        newMetadata.put("sc_id", newProjectId);
        newMetadata.put("my_ws_name", sanitizeProjectName(newProjectName));
        if (!TextUtils.isEmpty(newAppName)) {
            newMetadata.put("my_app_name", newAppName.trim());
        }
        newMetadata.put("my_sc_reg_dt", new nB().a("yyyyMMddHHmmss"));

        copyIfExists(new File(wq.b(sourceProjectId)), new File(wq.b(newProjectId)));
        copyIfExists(new File(wq.e(), sourceProjectId), new File(wq.e(), newProjectId));
        copyIfExists(new File(wq.g(), sourceProjectId), new File(wq.g(), newProjectId));
        copyIfExists(new File(wq.t(), sourceProjectId), new File(wq.t(), newProjectId));
        copyIfExists(new File(wq.d(), sourceProjectId), new File(wq.d(), newProjectId));
        copyIfExists(new File(wq.a(sourceProjectId)), new File(wq.a(newProjectId)));

        lC.a(newProjectId, newMetadata);
        updateProjectResources(newProjectId, newMetadata);
        wq.a(context, newProjectId);

        workspace.projectIds.add(newProjectId);
        workspace.updatedAt = System.currentTimeMillis();
        repository.saveWorkspace(workspace);

        return newMetadata;
    }

    public void deleteProject(@NonNull AgentRepository.Workspace workspace, @NonNull String projectId) {
        lC.a(context, projectId);
        long updatedAt = System.currentTimeMillis();
        boolean removedFromAnyWorkspace = false;
        for (AgentRepository.Workspace candidate : repository.getWorkspaces()) {
            if (candidate.projectIds.remove(projectId)) {
                candidate.updatedAt = updatedAt;
                repository.saveWorkspace(candidate);
                if (candidate.id.equals(workspace.id)) {
                    workspace.projectIds.remove(projectId);
                    workspace.updatedAt = updatedAt;
                }
                removedFromAnyWorkspace = true;
            }
        }
        if (!removedFromAnyWorkspace) {
            workspace.projectIds.remove(projectId);
            workspace.updatedAt = updatedAt;
            repository.saveWorkspace(workspace);
        }
    }

    @NonNull
    public LinkedHashMap<String, Object> listFiles(@NonNull String projectId, @Nullable String directory,
                                                   boolean recursive) throws IOException {
        File root = resolveProjectPath(projectId, directory);
        if (!root.exists()) {
            throw new IOException("Path does not exist: " + root.getAbsolutePath());
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        if (root.isFile()) {
            result.put("path", toRelativePath(projectId, root));
            result.put("type", "file");
            result.put("size", root.length());
            return result;
        }

        ArrayList<LinkedHashMap<String, Object>> children = new ArrayList<>();
        listChildren(projectId, root, recursive, children);
        result.put("path", toRelativePath(projectId, root));
        result.put("type", "directory");
        result.put("children", children);
        return result;
    }

    @NonNull
    public LinkedHashMap<String, Object> readFile(@NonNull String projectId, @NonNull String relativePath,
                                                  @Nullable Integer offset, @Nullable Integer length) throws IOException {
        File file = resolveProjectPath(projectId, relativePath);
        if (!file.isFile()) {
            throw new IOException("Not a file: " + relativePath);
        }
        String content = FileUtil.readFile(file.getAbsolutePath());
        int safeOffset = Math.max(offset == null ? 0 : offset, 0);
        int safeLength = Math.max(length == null ? DEFAULT_READ_LENGTH : length, 1);
        int end = Math.min(content.length(), safeOffset + safeLength);
        if (safeOffset > content.length()) {
            safeOffset = content.length();
        }

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("path", relativePath);
        result.put("size", content.length());
        result.put("offset", safeOffset);
        result.put("length", end - safeOffset);
        result.put("content", content.substring(safeOffset, end));
        result.put("has_more", end < content.length());
        return result;
    }

    @NonNull
    public LinkedHashMap<String, Object> writeFile(@NonNull String projectId, @NonNull String relativePath,
                                                   @NonNull String content) throws IOException {
        File file = resolveProjectPath(projectId, relativePath);
        File parent = file.getParentFile();
        if (parent != null) {
            FileUtil.makeDir(parent.getAbsolutePath());
        }
        FileUtil.writeFile(file.getAbsolutePath(), content);

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("path", relativePath);
        result.put("written_bytes", content.length());
        return result;
    }

    @NonNull
    public LinkedHashMap<String, Object> deleteFile(@NonNull String projectId, @NonNull String relativePath) throws IOException {
        File file = resolveProjectPath(projectId, relativePath);
        if (!file.exists()) {
            throw new IOException("Path does not exist: " + relativePath);
        }
        FileUtil.deleteFile(file.getAbsolutePath());

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("path", relativePath);
        result.put("deleted", true);
        return result;
    }

    @NonNull
    public LinkedHashMap<String, Object> moveFile(@NonNull String projectId, @NonNull String fromPath,
                                                  @NonNull String toPath) throws IOException {
        File source = resolveProjectPath(projectId, fromPath);
        File target = resolveProjectPath(projectId, toPath);
        File parent = target.getParentFile();
        if (parent != null) {
            FileUtil.makeDir(parent.getAbsolutePath());
        }
        if (source.isDirectory()) {
            FileUtil.copyDirectory(source, target);
            FileUtil.deleteFile(source.getAbsolutePath());
        } else {
            FileUtil.moveFile(source.getAbsolutePath(), target.getAbsolutePath());
        }

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("from", fromPath);
        result.put("to", toPath);
        return result;
    }

    @NonNull
    public LinkedHashMap<String, Object> readCompileLog(@NonNull String projectId, @Nullable Integer offset,
                                                        @Nullable Integer length) throws IOException {
        return readFile(projectId, "compile_log", offset, length);
    }

    @NonNull
    public File resolveProjectPath(@NonNull String projectId, @Nullable String relativePath) throws IOException {
        File root = new File(wq.b(projectId));
        if (!root.exists()) {
            throw new IOException("Project data folder was not found for " + projectId);
        }

        File target = TextUtils.isEmpty(relativePath) ? root : new File(root, relativePath);
        String canonicalRoot = root.getCanonicalPath();
        String canonicalTarget = target.getCanonicalPath();
        if (!canonicalTarget.equals(canonicalRoot) && !canonicalTarget.startsWith(canonicalRoot + File.separator)) {
            throw new IOException("Path escapes the project sandbox");
        }
        return target;
    }

    @NonNull
    private String toRelativePath(@NonNull String projectId, @NonNull File file) throws IOException {
        File root = new File(wq.b(projectId));
        String canonicalRoot = root.getCanonicalPath();
        String canonicalFile = file.getCanonicalPath();
        if (canonicalRoot.equals(canonicalFile)) {
            return ".";
        }
        return canonicalFile.substring(canonicalRoot.length() + 1).replace(File.separatorChar, '/');
    }

    private void listChildren(@NonNull String projectId, @NonNull File directory, boolean recursive,
                              @NonNull List<LinkedHashMap<String, Object>> output) throws IOException {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File child : files) {
            LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
            entry.put("path", toRelativePath(projectId, child));
            entry.put("name", child.getName());
            entry.put("type", child.isDirectory() ? "directory" : "file");
            if (!child.isDirectory()) {
                entry.put("size", child.length());
            }
            output.add(entry);
            if (recursive && child.isDirectory()) {
                listChildren(projectId, child, true, output);
            }
        }
    }

    private void copyIfExists(@NonNull File source, @NonNull File target) throws IOException {
        if (source.exists()) {
            FileUtil.copyDirectory(source, target);
        }
    }

    private void updateProjectResources(@NonNull String scId, @NonNull HashMap<String, Object> metadata) {
        String valuesDir = new File(new File(wq.b(scId), "files/resource"), "values").getAbsolutePath();
        String stringsPath = valuesDir + File.separator + "strings.xml";
        String colorsPath = valuesDir + File.separator + "colors.xml";

        if (FileUtil.isExistFile(stringsPath)) {
            String xml = FileUtil.readFile(stringsPath);
            xml = xml.replaceAll("(<string\\s+name=\"app_name\">)(.*?)(</string>)",
                    "$1" + java.util.regex.Matcher.quoteReplacement(yB.c(metadata, "my_app_name")) + "$3");
            FileUtil.writeFile(stringsPath, xml);
        }

        if (FileUtil.isExistFile(colorsPath)) {
            String xml = FileUtil.readFile(colorsPath);
            xml = replaceColor(xml, "colorAccent", yB.a(metadata, COLOR_ACCENT, getDefaultColor(COLOR_ACCENT)));
            xml = replaceColor(xml, "colorPrimary", yB.a(metadata, COLOR_PRIMARY, getDefaultColor(COLOR_PRIMARY)));
            xml = replaceColor(xml, "colorPrimaryDark", yB.a(metadata, COLOR_PRIMARY_DARK, getDefaultColor(COLOR_PRIMARY_DARK)));
            xml = replaceColor(xml, "colorControlHighlight", yB.a(metadata, COLOR_CONTROL_HIGHLIGHT, getDefaultColor(COLOR_CONTROL_HIGHLIGHT)));
            xml = replaceColor(xml, "colorControlNormal", yB.a(metadata, COLOR_CONTROL_NORMAL, getDefaultColor(COLOR_CONTROL_NORMAL)));
            FileUtil.writeFile(colorsPath, xml);
        }
    }

    @NonNull
    private String replaceColor(@NonNull String xml, @NonNull String colorName, int color) {
        String hex = String.format(Locale.US, "#%06X", (0xFFFFFF & color));
        return xml.replaceAll("(<color\\s+name=\"" + colorName + "\">)(.*?)(</color>)", "$1" + hex + "$3");
    }

    @NonNull
    private String sanitizeProjectName(@Nullable String projectName) {
        String cleaned = TextUtils.isEmpty(projectName) ? lC.c() : projectName.trim().replaceAll("[^A-Za-z0-9 _.-]", " ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? lC.c() : cleaned;
    }

    @NonNull
    private String sanitizePackageName(@Nullable String packageName, @NonNull String projectName) {
        if (TextUtils.isEmpty(packageName)) {
            return "com.my." + sanitizeProjectName(projectName).toLowerCase(Locale.US).replace(' ', '_');
        }
        String normalized = packageName.trim().toLowerCase(Locale.US).replaceAll("[^a-z0-9_.]", "");
        while (normalized.contains("..")) {
            normalized = normalized.replace("..", ".");
        }
        normalized = normalized.replaceAll("^\\.+|\\.+$", "");
        if (TextUtils.isEmpty(normalized)) {
            return "com.my." + sanitizeProjectName(projectName).toLowerCase(Locale.US).replace(' ', '_');
        }
        return normalized;
    }
}
