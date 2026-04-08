package pro.sketchware.ai.tools;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.HashMap;

import a.a.a.ProjectBuilder;
import a.a.a.jC;
import a.a.a.kC;
import a.a.a.lC;
import a.a.a.wq;
import a.a.a.yB;
import a.a.a.yq;
import mod.hey.studios.compiler.kotlin.KotlinCompilerBridge;
import mod.hey.studios.project.proguard.ProguardHandler;
import mod.hey.studios.project.stringfog.StringfogHandler;
import mod.jbk.build.BuiltInLibraries;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.utility.FileUtil;

public final class BuildTools {

    private BuildTools() {
    }

    private static ToolResult error(String message) {
        return ToolResult.failure(null, message);
    }

    private static ToolResult success(JsonObject payload) {
        return ToolResult.success(null, payload.toString());
    }

    private static JsonObject scIdProperty() {
        JsonObject scId = new JsonObject();
        scId.addProperty("type", "string");
        scId.addProperty("description", "The project SC ID");
        return scId;
    }

    private static ToolResult requireProject(JsonObject arguments, ToolContext context) {
        if (!arguments.has("sc_id") || arguments.get("sc_id").isJsonNull()) {
            return error("sc_id is required");
        }
        String scId = arguments.get("sc_id").getAsString();
        if (scId.isEmpty()) {
            return error("sc_id is required");
        }
        if (!context.isProjectAllowed(scId)) {
            return error("Project " + scId + " is not in this workspace");
        }
        return null;
    }

    private static final class BuildArtifacts {
        final yq project;
        final ProjectBuilder builder;

        BuildArtifacts(yq project, ProjectBuilder builder) {
            this.project = project;
            this.builder = builder;
        }
    }

    private static BuildArtifacts prepareBuild(ToolContext context, String scId) throws Exception {
        Context appContext = context.getAppContext();
        HashMap<String, Object> metadata = lC.b(scId);
        if (metadata == null) {
            throw new IllegalStateException("Project metadata not found for " + scId);
        }

        yq project = new yq(appContext, wq.d(scId), metadata);
        FileUtil.deleteFile(project.projectMyscPath);

        context.reportProgress("Preparing project files…", 5);
        project.c(appContext);
        project.a();
        project.a(appContext, wq.e("600"));

        if (yB.a(lC.b(scId), "custom_icon")) {
            project.aa(wq.e() + File.separator + scId + File.separator + "mipmaps");
            if (yB.a(lC.b(scId), "isIconAdaptive", false)) {
                project.createLauncherIconXml("""
                        <?xml version=\"1.0\" encoding=\"utf-8\"?>
                        <adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\" >
                        <background android:drawable=\"@mipmap/ic_launcher_background\"/>
                        <foreground android:drawable=\"@mipmap/ic_launcher_foreground\"/>
                        <monochrome android:drawable=\"@mipmap/ic_launcher_monochrome\"/>
                        </adaptive-icon>""");
            } else {
                project.a(wq.e() + File.separator + scId + File.separator + "icon.png");
            }
        }

        context.reportProgress("Generating source code…", 12);
        kC resources = jC.d(scId);
        resources.b(project.resDirectoryPath + File.separator + "drawable-xhdpi");
        resources = jC.d(scId);
        resources.c(project.resDirectoryPath + File.separator + "raw");
        resources = jC.d(scId);
        resources.a(project.assetsPath + File.separator + "fonts");

        ProjectBuilder builder = new ProjectBuilder((progress, step) -> {
            if (context.isCancelled()) {
                return;
            }
            int mapped = Math.min(95, Math.max(8, step * 4));
            context.reportProgress(progress, mapped);
        }, appContext, project);
        builder.setBuildAppBundle(false);

        var fileManager = jC.b(scId);
        var dataManager = jC.a(scId);
        var libraryManager = jC.c(scId);

        project.a(libraryManager, fileManager, dataManager, yq.ExportType.DEBUG_APP);
        builder.buildBuiltInLibraryInformation();
        project.b(fileManager, dataManager, libraryManager, builder.getBuiltInLibraryManager());
        project.f();
        project.e();

        return new BuildArtifacts(project, builder);
    }

    private static void executeDebugBuild(ToolContext context, String scId) throws Exception {
        BuildArtifacts artifacts = prepareBuild(context, scId);
        ProjectBuilder builder = artifacts.builder;

        if (context.isCancelled()) return;
        context.reportProgress("Extracting compile assets…", 20);
        builder.maybeExtractAapt2();
        BuiltInLibraries.extractCompileAssets((progress, step) -> context.reportProgress(progress, 26));

        if (context.isCancelled()) return;
        context.reportProgress("Compiling resources…", 35);
        builder.compileResources();

        if (context.isCancelled()) return;
        context.reportProgress("Generating view binding…", 45);
        builder.generateViewBinding();

        if (context.isCancelled()) return;
        context.reportProgress("Compiling Kotlin…", 52);
        try {
            KotlinCompilerBridge.compileKotlinCodeIfPossible((progress, step) -> context.reportProgress(progress, 52), builder);
        } catch (Throwable throwable) {
            if (throwable instanceof Exception exception) {
                throw exception;
            }
            throw new RuntimeException(throwable);
        }

        if (context.isCancelled()) return;
        context.reportProgress("Compiling Java…", 62);
        builder.compileJavaCode();

        if (context.isCancelled()) return;
        new StringfogHandler(scId).start((progress, step) -> context.reportProgress(progress, 68), builder);

        if (context.isCancelled()) return;
        new ProguardHandler(scId).start((progress, step) -> context.reportProgress(progress, 72), builder);

        if (context.isCancelled()) return;
        context.reportProgress(builder.getDxRunningText(), 80);
        builder.createDexFilesFromClasses();

        if (context.isCancelled()) return;
        context.reportProgress("Merging DEX files…", 88);
        builder.getDexFilesReady();

        if (context.isCancelled()) return;
        context.reportProgress("Building APK…", 94);
        builder.buildApk();
        builder.signDebugApk();
    }

    private static void requestInstall(Context context, String apkPath) {
        File apkFile = new File(apkPath);
        Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", apkFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        context.startActivity(intent);
    }

    public static class BuildProjectTool implements AgentTool {
        @Override
        public String getName() {
            return "build_project";
        }

        @Override
        public String getDescription() {
            return "Builds a workspace project into a signed debug APK and returns its artifact path and compile log path.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject properties = new JsonObject();
            properties.add("sc_id", scIdProperty());
            schema.add("properties", properties);
            JsonArray required = new JsonArray();
            required.add("sc_id");
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            ToolResult validation = requireProject(arguments, context);
            if (validation != null) {
                return validation;
            }
            String scId = arguments.get("sc_id").getAsString();

            try {
                executeDebugBuild(context, scId);
                if (context.isCancelled()) {
                    return error("Build cancelled");
                }

                yq project = new yq(context.getAppContext(), wq.d(scId), lC.b(scId));
                JsonObject result = new JsonObject();
                result.addProperty("sc_id", scId);
                result.addProperty("status", "built");
                result.addProperty("artifact_type", "debug_apk");
                result.addProperty("artifact_path", project.finalToInstallApkPath);
                result.addProperty("compile_log_path", context.getProjectCompileLogFile(scId).getAbsolutePath());
                result.addProperty("installable", true);
                result.addProperty("message", "Debug APK built successfully");
                return success(result);
            } catch (Exception e) {
                JsonObject result = new JsonObject();
                result.addProperty("sc_id", scId);
                result.addProperty("status", "failed");
                result.addProperty("compile_log_path", context.getProjectCompileLogFile(scId).getAbsolutePath());
                result.addProperty("message", e.getMessage() != null ? e.getMessage() : "Build failed");
                return ToolResult.failure(null, result.toString());
            }
        }
    }

    public static class RunProjectTool implements AgentTool {
        @Override
        public String getName() {
            return "run_project";
        }

        @Override
        public String getDescription() {
            return "Builds a workspace project into a signed debug APK and immediately opens the Android package installer.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject properties = new JsonObject();
            properties.add("sc_id", scIdProperty());
            schema.add("properties", properties);
            JsonArray required = new JsonArray();
            required.add("sc_id");
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            ToolResult validation = requireProject(arguments, context);
            if (validation != null) {
                return validation;
            }
            String scId = arguments.get("sc_id").getAsString();

            try {
                executeDebugBuild(context, scId);
                if (context.isCancelled()) {
                    return error("Run cancelled");
                }

                yq project = new yq(context.getAppContext(), wq.d(scId), lC.b(scId));
                requestInstall(context.getAppContext(), project.finalToInstallApkPath);

                JsonObject result = new JsonObject();
                result.addProperty("sc_id", scId);
                result.addProperty("status", "install_prompt_opened");
                result.addProperty("artifact_type", "debug_apk");
                result.addProperty("artifact_path", project.finalToInstallApkPath);
                result.addProperty("compile_log_path", context.getProjectCompileLogFile(scId).getAbsolutePath());
                result.addProperty("installable", true);
                result.addProperty("message", "Build completed and install prompt opened");
                return success(result);
            } catch (Exception e) {
                JsonObject result = new JsonObject();
                result.addProperty("sc_id", scId);
                result.addProperty("status", "failed");
                result.addProperty("compile_log_path", context.getProjectCompileLogFile(scId).getAbsolutePath());
                result.addProperty("message", e.getMessage() != null ? e.getMessage() : "Run failed");
                return ToolResult.failure(null, result.toString());
            }
        }
    }
}
