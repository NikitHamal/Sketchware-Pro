package pro.sketchware.ai;

import mod.hilal.saif.activities.tools.ConfigActivity;

public final class AgentSettings {
    private AgentSettings() {
    }

    public static String getApiKey(String provider) {
        return switch (provider) {
            case AgentProvider.GEMINI -> ConfigActivity.getStringSettingValueOrSetAndGet(ConfigActivity.SETTING_AI_PROVIDER_GEMINI_API_KEY, "");
            case AgentProvider.NVIDIA -> ConfigActivity.getStringSettingValueOrSetAndGet(ConfigActivity.SETTING_AI_PROVIDER_NVIDIA_API_KEY, "");
            case AgentProvider.OPENROUTER -> ConfigActivity.getStringSettingValueOrSetAndGet(ConfigActivity.SETTING_AI_PROVIDER_OPENROUTER_API_KEY, "");
            default -> "";
        };
    }
}
