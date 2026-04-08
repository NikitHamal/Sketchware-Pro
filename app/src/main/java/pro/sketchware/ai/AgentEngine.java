package pro.sketchware.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AgentEngine {
    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile("<tool_call>(.*?)</tool_call>", Pattern.DOTALL);
    private final Gson gson = new Gson();
    private final AgentLlmService llmService = new AgentLlmService();

    public ArrayList<AgentMessage> runTurn(
            AgentConversation conversation,
            AgentWorkspace workspace,
            ArrayList<AgentMessage> historyMessages,
            AgentToolExecutor toolExecutor
    ) {
        ArrayList<AgentMessage> generatedMessages = new ArrayList<>();
        String provider = conversation.provider == null ? AgentProvider.GEMINI : conversation.provider;
        String apiKey = AgentSettings.getApiKey(provider);

        if (apiKey.trim().isEmpty()) {
            generatedMessages.add(AgentMessage.create(
                    conversation.id,
                    AgentMessage.ROLE_ASSISTANT,
                    "No API key is configured for " + AgentProvider.getDisplayName(provider) + ". Set it in App Settings."
            ));
            return generatedMessages;
        }

        String model = conversation.model == null ? "" : conversation.model;
        if (model.isEmpty()) {
            generatedMessages.add(AgentMessage.create(
                    conversation.id,
                    AgentMessage.ROLE_ASSISTANT,
                    "No model is selected. Choose a model first."
            ));
            return generatedMessages;
        }

        ArrayList<AgentMessage> workingHistory = new ArrayList<>(historyMessages);
        String systemPrompt = buildSystemPrompt(workspace, toolExecutor.describeTools());

        for (int iteration = 0; iteration < 6; iteration++) {
            String llmOutput;
            try {
                llmOutput = llmService.chat(
                        provider,
                        apiKey,
                        model,
                        systemPrompt,
                        toLlmMessages(workingHistory)
                );
            } catch (Exception e) {
                generatedMessages.add(AgentMessage.create(
                        conversation.id,
                        AgentMessage.ROLE_ASSISTANT,
                        "Agent request failed: " + e.getMessage()
                ));
                return generatedMessages;
            }

            List<ToolCall> toolCalls = extractToolCalls(llmOutput);
            String cleanedAssistantText = stripToolCalls(llmOutput).trim();

            if (!cleanedAssistantText.isEmpty()) {
                AgentMessage assistant = AgentMessage.create(
                        conversation.id,
                        AgentMessage.ROLE_ASSISTANT,
                        cleanedAssistantText
                );
                generatedMessages.add(assistant);
                workingHistory.add(assistant);
            }

            if (toolCalls.isEmpty()) {
                if (cleanedAssistantText.isEmpty()) {
                    AgentMessage assistant = AgentMessage.create(
                            conversation.id,
                            AgentMessage.ROLE_ASSISTANT,
                            "I could not produce a valid response."
                    );
                    generatedMessages.add(assistant);
                }
                return generatedMessages;
            }

            for (ToolCall toolCall : toolCalls) {
                JsonObject result = toolExecutor.execute(toolCall.name, toolCall.arguments);
                AgentMessage toolMessage = AgentMessage.create(
                        conversation.id,
                        AgentMessage.ROLE_TOOL,
                        "[Tool Result: " + toolCall.name + "]\n" + gson.toJson(result)
                );
                generatedMessages.add(toolMessage);
                workingHistory.add(toolMessage);
            }
        }

        generatedMessages.add(AgentMessage.create(
                conversation.id,
                AgentMessage.ROLE_ASSISTANT,
                "I stopped after multiple tool iterations. Please refine the request."
        ));
        return generatedMessages;
    }

    private String buildSystemPrompt(AgentWorkspace workspace, JsonArray tools) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are Sketchware Pro's autonomous coding agent.\n");
        builder.append("Workspace scope:\n");
        builder.append("- Workspace ID: ").append(workspace.id).append('\n');
        builder.append("- Workspace name: ").append(workspace.name).append('\n');
        builder.append("- Allowed project IDs: ").append(workspace.projectIds).append('\n');
        builder.append("You can only operate on allowed projects and those created during this conversation.\n");
        builder.append("When you need to use a tool, return one or more tool calls exactly in this format:\n");
        builder.append("<tool_call>{\"tool\":\"tool_name\",\"arguments\":{...}}</tool_call>\n");
        builder.append("Do not wrap tool calls in markdown code blocks.\n");
        builder.append("You may include multiple tool calls.\n");
        builder.append("After receiving tool results, continue automatically until the user request is complete.\n");
        builder.append("Tool catalog:\n");
        builder.append(gson.toJson(tools));
        return builder.toString();
    }

    private List<AgentLlmService.ChatInputMessage> toLlmMessages(ArrayList<AgentMessage> messages) {
        ArrayList<AgentLlmService.ChatInputMessage> mapped = new ArrayList<>();
        int start = Math.max(0, messages.size() - 24);
        for (int i = start; i < messages.size(); i++) {
            AgentMessage message = messages.get(i);
            if (AgentMessage.ROLE_TOOL.equals(message.role)) {
                mapped.add(new AgentLlmService.ChatInputMessage(
                        AgentMessage.ROLE_USER,
                        "Tool result:\n" + message.content
                ));
            } else {
                mapped.add(new AgentLlmService.ChatInputMessage(message.role, message.content));
            }
        }
        return mapped;
    }

    private List<ToolCall> extractToolCalls(String text) {
        ArrayList<ToolCall> calls = new ArrayList<>();
        Matcher matcher = TOOL_CALL_PATTERN.matcher(text);
        while (matcher.find()) {
            String payload = normalizeToolPayload(matcher.group(1));
            try {
                JsonObject object = JsonParser.parseString(payload).getAsJsonObject();
                String toolName = object.has("tool") ? object.get("tool").getAsString() : "";
                JsonObject arguments = new JsonObject();
                if (object.has("arguments") && object.get("arguments").isJsonObject()) {
                    arguments = object.getAsJsonObject("arguments");
                }
                if (!toolName.isEmpty()) {
                    calls.add(new ToolCall(toolName, arguments));
                }
            } catch (Exception ignored) {
            }
        }
        return calls;
    }

    private String normalizeToolPayload(String payload) {
        if (payload == null) {
            return "";
        }
        String normalized = payload.trim();
        if (!normalized.startsWith("```")) {
            return normalized;
        }

        normalized = normalized.replaceFirst("^```(?:json)?\\s*", "");
        normalized = normalized.replaceFirst("\\s*```$", "");
        return normalized.trim();
    }

    private String stripToolCalls(String text) {
        return TOOL_CALL_PATTERN.matcher(text).replaceAll("").trim();
    }

    private static class ToolCall {
        final String name;
        final JsonObject arguments;

        ToolCall(String name, JsonObject arguments) {
            this.name = name;
            this.arguments = arguments;
        }
    }
}
