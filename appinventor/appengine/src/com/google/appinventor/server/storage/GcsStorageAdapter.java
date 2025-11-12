// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2024 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.server.storage;

import com.google.appengine.tools.cloudstorage.GcsFileMetadata;
import com.google.appengine.tools.cloudstorage.GcsFileOptions;
import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsInputChannel;
import com.google.appengine.tools.cloudstorage.GcsOutputChannel;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.GcsServiceFactory;
import com.google.appengine.tools.cloudstorage.RetryParams;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Google Cloud Storage Adapter
 *
 * This adapter wraps the Google Cloud Storage (GCS) API to implement
 * the CloudStorageService interface. This is the original MIT App Inventor
 * storage backend.
 *
 * This adapter maintains 100% compatibility with the original implementation
 * while allowing easy migration to alternative storage backends.
 *
 * @author MIT App Inventor Team
 */
public class GcsStorageAdapter implements CloudStorageService {
  private static final Logger LOG = Logger.getLogger(GcsStorageAdapter.class.getName());

  private final GcsService gcsService;

  /**
   * Constructor with custom RetryParams
   * @param retryParams retry configuration for GCS operations
   */
  public GcsStorageAdapter(RetryParams retryParams) {
    this.gcsService = GcsServiceFactory.createGcsService(retryParams);
    LOG.log(Level.INFO, "GcsStorageAdapter initialized with Google Cloud Storage backend");
  }

  /**
   * Constructor with default RetryParams
   */
  public GcsStorageAdapter() {
    RetryParams retryParams = new RetryParams.Builder()
        .initialRetryDelayMillis(100)
        .retryMaxAttempts(10)
        .totalRetryPeriodMillis(10000)
        .build();
    this.gcsService = GcsServiceFactory.createGcsService(retryParams);
    LOG.log(Level.INFO, "GcsStorageAdapter initialized with default retry parameters");
  }

  /**
   * FileMetadata wrapper for GCS
   */
  private static class GcsFileMetadataWrapper implements FileMetadata {
    private final GcsFileMetadata metadata;

    public GcsFileMetadataWrapper(GcsFileMetadata metadata) {
      this.metadata = metadata;
    }

    @Override
    public long getLength() {
      return metadata.getLength();
    }

    @Override
    public String getContentType() {
      return metadata.getOptions().getMimeType();
    }

    @Override
    public String getETag() {
      return metadata.getEtag();
    }
  }

  /**
   * OutputChannel wrapper for GCS
   */
  private static class GcsOutputChannelWrapper implements OutputChannel {
    private final GcsOutputChannel channel;

    public GcsOutputChannelWrapper(GcsOutputChannel channel) {
      this.channel = channel;
    }

    @Override
    public void write(ByteBuffer data) throws IOException {
      channel.write(data);
    }

    @Override
    public void close() throws IOException {
      channel.close();
    }
  }

  /**
   * InputChannel wrapper for GCS
   */
  private static class GcsInputChannelWrapper implements InputChannel {
    private final GcsInputChannel channel;

    public GcsInputChannelWrapper(GcsInputChannel channel) {
      this.channel = channel;
    }

    @Override
    public int read(ByteBuffer buffer) throws IOException {
      return channel.read(buffer);
    }

    @Override
    public void close() throws IOException {
      channel.close();
    }
  }

  @Override
  public OutputChannel createOrReplace(String bucketName, String fileName) throws IOException {
    try {
      GcsFilename gcsFilename = new GcsFilename(bucketName, fileName);
      GcsOutputChannel channel = gcsService.createOrReplace(
          gcsFilename,
          GcsFileOptions.getDefaultInstance()
      );
      return new GcsOutputChannelWrapper(channel);
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Failed to create/replace file in GCS: bucket=" +
              bucketName + ", file=" + fileName, e);
      throw e;
    }
  }

  @Override
  public InputChannel openReadChannel(String bucketName, String fileName, long startOffset)
      throws IOException {
    try {
      GcsFilename gcsFilename = new GcsFilename(bucketName, fileName);
      GcsInputChannel channel = gcsService.openReadChannel(gcsFilename, startOffset);
      return new GcsInputChannelWrapper(channel);
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Failed to open read channel in GCS: bucket=" +
              bucketName + ", file=" + fileName, e);
      throw e;
    }
  }

  @Override
  public FileMetadata getMetadata(String bucketName, String fileName) throws IOException {
    try {
      GcsFilename gcsFilename = new GcsFilename(bucketName, fileName);
      GcsFileMetadata metadata = gcsService.getMetadata(gcsFilename);
      if (metadata == null) {
        throw new IOException("File not found: " + fileName + " in bucket: " + bucketName);
      }
      return new GcsFileMetadataWrapper(metadata);
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Failed to get metadata from GCS: bucket=" +
              bucketName + ", file=" + fileName, e);
      throw e;
    }
  }

  @Override
  public void delete(String bucketName, String fileName) throws IOException {
    try {
      GcsFilename gcsFilename = new GcsFilename(bucketName, fileName);
      gcsService.delete(gcsFilename);
    } catch (IOException e) {
      LOG.log(Level.WARNING, "Failed to delete file from GCS: bucket=" +
              bucketName + ", file=" + fileName, e);
      throw e;
    }
  }

  @Override
  public boolean exists(String bucketName, String fileName) {
    try {
      GcsFilename gcsFilename = new GcsFilename(bucketName, fileName);
      GcsFileMetadata metadata = gcsService.getMetadata(gcsFilename);
      return metadata != null;
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  public InputStream getInputStream(String bucketName, String fileName) throws IOException {
    try {
      // Read the entire file into memory and return as ByteArrayInputStream
      FileMetadata metadata = getMetadata(bucketName, fileName);
      int fileSize = (int) metadata.getLength();

      ByteBuffer buffer = ByteBuffer.allocate(fileSize);
      InputChannel channel = openReadChannel(bucketName, fileName, 0);

      try {
        int bytesRead = 0;
        while (bytesRead < fileSize) {
          int read = channel.read(buffer);
          if (read == -1) break;
          bytesRead += read;
        }
      } finally {
        channel.close();
      }

      return new ByteArrayInputStream(buffer.array());
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Failed to get InputStream from GCS: bucket=" +
              bucketName + ", file=" + fileName, e);
      throw e;
    }
  }

  @Override
  public String getBackendInfo() {
    return "Google Cloud Storage (GCS)";
  }
}
