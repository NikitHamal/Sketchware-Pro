package pro.sketchware.ai.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.ModelInfo;
import pro.sketchware.ai.models.ToolCall;

/**
 * AI API client for the Google Gemini (Generative Language) API.
 *
 * <p>Uses the v1beta endpoint with SSE streaming for chat completions
 * and API-key query-parameter authentication.
 */
public class GeminiApiClient extends AiApiClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public GeminiApiClient(String apiKey) {
        super(apiKey, AiProvider.GEMINI);
    }

    // -----------------------------------------------------------------------
    // Model listing
    // -----------------------------------------------------------------------

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        String url = BASE_URL + "/v1beta/models?key=" + apiKey;
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Gemini fetchModels failed: HTTP " + response.code()
                        + " " + readBodySafely(response));
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Gemini fetchModels returned empty body");
            }

            JsonObject root = JsonParser.parseString(body.string()).getAsJsonObject();
            JsonArray modelsArray = root.has("models") ? root.getAsJsonArray("models") : new JsonArray();

            List<ModelInfo> result = new ArrayList<>();
            for (JsonElement elem : modelsArray) {
                JsonObject model = elem.getAsJsonObject();

                // Only include models that support generateContent
                if (!supportsGenerateContent(model)) {
                    continue;
                }

                String name = getStringOrDefault(model, "name", "");
                String displayName = getStringOrDefault(model, "displayName", name);
                String description = getStringOrDefault(model, "description", "");
                long inputTokenLimit = model.has("inputTokenLimit")
                        ? model.get("inputTokenLimit").getAsLong() : 0L;

                result.add(new ModelInfo(name, displayName, AiProvider.GEMINI, inputTokenLimit, description));
            }

            return result;
        }
    }

    private boolean supportsGenerateContent(JsonObject model) {
        if (!model.has("supportedGenerationMethods")) {
            return false;
        }
        JsonArray methods = model.getAsJsonArray("supportedGenerationMethods");
        for (JsonElement method : methods) {
            if ("generateContent".equals(method.getAsString())) {
                return true;
            }
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Chat requests
    // -----------------------------------------------------------------------

    @Override
    public void sendChatRequest(List<ChatMessage> messages, String modelId,
                                String systemPrompt, StreamingResponseHandler handler) {
        sendChatRequest(messages, modelId, systemPrompt, null, handler);
    }

    @Override
    public void sendChatRequest(List<ChatMessage> messages, String modelId,
                                String systemPrompt, List<ToolDefinition> tools,
                                StreamingResponseHandler handler) {
        try {
            String url = BASE_URL + "/v1beta/" + modelId + ":streamGenerateContent?alt=sse&key=" + apiKey;

            JsonObject requestBody = buildRequestBody(messages, systemPrompt, tools);

            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(requestBody.toString(), JSON))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    handler.onError("Gemini request failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        String errorBody = readBodySafely(response);
                        handler.onError("Gemini HTTP " + response.code() + ": " + errorBody);
                        response.close();
                        return;
                    }

                    ResponseBody body = response.body();
                    if (body == null) {
                        handler.onError("Gemini returned empty response body");
                        return;
                    }

                    parseGeminiSseStream(body, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("Failed to build Gemini request: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Request body construction
    // -----------------------------------------------------------------------

    private JsonObject buildRequestBody(List<ChatMessage> messages, String systemPrompt,
                                        List<ToolDefinition> tools) {
        JsonObject body = new JsonObject();

        // System instruction
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JsonObject systemInstruction = new JsonObject();
            JsonArray parts = new JsonArray();
            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", systemPrompt);
            parts.add(textPart);
            systemInstruction.add("parts", parts);
            body.add("systemInstruction", systemInstruction);
        }

        // Contents
        JsonArray contents = new JsonArray();
        for (ChatMessage message : messages) {
            String role = message.getRole();
            // Skip system messages; they go into systemInstruction
            if ("system".equals(role)) {
                continue;
            }

            JsonObject content = new JsonObject();
            content.addProperty("role", mapRoleToGemini(role));

            JsonArray parts = new JsonArray();

            // Text content
            if (message.getContent() != null && !message.getContent().isEmpty()) {
                JsonObject textPart = new JsonObject();
                textPart.addProperty("text", message.getContent());
                parts.add(textPart);
            }

            // Tool call results from "tool" role messages
            if ("tool".equals(role) && message.getToolCallId() != null) {
                JsonObject functionResponse = new JsonObject();
                functionResponse.addProperty("name", message.getToolCallId());
                JsonObject responseContent = new JsonObject();
                responseContent.addProperty("result", message.getContent() != null ? message.getContent() : "");
                functionResponse.add("response", responseContent);

                JsonObject functionResponsePart = new JsonObject();
                functionResponsePart.add("functionResponse", functionResponse);
                parts.add(functionResponsePart);
            }

            // Tool calls from assistant messages
            if ("assistant".equals(role) && message.getToolCalls() != null) {
                for (ToolCall tc : message.getToolCalls()) {
                    JsonObject functionCall = new JsonObject();
                    functionCall.addProperty("name", tc.getName());
                    try {
                        JsonObject args = JsonParser.parseString(
                                tc.getArguments() != null ? tc.getArguments() : "{}").getAsJsonObject();
                        functionCall.add("args", args);
                    } catch (Exception e) {
                        functionCall.add("args", new JsonObject());
                    }

                    JsonObject functionCallPart = new JsonObject();
                    functionCallPart.add("functionCall", functionCall);
                    parts.add(functionCallPart);
                }
            }

            if (parts.size() > 0) {
                content.add("parts", parts);
                contents.add(content);
            }
        }
        body.add("contents", contents);

        // Tools
        if (tools != null && !tools.isEmpty()) {
            body.add("tools", buildToolsPayload(tools));

            // Allow the model to decide when to call functions
            JsonObject toolConfig = new JsonObject();
            JsonObject functionCallingConfig = new JsonObject();
            functionCallingConfig.addProperty("mode", "AUTO");
            toolConfig.add("functionCallingConfig", functionCallingConfig);
            body.add("toolConfig", toolConfig);
        }

        return body;
    }

    /**
     * Maps standard chat roles to Gemini API roles.
     */
    private String mapRoleToGemini(String role) {
        switch (role) {
            case "assistant":
                return "model";
            case "tool":
                return "user";
            default:
                return "user";
        }
    }

    /**
     * Builds the Gemini tools payload from tool definitions.
     *
     * <pre>
     * [
     *   {
     *     "functionDeclarations": [
     *       { "name": "...", "description": "...", "parameters": { ... } },
     *       ...
     *     ]
     *   }
     * ]
     * </pre>
     */
    public JsonArray buildToolsPayload(List<ToolDefinition> tools) {
        JsonArray declarations = new JsonArray();
        for (ToolDefinition tool : tools) {
            declarations.add(tool.toGeminiJson());
        }

        JsonObject toolsObject = new JsonObject();
        toolsObject.add("functionDeclarations", declarations);

        JsonArray toolsArray = new JsonArray();
        toolsArray.add(toolsObject);
        return toolsArray;
    }

    // -----------------------------------------------------------------------
    // SSE stream parsing
    // -----------------------------------------------------------------------

    private void parseGeminiSseStream(ResponseBody body, StreamingResponseHandler handler) {
        StringBuilder fullResponse = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // SSE format: "data: {...}"
                if (!line.startsWith("data: ")) {
                    continue;
                }

                String data = line.substring(6).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) {
                    continue;
                }

                try {
                    JsonObject event = JsonParser.parseString(data).getAsJsonObject();
                    processGeminiEvent(event, fullResponse, handler);
                } catch (Exception e) {
                    // Skip malformed JSON chunks gracefully
                }
            }

            handler.onComplete(fullResponse.toString());
        } catch (IOException e) {
            handler.onError("Error reading Gemini stream: " + e.getMessage());
        }
    }

    private void processGeminiEvent(JsonObject event, StringBuilder fullResponse,
                                    StreamingResponseHandler handler) {
        if (!event.has("candidates")) {
            return;
        }

        JsonArray candidates = event.getAsJsonArray("candidates");
        if (candidates.size() == 0) {
            return;
        }

        JsonObject candidate = candidates.get(0).getAsJsonObject();
        if (!candidate.has("content")) {
            return;
        }

        JsonObject content = candidate.getAsJsonObject("content");
        if (!content.has("parts")) {
            return;
        }

        JsonArray parts = content.getAsJsonArray("parts");
        for (JsonElement partElem : parts) {
            JsonObject part = partElem.getAsJsonObject();

            // Text chunk
            if (part.has("text")) {
                String text = part.get("text").getAsString();
                fullResponse.append(text);
                handler.onChunk(text);
            }

            // Function call
            if (part.has("functionCall")) {
                JsonObject functionCall = part.getAsJsonObject("functionCall");
                String name = getStringOrDefault(functionCall, "name", "unknown");
                JsonObject args = functionCall.has("args")
                        ? functionCall.getAsJsonObject("args") : new JsonObject();

                String callId = "call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
                ToolCall toolCall = new ToolCall(callId, name, args.toString());
                handler.onToolCall(toolCall);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Sketchware tool definitions
    // -----------------------------------------------------------------------

    /**
     * Returns the comprehensive set of Sketchware operation tool definitions
     * for use with Gemini function calling.
     */
    public static List<ToolDefinition> getSketchwareTools() {
        List<ToolDefinition> tools = new ArrayList<>();

        // -- Project management --
        tools.add(new ToolDefinition("create_project",
                "Create a new Sketchware project with the given package name, app name, and project settings.",
                buildParams(
                        param("package_name", "string", "The Java package name (e.g., com.example.myapp)"),
                        param("app_name", "string", "The display name of the application"),
                        param("project_type", "string", "The project type: 'activity' or 'fragment'. Defaults to 'activity'.")
                )));

        tools.add(new ToolDefinition("delete_project",
                "Permanently delete a Sketchware project by its project ID.",
                buildParams(
                        param("project_id", "string", "The unique identifier of the project to delete")
                )));

        tools.add(new ToolDefinition("list_projects",
                "List all Sketchware projects with their IDs, names, and package names.",
                buildParams()));

        tools.add(new ToolDefinition("get_project_info",
                "Get detailed information about a specific Sketchware project including its settings, activities, and libraries.",
                buildParams(
                        param("project_id", "string", "The unique identifier of the project")
                )));

        tools.add(new ToolDefinition("duplicate_project",
                "Create a duplicate copy of an existing Sketchware project with a new name.",
                buildParams(
                        param("project_id", "string", "The unique identifier of the project to duplicate"),
                        param("new_name", "string", "The display name for the duplicated project")
                )));

        // -- Activity management --
        tools.add(new ToolDefinition("create_activity",
                "Create a new activity in a Sketchware project with the specified name and options.",
                buildParams(
                        param("project_id", "string", "The unique identifier of the project"),
                        param("activity_name", "string", "The name of the activity class (e.g., MainActivity)"),
                        param("layout_name", "string", "The layout XML file name (e.g., activity_main)")
                )));

        tools.add(new ToolDefinition("delete_activity",
                "Delete an activity and its associated layout from a Sketchware project.",
                buildParams(
                        param("project_id", "string", "The unique identifier of the project"),
                        param("activity_name", "string", "The name of the activity class to delete")
                )));

        tools.add(new ToolDefinition("list_activities",
                "List all activities in a Sketchware project with their names and associated layouts.",
                buildParams(
                        param("project_id", "string", "The unique identifier of the project")
                )));

        // -- File operations --
        tools.add(new ToolDefinition("read_file",
                "Read the contents of a file in the Sketchware project (Java source, XML layout, resource, etc.).",
                buildParams(
                        param("project_id", "string", "The unique identifier of the project"),
                        param("file_path", "string", "The relative path of the file within the project")
                )));

        tools.add(new ToolDefinition("write_file",
                "Write or overwrite the contents of a file in the Sketchware project.",
                buildParams(
                        param("project_id", "string", "The unique identifier of the project"),
                        param("file_path", "string", "The relative path of the file within the project"),
                        param("content", "string", "The full content to write to the file")
                )));

        tools.add(new ToolDefinition("delete_file",
                "Delete a file from the Sketchware project.",
                buildParams(
                        param("project_id", "string", "The unique identifier of the project"),
                        param("file_path", "string", "The relative path of the file to delete")
                )));

        tools.add(new ToolDefinition("list_files",
                "List all files in a Sketchware project directory, optionally filtered by type.",
                buildParams(
                        param("project_id", "string", "The unique identifier of the project"),
                        param("directory", "string", "The relative directory path to list (e.g., 'java', 'res/layout')"),
                        param("file_type", "string", "Optional filter: 'java', 'xml', 'all'. Defaults to 'all'.")
                )));

        tools.add(new ToolDefinition("copy_file",
                "Copy a file within a Sketchware project to a new location.",
                buildParams(
                        param("project_id", "string", "The unique identifier of the project"),
                        param("source_path", "string", "The relative path of the source file"),
                        param("destination_path", "string", "The relative path for the copied file")
                )));

        tools.add(new ToolDefinition("move_file",
                "Move or rename a file within a Sketchware project.",
                buildParams(
                        param("project_id", "string", "The unique identifier of the project"),
                        param("source_path", "string", "The current relative path of the file"),
                        param("destination_path", "string", "The new relative path for the file")
                )));

        // -- Layout operations --
        tools.add(new ToolDefinition("create_layout_xml",
                "Create a new XML layout file for a Sketchware project activity or fragment.",
                buildParams(
                        param("project_id", "string", "The unique identifier of the project"),
                        param("layout_name", "string", "The layout file name without extension (e.g., activity_main)"),
                        param("xml_content", "string", "The full XML content of the layout")
                )));

        tools.add(new ToolDefinition("edit_layout_xml",
                "Replace the XML content of an existing layout file in a Sketchware project.",
                buildParams(
                        param("project_id", "string", "The unique identifier of the project"),
                        param("layout_name", "string", "The layout file name without extension"),
                        param("xml_content", "string", "The new full XML content for the layout")
                )));

        tools.add(new ToolDefinition("get_layout_xml",
                "Get the XML content of a layout file in a Sketchware project.",
                buildParams(
                        param("project_id", "string", "The unique identifier of the project"),
                        param("layout_name", "string", "The layout file name without extension")
                )));

        // -- Resources --
        tools.add(new ToolDefinition("add_string_resource",
                "Add a string resource entry to the Sketchware project's strings.xml.",
                buildParams(
                        param("project_id", "string", "The unique identifier of the project"),
                        param("resource_name", "string", "The resource name identifier (e.g., app_name)"),
                        param("value", "string", "The string value")
                )));

        tools.add(new ToolDefinition("add_color_resource",
                "Add a color resource entry to the Sketchware project's colors.xml.",
                buildParams(
                        param("project_id", "string", "The unique identifier of the project"),
                        param("resource_name", "string", "The resource name identifier (e.g., primary_color)"),
                        param("value", "string", "The color hex value (e.g., #FF5722)")
                )));

        // -- Build --
        tools.add(new ToolDefinition("compile_project",
                "Compile and build the Sketchware project, producing an APK if successful.",
                buildParams(
                        param("project_id", "string", "The unique identifier of the project")
                )));

        tools.add(new ToolDefinition("get_compile_logs",
                "Get the compilation log output from the last build attempt of a Sketchware project.",
                buildParams(
                        param("project_id", "string", "The unique identifier of the project")
                )));

        // -- Libraries --
        tools.add(new ToolDefinition("add_library",
                "Add a library dependency to a Sketchware project.",
                buildParams(
                        param("project_id", "string", "The unique identifier of the project"),
                        param("library_name", "string", "The library identifier or Maven coordinate"),
                        param("library_type", "string", "The type of library: 'builtin', 'local', or 'maven'")
                )));

        tools.add(new ToolDefinition("remove_library",
                "Remove a library dependency from a Sketchware project.",
                buildParams(
                        param("project_id", "string", "The unique identifier of the project"),
                        param("library_name", "string", "The library identifier to remove")
                )));

        tools.add(new ToolDefinition("list_libraries",
                "List all library dependencies currently added to a Sketchware project.",
                buildParams(
                        param("project_id", "string", "The unique identifier of the project")
                )));

        // -- Project structure --
        tools.add(new ToolDefinition("get_project_structure",
                "Get the complete directory and file structure of a Sketchware project as a tree.",
                buildParams(
                        param("project_id", "string", "The unique identifier of the project")
                )));

        return tools;
    }

    // -----------------------------------------------------------------------
    // JSON Schema helpers for tool parameter construction
    // -----------------------------------------------------------------------

    private static JsonObject buildParams(JsonObject... properties) {
        JsonObject params = new JsonObject();
        params.addProperty("type", "object");

        JsonObject props = new JsonObject();
        JsonArray required = new JsonArray();

        for (JsonObject prop : properties) {
            String name = prop.get("_name").getAsString();
            prop.remove("_name");
            boolean isRequired = !prop.has("optional") || !prop.get("optional").getAsBoolean();
            prop.remove("optional");
            props.add(name, prop);
            if (isRequired) {
                required.add(name);
            }
        }

        params.add("properties", props);
        if (required.size() > 0) {
            params.add("required", required);
        }

        return params;
    }

    private static JsonObject param(String name, String type, String description) {
        JsonObject p = new JsonObject();
        p.addProperty("_name", name);
        p.addProperty("type", type);
        p.addProperty("description", description);
        return p;
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    private static String getStringOrDefault(JsonObject obj, String key, String defaultValue) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return defaultValue;
    }

    private static String readBodySafely(Response response) {
        try {
            ResponseBody body = response.body();
            return body != null ? body.string() : "(no body)";
        } catch (Exception e) {
            return "(failed to read body: " + e.getMessage() + ")";
        }
    }
}
