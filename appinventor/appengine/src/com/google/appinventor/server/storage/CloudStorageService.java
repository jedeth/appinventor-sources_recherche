// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2024 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.server.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Cloud Storage Service Interface
 *
 * This interface provides an abstraction layer for cloud storage backends,
 * enabling MIT App Inventor to run on infrastructure independent of Google
 * Cloud Storage while maintaining full compatibility with the original implementation.
 *
 * Supported backends:
 * - Google Cloud Storage (GCS) - Original MIT implementation
 * - Amazon S3 / MinIO / S3-compatible - For Google-free deployment
 *
 * Configuration is done via appengine-web.xml:
 * <property name="storage.backend" value="gcs" />  <!-- or "s3" -->
 *
 * @author MIT App Inventor Team
 */
public interface CloudStorageService {

  /**
   * File metadata returned by getMetadata()
   */
  public interface FileMetadata {
    /**
     * Get the size of the file in bytes
     * @return file size in bytes
     */
    long getLength();

    /**
     * Get the content type of the file
     * @return content type (MIME type)
     */
    String getContentType();

    /**
     * Get the ETag of the file (for version control)
     * @return ETag string
     */
    String getETag();
  }

  /**
   * Output channel for writing data to cloud storage
   */
  public interface OutputChannel extends AutoCloseable {
    /**
     * Write data to the storage
     * @param data the data buffer to write
     * @throws IOException if write fails
     */
    void write(java.nio.ByteBuffer data) throws IOException;

    /**
     * Close the channel and finalize the write
     * @throws IOException if close fails
     */
    @Override
    void close() throws IOException;
  }

  /**
   * Input channel for reading data from cloud storage
   */
  public interface InputChannel extends AutoCloseable {
    /**
     * Read data from storage into the buffer
     * @param buffer the buffer to read into
     * @return number of bytes read, or -1 if end of file
     * @throws IOException if read fails
     */
    int read(java.nio.ByteBuffer buffer) throws IOException;

    /**
     * Close the channel
     * @throws IOException if close fails
     */
    @Override
    void close() throws IOException;
  }

  /**
   * Create a new file or replace an existing file in cloud storage
   *
   * @param bucketName name of the storage bucket
   * @param fileName name/path of the file within the bucket
   * @return an OutputChannel for writing data to the file
   * @throws IOException if operation fails
   */
  OutputChannel createOrReplace(String bucketName, String fileName) throws IOException;

  /**
   * Open a read channel to an existing file
   *
   * @param bucketName name of the storage bucket
   * @param fileName name/path of the file within the bucket
   * @param startOffset offset in bytes from which to start reading (0 for beginning)
   * @return an InputChannel for reading data from the file
   * @throws IOException if file doesn't exist or operation fails
   */
  InputChannel openReadChannel(String bucketName, String fileName, long startOffset) throws IOException;

  /**
   * Get metadata about a file without reading its content
   *
   * @param bucketName name of the storage bucket
   * @param fileName name/path of the file within the bucket
   * @return FileMetadata containing information about the file
   * @throws IOException if file doesn't exist or operation fails
   */
  FileMetadata getMetadata(String bucketName, String fileName) throws IOException;

  /**
   * Delete a file from cloud storage
   *
   * @param bucketName name of the storage bucket
   * @param fileName name/path of the file within the bucket
   * @throws IOException if file doesn't exist or operation fails
   */
  void delete(String bucketName, String fileName) throws IOException;

  /**
   * Check if a file exists in cloud storage
   *
   * @param bucketName name of the storage bucket
   * @param fileName name/path of the file within the bucket
   * @return true if the file exists, false otherwise
   */
  boolean exists(String bucketName, String fileName);

  /**
   * Get a simple InputStream for reading a file (convenience method)
   * This is useful for simple read operations without needing channels
   *
   * @param bucketName name of the storage bucket
   * @param fileName name/path of the file within the bucket
   * @return InputStream for reading the file
   * @throws IOException if file doesn't exist or operation fails
   */
  InputStream getInputStream(String bucketName, String fileName) throws IOException;

  /**
   * Get information about the backend being used
   * @return String describing the backend (e.g., "Google Cloud Storage", "Amazon S3", "MinIO")
   */
  String getBackendInfo();
}
