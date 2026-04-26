package pro.sketchware.ai.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.ai.models.ToolResult;

/**
 * Phase3Tools — Advanced Development Tools
 *
 * 6 AI agent tools:
 *   analyze_code          — Static analysis of Java source files
 *   search_maven          — Search built-in library catalog
 *   create_from_template  — Create app from named template
 *   add_locale_strings    — Add translated string resources
 *   review_source_code    — Review Java code quality
 *   validate_rtl_layout   — Detect RTL layout issues
 */
public final class Phase3Tools {

    private Phase3Tools() {}

    // ── Package-level helpers ─────────────────────────────────────────────

    static ToolResult ok(String output)  { return ToolResult.success(null, output); }
    static ToolResult err(String msg)    { return ToolResult.failure(null, msg); }

    static String req(JsonObject args, String key) {
        if (!args.has(key) || args.get(key).isJsonNull()) return null;
        return args.get(key).getAsString().trim();
    }

    static String readFile(File f) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            char[] buf = new char[4096]; int n;
            while ((n = br.read(buf)) != -1) sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    static void writeFile(File f, String content) throws IOException {
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        try (FileWriter fw = new FileWriter(f)) { fw.write(content); }
    }

    static void addP(JsonObject props, String key, String type, String desc) {
        JsonObject p = new JsonObject();
        p.addProperty("type", type);
        p.addProperty("description", desc);
        props.add(key, p);
    }

    // ── Tool 1: analyze_code ─────────────────────────────────────────────

    public static class AnalyzeCodeTool implements AgentTool {
        @Override public String getName() { return "analyze_code"; }

        @Override public String getDescription() {
            return "Performs static analysis on a Java source file in a Sketchware Pro project. "
                 + "Detects unused imports, setText(int) bugs, empty catch blocks, "
                 + "Thread.sleep without try-catch, and suggests best practices. "
                 + "Use before build_project to catch errors early.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject s = new JsonObject(); s.addProperty("type", "object");
            JsonObject p = new JsonObject();
            addP(p, "sc_id",     "string", "Project ID");
            addP(p, "file_path", "string", "Path, e.g. \'java/com/example/MainActivity.java\'");
            s.add("properties", p);
            JsonArray r = new JsonArray(); r.add("sc_id"); r.add("file_path");
            s.add("required", r);
            return s;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = req(args, "sc_id");
            String path = req(args, "file_path");
            if (scId == null || path == null) return err("sc_id and file_path are required");
            if (!ctx.isProjectAllowed(scId)) return err("Access denied: project " + scId);
            ctx.reportProgress("Analysing source code...", -1, true);

            // Resolve file path
            String relative = path;
            if (relative.startsWith("java/")) relative = relative.substring(5);
            else if (relative.startsWith("app/src/main/java/")) relative = relative.substring(18);
            File file = new File(ctx.getProjectJavaDir(scId), relative);
            if (!file.exists()) file = new File(ctx.getProjectDataDir(scId), path);
            if (!file.exists()) return err("File not found: " + path);

            String source;
            try { source = readFile(file); }
            catch (IOException e) { return err("Could not read file: " + e.getMessage()); }

            StringBuilder report = new StringBuilder();
            report.append("Code Analysis: ").append(file.getName()).append("\n");
            report.append("=".repeat(50)).append("\n\n");

            List<String> issues = new ArrayList<>();
            List<String> suggestions = new ArrayList<>();
            String[] lines = source.split("\n");
            int lineNum = 0;

            for (String line : lines) {
                lineNum++;
                String t = line.trim();

                if (t.startsWith("import ") && !t.startsWith("import static")) {
                    String cls = t.replace("import ", "").replace(";", "").trim();
                    String simple = cls.contains(".") ? cls.substring(cls.lastIndexOf('.')+1) : cls;
                    if (!simple.isEmpty() && !simple.equals("*")) {
                        long uses = java.util.Arrays.stream(lines)
                                .filter(l -> !l.trim().startsWith("import"))
                                .filter(l -> l.contains(simple)).count();
                        if (uses == 0)
                            issues.add("L" + lineNum + ": Possibly unused import: " + cls);
                    }
                }
                if (t.matches(".*\\.setText\\(\\d+\\).*"))

                    issues.add("L" + lineNum + ": setText(int) sets resource ID. Use setText(String.valueOf(n)).");
                if ((t.equals("} catch (Exception e) {") || t.equals("catch (Exception e) {"))
                        && lineNum < lines.length && lines[lineNum].trim().equals("}"))
                    issues.add("L" + lineNum + ": Empty catch block silently swallows exceptions.");
                if (t.contains("Thread.sleep(")) {
                    boolean hasTry = false;
                    for (int i = Math.max(0, lineNum-3); i < lineNum-1; i++) {
                        if (lines[i].contains("try {") || lines[i].contains("try{")) { hasTry = true; break; }
                    }
                    if (!hasTry) issues.add("L" + lineNum + ": Thread.sleep() needs try-catch (InterruptedException).");
                }
                if (t.contains("AsyncTask"))
                    suggestions.add("L" + lineNum + ": AsyncTask is deprecated. Use ExecutorService + Handler.");
                if (t.contains("System.out.println"))
                    suggestions.add("L" + lineNum + ": Replace System.out.println with Log.d(TAG, ...).");
            }

            if (issues.isEmpty() && suggestions.isEmpty()) {
                report.append("No obvious issues detected. Code looks good.\n");
            } else {
                if (!issues.isEmpty()) {
                    report.append("ISSUES (").append(issues.size()).append("):\n");
                    for (String i : issues) report.append("  Warning: ").append(i).append("\n");
                    report.append("\n");
                }
                if (!suggestions.isEmpty()) {
                    report.append("SUGGESTIONS (").append(suggestions.size()).append("):\n");
                    for (String s : suggestions) report.append("  Tip: ").append(s).append("\n");
                }
            }
            report.append("\nTotal lines analysed: ").append(lineNum);
            return ok(report.toString());
        }
    }

    // ── Tool 2: search_maven ─────────────────────────────────────────────

    public static class SearchMavenTool implements AgentTool {
        @Override public String getName() { return "search_maven"; }

        @Override public String getDescription() {
            return "Looks up a well-known Android library and returns its Gradle dependency string. "
                 + "Supports Retrofit, OkHttp, Glide, Picasso, Room, Lifecycle, Hilt, "
                 + "Gson, Moshi, Coil, Volley, Lottie, ExoPlayer, Firebase, Material, and more.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject s = new JsonObject(); s.addProperty("type", "object");
            JsonObject p = new JsonObject();
            addP(p, "library_name", "string", "Library name, e.g. \'Retrofit\', \'Glide\', \'Room\'");
            s.add("properties", p);
            JsonArray r = new JsonArray(); r.add("library_name");
            s.add("required", r);
            return s;
        }

        private static final String[][] CATALOG = {
            {"retrofit",    "com.squareup.retrofit2:retrofit:2.11.0",        "Type-safe HTTP client."},
            {"okhttp",      "com.squareup.okhttp3:okhttp:4.12.0",            "HTTP client. Add logging interceptor too."},
            {"glide",       "com.github.bumptech.glide:glide:4.16.0",        "Image loading. Add compiler annotation processor."},
            {"picasso",     "com.squareup.picasso:picasso:2.8",              "Simple image loading by Square."},
            {"coil",        "io.coil-kt:coil:2.6.0",                        "Kotlin-first image loading."},
            {"room",        "androidx.room:room-runtime:2.6.1",              "SQLite ORM. Add room-compiler annotation processor."},
            {"lifecycle",   "androidx.lifecycle:lifecycle-viewmodel:2.8.0",  "ViewModel + LiveData."},
            {"hilt",        "com.google.dagger:hilt-android:2.51",           "Dependency injection. Requires Hilt plugin."},
            {"gson",        "com.google.code.gson:gson:2.10.1",              "JSON serialization. No setup needed."},
            {"moshi",       "com.squareup.moshi:moshi:1.15.1",              "Modern JSON library."},
            {"volley",      "com.android.volley:volley:1.2.1",              "HTTP networking by Google."},
            {"lottie",      "com.airbnb.android:lottie:6.4.0",              "After Effects animations."},
            {"exoplayer",   "androidx.media3:media3-exoplayer:1.3.1",        "Media playback. Add media3-ui for UI."},
            {"mpandroidchart","com.github.PhilJay:MPAndroidChart:v3.1.0",   "Charts. Add JitPack repo."},
            {"material",    "com.google.android.material:material:1.12.0",   "Material Design components."},
            {"firebase_auth","com.google.firebase:firebase-auth:23.0.0",    "Firebase Authentication."},
            {"firebase_db", "com.google.firebase:firebase-database:21.0.0", "Firebase Realtime Database."},
            {"rxjava",      "io.reactivex.rxjava3:rxjava:3.1.8",            "Reactive extensions."},
            {"workmanager", "androidx.work:work-runtime:2.9.0",             "Background task scheduling."},
            {"datastore",   "androidx.datastore:datastore-preferences:1.1.1","Modern SharedPreferences replacement."},
            {"zxing",       "com.journeyapps:zxing-android-embedded:4.3.0", "QR/barcode scanner."},
        };

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String query = req(args, "library_name");
            if (query == null) return err("library_name is required");
            ctx.reportProgress("Searching library catalog...", -1, true);

            String q = query.toLowerCase().replace(" ", "").replace("-", "");
            List<String[]> matches = new ArrayList<>();
            for (String[] entry : CATALOG) {
                if (entry[0].replace("-","").contains(q) || q.contains(entry[0].replace("-",""))
                        || entry[1].toLowerCase().contains(q)) {
                    matches.add(entry);
                }
            }

            if (matches.isEmpty()) {
                return ok("No library found for: " + query + "\n\n"
                        + "Use download_dependency with full Maven coordinate:\n"
                        + "  group:artifact:version (e.g. com.squareup.retrofit2:retrofit:2.11.0)\n"
                        + "Search at: https://mvnrepository.com");
            }
            StringBuilder sb = new StringBuilder("Library Search: " + query + "\n" + "=".repeat(40) + "\n\n");
            for (String[] m : matches) {
                sb.append("Dependency: ").append(m[1]).append("\n");
                sb.append("Notes:      ").append(m[2]).append("\n");
                sb.append("Install:    Use download_dependency tool\n\n");
            }
            return ok(sb.toString());
        }
    }

    // ── Tool 3: create_from_template ─────────────────────────────────────

    public static class CreateFromTemplateTool implements AgentTool {
        @Override public String getName() { return "create_from_template"; }

        @Override public String getDescription() {
            return "Returns a step-by-step plan to build a Sketchware Pro app from a named template. "
                 + "Templates: calculator, todo_list, notes, login_screen, splash_screen, "
                 + "settings_screen, profile_screen, list_detail. "
                 + "Each plan uses only available tools (add_view, add_block, write_file, etc.).";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject s = new JsonObject(); s.addProperty("type", "object");
            JsonObject p = new JsonObject();
            addP(p, "template_name", "string", "calculator, todo_list, notes, login_screen, splash_screen");
            addP(p, "app_name",      "string", "Display name, e.g. \'My Calculator\'");
            addP(p, "package_name",  "string", "Java package, e.g. \'com.example.calc\'");
            s.add("properties", p);
            JsonArray r = new JsonArray();
            r.add("template_name"); r.add("app_name"); r.add("package_name");
            s.add("required", r);
            return s;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String template = req(args, "template_name");
            String appName  = req(args, "app_name");
            String pkg      = req(args, "package_name");
            if (template == null || appName == null || pkg == null)
                return err("template_name, app_name and package_name are required");
            ctx.reportProgress("Generating template plan: " + template + "...", -1, true);

            switch (template.toLowerCase().trim()) {
                case "calculator":
                    return ok("TEMPLATE: Calculator App — " + appName + "\n\n"
                        + "1. create_project  name=\"" + appName + "\"  package=\"" + pkg + "\"\n"
                        + "2. add_view root LinearLayout vertical\n"
                        + "3. add_view TextView id=tvDisplay text=0 textSize=40 gravity=end\n"
                        + "4. add_view GridLayout for digit buttons (0-9, +, -, *, /, =, C)\n"
                        + "5. add_view Button for each digit and operator\n"
                        + "6. add_block MainActivity.onCreate: init String currentInput, String operator\n"
                        + "7. add_block each digit button onClick: currentInput+=digit; tvDisplay.setText(currentInput)\n"
                        + "8. add_block operator buttons: store operator, clear currentInput\n"
                        + "9. add_block equals button: evaluate and show result\n"
                        + "10. build_project\n\n"
                        + "Use addSourceDirectly opCode for Java expression evaluation.");

                case "todo_list":
                    return ok("TEMPLATE: Todo List App — " + appName + "\n\n"
                        + "1. create_project  name=\"" + appName + "\"  package=\"" + pkg + "\"\n"
                        + "2. add_view root LinearLayout vertical\n"
                        + "3. add_view LinearLayout horizontal: EditText id=etTask + Button id=btnAdd\n"
                        + "4. add_view ListView id=lvTasks  layout.weight=1\n"
                        + "5. write_file MainActivity.java with ArrayList<String> + ArrayAdapter\n"
                        + "6. add_block onCreate: init adapter, attach to ListView\n"
                        + "7. add_block btnAdd.onClick: add text, notifyDataSetChanged\n"
                        + "8. add_block lvTasks.onItemLongClick: delete dialog\n"
                        + "9. build_project");

                case "login_screen":
                    return ok("TEMPLATE: Login Screen — " + appName + "\n\n"
                        + "1. create_project  name=\"" + appName + "\"  package=\"" + pkg + "\"\n"
                        + "2. add_view root: ScrollView > LinearLayout vertical\n"
                        + "3. add_view ImageView id=ivLogo\n"
                        + "4. add_view EditText id=etEmail  inputType=textEmailAddress\n"
                        + "5. add_view EditText id=etPassword  inputType=textPassword\n"
                        + "6. add_view Button id=btnLogin text=Login\n"
                        + "7. add_view TextView id=tvSignup text=Create account\n"
                        + "8. create_activity HomeActivity\n"
                        + "9. add_block btnLogin.onClick: validate, startActivity(HomeActivity)\n"
                        + "10. build_project");

                case "splash_screen":
                    return ok("TEMPLATE: Splash Screen — " + appName + "\n\n"
                        + "1. create_project  name=\"" + appName + "\"  package=\"" + pkg + "\"\n"
                        + "2. add_view root RelativeLayout fullscreen\n"
                        + "3. add_view ImageView id=ivLogo centered\n"
                        + "4. add_view ProgressBar id=pbLoading below logo\n"
                        + "5. create_activity MainActivity\n"
                        + "6. add_block SplashActivity.onCreate: addSourceDirectly with:\n"
                        + "   new Handler(Looper.getMainLooper()).postDelayed(() -> {\n"
                        + "     startActivity(new Intent(this, MainActivity.class)); finish();\n"
                        + "   }, 2000);\n"
                        + "7. build_project");

                case "notes":
                    return ok("TEMPLATE: Notes App — " + appName + "\n\n"
                        + "1. create_project  name=\"" + appName + "\"  package=\"" + pkg + "\"\n"
                        + "2. create_activity NoteEditActivity\n"
                        + "3. Main activity: add_view RecyclerView/ListView + FloatingActionButton\n"
                        + "4. NoteEditActivity: EditText title + EditText multiline body\n"
                        + "5. Store notes as JSON in SharedPreferences\n"
                        + "6. FAB onClick: startActivity(NoteEditActivity)\n"
                        + "7. Save button: save JSON and finish()\n"
                        + "8. build_project");

                default:
                    return ok("Unknown template: " + template + "\n\n"
                        + "Available: calculator, todo_list, login_screen, splash_screen, notes");
            }
        }
    }

    // ── Tool 4: add_locale_strings ───────────────────────────────────────

    public static class AddLocaleStringsTool implements AgentTool {
        @Override public String getName() { return "add_locale_strings"; }

        @Override public String getDescription() {
            return "Adds a translated string resource to a Sketchware Pro project. "
                 + "Supported locales: ar (Arabic/RTL), fr (French), es (Spanish), "
                 + "de (German), tr (Turkish), hi (Hindi), ur (Urdu/RTL), zh (Chinese). "
                 + "Creates values-{locale}/strings.xml automatically.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject s = new JsonObject(); s.addProperty("type", "object");
            JsonObject p = new JsonObject();
            addP(p, "sc_id",       "string", "Project ID");
            addP(p, "locale",      "string", "Locale code: ar, fr, es, de, tr, hi, ur, zh");
            addP(p, "string_name", "string", "String resource name, e.g. \'app_name\'");
            addP(p, "translation", "string", "Translated text value");
            s.add("properties", p);
            JsonArray r = new JsonArray();
            r.add("sc_id"); r.add("locale"); r.add("string_name"); r.add("translation");
            s.add("required", r);
            return s;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = req(args, "sc_id"), locale = req(args, "locale");
            String name = req(args, "string_name"), value = req(args, "translation");
            if (scId == null || locale == null || name == null || value == null)
                return err("sc_id, locale, string_name and translation are required");
            if (!ctx.isProjectAllowed(scId)) return err("Access denied: project " + scId);

            String[] supported = {"ar","fr","es","de","tr","hi","ur","zh","ja","ko","pt","ru","it","nl"};
            boolean valid = false;
            for (String l : supported) if (l.equals(locale)) { valid = true; break; }
            if (!valid) return err("Unsupported locale: " + locale + ". Supported: ar,fr,es,de,tr,hi,ur,zh,...");

            ctx.reportProgress("Adding " + locale + " translation...", -1, true);

            File resDir = new File(ctx.getProjectResourceDir(scId), "values-" + locale);
            resDir.mkdirs();
            File stringsFile = new File(resDir, "strings.xml");

            String existing = "";
            if (stringsFile.exists()) {
                try { existing = readFile(stringsFile); } catch (IOException ignored) {}
            }

            String escaped = value.replace("&", "&amp;").replace("<", "&lt;")
                    .replace(">", "&gt;").replace("\"", "&quot;");
            String entry = "    <string name=\"" + name + "\">" + escaped + "</string>\n";

            if (existing.contains("<resources>")) {
                existing = existing.replace("</resources>", entry + "</resources>");
            } else {
                boolean rtl = locale.equals("ar") || locale.equals("ur");
                existing = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + (rtl ? "<!-- RTL locale: " + locale + " -->\n" : "")
                        + "<resources>\n" + entry + "</resources>\n";
            }

            try { writeFile(stringsFile, existing); }
            catch (IOException e) { return err("Write failed: " + e.getMessage()); }

            boolean isRtl = locale.equals("ar") || locale.equals("ur");
            return ok("Translation added: " + locale + " / " + name + " = \"" + value + "\"\n"
                    + "File: values-" + locale + "/strings.xml\n"
                    + (isRtl ? "RTL: Add android:supportsRtl=\"true\" in AndroidManifest." : ""));
        }
    }

    // ── Tool 5: review_source_code ───────────────────────────────────────

    public static class ReviewSourceCodeTool implements AgentTool {
        @Override public String getName() { return "review_source_code"; }

        @Override public String getDescription() {
            return "Reviews Java source code for Android best practice issues: "
                 + "memory leaks (static Context, Handler without Looper), deprecated APIs, "
                 + "missing error handling, logging issues. "
                 + "Pass the source code as a string. Returns findings with fix suggestions.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject s = new JsonObject(); s.addProperty("type", "object");
            JsonObject p = new JsonObject();
            addP(p, "sc_id",         "string", "Project ID");
            addP(p, "source_code",   "string", "Full Java source code to review");
            addP(p, "activity_name", "string", "Activity name for context (optional)");
            s.add("properties", p);
            JsonArray r = new JsonArray(); r.add("sc_id"); r.add("source_code");
            s.add("required", r);
            return s;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId   = req(args, "sc_id");
            String code   = req(args, "source_code");
            String act    = req(args, "activity_name");
            if (scId == null || code == null) return err("sc_id and source_code are required");
            if (!ctx.isProjectAllowed(scId)) return err("Access denied: project " + scId);
            ctx.reportProgress("Reviewing source code...", -1, true);

            List<String> findings = new ArrayList<>();
            if (code.contains("new Handler()") && !code.contains("Looper.getMainLooper()"))
                findings.add("MEMORY LEAK: Use new Handler(Looper.getMainLooper()) in API 30+.");
            if (code.contains("AsyncTask"))
                findings.add("DEPRECATED: AsyncTask removed in API 30. Use ExecutorService + Handler.");
            if (code.contains("static Context") || code.contains("static Activity"))
                findings.add("MEMORY LEAK: Static Context/Activity reference. Use WeakReference<Activity>.");
            if (code.contains("e.printStackTrace()"))
                findings.add("BAD PRACTICE: Replace e.printStackTrace() with Log.e(TAG, message, e).");
            if (code.contains("System.out.println"))
                findings.add("BAD PRACTICE: Replace System.out.println with Log.d(TAG, ...).");
            if (code.contains("getApplicationContext()") && code.contains("AlertDialog"))
                findings.add("CRASH RISK: Do not use getApplicationContext() for dialogs. Use Activity context.");
            if (!code.contains("TAG") && code.contains("Log."))
                findings.add("STYLE: Add: private static final String TAG = \""
                        + (act != null ? act : "MyActivity") + "\";");

            StringBuilder sb = new StringBuilder("Code Review");
            if (act != null) sb.append(": ").append(act);
            sb.append("\n").append("=".repeat(40)).append("\n\n");

            if (findings.isEmpty()) {
                sb.append("No major issues found. Code looks good for Sketchware Pro.\n");
                sb.append("\nGeneral tips:\n");
                sb.append("  - Add null checks before using intent extras\n");
                sb.append("  - Use try-with-resources for streams\n");
            } else {
                sb.append("FINDINGS (").append(findings.size()).append("):\n\n");
                for (int i = 0; i < findings.size(); i++)
                    sb.append(i+1).append(". ").append(findings.get(i)).append("\n\n");
            }
            sb.append("\nTo apply: use write_file with corrected source, then build_project.");
            return ok(sb.toString());
        }
    }

    // ── Tool 6: validate_rtl_layout ──────────────────────────────────────

    public static class ValidateRtlLayoutTool implements AgentTool {
        @Override public String getName() { return "validate_rtl_layout"; }

        @Override public String getDescription() {
            return "Validates a Sketchware Pro activity layout for RTL compatibility. "
                 + "Detects hardcoded left/right margins (use Start/End), "
                 + "missing layoutDirection, and gravity issues for Arabic/Hebrew/Urdu apps.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject s = new JsonObject(); s.addProperty("type", "object");
            JsonObject p = new JsonObject();
            addP(p, "sc_id",         "string", "Project ID");
            addP(p, "activity_name", "string", "Activity name without .java");
            s.add("properties", p);
            JsonArray r = new JsonArray(); r.add("sc_id"); r.add("activity_name");
            s.add("required", r);
            return s;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId    = req(args, "sc_id");
            String actName = req(args, "activity_name");
            if (scId == null || actName == null) return err("sc_id and activity_name are required");
            if (!ctx.isProjectAllowed(scId)) return err("Access denied: project " + scId);
            ctx.reportProgress("Validating RTL compatibility...", -1, true);

            File viewFile = new File(ctx.getProjectDataDir(scId), "view");
            if (!viewFile.exists()) return err("No view file found for project " + scId);

            String raw;
            try { raw = readFile(viewFile); }
            catch (IOException e) { return err("Could not read view file: " + e.getMessage()); }

            List<String> issues = new ArrayList<>();
            if (raw.contains("\"marginLeft\"") && !raw.contains("\"marginStart\""))
                issues.add("marginLeft without marginStart — use marginStart for RTL");
            if (raw.contains("\"marginRight\"") && !raw.contains("\"marginEnd\""))
                issues.add("marginRight without marginEnd — use marginEnd for RTL");
            if (raw.contains("\"paddingLeft\"") && !raw.contains("\"paddingStart\""))
                issues.add("paddingLeft without paddingStart — use paddingStart for RTL");
            if (raw.contains("\"gravity\":3") || raw.contains("\"gravity\": 3"))
                issues.add("gravity=LEFT (3) — use END (5) for RTL-safe alignment");
            if (!raw.contains("\"layoutDirection\"") && raw.length() > 100)
                issues.add("No layoutDirection — add layoutDirection=locale on root view");

            StringBuilder sb = new StringBuilder("RTL Validation: " + actName + "\n" + "=".repeat(40) + "\n\n");
            if (issues.isEmpty()) {
                sb.append("No RTL issues detected.\n");
                sb.append("Tip: Ensure AndroidManifest has android:supportsRtl=\"true\".\n");
            } else {
                sb.append("RTL ISSUES (").append(issues.size()).append("):\n");
                for (String i : issues) sb.append("  Warning: ").append(i).append("\n");
                sb.append("\nFixes: use modify_view to update marginStart/End instead of Left/Right.\n");
            }
            return ok(sb.toString());
        }
    }
}
