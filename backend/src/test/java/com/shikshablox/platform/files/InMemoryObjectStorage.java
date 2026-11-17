package com.shikshablox.platform.files;

import com.shikshablox.platform.files.MediaService.Visibility;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Test double for object storage (picked up by every Spring test via component scanning). */
@Component
@Primary
public class InMemoryObjectStorage implements ObjectStorage {

  public final Map<String, byte[]> objects = new ConcurrentHashMap<>();

  @Override
  public void put(Visibility visibility, String key, byte[] content, String contentType) {
    objects.put(visibility + ":" + key, content);
  }

  @Override
  public byte[] get(Visibility visibility, String key) {
    return objects.get(visibility + ":" + key);
  }

  @Override
  public void delete(Visibility visibility, String key) {
    objects.remove(visibility + ":" + key);
  }

  @Override
  public String publicUrl(String key) {
    return "https://media.test/" + key;
  }

  @Override
  public String signedUrl(String key, Duration ttl, String downloadName) {
    return "https://media.test/signed/" + key + "?ttl=" + ttl.toSeconds();
  }
}
