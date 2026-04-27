package pro.sketchware.library;

import java.io.File;

import pro.sketchware.utility.io.SafeFileOps;

public final class LibraryUpdateManager {
    private final LibraryUpdateUndoManager undoManager = new LibraryUpdateUndoManager();

    public void replace(File currentArtifact, File replacement, File backupDir) throws Exception {
        if (currentArtifact.exists()) undoManager.snapshot(currentArtifact, backupDir);
        SafeFileOps.ensureParent(currentArtifact);
        java.nio.file.Files.copy(replacement.toPath(), currentArtifact.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public boolean undoLast() throws Exception { return undoManager.undoLast(); }
}
