package pro.sketchware.agent.provider;

import java.util.List;
import java.util.function.Consumer;

import pro.sketchware.agent.data.AgentDatabase;

public interface AIProvider {

    String getId();

    String getDisplayName();

    void fetchModels(String apiKey, Consumer<List<AgentDatabase.ModelInfo>> callback);

    void sendMessage(
            String apiKey,
            String model,
            List<MessagePayload> messages,
            List<ToolDefinition> tools,
            StreamCallback callback
    );

    void cancelRequest();

    class MessagePayload {
        public String role;
        public String content;
        public String toolCallId;
        public String toolCalls;

        public MessagePayload(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public MessagePayload(String role, String content, String toolCallId) {
            this.role = role;
            this.content = content;
            this.toolCallId = toolCallId;
        }
    }

    class ToolDefinition {
        public String name;
        public String description;
        public String parametersJson;

        public ToolDefinition(String name, String description, String parametersJson) {
            this.name = name;
            this.description = description;
            this.parametersJson = parametersJson;
        }
    }

    class ToolCall {
        public String id;
        public String name;
        public String arguments;

        public ToolCall(String id, String name, String arguments) {
            this.id = id;
            this.name = name;
            this.arguments = arguments;
        }
    }

    interface StreamCallback {
        void onToken(String token);
        void onToolCall(List<ToolCall> toolCalls);
        void onComplete(String fullResponse);
        void onError(String error);
    }
}
