package pro.sketchware.library;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class LibraryProjectLinker {
    private final List<File> artifacts = new ArrayList<>();

    public void add(LocalLibraryMetadata metadata) { if (metadata != null && metadata.isValid()) artifacts.add(metadata.artifact); }
    public List<File> classpath() { return new ArrayList<>(artifacts); }
    public String toGradleFileTreeLine(String dirName) { return "implementation fileTree(dir: '" + dirName + "', include: ['*.jar', '*.aar'])"; }
}
