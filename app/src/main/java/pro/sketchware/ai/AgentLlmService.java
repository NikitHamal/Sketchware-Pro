package pro.sketchware.ai;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AgentLlmService {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build();
    private final Gson gson = new Gson();

    public static class ChatInputMessage {
        public String role;
        public String content;

        public ChatInputMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    public List<AgentModelInfo> fetchModels(@NonNull String provider, @NonNull String apiKey) throws IOException {
        return switch (provider) {
            case AgentProvider.GEMINI -> fetchGeminiModels(apiKey);
            case AgentProvider.NVIDIA -> fetchNvidiaModels(apiKey);
            case AgentProvider.OPENROUTER -> fetchOpenRouterModels(apiKey);
            default -> new ArrayList<>();
        };
    }

    public String chat(
            @NonNull String provider,
            @NonNull String apiKey,
            @NonNull String model,
            @NonNull String systemPrompt,
            @NonNull List<ChatInputMessage> messages
    ) throws IOException {
        return switch (provider) {
            case AgentProvider.GEMINI -> chatGemini(apiKey, model, systemPrompt, messages);
            case AgentProvider.NVIDIA -> chatOpenAICompatible("https://integrate.api.nvidia.com/v1/chat/completions", apiKey, model, systemPrompt, messages, false);
            case AgentProvider.OPENROUTER -> chatOpenAICompatible("https://openrouter.ai/api/v1/chat/completions", apiKey, model, systemPrompt, messages, true);
            default -> "Provider is not supported.";
        };
    }

    private List<AgentModelInfo> fetchGeminiModels(String apiKey) throws IOException {
        Request request = new Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("Gemini model list request failed: " + response.code() + " " + body);
            }

            JsonObject json = gson.fromJson(body, JsonObject.class);
            JsonArray models = json != null && json.has("models") ? json.getAsJsonArray("models") : new JsonArray();
            ArrayList<AgentModelInfo> parsed = new ArrayList<>();

            for (JsonElement element : models) {
                JsonObject item = element.getAsJsonObject();
                String modelName = item.has("name") ? item.get("name").getAsString() : "";
                boolean supportsGenerateContent = false;
                if (item.has("supportedGenerationMethods")) {
                    for (JsonElement method : item.getAsJsonArray("supportedGenerationMethods")) {
                        if ("generateContent".equals(method.getAsString())) {
                            supportsGenerateContent = true;
                            break;
                        }
                    }
                }
                if (supportsGenerateContent && !modelName.isEmpty()) {
                    String id = modelName.startsWith("models/") ? modelName.substring("models/".length()) : modelName;
                    parsed.add(new AgentModelInfo(id, id));
                }
            }
            return parsed;
        }
    }

    private List<AgentModelInfo> fetchNvidiaModels(String apiKey) throws IOException {
        return fetchOpenAICompatibleModels("https://integrate.api.nvidia.com/v1/models", apiKey);
    }

    private List<AgentModelInfo> fetchOpenRouterModels(String apiKey) throws IOException {
        return fetchOpenAICompatibleModels("https://openrouter.ai/api/v1/models", apiKey);
    }

    private List<AgentModelInfo> fetchOpenAICompatibleModels(String url, String apiKey) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("Model list request failed: " + response.code() + " " + body);
            }

            JsonObject json = gson.fromJson(body, JsonObject.class);
            JsonArray data = json != null && json.has("data") ? json.getAsJsonArray("data") : new JsonArray();
            ArrayList<AgentModelInfo> parsed = new ArrayList<>();

            for (JsonElement element : data) {
                JsonObject item = element.getAsJsonObject();
                String id = item.has("id") && !item.get("id").isJsonNull() ? item.get("id").getAsString() : "";
                if (!id.isEmpty()) {
                    parsed.add(new AgentModelInfo(id, id));
                }
            }
            return parsed;
        }
    }

    private String chatGemini(String apiKey, String model, String systemPrompt, List<ChatInputMessage> messages) throws IOException {
        JsonObject body = new JsonObject();

        JsonObject systemInstruction = new JsonObject();
        JsonArray systemParts = new JsonArray();
        JsonObject systemText = new JsonObject();
        systemText.addProperty("text", systemPrompt);
        systemParts.add(systemText);
        systemInstruction.add("parts", systemParts);
        body.add("systemInstruction", systemInstruction);

        JsonArray contents = new JsonArray();
        for (ChatInputMessage message : messages) {
            JsonObject content = new JsonObject();
            String role = AgentMessage.ROLE_ASSISTANT.equals(message.role) ? "model" : "user";
            content.addProperty("role", role);
            JsonArray parts = new JsonArray();
            JsonObject text = new JsonObject();
            text.addProperty("text", message.content);
            parts.add(text);
            content.add("parts", parts);
            contents.add(content);
        }
        body.add("contents", contents);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.2);
        body.add("generationConfig", generationConfig);

        String modelPath = model.startsWith("models/") ? model : "models/" + model;
        Request request = new Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/" + modelPath + ":generateContent?key=" + apiKey)
                .post(RequestBody.create(gson.toJson(body), JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("Gemini completion failed: " + response.code() + " " + responseBody);
            }

            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            if (json == null || !json.has("candidates")) {
                return "I could not generate a response.";
            }

            JsonArray candidates = json.getAsJsonArray("candidates");
            if (candidates.size() == 0) {
                return "I could not generate a response.";
            }

            JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
            if (content == null || !content.has("parts")) {
                return "I could not generate a response.";
            }

            StringBuilder output = new StringBuilder();
            for (JsonElement element : content.getAsJsonArray("parts")) {
                JsonObject part = element.getAsJsonObject();
                if (part.has("text")) {
                    output.append(part.get("text").getAsString());
                }
            }
            return output.toString().trim();
        }
    }

    private String chatOpenAICompatible(
            String url,
            String apiKey,
            String model,
            String systemPrompt,
            List<ChatInputMessage> messages,
            boolean openRouter
    ) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("temperature", 0.2);

        JsonArray payloadMessages = new JsonArray();

        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", systemPrompt);
        payloadMessages.add(system);

        for (ChatInputMessage message : messages) {
            JsonObject entry = new JsonObject();
            String role = AgentMessage.ROLE_ASSISTANT.equals(message.role) ? "assistant" : "user";
            entry.addProperty("role", role);
            entry.addProperty("content", message.content);
            payloadMessages.add(entry);
        }

        body.add("messages", payloadMessages);

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(gson.toJson(body), JSON));

        if (openRouter) {
            requestBuilder.header("HTTP-Referer", "https://github.com/Sketchware-Pro/sketchware-pro")
                    .header("X-Title", "Sketchware Pro Agent");
        }

        try (Response response = client.newCall(requestBuilder.build()).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("Chat completion failed: " + response.code() + " " + responseBody);
            }

            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            JsonArray choices = json != null && json.has("choices") ? json.getAsJsonArray("choices") : new JsonArray();
            if (choices.size() == 0) {
                return "I could not generate a response.";
            }

            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (message == null || !message.has("content")) {
                return "I could not generate a response.";
            }
            JsonElement content = message.get("content");
            if (content == null || content.isJsonNull()) {
                return "I could not generate a response.";
            }
            if (content.isJsonPrimitive()) {
                return content.getAsString();
            }
            if (content.isJsonArray()) {
                StringBuilder output = new StringBuilder();
                for (JsonElement part : content.getAsJsonArray()) {
                    if (!part.isJsonObject()) {
                        continue;
                    }
                    JsonObject object = part.getAsJsonObject();
                    if (object.has("text") && !object.get("text").isJsonNull()) {
                        output.append(object.get("text").getAsString());
                    }
                }
                String text = output.toString().trim();
                return text.isEmpty() ? "I could not generate a response." : text;
            }
            return "I could not generate a response.";
        }
    }
}
