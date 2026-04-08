package pro.sketchware.ai.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import pro.sketchware.ai.models.ToolResult;

/**
 * Contains tools for file operations within Sketchware Pro projects.
 * Files are stored under .sketchware/mysc/{sc_id}/.
 */
public final class FileTools {

    private FileTools() {
    }

    private static ToolResult success(String output) {
        return new ToolResult(null, true, output, null);
    }

    private static ToolResult error(String message) {
        return new ToolResult(null, false, null, message);
    }

    private static String readFileContent(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
        }
        return sb.toString();
    }

    private static void writeFileContent(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    private static void copyFile(File source, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private static boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }

    /**
     * Validates that a relative file path does not escape the project directory.
     */
    private static boolean isPathSafe(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return false;
        }
        // Reject paths that try to escape via ".."
        String normalized = relativePath.replace('\\', '/');
        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) {
                return false;
            }
        }
        return !normalized.startsWith("/");
    }

    /**
     * Reads a file from a project.
     */
    public static class ReadFileTool implements AgentTool {

        @Override
        public String getName() {
            return "read_file";
        }

        @Override
        public String getDescription() {
            return "Reads the contents of a file from a Sketchware Pro project. "
                    + "The file_path is relative to the project's mysc directory "
                    + "(e.g., \"src/main.java\", \"res/layout/main.xml\").";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID");
            properties.add("sc_id", scIdProp);

            JsonObject pathProp = new JsonObject();
            pathProp.addProperty("type", "string");
            pathProp.addProperty("description", "File path relative to the project root (e.g., \"src/MainActivity.java\")");
            properties.add("file_path", pathProp);

            JsonArray required = new JsonArray();
            required.add("sc_id");
            required.add("file_path");

            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", properties);
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            if (!arguments.has("sc_id") || arguments.get("sc_id").isJsonNull()) {
                return error("Missing required parameter: sc_id");
            }
            if (!arguments.has("file_path") || arguments.get("file_path").isJsonNull()) {
                return error("Missing required parameter: file_path");
            }

            String scId = arguments.get("sc_id").getAsString();
            String filePath = arguments.get("file_path").getAsString();

            if (!context.isProjectAllowed(scId)) {
                return error("Access denied: project " + scId + " is not in the current workspace");
            }

            if (!isPathSafe(filePath)) {
                return error("Invalid file path: path must be relative and cannot contain '..'");
            }

            File file = new File(context.getProjectMyscDir(scId), filePath);
            if (!file.exists()) {
                return error("File not found: " + filePath);
            }

            if (file.isDirectory()) {
                return error("Path is a directory, not a file: " + filePath);
            }

            try {
                String content = readFileContent(file);
                JsonObject result = new JsonObject();
                result.addProperty("file_path", filePath);
                result.addProperty("content", content);
                result.addProperty("size", file.length());
                return success(result.toString());
            } catch (IOException e) {
                return error("Failed to read file: " + e.getMessage());
            }
        }
    }

    /**
     * Writes or overwrites a file in a project.
     */
    public static class WriteFileTool implements AgentTool {

        @Override
        public String getName() {
            return "write_file";
        }

        @Override
        public String getDescription() {
            return "Writes content to a file in a Sketchware Pro project. "
                    + "Creates the file and parent directories if they don't exist. "
                    + "Overwrites the file if it already exists.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID");
            properties.add("sc_id", scIdProp);

            JsonObject pathProp = new JsonObject();
            pathProp.addProperty("type", "string");
            pathProp.addProperty("description", "File path relative to the project root");
            properties.add("file_path", pathProp);

            JsonObject contentProp = new JsonObject();
            contentProp.addProperty("type", "string");
            contentProp.addProperty("description", "The content to write to the file");
            properties.add("content", contentProp);

            JsonArray required = new JsonArray();
            required.add("sc_id");
            required.add("file_path");
            required.add("content");

            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", properties);
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            if (!arguments.has("sc_id") || arguments.get("sc_id").isJsonNull()) {
                return error("Missing required parameter: sc_id");
            }
            if (!arguments.has("file_path") || arguments.get("file_path").isJsonNull()) {
                return error("Missing required parameter: file_path");
            }
            if (!arguments.has("content") || arguments.get("content").isJsonNull()) {
                return error("Missing required parameter: content");
            }

            String scId = arguments.get("sc_id").getAsString();
            String filePath = arguments.get("file_path").getAsString();
            String content = arguments.get("content").getAsString();

            if (!context.isProjectAllowed(scId)) {
                return error("Access denied: project " + scId + " is not in the current workspace");
            }

            if (!isPathSafe(filePath)) {
                return error("Invalid file path: path must be relative and cannot contain '..'");
            }

            File file = new File(context.getProjectMyscDir(scId), filePath);

            try {
                writeFileContent(file, content);

                JsonObject result = new JsonObject();
                result.addProperty("file_path", filePath);
                result.addProperty("size", file.length());
                result.addProperty("message", "File written successfully");
                return success(result.toString());
            } catch (IOException e) {
                return error("Failed to write file: " + e.getMessage());
            }
        }
    }

    /**
     * Deletes a file from a project.
     */
    public static class DeleteFileTool implements AgentTool {

        @Override
        public String getName() {
            return "delete_file";
        }

        @Override
        public String getDescription() {
            return "Deletes a file from a Sketchware Pro project.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID");
            properties.add("sc_id", scIdProp);

            JsonObject pathProp = new JsonObject();
            pathProp.addProperty("type", "string");
            pathProp.addProperty("description", "File path relative to the project root");
            properties.add("file_path", pathProp);

            JsonArray required = new JsonArray();
            required.add("sc_id");
            required.add("file_path");

            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", properties);
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            if (!arguments.has("sc_id") || arguments.get("sc_id").isJsonNull()) {
                return error("Missing required parameter: sc_id");
            }
            if (!arguments.has("file_path") || arguments.get("file_path").isJsonNull()) {
                return error("Missing required parameter: file_path");
            }

            String scId = arguments.get("sc_id").getAsString();
            String filePath = arguments.get("file_path").getAsString();

            if (!context.isProjectAllowed(scId)) {
                return error("Access denied: project " + scId + " is not in the current workspace");
            }

            if (!isPathSafe(filePath)) {
                return error("Invalid file path: path must be relative and cannot contain '..'");
            }

            File file = new File(context.getProjectMyscDir(scId), filePath);
            if (!file.exists()) {
                return error("File not found: " + filePath);
            }

            boolean deleted;
            if (file.isDirectory()) {
                deleted = deleteRecursive(file);
            } else {
                deleted = file.delete();
            }

            if (deleted) {
                JsonObject result = new JsonObject();
                result.addProperty("file_path", filePath);
                result.addProperty("message", "File deleted successfully");
                return success(result.toString());
            } else {
                return error("Failed to delete file: " + filePath);
            }
        }
    }

    /**
     * Lists files in a project directory.
     */
    public static class ListFilesTool implements AgentTool {

        @Override
        public String getName() {
            return "list_files";
        }

        @Override
        public String getDescription() {
            return "Lists files and directories within a Sketchware Pro project directory. "
                    + "Returns name, type (file/directory), and size for each entry.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID");
            properties.add("sc_id", scIdProp);

            JsonObject dirProp = new JsonObject();
            dirProp.addProperty("type", "string");
            dirProp.addProperty("description", "Directory path relative to the project root. Defaults to root if not specified.");
            properties.add("directory", dirProp);

            JsonArray required = new JsonArray();
            required.add("sc_id");

            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", properties);
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            if (!arguments.has("sc_id") || arguments.get("sc_id").isJsonNull()) {
                return error("Missing required parameter: sc_id");
            }

            String scId = arguments.get("sc_id").getAsString();
            String directory = arguments.has("directory") && !arguments.get("directory").isJsonNull()
                    ? arguments.get("directory").getAsString() : "";

            if (!context.isProjectAllowed(scId)) {
                return error("Access denied: project " + scId + " is not in the current workspace");
            }

            if (!directory.isEmpty() && !isPathSafe(directory)) {
                return error("Invalid directory path: path must be relative and cannot contain '..'");
            }

            File dir;
            if (directory.isEmpty()) {
                dir = context.getProjectMyscDir(scId);
            } else {
                dir = new File(context.getProjectMyscDir(scId), directory);
            }

            if (!dir.exists()) {
                return error("Directory not found: " + (directory.isEmpty() ? "/" : directory));
            }

            if (!dir.isDirectory()) {
                return error("Path is not a directory: " + directory);
            }

            JsonArray entries = new JsonArray();
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("name", file.getName());
                    entry.addProperty("type", file.isDirectory() ? "directory" : "file");
                    entry.addProperty("size", file.isFile() ? file.length() : 0);
                    entries.add(entry);
                }
            }

            return success(entries.toString());
        }
    }

    /**
     * Copies a file within or between projects.
     */
    public static class CopyFileTool implements AgentTool {

        @Override
        public String getName() {
            return "copy_file";
        }

        @Override
        public String getDescription() {
            return "Copies a file within a project or between two projects. "
                    + "Both source and target projects must be in the workspace.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject srcScIdProp = new JsonObject();
            srcScIdProp.addProperty("type", "string");
            srcScIdProp.addProperty("description", "SC ID of the source project");
            properties.add("source_sc_id", srcScIdProp);

            JsonObject srcPathProp = new JsonObject();
            srcPathProp.addProperty("type", "string");
            srcPathProp.addProperty("description", "Source file path relative to the project root");
            properties.add("source_path", srcPathProp);

            JsonObject tgtScIdProp = new JsonObject();
            tgtScIdProp.addProperty("type", "string");
            tgtScIdProp.addProperty("description", "SC ID of the target project");
            properties.add("target_sc_id", tgtScIdProp);

            JsonObject tgtPathProp = new JsonObject();
            tgtPathProp.addProperty("type", "string");
            tgtPathProp.addProperty("description", "Target file path relative to the project root");
            properties.add("target_path", tgtPathProp);

            JsonArray required = new JsonArray();
            required.add("source_sc_id");
            required.add("source_path");
            required.add("target_sc_id");
            required.add("target_path");

            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", properties);
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            if (!arguments.has("source_sc_id") || arguments.get("source_sc_id").isJsonNull()) {
                return error("Missing required parameter: source_sc_id");
            }
            if (!arguments.has("source_path") || arguments.get("source_path").isJsonNull()) {
                return error("Missing required parameter: source_path");
            }
            if (!arguments.has("target_sc_id") || arguments.get("target_sc_id").isJsonNull()) {
                return error("Missing required parameter: target_sc_id");
            }
            if (!arguments.has("target_path") || arguments.get("target_path").isJsonNull()) {
                return error("Missing required parameter: target_path");
            }

            String sourceScId = arguments.get("source_sc_id").getAsString();
            String sourcePath = arguments.get("source_path").getAsString();
            String targetScId = arguments.get("target_sc_id").getAsString();
            String targetPath = arguments.get("target_path").getAsString();

            if (!context.isProjectAllowed(sourceScId)) {
                return error("Access denied: source project " + sourceScId + " is not in the current workspace");
            }
            if (!context.isProjectAllowed(targetScId)) {
                return error("Access denied: target project " + targetScId + " is not in the current workspace");
            }

            if (!isPathSafe(sourcePath)) {
                return error("Invalid source path: path must be relative and cannot contain '..'");
            }
            if (!isPathSafe(targetPath)) {
                return error("Invalid target path: path must be relative and cannot contain '..'");
            }

            File sourceFile = new File(context.getProjectMyscDir(sourceScId), sourcePath);
            File targetFile = new File(context.getProjectMyscDir(targetScId), targetPath);

            if (!sourceFile.exists()) {
                return error("Source file not found: " + sourcePath);
            }

            try {
                copyFile(sourceFile, targetFile);

                JsonObject result = new JsonObject();
                result.addProperty("source", sourceScId + ":" + sourcePath);
                result.addProperty("target", targetScId + ":" + targetPath);
                result.addProperty("message", "File copied successfully");
                return success(result.toString());
            } catch (IOException e) {
                return error("Failed to copy file: " + e.getMessage());
            }
        }
    }

    /**
     * Moves a file within a project.
     */
    public static class MoveFileTool implements AgentTool {

        @Override
        public String getName() {
            return "move_file";
        }

        @Override
        public String getDescription() {
            return "Moves or renames a file within a Sketchware Pro project.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID");
            properties.add("sc_id", scIdProp);

            JsonObject srcPathProp = new JsonObject();
            srcPathProp.addProperty("type", "string");
            srcPathProp.addProperty("description", "Current file path relative to the project root");
            properties.add("source_path", srcPathProp);

            JsonObject tgtPathProp = new JsonObject();
            tgtPathProp.addProperty("type", "string");
            tgtPathProp.addProperty("description", "New file path relative to the project root");
            properties.add("target_path", tgtPathProp);

            JsonArray required = new JsonArray();
            required.add("sc_id");
            required.add("source_path");
            required.add("target_path");

            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", properties);
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            if (!arguments.has("sc_id") || arguments.get("sc_id").isJsonNull()) {
                return error("Missing required parameter: sc_id");
            }
            if (!arguments.has("source_path") || arguments.get("source_path").isJsonNull()) {
                return error("Missing required parameter: source_path");
            }
            if (!arguments.has("target_path") || arguments.get("target_path").isJsonNull()) {
                return error("Missing required parameter: target_path");
            }

            String scId = arguments.get("sc_id").getAsString();
            String sourcePath = arguments.get("source_path").getAsString();
            String targetPath = arguments.get("target_path").getAsString();

            if (!context.isProjectAllowed(scId)) {
                return error("Access denied: project " + scId + " is not in the current workspace");
            }

            if (!isPathSafe(sourcePath)) {
                return error("Invalid source path: path must be relative and cannot contain '..'");
            }
            if (!isPathSafe(targetPath)) {
                return error("Invalid target path: path must be relative and cannot contain '..'");
            }

            File projectDir = context.getProjectMyscDir(scId);
            File sourceFile = new File(projectDir, sourcePath);
            File targetFile = new File(projectDir, targetPath);

            if (!sourceFile.exists()) {
                return error("Source file not found: " + sourcePath);
            }

            File targetParent = targetFile.getParentFile();
            if (targetParent != null && !targetParent.exists()) {
                targetParent.mkdirs();
            }

            if (sourceFile.renameTo(targetFile)) {
                JsonObject result = new JsonObject();
                result.addProperty("source_path", sourcePath);
                result.addProperty("target_path", targetPath);
                result.addProperty("message", "File moved successfully");
                return success(result.toString());
            } else {
                // renameTo can fail across filesystems; fall back to copy+delete
                try {
                    copyFile(sourceFile, targetFile);
                    if (sourceFile.delete()) {
                        JsonObject result = new JsonObject();
                        result.addProperty("source_path", sourcePath);
                        result.addProperty("target_path", targetPath);
                        result.addProperty("message", "File moved successfully");
                        return success(result.toString());
                    } else {
                        // Clean up the copy since we couldn't delete the source
                        targetFile.delete();
                        return error("Failed to remove source file after copy");
                    }
                } catch (IOException e) {
                    return error("Failed to move file: " + e.getMessage());
                }
            }
        }
    }
}
