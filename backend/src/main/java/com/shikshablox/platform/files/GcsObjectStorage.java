package com.shikshablox.platform.files;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.shikshablox.platform.files.MediaService.Visibility;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Google Cloud Storage via the workload's own identity (Application Default Credentials), for
 * deployments where HMAC/service-account keys are disallowed. Signed URLs are signed through the
 * IAM Credentials API, so the runtime service account needs Token Creator on itself.
 */
@Component
@ConditionalOnProperty(name = "shikshablox.storage.provider", havingValue = "gcs")
class GcsObjectStorage implements ObjectStorage {

  private final Storage storage;
  private final String privateBucket;
  private final String publicBucket;
  private final String publicBaseUrl;

  GcsObjectStorage(
      @Value("${shikshablox.storage.private-bucket}") String privateBucket,
      @Value("${shikshablox.storage.public-bucket}") String publicBucket,
      @Value("${shikshablox.storage.public-base-url}") String publicBaseUrl) {
    this.storage = StorageOptions.getDefaultInstance().getService();
    this.privateBucket = privateBucket;
    this.publicBucket = publicBucket;
    this.publicBaseUrl =
        publicBaseUrl.endsWith("/")
            ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
            : publicBaseUrl;
  }

  private String bucket(Visibility v) {
    return v == Visibility.PUBLIC ? publicBucket : privateBucket;
  }

  @Override
  public void put(Visibility visibility, String key, byte[] content, String contentType) {
    BlobInfo info =
        BlobInfo.newBuilder(BlobId.of(bucket(visibility), key))
            .setContentType(contentType)
            .setCacheControl(
                visibility == Visibility.PUBLIC
                    ? "public, max-age=31536000, immutable"
                    : "private, no-store")
            .build();
    storage.create(info, content);
  }

  @Override
  public byte[] get(Visibility visibility, String key) {
    return storage.readAllBytes(BlobId.of(bucket(visibility), key));
  }

  @Override
  public void delete(Visibility visibility, String key) {
    storage.delete(BlobId.of(bucket(visibility), key));
  }

  @Override
  public String publicUrl(String key) {
    return publicBaseUrl + "/" + key;
  }

  @Override
  public String signedUrl(String key, Duration ttl, String downloadName) {
    String safeName =
        downloadName == null ? "download" : downloadName.replaceAll("[^A-Za-z0-9._-]", "_");
    return storage
        .signUrl(
            BlobInfo.newBuilder(BlobId.of(privateBucket, key)).build(),
            ttl.toSeconds(),
            TimeUnit.SECONDS,
            Storage.SignUrlOption.withV4Signature(),
            Storage.SignUrlOption.withQueryParams(
                Map.of(
                    "response-content-disposition", "attachment; filename=\"" + safeName + "\"")))
        .toString();
  }
}
