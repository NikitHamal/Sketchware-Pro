package pro.sketchware.ai.tools;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Context passed to tools during execution. Contains the application context,
 * the workspace identifier, and the list of project IDs the agent is allowed to access.
 */
public class ToolContext {

    private final Context appContext;
    private final List<String> allowedProjectIds;
    private final String workspaceId;

    public ToolContext(Context appContext, List<String> allowedProjectIds, String workspaceId) {
        this.appContext = appContext;
        this.allowedProjectIds = allowedProjectIds != null
                ? new ArrayList<>(allowedProjectIds)
                : new ArrayList<>();
        this.workspaceId = workspaceId;
    }

    /**
     * Returns the application context.
     */
    public Context getAppContext() {
        return appContext;
    }

    /**
     * Returns an unmodifiable list of project SC IDs the agent is allowed to access.
     */
    public List<String> getAllowedProjectIds() {
        return Collections.unmodifiableList(allowedProjectIds);
    }

    /**
     * Returns the workspace identifier for this session.
     */
    public String getWorkspaceId() {
        return workspaceId;
    }

    /**
     * Checks whether the agent is allowed to access the given project.
     *
     * @param scId the project SC ID to check
     * @return true if the project is in the allowed list, false otherwise
     */
    public boolean isProjectAllowed(String scId) {
        if (scId == null || scId.isEmpty()) {
            return false;
        }
        return allowedProjectIds.contains(scId);
    }

    /**
     * Returns the root .sketchware directory on external storage.
     */
    public File getSketchwareDir() {
        return new File(Environment.getExternalStorageDirectory(), ".sketchware");
    }

    /**
     * Returns the data directory for a specific project.
     * Path: .sketchware/data/{sc_id}/
     */
    public File getProjectDataDir(String scId) {
        return new File(getSketchwareDir(), "data" + File.separator + scId);
    }

    /**
     * Returns the mysc directory for a specific project.
     * Path: .sketchware/mysc/{sc_id}/
     */
    public File getProjectMyscDir(String scId) {
        return new File(getSketchwareDir(), "mysc" + File.separator + scId);
    }

    /**
     * Returns the mysc list directory for a specific project.
     * Path: .sketchware/mysc/list/{sc_id}/
     */
    public File getProjectMyscListDir(String scId) {
        return new File(getSketchwareDir(), "mysc" + File.separator + "list" + File.separator + scId);
    }

    /**
     * Returns the backup directory for a specific project.
     * Path: .sketchware/bak/{sc_id}/
     */
    public File getProjectBackupDir(String scId) {
        return new File(getSketchwareDir(), "bak" + File.separator + scId);
    }
}
