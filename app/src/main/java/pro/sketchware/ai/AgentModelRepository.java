package pro.sketchware.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AgentModelRepository {
    private final AgentStorage storage = AgentStorage.getInstance();
    private final AgentLlmService llmService = new AgentLlmService();

    public interface ModelsCallback {
        void onComplete(ArrayList<AgentModelInfo> models, Exception error);
    }

    public ArrayList<AgentModelInfo> getCachedModels(String provider) {
        return storage.getCachedModels(provider);
    }

    public ArrayList<AgentModelInfo> ensureModels(String provider, String apiKey) throws Exception {
        ArrayList<AgentModelInfo> cached = storage.getCachedModels(provider);
        if (!cached.isEmpty()) {
            return cached;
        }
        return refreshModels(provider, apiKey);
    }

    public ArrayList<AgentModelInfo> refreshModels(String provider, String apiKey) throws Exception {
        List<AgentModelInfo> models = llmService.fetchModels(provider, apiKey);
        ArrayList<AgentModelInfo> sorted = new ArrayList<>(models);
        sorted.sort(Comparator.comparing(model -> model.id));
        storage.updateCachedModels(provider, sorted);
        return sorted;
    }
}
