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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public, read-only site data for the website renderer (cached by the site's ISR). */
@RestController
@RequestMapping("/api/public/v1/site")
@Tag(name = "Public site")
class PublicSiteController {

  private final PublicSiteService site;
  private final RateLimiter limiter;

  PublicSiteController(PublicSiteService site, RateLimiter limiter) {
    this.site = site;
    this.limiter = limiter;
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
        .cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic())
        .body(body);
  }
}
