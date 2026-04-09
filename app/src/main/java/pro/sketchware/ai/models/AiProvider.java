package pro.sketchware.ai.models;

public enum AiProvider {
    GEMINI(
            "Gemini",
            "https://generativelanguage.googleapis.com",
            "/v1beta/models",
            "/v1beta/chat/completions",
            true
    ),
    NVIDIA(
            "NVIDIA",
            "https://integrate.api.nvidia.com",
            "/v1/models",
            "/v1/chat/completions",
            true
    ),
    OPENROUTER(
            "OpenRouter",
            "https://openrouter.ai",
            "/api/v1/models",
            "/api/v1/chat/completions",
            true
    ),
    DEEPINFRA(
            "DeepInfra",
            "https://api.deepinfra.com",
            "/models/featured",
            "/v1/openai/chat/completions",
            false
    ),
    PAXSENIX(
            "Paxsenix",
            "https://api.paxsenix.org",
            "/v1/models",
            "/v1/chat/completions",
            true
    );

    private final String displayName;
    private final String baseUrl;
    private final String modelsEndpoint;
    private final String chatEndpoint;
    private final boolean apiKeyRequired;

    AiProvider(String displayName, String baseUrl, String modelsEndpoint, String chatEndpoint,
               boolean apiKeyRequired) {
        this.displayName = displayName;
        this.baseUrl = baseUrl;
        this.modelsEndpoint = modelsEndpoint;
        this.chatEndpoint = chatEndpoint;
        this.apiKeyRequired = apiKeyRequired;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModelsEndpoint() {
        return modelsEndpoint;
    }

    public String getChatEndpoint() {
        return chatEndpoint;
    }

    public boolean requiresApiKey() {
        return apiKeyRequired;
    }

    public static AiProvider fromName(String name) {
        if (name == null) {
            return null;
        }
        for (AiProvider provider : values()) {
            if (provider.name().equalsIgnoreCase(name)
                    || provider.displayName.equalsIgnoreCase(name)) {
                return provider;
            }
        }
        return null;
    }
}
