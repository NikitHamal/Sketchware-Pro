package pro.sketchware.agent;

import static pro.sketchware.utility.GsonUtils.getGson;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AgentModelService {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient httpClient = new OkHttpClient.Builder().build();
    private final AgentRepository repository;

    public AgentModelService(@NonNull AgentRepository repository) {
        this.repository = repository;
    }

    @NonNull
    public ArrayList<AgentRepository.ModelInfo> refreshModels(@NonNull AgentProvider provider, @NonNull String apiKey)
            throws IOException {
        if (TextUtils.isEmpty(apiKey)) {
            throw new IOException("Add an API key for " + provider.displayName + " first.");
        }
        ArrayList<AgentRepository.ModelInfo> models = switch (provider) {
            case GEMINI -> fetchGeminiModels(apiKey);
            case NVIDIA -> fetchNvidiaModels(apiKey);
            case OPENROUTER -> fetchOpenRouterModels(apiKey);
        };
        models.removeIf(model -> TextUtils.isEmpty(model.id) || !model.supportsTools);
        models.sort(Comparator.comparing(AgentRepository.ModelInfo::getDisplayName, String.CASE_INSENSITIVE_ORDER));

        AgentRepository.ProviderState state = repository.getProviderState(provider);
        state.apiKey = apiKey;
        state.cachedModels = models;
        state.modelsUpdatedAt = System.currentTimeMillis();
        repository.saveProviderState(provider, state);
        return models;
    }

    @NonNull
    public ArrayList<AgentRepository.ModelInfo> getCachedModels(@NonNull AgentProvider provider) {
        return repository.getProviderState(provider).cachedModels;
    }

    @NonNull
    private ArrayList<AgentRepository.ModelInfo> fetchGeminiModels(@NonNull String apiKey) throws IOException {
        Request request = new Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?key="
                        + URLEncoder.encode(apiKey, StandardCharsets.UTF_8))
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = bodyOrThrow(response);
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            JsonArray models = json.getAsJsonArray("models");
            ArrayList<AgentRepository.ModelInfo> result = new ArrayList<>();
            if (models == null) {
                return result;
            }

            for (JsonElement element : models) {
                JsonObject item = element.getAsJsonObject();
                JsonArray methods = item.getAsJsonArray("supportedGenerationMethods");
                if (methods == null || !contains(methods, "generateContent")) {
                    continue;
                }

                String name = stringOrNull(item, "name");
                if (TextUtils.isEmpty(name)) {
                    continue;
                }

                AgentRepository.ModelInfo model = new AgentRepository.ModelInfo();
                model.id = name.startsWith("models/") ? name.substring("models/".length()) : name;
                model.label = stringOrNull(item, "displayName");
                model.description = stringOrNull(item, "description");
                model.supportsTools = true;
                result.add(model);
            }
            return result;
        }
    }

    @NonNull
    private ArrayList<AgentRepository.ModelInfo> fetchOpenRouterModels(@NonNull String apiKey) throws IOException {
        Request request = new Request.Builder()
                .url("https://openrouter.ai/api/v1/models")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = bodyOrThrow(response);
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            JsonArray data = json.getAsJsonArray("data");
            ArrayList<AgentRepository.ModelInfo> result = new ArrayList<>();
            if (data == null) {
                return result;
            }

            for (JsonElement element : data) {
                JsonObject item = element.getAsJsonObject();
                AgentRepository.ModelInfo model = new AgentRepository.ModelInfo();
                model.id = stringOrNull(item, "id");
                model.label = stringOrNull(item, "name");
                model.description = stringOrNull(item, "description");
                JsonArray supportedParameters = item.getAsJsonArray("supported_parameters");
                model.supportsTools = supportedParameters == null
                        || contains(supportedParameters, "tools")
                        || contains(supportedParameters, "tool_choice");
                if (!TextUtils.isEmpty(model.id)) {
                    result.add(model);
                }
            }
            return result;
        }
    }

    @NonNull
    private ArrayList<AgentRepository.ModelInfo> fetchNvidiaModels(@NonNull String apiKey) throws IOException {
        Request request = new Request.Builder()
                .url("https://integrate.api.nvidia.com/v1/models")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = bodyOrThrow(response);
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            JsonArray data = json.getAsJsonArray("data");
            ArrayList<AgentRepository.ModelInfo> result = new ArrayList<>();
            if (data == null) {
                return result;
            }

            for (JsonElement element : data) {
                JsonObject item = element.getAsJsonObject();
                AgentRepository.ModelInfo model = new AgentRepository.ModelInfo();
                model.id = stringOrNull(item, "id");
                model.label = stringOrNull(item, "id");
                model.description = stringOrNull(item, "owned_by");
                model.supportsTools = true;
                if (!TextUtils.isEmpty(model.id)) {
                    result.add(model);
                }
            }
            return result;
        }
    }

    @NonNull
    private String bodyOrThrow(@NonNull Response response) throws IOException {
        String body = response.body() != null ? response.body().string() : "";
        if (!response.isSuccessful()) {
            throw new IOException("HTTP " + response.code() + ": " + body);
        }
        return body;
    }

    private boolean contains(@NonNull JsonArray array, @NonNull String value) {
        for (JsonElement element : array) {
            if (value.equalsIgnoreCase(element.getAsString())) {
                return true;
            }
        }
        return false;
    }

    private String stringOrNull(@NonNull JsonObject object, @NonNull String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }
}
