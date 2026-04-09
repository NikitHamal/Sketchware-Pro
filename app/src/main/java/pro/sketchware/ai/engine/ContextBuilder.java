package pro.sketchware.ai.engine;

import android.os.Environment;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ContextBuilder {

    private static final String SKETCHWARE_DIR = ".sketchware";

    public static String buildProjectContext(List<String> projectIds) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Project Context\n\n");

        for (String scId : projectIds) {
            String projectInfo = getProjectSummary(scId);
            if (projectInfo != null) {
                sb.append("## Project ").append(scId).append("\n");
                sb.append(projectInfo).append("\n\n");
            }
        }

        return sb.toString();
    }

    public static String getProjectSummary(String scId) {
        File projectDir = getProjectDataDir(scId);
        if (!projectDir.exists()) return null;

        StringBuilder sb = new StringBuilder();

        File projectFile = new File(projectDir, "project");
        if (projectFile.exists()) {
            String content = readFile(projectFile);
            if (content != null) {
                try {
                    JsonObject proj = JsonParser.parseString(content).getAsJsonObject();
                    sb.append("- Name: ").append(getStr(proj, "my_app_name")).append("\n");
                    sb.append("- Package: ").append(getStr(proj, "my_sc_pkg_name")).append("\n");
                    sb.append("- Version: ").append(getStr(proj, "sc_ver_name")).append("\n");
                } catch (Exception e) {
                    sb.append("- (Could not parse project metadata)\n");
                }
            }
        }

        File fileFile = new File(projectDir, "file");
        if (fileFile.exists()) {
            String content = readFile(fileFile);
            if (content != null) {
                try {
                    JsonArray activities = JsonParser.parseString(content).getAsJsonArray();
                    sb.append("- Activities: ");
                    List<String> names = new ArrayList<>();
                    for (JsonElement el : activities) {
                        JsonObject obj = el.getAsJsonObject();
                        names.add(getStr(obj, "fileName"));
                    }
                    sb.append(String.join(", ", names)).append("\n");
                } catch (Exception e) {
                    sb.append("- (Could not parse activities)\n");
                }
            }
        }

        File libraryFile = new File(projectDir, "library");
        if (libraryFile.exists()) {
            String content = readFile(libraryFile);
            if (content != null && !content.trim().isEmpty()) {
                sb.append("- Has custom libraries\n");
            }
        }

        return sb.toString();
    }

    public static File getSketchwareDir() {
        return new File(Environment.getExternalStorageDirectory(), SKETCHWARE_DIR);
    }

    public static File getProjectDataDir(String scId) {
        return new File(getSketchwareDir(), "data/" + scId);
    }

    public static File getProjectSourceDir(String scId) {
        return new File(getSketchwareDir(), "mysc/" + scId);
    }

    public static String readFile(File file) {
        if (!file.exists()) return null;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString().trim();
        } catch (IOException e) {
            return null;
        }
    }

    public static void writeFile(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
            writer.write(content);
        }
    }

    private static String getStr(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return "";
    }
}
