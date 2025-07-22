package pro.sketchware.activities.ai.chat.models;

import java.util.List;
import java.util.ArrayList;

public class ChatMessage {
    public static final int TYPE_USER = 1;
    public static final int TYPE_AI = 2;
    public static final int TYPE_PROPOSAL = 3;

    private String id;
    private String content;
    private int type;
    private long timestamp;
    private String projectId;
    private String projectName;
    private String appName;
    private String packageName;
    private String proposalData;
    private String explanation;
    private List<AttachedFile> attachedFiles;
    private String thinkingContent;

    public ChatMessage() {
        this.attachedFiles = new ArrayList<>();
    }

    public ChatMessage(String id, String content, int type, long timestamp) {
        this.id = id;
        this.content = content;
        this.type = type;
        this.timestamp = timestamp;
        this.attachedFiles = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isUserMessage() {
        return type == TYPE_USER;
    }

    public boolean isAiMessage() {
        return type == TYPE_AI;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public boolean hasProjectData() {
        return projectId != null && !projectId.isEmpty();
    }

    public String getProposalData() {
        return proposalData;
    }

    public void setProposalData(String proposalData) {
        this.proposalData = proposalData;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public boolean hasProposalData() {
        return proposalData != null && !proposalData.isEmpty();
    }

    public List<AttachedFile> getAttachedFiles() {
        return attachedFiles;
    }

    public void setAttachedFiles(List<AttachedFile> attachedFiles) {
        this.attachedFiles = attachedFiles != null ? attachedFiles : new ArrayList<>();
    }

    public void addAttachedFile(AttachedFile file) {
        if (this.attachedFiles == null) {
            this.attachedFiles = new ArrayList<>();
        }
        this.attachedFiles.add(file);
    }

    public void removeAttachedFile(AttachedFile file) {
        if (this.attachedFiles != null) {
            this.attachedFiles.remove(file);
        }
    }

    public boolean hasAttachedFiles() {
        return attachedFiles != null && !attachedFiles.isEmpty();
    }

    public String getThinkingContent() {
        return thinkingContent;
    }

    public void setThinkingContent(String thinkingContent) {
        this.thinkingContent = thinkingContent;
    }

    public boolean hasThinkingContent() {
        return thinkingContent != null && !thinkingContent.trim().isEmpty();
    }

    public static class AttachedFile {
        private String id;
        private String name;
        private String url;
        private String mimeType;
        private long size;
        private String localPath;

        public AttachedFile() {}

        public AttachedFile(String id, String name, String url, String mimeType, long size) {
            this.id = id;
            this.name = name;
            this.url = url;
            this.mimeType = mimeType;
            this.size = size;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }

        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }

        public String getLocalPath() { return localPath; }
        public void setLocalPath(String localPath) { this.localPath = localPath; }

        public String getFormattedSize() {
            if (size < 1024) return size + " B";
            else if (size < 1048576) return String.format("%.1f KB", size / 1024.0);
            else return String.format("%.1f MB", size / 1048576.0);
        }
    }
}