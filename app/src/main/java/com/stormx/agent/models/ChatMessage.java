package com.stormx.agent.models;

import java.util.List;
import java.util.ArrayList;

public class ChatMessage {
    public static final int TYPE_USER = 1;
    public static final int TYPE_AI = 2;

    private String id;
    private String content;
    private int type;
    private long timestamp;
    private List<AttachedFile> attachedFiles;

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

    public List<AttachedFile> getAttachedFiles() {
        return attachedFiles;
    }

    public void setAttachedFiles(List<AttachedFile> attachedFiles) {
        this.attachedFiles = attachedFiles;
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
            if (size < 1024) {
                return size + " B";
            } else if (size < 1024 * 1024) {
                return String.format("%.1f KB", size / 1024.0);
            } else {
                return String.format("%.1f MB", size / (1024.0 * 1024.0));
            }
        }
    }
}