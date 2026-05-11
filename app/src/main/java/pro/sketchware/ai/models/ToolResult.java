package pro.sketchware.ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonObject;

public class ToolResult {

    private final String toolCallId;
    private final boolean success;
    private final String output;
    private final String error;
    @Nullable
    private final String contentType;
    @Nullable
    private final byte[] rawData;

    public ToolResult(String toolCallId, boolean success, String output, String error) {
        this(toolCallId, success, output, error, null, null);
    }

    public ToolResult(String toolCallId, boolean success, String output, String error,
                      @Nullable String contentType, @Nullable byte[] rawData) {
        this.toolCallId = toolCallId;
        this.success = success;
        this.output = output;
        this.error = error;
        this.contentType = contentType;
        this.rawData = rawData;
    }

    @NonNull
    public static ToolResult success(@NonNull String toolCallId, @NonNull String output) {
        return new ToolResult(toolCallId, true, output, null);
    }

    @NonNull
    public static ToolResult successJson(@NonNull String toolCallId, @NonNull String jsonContent) {
        return new ToolResult(toolCallId, true, jsonContent, null, "application/json", null);
    }

    @NonNull
    public static ToolResult failure(@NonNull String toolCallId, @NonNull String error) {
        return new ToolResult(toolCallId, false, null, error);
    }

    @NonNull
    public static ToolResult failure(@NonNull String toolCallId, @NonNull String toolName, @NonNull String error) {
        return new ToolResult(toolCallId, false, null, "[" + toolName + " failed] " + error);
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getOutput() {
        return output;
    }

    public String getError() {
        return error;
    }

    @Nullable
    public String getContentType() {
        return contentType;
    }

    @Nullable
    public byte[] getRawData() {
        return rawData;
    }

    public String getContent() {
        return success ? output : error;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("toolCallId", toolCallId);
        json.addProperty("success", success);
        if (output != null) {
            json.addProperty("output", output);
        }
        if (error != null) {
            json.addProperty("error", error);
        }
        if (contentType != null) {
            json.addProperty("contentType", contentType);
        }
        return json;
    }

    @Override
    public String toString() {
        return "ToolResult{toolCallId='" + toolCallId + "', success=" + success + "}";
    }
}