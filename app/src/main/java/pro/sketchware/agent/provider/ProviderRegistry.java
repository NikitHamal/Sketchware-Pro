package pro.sketchware.agent.provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProviderRegistry {

    private static final Map<String, AIProvider> providers = new LinkedHashMap<>();

    static {
        register(new GeminiProvider());
        register(new NvidiaProvider());
        register(new OpenRouterProvider());
    }

    private static void register(AIProvider provider) {
        providers.put(provider.getId(), provider);
    }

    public static AIProvider getProvider(String id) {
        return providers.get(id);
    }

    public static List<AIProvider> getAllProviders() {
        return new ArrayList<>(providers.values());
    }

    public static List<String> getProviderIds() {
        return new ArrayList<>(providers.keySet());
    }
}
