package pro.sketchware.ai.engine;

import android.content.Context;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides helper methods for reading and writing Sketchware project files,
 * and for building AI context strings from project metadata.
 */
public class ContextBuilder {

    private static final String SKETCHWARE_DIR = ".sketchware";

    // ── Context building ─────────────────────────────────────────────────────

    /**
     * Builds a context string summarising all supplied project IDs.
     *
     * @param projectIds list of Sketchware project IDs (sc_id strings)
     * @return a Markdown-formatted context string
     */
    @NonNull
    public static String buildProjectContext(@NonNull List<String> projectIds) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Project Context\n\n");
        for (String scId : projectIds) {
            String summary = getProjectSummary(scId);
            if (summary != null) {
                sb.append("## Project ").append(scId).append("\n");
                sb.append(summary).append("\n\n");
            }
        }
        return sb.toString();
    }

    /**
     * Returns a short human-readable summary of a single project, or null if not found.
     */
    @Nullable
    public static String getProjectSummary(@NonNull String scId) {
        File projectDir = getProjectDataDir(scId);
        if (!projectDir.exists()) return null;

        StringBuilder sb = new StringBuilder();

        // -- Project metadata --
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

        // -- Activities --
        File fileFile = new File(projectDir, "file");
        if (fileFile.exists()) {
            String content = readFile(fileFile);
            if (content != null) {
                try {
                    JsonArray activities = JsonParser.parseString(content).getAsJsonArray();
                    sb.append("- Activities: ");
                    List<String> names = new ArrayList<>();
                    for (JsonElement el : activities) {
                        if (el.isJsonObject()) {
                            names.add(getStr(el.getAsJsonObject(), "fileName"));
                        }
                    }
                    sb.append(String.join(", ", names)).append("\n");
                } catch (Exception e) {
                    sb.append("- (Could not parse activities)\n");
                }
            }
        }

        // -- Libraries --
        File libraryFile = new File(projectDir, "library");
        if (libraryFile.exists()) {
            String content = readFile(libraryFile);
            if (content != null && !content.trim().isEmpty()) {
                sb.append("- Has custom libraries\n");
            }
        }

        return sb.toString();
    }

    /**
     * Builds a detailed context string for a specific activity's layout,
     * reading the ViewBean data from jC (in-memory) or disk.
     */
    @NonNull
    public static String getActivityLayoutContext(@NonNull String scId, @NonNull String xmlName) {
        StringBuilder sb = new StringBuilder();
        sb.append("Layout context for activity ").append(xmlName).append(":\n\n");

        try {
            ArrayList<com.besome.sketch.beans.ViewBean> beans =
                    a.a.a.jC.a(scId).d(xmlName);
            if (beans != null && !beans.isEmpty()) {
                sb.append("Views (").append(beans.size()).append(" total):\n");
                for (com.besome.sketch.beans.ViewBean bean : beans) {
                    sb.append("  id=").append(bean.id);
                    sb.append(" type=").append(com.besome.sketch.beans.ViewBean.getViewTypeName(bean.type));
                    sb.append("(").append(bean.type).append(")");
                    sb.append(" parent=").append(bean.parent != null ? bean.parent : "root");
                    if (bean.text != null && bean.text.text != null && !bean.text.text.isEmpty()) {
                        sb.append(" text=\"").append(bean.text.text).append("\"");
                    }
                    sb.append("\n");
                }
            } else {
                sb.append("(No views loaded in memory)\n");
            }
        } catch (Exception e) {
            sb.append("(Could not read layout: ").append(e.getMessage()).append(")\n");
        }

        sb.append("\n").append(pro.sketchware.ai.tools.SketchwareViewBridge.buildTypeReference());
        return sb.toString();
    }

    // ── Directory resolution ─────────────────────────────────────────────────

    /**
     * Returns the root .sketchware directory on external storage.
     *
     * <p>Uses {@link Environment#getExternalStorageDirectory()} which is
     * safe for Sketchware's use-case (the app already has MANAGE_EXTERNAL_STORAGE).
     * The deprecation note on this API only applies to apps that should use
     * scoped storage instead — Sketchware intentionally accesses the public
     * external storage root.
     */
    @NonNull
    public static File getSketchwareDir() {
        // ✅ NOTE: getExternalStorageDirectory() is used intentionally here.
        // Sketchware Pro holds MANAGE_EXTERNAL_STORAGE, so accessing the public
        // SD root is correct. Do NOT replace with context.getExternalFilesDir(),
        // which returns an app-private scoped path that would break project access.
        @SuppressWarnings("deprecation")
        File sdcard = Environment.getExternalStorageDirectory();
        return new File(sdcard, SKETCHWARE_DIR);
    }

    @NonNull
    public static File getProjectDataDir(@NonNull String scId) {
        return new File(getSketchwareDir(), "data/" + scId);
    }

    @NonNull
    public static File getProjectSourceDir(@NonNull String scId) {
        return new File(getSketchwareDir(), "mysc/" + scId);
    }

    // ── File I/O ─────────────────────────────────────────────────────────────

    /**
     * Reads a file and returns its contents as a UTF-8 string, or null on failure.
     *
     * <p>Uses explicit UTF-8 charset to avoid locale-dependent decoding issues
     * on devices with non-UTF-8 default charsets.  ✅ FIX: was using FileReader
     * which relies on the default charset.
     */
    @Nullable
    public static String readFile(@NonNull File file) {
        if (!file.exists() || !file.isFile()) return null;
        StringBuilder sb = new StringBuilder((int) Math.max(file.length(), 64));
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString().trim();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Writes {@code content} to {@code file} using UTF-8, creating parent directories
     * as needed.
     *
     * <p>✅ FIX: was using FileWriter (default charset). Now uses explicit UTF-8.
     */
    public static void writeFile(@NonNull File file, @NonNull String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                throw new IOException("Failed to create directories: " + parent.getAbsolutePath());
            }
        }
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8))) {
            writer.write(content);
        }
    }

    // ── JSON helpers ─────────────────────────────────────────────────────────

    @NonNull
    private static String getStr(@NonNull JsonObject obj, @NonNull String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return "";
    }
}
