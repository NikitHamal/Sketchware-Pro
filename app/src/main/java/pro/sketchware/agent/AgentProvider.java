package pro.sketchware.agent;

import androidx.annotation.NonNull;

public enum AgentProvider {
    GEMINI("gemini", "Gemini"),
    NVIDIA("nvidia", "NVIDIA"),
    OPENROUTER("openrouter", "OpenRouter");

    public final String id;
    public final String displayName;

    AgentProvider(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    @NonNull
    public static AgentProvider fromId(String id) {
        for (AgentProvider provider : values()) {
            if (provider.id.equals(id)) {
                return provider;
            }
        }
        return GEMINI;
    }
}
