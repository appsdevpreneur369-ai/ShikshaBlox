package com.shikshablox.modules.website;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shikshablox.platform.tenancy.OrganizationService;
import com.shikshablox.platform.tenancy.TenantContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * On-demand ISR: after content is published (and the transaction committed), asks the public site
 * to drop its cache for every host of the school. Best effort: pages also revalidate on a timer, so
 * a failed call only delays the update.
 */
@Component
class SiteRevalidator {

  private static final Logger log = LoggerFactory.getLogger(SiteRevalidator.class);

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  private final JdbcClient jdbc;
  private final OrganizationService organizations;
  private final ObjectMapper json;
  private final String url;
  private final String secret;
  private final String baseDomain;

  SiteRevalidator(
      JdbcClient jdbc,
      OrganizationService organizations,
      ObjectMapper json,
      @Value("${shikshablox.site.revalidate-url:}") String url,
      @Value("${shikshablox.site.revalidate-secret:}") String secret,
      @Value("${shikshablox.base-domain}") String baseDomain) {
    this.jdbc = jdbc;
    this.organizations = organizations;
    this.json = json;
    this.url = url;
    this.secret = secret;
    this.baseDomain = baseDomain;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  void onPublished(PageService.PagePublished event) {
    if (url.isBlank() || secret.isBlank()) {
      return;
    }
    UUID org = event.organizationId();
    Thread.ofVirtual().start(() -> revalidate(org));
  }

  void revalidate(UUID org) {
    try {
      List<String> hosts = new ArrayList<>();
      try (var ignored = TenantContext.forTenant(org, null)) {
        organizations.findVisible(org).ifPresent(o -> hosts.add(o.slug() + "." + baseDomain));
        hosts.addAll(
            jdbc.sql("select host from site_domain where status = 'VERIFIED'")
                .query(String.class)
                .list());
      }
      List<String> tags = hosts.stream().map(h -> "host:" + h).toList();
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofSeconds(5))
              .header("Content-Type", "application/json")
              .header("X-Revalidate-Secret", secret)
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      json.writeValueAsString(Map.of("tags", tags))))
              .build();
      HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
      if (res.statusCode() >= 300) {
        log.warn("Site revalidation returned {}", res.statusCode());
      }
    } catch (Exception e) {
      log.warn("Site revalidation failed: {}", e.getMessage());
    }
  }
}
