package pro.sketchware.ai.models;

public enum AiProvider {

    GEMINI("Gemini",
            "https://generativelanguage.googleapis.com",
            "/v1beta/models", "/v1beta/chat/completions", true, false,
            "Google Gemini — powerful multimodal AI. Best for complex Sketchware projects with large context windows."),

    OPENAI("OpenAI",
            "https://api.openai.com",
            "/v1/models", "/v1/chat/completions", true, false,
            "OpenAI GPT-4o & o-series — industry-leading code generation. Best-in-class for Java/Android development."),

    ANTHROPIC("Anthropic Claude",
            "https://api.anthropic.com",
            "/v1/models", "/v1/messages", true, false,
            "Anthropic Claude — excellent at following complex instructions. Claude 3.5 Sonnet excels at multi-step Android coding tasks."),

    DEEPSEEK("DeepSeek",
            "https://api.deepseek.com",
            "/v1/models", "/v1/chat/completions", true, false,
            "DeepSeek — Chinese AI with outstanding code performance. DeepSeek-V3 rivals GPT-4 at a fraction of the cost."),

    XAI_GROK("xAI Grok",
            "https://api.x.ai",
            "/v1/models", "/v1/chat/completions", true, false,
            "xAI Grok — Elon Musk's AI with real-time knowledge. Grok-3 is excellent for code and reasoning tasks."),

    GROQ("Groq",
            "https://api.groq.com",
            "/openai/v1/models", "/openai/v1/chat/completions", true, true,
            "Groq — blazing fast inference with UNLIMITED rate limits on LPU hardware. Best for rapid Sketchware project generation."),

    NVIDIA("NVIDIA",
            "https://integrate.api.nvidia.com",
            "/v1/models", "/v1/chat/completions", true, false,
            "NVIDIA NIM — enterprise-grade AI models optimized for code. Supports Llama, Mistral, and more."),

    OPENROUTER("OpenRouter",
            "https://openrouter.ai",
            "/api/v1/models", "/api/v1/chat/completions", true, false,
            "OpenRouter — unified API gateway to 100+ AI models including GPT-4, Claude, and Gemini."),

    DEEPINFRA("DeepInfra",
            "https://api.deepinfra.com",
            "/models/featured", "/v1/openai/chat/completions", false, false,
            "DeepInfra — free inference for open-source models. Note: may be rate-limited (403) during peak hours."),

    PAXSENIX("Paxsenix",
            "https://api.paxsenix.biz.id",
            "/v1/models", "/v1/chat/completions", true, false,
            "Paxsenix — alternative AI gateway. Good fallback when other providers are unavailable."),

    AIRFORCE("AirForce",
            "https://api.airforce",
            "/v1/models", "/v1/chat/completions", false, false,
            "AirForce — completely free AI proxy. No API key required. Provides access to GPT-4o, Claude, and Gemini."),

    MANUS("Manus AI",
            "https://api.manus.im",
            "/v1/models", "/v1/tasks", true, false,
            "Manus AI — an autonomous agent API (async task-based, not OpenAI-compatible). Requires active Manus subscription at manus.im. NOTE: Manus uses /v1/tasks for submissions, not standard chat completions."),

    TOGETHER("Together AI",
            "https://api.together.xyz",
            "/v1/models", "/v1/chat/completions", true, false,
            "Together AI — open-source models including Llama 3.3, Gemma 3, DeepSeek R1, and Qwen 2.5. Free tier available at together.ai."),

    HUGGINGFACE("HuggingFace",
            "https://api-inference.huggingface.co",
            "/v1/models", "/v1/chat/completions", true, false,
            "HuggingFace — free inference for Gemma, Llama, Mistral, and Qwen models. Free API key at huggingface.co."),

    CEREBRAS("Cerebras",
            "https://api.cerebras.ai",
            "/v1/models", "/v1/chat/completions", true, false,
            "Cerebras — ultra-fast inference on custom hardware. Free tier with Llama 3.3 70B and Llama 3.1 8B. API key at cloud.cerebras.ai."),

    GOOGLE_AI_STUDIO("Google AI Studio",
            "https://generativelanguage.googleapis.com",
            "/v1beta/openai/models", "/v1beta/openai/chat/completions", true, false,
            "Google AI Studio — free access to Gemma 3 models (1B, 4B, 12B, 27B) and Gemini Flash. Free API key at aistudio.google.com."),

    LOCAL_LLM("Local LLM",
            "http://localhost:1234",
            "/v1/models", "/v1/chat/completions", false, true,
            "Local LLM — run models locally on your device or server (LM Studio, Ollama, etc.). Complete privacy, no internet needed.");

    private final String displayName;
    private final String baseUrl;
    private final String modelsEndpoint;
    private final String chatEndpoint;
    private final boolean apiKeyRequired;
    private final boolean unlimited;
    private final String description;

    AiProvider(String displayName, String baseUrl, String modelsEndpoint,
               String chatEndpoint, boolean apiKeyRequired, boolean unlimited, String description) {
        this.displayName    = displayName;
        this.baseUrl        = baseUrl;
        this.modelsEndpoint = modelsEndpoint;
        this.chatEndpoint   = chatEndpoint;
        this.apiKeyRequired = apiKeyRequired;
        this.unlimited      = unlimited;
        this.description    = description;
    }

    public String getDisplayName()    { return displayName; }
    public String getBaseUrl()        { return baseUrl; }
    public String getModelsEndpoint() { return modelsEndpoint; }
    public String getChatEndpoint()   { return chatEndpoint; }
    public boolean requiresApiKey()   { return apiKeyRequired; }
    public boolean isUnlimited()      { return unlimited; }
    public String getDescription()    { return description; }

    public String getSelectorLabel() {
        if (unlimited)       return displayName + " ∞";
        if (!apiKeyRequired) return displayName + " 🆓";
        return displayName;
    }

    public static AiProvider fromName(String name) {
        if (name == null) return null;
        for (AiProvider p : values()) {
            if (p.name().equalsIgnoreCase(name) || p.displayName.equalsIgnoreCase(name))
                return p;
        }
        return null;
    }
}
