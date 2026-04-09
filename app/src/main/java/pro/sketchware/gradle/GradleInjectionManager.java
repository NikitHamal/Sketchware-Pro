package pro.sketchware.gradle;

import java.io.File;

import pro.sketchware.activities.projecttools.ProjectToolPaths;
import pro.sketchware.utility.FileUtil;

public final class GradleInjectionManager {

    private static final String FILE_APP = "app_gradle_inject.txt";
    private static final String FILE_PROJECT = "proj_gradle_inject.txt";
    private static final String FILE_PROPERTIES = "properties_inject.txt";

    private GradleInjectionManager() {
    }

    public static File getInjectionDir(String scId) {
        return ProjectToolPaths.getProjectGradleInjectionDir(scId);
    }

    public static String readAppGradleInject(String scId) {
        return readSafe(new File(getInjectionDir(scId), FILE_APP));
    }

    public static String readProjectGradleInject(String scId) {
        return readSafe(new File(getInjectionDir(scId), FILE_PROJECT));
    }

    public static String readPropertiesInject(String scId) {
        return readSafe(new File(getInjectionDir(scId), FILE_PROPERTIES));
    }

    public static void writeAppGradleInject(String scId, String content) {
        writeSafe(new File(getInjectionDir(scId), FILE_APP), content);
    }

    public static void writeProjectGradleInject(String scId, String content) {
        writeSafe(new File(getInjectionDir(scId), FILE_PROJECT), content);
    }

    public static void writePropertiesInject(String scId, String content) {
        writeSafe(new File(getInjectionDir(scId), FILE_PROPERTIES), content);
    }

    public static String appendIfPresent(String baseContent, String injection) {
        String safeBase = baseContent == null ? "" : baseContent.trim();
        String safeInjection = injection == null ? "" : injection.trim();
        if (safeInjection.isEmpty()) {
            return safeBase.isEmpty() ? "" : safeBase + "\n";
        }
        if (safeBase.isEmpty()) {
            return safeInjection + "\n";
        }
        return safeBase + "\n\n" + safeInjection + "\n";
    }

    private static String readSafe(File file) {
        if (file == null || !file.exists()) {
            return "";
        }
        return FileUtil.readFile(file.getAbsolutePath()).trim();
    }

    private static void writeSafe(File file, String content) {
        if (file == null) {
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        FileUtil.writeFile(file.getAbsolutePath(), content == null ? "" : content);
    }
}
