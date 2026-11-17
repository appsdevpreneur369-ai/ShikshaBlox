package com.shikshablox.platform.files;

import com.shikshablox.platform.files.MediaService.Visibility;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * S3-compatible storage: MinIO locally, AWS S3 / Cloudflare R2 / Google Cloud Storage
 * (interoperability HMAC keys) when deployed. Path-style addressing and "checksum only when
 * required" keep it compatible with non-AWS stores.
 */
@Component
class S3ObjectStorage implements ObjectStorage, DisposableBean {

  private final S3Client s3;
  private final S3Presigner presigner;
  private final String privateBucket;
  private final String publicBucket;
  private final String publicBaseUrl;

  S3ObjectStorage(
      @Value("${shikshablox.storage.endpoint}") String endpoint,
      @Value("${shikshablox.storage.region:us-east-1}") String region,
      @Value("${shikshablox.storage.access-key}") String accessKey,
      @Value("${shikshablox.storage.secret-key}") String secretKey,
      @Value("${shikshablox.storage.private-bucket}") String privateBucket,
      @Value("${shikshablox.storage.public-bucket}") String publicBucket,
      @Value("${shikshablox.storage.public-base-url}") String publicBaseUrl,
      @Value("${shikshablox.storage.signing-endpoint:}") String signingEndpoint) {
    var creds = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    var s3Config = S3Configuration.builder().pathStyleAccessEnabled(true).build();
    this.s3 =
        S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .credentialsProvider(creds)
            .serviceConfiguration(s3Config)
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
            .httpClient(UrlConnectionHttpClient.create())
            .build();
    // Signed URLs must use the host the browser can reach (may differ from the internal endpoint).
    this.presigner =
        S3Presigner.builder()
            .endpointOverride(
                URI.create(
                    signingEndpoint == null || signingEndpoint.isBlank()
                        ? endpoint
                        : signingEndpoint))
            .region(Region.of(region))
            .credentialsProvider(creds)
            .serviceConfiguration(s3Config)
            .build();
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
    s3.putObject(
        PutObjectRequest.builder()
            .bucket(bucket(visibility))
            .key(key)
            .contentType(contentType)
            .cacheControl(
                visibility == Visibility.PUBLIC
                    ? "public, max-age=31536000, immutable"
                    : "private, no-store")
            .build(),
        RequestBody.fromBytes(content));
  }

  @Override
  public byte[] get(Visibility visibility, String key) {
    return s3.getObjectAsBytes(
            GetObjectRequest.builder().bucket(bucket(visibility)).key(key).build())
        .asByteArray();
  }

  @Override
  public void delete(Visibility visibility, String key) {
    s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket(visibility)).key(key).build());
  }

  @Override
  public String publicUrl(String key) {
    return publicBaseUrl + "/" + key;
  }

  @Override
  public String signedUrl(String key, Duration ttl, String downloadName) {
    String safeName =
        downloadName == null ? "download" : downloadName.replaceAll("[^A-Za-z0-9._-]", "_");
    return presigner
        .presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(
                    GetObjectRequest.builder()
                        .bucket(privateBucket)
                        .key(key)
                        .responseContentDisposition("attachment; filename=\"" + safeName + "\"")
                        .build())
                .build())
        .url()
        .toString();
  }

  @Override
  public void destroy() {
    s3.close();
    presigner.close();
  }
}
