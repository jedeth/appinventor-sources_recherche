package com.google.appinventor.server.storage.database.entities;

import java.time.Instant;
import java.util.Objects;

/**
 * User entity - replaces UserData.java (Objectify)
 * Maps to 'users' table in PostgreSQL
 */
public class User {
    private String id;
    private String email;
    private Long repositoryId;
    private boolean isAdmin;
    private String userLink;
    private String settingsJson;  // JSON string
    private boolean tosAccepted;
    private String sessionId;
    private Long sessionStartTimestamp;
    private Instant lastLogin;
    private Instant createdAt;
    private Instant updatedAt;

    // RGPD fields
    private Instant consentDate;
    private Instant dataRetentionUntil;

    public User() {}

    public User(String id, String email) {
        this.id = id;
        this.email = email;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Long getRepositoryId() { return repositoryId; }
    public void setRepositoryId(Long repositoryId) { this.repositoryId = repositoryId; }

    public boolean isAdmin() { return isAdmin; }
    public void setAdmin(boolean admin) { isAdmin = admin; }

    public String getUserLink() { return userLink; }
    public void setUserLink(String userLink) { this.userLink = userLink; }

    public String getSettingsJson() { return settingsJson; }
    public void setSettingsJson(String settingsJson) { this.settingsJson = settingsJson; }

    public boolean isTosAccepted() { return tosAccepted; }
    public void setTosAccepted(boolean tosAccepted) { this.tosAccepted = tosAccepted; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Long getSessionStartTimestamp() { return sessionStartTimestamp; }
    public void setSessionStartTimestamp(Long sessionStartTimestamp) {
        this.sessionStartTimestamp = sessionStartTimestamp;
    }

    public Instant getLastLogin() { return lastLogin; }
    public void setLastLogin(Instant lastLogin) { this.lastLogin = lastLogin; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getConsentDate() { return consentDate; }
    public void setConsentDate(Instant consentDate) { this.consentDate = consentDate; }

    public Instant getDataRetentionUntil() { return dataRetentionUntil; }
    public void setDataRetentionUntil(Instant dataRetentionUntil) {
        this.dataRetentionUntil = dataRetentionUntil;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "User{id='" + id + "', email='" + email + "', isAdmin=" + isAdmin + "}";
    }
}
