package pro.sketchware.manifest;

import java.io.File;

import a.a.a.wq;
import mod.hilal.saif.android_manifest.AndroidManifestInjector;
import pro.sketchware.utility.FileUtil;

public final class ProjectManifestManager {

    public static final String MODE_GENERATED = "generated";
    public static final String MODE_HYBRID = "hybrid";
    public static final String MODE_RAW = "raw";

    private ProjectManifestManager() {
    }

    public static String getManifestDirectory(String scId) {
        return wq.b(scId) + File.separator + "manifest";
    }

    public static String getModePath(String scId) {
        return getManifestDirectory(scId) + File.separator + "mode.txt";
    }

    public static String getRawManifestPath(String scId) {
        return getManifestDirectory(scId) + File.separator + "raw_override.xml";
    }

    public static String getEffectiveManifestPath(String scId) {
        return getManifestDirectory(scId) + File.separator + "effective_preview.xml";
    }

    public static String getMode(String scId) {
        String value = FileUtil.readFileIfExist(getModePath(scId)).trim().toLowerCase();
        if (MODE_GENERATED.equals(value) || MODE_HYBRID.equals(value) || MODE_RAW.equals(value)) {
            return value;
        }
        return MODE_HYBRID;
    }

    public static void setMode(String scId, String mode) {
        FileUtil.makeDir(getManifestDirectory(scId));
        FileUtil.writeFile(getModePath(scId), normalizeMode(mode));
    }

    public static void ensureRawManifestSeeded(String scId, String content) {
        FileUtil.makeDir(getManifestDirectory(scId));
        String rawPath = getRawManifestPath(scId);
        if (!FileUtil.isExistFile(rawPath) || FileUtil.readFile(rawPath).trim().isEmpty()) {
            FileUtil.writeFile(rawPath, content);
        }
    }

    public static String getEditableRawManifestOrFallback(String scId, String fallback) {
        String rawPath = getRawManifestPath(scId);
        if (FileUtil.isExistFile(rawPath)) {
            String raw = FileUtil.readFile(rawPath);
            if (!raw.trim().isEmpty()) {
                return raw;
            }
        }
        return fallback;
    }

    public static String apply(String scId, String generatedManifest, String applicationId) {
        String mode = getMode(scId);
        String effective;
        if (MODE_RAW.equals(mode)) {
            effective = getEditableRawManifestOrFallback(scId, generatedManifest);
        } else if (MODE_GENERATED.equals(mode)) {
            effective = generatedManifest;
        } else {
            effective = AndroidManifestInjector.mHolder(generatedManifest, scId);
        }
        effective = effective.replace("${applicationId}", applicationId);
        FileUtil.makeDir(getManifestDirectory(scId));
        FileUtil.writeFile(getEffectiveManifestPath(scId), effective);
        return effective;
    }

    private static String normalizeMode(String mode) {
        if (mode == null) {
            return MODE_HYBRID;
        }
        mode = mode.trim().toLowerCase();
        if (MODE_GENERATED.equals(mode) || MODE_HYBRID.equals(mode) || MODE_RAW.equals(mode)) {
            return mode;
        }
        return MODE_HYBRID;
    }
}
