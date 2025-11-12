# Phase 4 - Migration Base de Données (Plan Adapté DSI Paris)

**Contexte Spécifique**: Déploiement 100% on-premise à Paris DSI
**Contrainte**: RGPD strict - Données ne peuvent pas être sur infrastructure US
**Infrastructure**: Serveurs Xeon 72 cores, 512GB RAM
**Timeline**: 3-6 mois (déploiement greenfield) ou 12-18 mois (migration données existantes)

---

## Table des Matières

1. [Vue d'Ensemble](#vue-densemble)
2. [Décision Stratégique](#décision-stratégique)
3. [Architecture Technique](#architecture-technique)
4. [Implémentation du SQLAdapter](#implémentation-du-sqladapter)
5. [Migration des Données](#migration-des-données)
6. [Timeline et Phases](#timeline-et-phases)
7. [Tests et Validation](#tests-et-validation)
8. [Déploiement](#déploiement)
9. [Rollback et Contingence](#rollback-et-contingence)
10. [Coûts et Ressources](#coûts-et-ressources)

---

## Vue d'Ensemble

### Contexte de la Décision

Contrairement à l'analyse initiale qui recommandait de **conserver Google Datastore** pour des raisons pragmatiques, **votre contrainte RGPD forte change complètement la donne**.

#### Pourquoi Google Datastore N'est PAS une Option

```
❌ PROBLÈME: Google Datastore = Google Cloud Platform
❌ Google Cloud = Entreprise US soumise au Patriot Act
❌ Données éducatives françaises ≠ Compatible avec législation US
❌ RGPD Article 44-49: Transfert de données hors UE strictement réglementé
```

#### Solution Retenue: PostgreSQL On-Premise

```
✅ PostgreSQL hébergé à Paris DSI
✅ Contrôle total des données (souveraineté)
✅ Pas de transfert hors UE
✅ RGPD compliant par design
✅ Haute performance sur votre infrastructure (72 cores, 512GB RAM)
```

### Objectifs de la Migration

1. **Remplacer Google Datastore** par PostgreSQL pour toutes les entités
2. **Implémenter un adapter SQL** similaire au CloudStorageAdapter (Phase 3)
3. **Garantir la compatibilité** avec le code existant (Objectify ORM)
4. **Maintenir les performances** avec votre infrastructure puissante
5. **Assurer la conformité RGPD** de bout en bout

---

## Décision Stratégique

### Deux Scénarios Possibles

#### **Scénario A: Déploiement Greenfield** (RECOMMANDÉ si possible)

- **Contexte**: Nouvelle instance App Inventor, pas de données existantes à migrer
- **Approche**: Big Bang - Implémentation directe avec PostgreSQL
- **Timeline**: 3-6 mois
- **Complexité**: MOYENNE
- **Coût**: 150K€ - 250K€

**Avantages**:
- Plus simple et plus rapide
- Pas de synchronisation complexe
- Tests plus simples
- Coût réduit

**Inconvénients**:
- Perd les données existantes (si elles existent)
- Pas de période de transition

#### **Scénario B: Migration avec Données Existantes**

- **Contexte**: Instance App Inventor existante avec utilisateurs et projets
- **Approche**: Strangler Pattern avec dual-write puis migration progressive
- **Timeline**: 12-18 mois
- **Complexité**: HAUTE
- **Coût**: 500K€ - 1M€

**Avantages**:
- Conserve toutes les données
- Migration progressive (moins de risque)
- Rollback possible

**Inconvénients**:
- Très complexe
- Coûteux
- Long

### Recommandation

**SI POSSIBLE**: Optez pour le **Scénario A (Greenfield)**

Rationale:
1. App Inventor est un outil pédagogique - les projets d'étudiants sont souvent temporaires
2. Une communication claire permettrait aux utilisateurs d'exporter leurs projets avant migration
3. La complexité du Scénario B ne se justifie que si données critiques existantes
4. Le ratio coût/bénéfice favorise largement Greenfield

**Le reste de ce document suppose le Scénario A**. Si vous devez faire Scénario B, contactez-moi pour plan détaillé.

---

## Architecture Technique

### Stack Technique Complète

```
┌─────────────────────────────────────────────────────────────┐
│                    MIT APP INVENTOR                          │
│                 (Java 17 + Tomcat 10)                        │
└─────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌──────────────┐      ┌──────────────┐     ┌──────────────┐
│  PostgreSQL  │      │    MinIO     │     │    Redis     │
│  (Phase 4)   │      │  (Phase 3)   │     │  (Phase 1)   │
│              │      │              │     │              │
│  Database    │      │  Storage     │     │  Cache +     │
│  16 tables   │      │  Objects     │     │  Sessions    │
└──────────────┘      └──────────────┘     └──────────────┘
   Patroni HA           Distributed          Sentinel HA
   3 nodes              4 nodes              3 nodes
```

### Couches Logicielles

```
┌───────────────────────────────────────────────────────────────┐
│                    Application Layer                          │
│    (OdeServlet, ProjectService, UserService, etc.)            │
└───────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌───────────────────────────────────────────────────────────────┐
│                  Abstraction Layer (NEW)                      │
│                   DatabaseService.java                        │
│          (Interface commune pour tous backends)               │
└───────────────────────────────────────────────────────────────┘
                              │
            ┌─────────────────┴─────────────────┐
            ▼                                   ▼
┌──────────────────────┐          ┌──────────────────────┐
│  ObjectifyAdapter    │          │   PostgreSQLAdapter  │
│  (Google Datastore)  │          │   (PostgreSQL/JDBC)  │
│  [Legacy/Removed]    │          │   [NEW - Phase 4]    │
└──────────────────────┘          └──────────────────────┘
```

### Mapping Objectify → PostgreSQL

```java
// AVANT (Objectify)
@Entity
public class UserData {
    @Id Long id;
    @Index String email;
    String settings;
}

// Query Objectify
ObjectifyService.ofy().load().type(UserData.class).filter("email =", email).first().now();

// APRÈS (PostgreSQL via JDBC)
// Table: users (id BIGINT PRIMARY KEY, email VARCHAR(255) INDEXED, settings JSONB)
String sql = "SELECT * FROM users WHERE email = ?";
PreparedStatement stmt = connection.prepareStatement(sql);
stmt.setString(1, email);
ResultSet rs = stmt.executeQuery();
```

---

## Implémentation du SQLAdapter

### Structure des Fichiers

```
appinventor/appengine/src/com/google/appinventor/server/
├── storage/
│   └── database/              # NEW - Phase 4
│       ├── DatabaseService.java           # Interface commune
│       ├── DatabaseFactory.java           # Factory pattern
│       ├── PostgreSQLAdapter.java         # Implémentation PostgreSQL
│       ├── entities/                      # Entities (POJOs)
│       │   ├── User.java
│       │   ├── Project.java
│       │   ├── FileData.java
│       │   └── ... (16 entities total)
│       ├── repositories/                  # Repositories (DAO pattern)
│       │   ├── UserRepository.java
│       │   ├── ProjectRepository.java
│       │   └── ...
│       └── migrations/                    # Scripts SQL
│           ├── V1__init_schema.sql
│           └── V2__add_indexes.sql
```

### 1. Interface `DatabaseService.java`

```java
package com.google.appinventor.server.storage.database;

import java.util.List;
import java.util.Optional;

/**
 * Abstraction layer for database operations.
 * Replaces direct Objectify/Datastore calls.
 *
 * Phase 4 - Google-Independent Database Layer
 */
public interface DatabaseService {

    // ============================================
    // User Operations
    // ============================================

    /**
     * Get user by ID
     */
    Optional<User> getUserById(String userId);

    /**
     * Get user by email
     */
    Optional<User> getUserByEmail(String email);

    /**
     * Save or update user
     */
    User saveUser(User user);

    /**
     * Delete user (RGPD - right to be forgotten)
     */
    void deleteUser(String userId);

    /**
     * Get all users (admin only)
     */
    List<User> getAllUsers(int limit, int offset);

    // ============================================
    // Project Operations
    // ============================================

    /**
     * Get project by ID
     */
    Optional<Project> getProjectById(long projectId);

    /**
     * Get all projects for a user
     */
    List<Project> getProjectsByUserId(String userId);

    /**
     * Save or update project
     */
    Project saveProject(Project project);

    /**
     * Delete project
     */
    void deleteProject(long projectId);

    /**
     * Search projects by name (for user)
     */
    List<Project> searchProjectsByName(String userId, String namePattern);

    // ============================================
    // File Operations
    // ============================================

    /**
     * Get file by ID
     */
    Optional<FileData> getFileById(long fileId);

    /**
     * Get all files for a project
     */
    List<FileData> getFilesByProjectId(long projectId);

    /**
     * Get specific file by name in project
     */
    Optional<FileData> getFileByName(long projectId, String fileName);

    /**
     * Save or update file
     */
    FileData saveFile(FileData file);

    /**
     * Delete file
     */
    void deleteFile(long fileId);

    /**
     * Delete all files for a project
     */
    void deleteFilesByProjectId(long projectId);

    // ============================================
    // Gallery Operations
    // ============================================

    /**
     * Get gallery app by ID
     */
    Optional<GalleryApp> getGalleryAppById(long galleryId);

    /**
     * Get all published gallery apps
     */
    List<GalleryApp> getPublishedGalleryApps(int limit, int offset);

    /**
     * Search gallery apps
     */
    List<GalleryApp> searchGalleryApps(String query, String category, int limit, int offset);

    /**
     * Save or update gallery app
     */
    GalleryApp saveGalleryApp(GalleryApp app);

    // ============================================
    // Session Operations
    // ============================================

    /**
     * Get user by session ID (for session management)
     */
    Optional<User> getUserBySessionId(String sessionId);

    /**
     * Update user session
     */
    void updateUserSession(String userId, String sessionId, long timestamp);

    /**
     * Clear user session (logout)
     */
    void clearUserSession(String userId);

    // ============================================
    // Nonce Operations (Security)
    // ============================================

    /**
     * Create nonce
     */
    void createNonce(String nonce, long timestamp);

    /**
     * Check if nonce exists and is valid
     */
    boolean isNonceValid(String nonce);

    /**
     * Delete expired nonces (cleanup)
     */
    void deleteExpiredNonces(long expiryTimestamp);

    // ============================================
    // RGPD Compliance Operations
    // ============================================

    /**
     * Get all data for a user (RGPD - right to access)
     */
    UserDataExport exportUserData(String userId);

    /**
     * Delete all user data (RGPD - right to be forgotten)
     */
    void deleteAllUserData(String userId);

    /**
     * Get users with expired data retention
     */
    List<User> getUsersWithExpiredRetention();

    // ============================================
    // Transaction Support
    // ============================================

    /**
     * Execute operation in transaction
     */
    <T> T executeInTransaction(DatabaseTransaction<T> transaction) throws Exception;

    /**
     * Get backend info (for monitoring/debugging)
     */
    String getBackendInfo();
}

/**
 * Functional interface for transactions
 */
@FunctionalInterface
interface DatabaseTransaction<T> {
    T execute() throws Exception;
}
```

### 2. Factory `DatabaseFactory.java`

```java
package com.google.appinventor.server.storage.database;

import com.google.appinventor.server.flags.Flag;
import java.util.logging.Logger;

/**
 * Factory for creating DatabaseService instances.
 * Similar to CloudStorageFactory from Phase 3.
 */
public class DatabaseFactory {

    private static final Logger LOG = Logger.getLogger(DatabaseFactory.class.getName());
    private static volatile DatabaseService instance;

    public enum Backend {
        POSTGRESQL("postgresql"),
        DATASTORE("datastore");  // Legacy, non supporté dans cette version

        private final String configValue;

        Backend(String configValue) {
            this.configValue = configValue;
        }

        public static Backend fromConfig(String config) {
            for (Backend backend : values()) {
                if (backend.configValue.equalsIgnoreCase(config)) {
                    return backend;
                }
            }
            return POSTGRESQL;  // Default
        }
    }

    /**
     * Get DatabaseService instance (Singleton with double-check locking)
     */
    public static DatabaseService getInstance() {
        if (instance == null) {
            synchronized (DatabaseFactory.class) {
                if (instance == null) {
                    instance = createInstance();
                }
            }
        }
        return instance;
    }

    /**
     * Create new DatabaseService instance based on configuration
     */
    private static DatabaseService createInstance() {
        String backendConfig = Flag.createFlag("db.backend", "postgresql").get();
        Backend backend = Backend.fromConfig(backendConfig);

        LOG.info("Initializing DatabaseService with backend: " + backend);

        switch (backend) {
            case POSTGRESQL:
                return createPostgreSQLAdapter();

            case DATASTORE:
                throw new UnsupportedOperationException(
                    "Google Datastore backend is not supported in this version. " +
                    "Please use PostgreSQL for RGPD-compliant deployment."
                );

            default:
                throw new IllegalStateException("Unknown database backend: " + backend);
        }
    }

    /**
     * Create PostgreSQL adapter
     */
    private static PostgreSQLAdapter createPostgreSQLAdapter() {
        String host = Flag.createFlag("db.host", "localhost").get();
        int port = Flag.createFlag("db.port", "5432").get();
        String database = Flag.createFlag("db.name", "appinventor").get();
        String user = Flag.createFlag("db.user", "appinventor_user").get();
        String password = Flag.createFlag("db.password", "").get();

        // Pool configuration
        int poolSize = Flag.createFlag("db.pool.size", "20").get();
        int maxPoolSize = Flag.createFlag("db.pool.max.size", "50").get();

        LOG.info(String.format("Connecting to PostgreSQL: %s:%d/%s (user: %s)",
            host, port, database, user));

        return new PostgreSQLAdapter(host, port, database, user, password, poolSize, maxPoolSize);
    }

    /**
     * For testing: inject custom instance
     */
    public static void setInstanceForTesting(DatabaseService testInstance) {
        instance = testInstance;
    }

    /**
     * Reset instance (for testing)
     */
    public static void resetInstance() {
        if (instance != null) {
            try {
                // Close connections if applicable
                if (instance instanceof AutoCloseable) {
                    ((AutoCloseable) instance).close();
                }
            } catch (Exception e) {
                LOG.warning("Error closing database instance: " + e.getMessage());
            }
            instance = null;
        }
    }
}
```

### 3. Adapter `PostgreSQLAdapter.java` (Squelette)

```java
package com.google.appinventor.server.storage.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * PostgreSQL implementation of DatabaseService.
 * Uses HikariCP for connection pooling.
 */
public class PostgreSQLAdapter implements DatabaseService, AutoCloseable {

    private static final Logger LOG = Logger.getLogger(PostgreSQLAdapter.class.getName());
    private final HikariDataSource dataSource;

    /**
     * Constructor
     */
    public PostgreSQLAdapter(String host, int port, String database,
                             String user, String password,
                             int minPoolSize, int maxPoolSize) {
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

        // Performance optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");

        // Application name (for monitoring)
        config.setPoolName("AppInventor-DB-Pool");
        config.addDataSourceProperty("ApplicationName", "AppInventor");

        this.dataSource = new HikariDataSource(config);

        LOG.info("PostgreSQL connection pool initialized: " + getBackendInfo());
    }

    /**
     * Get connection from pool
     */
    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // ========================================
    // User Operations
    // ========================================

    @Override
    public Optional<User> getUserById(String userId) {
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
            throw new DatabaseException("Failed to get user", e);
        }
    }

    @Override
    public Optional<User> getUserByEmail(String email) {
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
            throw new DatabaseException("Failed to get user", e);
        }
    }

    @Override
    public User saveUser(User user) {
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
                updated_at = CURRENT_TIMESTAMP
            RETURNING *
            """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            int idx = 1;
            stmt.setString(idx++, user.getId());
            stmt.setString(idx++, user.getEmail());
            stmt.setLong(idx++, user.getRepositoryId());
            stmt.setBoolean(idx++, user.isAdmin());
            stmt.setString(idx++, user.getUserLink());
            stmt.setString(idx++, user.getSettingsJson());  // JSON
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
            throw new DatabaseException("Failed to save user", e);
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * Map ResultSet to User entity
     */
    private User mapUserFromResultSet(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getString("id"));
        user.setEmail(rs.getString("email"));
        user.setRepositoryId(rs.getLong("repository_id"));
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

        // ... map other fields

        return user;
    }

    // ========================================
    // Transaction Support
    // ========================================

    @Override
    public <T> T executeInTransaction(DatabaseTransaction<T> transaction) throws Exception {
        Connection conn = getConnection();
        boolean autoCommit = conn.getAutoCommit();

        try {
            conn.setAutoCommit(false);
            T result = transaction.execute();
            conn.commit();
            return result;

        } catch (Exception e) {
            conn.rollback();
            throw e;

        } finally {
            conn.setAutoCommit(autoCommit);
            conn.close();
        }
    }

    // ========================================
    // Info & Cleanup
    // ========================================

    @Override
    public String getBackendInfo() {
        return String.format("PostgreSQL [pool: active=%d, idle=%d, total=%d]",
            dataSource.getHikariPoolMXBean().getActiveConnections(),
            dataSource.getHikariPoolMXBean().getIdleConnections(),
            dataSource.getHikariPoolMXBean().getTotalConnections()
        );
    }

    @Override
    public void close() throws Exception {
        if (dataSource != null && !dataSource.isClosed()) {
            LOG.info("Closing PostgreSQL connection pool...");
            dataSource.close();
        }
    }
}
```

### 4. Entité `User.java` (POJO)

```java
package com.google.appinventor.server.storage.database.entities;

import java.time.Instant;

/**
 * User entity - replaces UserData.java (Objectify)
 */
public class User {
    private String id;
    private String email;
    private long repositoryId;
    private boolean isAdmin;
    private String userLink;
    private String settingsJson;  // JSON storage
    private boolean tosAccepted;
    private String sessionId;
    private Long sessionStartTimestamp;
    private Instant lastLogin;
    private Instant createdAt;
    private Instant updatedAt;

    // RGPD fields
    private Instant consentDate;
    private Instant dataRetentionUntil;

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    // ... (autres getters/setters)

    @Override
    public String toString() {
        return "User{id='" + id + "', email='" + email + "'}";
    }
}
```

---

## Migration des Données

### Option A: Greenfield (Recommandée)

**Étapes**:

1. **Déployer l'infrastructure Kubernetes** (déjà fait avec manifests Phase 4)
2. **Builder les images Docker** avec le nouveau code PostgreSQL
3. **Initialiser la base PostgreSQL** avec le schema (01-init-database.sql)
4. **Déployer App Inventor** directement avec PostgreSQL
5. **Tester** avec utilisateurs pilotes
6. **Ouvrir** en production

**Timeline**: 3-6 mois

### Option B: Migration avec Données (si nécessaire)

Si vous DEVEZ migrer des données existantes depuis Google Datastore:

#### Étape 1: Export depuis Datastore

```bash
# Utiliser gcloud pour exporter Datastore → Cloud Storage
gcloud datastore export gs://your-export-bucket --kinds=UserData,ProjectData,FileData,...

# Télécharger localement (RGPD: données rapatriées en France)
gsutil -m cp -r gs://your-export-bucket ./datastore-export/
```

#### Étape 2: Script de Conversion

```python
#!/usr/bin/env python3
"""
Script de migration Datastore → PostgreSQL
RGPD Compliant - Données traitées localement à Paris DSI
"""

import json
import psycopg2
from google.cloud import datastore
from datetime import datetime

def migrate_users(datastore_export_path, pg_connection):
    """Migrer les utilisateurs"""

    print("Migrating users...")

    with open(f"{datastore_export_path}/UserData.json", 'r') as f:
        users = json.load(f)

    cursor = pg_connection.cursor()

    for user in users:
        # Mapper Datastore → PostgreSQL
        sql = """
            INSERT INTO users (id, email, repository_id, is_admin,
                              tos_accepted, created_at)
            VALUES (%s, %s, %s, %s, %s, %s)
            ON CONFLICT (id) DO NOTHING
        """

        cursor.execute(sql, (
            user['id'],
            user['email'],
            user.get('repositoryId', 0),
            user.get('isAdmin', False),
            user.get('tosAccepted', False),
            datetime.now()
        ))

    pg_connection.commit()
    print(f"Migrated {len(users)} users")

def migrate_projects(datastore_export_path, pg_connection):
    """Migrer les projets"""
    # Similar implementation...
    pass

def migrate_files(datastore_export_path, pg_connection, minio_client):
    """
    Migrer les fichiers
    Stratégie: petits fichiers → PostgreSQL, gros fichiers → MinIO
    """
    # Implementation...
    pass

if __name__ == "__main__":
    # Connection PostgreSQL
    pg_conn = psycopg2.connect(
        host="postgresql.appinventor.svc.cluster.local",
        port=5432,
        database="appinventor",
        user="appinventor_user",
        password="xxx"
    )

    # Migrate
    migrate_users("./datastore-export", pg_conn)
    migrate_projects("./datastore-export", pg_conn)
    migrate_files("./datastore-export", pg_conn, minio_client)

    pg_conn.close()
    print("Migration completed!")
```

---

## Timeline et Phases

### Scénario A: Greenfield (3-6 mois)

```
Mois 1-2: Développement
├── Semaine 1-2: Design détaillé DatabaseService interface
├── Semaine 3-4: Implémentation PostgreSQLAdapter (users, projects)
├── Semaine 5-6: Implémentation entités restantes (files, gallery, etc.)
└── Semaine 7-8: Tests unitaires et d'intégration

Mois 3-4: Intégration et Tests
├── Semaine 9-10: Modification code application (remplacer Objectify)
├── Semaine 11-12: Tests fonctionnels complets
├── Semaine 13-14: Tests de performance et tuning
└── Semaine 15-16: Tests de charge (simuler 1000+ utilisateurs)

Mois 5-6: Déploiement
├── Semaine 17-18: Déploiement environnement de staging
├── Semaine 19-20: Tests utilisateurs pilotes (20-50 utilisateurs)
├── Semaine 21-22: Corrections bugs et ajustements
├── Semaine 23: Déploiement production
└── Semaine 24: Monitoring post-déploiement
```

### Scénario B: Migration (12-18 mois)

Voir DATABASE_MIGRATION_STRATEGY.md pour détails complets.

---

## Tests et Validation

### Tests Unitaires

```java
@Test
public void testSaveAndRetrieveUser() {
    DatabaseService db = DatabaseFactory.getInstance();

    // Create user
    User user = new User();
    user.setId("test-user-123");
    user.setEmail("test@dsi.paris.fr");
    user.setAdmin(false);

    // Save
    User saved = db.saveUser(user);
    assertNotNull(saved);

    // Retrieve
    Optional<User> retrieved = db.getUserById("test-user-123");
    assertTrue(retrieved.isPresent());
    assertEquals("test@dsi.paris.fr", retrieved.get().getEmail());
}
```

### Tests d'Intégration

```java
@Test
public void testCreateProjectWithFiles() {
    DatabaseService db = DatabaseFactory.getInstance();

    // Create user
    User user = createTestUser();
    db.saveUser(user);

    // Create project
    Project project = new Project();
    project.setProjectName("Test Project");
    project.setOwnerId(user.getId());
    Project saved = db.saveProject(project);

    // Add files
    FileData file1 = new FileData();
    file1.setProjectId(saved.getProjectId());
    file1.setFileName("Screen1.scm");
    file1.setContent("#|....".getBytes());
    db.saveFile(file1);

    // Verify
    List<FileData> files = db.getFilesByProjectId(saved.getProjectId());
    assertEquals(1, files.size());
}
```

### Tests de Performance

```java
@Test
public void testConcurrentUserAccess() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(100);

    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
        final int userId = i;
        futures.add(executor.submit(() -> {
            DatabaseService db = DatabaseFactory.getInstance();
            User user = new User();
            user.setId("user-" + userId);
            user.setEmail("user" + userId + "@test.fr");
            db.saveUser(user);
        }));
    }

    // Wait for completion
    for (Future<?> future : futures) {
        future.get(30, TimeUnit.SECONDS);
    }

    // Verify all users created
    DatabaseService db = DatabaseFactory.getInstance();
    List<User> users = db.getAllUsers(1000, 0);
    assertEquals(1000, users.size());
}
```

---

## Déploiement

### Checklist Pré-Déploiement

```markdown
☐ Infrastructure Kubernetes déployée (Phase 1-4)
  ☐ PostgreSQL cluster (3 nodes) ✓
  ☐ MinIO cluster (4 nodes) ✓
  ☐ Redis cluster (3 nodes) ✓

☐ Base de données PostgreSQL
  ☐ Schema initialisé (16 tables) ✓
  ☐ Indexes créés ✓
  ☐ Utilisateur appinventor_user avec permissions ✓
  ☐ Backup configuré ✓

☐ Code Application
  ☐ PostgreSQLAdapter implémenté et testé ✓
  ☐ Tous les appels Objectify remplacés ✓
  ☐ Tests unitaires passent (100%) ✓
  ☐ Tests d'intégration passent (100%) ✓
  ☐ Tests de performance OK ✓

☐ Configuration
  ☐ appengine-web.xml configuré (db.backend=postgresql) ✓
  ☐ Secrets Kubernetes créés ✓
  ☐ ConfigMaps créés ✓

☐ Monitoring
  ☐ Prometheus + Grafana déployés ✓
  ☐ Dashboards créés ✓
  ☐ Alertes configurées ✓

☐ Documentation
  ☐ Guide opérateur créé ✓
  ☐ Procédures de backup documentées ✓
  ☐ Procédures de rollback documentées ✓

☐ Conformité RGPD
  ☐ Données hébergées à Paris DSI uniquement ✓
  ☐ Pas de transfert hors UE ✓
  ☐ Procédures "droit à l'oubli" testées ✓
  ☐ Procédures "export données" testées ✓
```

### Procédure de Déploiement

```bash
# 1. Vérifier l'état du cluster
kubectl get nodes
kubectl get pods -n appinventor

# 2. Appliquer les migrations SQL si nécessaire
kubectl exec -it postgresql-0 -n appinventor -- psql -U postgres -d appinventor -f /migrations/V2__add_indexes.sql

# 3. Builder les nouvelles images Docker
cd appinventor/appengine
docker build -t localhost:5000/appinventor:v2.0.0 .
docker push localhost:5000/appinventor:v2.0.0

# 4. Mettre à jour les deployments (rolling update)
kubectl set image deployment/appinventor appinventor=localhost:5000/appinventor:v2.0.0 -n appinventor

# 5. Surveiller le déploiement
kubectl rollout status deployment/appinventor -n appinventor

# 6. Vérifier les logs
kubectl logs -f deployment/appinventor -n appinventor

# 7. Tests de fumée
curl -k https://appinventor.dsi.paris.fr/_ah/health
curl -k https://appinventor.dsi.paris.fr/_ah/ready
```

---

## Rollback et Contingence

### Plan de Rollback

#### Si Problème Détecté dans les 1ères Heures

```bash
# Rollback Kubernetes automatique
kubectl rollout undo deployment/appinventor -n appinventor

# Revenir à la version précédente
kubectl rollout history deployment/appinventor -n appinventor
kubectl rollout undo deployment/appinventor --to-revision=2 -n appinventor
```

#### Si Corruption de Données

```bash
# 1. Arrêter l'application
kubectl scale deployment/appinventor --replicas=0 -n appinventor

# 2. Restaurer backup PostgreSQL
kubectl exec -it postgresql-0 -n appinventor -- bash
pg_restore -U postgres -d appinventor /backups/appinventor_backup_YYYYMMDD.dump

# 3. Redémarrer
kubectl scale deployment/appinventor --replicas=3 -n appinventor
```

### Indicateurs de Santé

```
🔴 ROLLBACK IMMÉDIAT si:
- Taux d'erreur > 5%
- Latence P95 > 5 secondes
- Impossibilité de créer/sauvegarder projets
- Perte de données détectée

🟡 INVESTIGATION URGENTE si:
- Taux d'erreur > 1%
- Latence P95 > 2 secondes
- Pics d'utilisation CPU/RAM

🟢 NORMAL si:
- Taux d'erreur < 0.1%
- Latence P95 < 1 seconde
- Utilisation CPU < 70%, RAM < 80%
```

---

## Coûts et Ressources

### Scénario A: Greenfield

#### Ressources Humaines

```
Développeur Senior Java (6 mois):     80K€ - 120K€
DevOps/SRE (3 mois):                   40K€ - 60K€
QA/Testeur (2 mois):                   20K€ - 30K€
Chef de Projet (4 mois):               40K€ - 60K€
                                      ─────────────
TOTAL RH:                             180K€ - 270K€
```

#### Infrastructure (Déjà Disponible)

```
✓ Serveurs Xeon 72 cores, 512GB RAM (déjà owned)
✓ Stockage SSD + HDD (déjà available)
✓ Réseau interne DSI (déjà en place)

Coût additionnel infrastructure: 0€
```

#### Logiciels

```
✓ Kubernetes (RKE2): Open-source, 0€
✓ PostgreSQL: Open-source, 0€
✓ MinIO: Open-source, 0€
✓ Redis: Open-source, 0€

Coût licences: 0€
```

#### Formation et Support

```
Formation équipe ops (2 jours):         5K€
Documentation et procédures:           10K€
Support post-déploiement (3 mois):     20K€
                                      ──────
TOTAL Formation/Support:               35K€
```

#### **TOTAL SCÉNARIO A: 215K€ - 305K€**

### Scénario B: Migration (si nécessaire)

```
Développement (x2):                   160K€ - 240K€
DevOps (x2):                           80K€ - 120K€
Migration des données:                 40K€ - 60K€
Tests étendus:                         60K€ - 90K€
Gestion risques:                       80K€ - 120K€
Formation/Support:                     80K€ - 120K€
                                      ─────────────
TOTAL SCÉNARIO B:                     500K€ - 750K€
```

---

## Conclusion et Recommandations

### Résumé

La migration Phase 4 (Base de Données) est **OBLIGATOIRE** dans votre contexte RGPD.

**Approche Recommandée**:
- ✅ Scénario A (Greenfield) si possible
- 🟡 Scénario B (Migration) seulement si données critiques existantes

**Timeline Réaliste**:
- Greenfield: 3-6 mois
- Migration: 12-18 mois

**Budget Réaliste**:
- Greenfield: 215K€ - 305K€
- Migration: 500K€ - 750K€

### Prochaines Étapes Immédiates

1. ✅ **Décision Greenfield vs Migration** (URGENT)
   - Évaluer si données existantes sont critiques
   - Communication utilisateurs si Greenfield

2. **Démarrage Développement** (Semaine 1-2)
   - Recrutement/allocation développeur senior Java
   - Setup environnement de dev
   - Kick-off meeting avec toutes les parties prenantes

3. **Implémentation Interface** (Semaine 3-8)
   - DatabaseService.java
   - PostgreSQLAdapter.java
   - Entities (16 classes)

4. **Tests** (Semaine 9-16)
   - Unitaires, intégration, performance

5. **Déploiement Staging** (Semaine 17-20)
   - Tests utilisateurs pilotes

6. **Production** (Semaine 21-24)
   - Go-live
   - Monitoring intensif

### Contact et Support

Pour toute question sur ce plan de migration:

- **Technical Lead**: [Votre nom]
- **Repo GitHub**: [URL]
- **Documentation**: /appinventor/deployment/

**Bon courage pour cette migration critique ! 🚀**

---

*Document créé le 2025-11-12 pour DSI Paris*
*Phase 4 - Google-Independent Database Migration*
*RGPD Compliant by Design*
