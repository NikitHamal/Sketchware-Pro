package pro.sketchware.settings;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public final class PermissionSettingsManager {
    private final ProjectSettingsStore store;
    public PermissionSettingsManager(ProjectSettingsStore store) { this.store = store; }
    public void setPermissions(Set<String> permissions) { store.putString("permissions", String.join("\n", permissions)); }
    public Set<String> getPermissions() { String raw = store.getString("permissions", ""); return new LinkedHashSet<>(Arrays.asList(raw.split("\\n"))); }
}
