package pro.sketchware.agent;

import static pro.sketchware.utility.GsonUtils.getGson;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import a.a.a.yB;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AgentChatOrchestrator {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_TOOL_ROUNDS = 6;

    private final OkHttpClient httpClient = new OkHttpClient.Builder().build();
    private final AgentRepository repository;
    private final AgentProjectManager projectManager;

    public AgentChatOrchestrator(@NonNull Context context, @NonNull AgentRepository repository) {
        this.repository = repository;
        projectManager = new AgentProjectManager(context.getApplicationContext(), repository);
    }

    @NonNull
    public AgentRepository.Conversation runTurn(@NonNull AgentRepository.Workspace workspace,
                                                @NonNull AgentRepository.Conversation conversation,
                                                @NonNull String userPrompt) throws Exception {
        AgentProvider provider = AgentProvider.fromId(conversation.providerId);
        AgentRepository.ProviderState providerState = repository.getProviderState(provider);
        if (TextUtils.isEmpty(providerState.apiKey)) {
            throw new IOException(provider.displayName + " API key is missing.");
        }
        if (TextUtils.isEmpty(conversation.modelId)) {
            if (!providerState.cachedModels.isEmpty()) {
                conversation.modelId = providerState.cachedModels.get(0).id;
            } else {
                throw new IOException("No cached models are available for " + provider.displayName + ". Refresh models first.");
            }
        }

        conversation.addMessage("user", null, userPrompt);

        switch (provider) {
            case GEMINI -> runGeminiTurn(workspace, conversation, providerState.apiKey);
            case NVIDIA, OPENROUTER -> runOpenAiCompatibleTurn(workspace, conversation, provider, providerState.apiKey);
        }

        repository.saveConversation(conversation);
        return conversation;
    }

    private void runOpenAiCompatibleTurn(@NonNull AgentRepository.Workspace workspace,
                                         @NonNull AgentRepository.Conversation conversation,
                                         @NonNull AgentProvider provider,
                                         @NonNull String apiKey) throws Exception {
        String endpoint = provider == AgentProvider.OPENROUTER
                ? "https://openrouter.ai/api/v1/chat/completions"
                : "https://integrate.api.nvidia.com/v1/chat/completions";

        JsonArray tools = buildOpenAiTools();
        JsonArray messages = buildOpenAiMessages(workspace, conversation);
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", conversation.modelId);
            requestBody.add("messages", messages);
            requestBody.add("tools", tools);
            requestBody.addProperty("tool_choice", "auto");

            Request.Builder requestBuilder = new Request.Builder()
                    .url(endpoint)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody.toString(), JSON));
            if (provider == AgentProvider.OPENROUTER) {
                requestBuilder.header("HTTP-Referer", "https://github.com/Sketchware-Pro")
                        .header("X-Title", "Sketchware Pro Agent");
            }

            try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
                String body = bodyOrThrow(response);
                JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                JsonObject message = root.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");

                JsonArray toolCalls = message.getAsJsonArray("tool_calls");
                String content = stringOrNull(message, "content");
                if (toolCalls == null || toolCalls.size() == 0) {
                    conversation.addMessage("assistant", null, TextUtils.isEmpty(content) ? "Completed." : content);
                    return;
                }

                JsonObject assistantMessage = new JsonObject();
                assistantMessage.addProperty("role", "assistant");
                if (!TextUtils.isEmpty(content)) {
                    assistantMessage.addProperty("content", content);
                } else {
                    assistantMessage.add("content", null);
                }
                assistantMessage.add("tool_calls", toolCalls);
                messages.add(assistantMessage);

                for (JsonElement element : toolCalls) {
                    JsonObject toolCall = element.getAsJsonObject();
                    JsonObject function = toolCall.getAsJsonObject("function");
                    String toolName = stringOrNull(function, "name");
                    String arguments = stringOrNull(function, "arguments");
                    String toolResult = executeTool(workspace, toolName, arguments);
                    conversation.addMessage("tool", toolName, toolResult);

                    JsonObject toolMessage = new JsonObject();
                    toolMessage.addProperty("role", "tool");
                    toolMessage.addProperty("tool_call_id", stringOrNull(toolCall, "id"));
                    toolMessage.addProperty("name", toolName);
                    toolMessage.addProperty("content", toolResult);
                    messages.add(toolMessage);
                }
            }
        }

        conversation.addMessage("assistant", null, "The selected model kept requesting tools without finishing. Review the tool output and continue from there.");
    }

    private void runGeminiTurn(@NonNull AgentRepository.Workspace workspace,
                               @NonNull AgentRepository.Conversation conversation,
                               @NonNull String apiKey) throws Exception {
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/"
                + URLEncoder.encode(conversation.modelId, StandardCharsets.UTF_8)
                + ":generateContent?key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);

        JsonArray contents = buildGeminiContents(conversation);
        JsonObject toolContainer = new JsonObject();
        toolContainer.add("functionDeclarations", buildGeminiTools());
        JsonArray tools = new JsonArray();
        tools.add(toolContainer);

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            JsonObject requestBody = new JsonObject();
            requestBody.add("contents", contents);
            requestBody.add("tools", tools);
            JsonObject systemInstruction = new JsonObject();
            JsonArray systemParts = new JsonArray();
            JsonObject systemText = new JsonObject();
            systemText.addProperty("text", buildSystemPrompt(workspace));
            systemParts.add(systemText);
            systemInstruction.add("parts", systemParts);
            requestBody.add("systemInstruction", systemInstruction);

            Request request = new Request.Builder()
                    .url(endpoint)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody.toString(), JSON))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String body = bodyOrThrow(response);
                JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                JsonObject candidate = root.getAsJsonArray("candidates").get(0).getAsJsonObject();
                JsonObject content = candidate.getAsJsonObject("content");
                JsonArray parts = content.getAsJsonArray("parts");
                if (parts == null || parts.size() == 0) {
                    conversation.addMessage("assistant", null, "The model returned an empty response.");
                    return;
                }

                contents.add(content);
                ArrayList<JsonObject> functionCalls = new ArrayList<>();
                StringBuilder textOutput = new StringBuilder();
                for (JsonElement partElement : parts) {
                    JsonObject part = partElement.getAsJsonObject();
                    if (part.has("text")) {
                        textOutput.append(part.get("text").getAsString());
                    } else if (part.has("functionCall")) {
                        functionCalls.add(part.getAsJsonObject("functionCall"));
                    }
                }

                if (functionCalls.isEmpty()) {
                    String finalText = textOutput.toString().trim();
                    conversation.addMessage("assistant", null, finalText.isEmpty() ? "Completed." : finalText);
                    return;
                }

                JsonObject functionResponseContent = new JsonObject();
                functionResponseContent.addProperty("role", "user");
                JsonArray responseParts = new JsonArray();
                for (JsonObject functionCall : functionCalls) {
                    String toolName = stringOrNull(functionCall, "name");
                    JsonObject args = functionCall.getAsJsonObject("args");
                    String toolResult = executeTool(workspace, toolName, args == null ? "{}" : args.toString());
                    conversation.addMessage("tool", toolName, toolResult);

                    JsonObject part = new JsonObject();
                    JsonObject functionResponse = new JsonObject();
                    functionResponse.addProperty("name", toolName);
                    JsonObject responseBody = new JsonObject();
                    responseBody.add("result", JsonParser.parseString(toolResult));
                    functionResponse.add("response", responseBody);
                    part.add("functionResponse", functionResponse);
                    responseParts.add(part);
                }
                functionResponseContent.add("parts", responseParts);
                contents.add(functionResponseContent);
            }
        }

        conversation.addMessage("assistant", null, "The selected Gemini model kept requesting tools without finishing. Continue the conversation to steer the next step.");
    }

    @NonNull
    private JsonArray buildOpenAiMessages(@NonNull AgentRepository.Workspace workspace,
                                          @NonNull AgentRepository.Conversation conversation) {
        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", buildSystemPrompt(workspace));
        messages.add(system);

        for (AgentRepository.Message message : conversation.messages) {
            if ("tool".equals(message.role)) {
                continue;
            }
            JsonObject item = new JsonObject();
            item.addProperty("role", message.role);
            item.addProperty("content", message.content);
            messages.add(item);
        }
        return messages;
    }

    @NonNull
    private JsonArray buildGeminiContents(@NonNull AgentRepository.Conversation conversation) {
        JsonArray contents = new JsonArray();
        for (AgentRepository.Message message : conversation.messages) {
            JsonObject content = new JsonObject();
            String role = "assistant".equals(message.role) ? "model" : "user";
            if ("tool".equals(message.role)) {
                continue;
            }
            content.addProperty("role", role);
            JsonArray parts = new JsonArray();
            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", message.content);
            parts.add(textPart);
            content.add("parts", parts);
            contents.add(content);
        }
        return contents;
    }

    @NonNull
    private String buildSystemPrompt(@NonNull AgentRepository.Workspace workspace) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are Sketchware Pro's autonomous coding agent. ");
        builder.append("You may only operate on projects already attached to this workspace. ");
        builder.append("Always use tools for project mutations, file reads, file writes, deletions, moves, duplication, or compile-log access. ");
        builder.append("Project file paths are relative to the project's .sketchware/data/<project_id>/ folder. ");
        builder.append("Read files before overwriting them unless the user explicitly asks to replace them. ");
        builder.append("When a task is complete, return a concise markdown summary of what changed and what remains.\n\n");
        builder.append("Workspace projects:\n");

        List<HashMap<String, Object>> projects = projectManager.listWorkspaceProjects(workspace);
        if (projects.isEmpty()) {
            builder.append("- No projects are currently attached.\n");
        } else {
            for (HashMap<String, Object> project : projects) {
                builder.append("- ")
                        .append(yB.c(project, "my_ws_name"))
                        .append(" (id: ").append(yB.c(project, "sc_id")).append(")")
                        .append(", package: ").append(yB.c(project, "my_sc_pkg_name"))
                        .append(", app: ").append(yB.c(project, "my_app_name"))
                        .append('\n');
            }
        }
        return builder.toString();
    }

    @NonNull
    private String executeTool(@NonNull AgentRepository.Workspace workspace, @NonNull String toolName,
                               @Nullable String rawArguments) {
        try {
            JsonObject arguments = TextUtils.isEmpty(rawArguments)
                    ? new JsonObject()
                    : JsonParser.parseString(rawArguments).getAsJsonObject();

            Object result = switch (toolName) {
                case "list_workspace_projects" -> buildWorkspaceProjectsResult(workspace);
                case "create_project" -> projectManager.createProject(
                        workspace,
                        requiredString(arguments, "project_name"),
                        stringOrNull(arguments, "app_name"),
                        stringOrNull(arguments, "package_name"),
                        stringOrNull(arguments, "version_name"),
                        stringOrNull(arguments, "version_code"));
                case "delete_project" -> {
                    String projectId = requiredString(arguments, "project_id");
                    verifyProjectInWorkspace(workspace, projectId);
                    projectManager.deleteProject(workspace, projectId);
                    LinkedHashMap<String, Object> result = new LinkedHashMap<>();
                    result.put("deleted_project_id", projectId);
                    yield result;
                }
                case "duplicate_project" -> {
                    String projectId = requiredString(arguments, "project_id");
                    verifyProjectInWorkspace(workspace, projectId);
                    yield projectManager.duplicateProject(workspace, projectId,
                            requiredString(arguments, "new_project_name"), stringOrNull(arguments, "new_app_name"));
                }
                case "list_project_files" -> {
                    String projectId = requiredString(arguments, "project_id");
                    verifyProjectInWorkspace(workspace, projectId);
                    yield projectManager.listFiles(projectId, stringOrNull(arguments, "directory"),
                            booleanOrFalse(arguments, "recursive"));
                }
                case "read_project_file" -> {
                    String projectId = requiredString(arguments, "project_id");
                    verifyProjectInWorkspace(workspace, projectId);
                    yield projectManager.readFile(projectId,
                            requiredString(arguments, "relative_path"),
                            integerOrNull(arguments, "offset"),
                            integerOrNull(arguments, "length"));
                }
                case "write_project_file" -> {
                    String projectId = requiredString(arguments, "project_id");
                    verifyProjectInWorkspace(workspace, projectId);
                    yield projectManager.writeFile(projectId,
                            requiredString(arguments, "relative_path"),
                            requiredString(arguments, "content"));
                }
                case "delete_project_file" -> {
                    String projectId = requiredString(arguments, "project_id");
                    verifyProjectInWorkspace(workspace, projectId);
                    yield projectManager.deleteFile(projectId, requiredString(arguments, "relative_path"));
                }
                case "move_project_file" -> {
                    String projectId = requiredString(arguments, "project_id");
                    verifyProjectInWorkspace(workspace, projectId);
                    yield projectManager.moveFile(projectId,
                            requiredString(arguments, "from_path"),
                            requiredString(arguments, "to_path"));
                }
                case "read_compile_log" -> {
                    String projectId = requiredString(arguments, "project_id");
                    verifyProjectInWorkspace(workspace, projectId);
                    yield projectManager.readCompileLog(projectId, integerOrNull(arguments, "offset"),
                            integerOrNull(arguments, "length"));
                }
                default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
            };
            return getGson().toJson(result);
        } catch (Exception e) {
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
            return error.toString();
        }
    }

    @NonNull
    private JsonArray buildOpenAiTools() {
        JsonArray tools = new JsonArray();
        for (JsonObject function : buildToolDeclarations()) {
            JsonObject tool = new JsonObject();
            tool.addProperty("type", "function");
            tool.add("function", function);
            tools.add(tool);
        }
        return tools;
    }

    @NonNull
    private JsonArray buildGeminiTools() {
        JsonArray declarations = new JsonArray();
        for (JsonObject declaration : buildToolDeclarations()) {
            declarations.add(declaration);
        }
        return declarations;
    }

    @NonNull
    private ArrayList<JsonObject> buildToolDeclarations() {
        ArrayList<JsonObject> tools = new ArrayList<>();
        tools.add(function("list_workspace_projects",
                "List every project attached to the current workspace.",
                objectSchema(new LinkedHashMap<>())));
        tools.add(function("create_project",
                "Create a brand new blank Sketchware Pro project and attach it to the current workspace.",
                objectSchema(linkedMapOf(
                        "project_name", stringSchema("Workspace-visible project name."),
                        "app_name", stringSchema("Android app label shown to users."),
                        "package_name", stringSchema("Java package name such as com.example.app."),
                        "version_name", stringSchema("Version name such as 1.0."),
                        "version_code", stringSchema("Version code such as 1.")
                ), "project_name")));
        tools.add(function("delete_project",
                "Delete a project and remove it from the workspace.",
                objectSchema(linkedMapOf("project_id", stringSchema("Sketchware project id.")), "project_id")));
        tools.add(function("duplicate_project",
                "Duplicate an existing workspace project into a new project attached to the workspace.",
                objectSchema(linkedMapOf(
                        "project_id", stringSchema("Source project id."),
                        "new_project_name", stringSchema("Name of the duplicated project."),
                        "new_app_name", stringSchema("Optional new app label.")
                ), "project_id", "new_project_name")));
        tools.add(function("list_project_files",
                "List files inside a workspace project data directory.",
                objectSchema(linkedMapOf(
                        "project_id", stringSchema("Project id."),
                        "directory", stringSchema("Directory relative to .sketchware/data/<project_id>/. Leave empty for the project root."),
                        "recursive", booleanSchema("Whether to walk nested directories.")
                ), "project_id")));
        tools.add(function("read_project_file",
                "Read a file from a workspace project. Use offset and length for large files.",
                objectSchema(linkedMapOf(
                        "project_id", stringSchema("Project id."),
                        "relative_path", stringSchema("File path relative to .sketchware/data/<project_id>/."),
                        "offset", integerSchema("Optional character offset."),
                        "length", integerSchema("Optional maximum characters to read.")
                ), "project_id", "relative_path")));
        tools.add(function("write_project_file",
                "Create or overwrite a file inside a workspace project.",
                objectSchema(linkedMapOf(
                        "project_id", stringSchema("Project id."),
                        "relative_path", stringSchema("File path relative to .sketchware/data/<project_id>/."),
                        "content", stringSchema("Full file contents to write.")
                ), "project_id", "relative_path", "content")));
        tools.add(function("delete_project_file",
                "Delete a file or directory inside a workspace project.",
                objectSchema(linkedMapOf(
                        "project_id", stringSchema("Project id."),
                        "relative_path", stringSchema("Target path relative to .sketchware/data/<project_id>/.")
                ), "project_id", "relative_path")));
        tools.add(function("move_project_file",
                "Move or rename a file or directory inside a workspace project.",
                objectSchema(linkedMapOf(
                        "project_id", stringSchema("Project id."),
                        "from_path", stringSchema("Existing source path."),
                        "to_path", stringSchema("Destination path.")
                ), "project_id", "from_path", "to_path")));
        tools.add(function("read_compile_log",
                "Read the last saved compile log for a workspace project.",
                objectSchema(linkedMapOf(
                        "project_id", stringSchema("Project id."),
                        "offset", integerSchema("Optional character offset."),
                        "length", integerSchema("Optional maximum characters to read.")
                ), "project_id")));
        return tools;
    }

    @NonNull
    private JsonObject function(@NonNull String name, @NonNull String description, @NonNull JsonObject parameters) {
        JsonObject function = new JsonObject();
        function.addProperty("name", name);
        function.addProperty("description", description);
        function.add("parameters", parameters);
        return function;
    }

    @NonNull
    private JsonObject objectSchema(@NonNull LinkedHashMap<String, JsonObject> properties, String... requiredKeys) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        for (Map.Entry<String, JsonObject> entry : properties.entrySet()) {
            props.add(entry.getKey(), entry.getValue());
        }
        schema.add("properties", props);
        JsonArray required = new JsonArray();
        for (String key : requiredKeys) {
            required.add(key);
        }
        schema.add("required", required);
        return schema;
    }

    @NonNull
    private LinkedHashMap<String, JsonObject> linkedMapOf(Object... values) {
        LinkedHashMap<String, JsonObject> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put((String) values[i], (JsonObject) values[i + 1]);
        }
        return map;
    }

    @NonNull
    private JsonObject stringSchema(@NonNull String description) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "string");
        schema.addProperty("description", description);
        return schema;
    }

    @NonNull
    private JsonObject integerSchema(@NonNull String description) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "integer");
        schema.addProperty("description", description);
        return schema;
    }

    @NonNull
    private JsonObject booleanSchema(@NonNull String description) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "boolean");
        schema.addProperty("description", description);
        return schema;
    }

    @NonNull
    private LinkedHashMap<String, Object> buildWorkspaceProjectsResult(@NonNull AgentRepository.Workspace workspace) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("workspace_id", workspace.id);
        result.put("workspace_name", workspace.name);
        result.put("projects", projectManager.listWorkspaceProjects(workspace));
        return result;
    }

    private void verifyProjectInWorkspace(@NonNull AgentRepository.Workspace workspace, @NonNull String projectId) {
        if (!workspace.projectIds.contains(projectId)) {
            throw new IllegalArgumentException("Project " + projectId + " is not attached to this workspace.");
        }
    }

    @NonNull
    private String requiredString(@NonNull JsonObject object, @NonNull String key) {
        String value = stringOrNull(object, key);
        if (TextUtils.isEmpty(value)) {
            throw new IllegalArgumentException("Missing required argument: " + key);
        }
        return value;
    }

    private Integer integerOrNull(@NonNull JsonObject object, @NonNull String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsInt();
    }

    private boolean booleanOrFalse(@NonNull JsonObject object, @NonNull String key) {
        JsonElement element = object.get(key);
        return element != null && !element.isJsonNull() && element.getAsBoolean();
    }

    private String stringOrNull(@NonNull JsonObject object, @NonNull String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    @NonNull
    private String bodyOrThrow(@NonNull Response response) throws IOException {
        String body = response.body() != null ? response.body().string() : "";
        if (!response.isSuccessful()) {
            throw new IOException("HTTP " + response.code() + ": " + body);
        }
        return body;
    }
}
