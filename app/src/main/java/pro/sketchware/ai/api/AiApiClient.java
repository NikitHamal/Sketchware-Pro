package pro.sketchware.ai.api;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;

import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.ModelInfo;

/**
 * Abstract base class for AI API clients. Each provider (Gemini, NVIDIA, OpenRouter)
 * extends this class and implements provider-specific request/response handling.
 *
 * <p>Authentication is handled differently per provider:
 * <ul>
 *   <li>Gemini: API key passed as a {@code ?key=} query parameter</li>
 *   <li>NVIDIA: API key passed in the {@code Authorization: Bearer} header</li>
 *   <li>OpenRouter: API key passed in the {@code Authorization: Bearer} header,
 *       plus additional {@code HTTP-Referer} and {@code X-Title} headers</li>
 * </ul>
 */
public abstract class AiApiClient {

    protected final OkHttpClient client;
    protected final String apiKey;
    protected final AiProvider provider;

    private static final long TIMEOUT_SECONDS = 60;

    /**
     * Constructs a new API client.
     *
     * @param apiKey   the API key for authentication
     * @param provider the AI provider this client connects to
     */
    protected AiApiClient(String apiKey, AiProvider provider) {
        this.apiKey = apiKey;
        this.provider = provider;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Fetches the list of available models from the provider.
     *
     * @return a list of available models
     * @throws IOException if a network or parsing error occurs
     */
    public abstract List<ModelInfo> fetchModels() throws IOException;

    /**
     * Sends a streaming chat request to the provider.
     *
     * @param messages     the conversation history
     * @param modelId      the model identifier to use
     * @param systemPrompt the system instruction, or null if none
     * @param handler      callback for streaming events
     */
    public abstract void sendChatRequest(List<ChatMessage> messages, String modelId,
                                         String systemPrompt, StreamingResponseHandler handler);

    /**
     * Sends a streaming chat request that includes tool definitions.
     *
     * @param messages     the conversation history
     * @param modelId      the model identifier to use
     * @param systemPrompt the system instruction, or null if none
     * @param tools        the list of tool definitions to include
     * @param handler      callback for streaming events
     */
    public abstract void sendChatRequest(List<ChatMessage> messages, String modelId,
                                         String systemPrompt, List<ToolDefinition> tools,
                                         StreamingResponseHandler handler);

    /**
     * Builds common request headers for bearer-token authentication.
     * Used by NVIDIA and OpenRouter providers.
     *
     * @param builder the request builder to add headers to
     * @return the same builder with authentication headers added
     */
    protected Request.Builder addBearerAuth(Request.Builder builder) {
        return builder.header("Authorization", "Bearer " + apiKey);
    }

    /**
     * Returns the full URL for the models endpoint.
     */
    protected String getModelsUrl() {
        return provider.getBaseUrl() + provider.getModelsEndpoint();
    }

    /**
     * Returns the full URL for the chat completions endpoint.
     */
    protected String getChatUrl() {
        return provider.getBaseUrl() + provider.getChatEndpoint();
    }

    /**
     * Shuts down the HTTP client, releasing any held resources.
     */
    public void shutdown() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
