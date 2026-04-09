package pro.sketchware.ai.models;

public enum AiProvider {
    GEMINI(
            "Gemini",
            "https://generativelanguage.googleapis.com",
            "/v1beta/models",
            "/v1beta/chat/completions"
    ),
    NVIDIA(
            "NVIDIA",
            "https://integrate.api.nvidia.com",
            "/v1/models",
            "/v1/chat/completions"
    ),
    OPENROUTER(
            "OpenRouter",
            "https://openrouter.ai",
            "/api/v1/models",
            "/api/v1/chat/completions"
    );

    private final String displayName;
    private final String baseUrl;
    private final String modelsEndpoint;
    private final String chatEndpoint;

    AiProvider(String displayName, String baseUrl, String modelsEndpoint, String chatEndpoint) {
        this.displayName = displayName;
        this.baseUrl = baseUrl;
        this.modelsEndpoint = modelsEndpoint;
        this.chatEndpoint = chatEndpoint;
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
