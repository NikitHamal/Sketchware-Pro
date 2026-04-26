package pro.sketchware.ai.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

import pro.sketchware.ai.models.ToolResult;

/**
 * SketchwareEncryptTool — writes and encrypts content back into Sketchware project files.
 *
 * After reading and modifying a project file with decrypt_project_file, use this tool
 * to save the changes. The tool automatically detects whether the file needs AES encryption
 * (core data files like logic, view, file) or can be saved as plain text (XML, Java, etc.).
 *
 * Registered as tool name: "encrypt_project_file"
 */
public final class SketchwareEncryptTool implements AgentTool {

    @Override
    public String getName() {
        return "encrypt_project_file";
    }

    @Override
    public String getDescription() {
        return "Writes and encrypts JSON content into an internal Sketchware project file "
                + "(e.g. logic, view, file, library). Always call decrypt_project_file first "
                + "to read the current content, then call this to save your changes. "
                + "Automatically handles encryption for core data files. "
                + "file_path is relative to .sketchware/ — e.g. 'data/12345/logic'.";
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject filePath = new JsonObject();
        filePath.addProperty("type", "string");
        filePath.addProperty("description",
                "Relative path of the Sketchware project file to write. "
                        + "Examples: 'data/12345/logic', 'data/12345/view'");
        props.add("file_path", filePath);

        JsonObject content = new JsonObject();
        content.addProperty("type", "string");
        content.addProperty("description",
                "JSON string content to encrypt and save into the file.");
        props.add("content", content);

        schema.add("properties", props);

        JsonArray required = new JsonArray();
        required.add("file_path");
        required.add("content");
        schema.add("required", required);

        return schema;
    }

    @Override
    public ToolResult execute(JsonObject arguments, ToolContext context) {
        String filePath = arguments.has("file_path")
                ? arguments.get("file_path").getAsString().trim()
                : "";
        String content = arguments.has("content")
                ? arguments.get("content").getAsString()
                : "";

        if (filePath.isEmpty()) {
            return ToolResult.failure(null,
                    "Error: file_path is required. Example: 'data/12345/logic'");
        }
        if (content.isEmpty()) {
            return ToolResult.failure(null,
                    "Error: content cannot be empty.");
        }

        try {
            File sketchwareDir = new File(
                    android.os.Environment.getExternalStorageDirectory(), ".sketchware");
            File targetFile = new File(sketchwareDir, filePath);

            // Create parent directories if needed
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // Normalize JSON
            String normalized = content.trim();
            try {
                if (normalized.startsWith("{")) {
                    normalized = new org.json.JSONObject(normalized).toString();
                } else if (normalized.startsWith("[")) {
                    normalized = new org.json.JSONArray(normalized).toString();
                }
            } catch (Exception ignored) {
                // Use content as-is if not valid JSON
            }

            // Determine if file needs encryption
            boolean needsEncryption = needsEncryption(targetFile, filePath);

            if (!needsEncryption) {
                // Plain text write (XML, Java, etc.)
                try (RandomAccessFile raf = new RandomAccessFile(targetFile, "rw")) {
                    raf.setLength(0);
                    raf.write(content.getBytes(StandardCharsets.UTF_8));
                }
                return ToolResult.success(null,
                        "Successfully saved (plain text): " + filePath
                                + "\nSize: " + content.length() + " characters");
            }

            // AES/CBC encryption
            String key = "sketchwaresecure";
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,
                    new javax.crypto.spec.SecretKeySpec(keyBytes, "AES"),
                    new javax.crypto.spec.IvParameterSpec(keyBytes));

            byte[] encrypted = cipher.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            try (RandomAccessFile raf = new RandomAccessFile(targetFile, "rw")) {
                raf.setLength(0);
                raf.write(encrypted);
            }

            return ToolResult.success(null,
                    "Successfully encrypted and saved: " + filePath
                            + "\nSize: " + encrypted.length + " bytes");

        } catch (Exception e) {
            return ToolResult.failure(null,
                    "Failed to write file '" + filePath + "': " + e.getMessage());
        }
    }

    /**
     * Determines if the file at the given path should be AES-encrypted.
     * Core Sketchware data files (no extension) need encryption.
     * Text-based files (XML, Java, JSON, etc.) are stored plain.
     */
    private boolean needsEncryption(File file, String relativePath) {
        String name = file.getName();
        if (name.contains(".")) {
            String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
            switch (ext) {
                case "xml": case "java": case "kt": case "json":
                case "txt": case "gradle": case "md": case "html":
                case "properties": case "pro":
                    return false;
            }
        }
        // Core data files under data/{sc_id}/ with no extension are encrypted
        return !name.contains(".")
                && (relativePath.startsWith("data/") || relativePath.startsWith("mysc/list/"));
    }
}
