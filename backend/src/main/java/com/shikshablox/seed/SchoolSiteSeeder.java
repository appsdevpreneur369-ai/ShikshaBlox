package com.shikshablox.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.shikshablox.modules.website.ContentService;
import com.shikshablox.modules.website.NavigationService;
import com.shikshablox.modules.website.OnboardingService;
import com.shikshablox.modules.website.PageService;
import com.shikshablox.modules.website.SiteService;
import com.shikshablox.platform.consent.ConsentService;
import com.shikshablox.platform.files.MediaService;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.OrganizationService;
import com.shikshablox.platform.tenancy.TenantContext;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Builds seeded school websites from {@code seed/<slug>/site.json}: logo and photos, design,
 * profile, the 14 standard pages, and starter notices, events, news, albums and FAQs. Each site is
 * built once (skipped when it is already onboarded).
 *
 * <ul>
 *   <li>{@code moriah}: first customer. Photos come from the school's previous public website,
 *       covered by a declaration recorded as "migrated — school to confirm".
 *   <li>{@code greenfield}: Greenfield Demo School, a fictional school shown on shikshablox.com.
 *       Photos are Unsplash-licensed stock.
 * </ul>
 */
@Component
@Order(20)
@ConditionalOnProperty(name = "shikshablox.seed.enabled", havingValue = "true")
class SchoolSiteSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(SchoolSiteSeeder.class);

  /** A seeded site: tenant slug and the photo declaration recorded for it. */
  record SeedSite(String slug, String declarationTitle, String declarationText, String source) {}

  static final java.util.List<SeedSite> SITES =
      java.util.List.of(
          new SeedSite(
              "moriah",
              "Photos migrated from the previous school website (moriaheducation.com) — school to confirm",
              "Photographs of students previously published on the school's own website, carried over to"
                  + " the new website at launch. The school is asked to confirm this declaration or remove"
                  + " any photo.",
              "ShikshaBlox onboarding (pending school confirmation)"),
          new SeedSite(
              "greenfield",
              "Stock photographs (Unsplash License) for the demo school",
              "All photographs on the Greenfield Demo School website are stock images used under the"
                  + " Unsplash License. Greenfield is a fictional school used to demonstrate ShikshaBlox.",
              "ShikshaBlox demo content"));

  private final OrganizationService organizations;
  private final SiteService sites;
  private final OnboardingService onboarding;
  private final PageService pages;
  private final ContentService content;
  private final MediaService media;
  private final ConsentService consents;
  private final NavigationService navigation;
  private final DataSource dataSource;
  private final ObjectMapper json;

  SchoolSiteSeeder(
      OrganizationService organizations,
      SiteService sites,
      OnboardingService onboarding,
      PageService pages,
      ContentService content,
      MediaService media,
      ConsentService consents,
      NavigationService navigation,
      DataSource dataSource,
      ObjectMapper json) {
    this.organizations = organizations;
    this.sites = sites;
    this.onboarding = onboarding;
    this.pages = pages;
    this.content = content;
    this.media = media;
    this.consents = consents;
    this.navigation = navigation;
    this.dataSource = dataSource;
    this.json = json;
  }

  /** Advisory lock key: several instances may start at once (Cloud Run); only one seeds. */
  private static final long LOCK_KEY = 0x5B_4D_4F_52_49_41_48L;

  @Override
  public void run(ApplicationArguments args) {
    try (Connection c = dataSource.getConnection();
        Statement st = c.createStatement()) {
      st.execute("select pg_advisory_lock(" + LOCK_KEY + ")");
      try {
        SITES.forEach(this::seedOnce);
      } finally {
        st.execute("select pg_advisory_unlock(" + LOCK_KEY + ")");
      }
    } catch (SQLException e) {
      log.error("School website seed could not take its lock", e);
    }
  }

  private void seedOnce(SeedSite site) {
    Organization org = organizations.findBySlug(site.slug()).orElse(null);
    if (org == null) {
      return;
    }
    boolean done =
        TenantContext.callInTenant(
            org.id(),
            () -> sites.find(org.id()).map(SiteService.Site::onboardingCompleted).orElse(false));
    if (done) {
      repairLogo(site, org);
      return;
    }
    try {
      String base = "seed/" + site.slug() + "/";
      JsonNode spec = json.readTree(read(base + "site.json"));
      TenantContext.callInTenant(
          org.id(),
          () -> {
            try {
              seed(site, base, spec);
            } catch (IOException e) {
              throw new java.io.UncheckedIOException(e);
            }
            return null;
          });
      log.info("Website seeded for {}", site.slug());
    } catch (Exception e) {
      log.error("Website seed failed for {}", site.slug(), e);
    }
  }

  /** Sites seeded before their logo was listed get it on the next start (idempotent). */
  private void repairLogo(SeedSite site, Organization org) {
    try {
      String base = "seed/" + site.slug() + "/";
      JsonNode spec = json.readTree(read(base + "site.json"));
      String file = spec.path("logo").asText("");
      if (file.isBlank() || !spec.path("images").has(file)) {
        return;
      }
      TenantContext.callInTenant(
          org.id(),
          () -> {
            SiteService.Site s = sites.find(org.id()).orElse(null);
            if (s == null || s.logoMediaId() != null) {
              return null;
            }
            try {
              MediaService.MediaAsset a =
                  media.upload(
                      new MediaService.Upload(
                          file,
                          read(base + file),
                          MediaService.Visibility.PUBLIC,
                          spec.path("images").path(file).path("alt").asText(null),
                          null,
                          "Website launch",
                          false));
              sites.updateDesign(s.theme(), s.brand(), a.id());
              log.info("Logo added for {}", site.slug());
            } catch (IOException e) {
              throw new java.io.UncheckedIOException(e);
            }
            return null;
          });
    } catch (Exception e) {
      log.warn("Logo repair skipped for {}: {}", site.slug(), e.getMessage());
    }
  }

  private void seed(SeedSite site, String base, JsonNode spec) throws IOException {
    consents.create(
        ConsentService.Kind.SCHOOL_DECLARATION,
        site.declarationTitle(),
        site.declarationText(),
        site.source(),
        LocalDate.now(),
        null);

    Map<String, UUID> images = new HashMap<>();
    Iterator<Map.Entry<String, JsonNode>> it = spec.path("images").fields();
    while (it.hasNext()) {
      var e = it.next();
      String file = e.getKey();
      MediaService.MediaAsset a =
          media.upload(
              new MediaService.Upload(
                  file,
                  read(base + file),
                  MediaService.Visibility.PUBLIC,
                  e.getValue().path("alt").asText(null),
                  null,
                  "Website launch",
                  e.getValue().path("students").asBoolean(false)));
      images.put(file, a.id());
    }

    onboarding.complete(
        new OnboardingService.OnboardingInput(
            spec.path("theme").asText("modern"),
            spec.path("brand"),
            spec.path("profile"),
            images.get(spec.path("logo").asText())));

    Map<String, UUID> pageIds = new HashMap<>();
    pages.list("en").forEach(p -> pageIds.put(p.templateKey(), p.id()));
    Iterator<Map.Entry<String, JsonNode>> pi = spec.path("pages").fields();
    while (pi.hasNext()) {
      var e = pi.next();
      UUID id = pageIds.get(e.getKey());
      if (id == null) {
        continue;
      }
      PageService.PageDetail d = pages.get(id, "en");
      String title = spec.path("titles").path(e.getKey()).asText(d.title());
      publishPage(id, title, d.seo(), e.getValue(), images);
    }

    // Extra pages (e.g. founders & leadership), keyed by slug.
    Iterator<Map.Entry<String, JsonNode>> cp = spec.path("customPages").fields();
    while (cp.hasNext()) {
      var e = cp.next();
      JsonNode page = e.getValue();
      PageService.PageDetail d = pages.createCustom(e.getKey(), page.path("title").asText(), "en");
      JsonNode seo = page.has("seo") ? page.get("seo") : d.seo();
      publishPage(d.id(), d.title(), seo, page.path("blocks"), images);
      pageIds.put(e.getKey(), d.id());
    }

    // Header menu: items name a standard page key or a custom page slug.
    if (spec.path("menu").path("header").isArray()) {
      navigation.saveMenu("header", menuItems(spec.path("menu").path("header"), pageIds));
    }

    LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
    Iterator<Map.Entry<String, JsonNode>> ci = spec.path("content").fields();
    while (ci.hasNext()) {
      var e = ci.next();
      for (JsonNode item : e.getValue()) {
        ObjectNode data = item.deepCopy();
        String date = data.path("date").asText("");
        if (date.matches("^[+-]\\d+$")) {
          data.put("date", today.plusDays(Integer.parseInt(date)).toString());
        }
        if (data.has("image")) {
          UUID img = images.get(data.remove("image").asText());
          if (img != null) {
            data.put("imageId", img.toString());
          }
        }
        if (data.has("images")) {
          ArrayNode ids = data.putArray("imageIds");
          data.remove("images")
              .forEach(n -> {
                UUID img = images.get(n.asText());
                if (img != null) {
                  ids.add(img.toString());
                }
              });
        }
        content.create(e.getKey(), data, true);
      }
    }
  }

  private void publishPage(
      UUID id, String title, JsonNode seo, JsonNode blocks, Map<String, UUID> images) {
    ObjectNode doc = json.createObjectNode().put("schemaVersion", 1);
    doc.set("blocks", resolve(blocks.deepCopy(), images));
    pages.saveDraft(id, "en", title, seo, doc, true);
    pages.publish(id, "en", "Launch content");
  }

  private ArrayNode menuItems(JsonNode items, Map<String, UUID> pageIds) {
    ArrayNode out = json.createArrayNode();
    for (JsonNode it : items) {
      ObjectNode o = out.addObject();
      o.putObject("label").put("en", it.path("label").asText());
      UUID page = pageIds.get(it.path("page").asText());
      if (page == null) {
        throw new IllegalStateException("Unknown menu page " + it.path("page").asText());
      }
      o.put("pageId", page.toString());
      if (it.path("children").isArray()) {
        o.set("children", menuItems(it.get("children"), pageIds));
      }
    }
    return out;
  }

  /** Replaces "@img:<file>" strings anywhere in the tree with the uploaded media id. */
  private JsonNode resolve(JsonNode node, Map<String, UUID> images) {
    if (node.isTextual() && node.asText().startsWith("@img:")) {
      UUID id = images.get(node.asText().substring(5));
      if (id == null) {
        throw new IllegalStateException("Unknown seed image " + node.asText());
      }
      return TextNode.valueOf(id.toString());
    }
    if (node.isObject()) {
      ObjectNode o = (ObjectNode) node;
      o.fieldNames().forEachRemaining(k -> o.set(k, resolve(o.get(k), images)));
    } else if (node.isArray()) {
      ArrayNode a = (ArrayNode) node;
      for (int i = 0; i < a.size(); i++) {
        a.set(i, resolve(a.get(i), images));
      }
    }
    return node;
  }

  private static byte[] read(String name) throws IOException {
    try (InputStream in = new ClassPathResource(name).getInputStream()) {
      return in.readAllBytes();
    }
  }
}
