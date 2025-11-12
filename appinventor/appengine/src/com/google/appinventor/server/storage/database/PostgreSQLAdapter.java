package com.google.appinventor.server.storage.database;

import com.google.appinventor.server.storage.database.entities.*;
import com.google.appinventor.server.storage.database.exceptions.DatabaseException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

/**
 * PostgreSQL implementation of DatabaseService
 * Uses HikariCP for high-performance connection pooling
 *
 * Phase 4 - RGPD Compliant On-Premise Deployment
 * Replaces Google Datastore with PostgreSQL
 *
 * @author MIT App Inventor Team
 * @version 2.0
 */
public class PostgreSQLAdapter implements DatabaseService {

    private static final Logger LOG = Logger.getLogger(PostgreSQLAdapter.class.getName());
    private final HikariDataSource dataSource;
    private final String backendInfo;

    /**
     * Constructor - initializes connection pool
     *
     * @param host Database host
     * @param port Database port
     * @param database Database name
     * @param user Database user
     * @param password Database password
     * @param minPoolSize Minimum connection pool size
     * @param maxPoolSize Maximum connection pool size
     */
    public PostgreSQLAdapter(String host, int port, String database,
                             String user, String password,
                             int minPoolSize, int maxPoolSize) {

        this.backendInfo = String.format("PostgreSQL @ %s:%d/%s", host, port, database);

        HikariConfig config = new HikariConfig();

        // JDBC URL
        config.setJdbcUrl(String.format("jdbc:postgresql://%s:%d/%s", host, port, database));
        config.setUsername(user);
        config.setPassword(password);

        // Pool configuration
        config.setMinimumIdle(minPoolSize);
        config.setMaximumPoolSize(maxPoolSize);
        config.setConnectionTimeout(30000);  // 30 seconds
        config.setIdleTimeout(600000);       // 10 minutes
        config.setMaxLifetime(1800000);      // 30 minutes
        config.setLeakDetectionThreshold(60000);  // 60 seconds

        // Performance optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");

        // Application name for monitoring
        config.setPoolName("AppInventor-DB-Pool");
        config.addDataSourceProperty("ApplicationName", "AppInventor");

        // Create pool
        try {
            this.dataSource = new HikariDataSource(config);
            LOG.info("PostgreSQL connection pool created: " + backendInfo);

            // Test connection
            try (Connection conn = dataSource.getConnection()) {
                LOG.info("Database connection test successful");
            }
        } catch (SQLException e) {
            LOG.severe("Failed to create connection pool: " + e.getMessage());
            throw new DatabaseException("Failed to initialize database connection pool", e);
        }
    }

    /**
     * Get connection from pool
     */
    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // ========================================
    // USER OPERATIONS - FULLY IMPLEMENTED
    // ========================================

    @Override
    public Optional<User> getUserById(String userId) throws DatabaseException {
        String sql = "SELECT * FROM users WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapUserFromResultSet(rs));
            }
            return Optional.empty();

        } catch (SQLException e) {
            LOG.severe("Error getting user by ID: " + e.getMessage());
            throw new DatabaseException("Failed to get user by ID: " + userId, e);
        }
    }

    @Override
    public Optional<User> getUserByEmail(String email) throws DatabaseException {
        String sql = "SELECT * FROM users WHERE email_lower = LOWER(?)";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapUserFromResultSet(rs));
            }
            return Optional.empty();

        } catch (SQLException e) {
            LOG.severe("Error getting user by email: " + e.getMessage());
            throw new DatabaseException("Failed to get user by email: " + email, e);
        }
    }

    @Override
    public Optional<User> getUserBySessionId(String sessionId) throws DatabaseException {
        String sql = "SELECT * FROM users WHERE session_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, sessionId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapUserFromResultSet(rs));
            }
            return Optional.empty();

        } catch (SQLException e) {
            LOG.severe("Error getting user by session ID: " + e.getMessage());
            throw new DatabaseException("Failed to get user by session ID", e);
        }
    }

    @Override
    public User saveUser(User user) throws DatabaseException {
        String sql = """
            INSERT INTO users (id, email, repository_id, is_admin, user_link,
                              settings, tos_accepted, session_id, session_start_timestamp,
                              last_login, consent_date, data_retention_until)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                email = EXCLUDED.email,
                repository_id = EXCLUDED.repository_id,
                is_admin = EXCLUDED.is_admin,
                user_link = EXCLUDED.user_link,
                settings = EXCLUDED.settings,
                tos_accepted = EXCLUDED.tos_accepted,
                session_id = EXCLUDED.session_id,
                session_start_timestamp = EXCLUDED.session_start_timestamp,
                last_login = EXCLUDED.last_login,
                consent_date = EXCLUDED.consent_date,
                data_retention_until = EXCLUDED.data_retention_until,
                updated_at = CURRENT_TIMESTAMP
            RETURNING *
            """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            int idx = 1;
            stmt.setString(idx++, user.getId());
            stmt.setString(idx++, user.getEmail());

            if (user.getRepositoryId() != null) {
                stmt.setLong(idx++, user.getRepositoryId());
            } else {
                stmt.setNull(idx++, Types.BIGINT);
            }

            stmt.setBoolean(idx++, user.isAdmin());
            stmt.setString(idx++, user.getUserLink());
            stmt.setString(idx++, user.getSettingsJson() != null ? user.getSettingsJson() : "{}");
            stmt.setBoolean(idx++, user.isTosAccepted());
            stmt.setString(idx++, user.getSessionId());

            if (user.getSessionStartTimestamp() != null) {
                stmt.setLong(idx++, user.getSessionStartTimestamp());
            } else {
                stmt.setNull(idx++, Types.BIGINT);
            }

            if (user.getLastLogin() != null) {
                stmt.setTimestamp(idx++, Timestamp.from(user.getLastLogin()));
            } else {
                stmt.setNull(idx++, Types.TIMESTAMP);
            }

            if (user.getConsentDate() != null) {
                stmt.setTimestamp(idx++, Timestamp.from(user.getConsentDate()));
            } else {
                stmt.setNull(idx++, Types.TIMESTAMP);
            }

            if (user.getDataRetentionUntil() != null) {
                stmt.setTimestamp(idx++, Timestamp.from(user.getDataRetentionUntil()));
            } else {
                stmt.setNull(idx++, Types.TIMESTAMP);
            }

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapUserFromResultSet(rs);
            }

            throw new DatabaseException("Failed to save user - no result returned");

        } catch (SQLException e) {
            LOG.severe("Error saving user: " + e.getMessage());
            throw new DatabaseException("Failed to save user: " + user.getId(), e);
        }
    }

    @Override
    public void deleteUser(String userId) throws DatabaseException {
        String sql = "DELETE FROM users WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userId);
            int deleted = stmt.executeUpdate();

            LOG.info("Deleted user " + userId + " (RGPD compliance)");

        } catch (SQLException e) {
            LOG.severe("Error deleting user: " + e.getMessage());
            throw new DatabaseException("Failed to delete user: " + userId, e);
        }
    }

    @Override
    public List<User> getAllUsers(int limit, int offset) throws DatabaseException {
        String sql = "SELECT * FROM users ORDER BY created_at DESC LIMIT ? OFFSET ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);
            stmt.setInt(2, offset);

            ResultSet rs = stmt.executeQuery();
            List<User> users = new ArrayList<>();

            while (rs.next()) {
                users.add(mapUserFromResultSet(rs));
            }

            return users;

        } catch (SQLException e) {
            LOG.severe("Error getting all users: " + e.getMessage());
            throw new DatabaseException("Failed to get all users", e);
        }
    }

    @Override
    public long countUsers() throws DatabaseException {
        String sql = "SELECT COUNT(*) FROM users";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;

        } catch (SQLException e) {
            throw new DatabaseException("Failed to count users", e);
        }
    }

    @Override
    public void updateUserSession(String userId, String sessionId, long timestamp) throws DatabaseException {
        String sql = "UPDATE users SET session_id = ?, session_start_timestamp = ?, " +
                    "last_login = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, sessionId);
            stmt.setLong(2, timestamp);
            stmt.setString(3, userId);

            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new DatabaseException("Failed to update user session", e);
        }
    }

    @Override
    public void clearUserSession(String userId) throws DatabaseException {
        String sql = "UPDATE users SET session_id = NULL, session_start_timestamp = NULL WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new DatabaseException("Failed to clear user session", e);
        }
    }

    /**
     * Map ResultSet to User entity
     */
    private User mapUserFromResultSet(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getString("id"));
        user.setEmail(rs.getString("email"));

        long repoId = rs.getLong("repository_id");
        if (!rs.wasNull()) {
            user.setRepositoryId(repoId);
        }

        user.setAdmin(rs.getBoolean("is_admin"));
        user.setUserLink(rs.getString("user_link"));
        user.setSettingsJson(rs.getString("settings"));
        user.setTosAccepted(rs.getBoolean("tos_accepted"));
        user.setSessionId(rs.getString("session_id"));

        Long sessionStart = rs.getLong("session_start_timestamp");
        if (!rs.wasNull()) {
            user.setSessionStartTimestamp(sessionStart);
        }

        Timestamp lastLogin = rs.getTimestamp("last_login");
        if (lastLogin != null) {
            user.setLastLogin(lastLogin.toInstant());
        }

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            user.setCreatedAt(createdAt.toInstant());
        }

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            user.setUpdatedAt(updatedAt.toInstant());
        }

        Timestamp consentDate = rs.getTimestamp("consent_date");
        if (consentDate != null) {
            user.setConsentDate(consentDate.toInstant());
        }

        Timestamp retentionUntil = rs.getTimestamp("data_retention_until");
        if (retentionUntil != null) {
            user.setDataRetentionUntil(retentionUntil.toInstant());
        }

        return user;
    }

    // ========================================
    // PROJECT OPERATIONS - CORE METHODS IMPLEMENTED
    // ========================================

    @Override
    public Optional<Project> getProjectById(long projectId) throws DatabaseException {
        String sql = "SELECT * FROM projects WHERE project_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, projectId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapProjectFromResultSet(rs));
            }
            return Optional.empty();

        } catch (SQLException e) {
            throw new DatabaseException("Failed to get project by ID: " + projectId, e);
        }
    }

    @Override
    public List<Project> getProjectsByUserId(String userId) throws DatabaseException {
        String sql = "SELECT * FROM projects WHERE owner_id = ? ORDER BY date_modified DESC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();
            List<Project> projects = new ArrayList<>();

            while (rs.next()) {
                projects.add(mapProjectFromResultSet(rs));
            }

            return projects;

        } catch (SQLException e) {
            throw new DatabaseException("Failed to get projects for user: " + userId, e);
        }
    }

    @Override
    public Project saveProject(Project project) throws DatabaseException {
        String sql = """
            INSERT INTO projects (project_id, project_name, project_type, owner_id,
                                 settings, date_created, date_modified, attribution_id)
            VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?)
            ON CONFLICT (project_id) DO UPDATE SET
                project_name = EXCLUDED.project_name,
                project_type = EXCLUDED.project_type,
                settings = EXCLUDED.settings,
                date_modified = EXCLUDED.date_modified,
                attribution_id = EXCLUDED.attribution_id,
                updated_at = CURRENT_TIMESTAMP
            RETURNING *
            """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, project.getProjectId());
            stmt.setString(2, project.getProjectName());
            stmt.setString(3, project.getProjectType());
            stmt.setString(4, project.getOwnerId());
            stmt.setString(5, project.getSettingsJson() != null ? project.getSettingsJson() : "{}");
            stmt.setLong(6, project.getDateCreated());
            stmt.setLong(7, project.getDateModified());
            stmt.setString(8, project.getAttributionId());

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapProjectFromResultSet(rs);
            }

            throw new DatabaseException("Failed to save project - no result returned");

        } catch (SQLException e) {
            throw new DatabaseException("Failed to save project: " + project.getProjectId(), e);
        }
    }

    @Override
    public void deleteProject(long projectId) throws DatabaseException {
        // Note: Files will be cascade deleted by foreign key constraint
        String sql = "DELETE FROM projects WHERE project_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, projectId);
            stmt.executeUpdate();

            LOG.info("Deleted project " + projectId);

        } catch (SQLException e) {
            throw new DatabaseException("Failed to delete project: " + projectId, e);
        }
    }

    private Project mapProjectFromResultSet(ResultSet rs) throws SQLException {
        Project project = new Project();
        project.setProjectId(rs.getLong("project_id"));
        project.setProjectName(rs.getString("project_name"));
        project.setProjectType(rs.getString("project_type"));
        project.setOwnerId(rs.getString("owner_id"));
        project.setSettingsJson(rs.getString("settings"));
        project.setDateCreated(rs.getLong("date_created"));
        project.setDateModified(rs.getLong("date_modified"));
        project.setAttributionId(rs.getString("attribution_id"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            project.setCreatedAt(createdAt.toInstant());
        }

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            project.setUpdatedAt(updatedAt.toInstant());
        }

        return project;
    }

    // ========================================
    // STUB IMPLEMENTATIONS - TODO
    // These methods have basic implementations that need to be completed
    // ========================================

    @Override
    public List<Project> getProjectsByUserIdAndState(String userId, int state) throws DatabaseException {
        // TODO: Implement state filtering via user_projects table
        LOG.warning("getProjectsByUserIdAndState not fully implemented yet");
        return getProjectsByUserId(userId);
    }

    @Override
    public List<Project> searchProjectsByName(String userId, String namePattern) throws DatabaseException {
        // TODO: Implement LIKE search
        LOG.warning("searchProjectsByName not fully implemented yet");
        return getProjectsByUserId(userId);
    }

    @Override
    public long countProjectsByUserId(String userId) throws DatabaseException {
        String sql = "SELECT COUNT(*) FROM projects WHERE owner_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to count projects", e);
        }
    }

    // FILE OPERATIONS - Basic stubs
    @Override
    public Optional<FileData> getFileById(long fileId) throws DatabaseException {
        // TODO: Implement
        throw new UnsupportedOperationException("getFileById not implemented yet");
    }

    @Override
    public List<FileData> getFilesByProjectId(long projectId) throws DatabaseException {
        // TODO: Implement
        throw new UnsupportedOperationException("getFilesByProjectId not implemented yet");
    }

    @Override
    public Optional<FileData> getFileByName(long projectId, String fileName) throws DatabaseException {
        // TODO: Implement
        throw new UnsupportedOperationException("getFileByName not implemented yet");
    }

    @Override
    public List<FileData> getFilesByProjectIdAndRole(long projectId, String role) throws DatabaseException {
        // TODO: Implement
        throw new UnsupportedOperationException("getFilesByProjectIdAndRole not implemented yet");
    }

    @Override
    public FileData saveFile(FileData file) throws DatabaseException {
        // TODO: Implement with large file handling (store in MinIO if > threshold)
        throw new UnsupportedOperationException("saveFile not implemented yet");
    }

    @Override
    public void deleteFile(long fileId) throws DatabaseException {
        // TODO: Implement
        throw new UnsupportedOperationException("deleteFile not implemented yet");
    }

    @Override
    public void deleteFilesByProjectId(long projectId) throws DatabaseException {
        // TODO: Implement (used when deleting project)
        throw new UnsupportedOperationException("deleteFilesByProjectId not implemented yet");
    }

    // USER-PROJECT ASSOCIATION - Stubs
    @Override
    public void addUserToProject(String userId, long projectId, int state) throws DatabaseException {
        // TODO: Insert into user_projects table
        throw new UnsupportedOperationException("addUserToProject not implemented yet");
    }

    @Override
    public void removeUserFromProject(String userId, long projectId) throws DatabaseException {
        // TODO: Delete from user_projects table
        throw new UnsupportedOperationException("removeUserFromProject not implemented yet");
    }

    @Override
    public boolean userHasAccessToProject(String userId, long projectId) throws DatabaseException {
        // TODO: Check owner_id or user_projects table
        throw new UnsupportedOperationException("userHasAccessToProject not implemented yet");
    }

    // GALLERY OPERATIONS - Stubs
    @Override
    public Optional<GalleryApp> getGalleryAppById(long galleryId) throws DatabaseException {
        throw new UnsupportedOperationException("Gallery operations not implemented yet");
    }

    @Override
    public List<GalleryApp> getPublishedGalleryApps(int limit, int offset) throws DatabaseException {
        throw new UnsupportedOperationException("Gallery operations not implemented yet");
    }

    @Override
    public List<GalleryApp> getGalleryAppsByDeveloper(String developerId) throws DatabaseException {
        throw new UnsupportedOperationException("Gallery operations not implemented yet");
    }

    @Override
    public List<GalleryApp> searchGalleryApps(String query, String category, int limit, int offset) throws DatabaseException {
        throw new UnsupportedOperationException("Gallery operations not implemented yet");
    }

    @Override
    public GalleryApp saveGalleryApp(GalleryApp app) throws DatabaseException {
        throw new UnsupportedOperationException("Gallery operations not implemented yet");
    }

    @Override
    public void deleteGalleryApp(long galleryId) throws DatabaseException {
        throw new UnsupportedOperationException("Gallery operations not implemented yet");
    }

    @Override
    public void incrementGalleryAppStat(long galleryId, String statType) throws DatabaseException {
        throw new UnsupportedOperationException("Gallery operations not implemented yet");
    }

    // COMMENTS - Stubs
    @Override
    public List<ProjectComment> getProjectComments(long projectId) throws DatabaseException {
        throw new UnsupportedOperationException("Comment operations not implemented yet");
    }

    @Override
    public ProjectComment addProjectComment(ProjectComment comment) throws DatabaseException {
        throw new UnsupportedOperationException("Comment operations not implemented yet");
    }

    @Override
    public void deleteProjectComment(long commentId) throws DatabaseException {
        throw new UnsupportedOperationException("Comment operations not implemented yet");
    }

    @Override
    public List<GalleryComment> getGalleryComments(long galleryId) throws DatabaseException {
        throw new UnsupportedOperationException("Comment operations not implemented yet");
    }

    @Override
    public GalleryComment addGalleryComment(GalleryComment comment) throws DatabaseException {
        throw new UnsupportedOperationException("Comment operations not implemented yet");
    }

    @Override
    public void deleteGalleryComment(long commentId) throws DatabaseException {
        throw new UnsupportedOperationException("Comment operations not implemented yet");
    }

    // NONCE OPERATIONS - Basic implementation
    @Override
    public void createNonce(String nonce, long timestamp) throws DatabaseException {
        String sql = "INSERT INTO nonce (nonce, timestamp) VALUES (?, ?) ON CONFLICT (nonce) DO NOTHING";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, nonce);
            stmt.setLong(2, timestamp);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to create nonce", e);
        }
    }

    @Override
    public boolean isNonceValid(String nonce) throws DatabaseException {
        String sql = "SELECT COUNT(*) FROM nonce WHERE nonce = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, nonce);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to check nonce", e);
        }
    }

    @Override
    public int deleteExpiredNonces(long expiryTimestamp) throws DatabaseException {
        String sql = "DELETE FROM nonce WHERE timestamp < ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, expiryTimestamp);
            return stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to delete expired nonces", e);
        }
    }

    // WHITELIST OPERATIONS - Basic implementation
    @Override
    public boolean isEmailWhitelisted(String email) throws DatabaseException {
        String sql = "SELECT COUNT(*) FROM whitelist WHERE email_lower = LOWER(?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to check whitelist", e);
        }
    }

    @Override
    public void addEmailToWhitelist(String email) throws DatabaseException {
        String sql = "INSERT INTO whitelist (email_lower) VALUES (LOWER(?)) ON CONFLICT DO NOTHING";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to add email to whitelist", e);
        }
    }

    @Override
    public void removeEmailFromWhitelist(String email) throws DatabaseException {
        String sql = "DELETE FROM whitelist WHERE email_lower = LOWER(?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to remove email from whitelist", e);
        }
    }

    @Override
    public List<String> getWhitelistedEmails() throws DatabaseException {
        String sql = "SELECT email_lower FROM whitelist ORDER BY email_lower";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            List<String> emails = new ArrayList<>();
            while (rs.next()) {
                emails.add(rs.getString(1));
            }
            return emails;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to get whitelisted emails", e);
        }
    }

    // MOTD - Stubs
    @Override
    public Optional<Motd> getCurrentMotd() throws DatabaseException {
        throw new UnsupportedOperationException("MOTD operations not implemented yet");
    }

    @Override
    public Motd saveMotd(Motd motd) throws DatabaseException {
        throw new UnsupportedOperationException("MOTD operations not implemented yet");
    }

    @Override
    public void deleteMotd(long motdId) throws DatabaseException {
        throw new UnsupportedOperationException("MOTD operations not implemented yet");
    }

    // BACKPACK - Stubs
    @Override
    public Optional<Backpack> getUserBackpack(String userId) throws DatabaseException {
        throw new UnsupportedOperationException("Backpack operations not implemented yet");
    }

    @Override
    public Backpack saveBackpack(Backpack backpack) throws DatabaseException {
        throw new UnsupportedOperationException("Backpack operations not implemented yet");
    }

    @Override
    public void deleteBackpack(String userId) throws DatabaseException {
        throw new UnsupportedOperationException("Backpack operations not implemented yet");
    }

    // RGPD COMPLIANCE
    @Override
    public UserDataExport exportUserData(String userId) throws DatabaseException {
        // TODO: Implement complete user data export
        throw new UnsupportedOperationException("User data export not implemented yet");
    }

    @Override
    public void deleteAllUserData(String userId) throws DatabaseException {
        // TODO: Implement cascading delete of all user data
        // Should delete: user, projects, files, comments, backpack, etc.
        LOG.warning("deleteAllUserData not fully implemented - only deleting user record");
        deleteUser(userId);
    }

    @Override
    public List<User> getUsersWithExpiredRetention() throws DatabaseException {
        String sql = "SELECT * FROM users WHERE data_retention_until IS NOT NULL " +
                    "AND data_retention_until < CURRENT_TIMESTAMP";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            List<User> users = new ArrayList<>();
            while (rs.next()) {
                users.add(mapUserFromResultSet(rs));
            }
            return users;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to get users with expired retention", e);
        }
    }

    @Override
    public int executeCleanupTasks() throws DatabaseException {
        int totalCleaned = 0;

        // Clean expired nonces (older than 24 hours)
        long expiryTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
        totalCleaned += deleteExpiredNonces(expiryTime);

        // TODO: Add more cleanup tasks

        LOG.info("Cleanup tasks executed: " + totalCleaned + " records cleaned");
        return totalCleaned;
    }

    // TRANSACTION SUPPORT
    @Override
    public <T> T executeInTransaction(DatabaseTransaction<T> transaction) throws Exception {
        Connection conn = getConnection();
        boolean originalAutoCommit = conn.getAutoCommit();

        try {
            conn.setAutoCommit(false);
            T result = transaction.execute();
            conn.commit();
            return result;
        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(originalAutoCommit);
            conn.close();
        }
    }

    @Override
    public void executeInTransactionVoid(DatabaseTransactionVoid transaction) throws Exception {
        Connection conn = getConnection();
        boolean originalAutoCommit = conn.getAutoCommit();

        try {
            conn.setAutoCommit(false);
            transaction.execute();
            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(originalAutoCommit);
            conn.close();
        }
    }

    // HEALTH CHECK & MONITORING
    @Override
    public boolean isHealthy() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            return rs.next() && rs.getInt(1) == 1;
        } catch (Exception e) {
            LOG.warning("Health check failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, Object> getStatistics() throws DatabaseException {
        Map<String, Object> stats = new HashMap<>();

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Count tables
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users");
            if (rs.next()) stats.put("user_count", rs.getLong(1));

            rs = stmt.executeQuery("SELECT COUNT(*) FROM projects");
            if (rs.next()) stats.put("project_count", rs.getLong(1));

            rs = stmt.executeQuery("SELECT COUNT(*) FROM file_data");
            if (rs.next()) stats.put("file_count", rs.getLong(1));

            // Pool stats
            stats.put("pool_active", dataSource.getHikariPoolMXBean().getActiveConnections());
            stats.put("pool_idle", dataSource.getHikariPoolMXBean().getIdleConnections());
            stats.put("pool_total", dataSource.getHikariPoolMXBean().getTotalConnections());

            return stats;

        } catch (SQLException e) {
            throw new DatabaseException("Failed to get statistics", e);
        }
    }

    @Override
    public String getBackendInfo() {
        return String.format("%s [pool: active=%d, idle=%d, total=%d]",
            backendInfo,
            dataSource.getHikariPoolMXBean().getActiveConnections(),
            dataSource.getHikariPoolMXBean().getIdleConnections(),
            dataSource.getHikariPoolMXBean().getTotalConnections()
        );
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            LOG.info("Closing PostgreSQL connection pool");
            dataSource.close();
        }
    }
}
