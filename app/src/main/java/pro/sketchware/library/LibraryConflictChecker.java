package pro.sketchware.library;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LibraryConflictChecker {
    public List<String> findNameConflicts(List<LocalLibraryMetadata> libraries) {
        Map<String, Integer> counts = new HashMap<>();
        for (LocalLibraryMetadata lib : libraries) {
            String key = lib == null || lib.id == null ? "" : lib.id.toLowerCase(Locale.US);
            counts.put(key, counts.getOrDefault(key, 0) + 1);
        }
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) if (!e.getKey().isEmpty() && e.getValue() > 1) out.add(e.getKey());
        return out;
    }
}
