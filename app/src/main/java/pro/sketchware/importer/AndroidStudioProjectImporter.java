package pro.sketchware.importer;

import static mod.hey.studios.util.ProjectFile.COLOR_ACCENT;
import static mod.hey.studios.util.ProjectFile.COLOR_CONTROL_HIGHLIGHT;
import static mod.hey.studios.util.ProjectFile.COLOR_CONTROL_NORMAL;
import static mod.hey.studios.util.ProjectFile.COLOR_PRIMARY;
import static mod.hey.studios.util.ProjectFile.COLOR_PRIMARY_DARK;
import static mod.hey.studios.util.ProjectFile.getDefaultColor;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import a.a.a.GB;
import a.a.a.jC;
import a.a.a.lC;
import a.a.a.oB;
import a.a.a.wq;
import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.project.ProjectSettings;
import mod.jbk.build.BuiltInLibraries;
import mod.pranav.dependency.resolver.DependencyResolver;
import pro.sketchware.managers.inject.InjectRootLayoutManager;
import pro.sketchware.manifest.ProjectManifestManager;
import pro.sketchware.tools.ViewBeanParser;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;

public class AndroidStudioProjectImporter {
    private static final String TAG = "ASProjectImporter";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final Pattern STRING_ASSIGNMENT = Pattern.compile("(?m)^[\\t ]*([A-Za-z_][A-Za-z0-9_]*)[\\t ]*=[\\t ]*['\"]([^'\"]+)['\"]");
    private static final Pattern PROPERTY_ASSIGNMENT = Pattern.compile("(?m)^[\\t ]*([A-Za-z_][A-Za-z0-9_]*)[\\t ]*[=:][\\t ]*['\"]?([^\\n'\"]+)['\"]?");
    private static final Pattern DEPENDENCY_PATTERN = Pattern.compile("(?m)^[\\t ]*(implementation|api|compileOnly|runtimeOnly|kapt|ksp)\\s*(?:\\(|\\s)\\s*['\"]([^:'\"\\s]+):([^:'\"\\s]+):([^'\")\\s]+)['\"]");
    private static final Pattern LAYOUT_REFERENCE_PATTERN = Pattern.compile("R\\.layout\\.([A-Za-z0-9_]+)");
    private static final Pattern SET_CONTENT_VIEW_PATTERN = Pattern.compile("setContentView\\s*\\(\\s*R\\.layout\\.([A-Za-z0-9_]+)\\s*\\)");
    private static final Pattern INFLATE_PATTERN = Pattern.compile("inflate\\s*\\(\\s*R\\.layout\\.([A-Za-z0-9_]+)\\s*\\)");
    private static final long MAX_EXTRACTED_BYTES = 512L * 1024L * 1024L;
    private static final int MAX_EXTRACTED_FILES = 20000;

    private final Context context;
    private final Gson gson = new Gson();
    private final FilePathUtil filePathUtil = new FilePathUtil();

    public AndroidStudioProjectImporter(Context context) {
        this.context = context.getApplicationContext();
    }

    public ImportResult importFromZipUri(Uri uri) throws Exception {
        File tempZip = new File(context.getCacheDir(), "import-" + System.currentTimeMillis() + ".zip");
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new IOException("Unable to open selected file");
            }
            copyStreamToFile(inputStream, tempZip);
        }
        return importFromZipFile(tempZip, "android_studio_zip", null);
    }

    public ImportResult importFromGitHub(String repoUrl, String branch, String token) throws Exception {
        GitHubRepoSpec repoSpec = GitHubRepoSpec.parse(repoUrl, branch);
        if (TextUtils.isEmpty(repoSpec.branch)) {
            repoSpec.branch = fetchDefaultBranch(repoSpec, token);
        }
        File archive = new File(context.getCacheDir(), repoSpec.repo + "-" + System.currentTimeMillis() + ".zip");
        downloadGitHubArchive(repoSpec, token, archive);
        ImportResult result = importFromZipFile(archive, "github", repoSpec.repo);
        result.sourceLabel = repoSpec.owner + "/" + repoSpec.repo + "#" + repoSpec.branch;
        return result;
    }

    public ImportResult importFromZipFile(File zipFile, String sourceType, String preferredProjectName) throws Exception {
        File extractDir = new File(context.getCacheDir(), "import-extracted-" + System.currentTimeMillis());
        safeExtract(zipFile, extractDir);
        DetectedProject detectedProject = detectProject(extractDir);
        if (detectedProject != null) {
            detectedProject.archiveLabel = zipFile.getName();
        }
        if (detectedProject == null) {
            throw new IOException("No supported Android application module was found in the selected archive");
        }
        if (detectedProject.roundTripMetadataJson != null && detectedProject.roundTripMetadataJson.isFile()) {
            return restoreRoundTripProject(detectedProject, sourceType, preferredProjectName);
        }
        return importGenericAndroidProject(detectedProject, sourceType, preferredProjectName);
    }

    public static void writeRoundTripMetadata(String scId, HashMap<String, Object> metadata, String androidStudioProjectRoot) {
        try {
            String skproDir = androidStudioProjectRoot + File.separator + ".skpro";
            String snapshotDir = skproDir + File.separator + "data_snapshot";
            FileUtil.deleteFile(skproDir);
            FileUtil.makeDir(snapshotDir);
            File dataDir = new File(wq.b(scId));
            if (dataDir.exists()) {
                FileUtil.copyDirectory(dataDir, new File(snapshotDir, "data"));
            }
            FileUtil.writeFile(skproDir + File.separator + "project_metadata.json", new Gson().toJson(metadata));
        } catch (Exception e) {
            Log.e(TAG, "Failed to write round-trip metadata", e);
        }
    }

    private ImportResult restoreRoundTripProject(DetectedProject detectedProject, String sourceType, String preferredProjectName) throws Exception {
        String scId = lC.b();
        String metadataJson = FileUtil.readFile(detectedProject.roundTripMetadataJson.getAbsolutePath());
        HashMap<String, Object> metadata = gson.fromJson(metadataJson, new TypeToken<HashMap<String, Object>>() {
        }.getType());
        if (metadata == null) {
            throw new IOException("Invalid round-trip metadata");
        }
        metadata.put("sc_id", scId);
        if (!TextUtils.isEmpty(preferredProjectName)) {
            metadata.put("my_ws_name", sanitizeProjectName(preferredProjectName));
        }
        lC.a(scId, metadata);
        wq.a(context, scId);
        new oB().b(wq.b(scId));
        if (detectedProject.roundTripDataDir == null || !detectedProject.roundTripDataDir.isDirectory()) {
            throw new IOException("Round-trip data snapshot is missing");
        }
        FileUtil.copyDirectory(detectedProject.roundTripDataDir, new File(wq.b(scId)));
        ImportResult result = new ImportResult();
        result.scId = scId;
        result.projectName = String.valueOf(metadata.get("my_ws_name"));
        result.sourceType = sourceType;
        result.sourceLabel = sourceType.equals("android_studio_zip") ? detectedProject.archiveLabel : detectedProject.rootDirectory.getName();
        result.visualScreens.add("Restored Sketchware project snapshot");
        result.summary = "Round-trip import completed";
        writeImportMetadata(scId, sourceType, true);
        writeImportReport(result);
        return result;
    }

    private ImportResult importGenericAndroidProject(DetectedProject detectedProject, String sourceType, String preferredProjectName) throws Exception {
        GradleSummary gradle = parseGradle(detectedProject.gradleFile);
        ManifestSummary manifest = parseManifest(detectedProject.manifestFile, detectedProject.resDirectory);

        String projectName = chooseProjectName(preferredProjectName, manifest.applicationLabel, detectedProject.rootDirectory.getName());
        String applicationId = chooseApplicationId(gradle.applicationId, gradle.namespace, manifest.packageName, projectName);
        String versionCode = gradle.versionCode == null ? "1" : gradle.versionCode;
        String versionName = gradle.versionName == null ? "1.0" : gradle.versionName;
        int minSdk = gradle.minSdk > 0 ? gradle.minSdk : 21;
        int targetSdk = gradle.targetSdk > 0 ? gradle.targetSdk : 36;

        String scId = lC.b();
        HashMap<String, Object> metadata = createProjectMetadata(scId, projectName, applicationId, manifest.applicationLabel, versionCode, versionName);
        lC.a(scId, metadata);
        wq.a(context, scId);
        new oB().b(wq.b(scId));

        ProjectSettings settings = new ProjectSettings(scId);
        settings.setValue(ProjectSettings.SETTING_NEW_XML_COMMAND, ProjectSettings.SETTING_GENERIC_VALUE_TRUE);
        settings.setValue(ProjectSettings.SETTING_ENABLE_VIEWBINDING, ProjectSettings.SETTING_GENERIC_VALUE_TRUE);
        settings.setValue(ProjectSettings.SETTING_MINIMUM_SDK_VERSION, String.valueOf(minSdk));
        settings.setValue(ProjectSettings.SETTING_TARGET_SDK_VERSION, String.valueOf(targetSdk));

        ensureProjectDirectories(scId);

        copySourceTree(detectedProject.sourceRoots, new File(filePathUtil.getPathJava(scId)));
        copyResources(detectedProject.resDirectory, new File(filePathUtil.getPathResource(scId)));
        copyIfDirectoryExists(detectedProject.assetsDirectory, new File(filePathUtil.getPathAssets(scId)));
        copyIfDirectoryExists(detectedProject.jniLibsDirectory, new File(filePathUtil.getPathNativelibs(scId)));
        importLocalJarsAndAars(detectedProject.libsDirectories, scId);

        resolveAndRegisterDependencies(scId, gradle.dependencies);

        if (!manifest.permissions.isEmpty()) {
            FileUtil.writeFile(filePathUtil.getPathPermission(scId), gson.toJson(manifest.permissions));
        }

        if (manifest.rawXml != null) {
            ProjectManifestManager.ensureRawManifestSeeded(scId, manifest.rawXml);
            ProjectManifestManager.setMode(scId, ProjectManifestManager.MODE_RAW);
        }

        ImportResult result = new ImportResult();
        result.scId = scId;
        result.projectName = projectName;
        result.sourceType = sourceType;
        result.sourceLabel = sourceType.equals("android_studio_zip") ? detectedProject.archiveLabel : detectedProject.rootDirectory.getName();
        result.importedDependencies.addAll(gradle.dependencies);
        result.unsupportedFeatures.addAll(gradle.warnings);

        materializeActivities(scId, manifest, detectedProject, result);
        writeImportMetadata(scId, sourceType, false);
        writeImportReport(result);
        result.summary = buildSummary(result, manifest);
        return result;
    }

    private void materializeActivities(String scId, ManifestSummary manifest, DetectedProject detectedProject, ImportResult result) {
        Set<String> usedScreenNames = new HashSet<>();
        jC.b(scId);
        jC.a(scId);
        InjectRootLayoutManager rootLayoutManager = new InjectRootLayoutManager(scId);

        for (ManifestActivity manifestActivity : manifest.activities) {
            File sourceFile = findSourceFileForClass(detectedProject.sourceRoots, manifestActivity.fullyQualifiedName);
            String simpleClassName = manifestActivity.fullyQualifiedName.substring(manifestActivity.fullyQualifiedName.lastIndexOf('.') + 1);
            String screenName = uniquifyScreenName(usedScreenNames, toSketchwareScreenName(simpleClassName));
            ProjectFileBean fileBean = new ProjectFileBean(ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY, screenName);
            jC.b(scId).a(fileBean);
            if (manifestActivity.launcher) {
                result.visualScreens.add(screenName + " (launcher)");
            } else {
                result.visualScreens.add(screenName);
            }

            String layoutName = null;
            if (sourceFile != null) {
                String source = FileUtil.readFile(sourceFile.getAbsolutePath());
                layoutName = detectLayoutName(source);
            }
            if (layoutName == null) {
                File guessed = new File(detectedProject.layoutDirectory, fileBean.getXmlName());
                if (guessed.exists()) {
                    layoutName = screenName;
                }
            }

            if (!TextUtils.isEmpty(layoutName)) {
                File originalLayout = new File(detectedProject.layoutDirectory, layoutName + ".xml");
                if (originalLayout.exists()) {
                    importLayoutForScreen(scId, fileBean, layoutName, originalLayout, rootLayoutManager, result);
                }
            } else {
                result.codeOnlyFiles.add(simpleClassName + " (no XML layout was detected)");
            }
        }
        jC.b(scId).j();
        jC.b(scId).l();
    }

    private void importLayoutForScreen(String scId, ProjectFileBean fileBean, String originalLayoutName,
                                       File originalLayoutFile, InjectRootLayoutManager rootLayoutManager,
                                       ImportResult result) {
        String layoutRoot = filePathUtil.getPathResource(scId) + File.separator + "layout";
        String originalTarget = layoutRoot + File.separator + originalLayoutName + ".xml";
        FileUtil.copyFile(originalLayoutFile.getAbsolutePath(), originalTarget);

        String visualXmlName = fileBean.getXmlName();
        String visualTarget = layoutRoot + File.separator + visualXmlName;
        if (!visualXmlName.equals(originalLayoutName + ".xml")) {
            FileUtil.copyFile(originalLayoutFile.getAbsolutePath(), visualTarget);
            result.warnings.add(fileBean.fileName + ": created visual companion layout " + visualXmlName + " from " + originalLayoutName + ".xml");
        }

        try {
            String visualContent = FileUtil.readFile(visualTarget);
            ViewBeanParser parser = new ViewBeanParser(visualContent);
            parser.setSkipRoot(true);
            ArrayList<ViewBean> parsedLayout = parser.parse();
            if (parser.getRootAttributes() != null) {
                rootLayoutManager.set(visualXmlName, InjectRootLayoutManager.toRoot(parser.getRootAttributes()));
            }
            jC.a(scId).c.put(visualXmlName, parsedLayout);
        } catch (Exception e) {
            result.warnings.add(fileBean.fileName + ": layout imported as code-only because visual parsing failed (" + e.getMessage() + ")");
        }
    }

    private void resolveAndRegisterDependencies(String scId, List<String> dependencies) {
        ArrayList<HashMap<String, Object>> localLibraries = LocalLibrariesUtil.getLocalLibraries(scId);
        Set<String> existingDependencies = new LinkedHashSet<>();
        Set<String> existingLibraryNames = new LinkedHashSet<>();
        for (HashMap<String, Object> localLibrary : localLibraries) {
            Object dependency = localLibrary.get("dependency");
            if (dependency != null) {
                existingDependencies.add(String.valueOf(dependency));
            }
            Object name = localLibrary.get("name");
            if (name != null) {
                existingLibraryNames.add(String.valueOf(name));
            }
        }
        if (dependencies.isEmpty()) {
            return;
        }

        BuiltInLibraries.maybeExtractAndroidJar();
        BuiltInLibraries.maybeExtractCoreLambdaStubsJar();

        for (String dependency : dependencies) {
            if (existingDependencies.contains(dependency)) {
                continue;
            }
            String[] parts = dependency.split(":", 3);
            if (parts.length != 3) {
                continue;
            }

            List<String> resolvedArtifacts = new ArrayList<>();
            try {
                new DependencyResolver(parts[0], parts[1], parts[2], false, new BuildSettings(scId))
                        .resolveDependency(new DependencyResolver.DependencyResolverCallback() {
                            @Override
                            public void onTaskCompleted(List<String> artifacts) {
                                if (artifacts != null) {
                                    resolvedArtifacts.addAll(artifacts);
                                }
                            }
                        });
            } catch (Throwable throwable) {
                Log.e(TAG, "Dependency resolution failed for " + dependency, throwable);
                continue;
            }

            if (resolvedArtifacts.isEmpty()) {
                resolvedArtifacts.add(sanitizeLibraryName(parts[1] + "-v" + parts[2]));
            }

            for (String artifactName : resolvedArtifacts) {
                String safeArtifactName = sanitizeLibraryName(artifactName);
                if (existingLibraryNames.contains(safeArtifactName)) {
                    continue;
                }
                String rootDependency = dependency.equals(parts[0] + ":" + parts[1] + ":" + parts[2]) && safeArtifactName.equals(sanitizeLibraryName(parts[1] + "-v" + parts[2]))
                        ? dependency : null;
                localLibraries.add(LocalLibrariesUtil.createLibraryMap(safeArtifactName, rootDependency));
                existingLibraryNames.add(safeArtifactName);
            }
            existingDependencies.add(dependency);
        }
        LocalLibrariesUtil.rewriteLocalLibFile(scId, gson.toJson(localLibraries));
    }

    private void importLocalJarsAndAars(List<File> libraryDirectories, String scId) throws IOException {
        String classpathDir = wq.b(scId) + File.separator + "files" + File.separator + "classpath";
        FileUtil.makeDir(classpathDir);
        for (File libraryDirectory : libraryDirectories) {
            List<File> files = collectFilesRecursively(libraryDirectory);
            for (File file : files) {
                String name = file.getName().toLowerCase(Locale.US);
                if (name.endsWith(".jar")) {
                    FileUtil.copyFile(file.getAbsolutePath(), classpathDir + File.separator + file.getName());
                } else if (name.endsWith(".aar")) {
                    importAarAsLocalLibrary(scId, file);
                }
            }
        }
    }

    private List<File> collectFilesRecursively(File directory) {
        List<File> files = new ArrayList<>();
        if (directory == null || !directory.exists()) {
            return files;
        }
        File[] children = directory.listFiles();
        if (children == null) {
            return files;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                files.addAll(collectFilesRecursively(child));
            } else {
                files.add(child);
            }
        }
        return files;
    }

    private void importAarAsLocalLibrary(String scId, File aarFile) throws IOException {
        String libraryName = sanitizeLibraryName(FileUtil.getFileNameNoExtension(aarFile.getName()));
        String localLibraryRoot = FileUtil.getExternalStorageDir() + "/.sketchware/libs/local_libs/" + libraryName;
        FileUtil.deleteFile(localLibraryRoot);
        FileUtil.makeDir(localLibraryRoot);
        try (ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(new java.io.FileInputStream(aarFile)))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String safeName = sanitizeZipEntryName(entry.getName());
                if (safeName == null) {
                    zipInputStream.closeEntry();
                    continue;
                }
                File target = new File(localLibraryRoot, safeName);
                String canonicalRoot = new File(localLibraryRoot).getCanonicalPath() + File.separator;
                String canonicalTarget = target.getCanonicalPath();
                if (!canonicalTarget.startsWith(canonicalRoot) && !canonicalTarget.equals(new File(localLibraryRoot).getCanonicalPath())) {
                    throw new IOException("Unsafe AAR entry detected");
                }
                if (entry.isDirectory()) {
                    target.mkdirs();
                } else {
                    target.getParentFile().mkdirs();
                    copyStreamToFile(zipInputStream, target);
                }
                zipInputStream.closeEntry();
            }
        }
        ArrayList<HashMap<String, Object>> localLibraries = LocalLibrariesUtil.getLocalLibraries(scId);
        boolean exists = false;
        for (HashMap<String, Object> library : localLibraries) {
            if (libraryName.equals(String.valueOf(library.get("name")))) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            localLibraries.add(LocalLibrariesUtil.createLibraryMap(libraryName, null));
            LocalLibrariesUtil.rewriteLocalLibFile(scId, gson.toJson(localLibraries));
        }
    }

    private void ensureProjectDirectories(String scId) {
        FileUtil.makeDir(wq.b(scId));
        FileUtil.makeDir(filePathUtil.getPathJava(scId));
        FileUtil.makeDir(filePathUtil.getPathResource(scId));
        FileUtil.makeDir(filePathUtil.getPathResource(scId) + File.separator + "layout");
        FileUtil.makeDir(filePathUtil.getPathResource(scId) + File.separator + "values");
        FileUtil.makeDir(filePathUtil.getPathAssets(scId));
        FileUtil.makeDir(filePathUtil.getPathNativelibs(scId));
        FileUtil.makeDir(wq.b(scId) + File.separator + "files" + File.separator + "classpath");
        FileUtil.makeDir(ProjectManifestManager.getManifestDirectory(scId));
    }

    private void copySourceTree(List<File> sourceRoots, File targetRoot) throws IOException {
        FileUtil.makeDir(targetRoot.getAbsolutePath());
        for (File sourceRoot : sourceRoots) {
            if (sourceRoot != null && sourceRoot.isDirectory()) {
                FileUtil.copyDirectory(sourceRoot, targetRoot);
            }
        }
    }

    private void copyResources(File resDirectory, File targetRoot) throws IOException {
        if (resDirectory != null && resDirectory.isDirectory()) {
            FileUtil.copyDirectory(resDirectory, targetRoot);
        }
    }

    private void copyIfDirectoryExists(File source, File target) throws IOException {
        if (source != null && source.isDirectory()) {
            FileUtil.copyDirectory(source, target);
        }
    }

    private ImportResult emptyResult() {
        return new ImportResult();
    }

    private String buildSummary(ImportResult result, ManifestSummary manifest) {
        return "Imported " + result.visualScreens.size() + " screen(s), "
                + result.importedDependencies.size() + " dependency declaration(s), and "
                + manifest.permissions.size() + " manifest permission(s).";
    }

    private void writeImportMetadata(String scId, String sourceType, boolean roundTrip) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source_type", sourceType);
        metadata.put("java_layout", "full_source_tree");
        metadata.put("round_trip", roundTrip);
        metadata.put("created_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date()));
        FileUtil.writeFile(wq.b(scId) + File.separator + "import_metadata.json", gson.toJson(metadata));
    }

    private void writeImportReport(ImportResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Project: ").append(result.projectName).append('\n');
        sb.append("Sketchware ID: ").append(result.scId).append('\n');
        if (!TextUtils.isEmpty(result.sourceLabel)) {
            sb.append("Source: ").append(result.sourceLabel).append('\n');
        }
        sb.append("Type: ").append(result.sourceType).append('\n').append('\n');
        appendSection(sb, "Visual / registered screens", result.visualScreens);
        appendSection(sb, "Code-only notes", result.codeOnlyFiles);
        appendSection(sb, "Dependencies", result.importedDependencies);
        appendSection(sb, "Warnings", result.warnings);
        appendSection(sb, "Unsupported / degraded features", result.unsupportedFeatures);
        FileUtil.writeFile(wq.b(result.scId) + File.separator + "import_report.txt", sb.toString());
    }

    private void appendSection(StringBuilder sb, String title, List<String> lines) {
        sb.append(title).append(':').append('\n');
        if (lines == null || lines.isEmpty()) {
            sb.append("- none\n\n");
            return;
        }
        for (String line : lines) {
            sb.append("- ").append(line).append('\n');
        }
        sb.append('\n');
    }

    private HashMap<String, Object> createProjectMetadata(String scId, String projectName, String packageName,
                                                          String appName, String versionCode, String versionName) {
        HashMap<String, Object> data = new HashMap<>();
        data.put("sc_id", scId);
        data.put("proj_type", 1);
        data.put("my_sc_pkg_name", packageName);
        data.put("my_ws_name", sanitizeProjectName(projectName));
        data.put("my_app_name", TextUtils.isEmpty(appName) ? projectName : appName);
        data.put("my_sc_reg_dt", new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(new Date()));
        data.put("custom_icon", false);
        data.put("isIconAdaptive", false);
        data.put("sc_ver_code", versionCode);
        data.put("sc_ver_name", versionName);
        data.put("sketchware_ver", GB.d(context));
        data.put(COLOR_ACCENT, getDefaultColor(COLOR_ACCENT));
        data.put(COLOR_PRIMARY, getDefaultColor(COLOR_PRIMARY));
        data.put(COLOR_PRIMARY_DARK, getDefaultColor(COLOR_PRIMARY_DARK));
        data.put(COLOR_CONTROL_HIGHLIGHT, getDefaultColor(COLOR_CONTROL_HIGHLIGHT));
        data.put(COLOR_CONTROL_NORMAL, getDefaultColor(COLOR_CONTROL_NORMAL));
        return data;
    }

    private String chooseProjectName(String preferredProjectName, String applicationLabel, String fallback) {
        if (!TextUtils.isEmpty(preferredProjectName)) {
            return sanitizeProjectName(preferredProjectName);
        }
        if (!TextUtils.isEmpty(applicationLabel)) {
            return sanitizeProjectName(applicationLabel);
        }
        return sanitizeProjectName(fallback);
    }

    private String chooseApplicationId(String applicationId, String namespace, String manifestPackage, String projectName) {
        if (!TextUtils.isEmpty(applicationId)) return applicationId;
        if (!TextUtils.isEmpty(namespace)) return namespace;
        if (!TextUtils.isEmpty(manifestPackage)) return manifestPackage;
        return "com.imported." + sanitizeProjectName(projectName).toLowerCase(Locale.US).replace(' ', '.');
    }

    private String sanitizeProjectName(String value) {
        if (TextUtils.isEmpty(value)) {
            return "ImportedProject";
        }
        value = value.replaceAll("[^A-Za-z0-9 _.-]", " ").trim();
        if (value.isEmpty()) {
            return "ImportedProject";
        }
        return value;
    }

    private String sanitizeLibraryName(String value) {
        return value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private String toSketchwareScreenName(String simpleClassName) {
        String base = simpleClassName;
        if (base.endsWith("Activity") && base.length() > "Activity".length()) {
            base = base.substring(0, base.length() - "Activity".length());
        }
        String snake = base.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.US);
        snake = snake.replaceAll("[^a-z0-9_]+", "_").replaceAll("_+", "_");
        snake = snake.replaceAll("^_+|_+$", "");
        return snake.isEmpty() ? "main" : snake;
    }

    private String uniquifyScreenName(Set<String> usedNames, String desired) {
        String candidate = desired;
        int index = 2;
        while (usedNames.contains(candidate)) {
            candidate = desired + "_" + index;
            index++;
        }
        usedNames.add(candidate);
        return candidate;
    }

    private String detectLayoutName(String source) {
        Matcher matcher = SET_CONTENT_VIEW_PATTERN.matcher(source);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = INFLATE_PATTERN.matcher(source);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = LAYOUT_REFERENCE_PATTERN.matcher(source);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private File findSourceFileForClass(List<File> sourceRoots, String fqcn) {
        String path = fqcn.replace('.', File.separatorChar);
        for (File sourceRoot : sourceRoots) {
            File javaFile = new File(sourceRoot, path + ".java");
            if (javaFile.exists()) return javaFile;
            File kotlinFile = new File(sourceRoot, path + ".kt");
            if (kotlinFile.exists()) return kotlinFile;
        }
        String simpleName = fqcn.substring(fqcn.lastIndexOf('.') + 1);
        for (File sourceRoot : sourceRoots) {
            List<File> javaCandidates = FileUtil.listFilesRecursively(sourceRoot, ".java");
            for (File candidate : javaCandidates) {
                if (candidate.getName().equals(simpleName + ".java")) return candidate;
            }
            List<File> ktCandidates = FileUtil.listFilesRecursively(sourceRoot, ".kt");
            for (File candidate : ktCandidates) {
                if (candidate.getName().equals(simpleName + ".kt")) return candidate;
            }
        }
        return null;
    }

    private DetectedProject detectProject(File extractedRoot) {
        List<File> manifestFiles = FileUtil.listFilesRecursively(extractedRoot, "AndroidManifest.xml");
        for (File manifestFile : manifestFiles) {
            File appMain = manifestFile.getParentFile();
            if (appMain == null || !"main".equals(appMain.getName())) {
                continue;
            }
            File srcDir = appMain.getParentFile();
            if (srcDir == null || !"src".equals(srcDir.getName())) {
                continue;
            }
            File appDir = srcDir.getParentFile();
            if (appDir == null) {
                continue;
            }
            File projectRoot = appDir.getParentFile();
            if (projectRoot == null) {
                continue;
            }
            DetectedProject detectedProject = new DetectedProject();
            detectedProject.rootDirectory = projectRoot;
            detectedProject.archiveLabel = projectRoot.getName();
            detectedProject.appDirectory = appDir;
            detectedProject.manifestFile = manifestFile;
            detectedProject.resDirectory = new File(appMain, "res");
            detectedProject.layoutDirectory = new File(detectedProject.resDirectory, "layout");
            detectedProject.assetsDirectory = new File(appMain, "assets");
            detectedProject.jniLibsDirectory = new File(appMain, "jniLibs");
            detectedProject.gradleFile = new File(appDir, "build.gradle");
            if (!detectedProject.gradleFile.exists()) {
                detectedProject.gradleFile = new File(appDir, "build.gradle.kts");
            }
            detectedProject.sourceRoots = new ArrayList<>();
            File javaRoot = new File(appMain, "java");
            if (javaRoot.isDirectory()) {
                detectedProject.sourceRoots.add(javaRoot);
            }
            File kotlinRoot = new File(appMain, "kotlin");
            if (kotlinRoot.isDirectory()) {
                detectedProject.sourceRoots.add(kotlinRoot);
            }
            detectedProject.libsDirectories = new ArrayList<>();
            File appLibs = new File(appDir, "libs");
            if (appLibs.isDirectory()) {
                detectedProject.libsDirectories.add(appLibs);
            }
            File rootLibs = new File(projectRoot, "libs");
            if (rootLibs.isDirectory()) {
                detectedProject.libsDirectories.add(rootLibs);
            }
            File skproDir = new File(projectRoot, ".skpro");
            File roundTripMetadata = new File(skproDir, "project_metadata.json");
            if (roundTripMetadata.isFile()) {
                detectedProject.roundTripMetadataJson = roundTripMetadata;
                File roundTripData = new File(skproDir, "data_snapshot/data");
                if (roundTripData.isDirectory()) {
                    detectedProject.roundTripDataDir = roundTripData;
                }
            }
            return detectedProject;
        }
        return null;
    }

    private ManifestSummary parseManifest(File manifestFile, File resDirectory) throws Exception {
        ManifestSummary summary = new ManifestSummary();
        summary.rawXml = FileUtil.readFile(manifestFile.getAbsolutePath());
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(manifestFile);
        Element manifestElement = document.getDocumentElement();
        summary.packageName = manifestElement.getAttribute("package");

        NodeList usesPermissions = document.getElementsByTagName("uses-permission");
        for (int i = 0; i < usesPermissions.getLength(); i++) {
            Element permissionElement = (Element) usesPermissions.item(i);
            String permissionName = getAndroidAttribute(permissionElement, "name");
            if (!TextUtils.isEmpty(permissionName)) {
                summary.permissions.add(permissionName);
            }
        }

        NodeList applicationNodes = document.getElementsByTagName("application");
        if (applicationNodes.getLength() > 0) {
            Element applicationElement = (Element) applicationNodes.item(0);
            String label = getAndroidAttribute(applicationElement, "label");
            summary.applicationLabel = resolveManifestLabel(label, resDirectory);
            NodeList childNodes = applicationElement.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node child = childNodes.item(i);
                if (!(child instanceof Element)) {
                    continue;
                }
                Element childElement = (Element) child;
                switch (childElement.getTagName()) {
                    case "activity" -> summary.activities.add(parseManifestActivity(childElement, summary.packageName));
                    case "service" -> addComponentName(summary.services, childElement, summary.packageName);
                    case "receiver" -> addComponentName(summary.receivers, childElement, summary.packageName);
                    case "provider" -> addComponentName(summary.providers, childElement, summary.packageName);
                }
            }
        }
        return summary;
    }

    private ManifestActivity parseManifestActivity(Element activityElement, String packageName) {
        ManifestActivity activity = new ManifestActivity();
        activity.fullyQualifiedName = resolveClassName(packageName, getAndroidAttribute(activityElement, "name"));
        activity.launcher = hasLauncherIntent(activityElement);
        return activity;
    }

    private boolean hasLauncherIntent(Element activityElement) {
        NodeList children = activityElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element)) {
                continue;
            }
            Element intentFilter = (Element) child;
            if (!"intent-filter".equals(intentFilter.getTagName())) {
                continue;
            }
            boolean hasMain = false;
            boolean hasLauncher = false;
            NodeList filters = intentFilter.getChildNodes();
            for (int j = 0; j < filters.getLength(); j++) {
                Node filterNode = filters.item(j);
                if (!(filterNode instanceof Element)) {
                    continue;
                }
                Element filterElement = (Element) filterNode;
                if ("action".equals(filterElement.getTagName())
                        && "android.intent.action.MAIN".equals(getAndroidAttribute(filterElement, "name"))) {
                    hasMain = true;
                }
                if ("category".equals(filterElement.getTagName())
                        && "android.intent.category.LAUNCHER".equals(getAndroidAttribute(filterElement, "name"))) {
                    hasLauncher = true;
                }
            }
            if (hasMain && hasLauncher) {
                return true;
            }
        }
        return false;
    }

    private void addComponentName(List<String> out, Element element, String manifestPackage) {
        String name = resolveClassName(manifestPackage, getAndroidAttribute(element, "name"));
        if (!TextUtils.isEmpty(name)) {
            out.add(name);
        }
    }

    private String getAndroidAttribute(Element element, String attribute) {
        String value = element.getAttributeNS(ANDROID_NS, attribute);
        if (TextUtils.isEmpty(value)) {
            value = element.getAttribute("android:" + attribute);
        }
        if (TextUtils.isEmpty(value)) {
            value = element.getAttribute(attribute);
        }
        return value;
    }

    private String resolveManifestLabel(String labelValue, File resDirectory) {
        if (TextUtils.isEmpty(labelValue)) {
            return null;
        }
        if (!labelValue.startsWith("@string/")) {
            return labelValue;
        }
        String key = labelValue.substring("@string/".length());
        File stringsXml = new File(resDirectory, "values/strings.xml");
        if (!stringsXml.isFile()) {
            return key;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().parse(stringsXml);
            NodeList strings = document.getElementsByTagName("string");
            for (int i = 0; i < strings.getLength(); i++) {
                Element element = (Element) strings.item(i);
                if (key.equals(element.getAttribute("name"))) {
                    return element.getTextContent();
                }
            }
        } catch (Exception ignored) {
        }
        return key;
    }

    private String resolveClassName(String manifestPackage, String value) {
        if (TextUtils.isEmpty(value)) {
            return value;
        }
        if (value.startsWith(".")) {
            return manifestPackage + value;
        }
        if (!value.contains(".")) {
            return manifestPackage + "." + value;
        }
        return value;
    }

    private GradleSummary parseGradle(File gradleFile) {
        GradleSummary summary = new GradleSummary();
        if (gradleFile == null || !gradleFile.isFile()) {
            summary.warnings.add("No module Gradle file was found; SDK versions and dependency import may be incomplete.");
            return summary;
        }
        String content = FileUtil.readFile(gradleFile.getAbsolutePath());
        summary.applicationId = findFirstValue(content, Arrays.asList("applicationId"));
        summary.namespace = findFirstValue(content, Arrays.asList("namespace"));
        summary.versionCode = findFirstNumeric(content, Arrays.asList("versionCode"));
        summary.versionName = findFirstValue(content, Arrays.asList("versionName"));
        summary.minSdk = parseInt(findFirstNumeric(content, Arrays.asList("minSdk", "minSdkVersion")), 0);
        summary.targetSdk = parseInt(findFirstNumeric(content, Arrays.asList("targetSdk", "targetSdkVersion")), 0);
        if (content.contains("compose = true") || content.contains("buildFeatures.compose") || content.contains("androidx.compose")) {
            summary.warnings.add("Jetpack Compose was detected. The project is imported in code mode; Compose is preserved but not reconstructed visually.");
        }
        if (content.contains("ksp(") || content.contains("com.google.devtools.ksp")) {
            summary.warnings.add("KSP-generated sources were detected. Generated code may need regeneration outside Sketchware Pro.");
        }
        if (content.contains("dagger.hilt") || content.contains("com.google.dagger.hilt")) {
            summary.warnings.add("Hilt was detected. Manifest and generated code are preserved, but project-specific Hilt tooling may still need review.");
        }
        File settingsGradle = new File(gradleFile.getParentFile().getParentFile(), "settings.gradle");
        if (!settingsGradle.exists()) {
            settingsGradle = new File(gradleFile.getParentFile().getParentFile(), "settings.gradle.kts");
        }
        if (settingsGradle.exists()) {
            String settingsContent = FileUtil.readFile(settingsGradle.getAbsolutePath());
            if (settingsContent.contains("include(") || settingsContent.contains("include ':") || settingsContent.contains("include \":")) {
                int moduleCount = 0;
                Matcher matcher = Pattern.compile("[:][A-Za-z0-9_\\-]+").matcher(settingsContent);
                while (matcher.find()) {
                    moduleCount++;
                }
                if (moduleCount > 1) {
                    summary.warnings.add("Multiple Gradle modules were detected. Only the primary app module was imported.");
                }
            }
        }

        Matcher dependencyMatcher = DEPENDENCY_PATTERN.matcher(content);
        LinkedHashSet<String> dependencies = new LinkedHashSet<>();
        while (dependencyMatcher.find()) {
            String configuration = dependencyMatcher.group(1);
            if ("compileOnly".equals(configuration)) {
                continue;
            }
            dependencies.add(dependencyMatcher.group(2) + ":" + dependencyMatcher.group(3) + ":" + dependencyMatcher.group(4));
        }
        summary.dependencies.addAll(dependencies);
        return summary;
    }

    private String findFirstValue(String content, List<String> keys) {
        for (String key : keys) {
            Matcher matcher = Pattern.compile("(?m)^[\\t ]*" + Pattern.quote(key) + "[\\t ]*=?[\\t ]*['\"]([^'\"]+)['\"]").matcher(content);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        Matcher matcher = STRING_ASSIGNMENT.matcher(content);
        Map<String, String> values = new HashMap<>();
        while (matcher.find()) {
            values.put(matcher.group(1), matcher.group(2));
        }
        for (String key : keys) {
            String value = values.get(key);
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return null;
    }

    private String findFirstNumeric(String content, List<String> keys) {
        for (String key : keys) {
            Matcher matcher = Pattern.compile("(?m)^[\\t ]*" + Pattern.quote(key) + "[\\t ]*=?[\\t ]*([0-9]+)").matcher(content);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return null;
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private GitHubRepoSpec fetchRepoSpec(String repoUrl, String branch) {
        return GitHubRepoSpec.parse(repoUrl, branch);
    }

    private String fetchDefaultBranch(GitHubRepoSpec spec, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("https://api.github.com/repos/" + spec.owner + "/" + spec.repo).openConnection();
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "Sketchware-Pro-Importer");
        if (!TextUtils.isEmpty(token)) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }
        connection.connect();
        int responseCode = connection.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            throw new IOException("GitHub API request failed with HTTP " + responseCode);
        }
        String response = readFully(connection.getInputStream());
        JSONObject jsonObject = new JSONObject(response);
        return jsonObject.getString("default_branch");
    }

    private void downloadGitHubArchive(GitHubRepoSpec spec, String token, File targetZip) throws Exception {
        String archiveUrl = "https://github.com/" + spec.owner + "/" + spec.repo + "/archive/refs/heads/" + spec.branch + ".zip";
        HttpURLConnection connection = (HttpURLConnection) new URL(archiveUrl).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "Sketchware-Pro-Importer");
        if (!TextUtils.isEmpty(token)) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }
        connection.connect();
        int responseCode = connection.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            throw new IOException("GitHub archive download failed with HTTP " + responseCode);
        }
        copyStreamToFile(connection.getInputStream(), targetZip);
    }

    private void safeExtract(File zipFile, File outputDirectory) throws IOException {
        FileUtil.makeDir(outputDirectory.getAbsolutePath());
        String canonicalRoot = outputDirectory.getCanonicalPath() + File.separator;
        long totalExtractedBytes = 0L;
        int extractedFiles = 0;
        try (ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(new java.io.FileInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String safeName = sanitizeZipEntryName(entry.getName());
                if (safeName == null) {
                    zipInputStream.closeEntry();
                    continue;
                }
                File target = new File(outputDirectory, safeName);
                String canonicalTarget = target.getCanonicalPath();
                if (!canonicalTarget.startsWith(canonicalRoot) && !canonicalTarget.equals(outputDirectory.getCanonicalPath())) {
                    throw new IOException("Unsafe archive entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    target.mkdirs();
                } else {
                    extractedFiles++;
                    if (extractedFiles > MAX_EXTRACTED_FILES) {
                        throw new IOException("Archive contains too many files to import safely");
                    }
                    long declaredSize = entry.getSize();
                    if (declaredSize > 0) {
                        totalExtractedBytes += declaredSize;
                        if (totalExtractedBytes > MAX_EXTRACTED_BYTES) {
                            throw new IOException("Archive is too large to import safely");
                        }
                    }
                    target.getParentFile().mkdirs();
                    copyStreamToFile(zipInputStream, target);
                    if (declaredSize < 0) {
                        totalExtractedBytes += target.length();
                        if (totalExtractedBytes > MAX_EXTRACTED_BYTES) {
                            throw new IOException("Archive is too large to import safely");
                        }
                    }
                }
                zipInputStream.closeEntry();
            }
        }
    }

    private String sanitizeZipEntryName(String entryName) {
        if (entryName == null) {
            return null;
        }
        String normalized = entryName.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.contains("../") || normalized.equals("..") || normalized.contains(":/")) {
            return null;
        }
        return normalized;
    }

    private static void copyStreamToFile(InputStream inputStream, File file) throws IOException {
        file.getParentFile().mkdirs();
        try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        }
    }

    private static String readFully(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }

    public static class ImportResult {
        public String scId;
        public String projectName;
        public String sourceType;
        public String sourceLabel;
        public String summary;
        public final ArrayList<String> visualScreens = new ArrayList<>();
        public final ArrayList<String> codeOnlyFiles = new ArrayList<>();
        public final ArrayList<String> importedDependencies = new ArrayList<>();
        public final ArrayList<String> warnings = new ArrayList<>();
        public final ArrayList<String> unsupportedFeatures = new ArrayList<>();

        public String toDisplayText() {
            StringBuilder sb = new StringBuilder();
            if (!TextUtils.isEmpty(summary)) {
                sb.append(summary).append("\n\n");
            }
            if (!TextUtils.isEmpty(scId)) {
                sb.append("Sketchware ID: ").append(scId).append('\n');
            }
            if (!TextUtils.isEmpty(projectName)) {
                sb.append("Project: ").append(projectName).append('\n');
            }
            if (!TextUtils.isEmpty(sourceLabel)) {
                sb.append("Source: ").append(sourceLabel).append('\n');
            }
            if (!visualScreens.isEmpty()) {
                sb.append("\nScreens:\n");
                for (String visualScreen : visualScreens) {
                    sb.append("• ").append(visualScreen).append('\n');
                }
            }
            if (!warnings.isEmpty()) {
                sb.append("\nWarnings:\n");
                for (String warning : warnings) {
                    sb.append("• ").append(warning).append('\n');
                }
            }
            if (!unsupportedFeatures.isEmpty()) {
                sb.append("\nUnsupported / degraded:\n");
                for (String item : unsupportedFeatures) {
                    sb.append("• ").append(item).append('\n');
                }
            }
            return sb.toString().trim();
        }
    }

    private static class DetectedProject {
        File rootDirectory;
        File appDirectory;
        File gradleFile;
        File manifestFile;
        File resDirectory;
        File layoutDirectory;
        File assetsDirectory;
        File jniLibsDirectory;
        ArrayList<File> sourceRoots;
        ArrayList<File> libsDirectories;
        File roundTripMetadataJson;
        File roundTripDataDir;
        String archiveLabel;
    }

    private static class GradleSummary {
        String applicationId;
        String namespace;
        String versionCode;
        String versionName;
        int minSdk;
        int targetSdk;
        final ArrayList<String> dependencies = new ArrayList<>();
        final ArrayList<String> warnings = new ArrayList<>();
    }

    private static class ManifestSummary {
        String packageName;
        String applicationLabel;
        String rawXml;
        final ArrayList<String> permissions = new ArrayList<>();
        final ArrayList<ManifestActivity> activities = new ArrayList<>();
        final ArrayList<String> services = new ArrayList<>();
        final ArrayList<String> receivers = new ArrayList<>();
        final ArrayList<String> providers = new ArrayList<>();
    }

    private static class ManifestActivity {
        String fullyQualifiedName;
        boolean launcher;
    }

    private static class GitHubRepoSpec {
        String owner;
        String repo;
        String branch;

        static GitHubRepoSpec parse(String url, String branch) {
            String cleaned = url.trim();
            if (cleaned.endsWith("/")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }
            cleaned = cleaned.replace("https://github.com/", "");
            cleaned = cleaned.replace("http://github.com/", "");
            if (cleaned.startsWith("github.com/")) {
                cleaned = cleaned.substring("github.com/".length());
            }
            String[] parts = cleaned.split("/");
            if (parts.length < 2) {
                throw new IllegalArgumentException("GitHub repository URL is invalid");
            }
            GitHubRepoSpec spec = new GitHubRepoSpec();
            spec.owner = parts[0];
            spec.repo = parts[1].replaceAll("\\.git$", "");
            spec.branch = branch;
            if ((spec.branch == null || spec.branch.isEmpty()) && parts.length >= 4 && "tree".equals(parts[2])) {
                StringBuilder branchBuilder = new StringBuilder(parts[3]);
                for (int i = 4; i < parts.length; i++) {
                    branchBuilder.append("/").append(parts[i]);
                }
                spec.branch = branchBuilder.toString();
            }
            return spec;
        }
    }
}
