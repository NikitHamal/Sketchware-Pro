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
 * AI API client for the NVIDIA NIM API (OpenAI-compatible).
 *
 * <p>Uses standard OpenAI chat completion format with bearer token authentication.
 */
public class NvidiaApiClient extends AiApiClient {

    private static final String BASE_URL = "https://integrate.api.nvidia.com";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public NvidiaApiClient(String apiKey) {
        super(apiKey, AiProvider.NVIDIA);
    }

    // -----------------------------------------------------------------------
    // Model listing
    // -----------------------------------------------------------------------

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        String url = BASE_URL + "/v1/models";
        Request request = addBearerAuth(new Request.Builder())
                .url(url)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("NVIDIA fetchModels failed: HTTP " + response.code()
                        + " " + readBodySafely(response));
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("NVIDIA fetchModels returned empty body");
            }

            JsonObject root = JsonParser.parseString(body.string()).getAsJsonObject();
            JsonArray dataArray = root.has("data") ? root.getAsJsonArray("data") : new JsonArray();

            List<ModelInfo> result = new ArrayList<>();
            for (JsonElement elem : dataArray) {
                JsonObject model = elem.getAsJsonObject();

                String id = getStringOrDefault(model, "id", "");
                // NVIDIA models typically don't have a separate display name
                String displayName = id;
                String description = getStringOrDefault(model, "object", "");

                result.add(new ModelInfo(id, displayName, AiProvider.NVIDIA, 0L, description));
            }

            return result;
        }
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
            String url = BASE_URL + "/v1/chat/completions";

            JsonObject requestBody = buildOpenAiRequestBody(messages, modelId, systemPrompt, tools);

            Request request = addBearerAuth(new Request.Builder())
                    .url(url)
                    .post(RequestBody.create(requestBody.toString(), JSON))
                    .header("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    handler.onError("NVIDIA request failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        String errorBody = readBodySafely(response);
                        handler.onError("NVIDIA HTTP " + response.code() + ": " + errorBody);
                        response.close();
                        return;
                    }

                    ResponseBody body = response.body();
                    if (body == null) {
                        handler.onError("NVIDIA returned empty response body");
                        return;
                    }

                    parseOpenAiSseStream(body, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("Failed to build NVIDIA request: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // OpenAI-format request body
    // -----------------------------------------------------------------------

    /**
     * Builds a standard OpenAI chat completion request body.
     * Shared logic that can be reused by OpenAI-compatible providers.
     */
    static JsonObject buildOpenAiRequestBody(List<ChatMessage> messages, String modelId,
                                             String systemPrompt, List<ToolDefinition> tools) {
        JsonObject body = new JsonObject();
        body.addProperty("model", modelId);
        body.addProperty("stream", true);

        JsonArray messagesArray = new JsonArray();

        // System prompt
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JsonObject systemMsg = new JsonObject();
            systemMsg.addProperty("role", "system");
            systemMsg.addProperty("content", systemPrompt);
            messagesArray.add(systemMsg);
        }

        // Conversation messages
        for (ChatMessage message : messages) {
            String role = message.getRole();
            // Skip system messages that are already handled by systemPrompt
            if ("system".equals(role)) {
                continue;
            }

            JsonObject msg = new JsonObject();
            msg.addProperty("role", role);

            if ("tool".equals(role)) {
                // Tool result message
                msg.addProperty("content", message.getContent() != null ? message.getContent() : "");
                if (message.getToolCallId() != null) {
                    msg.addProperty("tool_call_id", message.getToolCallId());
                }
            } else if ("assistant".equals(role) && message.getToolCalls() != null
                    && !message.getToolCalls().isEmpty()) {
                // Assistant message with tool calls
                if (message.getContent() != null) {
                    msg.addProperty("content", message.getContent());
                }
                JsonArray toolCallsArray = new JsonArray();
                for (ToolCall tc : message.getToolCalls()) {
                    JsonObject tcObj = new JsonObject();
                    tcObj.addProperty("id", tc.getId());
                    tcObj.addProperty("type", "function");

                    JsonObject function = new JsonObject();
                    function.addProperty("name", tc.getName());
                    function.addProperty("arguments", tc.getArguments() != null ? tc.getArguments() : "{}");
                    tcObj.add("function", function);

                    toolCallsArray.add(tcObj);
                }
                msg.add("tool_calls", toolCallsArray);
            } else {
                msg.addProperty("content", message.getContent() != null ? message.getContent() : "");
            }

            messagesArray.add(msg);
        }

        body.add("messages", messagesArray);

        // Tools
        if (tools != null && !tools.isEmpty()) {
            JsonArray toolsArray = new JsonArray();
            for (ToolDefinition tool : tools) {
                toolsArray.add(tool.toOpenAiJson());
            }
            body.add("tools", toolsArray);
            body.addProperty("tool_choice", "auto");
        }

        return body;
    }

    // -----------------------------------------------------------------------
    // OpenAI SSE stream parsing
    // -----------------------------------------------------------------------

    /**
     * Parses an OpenAI-format SSE stream (used by NVIDIA and OpenRouter).
     * Handles content deltas and tool call deltas.
     */
    static void parseOpenAiSseStream(ResponseBody body, StreamingResponseHandler handler) {
        StringBuilder fullResponse = new StringBuilder();
        // Track accumulated tool calls by index
        java.util.Map<Integer, ToolCallAccumulator> toolCallMap = new java.util.LinkedHashMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data: ")) {
                    continue;
                }

                String data = line.substring(6).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) {
                    continue;
                }

                try {
                    JsonObject event = JsonParser.parseString(data).getAsJsonObject();
                    processOpenAiEvent(event, fullResponse, toolCallMap, handler);
                } catch (Exception e) {
                    // Skip malformed JSON chunks gracefully
                }
            }

            // Emit any accumulated tool calls
            for (ToolCallAccumulator acc : toolCallMap.values()) {
                ToolCall toolCall = new ToolCall(acc.id, acc.name, acc.arguments.toString());
                handler.onToolCall(toolCall);
            }

            handler.onComplete(fullResponse.toString());
        } catch (IOException e) {
            handler.onError("Error reading stream: " + e.getMessage());
        }
    }

    private static void processOpenAiEvent(JsonObject event, StringBuilder fullResponse,
                                           java.util.Map<Integer, ToolCallAccumulator> toolCallMap,
                                           StreamingResponseHandler handler) {
        if (!event.has("choices")) {
            return;
        }

        JsonArray choices = event.getAsJsonArray("choices");
        if (choices.size() == 0) {
            return;
        }

        JsonObject choice = choices.get(0).getAsJsonObject();
        if (!choice.has("delta")) {
            return;
        }

        JsonObject delta = choice.getAsJsonObject("delta");

        // Text content delta
        if (delta.has("content") && !delta.get("content").isJsonNull()) {
            String content = delta.get("content").getAsString();
            fullResponse.append(content);
            handler.onChunk(content);
        }

        // Tool call deltas (streamed incrementally)
        if (delta.has("tool_calls") && delta.get("tool_calls").isJsonArray()) {
            JsonArray toolCalls = delta.getAsJsonArray("tool_calls");
            for (JsonElement tcElem : toolCalls) {
                JsonObject tc = tcElem.getAsJsonObject();
                int index = tc.has("index") ? tc.get("index").getAsInt() : 0;

                ToolCallAccumulator acc = toolCallMap.get(index);
                if (acc == null) {
                    acc = new ToolCallAccumulator();
                    toolCallMap.put(index, acc);
                }

                if (tc.has("id") && !tc.get("id").isJsonNull()) {
                    acc.id = tc.get("id").getAsString();
                }

                if (tc.has("function")) {
                    JsonObject function = tc.getAsJsonObject("function");
                    if (function.has("name") && !function.get("name").isJsonNull()) {
                        acc.name = function.get("name").getAsString();
                    }
                    if (function.has("arguments") && !function.get("arguments").isJsonNull()) {
                        acc.arguments.append(function.get("arguments").getAsString());
                    }
                }
            }
        }
    }

    /**
     * Accumulates streamed tool call fragments until the full call is ready.
     */
    static class ToolCallAccumulator {
        String id = "";
        String name = "";
        StringBuilder arguments = new StringBuilder();
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
