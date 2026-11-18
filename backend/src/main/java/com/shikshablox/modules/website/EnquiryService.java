package com.shikshablox.modules.website;

import com.fasterxml.jackson.databind.JsonNode;
import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.notification.NotificationService;
import com.shikshablox.platform.shared.RateLimiter;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.TenantContext;
import com.shikshablox.platform.tenancy.TenantResolver;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Admissions enquiries from the website form (M4): validated, rate limited and stored per school;
 * school administrators get an email alert and work them from the Studio inbox. Emits {@link
 * EnquiryReceived} for other modules (e.g. a future admissions CRM).
 */
@Service
public class EnquiryService {

  private static final Logger log = LoggerFactory.getLogger(EnquiryService.class);
  private static final Pattern PHONE = Pattern.compile("^[+0-9 \\-]{8,20}$");
  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
  static final Set<String> STATUSES = Set.of("NEW", "CONTACTED", "ENROLLED", "CLOSED", "SPAM");

  /** Published after an enquiry is stored. Carries no personal data. */
  public record EnquiryReceived(UUID organizationId, UUID enquiryId) {}

  public record Enquiry(
      UUID id,
      String name,
      String phone,
      String email,
      String gradeSought,
      String message,
      String sourcePage,
      String status,
      String notes,
      Instant createdAt,
      Instant updatedAt) {}

  private final TenantResolver resolver;
  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final RateLimiter limiter;
  private final AuditService audit;
  private final NotificationService notifications;
  private final SiteService sites;
  private final ApplicationEventPublisher events;
  private final String adminBaseUrl;

  EnquiryService(
      TenantResolver resolver,
      JdbcClient jdbc,
      TransactionTemplate tx,
      RateLimiter limiter,
      AuditService audit,
      NotificationService notifications,
      SiteService sites,
      ApplicationEventPublisher events,
      @Value("${shikshablox.admin-base-url:http://admin.shikshablox.localhost}")
          String adminBaseUrl) {
    this.resolver = resolver;
    this.jdbc = jdbc;
    this.tx = tx;
    this.limiter = limiter;
    this.audit = audit;
    this.notifications = notifications;
    this.sites = sites;
    this.events = events;
    this.adminBaseUrl = adminBaseUrl;
  }

  /** Public submission. Silently accepts (and drops) honeypot hits so bots learn nothing. */
  public void submit(String host, JsonNode in, String clientIp) {
    Organization org = resolver.resolve(host).orElseThrow(() -> ApiException.notFound("School"));
    String ipKey = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
    if (!limiter.tryAcquire("enquiry:" + org.id() + ":" + ipKey, 5, Duration.ofMinutes(10))
        || !limiter.tryAcquire("enquiry:" + org.id(), 200, Duration.ofHours(1))) {
      throw ApiException.tooManyRequests();
    }
    if (in == null || !in.isObject()) {
      throw ApiException.badRequest("invalid_enquiry", "Please fill in the form.");
    }
    if (!in.path("website").asText("").isBlank()) {
      log.info("Enquiry honeypot triggered for {}", org.slug());
      return;
    }
    String name = text(in, "name", 120, true, "Your name");
    String phone = text(in, "phone", 20, true, "Phone");
    if (!PHONE.matcher(phone).matches()) {
      throw ApiException.badRequest("invalid_phone", "Enter a valid phone number.");
    }
    String email = text(in, "email", 200, false, "Email");
    if (email != null && !EMAIL.matcher(email).matches()) {
      throw ApiException.badRequest("invalid_email", "Enter a valid email address.");
    }
    String grade = text(in, "gradeSought", 60, false, "Class");
    String message = text(in, "message", 2000, true, "Message");
    String page = text(in, "pageUrl", 200, false, "Page");
    if (!in.path("consent").asBoolean(false)) {
      throw ApiException.badRequest(
          "consent_required", "Please agree that the school may contact you about this enquiry.");
    }
    TenantContext.callInTenant(
        org.id(),
        () ->
            tx.execute(s -> {
              UUID id =
                  jdbc.sql(
                          "insert into enquiry (organization_id, name, phone, email, grade_sought, message, source_page,"
                              + " consent_given) values (?, ?, ?, ?, ?, ?, ?, true) returning id")
                      .params(org.id(), name, phone, email, grade, message, page)
                      .query(UUID.class)
                      .single();
              audit.record(
                  "enquiry.received",
                  "enquiry",
                  id.toString(),
                  null,
                  Map.of("grade", grade == null ? "" : grade));
              events.publishEvent(new EnquiryReceived(org.id(), id));
              return id;
            }));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  void alert(EnquiryReceived e) {
    Thread.ofVirtual()
        .start(() -> {
          try (var ignored = TenantContext.forTenant(e.organizationId(), null)) {
            Enquiry q = get(e.enquiryId());
            Set<String> to = new LinkedHashSet<>(adminEmails(e.organizationId()));
            sites
                .find(e.organizationId())
                .map(sv -> sv.profile().path("email").asText(""))
                .filter(s -> !s.isBlank())
                .ifPresent(to::add);
            String school =
                jdbc.sql("select name from organization where id = ?")
                    .param(e.organizationId())
                    .query(String.class)
                    .optional()
                    .orElse("your school");
            notifications.email(
                NotificationService.Template.ENQUIRY_RECEIVED,
                new ArrayList<>(to),
                Map.of(
                    "school",
                    school,
                    "studentName",
                    "—",
                    "gradeSought",
                    q.gradeSought() == null ? "Not specified" : q.gradeSought(),
                    "guardianName",
                    q.name(),
                    "link",
                    adminBaseUrl + "/enquiries"));
          } catch (RuntimeException ex) {
            log.warn("Enquiry alert failed: {}", ex.getMessage());
          }
        });
  }

  private List<String> adminEmails(UUID org) {
    return TenantContext.callAsSystem(
        "enquiry alert recipients",
        () ->
            jdbc.sql(
                    "select distinct u.email from role_assignment r join app_user u on u.id = r.user_id"
                        + " where r.organization_id = ? and r.role = 'SCHOOL_ADMIN' and u.status = 'ACTIVE'")
                .param(org)
                .query(String.class)
                .list());
  }

  // --------------------------------------------------------------------------- inbox

  public List<Enquiry> list(String status, String q) {
    StringBuilder sql = new StringBuilder("select * from enquiry where true");
    List<Object> params = new ArrayList<>();
    if (status != null && !status.isBlank()) {
      sql.append(" and status = ?");
      params.add(status);
    }
    if (q != null && !q.isBlank()) {
      sql.append(" and (name ilike ? or phone ilike ? or coalesce(email, '') ilike ?)");
      String like = "%" + q.trim().replace("%", "") + "%";
      params.add(like);
      params.add(like);
      params.add(like);
    }
    sql.append(" order by created_at desc limit 500");
    return jdbc.sql(sql.toString()).params(params).query(EnquiryService::map).list();
  }

  public Map<String, Long> counts() {
    Map<String, Long> out = new java.util.LinkedHashMap<>();
    STATUSES.forEach(s -> out.put(s, 0L));
    jdbc.sql("select status, count(*) from enquiry group by status")
        .query((rs, i) -> out.put(rs.getString(1), rs.getLong(2)))
        .list();
    return out;
  }

  public Enquiry get(UUID id) {
    return jdbc.sql("select * from enquiry where id = ?")
        .param(id)
        .query(EnquiryService::map)
        .optional()
        .orElseThrow(() -> ApiException.notFound("Enquiry"));
  }

  public Enquiry update(UUID id, String status, String notes) {
    if (status != null && !STATUSES.contains(status)) {
      throw ApiException.badRequest("invalid_status", "Unknown status.");
    }
    if (notes != null && notes.length() > 4000) {
      throw ApiException.badRequest("too_long", "Notes are too long.");
    }
    return tx.execute(s -> {
      Enquiry before = get(id);
      jdbc.sql(
              "update enquiry set status = coalesce(?, status), notes = coalesce(?, notes), updated_at = now() where id = ?")
          .params(status, notes, id)
          .update();
      audit.record(
          "enquiry.updated",
          "enquiry",
          id.toString(),
          Map.of("status", before.status()),
          Map.of("status", status == null ? before.status() : status));
      return get(id);
    });
  }

  private static String text(JsonNode in, String key, int max, boolean required, String label) {
    String v = in.path(key).asText("").trim();
    if (v.isEmpty()) {
      if (required) {
        throw ApiException.badRequest("missing_field", label + " is required.");
      }
      return null;
    }
    if (v.length() > max) {
      throw ApiException.badRequest("too_long", label + " is too long.");
    }
    return v;
  }

  private static Enquiry map(ResultSet rs, int i) throws SQLException {
    return new Enquiry(
        rs.getObject("id", UUID.class),
        rs.getString("name"),
        rs.getString("phone"),
        rs.getString("email"),
        rs.getString("grade_sought"),
        rs.getString("message"),
        rs.getString("source_page"),
        rs.getString("status"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }
}
