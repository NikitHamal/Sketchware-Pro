package pro.sketchware.ai.tools;

import android.os.Environment;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import pro.sketchware.ai.models.ToolResult;

/**
 * ShellTool — Full shell command execution for the AI agent.
 *
 * Unlike {@link DevTools.ShellExecutorTool} which is read-only (grep, ls, find, cat, wc),
 * this tool allows the AI to run arbitrary shell commands within the Sketchware storage
 * directory. The working directory is always /sdcard/.sketchware for safety.
 *
 * Registered as tool name: "run_shell_command"
 *
 * Timeout: 30 seconds. Output is capped at 4000 characters to avoid token overflow.
 */
public final class ShellTool implements AgentTool {

    private static final int TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_CHARS = 4000;

    @Override
    public String getName() {
        return "run_shell_command";
    }

    @Override
    public String getDescription() {
        return "Executes a shell command directly on the Android device within the Sketchware "
                + "storage directory (/sdcard/.sketchware). Use this when other tools cannot "
                + "cover the request — e.g. running custom scripts, compressing files, "
                + "inspecting raw binary data, or chaining multiple file operations. "
                + "The working directory is always /sdcard/.sketchware. "
                + "Timeout: 30 seconds. Output capped at 4000 characters.";
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject command = new JsonObject();
        command.addProperty("type", "string");
        command.addProperty("description",
                "Shell command to execute, e.g. 'ls data/12345' or 'cat data/12345/project | head -20'");
        props.add("command", command);

        schema.add("properties", props);

        JsonArray required = new JsonArray();
        required.add("command");
        schema.add("required", required);

        return schema;
    }

    @Override
    public ToolResult execute(JsonObject arguments, ToolContext context) {
        String command = arguments.has("command")
                ? arguments.get("command").getAsString().trim()
                : "";

        if (command.isEmpty()) {
            return ToolResult.failure(null, "No command provided. Please specify a shell command.");
        }

        // Block obviously dangerous commands
        if (isDangerous(command)) {
            return ToolResult.failure(null,
                    "Command blocked for safety: '" + command + "'. "
                            + "Commands that delete system files or modify app packages are not allowed.");
        }

        try {
            File workDir = new File(Environment.getExternalStorageDirectory(), ".sketchware");
            if (!workDir.exists()) {
                workDir = Environment.getExternalStorageDirectory();
            }

            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            pb.directory(workDir);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return ToolResult.failure(null,
                        "Command timed out after " + TIMEOUT_SECONDS + " seconds: " + command);
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = process.exitValue();
            String rawOutput = output.toString().trim();

            String finalOutput = rawOutput.isEmpty() ? "(no output)" : rawOutput;
            if (finalOutput.length() > MAX_OUTPUT_CHARS) {
                finalOutput = finalOutput.substring(0, MAX_OUTPUT_CHARS)
                        + "\n... [output truncated at " + MAX_OUTPUT_CHARS + " characters]";
            }

            String result = "Command: " + command
                    + "\nExit code: " + exitCode
                    + "\nWorking dir: " + workDir.getAbsolutePath()
                    + "\n\nOutput:\n" + finalOutput;

            if (exitCode == 0) {
                return ToolResult.success(null, result);
            } else {
                return ToolResult.failure(null, result);
            }

        } catch (Exception e) {
            return ToolResult.failure(null,
                    "Failed to execute shell command: " + e.getMessage());
        }
    }

    private boolean isDangerous(String command) {
        String lower = command.toLowerCase().trim();
        // Block commands that could destroy system or app data
        String[] blocked = {
                "rm -rf /",
                "rm -rf ~/",
                "format",
                "mkfs",
                "> /dev/",
                "dd if=",
        };
        for (String b : blocked) {
            if (lower.contains(b)) return true;
        }
        return false;
    }
}
