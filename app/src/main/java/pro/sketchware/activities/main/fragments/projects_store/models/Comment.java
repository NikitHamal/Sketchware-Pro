package pro.sketchware.activities.main.fragments.projects_store.models;

import java.util.Objects;

/**
 * Model class representing a comment in the projects store
 */
public class Comment {
    private int id;
    private String author;
    private String content;
    private long timestamp;
    private int projectId;
    private int userId;

    public Comment() {
        // Default constructor
    }

    public Comment(int id, String author, String content, long timestamp) {
        this.id = id;
        this.author = author;
        this.content = content;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    @Override
    public String toString() {
        return "Comment{" +
                "id=" + id +
                ", author='" + author + '\'' +
                ", content='" + content + '\'' +
                ", timestamp=" + timestamp +
                ", projectId=" + projectId +
                ", userId=" + userId +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Comment comment = (Comment) o;
        return id == comment.id && 
               timestamp == comment.timestamp && 
               projectId == comment.projectId && 
               userId == comment.userId && 
               Objects.equals(author, comment.author) && 
               Objects.equals(content, comment.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, author, content, timestamp, projectId, userId);
    }
}