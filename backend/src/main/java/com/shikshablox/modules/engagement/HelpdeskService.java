package com.shikshablox.modules.engagement;

import static com.shikshablox.modules.engagement.Inputs.optional;
import static com.shikshablox.modules.engagement.Inputs.required;

import com.fasterxml.jackson.databind.JsonNode;
import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.entitlement.EntitlementService;
import com.shikshablox.platform.notification.NotificationService;
import com.shikshablox.platform.shared.RateLimiter;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.TenantContext;
import com.shikshablox.platform.tenancy.TenantResolver;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Parent helpdesk. Parents raise requests on the website and follow them with the ticket number and
 * their phone; staff assign, reply (publicly or as internal notes) and resolve them within an SLA
 * set by priority. Phone and walk-in requests can be logged by staff.
 */
@Service
public class HelpdeskService {

  private static final Logger log = LoggerFactory.getLogger(HelpdeskService.class);
  static final String MODULE = "ENGAGEMENT";
  static final Set<String> CATEGORIES =
      Set.of(
          "FEES",
          "TRANSPORT",
          "ACADEMICS",
          "ADMISSIONS",
          "FACILITIES",
          "CERTIFICATES",
          "COMPLAINT",
          "OTHER");
  static final Set<String> STATUSES =
      Set.of("OPEN", "IN_PROGRESS", "WAITING", "RESOLVED", "CLOSED");
  static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH");
  static final Set<String> SOURCES = Set.of("WEBSITE", "PHONE", "WALK_IN", "EMAIL", "OTHER");

  /** Resolution targets by priority. */
  static Duration sla(String priority) {
    return switch (priority) {
      case "HIGH" -> Duration.ofHours(24);
      case "LOW" -> Duration.ofDays(7);
      default -> Duration.ofDays(3);
    };
  }

  public record Ticket(
      UUID id,
      String ticketNo,
      String category,
      String subject,
      String requesterName,
      String phone,
      String email,
      String studentName,
      String studentClass,
      String priority,
      String status,
      UUID assignedTo,
      String assignedName,
      String source,
      Instant dueAt,
      boolean overdue,
      Instant firstResponseAt,
      Instant resolvedAt,
      Instant createdAt,
      Instant updatedAt) {}

  public record Message(
      UUID id,
      String authorKind,
      String authorName,
      String body,
      boolean isPublic,
      Instant createdAt) {}

  public record TicketDetail(Ticket ticket, List<Message> messages) {}

  public record TicketInput(
      String category,
      String subject,
      String description,
      String requesterName,
      String phone,
      String email,
      String studentName,
      String studentClass,
      String priority,
      String source) {}

  public record TicketUpdate(
      String status, String priority, String category, UUID assignedTo, boolean unassign) {}

  public record Summary(
      long open, long overdue, long dueToday, long unassigned, long resolvedThisWeek) {}

  /** What a parent sees: status and public conversation only. */
  public record PublicTicket(
      String ticketNo,
      String category,
      String subject,
      String status,
      Instant createdAt,
      List<PublicMessage> messages) {}

  public record PublicMessage(String from, String body, Instant createdAt) {}

  public record Staff(UUID id, String name) {}

  record TicketEvent(UUID organizationId, UUID ticketId, String kind) {}

  private final TenantResolver resolver;
  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final RateLimiter limiter;
  private final AuditService audit;
  private final EntitlementService entitlements;
  private final NotificationService notifications;
  private final String adminBaseUrl;

  HelpdeskService(
      TenantResolver resolver,
      JdbcClient jdbc,
      TransactionTemplate tx,
      RateLimiter limiter,
      AuditService audit,
      EntitlementService entitlements,
      NotificationService notifications,
      @Value("${shikshablox.admin-base-url:http://admin.shikshablox.localhost}")
          String adminBaseUrl) {
    this.resolver = resolver;
    this.jdbc = jdbc;
    this.tx = tx;
    this.limiter = limiter;
    this.audit = audit;
    this.entitlements = entitlements;
    this.notifications = notifications;
    this.adminBaseUrl = adminBaseUrl;
  }

  // ------------------------------------------------------------------ public

  public PublicTicket raise(String host, JsonNode in, String clientIp) {
    Organization org = resolver.resolve(host).orElseThrow(() -> ApiException.notFound("School"));
    String ip = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
    if (!limiter.tryAcquire("helpdesk:" + org.id() + ":" + ip, 5, Duration.ofMinutes(30))
        || !limiter.tryAcquire("helpdesk:" + org.id(), 200, Duration.ofHours(1))) {
      throw ApiException.tooManyRequests();
    }
    if (in == null || !in.isObject()) {
      throw ApiException.badRequest("invalid_request", "Please fill in the form.");
    }
    if (!in.path("website").asText("").isBlank()) {
      log.info("Helpdesk honeypot triggered for {}", org.slug());
      return new PublicTicket("—", "OTHER", "", "OPEN", Instant.now(), List.of());
    }
    if (!in.path("consent").asBoolean(false)) {
      throw ApiException.badRequest(
          "consent_required", "Please agree that the school may use these details to respond.");
    }
    TicketInput t =
        new TicketInput(
            Inputs.text(in, "category", 20, false, "Category"),
            Inputs.text(in, "subject", 160, true, "Subject"),
            Inputs.text(in, "description", 4000, true, "Details"),
            Inputs.text(in, "requesterName", 120, true, "Your name"),
            Inputs.text(in, "phone", 20, true, "Phone"),
            Inputs.text(in, "email", 200, false, "Email"),
            Inputs.text(in, "studentName", 120, false, "Student's name"),
            Inputs.text(in, "studentClass", 60, false, "Class"),
            "NORMAL",
            "WEBSITE");
    return TenantContext.callInTenant(
        org.id(),
        () -> {
          if (!entitlements.hasFeature(MODULE, "helpdesk")) {
            throw ApiException.notFound("Helpdesk");
          }
          UUID id = insert(t, true);
          return publicView(id);
        });
  }

  /** Parent looks up a ticket by number + phone (rate limited). */
  public PublicTicket lookup(String host, String ticketNo, String phone, String clientIp) {
    Organization org = resolver.resolve(host).orElseThrow(() -> ApiException.notFound("School"));
    String ip = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
    if (!limiter.tryAcquire("helpdesk-lookup:" + org.id() + ":" + ip, 20, Duration.ofMinutes(10))) {
      throw ApiException.tooManyRequests();
    }
    String no = required(ticketNo, 20, "Ticket number").toUpperCase(java.util.Locale.ROOT);
    String key = Inputs.phoneKey(Inputs.phone(phone));
    return TenantContext.callInTenant(org.id(), () -> publicView(findForRequester(no, key)));
  }

  public PublicTicket requesterReply(
      String host, String ticketNo, String phone, String body, String clientIp) {
    Organization org = resolver.resolve(host).orElseThrow(() -> ApiException.notFound("School"));
    String ip = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
    if (!limiter.tryAcquire("helpdesk-reply:" + org.id() + ":" + ip, 10, Duration.ofMinutes(30))) {
      throw ApiException.tooManyRequests();
    }
    String no = required(ticketNo, 20, "Ticket number").toUpperCase(java.util.Locale.ROOT);
    String key = Inputs.phoneKey(Inputs.phone(phone));
    String text = required(body, 4000, "Message");
    return TenantContext.callInTenant(
        org.id(),
        () -> {
          UUID id = findForRequester(no, key);
          tx.executeWithoutResult(s -> {
            String status =
                jdbc.sql("select status from helpdesk_ticket where id = ?")
                    .param(id)
                    .query(String.class)
                    .single();
            if ("CLOSED".equals(status)) {
              throw ApiException.conflict(
                  "closed", "This request is closed. Please raise a new one.");
            }
            message(id, "REQUESTER", null, text, true);
            // A parent reply reopens a resolved or waiting ticket.
            jdbc.sql(
                    "update helpdesk_ticket set status = case when status in ('RESOLVED', 'WAITING') then 'OPEN' else status end,"
                        + " resolved_at = case when status = 'RESOLVED' then null else resolved_at end, updated_at = now()"
                        + " where id = ?")
                .param(id)
                .update();
          });
          notifyStaff(org.id(), id, "reply");
          return publicView(id);
        });
  }

  private UUID findForRequester(String no, String phoneKey) {
    return jdbc.sql(
            "select id from helpdesk_ticket where ticket_no = ? and right(regexp_replace(phone, '\\D', '', 'g'), 10) = ?")
        .params(no, phoneKey)
        .query(UUID.class)
        .optional()
        .orElseThrow(() -> ApiException.notFound("Request"));
  }

  private PublicTicket publicView(UUID id) {
    Ticket t = ticket(id);
    List<PublicMessage> msgs =
        jdbc.sql(
                "select author_kind, body, created_at from helpdesk_message where ticket_id = ? and is_public"
                    + " order by created_at")
            .param(id)
            .query((rs, i) ->
                new PublicMessage(
                    switch (rs.getString(1)) {
                      case "REQUESTER" -> "You";
                      case "STAFF" -> "School";
                      default -> "Update";
                    },
                    rs.getString(2),
                    rs.getTimestamp(3).toInstant()))
            .list();
    return new PublicTicket(
        t.ticketNo(), t.category(), t.subject(), t.status(), t.createdAt(), msgs);
  }

  // ------------------------------------------------------------------ staff

  public Summary summary() {
    entitlements.requireFeature(MODULE, "helpdesk");
    return jdbc.sql(
            "select"
                + " count(*) filter (where status in ('OPEN', 'IN_PROGRESS', 'WAITING')),"
                + " count(*) filter (where status in ('OPEN', 'IN_PROGRESS') and due_at < now()),"
                + " count(*) filter (where status in ('OPEN', 'IN_PROGRESS') and due_at >= now()"
                + "   and due_at < (date_trunc('day', now() at time zone 'Asia/Kolkata') + interval '1 day') at time zone 'Asia/Kolkata'),"
                + " count(*) filter (where status in ('OPEN', 'IN_PROGRESS', 'WAITING') and assigned_to is null),"
                + " count(*) filter (where resolved_at > now() - interval '7 days')"
                + " from helpdesk_ticket")
        .query((rs, i) ->
            new Summary(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5)))
        .single();
  }

  public List<Ticket> list(String view, String q) {
    entitlements.requireFeature(MODULE, "helpdesk");
    StringBuilder sql = new StringBuilder(TICKET_SELECT + " where true");
    List<Object> p = new ArrayList<>();
    switch (view == null ? "open" : view) {
      case "overdue" -> sql.append(" and t.status in ('OPEN', 'IN_PROGRESS') and t.due_at < now()");
      case "mine" -> {
        sql.append(" and t.status in ('OPEN', 'IN_PROGRESS', 'WAITING') and t.assigned_to = ?");
        p.add(TenantContext.userId().orElse(null));
      }
      case "unassigned" ->
          sql.append(
              " and t.status in ('OPEN', 'IN_PROGRESS', 'WAITING') and t.assigned_to is null");
      case "resolved" -> sql.append(" and t.status in ('RESOLVED', 'CLOSED')");
      case "all" -> {}
      default -> sql.append(" and t.status in ('OPEN', 'IN_PROGRESS', 'WAITING')");
    }
    if (q != null && !q.isBlank()) {
      String like = "%" + q.trim().replace("%", "") + "%";
      sql.append(
          " and (t.ticket_no ilike ? or t.subject ilike ? or t.requester_name ilike ? or t.phone ilike ?"
              + " or coalesce(t.student_name, '') ilike ?)");
      for (int i = 0; i < 5; i++) {
        p.add(like);
      }
    }
    sql.append(
        " order by case when t.status in ('OPEN', 'IN_PROGRESS') then 0 when t.status = 'WAITING' then 1 else 2 end,"
            + " t.due_at, t.created_at desc limit 500");
    return jdbc.sql(sql.toString()).params(p).query(HelpdeskService::mapTicket).list();
  }

  public TicketDetail get(UUID id) {
    entitlements.requireFeature(MODULE, "helpdesk");
    return new TicketDetail(ticket(id), messages(id));
  }

  /** Staff log a request that came by phone, walk-in or email. */
  public TicketDetail create(TicketInput in) {
    entitlements.requireFeature(MODULE, "helpdesk");
    UUID id = insert(in, false);
    return get(id);
  }

  public TicketDetail update(UUID id, TicketUpdate u) {
    entitlements.requireFeature(MODULE, "helpdesk");
    TicketDetail out =
        tx.execute(s -> {
          Ticket before = ticket(id);
          String status =
              u.status() == null ? before.status() : Inputs.oneOf(u.status(), STATUSES, "status");
          String priority =
              u.priority() == null
                  ? before.priority()
                  : Inputs.oneOf(u.priority(), PRIORITIES, "priority");
          String category =
              u.category() == null
                  ? before.category()
                  : Inputs.oneOf(u.category(), CATEGORIES, "category");
          UUID assignee =
              u.unassign()
                  ? null
                  : (u.assignedTo() == null ? before.assignedTo() : staffMember(u.assignedTo()));
          if (assignee != null && !assignee.equals(before.assignedTo())) {
            entitlements.requireFeature(MODULE, "assignment");
          }
          Instant due =
              priority.equals(before.priority())
                  ? before.dueAt()
                  : before.createdAt().plus(sla(priority));
          jdbc.sql(
                  "update helpdesk_ticket set status = ?, priority = ?, category = ?, assigned_to = ?, due_at = ?,"
                      + " resolved_at = case when ? in ('RESOLVED', 'CLOSED') then coalesce(resolved_at, now()) else null end,"
                      + " updated_at = now() where id = ?")
              .params(status, priority, category, assignee, Timestamp.from(due), status, id)
              .update();
          List<String> changes = new ArrayList<>();
          if (!status.equals(before.status())) {
            changes.add("Status: " + label(status));
          }
          if (!priority.equals(before.priority())) {
            changes.add("Priority: " + priority.toLowerCase(java.util.Locale.ROOT));
          }
          if (assignee != null && !assignee.equals(before.assignedTo())) {
            changes.add("Assigned to " + staffName(assignee));
          }
          if (!changes.isEmpty()) {
            message(
                id,
                "SYSTEM",
                TenantContext.userId().orElse(null),
                String.join(" · ", changes),
                !status.equals(before.status()));
          }
          audit.record(
              "helpdesk.ticket_updated",
              "helpdesk_ticket",
              id.toString(),
              Map.of("status", before.status(), "priority", before.priority()),
              Map.of("status", status, "priority", priority));
          return get(id);
        });
    return out;
  }

  /** Staff reply; public replies go to the parent (status page and email). */
  public TicketDetail reply(UUID id, String body, boolean isPublic, String setStatus) {
    entitlements.requireFeature(MODULE, "helpdesk");
    String text = required(body, 4000, "Reply");
    String status =
        setStatus == null || setStatus.isBlank()
            ? null
            : Inputs.oneOf(setStatus, STATUSES, "status");
    TicketDetail out =
        tx.execute(s -> {
          Ticket t = ticket(id);
          message(id, "STAFF", TenantContext.userId().orElse(null), text, isPublic);
          jdbc.sql(
                  "update helpdesk_ticket set first_response_at = case when ? then coalesce(first_response_at, now()) else first_response_at end,"
                      + " status = coalesce(?, case when status = 'OPEN' and ? then 'IN_PROGRESS' else status end),"
                      + " resolved_at = case when coalesce(?, status) in ('RESOLVED', 'CLOSED') then coalesce(resolved_at, now()) else null end,"
                      + " updated_at = now() where id = ?")
              .params(isPublic, status, isPublic, status, id)
              .update();
          audit.record(
              "helpdesk.replied",
              "helpdesk_ticket",
              id.toString(),
              null,
              Map.of("public", isPublic));
          return get(id);
        });
    if (isPublic) {
      notifyRequester(out.ticket(), text);
    }
    return out;
  }

  public void delete(UUID id) {
    entitlements.requireFeature(MODULE, "helpdesk");
    tx.executeWithoutResult(s -> {
      Ticket t = ticket(id);
      jdbc.sql("delete from helpdesk_ticket where id = ?").param(id).update();
      audit.record(
          "helpdesk.ticket_deleted",
          "helpdesk_ticket",
          id.toString(),
          Map.of("ticketNo", t.ticketNo()),
          null);
    });
  }

  public List<Staff> staff() {
    UUID org = TenantContext.requireOrganizationId();
    return jdbc.sql(
            "select distinct u.id, u.display_name from role_assignment r join app_user u on u.id = r.user_id"
                + " where r.organization_id = ? and u.status = 'ACTIVE' order by u.display_name")
        .param(org)
        .query((rs, i) -> new Staff(rs.getObject(1, UUID.class), rs.getString(2)))
        .list();
  }

  // ------------------------------------------------------------------ helpers

  private UUID insert(TicketInput in, boolean fromWebsite) {
    String category =
        in.category() == null || in.category().isBlank()
            ? "OTHER"
            : Inputs.oneOf(in.category(), CATEGORIES, "category");
    String subject = required(in.subject(), 160, "Subject");
    String description = required(in.description(), 4000, "Details");
    String name = required(in.requesterName(), 120, "Name");
    String phone = Inputs.phone(in.phone());
    String email = Inputs.email(in.email());
    String priority =
        in.priority() == null ? "NORMAL" : Inputs.oneOf(in.priority(), PRIORITIES, "priority");
    String source =
        fromWebsite
            ? "WEBSITE"
            : (in.source() == null ? "PHONE" : Inputs.oneOf(in.source(), SOURCES, "source"));
    UUID org = TenantContext.requireOrganizationId();
    UUID id =
        tx.execute(s -> {
          jdbc.sql("select pg_advisory_xact_lock(hashtext(?))")
              .param("helpdesk:" + org)
              .query((rs, i) -> 1)
              .list();
          int next =
              jdbc.sql(
                      "select coalesce(max(substring(ticket_no from '[0-9]+$')::int), 0) + 1 from helpdesk_ticket")
                  .query(Integer.class)
                  .single();
          String no = "HD-" + String.format("%05d", next);
          UUID tid =
              jdbc.sql(
                      "insert into helpdesk_ticket (organization_id, ticket_no, category, subject, requester_name, phone,"
                          + " email, student_name, student_class, priority, source, due_at)"
                          + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now() + make_interval(secs => ?)) returning id")
                  .params(
                      org,
                      no,
                      category,
                      subject,
                      name,
                      phone,
                      email,
                      optional(in.studentName(), 120, "Student's name"),
                      optional(in.studentClass(), 60, "Class"),
                      priority,
                      source,
                      (double) sla(priority).toSeconds())
                  .query(UUID.class)
                  .single();
          message(
              tid,
              fromWebsite ? "REQUESTER" : "STAFF",
              fromWebsite ? null : TenantContext.userId().orElse(null),
              description,
              true);
          audit.record(
              "helpdesk.ticket_created",
              "helpdesk_ticket",
              tid.toString(),
              null,
              Map.of("ticketNo", no, "source", source));
          return tid;
        });
    notifyStaff(org, id, "new");
    return id;
  }

  private void message(UUID ticketId, String kind, UUID author, String body, boolean isPublic) {
    jdbc.sql(
            "insert into helpdesk_message (organization_id, ticket_id, author_kind, author_id, body, is_public)"
                + " values (?, ?, ?, ?, ?, ?)")
        .params(TenantContext.requireOrganizationId(), ticketId, kind, author, body, isPublic)
        .update();
  }

  private List<Message> messages(UUID id) {
    return jdbc.sql(
            "select m.id, m.author_kind, coalesce(u.display_name, t.requester_name), m.body, m.is_public, m.created_at"
                + " from helpdesk_message m join helpdesk_ticket t on t.id = m.ticket_id"
                + " left join app_user u on u.id = m.author_id where m.ticket_id = ? order by m.created_at, m.id")
        .param(id)
        .query((rs, i) ->
            new Message(
                rs.getObject(1, UUID.class),
                rs.getString(2),
                "SYSTEM".equals(rs.getString(2)) && rs.getString(3) == null
                    ? "System"
                    : rs.getString(3),
                rs.getString(4),
                rs.getBoolean(5),
                rs.getTimestamp(6).toInstant()))
        .list();
  }

  private UUID staffMember(UUID userId) {
    if (staff().stream().noneMatch(s -> s.id().equals(userId))) {
      throw ApiException.badRequest("invalid_assignee", "Choose someone from your school.");
    }
    return userId;
  }

  private String staffName(UUID id) {
    return staff().stream()
        .filter(s -> s.id().equals(id))
        .map(Staff::name)
        .findFirst()
        .orElse("a colleague");
  }

  static String label(String status) {
    return switch (status) {
      case "OPEN" -> "open";
      case "IN_PROGRESS" -> "in progress";
      case "WAITING" -> "waiting for you";
      case "RESOLVED" -> "resolved";
      case "CLOSED" -> "closed";
      default -> status;
    };
  }

  /** New ticket or parent reply: email the assignee, else school admins and front office. */
  private void notifyStaff(UUID org, UUID ticketId, String kind) {
    Thread.ofVirtual()
        .start(() -> {
          try (var ignored = TenantContext.forTenant(org, null)) {
            Ticket t = ticket(ticketId);
            Set<String> to = new LinkedHashSet<>();
            TenantContext.runAsSystem(
                "helpdesk alert recipients",
                () -> {
                  if (t.assignedTo() != null) {
                    to.addAll(
                        jdbc.sql("select email from app_user where id = ? and status = 'ACTIVE'")
                            .param(t.assignedTo())
                            .query(String.class)
                            .list());
                  }
                  if (to.isEmpty()) {
                    to.addAll(
                        jdbc.sql(
                                "select distinct u.email from role_assignment r join app_user u on u.id = r.user_id"
                                    + " where r.organization_id = ? and r.role in ('SCHOOL_ADMIN', 'FRONT_OFFICE') and u.status = 'ACTIVE'")
                            .param(org)
                            .query(String.class)
                            .list());
                  }
                });
            notifications.email(
                NotificationService.Template.HELPDESK_STAFF,
                new ArrayList<>(to),
                Map.of(
                    "ticketNo",
                    t.ticketNo(),
                    "what",
                    "new".equals(kind) ? "New request" : "Parent replied",
                    "subject",
                    t.subject(),
                    "link",
                    adminBaseUrl + "/parents"));
          } catch (RuntimeException e) {
            log.warn("Helpdesk staff alert failed: {}", e.getMessage());
          }
        });
  }

  private void notifyRequester(Ticket t, String reply) {
    if (t.email() == null) {
      return;
    }
    UUID org = TenantContext.requireOrganizationId();
    Thread.ofVirtual()
        .start(() -> {
          try {
            String school =
                TenantContext.callAsSystem(
                    "school name",
                    () ->
                        jdbc.sql("select name from organization where id = ?")
                            .param(org)
                            .query(String.class)
                            .single());
            notifications.email(
                NotificationService.Template.HELPDESK_REPLY,
                List.of(t.email()),
                Map.of(
                    "school",
                    school,
                    "ticketNo",
                    t.ticketNo(),
                    "subject",
                    t.subject(),
                    "reply",
                    reply));
          } catch (RuntimeException e) {
            log.warn("Helpdesk reply email failed: {}", e.getMessage());
          }
        });
  }

  private static final String TICKET_SELECT =
      "select t.*, u.display_name assigned_name from helpdesk_ticket t left join app_user u on u.id = t.assigned_to";

  private Ticket ticket(UUID id) {
    return jdbc.sql(TICKET_SELECT + " where t.id = ?")
        .param(id)
        .query(HelpdeskService::mapTicket)
        .optional()
        .orElseThrow(() -> ApiException.notFound("Request"));
  }

  private static Ticket mapTicket(ResultSet rs, int i) throws SQLException {
    Instant due = rs.getTimestamp("due_at").toInstant();
    String status = rs.getString("status");
    return new Ticket(
        rs.getObject("id", UUID.class),
        rs.getString("ticket_no"),
        rs.getString("category"),
        rs.getString("subject"),
        rs.getString("requester_name"),
        rs.getString("phone"),
        rs.getString("email"),
        rs.getString("student_name"),
        rs.getString("student_class"),
        rs.getString("priority"),
        status,
        rs.getObject("assigned_to", UUID.class),
        rs.getString("assigned_name"),
        rs.getString("source"),
        due,
        ("OPEN".equals(status) || "IN_PROGRESS".equals(status)) && due.isBefore(Instant.now()),
        ts(rs.getTimestamp("first_response_at")),
        ts(rs.getTimestamp("resolved_at")),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }

  private static Instant ts(Timestamp t) {
    return t == null ? null : t.toInstant();
  }
}
