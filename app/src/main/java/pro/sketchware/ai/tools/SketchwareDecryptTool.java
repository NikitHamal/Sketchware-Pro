package pro.sketchware.ai.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import pro.sketchware.ai.models.ToolResult;

/**
 * SketchwareDecryptTool — reads and decrypts internal Sketchware project files.
 *
 * Sketchware stores project data (logic, view, file, library, etc.) in encrypted
 * binary format under .sketchware/data/{sc_id}/. This tool decrypts those files
 * and returns their JSON content so the AI can read and understand project structure.
 *
 * Registered as tool name: "decrypt_project_file"
 */
public final class SketchwareDecryptTool implements AgentTool {

    @Override
    public String getName() {
        return "decrypt_project_file";
    }

    @Override
    public String getDescription() {
        return "Reads and decrypts an internal Sketchware project file (e.g. logic, view, file, "
                + "library, permission, resource) and returns its JSON content. "
                + "Use this before editing any encrypted Sketchware project data file. "
                + "file_path is relative to .sketchware/ — e.g. 'data/12345/logic' "
                + "or 'data/12345/view'.";
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject filePath = new JsonObject();
        filePath.addProperty("type", "string");
        filePath.addProperty("description",
                "Relative path of the Sketchware project file to decrypt. "
                        + "Examples: 'data/12345/logic', 'data/12345/view', 'data/12345/library'");
        props.add("file_path", filePath);

        schema.add("properties", props);

        JsonArray required = new JsonArray();
        required.add("file_path");
        schema.add("required", required);

        return schema;
    }

    @Override
    public ToolResult execute(JsonObject arguments, ToolContext context) {
        String filePath = arguments.has("file_path")
                ? arguments.get("file_path").getAsString().trim()
                : "";

        if (filePath.isEmpty()) {
            return ToolResult.failure(null,
                    "Error: file_path is required. Example: 'data/12345/logic'");
        }

        try {
            // Use ContextBuilder's file reading which handles encryption
            android.os.Environment env = null;
            java.io.File sketchwareDir = new java.io.File(
                    android.os.Environment.getExternalStorageDirectory(), ".sketchware");
            java.io.File targetFile = new java.io.File(sketchwareDir, filePath);

            if (!targetFile.exists()) {
                return ToolResult.failure(null,
                        "File not found: " + filePath
                                + "\nExpected at: " + targetFile.getAbsolutePath());
            }

            byte[] bytes = java.nio.file.Files.readAllBytes(targetFile.toPath());
            if (bytes.length == 0) {
                return ToolResult.success(null,
                        "File is empty: " + filePath);
            }

            // Try plain text first
            String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8).trim();
            if (text.startsWith("{") || text.startsWith("[") || text.startsWith("<?xml")) {
                return ToolResult.success(null,
                        "Content of " + filePath + " (plain text):\n" + text);
            }

            // Try AES/CBC decryption (Sketchware format)
            try {
                String key = "sketchwaresecure";
                javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
                byte[] keyBytes = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                cipher.init(javax.crypto.Cipher.DECRYPT_MODE,
                        new javax.crypto.spec.SecretKeySpec(keyBytes, "AES"),
                        new javax.crypto.spec.IvParameterSpec(keyBytes));
                byte[] decrypted = cipher.doFinal(bytes);
                String decryptedText = new String(decrypted, java.nio.charset.StandardCharsets.UTF_8).trim();

                // Pretty-print JSON if possible
                if (decryptedText.startsWith("{")) {
                    decryptedText = new org.json.JSONObject(decryptedText).toString(2);
                } else if (decryptedText.startsWith("[")) {
                    decryptedText = new org.json.JSONArray(decryptedText).toString(2);
                }

                return ToolResult.success(null,
                        "Content of " + filePath + " (decrypted):\n" + decryptedText);

            } catch (Exception decryptEx) {
                // Return raw text if decryption fails
                return ToolResult.success(null,
                        "Content of " + filePath + " (raw, could not decrypt):\n" + text);
            }

        } catch (Exception e) {
            return ToolResult.failure(null,
                    "Failed to read file '" + filePath + "': " + e.getMessage());
        }
    }
}
