package pro.sketchware.compiler;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

public class IncrementalCompileCache {

    private static final String TAG = "IncrementalCompileCache";
    private static final String CACHE_FILENAME = ".incremental_cache";

    private final File cacheFile;
    private Map<String, Long> cache;

    public IncrementalCompileCache(String projectId) {
        File cacheDir = new File(Environment.getExternalStorageDirectory(), ".sketchware/data/" + projectId);
        cacheFile = new File(cacheDir, CACHE_FILENAME);
        cache = load();
    }

    public boolean hasChanges(String... directoriesOrFiles) {
        Map<String, Long> current = snapshot(directoriesOrFiles);
        return !current.equals(cache);
    }

    public void save(String... directoriesOrFiles) {
        cache = snapshot(directoriesOrFiles);
        try {
            File parent = cacheFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(cacheFile))) {
                output.writeObject(cache);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to save incremental cache", e);
        }
    }

    private Map<String, Long> load() {
        if (!cacheFile.exists()) {
            return new HashMap<>();
        }
        try (ObjectInputStream input = new ObjectInputStream(new FileInputStream(cacheFile))) {
            Object value = input.readObject();
            if (value instanceof Map) {
                return (Map<String, Long>) value;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load incremental cache", e);
        }
        return new HashMap<>();
    }

    private Map<String, Long> snapshot(String... directoriesOrFiles) {
        Map<String, Long> current = new HashMap<>();
        if (directoriesOrFiles == null) {
            return current;
        }
        for (String path : directoriesOrFiles) {
            if (path == null || path.isEmpty()) {
                continue;
            }
            collect(new File(path), current);
        }
        return current;
    }

    private void collect(File file, Map<String, Long> current) {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    collect(child, current);
                }
            }
        } else if (file.getName().endsWith(".java") || file.getName().endsWith(".kt")) {
            current.put(file.getAbsolutePath(), file.lastModified());
        }
    }
}
