package pro.sketchware.ai.tools;

import android.content.Context;
import android.content.Intent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pro.sketchware.ai.models.ToolResult;

/**
 * LayoutGeneratorTool — AI-driven Android XML layout generation from text descriptions.
 *
 * Ports the GeradorDeLayout concept from Sketchware-IA into the nikit AgentTool system.
 * The AI describes what it wants, this tool generates a proper Sketchware-compatible
 * XML layout and writes it live into the project's view file with a broadcast reload.
 *
 * This tool works at the XML level (human-readable) vs LiveUiPreviewTool which works
 * at the ViewBean JSON level. Both approaches are available to the AI agent.
 *
 * Registered as tool name: "generate_layout_from_description"
 */
public final class LayoutGeneratorTool implements AgentTool {

    /** Broadcast sent after layout is written — DesignActivity reloads live. */
    public static final String ACTION_LIVE_LAYOUT_RELOAD =
            "pro.sketchware.ai.ACTION_LIVE_LAYOUT_RELOAD";

    @Override
    public String getName() {
        return "generate_layout_from_description";
    }

    @Override
    public String getDescription() {
        return "Generates a Sketchware-compatible Android XML layout from a natural language description "
                + "and applies it to the specified activity. Use this to create or replace an entire "
                + "screen layout based on a design requirement. "
                + "Supported components: LinearLayout, RelativeLayout, ScrollView, HorizontalScrollView, "
                + "CardView, Button, TextView, EditText, ImageView, RecyclerView, Switch, SeekBar, "
                + "ProgressBar, CheckBox, RadioButton, Spinner, WebView, FAB, and more. "
                + "After applying, the design editor reloads the layout live.";
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject scId = new JsonObject();
        scId.addProperty("type", "string");
        scId.addProperty("description", "Project ID (sc_id)");
        props.add("sc_id", scId);

        JsonObject activityName = new JsonObject();
        activityName.addProperty("type", "string");
        activityName.addProperty("description",
                "Activity name, e.g. 'main' for main.xml");
        props.add("activity_name", activityName);

        JsonObject description = new JsonObject();
        description.addProperty("type", "string");
        description.addProperty("description",
                "Natural language description of the desired layout. "
                        + "Be specific about views, their arrangement, colors, and purpose. "
                        + "Example: 'A login screen with a centered logo at the top, "
                        + "email EditText, password EditText with toggle, and a blue Login button'");
        props.add("description", description);

        JsonObject xmlLayout = new JsonObject();
        xmlLayout.addProperty("type", "string");
        xmlLayout.addProperty("description",
                "Optional: Provide the complete Android XML layout directly if you already "
                        + "know exactly what XML to use. If provided, description is ignored.");
        props.add("xml_layout", xmlLayout);

        schema.add("properties", props);

        JsonArray required = new JsonArray();
        required.add("sc_id");
        required.add("activity_name");
        schema.add("required", required);

        return schema;
    }

    @Override
    public ToolResult execute(JsonObject arguments, ToolContext context) {
        String scId       = arguments.has("sc_id")         ? arguments.get("sc_id").getAsString().trim()          : "";
        String actName    = arguments.has("activity_name") ? arguments.get("activity_name").getAsString().trim()  : "";
        String desc       = arguments.has("description")   ? arguments.get("description").getAsString().trim()    : "";
        String xmlLayout  = arguments.has("xml_layout")    ? arguments.get("xml_layout").getAsString().trim()     : "";

        if (scId.isEmpty())   return ToolResult.failure(null, "Error: sc_id is required.");
        if (actName.isEmpty()) return ToolResult.failure(null, "Error: activity_name is required.");
        if (desc.isEmpty() && xmlLayout.isEmpty())
            return ToolResult.failure(null, "Error: either description or xml_layout is required.");

        try {
            String finalXml;

            if (!xmlLayout.isEmpty()) {
                // Use provided XML directly
                finalXml = cleanXml(xmlLayout);
            } else {
                // Build layout XML from description using Sketchware-IA's approach
                finalXml = buildLayoutFromDescription(desc, actName);
            }

            if (!looksLikeXml(finalXml)) {
                return ToolResult.failure(null,
                        "Generated content is not valid XML. Try being more specific in the description.");
            }

            // Write the XML to the resource layout file
            File sketchwareDir = new File(
                    android.os.Environment.getExternalStorageDirectory(), ".sketchware");
            File layoutDir = new File(sketchwareDir,
                    "mysc/" + scId + "/app/src/main/res/layout");
            layoutDir.mkdirs();

            String xmlFileName = actName.endsWith(".xml") ? actName : actName + ".xml";
            File layoutFile = new File(layoutDir, xmlFileName);

            try (FileWriter writer = new FileWriter(layoutFile)) {
                writer.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
                writer.write(finalXml);
            }

            // Also update the Sketchware view file (ViewBean format) for consistency
            broadcastLayoutReload(context.getAppContext(), scId, actName.replace(".xml", ""));

            return ToolResult.success(null,
                    "Layout generated and applied successfully!\n"
                            + "Activity: " + actName + "\n"
                            + "File: " + layoutFile.getAbsolutePath() + "\n"
                            + "XML preview (first 800 chars):\n"
                            + (finalXml.length() > 800 ? finalXml.substring(0, 800) + "..." : finalXml));

        } catch (Exception e) {
            return ToolResult.failure(null,
                    "Failed to generate layout: " + e.getMessage());
        }
    }

    /**
     * Builds a complete layout XML based on a description.
     * Uses a template-based approach matching common UI patterns.
     */
    private String buildLayoutFromDescription(String description, String activityName) {
        String lower = description.toLowerCase();

        // Login / Auth screens
        if (lower.contains("login") || lower.contains("sign in") || lower.contains("auth")) {
            return buildLoginLayout(description);
        }
        // Registration screens
        if (lower.contains("register") || lower.contains("sign up") || lower.contains("signup")) {
            return buildRegisterLayout(description);
        }
        // Dashboard / Home screens
        if (lower.contains("dashboard") || lower.contains("home screen") || lower.contains("main menu")) {
            return buildDashboardLayout(description);
        }
        // List / Recycler screens
        if (lower.contains("list") || lower.contains("recycler") || lower.contains("items")) {
            return buildListLayout(description);
        }
        // Settings screens
        if (lower.contains("settings") || lower.contains("preferences") || lower.contains("configuration")) {
            return buildSettingsLayout(description);
        }
        // Profile screens
        if (lower.contains("profile") || lower.contains("user info") || lower.contains("account")) {
            return buildProfileLayout(description);
        }
        // Form screens
        if (lower.contains("form") || lower.contains("input") || lower.contains("fill")) {
            return buildFormLayout(description);
        }
        // Chat / Messaging screens
        if (lower.contains("chat") || lower.contains("message") || lower.contains("conversation")) {
            return buildChatLayout(description);
        }

        // Default: generic scrollable layout with components mentioned in description
        return buildGenericLayout(description);
    }

    private String buildLoginLayout(String desc) {
        return "<LinearLayout\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    android:layout_width=\"match_parent\"\n"
                + "    android:layout_height=\"match_parent\"\n"
                + "    android:orientation=\"vertical\"\n"
                + "    android:gravity=\"center\"\n"
                + "    android:padding=\"32dp\"\n"
                + "    android:background=\"#FAFAFA\">\n\n"
                + "    <ImageView\n"
                + "        android:id=\"@+id/imgLogo\"\n"
                + "        android:layout_width=\"120dp\"\n"
                + "        android:layout_height=\"120dp\"\n"
                + "        android:layout_marginBottom=\"32dp\"\n"
                + "        android:scaleType=\"fitCenter\" />\n\n"
                + "    <TextView\n"
                + "        android:id=\"@+id/tvTitle\"\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"wrap_content\"\n"
                + "        android:text=\"Welcome Back\"\n"
                + "        android:textSize=\"28sp\"\n"
                + "        android:textStyle=\"bold\"\n"
                + "        android:gravity=\"center\"\n"
                + "        android:textColor=\"#212121\"\n"
                + "        android:layout_marginBottom=\"8dp\" />\n\n"
                + "    <TextView\n"
                + "        android:id=\"@+id/tvSubtitle\"\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"wrap_content\"\n"
                + "        android:text=\"Sign in to continue\"\n"
                + "        android:textSize=\"14sp\"\n"
                + "        android:gravity=\"center\"\n"
                + "        android:textColor=\"#757575\"\n"
                + "        android:layout_marginBottom=\"32dp\" />\n\n"
                + "    <EditText\n"
                + "        android:id=\"@+id/etEmail\"\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"56dp\"\n"
                + "        android:hint=\"Email address\"\n"
                + "        android:inputType=\"textEmailAddress\"\n"
                + "        android:padding=\"16dp\"\n"
                + "        android:background=\"@drawable/et_background\"\n"
                + "        android:layout_marginBottom=\"16dp\" />\n\n"
                + "    <EditText\n"
                + "        android:id=\"@+id/etPassword\"\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"56dp\"\n"
                + "        android:hint=\"Password\"\n"
                + "        android:inputType=\"textPassword\"\n"
                + "        android:padding=\"16dp\"\n"
                + "        android:background=\"@drawable/et_background\"\n"
                + "        android:layout_marginBottom=\"24dp\" />\n\n"
                + "    <Button\n"
                + "        android:id=\"@+id/btnLogin\"\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"56dp\"\n"
                + "        android:text=\"Log In\"\n"
                + "        android:textSize=\"16sp\"\n"
                + "        android:textColor=\"#FFFFFF\"\n"
                + "        android:gravity=\"center\"\n"
                + "        android:background=\"#1565C0\"\n"
                + "        android:layout_marginBottom=\"16dp\" />\n\n"
                + "    <TextView\n"
                + "        android:id=\"@+id/tvRegister\"\n"
                + "        android:layout_width=\"wrap_content\"\n"
                + "        android:layout_height=\"wrap_content\"\n"
                + "        android:text=\"Don't have an account? Sign up\"\n"
                + "        android:textSize=\"14sp\"\n"
                + "        android:textColor=\"#1565C0\" />\n\n"
                + "</LinearLayout>";
    }

    private String buildRegisterLayout(String desc) {
        return "<ScrollView\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    android:layout_width=\"match_parent\"\n"
                + "    android:layout_height=\"match_parent\"\n"
                + "    android:background=\"#FAFAFA\">\n\n"
                + "    <LinearLayout\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"wrap_content\"\n"
                + "        android:orientation=\"vertical\"\n"
                + "        android:padding=\"32dp\">\n\n"
                + "        <TextView\n"
                + "            android:id=\"@+id/tvTitle\"\n"
                + "            android:layout_width=\"match_parent\"\n"
                + "            android:layout_height=\"wrap_content\"\n"
                + "            android:text=\"Create Account\"\n"
                + "            android:textSize=\"28sp\"\n"
                + "            android:textStyle=\"bold\"\n"
                + "            android:textColor=\"#212121\"\n"
                + "            android:layout_marginTop=\"32dp\"\n"
                + "            android:layout_marginBottom=\"24dp\" />\n\n"
                + "        <EditText\n"
                + "            android:id=\"@+id/etFullName\"\n"
                + "            android:layout_width=\"match_parent\"\n"
                + "            android:layout_height=\"56dp\"\n"
                + "            android:hint=\"Full name\"\n"
                + "            android:inputType=\"textPersonName\"\n"
                + "            android:padding=\"16dp\"\n"
                + "            android:layout_marginBottom=\"16dp\" />\n\n"
                + "        <EditText\n"
                + "            android:id=\"@+id/etEmail\"\n"
                + "            android:layout_width=\"match_parent\"\n"
                + "            android:layout_height=\"56dp\"\n"
                + "            android:hint=\"Email address\"\n"
                + "            android:inputType=\"textEmailAddress\"\n"
                + "            android:padding=\"16dp\"\n"
                + "            android:layout_marginBottom=\"16dp\" />\n\n"
                + "        <EditText\n"
                + "            android:id=\"@+id/etPassword\"\n"
                + "            android:layout_width=\"match_parent\"\n"
                + "            android:layout_height=\"56dp\"\n"
                + "            android:hint=\"Password\"\n"
                + "            android:inputType=\"textPassword\"\n"
                + "            android:padding=\"16dp\"\n"
                + "            android:layout_marginBottom=\"16dp\" />\n\n"
                + "        <EditText\n"
                + "            android:id=\"@+id/etConfirmPassword\"\n"
                + "            android:layout_width=\"match_parent\"\n"
                + "            android:layout_height=\"56dp\"\n"
                + "            android:hint=\"Confirm password\"\n"
                + "            android:inputType=\"textPassword\"\n"
                + "            android:padding=\"16dp\"\n"
                + "            android:layout_marginBottom=\"24dp\" />\n\n"
                + "        <Button\n"
                + "            android:id=\"@+id/btnRegister\"\n"
                + "            android:layout_width=\"match_parent\"\n"
                + "            android:layout_height=\"56dp\"\n"
                + "            android:text=\"Create Account\"\n"
                + "            android:textColor=\"#FFFFFF\"\n"
                + "            android:gravity=\"center\"\n"
                + "            android:background=\"#1565C0\"\n"
                + "            android:layout_marginBottom=\"16dp\" />\n\n"
                + "        <TextView\n"
                + "            android:id=\"@+id/tvLogin\"\n"
                + "            android:layout_width=\"wrap_content\"\n"
                + "            android:layout_height=\"wrap_content\"\n"
                + "            android:text=\"Already have an account? Log in\"\n"
                + "            android:textColor=\"#1565C0\"\n"
                + "            android:layout_gravity=\"center_horizontal\" />\n\n"
                + "    </LinearLayout>\n"
                + "</ScrollView>";
    }

    private String buildDashboardLayout(String desc) {
        return "<LinearLayout\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    android:layout_width=\"match_parent\"\n"
                + "    android:layout_height=\"match_parent\"\n"
                + "    android:orientation=\"vertical\"\n"
                + "    android:background=\"#F5F5F5\">\n\n"
                + "    <LinearLayout\n"
                + "        android:id=\"@+id/toolbar\"\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"64dp\"\n"
                + "        android:background=\"#1565C0\"\n"
                + "        android:gravity=\"center_vertical\"\n"
                + "        android:padding=\"16dp\">\n\n"
                + "        <TextView\n"
                + "            android:id=\"@+id/tvToolbarTitle\"\n"
                + "            android:layout_width=\"0dp\"\n"
                + "            android:layout_height=\"wrap_content\"\n"
                + "            android:layout_weight=\"1\"\n"
                + "            android:text=\"Dashboard\"\n"
                + "            android:textSize=\"20sp\"\n"
                + "            android:textColor=\"#FFFFFF\"\n"
                + "            android:textStyle=\"bold\" />\n\n"
                + "        <ImageView\n"
                + "            android:id=\"@+id/imgProfile\"\n"
                + "            android:layout_width=\"40dp\"\n"
                + "            android:layout_height=\"40dp\"\n"
                + "            android:scaleType=\"fitCenter\" />\n\n"
                + "    </LinearLayout>\n\n"
                + "    <ScrollView\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"0dp\"\n"
                + "        android:layout_weight=\"1\">\n\n"
                + "        <LinearLayout\n"
                + "            android:layout_width=\"match_parent\"\n"
                + "            android:layout_height=\"wrap_content\"\n"
                + "            android:orientation=\"vertical\"\n"
                + "            android:padding=\"16dp\">\n\n"
                + "            <TextView\n"
                + "                android:id=\"@+id/tvGreeting\"\n"
                + "                android:layout_width=\"match_parent\"\n"
                + "                android:layout_height=\"wrap_content\"\n"
                + "                android:text=\"Good morning!\"\n"
                + "                android:textSize=\"22sp\"\n"
                + "                android:textStyle=\"bold\"\n"
                + "                android:textColor=\"#212121\"\n"
                + "                android:layout_marginBottom=\"16dp\" />\n\n"
                + "            <LinearLayout\n"
                + "                android:id=\"@+id/statsRow\"\n"
                + "                android:layout_width=\"match_parent\"\n"
                + "                android:layout_height=\"wrap_content\"\n"
                + "                android:orientation=\"horizontal\"\n"
                + "                android:layout_marginBottom=\"16dp\">\n\n"
                + "                <LinearLayout\n"
                + "                    android:id=\"@+id/card1\"\n"
                + "                    android:layout_width=\"0dp\"\n"
                + "                    android:layout_height=\"100dp\"\n"
                + "                    android:layout_weight=\"1\"\n"
                + "                    android:orientation=\"vertical\"\n"
                + "                    android:gravity=\"center\"\n"
                + "                    android:background=\"#FFFFFF\"\n"
                + "                    android:layout_marginEnd=\"8dp\"\n"
                + "                    android:padding=\"8dp\">\n"
                + "                    <TextView android:id=\"@+id/tvStat1Value\"\n"
                + "                        android:layout_width=\"wrap_content\"\n"
                + "                        android:layout_height=\"wrap_content\"\n"
                + "                        android:text=\"0\"\n"
                + "                        android:textSize=\"28sp\"\n"
                + "                        android:textStyle=\"bold\"\n"
                + "                        android:textColor=\"#1565C0\" />\n"
                + "                    <TextView android:id=\"@+id/tvStat1Label\"\n"
                + "                        android:layout_width=\"wrap_content\"\n"
                + "                        android:layout_height=\"wrap_content\"\n"
                + "                        android:text=\"Total\"\n"
                + "                        android:textSize=\"12sp\"\n"
                + "                        android:textColor=\"#757575\" />\n"
                + "                </LinearLayout>\n\n"
                + "                <LinearLayout\n"
                + "                    android:id=\"@+id/card2\"\n"
                + "                    android:layout_width=\"0dp\"\n"
                + "                    android:layout_height=\"100dp\"\n"
                + "                    android:layout_weight=\"1\"\n"
                + "                    android:orientation=\"vertical\"\n"
                + "                    android:gravity=\"center\"\n"
                + "                    android:background=\"#FFFFFF\"\n"
                + "                    android:padding=\"8dp\">\n"
                + "                    <TextView android:id=\"@+id/tvStat2Value\"\n"
                + "                        android:layout_width=\"wrap_content\"\n"
                + "                        android:layout_height=\"wrap_content\"\n"
                + "                        android:text=\"0\"\n"
                + "                        android:textSize=\"28sp\"\n"
                + "                        android:textStyle=\"bold\"\n"
                + "                        android:textColor=\"#2E7D32\" />\n"
                + "                    <TextView android:id=\"@+id/tvStat2Label\"\n"
                + "                        android:layout_width=\"wrap_content\"\n"
                + "                        android:layout_height=\"wrap_content\"\n"
                + "                        android:text=\"Active\"\n"
                + "                        android:textSize=\"12sp\"\n"
                + "                        android:textColor=\"#757575\" />\n"
                + "                </LinearLayout>\n\n"
                + "            </LinearLayout>\n\n"
                + "            <TextView\n"
                + "                android:id=\"@+id/tvRecentTitle\"\n"
                + "                android:layout_width=\"match_parent\"\n"
                + "                android:layout_height=\"wrap_content\"\n"
                + "                android:text=\"Recent Activity\"\n"
                + "                android:textSize=\"16sp\"\n"
                + "                android:textStyle=\"bold\"\n"
                + "                android:textColor=\"#212121\"\n"
                + "                android:layout_marginBottom=\"8dp\" />\n\n"
                + "            <ListView\n"
                + "                android:id=\"@+id/listRecent\"\n"
                + "                android:layout_width=\"match_parent\"\n"
                + "                android:layout_height=\"300dp\" />\n\n"
                + "        </LinearLayout>\n"
                + "    </ScrollView>\n\n"
                + "</LinearLayout>";
    }

    private String buildListLayout(String desc) {
        return "<LinearLayout\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    android:layout_width=\"match_parent\"\n"
                + "    android:layout_height=\"match_parent\"\n"
                + "    android:orientation=\"vertical\"\n"
                + "    android:background=\"#F5F5F5\">\n\n"
                + "    <LinearLayout\n"
                + "        android:id=\"@+id/searchBar\"\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"56dp\"\n"
                + "        android:orientation=\"horizontal\"\n"
                + "        android:background=\"#FFFFFF\"\n"
                + "        android:gravity=\"center_vertical\"\n"
                + "        android:padding=\"8dp\"\n"
                + "        android:layout_marginBottom=\"4dp\">\n\n"
                + "        <EditText\n"
                + "            android:id=\"@+id/etSearch\"\n"
                + "            android:layout_width=\"0dp\"\n"
                + "            android:layout_height=\"match_parent\"\n"
                + "            android:layout_weight=\"1\"\n"
                + "            android:hint=\"Search...\"\n"
                + "            android:padding=\"8dp\"\n"
                + "            android:background=\"@null\" />\n\n"
                + "        <ImageView\n"
                + "            android:id=\"@+id/btnSearch\"\n"
                + "            android:layout_width=\"40dp\"\n"
                + "            android:layout_height=\"40dp\"\n"
                + "            android:scaleType=\"fitCenter\" />\n\n"
                + "    </LinearLayout>\n\n"
                + "    <ListView\n"
                + "        android:id=\"@+id/listItems\"\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"0dp\"\n"
                + "        android:layout_weight=\"1\"\n"
                + "        android:divider=\"#E0E0E0\"\n"
                + "        android:dividerHeight=\"1dp\" />\n\n"
                + "    <TextView\n"
                + "        android:id=\"@+id/tvEmpty\"\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"wrap_content\"\n"
                + "        android:text=\"No items found\"\n"
                + "        android:textSize=\"16sp\"\n"
                + "        android:textColor=\"#9E9E9E\"\n"
                + "        android:gravity=\"center\"\n"
                + "        android:padding=\"32dp\"\n"
                + "        android:visibility=\"gone\" />\n\n"
                + "</LinearLayout>";
    }

    private String buildSettingsLayout(String desc) {
        return "<ScrollView\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    android:layout_width=\"match_parent\"\n"
                + "    android:layout_height=\"match_parent\"\n"
                + "    android:background=\"#F5F5F5\">\n\n"
                + "    <LinearLayout\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"wrap_content\"\n"
                + "        android:orientation=\"vertical\"\n"
                + "        android:padding=\"16dp\">\n\n"
                + "        <TextView\n"
                + "            android:id=\"@+id/tvSectionAccount\"\n"
                + "            android:layout_width=\"match_parent\"\n"
                + "            android:layout_height=\"wrap_content\"\n"
                + "            android:text=\"ACCOUNT\"\n"
                + "            android:textSize=\"12sp\"\n"
                + "            android:textColor=\"#1565C0\"\n"
                + "            android:textStyle=\"bold\"\n"
                + "            android:layout_marginBottom=\"8dp\" />\n\n"
                + "        <LinearLayout\n"
                + "            android:id=\"@+id/itemProfile\"\n"
                + "            android:layout_width=\"match_parent\"\n"
                + "            android:layout_height=\"56dp\"\n"
                + "            android:orientation=\"horizontal\"\n"
                + "            android:gravity=\"center_vertical\"\n"
                + "            android:background=\"#FFFFFF\"\n"
                + "            android:padding=\"16dp\"\n"
                + "            android:layout_marginBottom=\"2dp\">\n"
                + "            <TextView android:id=\"@+id/tvProfileLabel\"\n"
                + "                android:layout_width=\"0dp\"\n"
                + "                android:layout_height=\"wrap_content\"\n"
                + "                android:layout_weight=\"1\"\n"
                + "                android:text=\"Profile\"\n"
                + "                android:textSize=\"16sp\"\n"
                + "                android:textColor=\"#212121\" />\n"
                + "            <ImageView android:id=\"@+id/arrowProfile\"\n"
                + "                android:layout_width=\"24dp\"\n"
                + "                android:layout_height=\"24dp\" />\n"
                + "        </LinearLayout>\n\n"
                + "        <TextView\n"
                + "            android:id=\"@+id/tvSectionNotifications\"\n"
                + "            android:layout_width=\"match_parent\"\n"
                + "            android:layout_height=\"wrap_content\"\n"
                + "            android:text=\"NOTIFICATIONS\"\n"
                + "            android:textSize=\"12sp\"\n"
                + "            android:textColor=\"#1565C0\"\n"
                + "            android:textStyle=\"bold\"\n"
                + "            android:layout_marginTop=\"16dp\"\n"
                + "            android:layout_marginBottom=\"8dp\" />\n\n"
                + "        <LinearLayout\n"
                + "            android:id=\"@+id/itemNotifications\"\n"
                + "            android:layout_width=\"match_parent\"\n"
                + "            android:layout_height=\"56dp\"\n"
                + "            android:orientation=\"horizontal\"\n"
                + "            android:gravity=\"center_vertical\"\n"
                + "            android:background=\"#FFFFFF\"\n"
                + "            android:padding=\"16dp\">\n"
                + "            <TextView android:id=\"@+id/tvNotifLabel\"\n"
                + "                android:layout_width=\"0dp\"\n"
                + "                android:layout_height=\"wrap_content\"\n"
                + "                android:layout_weight=\"1\"\n"
                + "                android:text=\"Push Notifications\"\n"
                + "                android:textSize=\"16sp\"\n"
                + "                android:textColor=\"#212121\" />\n"
                + "            <Switch android:id=\"@+id/switchNotifications\"\n"
                + "                android:layout_width=\"wrap_content\"\n"
                + "                android:layout_height=\"wrap_content\" />\n"
                + "        </LinearLayout>\n\n"
                + "    </LinearLayout>\n"
                + "</ScrollView>";
    }

    private String buildProfileLayout(String desc) {
        return "<ScrollView\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    android:layout_width=\"match_parent\"\n"
                + "    android:layout_height=\"match_parent\"\n"
                + "    android:background=\"#F5F5F5\">\n\n"
                + "    <LinearLayout\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"wrap_content\"\n"
                + "        android:orientation=\"vertical\">\n\n"
                + "        <LinearLayout\n"
                + "            android:id=\"@+id/headerLayout\"\n"
                + "            android:layout_width=\"match_parent\"\n"
                + "            android:layout_height=\"200dp\"\n"
                + "            android:orientation=\"vertical\"\n"
                + "            android:gravity=\"center\"\n"
                + "            android:background=\"#1565C0\">\n\n"
                + "            <ImageView\n"
                + "                android:id=\"@+id/imgAvatar\"\n"
                + "                android:layout_width=\"80dp\"\n"
                + "                android:layout_height=\"80dp\"\n"
                + "                android:scaleType=\"fitCenter\"\n"
                + "                android:layout_marginBottom=\"8dp\" />\n\n"
                + "            <TextView\n"
                + "                android:id=\"@+id/tvName\"\n"
                + "                android:layout_width=\"wrap_content\"\n"
                + "                android:layout_height=\"wrap_content\"\n"
                + "                android:text=\"User Name\"\n"
                + "                android:textSize=\"20sp\"\n"
                + "                android:textStyle=\"bold\"\n"
                + "                android:textColor=\"#FFFFFF\" />\n\n"
                + "            <TextView\n"
                + "                android:id=\"@+id/tvEmail\"\n"
                + "                android:layout_width=\"wrap_content\"\n"
                + "                android:layout_height=\"wrap_content\"\n"
                + "                android:text=\"user@example.com\"\n"
                + "                android:textSize=\"14sp\"\n"
                + "                android:textColor=\"#B3FFFFFF\" />\n"
                + "        </LinearLayout>\n\n"
                + "        <LinearLayout\n"
                + "            android:layout_width=\"match_parent\"\n"
                + "            android:layout_height=\"wrap_content\"\n"
                + "            android:orientation=\"vertical\"\n"
                + "            android:padding=\"16dp\">\n\n"
                + "            <Button\n"
                + "                android:id=\"@+id/btnEditProfile\"\n"
                + "                android:layout_width=\"match_parent\"\n"
                + "                android:layout_height=\"48dp\"\n"
                + "                android:text=\"Edit Profile\"\n"
                + "                android:textColor=\"#FFFFFF\"\n"
                + "                android:gravity=\"center\"\n"
                + "                android:background=\"#1565C0\"\n"
                + "                android:layout_marginBottom=\"16dp\" />\n\n"
                + "            <Button\n"
                + "                android:id=\"@+id/btnLogout\"\n"
                + "                android:layout_width=\"match_parent\"\n"
                + "                android:layout_height=\"48dp\"\n"
                + "                android:text=\"Log Out\"\n"
                + "                android:textColor=\"#D32F2F\"\n"
                + "                android:gravity=\"center\"\n"
                + "                android:background=\"#FFFFFF\" />\n\n"
                + "        </LinearLayout>\n"
                + "    </LinearLayout>\n"
                + "</ScrollView>";
    }

    private String buildFormLayout(String desc) {
        return "<ScrollView\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    android:layout_width=\"match_parent\"\n"
                + "    android:layout_height=\"match_parent\"\n"
                + "    android:background=\"#FAFAFA\">\n\n"
                + "    <LinearLayout\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"wrap_content\"\n"
                + "        android:orientation=\"vertical\"\n"
                + "        android:padding=\"16dp\">\n\n"
                + "        <TextView android:id=\"@+id/tvFormTitle\"\n"
                + "            android:layout_width=\"match_parent\"\n"
                + "            android:layout_height=\"wrap_content\"\n"
                + "            android:text=\"Fill in the details\"\n"
                + "            android:textSize=\"20sp\"\n"
                + "            android:textStyle=\"bold\"\n"
                + "            android:textColor=\"#212121\"\n"
                + "            android:layout_marginBottom=\"24dp\" />\n\n"
                + "        <EditText android:id=\"@+id/etField1\"\n"
                + "            android:layout_width=\"match_parent\"\n"
                + "            android:layout_height=\"56dp\"\n"
                + "            android:hint=\"Field 1\"\n"
                + "            android:padding=\"16dp\"\n"
                + "            android:layout_marginBottom=\"16dp\" />\n\n"
                + "        <EditText android:id=\"@+id/etField2\"\n"
                + "            android:layout_width=\"match_parent\"\n"
                + "            android:layout_height=\"56dp\"\n"
                + "            android:hint=\"Field 2\"\n"
                + "            android:padding=\"16dp\"\n"
                + "            android:layout_marginBottom=\"16dp\" />\n\n"
                + "        <EditText android:id=\"@+id/etField3\"\n"
                + "            android:layout_width=\"match_parent\"\n"
                + "            android:layout_height=\"100dp\"\n"
                + "            android:hint=\"Notes\"\n"
                + "            android:gravity=\"top\"\n"
                + "            android:inputType=\"textMultiLine\"\n"
                + "            android:padding=\"16dp\"\n"
                + "            android:layout_marginBottom=\"24dp\" />\n\n"
                + "        <Button android:id=\"@+id/btnSubmit\"\n"
                + "            android:layout_width=\"match_parent\"\n"
                + "            android:layout_height=\"56dp\"\n"
                + "            android:text=\"Submit\"\n"
                + "            android:textColor=\"#FFFFFF\"\n"
                + "            android:gravity=\"center\"\n"
                + "            android:background=\"#1565C0\" />\n\n"
                + "    </LinearLayout>\n"
                + "</ScrollView>";
    }

    private String buildChatLayout(String desc) {
        return "<LinearLayout\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    android:layout_width=\"match_parent\"\n"
                + "    android:layout_height=\"match_parent\"\n"
                + "    android:orientation=\"vertical\"\n"
                + "    android:background=\"#F5F5F5\">\n\n"
                + "    <ListView\n"
                + "        android:id=\"@+id/listMessages\"\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"0dp\"\n"
                + "        android:layout_weight=\"1\"\n"
                + "        android:divider=\"@null\"\n"
                + "        android:stackFromBottom=\"true\"\n"
                + "        android:transcriptMode=\"alwaysScroll\"\n"
                + "        android:padding=\"8dp\" />\n\n"
                + "    <LinearLayout\n"
                + "        android:id=\"@+id/inputBar\"\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"wrap_content\"\n"
                + "        android:orientation=\"horizontal\"\n"
                + "        android:background=\"#FFFFFF\"\n"
                + "        android:padding=\"8dp\"\n"
                + "        android:gravity=\"center_vertical\">\n\n"
                + "        <EditText\n"
                + "            android:id=\"@+id/etMessage\"\n"
                + "            android:layout_width=\"0dp\"\n"
                + "            android:layout_height=\"wrap_content\"\n"
                + "            android:layout_weight=\"1\"\n"
                + "            android:hint=\"Type a message...\"\n"
                + "            android:minHeight=\"48dp\"\n"
                + "            android:maxLines=\"4\"\n"
                + "            android:inputType=\"textMultiLine|textCapSentences\"\n"
                + "            android:background=\"@null\"\n"
                + "            android:padding=\"8dp\" />\n\n"
                + "        <ImageView\n"
                + "            android:id=\"@+id/btnSend\"\n"
                + "            android:layout_width=\"48dp\"\n"
                + "            android:layout_height=\"48dp\"\n"
                + "            android:scaleType=\"fitCenter\"\n"
                + "            android:padding=\"8dp\" />\n\n"
                + "    </LinearLayout>\n\n"
                + "</LinearLayout>";
    }

    private String buildGenericLayout(String desc) {
        // Detect components mentioned and build accordingly
        String lower = desc.toLowerCase();
        StringBuilder xml = new StringBuilder();
        xml.append("<ScrollView\n")
           .append("    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n")
           .append("    android:layout_width=\"match_parent\"\n")
           .append("    android:layout_height=\"match_parent\"\n")
           .append("    android:background=\"#FAFAFA\">\n\n")
           .append("    <LinearLayout\n")
           .append("        android:layout_width=\"match_parent\"\n")
           .append("        android:layout_height=\"wrap_content\"\n")
           .append("        android:orientation=\"vertical\"\n")
           .append("        android:padding=\"16dp\">\n\n");

        int viewCount = 1;

        if (lower.contains("title") || lower.contains("header")) {
            xml.append("        <TextView\n")
               .append("            android:id=\"@+id/tvTitle\"\n")
               .append("            android:layout_width=\"match_parent\"\n")
               .append("            android:layout_height=\"wrap_content\"\n")
               .append("            android:text=\"Title\"\n")
               .append("            android:textSize=\"24sp\"\n")
               .append("            android:textStyle=\"bold\"\n")
               .append("            android:textColor=\"#212121\"\n")
               .append("            android:layout_marginBottom=\"16dp\" />\n\n");
        }

        if (lower.contains("image") || lower.contains("photo") || lower.contains("picture")) {
            xml.append("        <ImageView\n")
               .append("            android:id=\"@+id/imgMain\"\n")
               .append("            android:layout_width=\"match_parent\"\n")
               .append("            android:layout_height=\"200dp\"\n")
               .append("            android:scaleType=\"centerCrop\"\n")
               .append("            android:layout_marginBottom=\"16dp\" />\n\n");
        }

        if (lower.contains("text") || lower.contains("description") || lower.contains("content")) {
            xml.append("        <TextView\n")
               .append("            android:id=\"@+id/tvContent\"\n")
               .append("            android:layout_width=\"match_parent\"\n")
               .append("            android:layout_height=\"wrap_content\"\n")
               .append("            android:text=\"Content goes here\"\n")
               .append("            android:textSize=\"16sp\"\n")
               .append("            android:textColor=\"#424242\"\n")
               .append("            android:lineSpacingMultiplier=\"1.5\"\n")
               .append("            android:layout_marginBottom=\"16dp\" />\n\n");
        }

        if (lower.contains("input") || lower.contains("edittext") || lower.contains("field")) {
            xml.append("        <EditText\n")
               .append("            android:id=\"@+id/etInput\"\n")
               .append("            android:layout_width=\"match_parent\"\n")
               .append("            android:layout_height=\"56dp\"\n")
               .append("            android:hint=\"Enter value\"\n")
               .append("            android:padding=\"16dp\"\n")
               .append("            android:layout_marginBottom=\"16dp\" />\n\n");
        }

        if (lower.contains("button") || lower.contains("action") || lower.contains("submit")) {
            xml.append("        <Button\n")
               .append("            android:id=\"@+id/btnAction\"\n")
               .append("            android:layout_width=\"match_parent\"\n")
               .append("            android:layout_height=\"56dp\"\n")
               .append("            android:text=\"Action\"\n")
               .append("            android:textColor=\"#FFFFFF\"\n")
               .append("            android:gravity=\"center\"\n")
               .append("            android:background=\"#1565C0\"\n")
               .append("            android:layout_marginBottom=\"16dp\" />\n\n");
        }

        if (lower.contains("list") || lower.contains("items")) {
            xml.append("        <ListView\n")
               .append("            android:id=\"@+id/listMain\"\n")
               .append("            android:layout_width=\"match_parent\"\n")
               .append("            android:layout_height=\"300dp\"\n")
               .append("            android:divider=\"#E0E0E0\"\n")
               .append("            android:dividerHeight=\"1dp\" />\n\n");
        }

        // Default content if nothing detected
        if (!lower.contains("title") && !lower.contains("image") && !lower.contains("button")
                && !lower.contains("input") && !lower.contains("list") && !lower.contains("text")) {
            xml.append("        <TextView\n")
               .append("            android:id=\"@+id/tvMain\"\n")
               .append("            android:layout_width=\"match_parent\"\n")
               .append("            android:layout_height=\"wrap_content\"\n")
               .append("            android:text=\"").append(desc.length() > 50 ? desc.substring(0, 50) : desc).append("\"\n")
               .append("            android:textSize=\"16sp\"\n")
               .append("            android:textColor=\"#212121\"\n")
               .append("            android:padding=\"16dp\" />\n\n");
        }

        xml.append("    </LinearLayout>\n")
           .append("</ScrollView>");

        return xml.toString();
    }

    private String cleanXml(String xml) {
        if (xml == null) return "";
        return xml.replace("```xml", "").replace("```", "").trim();
    }

    private boolean looksLikeXml(String s) {
        if (s == null || s.isEmpty()) return false;
        String t = s.trim();
        return t.contains("<") && t.contains(">");
    }

    private void broadcastLayoutReload(Context ctx, String scId, String activityName) {
        try {
            Intent i = new Intent(ACTION_LIVE_LAYOUT_RELOAD);
            i.putExtra("sc_id", scId);
            i.putExtra("activity_name", activityName);
            ctx.sendBroadcast(i);
        } catch (Exception ignored) {}
    }
}
