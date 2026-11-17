package com.shikshablox.modules.website;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shikshablox.platform.entitlement.EntitlementService;
import com.shikshablox.platform.files.MediaService;
import com.shikshablox.platform.files.MediaService.MediaAsset;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.TenantContext;
import com.shikshablox.platform.tenancy.TenantResolver;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Read model for the public website: only published content, only media that may be published
 * (consent rule), resolved to public URLs. Every query runs in the school's tenant context derived
 * from the host name.
 */
@Service
public class PublicSiteService {

  private final TenantResolver resolver;
  private final SiteService sites;
  private final MediaService media;
  private final EntitlementService entitlements;
  private final NavigationService navigation;
  private final JdbcClient jdbc;
  private final ObjectMapper json;

  PublicSiteService(
      TenantResolver resolver,
      SiteService sites,
      MediaService media,
      EntitlementService entitlements,
      NavigationService navigation,
      JdbcClient jdbc,
      ObjectMapper json) {
    this.resolver = resolver;
    this.sites = sites;
    this.media = media;
    this.entitlements = entitlements;
    this.navigation = navigation;
    this.jdbc = jdbc;
    this.json = json;
  }

  /** Site settings, navigation and branding for {@code host}. */
  public Optional<ObjectNode> site(String host) {
    Optional<Organization> org = resolver.resolve(host);
    if (org.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        TenantContext.callInTenant(
            org.get().id(),
            () -> {
              var site = sites.find(org.get().id()).orElse(null);
              ObjectNode out = json.createObjectNode();
              out.put("name", org.get().name());
              out.put("slug", org.get().slug());
              out.put("theme", site == null ? "modern" : site.theme());
              out.set("brand", site == null ? SiteService.defaultBrand(json) : site.brand());
              out.set("profile", site == null ? json.createObjectNode() : site.profile());
              ArrayNode langs = out.putArray("languages");
              (site == null ? java.util.List.of("en") : site.languages()).forEach(langs::add);
              out.put("defaultLanguage", site == null ? "en" : site.defaultLanguage());
              out.put("showBadge", !entitlements.hasFeature("WEBSITE", "remove_branding"));
              Map<String, String> paths = pagePaths();
              out.set("header", resolveMenu(navigation.menu("header"), paths));
              out.set("footer", resolveMenu(navigation.menu("footer"), paths));
              ObjectNode mediaMap = out.putObject("media");
              if (site != null && site.logoMediaId() != null) {
                out.put("logoId", site.logoMediaId().toString());
                addMedia(Set.of(site.logoMediaId()), mediaMap, false);
              }
              return out;
            }));
  }

  /** A published page by path, with its media map; empty when missing or unpublished. */
  public Optional<ObjectNode> page(String host, String path, String language) {
    Optional<Organization> org = resolver.resolve(host);
    if (org.isEmpty()) {
      return Optional.empty();
    }
    String slug = path == null ? "" : path.replaceAll("^/+|/+$", "").toLowerCase();
    return TenantContext.callInTenant(
        org.get().id(),
        () -> {
          String lang =
              language == null || language.isBlank()
                  ? sites.find(org.get().id()).map(SiteService.Site::defaultLanguage).orElse("en")
                  : language;
          record Row(
              UUID id, String templateKey, String title, String seo, String doc, String lang) {}
          Optional<Row> row =
              jdbc.sql(
                      "select p.id, p.template_key, c.published_title, c.published_seo::text, c.published_document::text, c.language"
                          + " from page p join page_content c on c.page_id = p.id"
                          + " where p.slug = ? and c.published_document is not null"
                          + " order by (c.language = ?) desc limit 1")
                  .params(slug, lang)
                  .query((rs, i) ->
                      new Row(
                          rs.getObject(1, UUID.class),
                          rs.getString(2),
                          rs.getString(3),
                          rs.getString(4),
                          rs.getString(5),
                          rs.getString(6)))
                  .optional();
          if (row.isEmpty()) {
            return Optional.<ObjectNode>empty();
          }
          Row r = row.get();
          ObjectNode doc = (ObjectNode) read(r.doc());
          inlineReusable(doc);
          ObjectNode out = json.createObjectNode();
          out.put("id", r.id().toString());
          out.put("slug", slug);
          out.put("templateKey", r.templateKey());
          out.put("language", r.lang());
          out.put("title", r.title());
          out.set("seo", r.seo() == null ? json.createObjectNode() : read(r.seo()));
          out.set("document", doc);
          Set<UUID> ids = new LinkedHashSet<>(DocumentValidator.mediaIds(doc));
          String og = out.path("seo").path("ogImageId").asText("");
          if (!og.isBlank()) {
            ids.add(UUID.fromString(og));
          }
          addMedia(ids, out.putObject("media"), true);
          return Optional.of(out);
        });
  }

  /** All published page paths (sitemap). */
  public Optional<ArrayNode> paths(String host) {
    return resolver
        .resolve(host)
        .map(org ->
            TenantContext.callInTenant(
                org.id(),
                () -> {
                  ArrayNode arr = json.createArrayNode();
                  jdbc.sql(
                          "select p.slug, max(c.published_at) from page p join page_content c on c.page_id = p.id"
                              + " where c.published_document is not null and coalesce((c.published_seo->>'noindex')::boolean, false) = false"
                              + " group by p.slug, p.sort_order order by p.sort_order")
                      .query((rs, i) ->
                          arr.addObject()
                              .put("path", "/" + rs.getString(1))
                              .put("updatedAt", rs.getTimestamp(2).toInstant().toString()))
                      .list();
                  return arr;
                }));
  }

  private void inlineReusable(ObjectNode doc) {
    ArrayNode blocks = (ArrayNode) doc.get("blocks");
    for (int i = 0; i < blocks.size(); i++) {
      JsonNode b = blocks.get(i);
      if ("reusable".equals(b.path("type").asText())) {
        String ref = b.path("props").path("refId").asText();
        Optional<String> inner =
            jdbc.sql("select block::text from reusable_block where id = ?::uuid")
                .param(ref)
                .query(String.class)
                .optional();
        if (inner.isPresent()) {
          ObjectNode ib = (ObjectNode) read(inner.get());
          ib.put("id", b.get("id").asText());
          blocks.set(i, ib);
        } else {
          blocks.remove(i--);
        }
      }
    }
  }

  private Map<String, String> pagePaths() {
    Map<String, String> m = new HashMap<>();
    jdbc.sql(
            "select distinct p.id, p.slug from page p join page_content c on c.page_id = p.id where c.published_document is not null")
        .query((rs, i) -> m.put(rs.getString(1), "/" + rs.getString(2)))
        .list();
    return m;
  }

  /** Replaces page ids by paths and drops items whose page is not published. */
  private ArrayNode resolveMenu(JsonNode items, Map<String, String> paths) {
    ArrayNode out = json.createArrayNode();
    for (JsonNode it : items) {
      ObjectNode o = json.createObjectNode();
      o.set("label", it.get("label"));
      if (it.has("pageId")) {
        String p = paths.get(it.get("pageId").asText());
        if (p == null) {
          continue;
        }
        o.put("href", p);
      } else if (it.has("href")) {
        o.put("href", it.get("href").asText());
      }
      if (it.has("children")) {
        ArrayNode kids = resolveMenu(it.get("children"), paths);
        if (!kids.isEmpty()) {
          o.set("children", kids);
        }
      }
      if (o.has("href") || o.has("children")) {
        out.add(o);
      }
    }
    return out;
  }

  /** Adds publishable media to the map; identifiable media without consent is left out. */
  private void addMedia(Set<UUID> ids, ObjectNode map, boolean enforceConsent) {
    for (UUID id : ids) {
      media
          .find(id)
          .ifPresent(a -> {
            if (a.visibility() != MediaService.Visibility.PUBLIC
                || (enforceConsent && !media.publishable(a))) {
              return;
            }
            map.set(id.toString(), view(a));
          });
    }
  }

  private ObjectNode view(MediaAsset a) {
    ObjectNode m = json.createObjectNode();
    m.put("kind", a.kind().name());
    if (a.altText() != null) {
      m.put("alt", a.altText());
    }
    if (a.caption() != null) {
      m.put("caption", a.caption());
    }
    if (a.kind() == MediaService.Kind.IMAGE) {
      m.put("width", a.width());
      m.put("height", a.height());
      ArrayNode srcset = m.putArray("srcset");
      a.variants()
          .forEach((name, v) ->
              media
                  .publicUrl(a, name)
                  .ifPresent(url -> srcset.addObject().put("url", url).put("width", v.width())));
      media.publicUrl(a, "large").ifPresent(u -> m.put("src", u));
    } else {
      media.publicUrl(a, null).ifPresent(u -> m.put("src", u));
      m.put("filename", a.originalFilename());
      m.put("sizeBytes", a.sizeBytes());
      m.put("contentType", a.contentType());
    }
    return m;
  }

  private JsonNode read(String raw) {
    try {
      return json.readTree(raw);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
