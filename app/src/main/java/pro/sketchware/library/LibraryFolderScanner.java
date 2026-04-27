package pro.sketchware.library;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.utility.io.SafeFileOps;

public final class LibraryFolderScanner {
    private LibraryFolderScanner() {}

    public static List<LocalLibraryMetadata> scan(File root) throws Exception {
        List<LocalLibraryMetadata> out = new ArrayList<>();
        if (root == null || !root.exists()) return out;
        for (File file : SafeFileOps.listFilesRecursively(root)) {
            String n = file.getName().toLowerCase();
            if (!n.endsWith(".jar") && !n.endsWith(".aar")) continue;
            LocalLibraryMetadata m = new LocalLibraryMetadata();
            m.artifact = file;
            m.root = root;
            m.name = file.getName();
            m.id = file.getName().replaceAll("\\.(jar|aar)$", "");
            out.add(m);
        }
        return out;
    }
}
