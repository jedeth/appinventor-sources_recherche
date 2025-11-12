// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2024 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.server.storage;

import com.google.appengine.tools.cloudstorage.RetryParams;
import com.google.appinventor.server.flags.Flag;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cloud Storage Factory
 *
 * Factory class for creating CloudStorageService instances based on configuration.
 * This enables switching between different storage backends (GCS, S3, MinIO) without
 * code changes, just by modifying appengine-web.xml configuration.
 *
 * Configuration (in appengine-web.xml):
 *
 * For Google Cloud Storage (default, original MIT implementation):
 *   <property name="storage.backend" value="gcs" />
 *   <property name="gcs.bucket" value="your-gcs-bucket" />
 *
 * For Amazon S3:
 *   <property name="storage.backend" value="s3" />
 *   <property name="s3.endpoint" value="" />  <!-- empty for AWS S3 -->
 *   <property name="s3.region" value="us-east-1" />
 *   <property name="s3.access.key" value="YOUR_ACCESS_KEY" />
 *   <property name="s3.secret.key" value="YOUR_SECRET_KEY" />
 *   <property name="s3.path.style" value="false" />
 *
 * For MinIO (S3-compatible):
 *   <property name="storage.backend" value="s3" />
 *   <property name="s3.endpoint" value="https://minio.example.com:9000" />
 *   <property name="s3.region" value="us-east-1" />
 *   <property name="s3.access.key" value="minioadmin" />
 *   <property name="s3.secret.key" value="minioadmin" />
 *   <property name="s3.path.style" value="true" />  <!-- MinIO uses path-style -->
 *
 * Compatibility with MIT App Inventor:
 * - If storage.backend is not set or set to "gcs", uses Google Cloud Storage (original)
 * - This ensures backward compatibility with existing MIT deployments
 * - Original GCS code paths are preserved for easy rollback
 *
 * @author MIT App Inventor Team
 */
public class CloudStorageFactory {
  private static final Logger LOG = Logger.getLogger(CloudStorageFactory.class.getName());

  // Singleton instance (lazy initialization)
  private static CloudStorageService instance = null;
  private static final Object lock = new Object();

  /**
   * Storage backend types
   */
  public enum Backend {
    GCS("gcs", "Google Cloud Storage"),
    S3("s3", "S3-compatible Storage (AWS S3, MinIO, etc.)");

    private final String configValue;
    private final String displayName;

    Backend(String configValue, String displayName) {
      this.configValue = configValue;
      this.displayName = displayName;
    }

    public String getConfigValue() {
      return configValue;
    }

    public String getDisplayName() {
      return displayName;
    }

    public static Backend fromConfig(String configValue) {
      for (Backend backend : values()) {
        if (backend.configValue.equalsIgnoreCase(configValue)) {
          return backend;
        }
      }
      // Default to GCS for backward compatibility
      return GCS;
    }
  }

  /**
   * Get or create the CloudStorageService instance (singleton pattern)
   *
   * This method is thread-safe and will create only one instance per application lifetime.
   *
   * @return CloudStorageService instance configured based on appengine-web.xml
   */
  public static CloudStorageService getInstance() {
    if (instance == null) {
      synchronized (lock) {
        if (instance == null) {
          instance = createStorageService();
        }
      }
    }
    return instance;
  }

  /**
   * Create a new CloudStorageService based on configuration
   *
   * @return CloudStorageService instance
   */
  private static CloudStorageService createStorageService() {
    // Read backend configuration
    // Default to "gcs" for backward compatibility with original MIT implementation
    String backendConfig = Flag.createFlag("storage.backend", "gcs").get();
    Backend backend = Backend.fromConfig(backendConfig);

    LOG.log(Level.INFO, "=".repeat(70));
    LOG.log(Level.INFO, "Initializing Cloud Storage Service");
    LOG.log(Level.INFO, "Backend configuration: " + backendConfig);
    LOG.log(Level.INFO, "Selected backend: " + backend.getDisplayName());

    CloudStorageService service;

    switch (backend) {
      case S3:
        LOG.log(Level.INFO, "Creating S3StorageAdapter...");
        try {
          service = new S3StorageAdapter();
          LOG.log(Level.INFO, "✓ S3StorageAdapter initialized successfully");
          LOG.log(Level.INFO, "  Backend info: " + service.getBackendInfo());
        } catch (Exception e) {
          LOG.log(Level.SEVERE, "✗ Failed to initialize S3StorageAdapter", e);
          LOG.log(Level.WARNING, "Falling back to GCS (original implementation)");
          service = createGcsService();
        }
        break;

      case GCS:
      default:
        LOG.log(Level.INFO, "Creating GcsStorageAdapter (original MIT implementation)...");
        service = createGcsService();
        LOG.log(Level.INFO, "✓ GcsStorageAdapter initialized successfully");
        break;
    }

    LOG.log(Level.INFO, "Cloud Storage Service ready: " + service.getBackendInfo());
    LOG.log(Level.INFO, "=".repeat(70));

    return service;
  }

  /**
   * Create GCS storage service with retry parameters
   */
  private static CloudStorageService createGcsService() {
    RetryParams retryParams = new RetryParams.Builder()
        .initialRetryDelayMillis(100)
        .retryMaxAttempts(10)
        .totalRetryPeriodMillis(10000)
        .build();
    return new GcsStorageAdapter(retryParams);
  }

  /**
   * Reset the singleton instance (for testing purposes only)
   * DO NOT use in production code!
   */
  public static void resetInstance() {
    synchronized (lock) {
      if (instance != null) {
        LOG.log(Level.WARNING, "Resetting CloudStorageFactory instance (testing only!)");
        instance = null;
      }
    }
  }

  /**
   * Set a custom instance (for testing purposes only)
   * DO NOT use in production code!
   */
  public static void setInstance(CloudStorageService customInstance) {
    synchronized (lock) {
      LOG.log(Level.WARNING, "Setting custom CloudStorageFactory instance (testing only!)");
      instance = customInstance;
    }
  }

  /**
   * Get information about the current configuration without creating an instance
   *
   * @return configuration information as a string
   */
  public static String getConfigurationInfo() {
    String backendConfig = Flag.createFlag("storage.backend", "gcs").get();
    Backend backend = Backend.fromConfig(backendConfig);

    StringBuilder info = new StringBuilder();
    info.append("Cloud Storage Configuration:\n");
    info.append("  Backend: ").append(backend.getDisplayName()).append("\n");
    info.append("  Config value: ").append(backendConfig).append("\n");

    if (backend == Backend.S3) {
      String endpoint = Flag.createFlag("s3.endpoint", "").get();
      String region = Flag.createFlag("s3.region", "us-east-1").get();
      boolean pathStyle = Flag.createFlag("s3.path.style", false).get();

      info.append("  S3 Endpoint: ").append(endpoint.isEmpty() ? "AWS default" : endpoint).append("\n");
      info.append("  S3 Region: ").append(region).append("\n");
      info.append("  Path-style access: ").append(pathStyle).append("\n");
    } else {
      String gcsBucket = Flag.createFlag("gcs.bucket", "").get();
      info.append("  GCS Bucket: ").append(gcsBucket.isEmpty() ? "default" : gcsBucket).append("\n");
    }

    return info.toString();
  }

  // Private constructor to prevent instantiation
  private CloudStorageFactory() {
  }
}
