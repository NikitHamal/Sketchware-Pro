package pro.sketchware.ai;

import static mod.hey.studios.util.ProjectFile.getDefaultColor;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;

import a.a.a.GB;
import a.a.a.lC;
import a.a.a.nB;
import a.a.a.oB;
import a.a.a.wq;
import a.a.a.yB;
import mod.hey.studios.project.ProjectSettings;
import mod.hey.studios.util.ProjectFile;
import pro.sketchware.utility.FileUtil;

public class AgentProjectManager {
    private final Context context;

    public AgentProjectManager(Context context) {
        this.context = context;
    }

    public ArrayList<HashMap<String, Object>> listProjects() {
        return lC.a();
    }

    public HashMap<String, Object> getProject(String scId) {
        return lC.b(scId);
    }

    public HashMap<String, Object> createProject(String workspaceName, String appName, String packageName) {
        String scId = lC.b();
        String resolvedWorkspaceName = isBlank(workspaceName) ? lC.c() : workspaceName.trim();
        String resolvedAppName = isBlank(appName) ? resolvedWorkspaceName : appName.trim();
        String resolvedPackageName = isBlank(packageName)
                ? buildDefaultPackageName(resolvedWorkspaceName)
                : packageName.trim();

        HashMap<String, Object> data = new HashMap<>();
        data.put("sc_id", scId);
        data.put("my_sc_pkg_name", resolvedPackageName);
        data.put("my_ws_name", resolvedWorkspaceName);
        data.put("my_app_name", resolvedAppName);
        data.put("my_sc_reg_dt", new nB().a("yyyyMMddHHmmss"));
        data.put("custom_icon", false);
        data.put("isIconAdaptive", false);
        data.put("sc_ver_code", "1");
        data.put("sc_ver_name", "1.0");
        data.put("sketchware_ver", GB.d(context));
        data.put(ProjectFile.COLOR_ACCENT, getDefaultColor(ProjectFile.COLOR_ACCENT));
        data.put(ProjectFile.COLOR_PRIMARY, getDefaultColor(ProjectFile.COLOR_PRIMARY));
        data.put(ProjectFile.COLOR_PRIMARY_DARK, getDefaultColor(ProjectFile.COLOR_PRIMARY_DARK));
        data.put(ProjectFile.COLOR_CONTROL_HIGHLIGHT, getDefaultColor(ProjectFile.COLOR_CONTROL_HIGHLIGHT));
        data.put(ProjectFile.COLOR_CONTROL_NORMAL, getDefaultColor(ProjectFile.COLOR_CONTROL_NORMAL));

        lC.a(scId, data);
        initializeProject(scId);
        updateProjectResourceFiles(scId, data);

        return lC.b(scId);
    }

    public HashMap<String, Object> duplicateProject(String sourceScId, String newWorkspaceName) throws IOException {
        HashMap<String, Object> source = lC.b(sourceScId);
        if (source == null) {
            throw new IOException("Source project was not found");
        }

        String newScId = lC.b();

        copyIfExists(new File(wq.b(sourceScId)), new File(wq.b(newScId)));
        copyIfExists(new File(wq.c(sourceScId)), new File(wq.c(newScId)));
        copyIfExists(new File(wq.e(), sourceScId), new File(wq.e(), newScId));
        copyIfExists(new File(wq.g(), sourceScId), new File(wq.g(), newScId));
        copyIfExists(new File(wq.t(), sourceScId), new File(wq.t(), newScId));
        copyIfExists(new File(wq.d(), sourceScId), new File(wq.d(), newScId));

        HashMap<String, Object> duplicated = new HashMap<>(source);
        duplicated.put("sc_id", newScId);

        String sourceWorkspaceName = yB.c(source, "my_ws_name");
        String sourceAppName = yB.c(source, "my_app_name");
        String sourcePackageName = yB.c(source, "my_sc_pkg_name");

        String workspaceName = isBlank(newWorkspaceName)
                ? sourceWorkspaceName + " Copy"
                : newWorkspaceName.trim();
        duplicated.put("my_ws_name", workspaceName);
        duplicated.put("my_app_name", sourceAppName + " Copy");
        duplicated.put("my_sc_pkg_name", buildCopyPackageName(sourcePackageName, newScId));

        lC.a(newScId, duplicated);
        updateProjectResourceFiles(newScId, duplicated);
        wq.a(context, newScId);
        ensureResourceDirectories(newScId);

        return lC.b(newScId);
    }

    public void deleteProject(String scId) {
        lC.a(context, scId);
    }

    private void initializeProject(String scId) {
        new oB().b(wq.b(scId));
        wq.a(context, scId);

        ProjectSettings projectSettings = new ProjectSettings(scId);
        projectSettings.setValue(ProjectSettings.SETTING_NEW_XML_COMMAND, ProjectSettings.SETTING_GENERIC_VALUE_TRUE);
        projectSettings.setValue(ProjectSettings.SETTING_ENABLE_VIEWBINDING, ProjectSettings.SETTING_GENERIC_VALUE_TRUE);

        ensureResourceDirectories(scId);
    }

    private void ensureResourceDirectories(String scId) {
        FileUtil.makeDir(new File(wq.e(), scId).getAbsolutePath());
        FileUtil.makeDir(new File(wq.g(), scId).getAbsolutePath());
        FileUtil.makeDir(new File(wq.t(), scId).getAbsolutePath());
        FileUtil.makeDir(new File(wq.d(), scId).getAbsolutePath());
    }

    private void updateProjectResourceFiles(String scId, HashMap<String, Object> data) {
        String baseDir = wq.b(scId) + "/files/resource/values/";
        String stringsFilePath = baseDir + "strings.xml";
        String colorsFilePath = baseDir + "colors.xml";
        String appName = Objects.toString(data.get("my_app_name"), "App");

        if (FileUtil.isExistFile(stringsFilePath)) {
            String xmlContent = FileUtil.readFile(stringsFilePath);
            xmlContent = xmlContent.replaceAll("(<string\\s+name=\"app_name\">)(.*?)(</string>)", "$1" + Matcher.quoteReplacement(appName) + "$3");
            FileUtil.writeFile(stringsFilePath, xmlContent);
        }

        if (FileUtil.isExistFile(colorsFilePath)) {
            String xmlContent = FileUtil.readFile(colorsFilePath);
            xmlContent = updateColor(xmlContent, "colorAccent", data);
            xmlContent = updateColor(xmlContent, "colorPrimary", data);
            xmlContent = updateColor(xmlContent, "colorPrimaryDark", data);
            xmlContent = updateColor(xmlContent, "colorControlHighlight", data);
            xmlContent = updateColor(xmlContent, "colorControlNormal", data);
            FileUtil.writeFile(colorsFilePath, xmlContent);
        }
    }

    private String updateColor(String xmlContent, String colorName, HashMap<String, Object> data) {
        String metadataKey = switch (colorName) {
            case "colorAccent" -> ProjectFile.COLOR_ACCENT;
            case "colorPrimary" -> ProjectFile.COLOR_PRIMARY;
            case "colorPrimaryDark" -> ProjectFile.COLOR_PRIMARY_DARK;
            case "colorControlHighlight" -> ProjectFile.COLOR_CONTROL_HIGHLIGHT;
            case "colorControlNormal" -> ProjectFile.COLOR_CONTROL_NORMAL;
            default -> "";
        };

        if (metadataKey.isEmpty()) {
            return xmlContent;
        }

        int color = getDefaultColor(metadataKey);
        Object value = data.get(metadataKey);
        if (value instanceof Number number) {
            color = number.intValue();
        }

        String hex = String.format(Locale.US, "#%06X", (0xFFFFFF & color));
        return xmlContent.replaceAll("(<color\\s+name=\"" + colorName + "\">)(.*?)(</color>)", "$1" + hex + "$3");
    }

    private String buildDefaultPackageName(String workspaceName) {
        String slug = workspaceName.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9_]+", "")
                .replaceAll("^[^a-z]+", "");
        if (slug.isEmpty()) {
            slug = "newproject";
        }
        return "com.my." + slug;
    }

    private String buildCopyPackageName(String sourcePackageName, String newScId) {
        if (isBlank(sourcePackageName)) {
            return "com.my.project" + newScId;
        }
        return sourcePackageName + ".copy" + newScId;
    }

    private void copyIfExists(File source, File destination) throws IOException {
        if (!source.exists()) {
            return;
        }
        if (source.isFile()) {
            File parent = destination.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            FileUtil.copyFile(source.getAbsolutePath(), destination.getAbsolutePath());
            return;
        }

        if (destination.exists()) {
            FileUtil.deleteFile(destination.getAbsolutePath());
        }
        FileUtil.copyDirectory(source, destination);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
