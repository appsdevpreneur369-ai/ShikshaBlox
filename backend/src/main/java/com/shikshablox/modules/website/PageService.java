package com.shikshablox.modules.website;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.entitlement.EntitlementService;
import com.shikshablox.platform.entitlement.UsageMeter;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.TenantContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Pages, per-language content (draft + published), revisions and the custom-page limit. */
@Service
public class PageService implements UsageMeter {

  static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*(/[a-z0-9]+(-[a-z0-9]+)*)*$");
  static final Set<String> RESERVED_SLUGS =
      Set.of(
          "api",
          "admin",
          "h",
          "_next",
          "preview",
          "sitemap.xml",
          "robots.txt",
          "disclosure",
          "enquiry");

  public record PageSummary(
      UUID id,
      String slug,
      String kind,
      String templateKey,
      int sortOrder,
      String language,
      String title,
      String status,
      boolean hasUnpublishedChanges,
      Instant publishedAt,
      Instant updatedAt) {}

  public record PageDetail(
      UUID id,
      String slug,
      String kind,
      String templateKey,
      String language,
      String title,
      JsonNode seo,
      JsonNode draftDocument,
      JsonNode publishedDocument,
      String status,
      Instant publishedAt,
      Instant updatedAt) {}

  public record Revision(
      UUID id,
      String language,
      String title,
      String action,
      String note,
      UUID createdBy,
      Instant createdAt) {}

  /** Raised after a successful publish; the site cache is revalidated after commit. */
  public record PagePublished(UUID organizationId, UUID pageId, String slug) {}

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final ObjectMapper json;
  private final DocumentValidator validator;
  private final AuditService audit;
  private final EntitlementService entitlements;
  private final ApplicationEventPublisher events;

  PageService(
      JdbcClient jdbc,
      TransactionTemplate tx,
      ObjectMapper json,
      DocumentValidator validator,
      AuditService audit,
      @org.springframework.context.annotation.Lazy EntitlementService entitlements,
      ApplicationEventPublisher events) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.json = json;
    this.validator = validator;
    this.audit = audit;
    this.entitlements = entitlements;
    this.events = events;
  }

  // --------------------------------------------------------------------------- usage meter

  @Override
  public String module() {
    return "WEBSITE";
  }

  @Override
  public String limitKey() {
    return "custom_pages";
  }

  /** Only custom pages count; the 14 standard pages are always included. */
  @Override
  public long usage(UUID organizationId) {
    return jdbc.sql("select count(*) from page where organization_id = ? and kind = 'CUSTOM'")
        .param(organizationId)
        .query(Long.class)
        .single();
  }

  // --------------------------------------------------------------------------- queries

  public List<PageSummary> list(String language) {
    return jdbc.sql(
            "select p.id, p.slug, p.kind, p.template_key, p.sort_order, c.language, c.title, c.status,"
                + " (c.published_document is distinct from c.draft_document or c.published_title is distinct from c.title) as changed,"
                + " c.published_at, c.updated_at"
                + " from page p join page_content c on c.page_id = p.id and c.language = ?"
                + " order by p.sort_order, p.slug")
        .param(language)
        .query((rs, i) ->
            new PageSummary(
                rs.getObject("id", UUID.class),
                rs.getString("slug"),
                rs.getString("kind"),
                rs.getString("template_key"),
                rs.getInt("sort_order"),
                rs.getString("language"),
                rs.getString("title"),
                rs.getString("status"),
                rs.getBoolean("changed"),
                ts(rs, "published_at"),
                ts(rs, "updated_at")))
        .list();
  }

  public PageDetail get(UUID id, String language) {
    return find(id, language).orElseThrow(() -> ApiException.notFound("Page"));
  }

  Optional<PageDetail> find(UUID id, String language) {
    return jdbc.sql(
            "select p.id, p.slug, p.kind, p.template_key, c.language, c.title, c.seo::text,"
                + " c.draft_document::text, c.published_document::text, c.status, c.published_at, c.updated_at"
                + " from page p join page_content c on c.page_id = p.id where p.id = ? and c.language = ?")
        .params(id, language)
        .query(this::mapDetail)
        .optional();
  }

  public Optional<UUID> idBySlug(String slug) {
    return jdbc.sql("select id from page where slug = ?").param(slug).query(UUID.class).optional();
  }

  // --------------------------------------------------------------------------- changes

  /** Creates a page (system use for templates; custom pages go through {@link #createCustom}). */
  UUID createPage(
      String slug,
      String kind,
      String templateKey,
      int order,
      String language,
      String title,
      JsonNode document) {
    UUID org = TenantContext.requireOrganizationId();
    UUID id =
        jdbc.sql(
                "insert into page (organization_id, slug, kind, template_key, sort_order) values (?, ?, ?, ?, ?)"
                    + " returning id")
            .params(org, slug, kind, templateKey, order)
            .query(UUID.class)
            .single();
    jdbc.sql(
            "insert into page_content (page_id, organization_id, language, title, draft_document, updated_by)"
                + " values (?, ?, ?, ?, ?::jsonb, ?)")
        .params(id, org, language, title, document.toString(), TenantContext.userId().orElse(null))
        .update();
    return id;
  }

  public PageDetail createCustom(String rawSlug, String title, String language) {
    String slug = rawSlug == null ? "" : rawSlug.trim().toLowerCase().replaceAll("^/+|/+$", "");
    if (!SLUG.matcher(slug).matches()
        || slug.length() > 120
        || RESERVED_SLUGS.contains(slug.split("/")[0])) {
      throw ApiException.badRequest(
          "invalid_slug", "Use lowercase words separated by hyphens, e.g. sports-day.");
    }
    if (title == null || title.isBlank() || title.length() > 160) {
      throw ApiException.badRequest(
          "invalid_title", "Give the page a title (up to 160 characters).");
    }
    entitlements.checkCanAdd("WEBSITE", "custom_pages", 1, "custom pages");
    return tx.execute(s -> {
      if (idBySlug(slug).isPresent()) {
        throw ApiException.conflict("slug_taken", "Another page already uses that address.");
      }
      ObjectNode doc = json.createObjectNode().put("schemaVersion", 1);
      doc.putArray("blocks");
      int order =
          jdbc.sql("select coalesce(max(sort_order), 0) + 10 from page")
              .query(Integer.class)
              .single();
      UUID id = createPage(slug, "CUSTOM", null, order, language, title.trim(), doc);
      audit.record(
          "page.created", "page", id.toString(), null, Map.of("slug", slug, "title", title));
      return get(id, language);
    });
  }

  /** Saves the draft. HTML is sanitised; raw HTML blocks need school administration rights. */
  public PageDetail saveDraft(
      UUID id,
      String language,
      String title,
      JsonNode seo,
      JsonNode document,
      boolean allowRawHtml) {
    JsonNode clean = validator.validateAndSanitise(document, allowRawHtml);
    ObjectNode cleanSeo = cleanSeo(seo);
    if (title == null || title.isBlank() || title.length() > 160) {
      throw ApiException.badRequest(
          "invalid_title", "Give the page a title (up to 160 characters).");
    }
    return tx.execute(s -> {
      PageDetail before = get(id, language);
      checkReusableRefs(clean);
      jdbc.sql(
              "update page_content set title = ?, seo = ?::jsonb, draft_document = ?::jsonb,"
                  + " status = case when status = 'PUBLISHED' then 'PUBLISHED' else 'DRAFT' end,"
                  + " updated_by = ?, updated_at = now() where page_id = ? and language = ?")
          .params(
              title.trim(),
              cleanSeo.toString(),
              clean.toString(),
              TenantContext.userId().orElse(null),
              id,
              language)
          .update();
      jdbc.sql("update page set updated_at = now() where id = ?").param(id).update();
      audit.record(
          "page.draft_saved",
          "page",
          id.toString(),
          Map.of("title", before.title()),
          Map.of("title", title.trim(), "blocks", clean.path("blocks").size()));
      return get(id, language);
    });
  }

  /** Publishes the current draft and records a revision. */
  public PageDetail publish(UUID id, String language, String note) {
    PageDetail result =
        tx.execute(s -> {
          PageDetail d = get(id, language);
          jdbc.sql(
                  "update page_content set published_document = draft_document, published_title = title,"
                      + " published_seo = seo, status = 'PUBLISHED', published_at = now(), updated_at = now()"
                      + " where page_id = ? and language = ?")
              .params(id, language)
              .update();
          jdbc.sql(
                  "insert into page_revision (page_id, organization_id, language, title, seo, document, action, note, created_by)"
                      + " values (?, ?, ?, ?, ?::jsonb, ?::jsonb, 'PUBLISHED', ?, ?)")
              .params(
                  id,
                  TenantContext.requireOrganizationId(),
                  language,
                  d.title(),
                  d.seo().toString(),
                  d.draftDocument().toString(),
                  note,
                  TenantContext.userId().orElse(null))
              .update();
          audit.record(
              "page.published",
              "page",
              id.toString(),
              null,
              Map.of("slug", d.slug(), "language", language));
          events.publishEvent(
              new PagePublished(TenantContext.requireOrganizationId(), id, d.slug()));
          return get(id, language);
        });
    return result;
  }

  /** Takes a page off the site (keeps the draft). */
  public PageDetail unpublish(UUID id, String language) {
    return tx.execute(s -> {
      PageDetail d = get(id, language);
      if ("home".equals(d.templateKey())) {
        throw ApiException.badRequest("home_required", "The home page cannot be unpublished.");
      }
      jdbc.sql(
              "update page_content set published_document = null, published_title = null, published_seo = null,"
                  + " status = 'DRAFT', published_at = null, updated_at = now() where page_id = ? and language = ?")
          .params(id, language)
          .update();
      audit.record("page.unpublished", "page", id.toString(), null, Map.of("slug", d.slug()));
      events.publishEvent(new PagePublished(TenantContext.requireOrganizationId(), id, d.slug()));
      return get(id, language);
    });
  }

  public void deleteCustom(UUID id) {
    tx.executeWithoutResult(s -> {
      String kind =
          jdbc.sql("select kind from page where id = ?")
              .param(id)
              .query(String.class)
              .optional()
              .orElseThrow(() -> ApiException.notFound("Page"));
      if (!"CUSTOM".equals(kind)) {
        throw ApiException.badRequest(
            "standard_page", "Standard pages cannot be deleted; unpublish them instead.");
      }
      jdbc.sql("delete from page where id = ?").param(id).update();
      audit.record("page.deleted", "page", id.toString(), null, null);
      events.publishEvent(new PagePublished(TenantContext.requireOrganizationId(), id, null));
    });
  }

  public List<Revision> revisions(UUID id, String language) {
    return jdbc.sql(
            "select id, language, title, action, note, created_by, created_at from page_revision"
                + " where page_id = ? and language = ? order by created_at desc limit 50")
        .params(id, language)
        .query((rs, i) ->
            new Revision(
                rs.getObject("id", UUID.class),
                rs.getString("language"),
                rs.getString("title"),
                rs.getString("action"),
                rs.getString("note"),
                rs.getObject("created_by", UUID.class),
                rs.getTimestamp("created_at").toInstant()))
        .list();
  }

  // --------------------------------------------------------------------------- helpers

  private void checkReusableRefs(JsonNode doc) {
    for (UUID ref : DocumentValidator.reusableRefs(doc)) {
      boolean exists =
          jdbc.sql("select exists(select 1 from reusable_block where id = ?)")
              .param(ref)
              .query(Boolean.class)
              .single();
      if (!exists) {
        throw ApiException.badRequest(
            "invalid_reusable", "A reusable block on this page no longer exists.");
      }
    }
  }

  ObjectNode cleanSeo(JsonNode seo) {
    ObjectNode out = json.createObjectNode();
    if (seo == null || !seo.isObject()) {
      return out;
    }
    copyText(seo, out, "metaTitle", 70);
    copyText(seo, out, "metaDescription", 200);
    String canonical = seo.path("canonical").asText("");
    if (!canonical.isBlank()) {
      if (!canonical.startsWith("https://")) {
        throw ApiException.badRequest(
            "invalid_canonical", "Canonical URLs must start with https://");
      }
      out.put("canonical", canonical.length() > 500 ? canonical.substring(0, 500) : canonical);
    }
    if (seo.path("noindex").isBoolean()) {
      out.put("noindex", seo.get("noindex").asBoolean());
    }
    String og = seo.path("ogImageId").asText("");
    if (!og.isBlank()) {
      try {
        out.put("ogImageId", UUID.fromString(og).toString());
      } catch (IllegalArgumentException e) {
        throw ApiException.badRequest(
            "invalid_og_image", "Choose a share image from the media library.");
      }
    }
    return out;
  }

  private static void copyText(JsonNode in, ObjectNode out, String key, int max) {
    String v = in.path(key).asText("").trim();
    if (!v.isEmpty()) {
      out.put(key, v.length() > max ? v.substring(0, max) : v);
    }
  }

  private PageDetail mapDetail(ResultSet rs, int i) throws SQLException {
    return new PageDetail(
        rs.getObject("id", UUID.class),
        rs.getString("slug"),
        rs.getString("kind"),
        rs.getString("template_key"),
        rs.getString("language"),
        rs.getString("title"),
        read(rs.getString("seo")),
        read(rs.getString("draft_document")),
        read(rs.getString("published_document")),
        rs.getString("status"),
        ts(rs, "published_at"),
        ts(rs, "updated_at"));
  }

  private JsonNode read(String raw) {
    if (raw == null) {
      return null;
    }
    try {
      return json.readTree(raw);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static Instant ts(ResultSet rs, String col) throws SQLException {
    Timestamp t = rs.getTimestamp(col);
    return t == null ? null : t.toInstant();
  }
}
