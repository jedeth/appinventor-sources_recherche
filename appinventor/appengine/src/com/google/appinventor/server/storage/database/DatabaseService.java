package com.google.appinventor.server.storage.database;

import com.google.appinventor.server.storage.database.entities.*;
import com.google.appinventor.server.storage.database.exceptions.DatabaseException;

import java.util.List;
import java.util.Optional;

/**
 * Database Service Interface - Phase 4
 *
 * Abstraction layer for database operations, replacing Google Datastore/Objectify.
 * Provides a clean interface for all database operations needed by MIT App Inventor.
 *
 * This interface allows switching between different database backends (PostgreSQL, MySQL, etc.)
 * without changing the application code.
 *
 * @author MIT App Inventor Team
 * @version 2.0 - Phase 4 (RGPD Compliant)
 */
public interface DatabaseService {

    // ========================================
    // User Operations
    // ========================================

    /**
     * Get user by ID
     * @param userId User identifier
     * @return Optional containing the user if found
     * @throws DatabaseException if database error occurs
     */
    Optional<User> getUserById(String userId) throws DatabaseException;

    /**
     * Get user by email (case-insensitive)
     * @param email User email
     * @return Optional containing the user if found
     * @throws DatabaseException if database error occurs
     */
    Optional<User> getUserByEmail(String email) throws DatabaseException;

    /**
     * Get user by session ID
     * @param sessionId Session identifier
     * @return Optional containing the user if found
     * @throws DatabaseException if database error occurs
     */
    Optional<User> getUserBySessionId(String sessionId) throws DatabaseException;

    /**
     * Save or update user
     * @param user User to save
     * @return Saved user with updated timestamps
     * @throws DatabaseException if database error occurs
     */
    User saveUser(User user) throws DatabaseException;

    /**
     * Delete user by ID (RGPD - right to be forgotten)
     * @param userId User identifier
     * @throws DatabaseException if database error occurs
     */
    void deleteUser(String userId) throws DatabaseException;

    /**
     * Get all users (paginated, admin only)
     * @param limit Maximum number of users to return
     * @param offset Number of users to skip
     * @return List of users
     * @throws DatabaseException if database error occurs
     */
    List<User> getAllUsers(int limit, int offset) throws DatabaseException;

    /**
     * Count total number of users
     * @return Total user count
     * @throws DatabaseException if database error occurs
     */
    long countUsers() throws DatabaseException;

    /**
     * Update user session information
     * @param userId User identifier
     * @param sessionId New session ID
     * @param timestamp Session start timestamp
     * @throws DatabaseException if database error occurs
     */
    void updateUserSession(String userId, String sessionId, long timestamp) throws DatabaseException;

    /**
     * Clear user session (logout)
     * @param userId User identifier
     * @throws DatabaseException if database error occurs
     */
    void clearUserSession(String userId) throws DatabaseException;

    // ========================================
    // Project Operations
    // ========================================

    /**
     * Get project by ID
     * @param projectId Project identifier
     * @return Optional containing the project if found
     * @throws DatabaseException if database error occurs
     */
    Optional<Project> getProjectById(long projectId) throws DatabaseException;

    /**
     * Get all projects owned by a user
     * @param userId User identifier
     * @return List of projects
     * @throws DatabaseException if database error occurs
     */
    List<Project> getProjectsByUserId(String userId) throws DatabaseException;

    /**
     * Get projects by user with state filter
     * @param userId User identifier
     * @param state Project state filter
     * @return List of projects
     * @throws DatabaseException if database error occurs
     */
    List<Project> getProjectsByUserIdAndState(String userId, int state) throws DatabaseException;

    /**
     * Save or update project
     * @param project Project to save
     * @return Saved project with updated timestamps
     * @throws DatabaseException if database error occurs
     */
    Project saveProject(Project project) throws DatabaseException;

    /**
     * Delete project by ID
     * @param projectId Project identifier
     * @throws DatabaseException if database error occurs
     */
    void deleteProject(long projectId) throws DatabaseException;

    /**
     * Search projects by name pattern (for a specific user)
     * @param userId User identifier
     * @param namePattern Name pattern (SQL LIKE syntax)
     * @return List of matching projects
     * @throws DatabaseException if database error occurs
     */
    List<Project> searchProjectsByName(String userId, String namePattern) throws DatabaseException;

    /**
     * Count projects for a user
     * @param userId User identifier
     * @return Number of projects
     * @throws DatabaseException if database error occurs
     */
    long countProjectsByUserId(String userId) throws DatabaseException;

    // ========================================
    // File Operations
    // ========================================

    /**
     * Get file by ID
     * @param fileId File identifier
     * @return Optional containing the file if found
     * @throws DatabaseException if database error occurs
     */
    Optional<FileData> getFileById(long fileId) throws DatabaseException;

    /**
     * Get all files for a project
     * @param projectId Project identifier
     * @return List of files
     * @throws DatabaseException if database error occurs
     */
    List<FileData> getFilesByProjectId(long projectId) throws DatabaseException;

    /**
     * Get specific file by name in a project
     * @param projectId Project identifier
     * @param fileName File name
     * @return Optional containing the file if found
     * @throws DatabaseException if database error occurs
     */
    Optional<FileData> getFileByName(long projectId, String fileName) throws DatabaseException;

    /**
     * Get files by role (e.g., "source", "target")
     * @param projectId Project identifier
     * @param role File role
     * @return List of files with that role
     * @throws DatabaseException if database error occurs
     */
    List<FileData> getFilesByProjectIdAndRole(long projectId, String role) throws DatabaseException;

    /**
     * Save or update file
     * @param file File to save
     * @return Saved file with updated timestamps
     * @throws DatabaseException if database error occurs
     */
    FileData saveFile(FileData file) throws DatabaseException;

    /**
     * Delete file by ID
     * @param fileId File identifier
     * @throws DatabaseException if database error occurs
     */
    void deleteFile(long fileId) throws DatabaseException;

    /**
     * Delete all files for a project
     * @param projectId Project identifier
     * @throws DatabaseException if database error occurs
     */
    void deleteFilesByProjectId(long projectId) throws DatabaseException;

    // ========================================
    // User-Project Association
    // ========================================

    /**
     * Associate user with project
     * @param userId User identifier
     * @param projectId Project identifier
     * @param state Initial state
     * @throws DatabaseException if database error occurs
     */
    void addUserToProject(String userId, long projectId, int state) throws DatabaseException;

    /**
     * Remove user from project
     * @param userId User identifier
     * @param projectId Project identifier
     * @throws DatabaseException if database error occurs
     */
    void removeUserFromProject(String userId, long projectId) throws DatabaseException;

    /**
     * Check if user has access to project
     * @param userId User identifier
     * @param projectId Project identifier
     * @return true if user has access
     * @throws DatabaseException if database error occurs
     */
    boolean userHasAccessToProject(String userId, long projectId) throws DatabaseException;

    // ========================================
    // Gallery Operations
    // ========================================

    /**
     * Get gallery app by ID
     * @param galleryId Gallery app identifier
     * @return Optional containing the gallery app if found
     * @throws DatabaseException if database error occurs
     */
    Optional<GalleryApp> getGalleryAppById(long galleryId) throws DatabaseException;

    /**
     * Get all published gallery apps (paginated)
     * @param limit Maximum number to return
     * @param offset Number to skip
     * @return List of gallery apps
     * @throws DatabaseException if database error occurs
     */
    List<GalleryApp> getPublishedGalleryApps(int limit, int offset) throws DatabaseException;

    /**
     * Get gallery apps by developer
     * @param developerId Developer user ID
     * @return List of gallery apps
     * @throws DatabaseException if database error occurs
     */
    List<GalleryApp> getGalleryAppsByDeveloper(String developerId) throws DatabaseException;

    /**
     * Search gallery apps
     * @param query Search query (searches in name and description)
     * @param category Category filter (null for all)
     * @param limit Maximum number to return
     * @param offset Number to skip
     * @return List of matching gallery apps
     * @throws DatabaseException if database error occurs
     */
    List<GalleryApp> searchGalleryApps(String query, String category, int limit, int offset) throws DatabaseException;

    /**
     * Save or update gallery app
     * @param app Gallery app to save
     * @return Saved gallery app
     * @throws DatabaseException if database error occurs
     */
    GalleryApp saveGalleryApp(GalleryApp app) throws DatabaseException;

    /**
     * Delete gallery app
     * @param galleryId Gallery app identifier
     * @throws DatabaseException if database error occurs
     */
    void deleteGalleryApp(long galleryId) throws DatabaseException;

    /**
     * Increment gallery app statistics
     * @param galleryId Gallery app identifier
     * @param statType Statistic type ("downloads", "likes", "views")
     * @throws DatabaseException if database error occurs
     */
    void incrementGalleryAppStat(long galleryId, String statType) throws DatabaseException;

    // ========================================
    // Comments Operations
    // ========================================

    /**
     * Get comments for a project
     * @param projectId Project identifier
     * @return List of comments
     * @throws DatabaseException if database error occurs
     */
    List<ProjectComment> getProjectComments(long projectId) throws DatabaseException;

    /**
     * Add comment to project
     * @param comment Comment to add
     * @return Saved comment
     * @throws DatabaseException if database error occurs
     */
    ProjectComment addProjectComment(ProjectComment comment) throws DatabaseException;

    /**
     * Delete comment
     * @param commentId Comment identifier
     * @throws DatabaseException if database error occurs
     */
    void deleteProjectComment(long commentId) throws DatabaseException;

    /**
     * Get comments for gallery app
     * @param galleryId Gallery app identifier
     * @return List of comments
     * @throws DatabaseException if database error occurs
     */
    List<GalleryComment> getGalleryComments(long galleryId) throws DatabaseException;

    /**
     * Add comment to gallery app
     * @param comment Comment to add
     * @return Saved comment
     * @throws DatabaseException if database error occurs
     */
    GalleryComment addGalleryComment(GalleryComment comment) throws DatabaseException;

    /**
     * Delete gallery comment
     * @param commentId Comment identifier
     * @throws DatabaseException if database error occurs
     */
    void deleteGalleryComment(long commentId) throws DatabaseException;

    // ========================================
    // Nonce Operations (Security)
    // ========================================

    /**
     * Create nonce for CSRF protection
     * @param nonce Nonce value
     * @param timestamp Creation timestamp
     * @throws DatabaseException if database error occurs
     */
    void createNonce(String nonce, long timestamp) throws DatabaseException;

    /**
     * Check if nonce exists and is valid
     * @param nonce Nonce value to check
     * @return true if nonce is valid
     * @throws DatabaseException if database error occurs
     */
    boolean isNonceValid(String nonce) throws DatabaseException;

    /**
     * Delete expired nonces (cleanup)
     * @param expiryTimestamp Timestamp before which nonces are considered expired
     * @return Number of nonces deleted
     * @throws DatabaseException if database error occurs
     */
    int deleteExpiredNonces(long expiryTimestamp) throws DatabaseException;

    // ========================================
    // Whitelist Operations
    // ========================================

    /**
     * Check if email is whitelisted
     * @param email Email to check
     * @return true if whitelisted
     * @throws DatabaseException if database error occurs
     */
    boolean isEmailWhitelisted(String email) throws DatabaseException;

    /**
     * Add email to whitelist
     * @param email Email to add
     * @throws DatabaseException if database error occurs
     */
    void addEmailToWhitelist(String email) throws DatabaseException;

    /**
     * Remove email from whitelist
     * @param email Email to remove
     * @throws DatabaseException if database error occurs
     */
    void removeEmailFromWhitelist(String email) throws DatabaseException;

    /**
     * Get all whitelisted emails
     * @return List of whitelisted emails
     * @throws DatabaseException if database error occurs
     */
    List<String> getWhitelistedEmails() throws DatabaseException;

    // ========================================
    // MOTD (Message of the Day) Operations
    // ========================================

    /**
     * Get current active MOTD
     * @return Optional containing current MOTD if active
     * @throws DatabaseException if database error occurs
     */
    Optional<Motd> getCurrentMotd() throws DatabaseException;

    /**
     * Save or update MOTD
     * @param motd MOTD to save
     * @return Saved MOTD
     * @throws DatabaseException if database error occurs
     */
    Motd saveMotd(Motd motd) throws DatabaseException;

    /**
     * Delete MOTD
     * @param motdId MOTD identifier
     * @throws DatabaseException if database error occurs
     */
    void deleteMotd(long motdId) throws DatabaseException;

    // ========================================
    // Backpack Operations
    // ========================================

    /**
     * Get user's backpack
     * @param userId User identifier
     * @return Optional containing backpack if exists
     * @throws DatabaseException if database error occurs
     */
    Optional<Backpack> getUserBackpack(String userId) throws DatabaseException;

    /**
     * Save or update backpack
     * @param backpack Backpack to save
     * @return Saved backpack
     * @throws DatabaseException if database error occurs
     */
    Backpack saveBackpack(Backpack backpack) throws DatabaseException;

    /**
     * Delete user's backpack
     * @param userId User identifier
     * @throws DatabaseException if database error occurs
     */
    void deleteBackpack(String userId) throws DatabaseException;

    // ========================================
    // RGPD Compliance Operations
    // ========================================

    /**
     * Export all user data (RGPD - right to access)
     * @param userId User identifier
     * @return Complete user data export
     * @throws DatabaseException if database error occurs
     */
    UserDataExport exportUserData(String userId) throws DatabaseException;

    /**
     * Delete all user data (RGPD - right to be forgotten)
     * Deletes user, projects, files, comments, etc.
     * @param userId User identifier
     * @throws DatabaseException if database error occurs
     */
    void deleteAllUserData(String userId) throws DatabaseException;

    /**
     * Get users with expired data retention
     * @return List of users whose data should be deleted
     * @throws DatabaseException if database error occurs
     */
    List<User> getUsersWithExpiredRetention() throws DatabaseException;

    /**
     * Execute cleanup tasks (expired nonces, old data, etc.)
     * @return Number of records cleaned up
     * @throws DatabaseException if database error occurs
     */
    int executeCleanupTasks() throws DatabaseException;

    // ========================================
    // Transaction Support
    // ========================================

    /**
     * Execute operation in a transaction
     * @param transaction Transaction callback
     * @return Result of the transaction
     * @throws Exception if transaction fails
     */
    <T> T executeInTransaction(DatabaseTransaction<T> transaction) throws Exception;

    /**
     * Execute operation in a transaction (void return)
     * @param transaction Transaction callback
     * @throws Exception if transaction fails
     */
    void executeInTransactionVoid(DatabaseTransactionVoid transaction) throws Exception;

    // ========================================
    // Health Check & Monitoring
    // ========================================

    /**
     * Check database connection health
     * @return true if database is accessible
     */
    boolean isHealthy();

    /**
     * Get database statistics
     * @return Map of statistics (table counts, connection pool stats, etc.)
     * @throws DatabaseException if database error occurs
     */
    java.util.Map<String, Object> getStatistics() throws DatabaseException;

    /**
     * Get backend information
     * @return String describing the backend (e.g., "PostgreSQL 15.0 @ localhost:5432")
     */
    String getBackendInfo();

    /**
     * Close database connections and cleanup resources
     */
    void close();
}

/**
 * Functional interface for transactions that return a value
 */
@FunctionalInterface
interface DatabaseTransaction<T> {
    T execute() throws Exception;
}

/**
 * Functional interface for transactions that return void
 */
@FunctionalInterface
interface DatabaseTransactionVoid {
    void execute() throws Exception;
}
