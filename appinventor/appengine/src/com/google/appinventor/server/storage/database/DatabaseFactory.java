package com.google.appinventor.server.storage.database;

import com.google.appinventor.server.storage.database.exceptions.DatabaseException;
import com.google.appinventor.server.flags.Flag;

import java.util.logging.Logger;

/**
 * Factory for creating DatabaseService instances
 * Singleton pattern with lazy initialization
 *
 * Usage:
 *   DatabaseService db = DatabaseFactory.getInstance();
 *   User user = db.getUserById("user123");
 *
 * Configuration via flags:
 *   db.backend=postgresql (default)
 *   db.host=localhost
 *   db.port=5432
 *   db.name=appinventor
 *   db.user=appinventor_user
 *   db.password=secret
 *
 * Phase 4 - RGPD Compliant
 */
public class DatabaseFactory {

    private static final Logger LOG = Logger.getLogger(DatabaseFactory.class.getName());
    private static volatile DatabaseService instance;
    private static final Object lock = new Object();

    public enum Backend {
        POSTGRESQL("postgresql"),
        MYSQL("mysql"),           // Future support
        DATASTORE("datastore");   // Legacy (not supported in RGPD version)

        private final String configValue;

        Backend(String configValue) {
            this.configValue = configValue;
        }

        public static Backend fromConfig(String config) {
            if (config == null || config.trim().isEmpty()) {
                return POSTGRESQL;  // Default
            }

            for (Backend backend : values()) {
                if (backend.configValue.equalsIgnoreCase(config.trim())) {
                    return backend;
                }
            }

            LOG.warning("Unknown database backend: " + config + ", defaulting to PostgreSQL");
            return POSTGRESQL;
        }

        public String getConfigValue() {
            return configValue;
        }
    }

    /**
     * Get DatabaseService instance (Singleton with double-check locking)
     * Thread-safe lazy initialization
     *
     * @return DatabaseService instance
     */
    public static DatabaseService getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = createInstance();
                    LOG.info("DatabaseService initialized: " + instance.getBackendInfo());
                }
            }
        }
        return instance;
    }

    /**
     * Create new DatabaseService instance based on configuration
     * Reads configuration from Flags
     *
     * @return DatabaseService implementation
     */
    private static DatabaseService createInstance() {
        String backendConfig = Flag.createFlag("db.backend", "postgresql").get();
        Backend backend = Backend.fromConfig(backendConfig);

        LOG.info("Creating DatabaseService with backend: " + backend.getConfigValue());

        switch (backend) {
            case POSTGRESQL:
                return createPostgreSQLAdapter();

            case MYSQL:
                throw new UnsupportedOperationException(
                    "MySQL backend not yet implemented. " +
                    "Use PostgreSQL for now."
                );

            case DATASTORE:
                throw new UnsupportedOperationException(
                    "Google Datastore backend is not supported in RGPD-compliant version. " +
                    "Please use PostgreSQL for on-premise deployment."
                );

            default:
                throw new IllegalStateException("Unknown database backend: " + backend);
        }
    }

    /**
     * Create PostgreSQL adapter with connection pooling (HikariCP)
     *
     * Configuration flags:
     *   db.host - Database host (default: localhost)
     *   db.port - Database port (default: 5432)
     *   db.name - Database name (default: appinventor)
     *   db.user - Database user (default: appinventor_user)
     *   db.password - Database password (required!)
     *   db.pool.min - Minimum pool size (default: 10)
     *   db.pool.max - Maximum pool size (default: 50)
     *
     * @return PostgreSQLAdapter instance
     */
    private static DatabaseService createPostgreSQLAdapter() {
        // Read configuration from flags
        String host = Flag.createFlag("db.host", "localhost").get();
        int port = Integer.parseInt(Flag.createFlag("db.port", "5432").get());
        String database = Flag.createFlag("db.name", "appinventor").get();
        String user = Flag.createFlag("db.user", "appinventor_user").get();
        String password = Flag.createFlag("db.password", "").get();

        // Validate password
        if (password == null || password.trim().isEmpty()) {
            LOG.severe("Database password not configured! Set db.password flag.");
            throw new DatabaseException(
                "Database password not configured. " +
                "Please set db.password in appengine-web.xml or environment variable."
            );
        }

        // Connection pool configuration
        int minPoolSize = Integer.parseInt(Flag.createFlag("db.pool.min", "10").get());
        int maxPoolSize = Integer.parseInt(Flag.createFlag("db.pool.max", "50").get());

        LOG.info(String.format(
            "Connecting to PostgreSQL: %s:%d/%s (user: %s, pool: %d-%d)",
            host, port, database, user, minPoolSize, maxPoolSize
        ));

        try {
            return new PostgreSQLAdapter(host, port, database, user, password, minPoolSize, maxPoolSize);
        } catch (Exception e) {
            LOG.severe("Failed to create PostgreSQL adapter: " + e.getMessage());
            throw new DatabaseException("Failed to initialize database connection", e);
        }
    }

    /**
     * Set custom instance (for testing only)
     * Allows injecting mock implementations
     *
     * @param testInstance Custom DatabaseService instance
     */
    public static void setInstanceForTesting(DatabaseService testInstance) {
        synchronized (lock) {
            if (instance != null) {
                LOG.warning("Replacing existing DatabaseService instance (testing mode)");
                try {
                    instance.close();
                } catch (Exception e) {
                    LOG.warning("Error closing previous instance: " + e.getMessage());
                }
            }
            instance = testInstance;
        }
    }

    /**
     * Reset instance (for testing or reload)
     * Closes existing connection and clears instance
     */
    public static void resetInstance() {
        synchronized (lock) {
            if (instance != null) {
                LOG.info("Resetting DatabaseService instance");
                try {
                    instance.close();
                } catch (Exception e) {
                    LOG.warning("Error closing DatabaseService: " + e.getMessage());
                }
                instance = null;
            }
        }
    }

    /**
     * Check if instance is initialized
     *
     * @return true if instance exists
     */
    public static boolean isInitialized() {
        return instance != null;
    }

    /**
     * Get current backend type (if initialized)
     *
     * @return Backend type or null if not initialized
     */
    public static Backend getCurrentBackend() {
        if (instance == null) {
            return null;
        }

        String info = instance.getBackendInfo();
        if (info.toLowerCase().contains("postgresql")) {
            return Backend.POSTGRESQL;
        } else if (info.toLowerCase().contains("mysql")) {
            return Backend.MYSQL;
        }

        return null;
    }

    /**
     * Shutdown hook - ensures clean database connection closure
     * Call this when application is shutting down
     */
    public static void shutdown() {
        LOG.info("DatabaseFactory shutdown initiated");
        resetInstance();
    }

    // Private constructor to prevent instantiation
    private DatabaseFactory() {
        throw new AssertionError("DatabaseFactory cannot be instantiated");
    }
}
