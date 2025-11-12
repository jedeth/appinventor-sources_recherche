package com.google.appinventor.server.storage.database.entities;

import java.time.Instant;
import java.util.List;

/**
 * All remaining entities for MIT App Inventor
 * Phase 4 - PostgreSQL Migration
 */

// ========================================
// Project Entity
// ========================================
class Project {
    private Long projectId;
    private String projectName;
    private String projectType;
    private String ownerId;
    private String settingsJson;
    private Long dateCreated;
    private Long dateModified;
    private String attributionId;
    private Instant createdAt;
    private Instant updatedAt;

    public Project() {}
    public Project(Long projectId, String projectName, String ownerId) {
        this.projectId = projectId;
        this.projectName = projectName;
        this.ownerId = ownerId;
        long now = System.currentTimeMillis();
        this.dateCreated = now;
        this.dateModified = now;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters/Setters
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getProjectType() { return projectType; }
    public void setProjectType(String projectType) { this.projectType = projectType; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getSettingsJson() { return settingsJson; }
    public void setSettingsJson(String settingsJson) { this.settingsJson = settingsJson; }
    public Long getDateCreated() { return dateCreated; }
    public void setDateCreated(Long dateCreated) { this.dateCreated = dateCreated; }
    public Long getDateModified() { return dateModified; }
    public void setDateModified(Long dateModified) { this.dateModified = dateModified; }
    public String getAttributionId() { return attributionId; }
    public void setAttributionId(String attributionId) { this.attributionId = attributionId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

// ========================================
// FileData Entity
// ========================================
class FileData {
    private Long id;
    private String fileName;
    private Long projectId;
    private byte[] content;
    private String storageLocation;  // "database" or "minio"
    private String minioKey;
    private Long fileSize;
    private String role;
    private boolean isBlobFile;
    private Instant createdAt;
    private Instant updatedAt;

    public FileData() {}
    public FileData(Long projectId, String fileName, byte[] content) {
        this.projectId = projectId;
        this.fileName = fileName;
        this.content = content;
        this.storageLocation = "database";
        this.fileSize = (long) (content != null ? content.length : 0);
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters/Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public byte[] getContent() { return content; }
    public void setContent(byte[] content) { this.content = content; }
    public String getStorageLocation() { return storageLocation; }
    public void setStorageLocation(String storageLocation) { this.storageLocation = storageLocation; }
    public String getMinioKey() { return minioKey; }
    public void setMinioKey(String minioKey) { this.minioKey = minioKey; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isBlobFile() { return isBlobFile; }
    public void setBlobFile(boolean blobFile) { isBlobFile = blobFile; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

// ========================================
// Gallery App Entity
// ========================================
class GalleryApp {
    private Long galleryId;
    private String appName;
    private String appDescription;
    private Long sourceProjectId;
    private String developerId;
    private String developerName;
    private String developerEmail;
    private Long appCreated;
    private Long appPublished;
    private String status;  // "draft", "published", "moderated", "removed"
    private String appImageUrl;
    private String appSourceUrl;
    private Integer downloads;
    private Integer likes;
    private Integer views;
    private List<String> tags;
    private String category;
    private Instant createdAt;
    private Instant updatedAt;

    public GalleryApp() {
        this.downloads = 0;
        this.likes = 0;
        this.views = 0;
        this.status = "draft";
    }

    // Getters/Setters (abbreviated for space)
    public Long getGalleryId() { return galleryId; }
    public void setGalleryId(Long galleryId) { this.galleryId = galleryId; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public String getAppDescription() { return appDescription; }
    public void setAppDescription(String appDescription) { this.appDescription = appDescription; }
    public Long getSourceProjectId() { return sourceProjectId; }
    public void setSourceProjectId(Long sourceProjectId) { this.sourceProjectId = sourceProjectId; }
    public String getDeveloperId() { return developerId; }
    public void setDeveloperId(String developerId) { this.developerId = developerId; }
    public String getDeveloperName() { return developerName; }
    public void setDeveloperName(String developerName) { this.developerName = developerName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getDownloads() { return downloads; }
    public void setDownloads(Integer downloads) { this.downloads = downloads; }
    public Integer getLikes() { return likes; }
    public void setLikes(Integer likes) { this.likes = likes; }
    public Integer getViews() { return views; }
    public void setViews(Integer views) { this.views = views; }
    // ... other getters/setters
}

// ========================================
// Comment Entities
// ========================================
class ProjectComment {
    private Long id;
    private Long projectId;
    private String comment;
    private String authorId;
    private String authorName;
    private Long timestamp;
    private Instant createdAt;

    public ProjectComment() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}

class GalleryComment {
    private Long commentId;
    private Long galleryId;
    private String comment;
    private String authorId;
    private String authorName;
    private Long timestamp;
    private boolean isModerated;
    private String moderationReason;
    private Instant createdAt;

    public GalleryComment() {}

    public Long getCommentId() { return commentId; }
    public void setCommentId(Long commentId) { this.commentId = commentId; }
    public Long getGalleryId() { return galleryId; }
    public void setGalleryId(Long galleryId) { this.galleryId = galleryId; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public boolean isModerated() { return isModerated; }
    public void setModerated(boolean moderated) { isModerated = moderated; }
}

// ========================================
// Simple Entities
// ========================================
class Motd {
    private Long id;
    private String caption;
    private String moreInfoUrl;
    private Long startDate;
    private Long endDate;
    private boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;

    public Motd() { this.isActive = true; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public String getMoreInfoUrl() { return moreInfoUrl; }
    public void setMoreInfoUrl(String moreInfoUrl) { this.moreInfoUrl = moreInfoUrl; }
    public Long getStartDate() { return startDate; }
    public void setStartDate(Long startDate) { this.startDate = startDate; }
    public Long getEndDate() { return endDate; }
    public void setEndDate(Long endDate) { this.endDate = endDate; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
}

class Backpack {
    private Long id;
    private String userId;
    private String contentJson;  // JSON string
    private Instant createdAt;
    private Instant updatedAt;

    public Backpack() {}
    public Backpack(String userId, String contentJson) {
        this.userId = userId;
        this.contentJson = contentJson;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getContentJson() { return contentJson; }
    public void setContentJson(String contentJson) { this.contentJson = contentJson; }
}

// ========================================
// UserDataExport (for RGPD compliance)
// ========================================
class UserDataExport {
    private User user;
    private List<Project> projects;
    private List<FileData> files;
    private List<GalleryApp> galleryApps;
    private List<ProjectComment> comments;
    private Backpack backpack;
    private Instant exportDate;

    public UserDataExport() {
        this.exportDate = Instant.now();
    }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public List<Project> getProjects() { return projects; }
    public void setProjects(List<Project> projects) { this.projects = projects; }
    public List<FileData> getFiles() { return files; }
    public void setFiles(List<FileData> files) { this.files = files; }
    public Instant getExportDate() { return exportDate; }
}
