package pro.sketchware.activities.main.fragments.projects_store.api;

import android.os.Parcel;
import android.os.Parcelable;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * Model class representing a project in the projects store API
 * Implements both Parcelable for Android and Serializable for general Java compatibility
 */
public class ProjectModel implements Parcelable, Serializable {
    
    private static final long serialVersionUID = 1L;
    
    // Core fields
    private String id;
    private String name;
    private String description;
    private String author;
    private String version;
    
    // Extended fields commonly found in project models
    private String projectType;
    private String category;
    private String thumbnailUrl;
    private String downloadUrl;
    private String sourceUrl;
    private long createdDate;
    private long updatedDate;
    private int downloadCount;
    private int viewCount;
    private float rating;
    private long fileSize;
    private String[] tags;
    private boolean isPublic;
    private boolean isFeatured;

    // Constructors
    public ProjectModel() {
        // Default constructor
    }

    public ProjectModel(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public ProjectModel(String id, String name, String description, String author, String version) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.author = author;
        this.version = version;
    }

    public ProjectModel(String id, String name, String description, String author, String version, long createdDate) {
        this(id, name, description, author, version);
        this.createdDate = createdDate;
    }

    // Core getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    // Extended getters and setters
    public String getProjectType() {
        return projectType;
    }

    public void setProjectType(String projectType) {
        this.projectType = projectType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public long getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(long createdDate) {
        this.createdDate = createdDate;
    }

    public long getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(long updatedDate) {
        this.updatedDate = updatedDate;
    }

    public int getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(int downloadCount) {
        this.downloadCount = downloadCount;
    }

    public int getViewCount() {
        return viewCount;
    }

    public void setViewCount(int viewCount) {
        this.viewCount = viewCount;
    }

    public float getRating() {
        return rating;
    }

    public void setRating(float rating) {
        this.rating = rating;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String[] getTags() {
        return tags;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
    }

    public boolean getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public boolean getIsFeatured() {
        return isFeatured;
    }

    public void setIsFeatured(boolean isFeatured) {
        this.isFeatured = isFeatured;
    }

    // Validation method
    public boolean isValid() {
        return id != null && !id.trim().isEmpty() && 
               name != null && !name.trim().isEmpty();
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ProjectModel model = new ProjectModel();

        public Builder id(String id) {
            model.setId(id);
            return this;
        }

        public Builder name(String name) {
            model.setName(name);
            return this;
        }

        public Builder description(String description) {
            model.setDescription(description);
            return this;
        }

        public Builder author(String author) {
            model.setAuthor(author);
            return this;
        }

        public Builder version(String version) {
            model.setVersion(version);
            return this;
        }

        public ProjectModel build() {
            return model;
        }
    }

    // Factory methods
    public static ProjectModel create() {
        return new ProjectModel();
    }

    public static ProjectModel createWithDefaults(String id, String name) {
        ProjectModel model = new ProjectModel(id, name);
        model.setCreatedDate(System.currentTimeMillis());
        model.setIsPublic(true);
        model.setIsFeatured(false);
        return model;
    }

    // Object methods
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ProjectModel that = (ProjectModel) obj;
        return Objects.equals(id, that.id) &&
               Objects.equals(name, that.name) &&
               Objects.equals(description, that.description) &&
               Objects.equals(author, that.author) &&
               Objects.equals(version, that.version) &&
               Objects.equals(projectType, that.projectType) &&
               Objects.equals(category, that.category) &&
               createdDate == that.createdDate &&
               downloadCount == that.downloadCount &&
               Float.compare(that.rating, rating) == 0 &&
               isPublic == that.isPublic &&
               isFeatured == that.isFeatured &&
               Arrays.equals(tags, that.tags);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, name, description, author, version, projectType, 
                                 category, createdDate, downloadCount, rating, isPublic, isFeatured);
        result = 31 * result + Arrays.hashCode(tags);
        return result;
    }

    @Override
    public String toString() {
        return "ProjectModel{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               ", description='" + description + '\'' +
               ", author='" + author + '\'' +
               ", version='" + version + '\'' +
               ", projectType='" + projectType + '\'' +
               ", category='" + category + '\'' +
               ", createdDate=" + createdDate +
               ", downloadCount=" + downloadCount +
               ", rating=" + rating +
               ", isPublic=" + isPublic +
               ", isFeatured=" + isFeatured +
               ", tags=" + Arrays.toString(tags) +
               '}';
    }

    // Cleanup method
    public void cleanup() {
        this.id = null;
        this.name = null;
        this.description = null;
        this.author = null;
        this.version = null;
        this.tags = null;
    }

    // Parcelable implementation
    protected ProjectModel(Parcel in) {
        id = in.readString();
        name = in.readString();
        description = in.readString();
        author = in.readString();
        version = in.readString();
        projectType = in.readString();
        category = in.readString();
        thumbnailUrl = in.readString();
        downloadUrl = in.readString();
        sourceUrl = in.readString();
        createdDate = in.readLong();
        updatedDate = in.readLong();
        downloadCount = in.readInt();
        viewCount = in.readInt();
        rating = in.readFloat();
        fileSize = in.readLong();
        tags = in.createStringArray();
        isPublic = in.readByte() != 0;
        isFeatured = in.readByte() != 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(name);
        dest.writeString(description);
        dest.writeString(author);
        dest.writeString(version);
        dest.writeString(projectType);
        dest.writeString(category);
        dest.writeString(thumbnailUrl);
        dest.writeString(downloadUrl);
        dest.writeString(sourceUrl);
        dest.writeLong(createdDate);
        dest.writeLong(updatedDate);
        dest.writeInt(downloadCount);
        dest.writeInt(viewCount);
        dest.writeFloat(rating);
        dest.writeLong(fileSize);
        dest.writeStringArray(tags);
        dest.writeByte((byte) (isPublic ? 1 : 0));
        dest.writeByte((byte) (isFeatured ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ProjectModel> CREATOR = new Creator<ProjectModel>() {
        @Override
        public ProjectModel createFromParcel(Parcel in) {
            return new ProjectModel(in);
        }

        @Override
        public ProjectModel[] newArray(int size) {
            return new ProjectModel[size];
        }
    };
}