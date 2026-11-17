package com.shikshablox.platform.files;

import com.shikshablox.platform.files.MediaService.Visibility;
import java.time.Duration;

/** Object storage abstraction (S3-compatible). Public objects are served via CDN-ready URLs. */
public interface ObjectStorage {

  void put(Visibility visibility, String key, byte[] content, String contentType);

  byte[] get(Visibility visibility, String key);

  void delete(Visibility visibility, String key);

  /** Stable public URL (public bucket). */
  String publicUrl(String key);

  /** Short-lived signed download URL (private bucket). */
  String signedUrl(String key, Duration ttl, String downloadName);
}
