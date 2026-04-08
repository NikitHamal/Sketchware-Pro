package pro.sketchware.ai;

import java.util.Arrays;
import java.util.List;

public final class AgentProvider {
    public static final String GEMINI = "gemini";
    public static final String NVIDIA = "nvidia";
    public static final String OPENROUTER = "openrouter";

    private AgentProvider() {
    }

    public static List<String> all() {
        return Arrays.asList(GEMINI, NVIDIA, OPENROUTER);
    }

    public static String getDisplayName(String provider) {
        if (provider == null) {
            return "Provider";
        }
        return switch (provider) {
            case GEMINI -> "Gemini";
            case NVIDIA -> "Nvidia";
            case OPENROUTER -> "OpenRouter";
            default -> provider;
        };
    }
}
