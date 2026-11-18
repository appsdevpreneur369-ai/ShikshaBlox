package com.shikshablox.modules.website;

import com.fasterxml.jackson.databind.JsonNode;
import com.shikshablox.platform.shared.RateLimiter;
import com.shikshablox.platform.shared.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public, read-only site data for the website renderer (cached by the site's ISR). */
@RestController
@RequestMapping("/api/public/v1/site")
@Tag(name = "Public site")
class PublicSiteController {

  private final PublicSiteService site;
  private final PublicFeedService feeds;
  private final RateLimiter limiter;

  PublicSiteController(PublicSiteService site, PublicFeedService feeds, RateLimiter limiter) {
    this.site = site;
    this.feeds = feeds;
    this.limiter = limiter;
  }

  @GetMapping("/feeds/{type}")
  @Operation(operationId = "publicFeed", summary = "Published items of a content type")
  ResponseEntity<JsonNode> feed(
      @PathVariable String type,
      @RequestParam String host,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(defaultValue = "all") String show,
      @RequestParam(required = false) String department) {
    limit(host);
    return cached(
        feeds
            .feed(host, type, limit, show, department)
            .orElseThrow(() -> ApiException.notFound("Feed")));
  }

  @GetMapping("/feeds/{type}/{slug}")
  @Operation(operationId = "publicFeedItem", summary = "One published item (news, event, album)")
  ResponseEntity<JsonNode> feedItem(
      @PathVariable String type, @PathVariable String slug, @RequestParam String host) {
    limit(host);
    return cached(feeds.item(host, type, slug).orElseThrow(() -> ApiException.notFound("Item")));
  }

  @GetMapping
  @Operation(
      operationId = "publicSite",
      summary = "Site settings, navigation and branding for a host")
  ResponseEntity<JsonNode> site(@RequestParam String host) {
    limit(host);
    return cached(site.site(host).orElseThrow(() -> ApiException.notFound("School")));
  }

  @GetMapping("/page")
  @Operation(operationId = "publicPage", summary = "A published page with its media")
  ResponseEntity<JsonNode> page(
      @RequestParam String host,
      @RequestParam(defaultValue = "") String path,
      @RequestParam(required = false) String lang) {
    limit(host);
    return cached(site.page(host, path, lang).orElseThrow(() -> ApiException.notFound("Page")));
  }

  @GetMapping("/paths")
  @Operation(operationId = "publicPaths", summary = "Published page paths (sitemap)")
  ResponseEntity<JsonNode> paths(@RequestParam String host) {
    limit(host);
    return cached(site.paths(host).orElseThrow(() -> ApiException.notFound("School")));
  }

  private void limit(String host) {
    // The site server caches responses, so this only bites on abuse or cache misses floods.
    if (!limiter.tryAcquire("public-site:" + host, 600, Duration.ofMinutes(1))) {
      throw ApiException.tooManyRequests();
    }
  }

  private static ResponseEntity<JsonNode> cached(JsonNode body) {
    return ResponseEntity.ok()
        // The site's ISR cache (revalidated on publish) is the cache; HTTP caching here would
        // serve stale JSON to a fresh render right after publishing.
        .cacheControl(CacheControl.noCache())
        .body(body);
  }
}
