package com.shikshablox.modules.website;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.shikshablox.modules.website.SiteTemplates.SchoolInfo;
import com.shikshablox.modules.website.SiteTemplates.StandardPage;
import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.tenancy.OrganizationService;
import com.shikshablox.platform.tenancy.TenantContext;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The onboarding wizard's final step: saves the school's details and design, then generates a
 * ready, published site (14 standard pages + header/footer menus) from the templates.
 */
@Service
public class OnboardingService {

  public record OnboardingInput(String theme, JsonNode brand, JsonNode profile, UUID logoMediaId) {}

  private final SiteService sites;
  private final PageService pages;
  private final NavigationService navigation;
  private final SiteTemplates templates;
  private final OrganizationService organizations;
  private final AuditService audit;
  private final JdbcClient jdbc;
  private final TransactionTemplate tx;

  OnboardingService(
      SiteService sites,
      PageService pages,
      NavigationService navigation,
      SiteTemplates templates,
      OrganizationService organizations,
      AuditService audit,
      JdbcClient jdbc,
      TransactionTemplate tx) {
    this.sites = sites;
    this.pages = pages;
    this.navigation = navigation;
    this.templates = templates;
    this.organizations = organizations;
    this.audit = audit;
    this.jdbc = jdbc;
    this.tx = tx;
  }

  public SiteService.Site complete(OnboardingInput in) {
    sites.current();
    sites.updateProfile(in.profile());
    sites.updateDesign(in.theme(), in.brand(), in.logoMediaId());
    tx.executeWithoutResult(s -> {
      ensureStandardPages(true);
      sites.markOnboarded();
      audit.record(
          "site.onboarded",
          "site",
          TenantContext.requireOrganizationId().toString(),
          null,
          Map.of("theme", in.theme()));
    });
    pages
        .list("en")
        .forEach(p -> {
          if (p.publishedAt() == null) {
            pages.publish(p.id(), "en", "Initial site from onboarding");
          }
        });
    return sites.current();
  }

  /**
   * Creates any missing standard pages and, if there are none yet, the default menus. Safe to call
   * repeatedly. Must run inside a transaction with a tenant context.
   */
  public Map<String, UUID> ensureStandardPages(boolean createMenus) {
    var site = sites.current();
    var org = organizations.current().orElseThrow();
    JsonNode p = site.profile();
    SchoolInfo info =
        new SchoolInfo(
            org.name(),
            p.path("address").path("city").asText(""),
            p.path("board").asText(""),
            p.path("tagline").asText(""));
    Map<String, UUID> ids = new HashMap<>();
    for (StandardPage sp : SiteTemplates.STANDARD_PAGES) {
      UUID id =
          jdbc.sql("select id from page where template_key = ? and kind = 'STANDARD'")
              .param(sp.key())
              .query(UUID.class)
              .optional()
              .orElseGet(() ->
                  pages.createPage(
                      sp.slug(),
                      "STANDARD",
                      sp.key(),
                      sp.order(),
                      site.defaultLanguage(),
                      sp.title(),
                      templates.document(sp.key(), info)));
      ids.put(sp.key(), id);
    }
    if (createMenus) {
      for (Map.Entry<String, ArrayNode> m : templates.menus(ids).entrySet()) {
        if (navigation.menu(m.getKey()).isEmpty()) {
          navigation.writeMenu(m.getKey(), m.getValue());
        }
      }
    }
    return ids;
  }
}
