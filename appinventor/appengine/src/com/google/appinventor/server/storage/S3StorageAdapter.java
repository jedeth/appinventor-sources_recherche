// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2024 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.server.storage;

import com.google.appinventor.server.flags.Flag;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * S3 Storage Adapter
 *
 * This adapter implements the CloudStorageService interface using the AWS S3 SDK,
 * enabling MIT App Inventor to run on infrastructure independent of Google Cloud Storage.
 *
 * Compatible with:
 * - Amazon S3 (AWS)
 * - MinIO (open-source S3-compatible storage)
 * - Any S3-compatible object storage service
 *
 * Configuration (via appengine-web.xml or system properties):
 * - storage.backend=s3
 * - s3.endpoint=https://s3.amazonaws.com (or MinIO endpoint)
 * - s3.region=us-east-1
 * - s3.access.key=YOUR_ACCESS_KEY
 * - s3.secret.key=YOUR_SECRET_KEY
 * - s3.path.style=false (true for MinIO, false for AWS S3)
 *
 * @author MIT App Inventor Team
 */
public class S3StorageAdapter implements CloudStorageService {
  private static final Logger LOG = Logger.getLogger(S3StorageAdapter.class.getName());

  private final S3Client s3Client;
  private final String backendName;

  /**
   * FileMetadata implementation for S3
   */
  private static class S3FileMetadata implements FileMetadata {
    private final long length;
    private final String contentType;
    private final String etag;

    public S3FileMetadata(long length, String contentType, String etag) {
      this.length = length;
      this.contentType = contentType;
      this.etag = etag;
    }

    @Override
    public long getLength() {
      return length;
    }

    @Override
    public String getContentType() {
      return contentType != null ? contentType : "application/octet-stream";
    }

    @Override
    public String getETag() {
      return etag;
    }
  }

  /**
   * OutputChannel implementation for S3
   * Buffers data in memory until close() is called, then uploads to S3
   */
  private class S3OutputChannel implements OutputChannel {
    private final String bucketName;
    private final String fileName;
    private final ByteArrayOutputStream buffer;
    private boolean closed = false;

    public S3OutputChannel(String bucketName, String fileName) {
      this.bucketName = bucketName;
      this.fileName = fileName;
      this.buffer = new ByteArrayOutputStream();
    }

    @Override
    public void write(ByteBuffer data) throws IOException {
      if (closed) {
        throw new IOException("Channel is closed");
      }
      // Write ByteBuffer to our internal buffer
      if (data.hasArray()) {
        buffer.write(data.array(), data.arrayOffset() + data.position(), data.remaining());
        data.position(data.limit());
      } else {
        byte[] bytes = new byte[data.remaining()];
        data.get(bytes);
        buffer.write(bytes);
      }
    }

    @Override
    public void close() throws IOException {
      if (closed) {
        return;
      }
      closed = true;

      try {
        byte[] content = buffer.toByteArray();
        PutObjectRequest putRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(fileName)
            .contentLength((long) content.length)
            .build();

        s3Client.putObject(putRequest, RequestBody.fromBytes(content));
        LOG.log(Level.FINE, "Successfully uploaded file to S3: bucket=" +
                bucketName + ", file=" + fileName + ", size=" + content.length);
      } catch (S3Exception e) {
        LOG.log(Level.SEVERE, "Failed to upload file to S3: bucket=" +
                bucketName + ", file=" + fileName, e);
        throw new IOException("S3 upload failed: " + e.getMessage(), e);
      } finally {
        buffer.close();
      }
    }
  }

  /**
   * InputChannel implementation for S3
   * Reads data from S3 into memory for channel-based access
   */
  private class S3InputChannel implements InputChannel {
    private final ByteBuffer buffer;
    private boolean closed = false;

    public S3InputChannel(String bucketName, String fileName, long startOffset) throws IOException {
      try {
        GetObjectRequest getRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(fileName)
            .build();

        byte[] content = s3Client.getObject(getRequest, ResponseTransformer.toBytes()).asByteArray();

        if (startOffset > 0) {
          int offset = (int) Math.min(startOffset, content.length);
          buffer = ByteBuffer.wrap(content, offset, content.length - offset);
        } else {
          buffer = ByteBuffer.wrap(content);
        }
        LOG.log(Level.FINE, "Opened read channel for S3 file: bucket=" +
                bucketName + ", file=" + fileName + ", offset=" + startOffset);
      } catch (S3Exception e) {
        LOG.log(Level.SEVERE, "Failed to open read channel for S3 file: bucket=" +
                bucketName + ", file=" + fileName, e);
        throw new IOException("S3 read failed: " + e.getMessage(), e);
      }
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
      if (closed) {
        throw new IOException("Channel is closed");
      }
      if (!buffer.hasRemaining()) {
        return -1; // End of file
      }

      int bytesToRead = Math.min(buffer.remaining(), dst.remaining());
      int originalLimit = buffer.limit();
      buffer.limit(buffer.position() + bytesToRead);
      dst.put(buffer);
      buffer.limit(originalLimit);
      return bytesToRead;
    }

    @Override
    public void close() throws IOException {
      closed = true;
    }
  }

  /**
   * Constructor - initializes S3 client from configuration
   */
  public S3StorageAdapter() {
    // Read configuration from flags/properties
    String endpoint = Flag.createFlag("s3.endpoint", "").get();
    String region = Flag.createFlag("s3.region", "us-east-1").get();
    String accessKey = Flag.createFlag("s3.access.key", "").get();
    String secretKey = Flag.createFlag("s3.secret.key", "").get();
    boolean pathStyleAccess = Flag.createFlag("s3.path.style", false).get();

    if (accessKey.isEmpty() || secretKey.isEmpty()) {
      throw new IllegalStateException(
          "S3 credentials not configured. Set s3.access.key and s3.secret.key in appengine-web.xml");
    }

    // Build S3 client
    S3ClientBuilder builder = S3Client.builder()
        .region(Region.of(region))
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKey, secretKey)))
        .forcePathStyle(pathStyleAccess);

    // Set custom endpoint if provided (for MinIO or other S3-compatible services)
    if (!endpoint.isEmpty()) {
      builder.endpointOverride(URI.create(endpoint));
      backendName = "S3-compatible (" + endpoint + ")";
      LOG.log(Level.INFO, "S3StorageAdapter initialized with custom endpoint: " + endpoint);
    } else {
      backendName = "Amazon S3 (AWS)";
      LOG.log(Level.INFO, "S3StorageAdapter initialized with AWS S3");
    }

    this.s3Client = builder.build();

    LOG.log(Level.INFO, "S3 Storage Configuration:");
    LOG.log(Level.INFO, "  Region: " + region);
    LOG.log(Level.INFO, "  Endpoint: " + (endpoint.isEmpty() ? "default AWS" : endpoint));
    LOG.log(Level.INFO, "  Path-style access: " + pathStyleAccess);
    LOG.log(Level.INFO, "  Access key: " + maskCredential(accessKey));
  }

  /**
   * Constructor with explicit S3Client (for testing)
   */
  public S3StorageAdapter(S3Client s3Client, String backendName) {
    this.s3Client = s3Client;
    this.backendName = backendName;
    LOG.log(Level.INFO, "S3StorageAdapter initialized with custom S3Client: " + backendName);
  }

  @Override
  public OutputChannel createOrReplace(String bucketName, String fileName) throws IOException {
    return new S3OutputChannel(bucketName, fileName);
  }

  @Override
  public InputChannel openReadChannel(String bucketName, String fileName, long startOffset)
      throws IOException {
    return new S3InputChannel(bucketName, fileName, startOffset);
  }

  @Override
  public FileMetadata getMetadata(String bucketName, String fileName) throws IOException {
    try {
      HeadObjectRequest headRequest = HeadObjectRequest.builder()
          .bucket(bucketName)
          .key(fileName)
          .build();

      HeadObjectResponse response = s3Client.headObject(headRequest);

      return new S3FileMetadata(
          response.contentLength(),
          response.contentType(),
          response.eTag()
      );
    } catch (NoSuchKeyException e) {
      throw new IOException("File not found: " + fileName + " in bucket: " + bucketName, e);
    } catch (S3Exception e) {
      LOG.log(Level.SEVERE, "Failed to get metadata from S3: bucket=" +
              bucketName + ", file=" + fileName, e);
      throw new IOException("S3 metadata retrieval failed: " + e.getMessage(), e);
    }
  }

  @Override
  public void delete(String bucketName, String fileName) throws IOException {
    try {
      DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
          .bucket(bucketName)
          .key(fileName)
          .build();

      s3Client.deleteObject(deleteRequest);
      LOG.log(Level.FINE, "Successfully deleted file from S3: bucket=" +
              bucketName + ", file=" + fileName);
    } catch (S3Exception e) {
      LOG.log(Level.WARNING, "Failed to delete file from S3: bucket=" +
              bucketName + ", file=" + fileName, e);
      throw new IOException("S3 delete failed: " + e.getMessage(), e);
    }
  }

  @Override
  public boolean exists(String bucketName, String fileName) {
    try {
      HeadObjectRequest headRequest = HeadObjectRequest.builder()
          .bucket(bucketName)
          .key(fileName)
          .build();
      s3Client.headObject(headRequest);
      return true;
    } catch (NoSuchKeyException e) {
      return false;
    } catch (S3Exception e) {
      LOG.log(Level.WARNING, "Error checking file existence in S3: bucket=" +
              bucketName + ", file=" + fileName, e);
      return false;
    }
  }

  @Override
  public InputStream getInputStream(String bucketName, String fileName) throws IOException {
    try {
      GetObjectRequest getRequest = GetObjectRequest.builder()
          .bucket(bucketName)
          .key(fileName)
          .build();

      byte[] content = s3Client.getObject(getRequest, ResponseTransformer.toBytes()).asByteArray();
      return new ByteArrayInputStream(content);
    } catch (NoSuchKeyException e) {
      throw new IOException("File not found: " + fileName + " in bucket: " + bucketName, e);
    } catch (S3Exception e) {
      LOG.log(Level.SEVERE, "Failed to get InputStream from S3: bucket=" +
              bucketName + ", file=" + fileName, e);
      throw new IOException("S3 read failed: " + e.getMessage(), e);
    }
  }

  @Override
  public String getBackendInfo() {
    return backendName;
  }

  /**
   * Mask credential for logging (show first 4 and last 4 characters only)
   */
  private static String maskCredential(String credential) {
    if (credential == null || credential.length() <= 8) {
      return "****";
    }
    return credential.substring(0, 4) + "..." + credential.substring(credential.length() - 4);
  }

  /**
   * Close the S3 client (call this on application shutdown)
   */
  public void close() {
    if (s3Client != null) {
      s3Client.close();
      LOG.log(Level.INFO, "S3Client closed");
    }
  }
}
