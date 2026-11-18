package com.shikshablox.modules.website;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.notification.NotificationService;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.TenantContext;
import com.shikshablox.platform.tenancy.TenantResolver;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * M5 publishing: review workflow (editor submits → approver publishes or sends back), scheduled
 * publishing, rollback to an earlier revision, and signed preview links for drafts. Also the
 * disclosure "last reviewed" tracking with reminder emails (M4).
 */
@Service
public class PublishingService {

  private static final Logger log = LoggerFactory.getLogger(PublishingService.class);
  static final Duration PREVIEW_TTL = Duration.ofHours(2);
  static final Duration DISCLOSURE_REVIEW_EVERY = Duration.ofDays(180);

  private final PageService pages;
  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final AuditService audit;
  private final NotificationService notifications;
  private final TenantResolver resolver;
  private final PublicSiteService publicSite;
  private final ObjectMapper json;
  private final byte[] key;
  private final String adminBaseUrl;

  PublishingService(
      PageService pages,
      JdbcClient jdbc,
      TransactionTemplate tx,
      AuditService audit,
      NotificationService notifications,
      TenantResolver resolver,
      PublicSiteService publicSite,
      ObjectMapper json,
      @Value("${shikshablox.security.jwt-secret}") String secret,
      @Value("${shikshablox.admin-base-url:http://admin.shikshablox.localhost}")
          String adminBaseUrl) {
    this.pages = pages;
    this.jdbc = jdbc;
    this.tx = tx;
    this.audit = audit;
    this.notifications = notifications;
    this.resolver = resolver;
    this.publicSite = publicSite;
    this.json = json;
    this.key = ("preview:" + secret).getBytes(StandardCharsets.UTF_8);
    this.adminBaseUrl = adminBaseUrl;
  }

  // --------------------------------------------------------------------------- workflow

  /** Editor asks for approval; approvers are emailed. */
  public PageService.PageDetail submitForReview(UUID id, String lang, String note) {
    PageService.PageDetail d =
        tx.execute(s -> {
          PageService.PageDetail p = pages.get(id, lang);
          jdbc.sql(
                  "update page_content set status = 'IN_REVIEW', review_note = ?, submitted_by = ?,"
                      + " scheduled_at = null, updated_at = now() where page_id = ? and language = ?")
              .params(clip(note), TenantContext.userId().orElse(null), id, lang)
              .update();
          audit.record(
              "page.submitted",
              "page",
              id.toString(),
              null,
              Map.of("note", note == null ? "" : note));
          return pages.get(id, lang);
        });
    UUID org = TenantContext.requireOrganizationId();
    List<String> approvers =
        TenantContext.callAsSystem(
            "approval recipients",
            () ->
                jdbc.sql(
                        "select distinct u.email from role_assignment r join app_user u on u.id = r.user_id"
                            + " where r.organization_id = ? and r.role in ('SCHOOL_ADMIN', 'CONTENT_APPROVER')"
                            + " and u.status = 'ACTIVE'")
                    .param(org)
                    .query(String.class)
                    .list());
    String author =
        TenantContext.userId()
            .flatMap(u ->
                TenantContext.callAsSystem(
                    "author name",
                    () ->
                        jdbc.sql("select display_name from app_user where id = ?")
                            .param(u)
                            .query(String.class)
                            .optional()))
            .orElse("An editor");
    notifications.email(
        NotificationService.Template.APPROVAL_REQUESTED,
        approvers,
        Map.of(
            "title",
            d.title(),
            "author",
            author,
            "school",
            schoolName(org),
            "link",
            adminBaseUrl + "/pages/" + id));
    return d;
  }

  /** Approver sends the page back to the editor with a note. */
  public PageService.PageDetail reject(UUID id, String lang, String note) {
    return tx.execute(s -> {
      PageService.PageDetail p = pages.get(id, lang);
      if (!"IN_REVIEW".equals(p.status()) && !"SCHEDULED".equals(p.status())) {
        throw ApiException.badRequest("not_in_review", "This page is not waiting for review.");
      }
      jdbc.sql(
              "update page_content set status = case when published_document is null then 'DRAFT' else 'PUBLISHED' end,"
                  + " review_note = ?, scheduled_at = null, updated_at = now() where page_id = ? and language = ?")
          .params(clip(note), id, lang)
          .update();
      audit.record(
          "page.changes_requested",
          "page",
          id.toString(),
          null,
          Map.of("note", note == null ? "" : note));
      return pages.get(id, lang);
    });
  }

  /** Publishes the current draft at a future time. */
  public PageService.PageDetail schedule(UUID id, String lang, Instant at) {
    if (at == null || at.isBefore(Instant.now().plusSeconds(60))) {
      throw ApiException.badRequest("invalid_time", "Choose a time at least a minute from now.");
    }
    if (at.isAfter(Instant.now().plus(Duration.ofDays(365)))) {
      throw ApiException.badRequest("invalid_time", "Schedule within the next year.");
    }
    return tx.execute(s -> {
      pages.get(id, lang);
      jdbc.sql(
              "update page_content set status = 'SCHEDULED', scheduled_at = ?, updated_at = now()"
                  + " where page_id = ? and language = ?")
          .params(Timestamp.from(at), id, lang)
          .update();
      audit.record("page.scheduled", "page", id.toString(), null, Map.of("at", at.toString()));
      return pages.get(id, lang);
    });
  }

  public PageService.PageDetail cancelSchedule(UUID id, String lang) {
    return tx.execute(s -> {
      jdbc.sql(
              "update page_content set status = case when published_document is null then 'DRAFT' else 'PUBLISHED' end,"
                  + " scheduled_at = null, updated_at = now() where page_id = ? and language = ? and status = 'SCHEDULED'")
          .params(id, lang)
          .update();
      audit.record("page.schedule_cancelled", "page", id.toString(), null, null);
      return pages.get(id, lang);
    });
  }

  /** Restores an earlier published revision as the draft and publishes it. */
  public PageService.PageDetail rollback(UUID id, String lang, UUID revisionId) {
    record Rev(String title, String seo, String doc) {}
    Rev rev =
        jdbc.sql(
                "select title, seo::text, document::text from page_revision where id = ? and page_id = ? and language = ?")
            .params(revisionId, id, lang)
            .query((rs, i) -> new Rev(rs.getString(1), rs.getString(2), rs.getString(3)))
            .optional()
            .orElseThrow(() -> ApiException.notFound("Revision"));
    tx.executeWithoutResult(s -> {
      jdbc.sql(
              "update page_content set title = ?, seo = ?::jsonb, draft_document = ?::jsonb, updated_at = now()"
                  + " where page_id = ? and language = ?")
          .params(rev.title(), rev.seo(), rev.doc(), id, lang)
          .update();
      audit.record(
          "page.rolled_back",
          "page",
          id.toString(),
          null,
          Map.of("revision", revisionId.toString()));
    });
    return pages.publish(id, lang, "Restored an earlier version");
  }

  /** Every minute: publish pages whose scheduled time has come. */
  @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
  void publishDue() {
    record Due(UUID org, UUID page, String lang) {}
    List<Due> due =
        TenantContext.callAsSystem(
            "scheduled publishing",
            () ->
                jdbc.sql(
                        "select organization_id, page_id, language from page_content"
                            + " where status = 'SCHEDULED' and scheduled_at <= now() limit 100")
                    .query((rs, i) ->
                        new Due(
                            rs.getObject(1, UUID.class),
                            rs.getObject(2, UUID.class),
                            rs.getString(3)))
                    .list());
    for (Due d : due) {
      try (var ignored = TenantContext.forTenant(d.org(), null)) {
        pages.publish(d.page(), d.lang(), "Scheduled publish");
        jdbc.sql("update page_content set scheduled_at = null where page_id = ? and language = ?")
            .params(d.page(), d.lang())
            .update();
      } catch (RuntimeException e) {
        log.warn("Scheduled publish failed for page {}: {}", d.page(), e.getMessage());
      }
    }
  }

  // --------------------------------------------------------------------------- preview

  /** A signed, short-lived token that lets the public site render one page's draft. */
  public String previewToken(UUID pageId, String lang) {
    pages.get(pageId, lang);
    String payload =
        TenantContext.requireOrganizationId()
            + "|"
            + pageId
            + "|"
            + lang
            + "|"
            + Instant.now().plus(PREVIEW_TTL).getEpochSecond();
    String p64 =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    return p64 + "~" + sign(p64);
  }

  /** The draft page for a valid preview token, shaped like the public page response. */
  Optional<ObjectNode> preview(String host, String token) {
    String[] parts = token == null ? new String[0] : token.split("~");
    if (parts.length != 2
        || !MessageDigest.isEqual(
            sign(parts[0]).getBytes(StandardCharsets.UTF_8),
            parts[1].getBytes(StandardCharsets.UTF_8))) {
      return Optional.empty();
    }
    String[] f =
        new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).split("\\|");
    if (f.length != 4 || Instant.now().getEpochSecond() > Long.parseLong(f[3])) {
      return Optional.empty();
    }
    UUID org = UUID.fromString(f[0]);
    Optional<Organization> hostOrg = resolver.resolve(host);
    if (hostOrg.isEmpty() || !hostOrg.get().id().equals(org)) {
      return Optional.empty();
    }
    return Optional.of(
        TenantContext.callInTenant(
            org,
            () -> {
              PageService.PageDetail d = pages.get(UUID.fromString(f[1]), f[2]);
              ObjectNode out = json.createObjectNode();
              out.put("id", d.id().toString());
              out.put("slug", d.slug());
              out.put("templateKey", d.templateKey());
              out.put("language", d.language());
              out.put("title", d.title());
              out.put("preview", true);
              out.set("seo", d.seo());
              JsonNode doc = d.draftDocument().deepCopy();
              out.set("document", doc);
              publicSite.addMedia(
                  new java.util.LinkedHashSet<>(DocumentValidator.mediaIds(doc)),
                  out.putObject("media"),
                  true);
              return out;
            }));
  }

  private String sign(String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  // --------------------------------------------------------------------------- disclosures

  public Instant markDisclosureReviewed() {
    UUID org = TenantContext.requireOrganizationId();
    tx.executeWithoutResult(s -> {
      jdbc.sql(
              "update site set disclosure_reviewed_at = now(), disclosure_reminder_sent_at = null where organization_id = ?")
          .param(org)
          .update();
      audit.record("disclosure.reviewed", "site", org.toString(), null, null);
    });
    return disclosureReviewedAt().orElse(null);
  }

  public Optional<Instant> disclosureReviewedAt() {
    return jdbc.sql("select disclosure_reviewed_at from site where organization_id = ?")
        .param(TenantContext.requireOrganizationId())
        .query((rs, i) -> Optional.ofNullable(rs.getTimestamp(1)).map(Timestamp::toInstant))
        .optional()
        .flatMap(o -> o);
  }

  /** Daily: remind school admins when disclosures haven't been reviewed for six months. */
  @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Kolkata")
  void disclosureReminders() {
    record Due(UUID org, Instant reviewed) {}
    List<Due> due =
        TenantContext.callAsSystem(
            "disclosure reminders",
            () ->
                jdbc.sql(
                        "select s.organization_id, s.disclosure_reviewed_at from site s"
                            + " where exists (select 1 from content_item c where c.organization_id = s.organization_id"
                            + "   and c.type = 'disclosures' and c.status = 'PUBLISHED')"
                            + " and coalesce(s.disclosure_reviewed_at, s.created_at) < now() - make_interval(days => ?)"
                            + " and (s.disclosure_reminder_sent_at is null or s.disclosure_reminder_sent_at < now() - interval '7 days')")
                    .param((int) DISCLOSURE_REVIEW_EVERY.toDays())
                    .query((rs, i) ->
                        new Due(
                            rs.getObject(1, UUID.class),
                            Optional.ofNullable(rs.getTimestamp(2))
                                .map(Timestamp::toInstant)
                                .orElse(null)))
                    .list());
    for (Due d : due) {
      List<String> admins =
          TenantContext.callAsSystem(
              "disclosure reminder recipients",
              () ->
                  jdbc.sql(
                          "select distinct u.email from role_assignment r join app_user u on u.id = r.user_id"
                              + " where r.organization_id = ? and r.role = 'SCHOOL_ADMIN' and u.status = 'ACTIVE'")
                      .param(d.org())
                      .query(String.class)
                      .list());
      notifications.email(
          NotificationService.Template.DISCLOSURE_REVIEW_DUE,
          admins,
          Map.of(
              "school",
              schoolName(d.org()),
              "lastReviewed",
              d.reviewed() == null ? "never" : d.reviewed().toString().substring(0, 10),
              "link",
              adminBaseUrl + "/content/disclosures"));
      TenantContext.runAsSystem(
          "disclosure reminder sent",
          () ->
              jdbc.sql(
                      "update site set disclosure_reminder_sent_at = now() where organization_id = ?")
                  .param(d.org())
                  .update());
    }
  }

  private String schoolName(UUID org) {
    return TenantContext.callAsSystem(
        "school name",
        () ->
            jdbc.sql("select name from organization where id = ?")
                .param(org)
                .query(String.class)
                .optional()
                .orElse("your school"));
  }

  private static String clip(String s) {
    if (s == null || s.isBlank()) {
      return null;
    }
    String t = s.trim();
    return t.length() > 500 ? t.substring(0, 500) : t;
  }
}
