package pro.sketchware.library;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class LibraryHealthChecker {
    public static final class Issue {
        public final String severity; public final String message; public final File file;
        Issue(String severity, String message, File file) { this.severity = severity; this.message = message; this.file = file; }
    }

    public List<Issue> check(List<LocalLibraryMetadata> libraries) {
        List<Issue> issues = new ArrayList<>();
        if (libraries == null) return issues;
        for (LocalLibraryMetadata lib : libraries) {
            if (lib == null || lib.artifact == null) issues.add(new Issue("error", "Library metadata is incomplete", null));
            else if (!lib.artifact.exists()) issues.add(new Issue("error", "Library artifact is missing", lib.artifact));
            else if (lib.artifact.length() == 0) issues.add(new Issue("error", "Library artifact is empty", lib.artifact));
            else if (!lib.artifact.getName().endsWith(".jar") && !lib.artifact.getName().endsWith(".aar")) issues.add(new Issue("warning", "Unsupported library artifact", lib.artifact));
        }
        return issues;
    }
}
