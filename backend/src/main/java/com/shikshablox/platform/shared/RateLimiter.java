package com.shikshablox.platform.shared;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fixed-window rate limiter for public and authentication endpoints.
 *
 * <p>In-memory, so limits apply per backend instance. That is sufficient for a single instance;
 * TODO(scale-out): back this with Redis (INCR + EXPIRE) when running more than one instance.
 */
@Component
public class RateLimiter {

  private record Window(long startMillis, long windowMillis, int count) {}

  private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
  private final Clock clock;

  public RateLimiter() {
    this(Clock.systemUTC());
  }

  RateLimiter(Clock clock) {
    this.clock = clock;
  }

  /** Records one hit for {@code key}; returns false when the limit for the window is exceeded. */
  public boolean tryAcquire(String key, int limit, Duration window) {
    long now = clock.millis();
    long windowMillis = window.toMillis();
    Window updated =
        windows.compute(
            key,
            (k, w) ->
                w == null || now - w.startMillis() >= w.windowMillis()
                    ? new Window(now, windowMillis, 1)
                    : new Window(w.startMillis(), w.windowMillis(), w.count() + 1));
    return updated.count() <= limit;
  }

  /** Clears a key, e.g. after a successful login. */
  public void reset(String key) {
    windows.remove(key);
  }

  @Scheduled(fixedDelay = 300_000)
  void evictExpired() {
    long now = clock.millis();
    windows
        .entrySet()
        .removeIf(e -> now - e.getValue().startMillis() >= e.getValue().windowMillis());
  }
}
