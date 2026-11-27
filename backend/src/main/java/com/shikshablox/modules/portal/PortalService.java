package com.shikshablox.modules.portal;

import com.shikshablox.modules.attendance.AttendanceService;
import com.shikshablox.modules.attendance.LeaveService;
import com.shikshablox.modules.certificates.CertificateService;
import com.shikshablox.modules.comms.BroadcastService;
import com.shikshablox.modules.comms.CommsService;
import com.shikshablox.modules.exams.ReportService;
import com.shikshablox.modules.homework.HomeworkService;
import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.entitlement.EntitlementService;
import com.shikshablox.platform.shared.RateLimiter;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.TenantContext;
import com.shikshablox.platform.tenancy.TenantResolver;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Parent portal. The school invites a guardian (personal link, 14 days); the parent opens it,
 * confirms their phone number and sets a 4–6 digit PIN, then signs in with phone + PIN. Five wrong
 * PINs lock the account for 15 minutes. Sessions are random tokens stored hashed (30 days), revoked
 * when the school re-invites or disables the parent.
 */
@Service
public class PortalService {

  static final String MODULE = "PORTAL";
  static final ZoneId IST = ZoneId.of("Asia/Kolkata");
  static final Duration INVITE_TTL = Duration.ofDays(14);
  static final Duration SESSION_TTL = Duration.ofDays(30);
  static final int MAX_FAILURES = 5;
  static final Duration LOCK = Duration.ofMinutes(15);
  private static final SecureRandom RANDOM = new SecureRandom();

  // ------------------------------------------------------------------ records

  public record Access(
      UUID guardianId,
      String name,
      String phone,
      String status,
      Instant lastLoginAt,
      Instant inviteExpiresAt) {}

  public record Invite(String link, Instant expiresAt) {}

  public record Session(String token, Instant expiresAt) {}

  public record Child(
      UUID id,
      String name,
      String admissionNo,
      String classLabel,
      String classTeacher,
      Integer rollNo,
      String house,
      String medicalAlerts) {}

  public record Me(String school, String guardianName, String phone, List<Child> children) {}

  public record CircularItem(
      String title,
      Instant sentAt,
      boolean read,
      boolean ackRequired,
      boolean acknowledged,
      String link) {}

  public record RequestItem(String ticketNo, String subject, String status, Instant updatedAt) {}

  public record PtmItem(
      String title, LocalDate date, LocalTime time, String teacher, String room, String code) {}

  public record Preferences(
      boolean available,
      boolean whatsapp,
      boolean sms,
      boolean email,
      boolean hasEmail,
      String language) {}

  private record Caller(
      UUID orgId,
      String orgName,
      UUID accountId,
      UUID guardianId,
      String guardianName,
      String phone) {}

  private final TenantResolver resolver;
  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final RateLimiter limiter;
  private final AuditService audit;
  private final EntitlementService entitlements;
  private final PasswordEncoder passwords;
  private final CommsService comms;
  private final BroadcastService broadcasts;
  private final AttendanceService attendance;
  private final LeaveService leave;
  private final ReportService reports;
  private final CertificateService certificates;
  private final HomeworkService homework;
  private final String dummyHash;

  PortalService(
      TenantResolver resolver,
      JdbcClient jdbc,
      TransactionTemplate tx,
      RateLimiter limiter,
      AuditService audit,
      EntitlementService entitlements,
      PasswordEncoder passwords,
      CommsService comms,
      BroadcastService broadcasts,
      AttendanceService attendance,
      LeaveService leave,
      ReportService reports,
      CertificateService certificates,
      HomeworkService homework) {
    this.resolver = resolver;
    this.jdbc = jdbc;
    this.tx = tx;
    this.limiter = limiter;
    this.audit = audit;
    this.entitlements = entitlements;
    this.passwords = passwords;
    this.comms = comms;
    this.broadcasts = broadcasts;
    this.attendance = attendance;
    this.leave = leave;
    this.reports = reports;
    this.certificates = certificates;
    this.homework = homework;
    this.dummyHash = passwords.encode("no-account-" + token(8));
  }

  // ------------------------------------------------------------------ staff

  /** Portal access of a student's guardians. */
  public List<Access> access(UUID studentId) {
    entitlements.requireModule(MODULE);
    return jdbc.sql(
            "select g.id, g.name, g.phone, coalesce(a.status, 'NONE'), a.last_login_at, a.invite_expires_at"
                + " from student_guardian sg join guardian g on g.id = sg.guardian_id"
                + " left join parent_account a on a.guardian_id = g.id where sg.student_id = ? order by sg.is_primary desc, g.name")
        .param(studentId)
        .query((rs, i) ->
            new Access(
                rs.getObject(1, UUID.class),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                ts(rs.getTimestamp(5)),
                ts(rs.getTimestamp(6))))
        .list();
  }

  /**
   * A new personal invite link for the guardian (also how a forgotten PIN is reset): any PIN and
   * sessions are cleared.
   */
  public Invite invite(UUID guardianId) {
    entitlements.requireModule(MODULE);
    String phone =
        jdbc.sql("select phone from guardian where id = ?")
            .param(guardianId)
            .query(String.class)
            .optional()
            .orElseThrow(() -> ApiException.notFound("Guardian"));
    if (phone == null) {
      throw ApiException.badRequest(
          "missing_phone", "Add the guardian's mobile number first; parents sign in with it.");
    }
    String code = token(24);
    Instant expires = Instant.now().plus(INVITE_TTL);
    tx.executeWithoutResult(s -> {
      jdbc.sql(
              "insert into parent_account (organization_id, guardian_id, status, invite_hash, invite_expires_at)"
                  + " values (?, ?, 'INVITED', ?, ?) on conflict (guardian_id) do update set status = 'INVITED', pin_hash = null,"
                  + " invite_hash = excluded.invite_hash, invite_expires_at = excluded.invite_expires_at, failed_attempts = 0,"
                  + " locked_until = null, updated_at = now()")
          .params(
              TenantContext.requireOrganizationId(),
              guardianId,
              hash(code),
              Timestamp.from(expires))
          .update();
      jdbc.sql(
              "delete from parent_session where account_id = (select id from parent_account where guardian_id = ?)")
          .param(guardianId)
          .update();
      audit.record("portal.invited", "guardian", guardianId.toString(), null, null);
    });
    return new Invite(broadcasts.siteUrl() + "/parent/setup?code=" + code, expires);
  }

  public void disable(UUID guardianId) {
    entitlements.requireModule(MODULE);
    tx.executeWithoutResult(s -> {
      int n =
          jdbc.sql(
                  "update parent_account set status = 'DISABLED', invite_hash = null, invite_expires_at = null, updated_at = now()"
                      + " where guardian_id = ?")
              .param(guardianId)
              .update();
      if (n == 0) {
        throw ApiException.notFound("Portal access");
      }
      jdbc.sql(
              "delete from parent_session where account_id = (select id from parent_account where guardian_id = ?)")
          .param(guardianId)
          .update();
      audit.record("portal.disabled", "guardian", guardianId.toString(), null, null);
    });
  }

  // ------------------------------------------------------------------ sign-in

  /** First visit from the invite link: confirm the phone number, choose a PIN, get signed in. */
  public Session setup(String host, String code, String phone, String pin, String ip) {
    Organization org = org(host);
    limit("portal-setup:" + org.id() + ":" + ip(ip), 10, Duration.ofMinutes(15));
    String p = pin(pin);
    if (code == null || !code.matches("^[A-Za-z0-9_-]{20,64}$")) {
      throw ApiException.notFound("Invitation");
    }
    return TenantContext.callInTenant(
        org.id(),
        () ->
            tx.execute(s -> {
              record Acc(UUID id, String phone, Instant expires) {}
              Acc a =
                  jdbc.sql(
                          "select a.id, g.phone, a.invite_expires_at from parent_account a join guardian g on g.id = a.guardian_id"
                              + " where a.invite_hash = ? and a.status <> 'DISABLED'")
                      .param(hash(code))
                      .query((rs, i) ->
                          new Acc(
                              rs.getObject(1, UUID.class), rs.getString(2), ts(rs.getTimestamp(3))))
                      .optional()
                      .orElseThrow(() -> ApiException.notFound("Invitation"));
              if (a.expires() == null || a.expires().isBefore(Instant.now())) {
                throw ApiException.conflict(
                    "expired",
                    "This invitation has expired. Please ask the school for a new link.");
              }
              if (!key(a.phone()).equals(key(phone == null ? "" : phone))) {
                throw ApiException.badRequest(
                    "phone_mismatch", "This is not the mobile number the school has for you.");
              }
              jdbc.sql(
                      "update parent_account set status = 'ACTIVE', pin_hash = ?, invite_hash = null, invite_expires_at = null,"
                          + " failed_attempts = 0, locked_until = null, last_login_at = now(), updated_at = now() where id = ?")
                  .params(passwords.encode(p), a.id())
                  .update();
              audit.record("portal.activated", "parent_account", a.id().toString(), null, null);
              return newSession(org.id(), a.id());
            }));
  }

  public Session login(String host, String phone, String pin, String ip) {
    Organization org = org(host);
    String k = key(phone == null ? "" : phone);
    limit("portal-login:" + org.id() + ":" + ip(ip), 20, Duration.ofMinutes(15));
    limit("portal-login:" + org.id() + ":" + k, 10, Duration.ofMinutes(15));
    if (k.length() < 8 || pin == null) {
      throw ApiException.badRequest("invalid_login", "Enter your mobile number and PIN.");
    }
    return TenantContext.callInTenant(
        org.id(),
        () -> {
          record Acc(UUID id, String status, String pinHash, int failures, Instant locked) {}
          Acc a =
              jdbc.sql(
                      "select a.id, a.status, a.pin_hash, a.failed_attempts, a.locked_until from parent_account a"
                          + " join guardian g on g.id = a.guardian_id where right(regexp_replace(g.phone, '\\D', '', 'g'), 10) = ?")
                  .param(k)
                  .query((rs, i) ->
                      new Acc(
                          rs.getObject(1, UUID.class),
                          rs.getString(2),
                          rs.getString(3),
                          rs.getInt(4),
                          ts(rs.getTimestamp(5))))
                  .optional()
                  .orElse(null);
          // One message for unknown numbers, wrong PINs and inactive accounts.
          ApiException wrong =
              new ApiException(
                  HttpStatus.UNAUTHORIZED, "invalid_login", "Mobile number or PIN is not right.");
          if (a == null || !"ACTIVE".equals(a.status()) || a.pinHash() == null) {
            passwords.matches(
                pin, dummyHash); // same work as a real check, so timing reveals nothing
            throw wrong;
          }
          if (a.locked() != null && a.locked().isAfter(Instant.now())) {
            throw ApiException.tooManyRequests();
          }
          if (!passwords.matches(pin, a.pinHash())) {
            int f = a.failures() + 1;
            tx.executeWithoutResult(s ->
                jdbc.sql(
                        "update parent_account set failed_attempts = ?, locked_until = ? where id = ?")
                    .params(
                        f >= MAX_FAILURES ? 0 : f,
                        f >= MAX_FAILURES ? Timestamp.from(Instant.now().plus(LOCK)) : null,
                        a.id())
                    .update());
            throw wrong;
          }
          return tx.execute(s -> {
            jdbc.sql(
                    "update parent_account set failed_attempts = 0, locked_until = null, last_login_at = now() where id = ?")
                .param(a.id())
                .update();
            return newSession(org.id(), a.id());
          });
        });
  }

  public void logout(String host, String token) {
    Organization org = org(host);
    TenantContext.callInTenant(
        org.id(),
        () ->
            tx.execute(s ->
                jdbc.sql("delete from parent_session where token_hash = ?")
                    .param(hash(token == null ? "" : token))
                    .update()));
  }

  private Session newSession(UUID org, UUID accountId) {
    String token = token(32);
    Instant expires = Instant.now().plus(SESSION_TTL);
    jdbc.sql(
            "insert into parent_session (organization_id, account_id, token_hash, expires_at) values (?, ?, ?, ?)")
        .params(org, accountId, hash(token), Timestamp.from(expires))
        .update();
    jdbc.sql("delete from parent_session where account_id = ? and expires_at < now()")
        .param(accountId)
        .update();
    return new Session(token, expires);
  }

  // ------------------------------------------------------------------ parent views

  public Me me(String host, String token) {
    return asParent(
        host,
        token,
        c -> new Me(c.orgName(), c.guardianName(), c.phone(), children(c.guardianId())));
  }

  public List<CircularItem> circulars(String host, String token) {
    return asParent(
        host,
        token,
        c -> {
          String base = broadcasts.siteUrl();
          return jdbc.sql(
                  "select b.title, coalesce(d.sent_at, b.sent_at), d.read_at is not null, b.ack_required, d.ack_at is not null, d.token"
                      + " from comms_delivery d join comms_broadcast b on b.id = d.broadcast_id join comms_contact ct on ct.id = d.contact_id"
                      + " where right(regexp_replace(ct.phone, '\\D', '', 'g'), 10) = ? and d.status in ('SENT', 'DELIVERED')"
                      + " order by coalesce(d.sent_at, b.sent_at) desc nulls last limit 50")
              .param(key(c.phone()))
              .query((rs, i) ->
                  new CircularItem(
                      rs.getString(1),
                      ts(rs.getTimestamp(2)),
                      rs.getBoolean(3),
                      rs.getBoolean(4),
                      rs.getBoolean(5),
                      base + "/c/" + rs.getString(6)))
              .list();
        });
  }

  public List<RequestItem> requests(String host, String token) {
    return asParent(
        host,
        token,
        c ->
            jdbc.sql(
                    "select ticket_no, subject, status, updated_at from helpdesk_ticket"
                        + " where right(regexp_replace(phone, '\\D', '', 'g'), 10) = ? order by updated_at desc limit 50")
                .param(key(c.phone()))
                .query((rs, i) ->
                    new RequestItem(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getTimestamp(4).toInstant()))
                .list());
  }

  public List<PtmItem> ptm(String host, String token) {
    return asParent(
        host,
        token,
        c ->
            jdbc.sql(
                    "select e.title, e.event_date, b.slot_start, h.name, h.room, b.code from ptm_booking b"
                        + " join ptm_host h on h.id = b.host_id join ptm_event e on e.id = h.event_id"
                        + " where right(regexp_replace(b.phone, '\\D', '', 'g'), 10) = ? and b.status = 'BOOKED' and e.event_date >= ?"
                        + " order by e.event_date, b.slot_start")
                .params(key(c.phone()), LocalDate.now(IST))
                .query((rs, i) ->
                    new PtmItem(
                        rs.getString(1),
                        rs.getObject(2, LocalDate.class),
                        rs.getObject(3, LocalTime.class),
                        rs.getString(4),
                        rs.getString(5),
                        rs.getString(6)))
                .list());
  }

  /** Each child's attendance (empty when attendance is not in the school's plan). */
  public List<AttendanceService.ChildAttendance> attendance(String host, String token) {
    return asParent(
        host,
        token,
        c ->
            children(c.guardianId()).stream()
                .map(ch -> attendance.child(ch.id()))
                .filter(java.util.Objects::nonNull)
                .toList());
  }

  /** Each child's report card with published exams (children without results are left out). */
  public List<ReportService.ReportCard> reportCards(String host, String token) {
    return asParent(
        host,
        token,
        c ->
            children(c.guardianId()).stream()
                .map(ch -> reports.forParent(ch.id()))
                .filter(java.util.Objects::nonNull)
                .toList());
  }

  public record CertificatesView(
      List<CertificateService.Requestable> types,
      List<CertificateService.Certificate> certificates) {}

  public CertificatesView certificates(String host, String token) {
    return asParent(
        host,
        token,
        c ->
            new CertificatesView(
                certificates.requestable(), certificates.forGuardian(c.guardianId())));
  }

  public CertificateService.Certificate requestCertificate(
      String host, String token, UUID studentId, UUID templateId, String purpose) {
    return asParent(
        host,
        token,
        c -> {
          if (!limiter.tryAcquire("portal-cert:" + c.accountId(), 5, Duration.ofHours(1))) {
            throw ApiException.tooManyRequests();
          }
          return certificates.request(c.guardianId(), studentId, templateId, purpose);
        });
  }

  public CertificateService.PrintView certificate(String host, String token, UUID id) {
    return asParent(host, token, c -> certificates.forGuardianPrint(c.guardianId(), id));
  }

  public List<HomeworkService.ChildHomework> homework(String host, String token) {
    return asParent(host, token, c -> homework.forGuardian(c.guardianId()));
  }

  public String homeworkFile(String host, String token, UUID fileId) {
    return asParent(host, token, c -> homework.fileUrlForGuardian(c.guardianId(), fileId));
  }

  public HomeworkService.Submission submitHomework(
      String host,
      String token,
      UUID homeworkId,
      UUID studentId,
      String text,
      String filename,
      byte[] content) {
    return asParent(
        host,
        token,
        c -> {
          if (!limiter.tryAcquire("portal-hw:" + c.accountId(), 30, Duration.ofHours(1))) {
            throw ApiException.tooManyRequests();
          }
          return homework.submit(c.guardianId(), homeworkId, studentId, text, filename, content);
        });
  }

  public List<LeaveService.Leave> leave(String host, String token) {
    return asParent(host, token, c -> leave.forGuardian(c.guardianId()));
  }

  public LeaveService.Leave applyLeave(String host, String token, LeaveService.LeaveInput in) {
    return asParent(
        host,
        token,
        c -> {
          if (!limiter.tryAcquire("portal-leave:" + c.accountId(), 10, Duration.ofHours(1))) {
            throw ApiException.tooManyRequests();
          }
          return leave.apply(c.guardianId(), in);
        });
  }

  public LeaveService.Leave cancelLeave(String host, String token, UUID id) {
    return asParent(host, token, c -> leave.cancel(c.guardianId(), id));
  }

  public Preferences preferences(String host, String token) {
    return asParent(host, token, c -> toPrefs(comms.preferences(c.phone())));
  }

  public Preferences setPreferences(
      String host, String token, boolean whatsapp, boolean sms, boolean email, String language) {
    return asParent(
        host,
        token,
        c -> {
          CommsService.Preferences p =
              comms.setPreferences(c.phone(), whatsapp, sms, email, language);
          audit.record(
              "portal.preferences",
              "parent_account",
              c.accountId().toString(),
              null,
              Map.of("whatsapp", whatsapp, "sms", sms, "email", email));
          return toPrefs(p);
        });
  }

  private static Preferences toPrefs(CommsService.Preferences p) {
    return p == null
        ? new Preferences(false, false, false, false, false, "en")
        : new Preferences(true, p.whatsapp(), p.sms(), p.email(), p.hasEmail(), p.language());
  }

  private List<Child> children(UUID guardianId) {
    return jdbc.sql(
            "select s.id, s.name, s.admission_no, c.grade, c.section, c.class_teacher, e.roll_no, s.house, s.medical_alerts"
                + " from student_guardian sg join student s on s.id = sg.student_id"
                + " left join sis_settings st on st.organization_id = s.organization_id"
                + " left join student_enrolment e on e.student_id = s.id and e.academic_year = st.current_year"
                + " left join class_section c on c.id = e.class_section_id"
                + " where sg.guardian_id = ? and s.status = 'ENROLLED' order by s.name")
        .param(guardianId)
        .query((rs, i) ->
            new Child(
                rs.getObject(1, UUID.class),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4) == null ? null : rs.getString(4) + " " + rs.getString(5),
                rs.getString(6),
                (Integer) rs.getObject(7),
                rs.getString(8),
                rs.getString(9)))
        .list();
  }

  /** Resolves the school and the signed-in parent, then runs the view in the school's context. */
  private <T> T asParent(String host, String token, Function<Caller, T> view) {
    Organization org = org(host);
    if (token == null || !token.matches("^[A-Za-z0-9_-]{30,64}$")) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "signed_out", "Please sign in.");
    }
    return TenantContext.callInTenant(
        org.id(),
        () -> {
          if (!entitlements.hasModule(MODULE)) {
            throw ApiException.notFound("Parent portal");
          }
          Caller c =
              jdbc.sql(
                      "select a.id, g.id, g.name, g.phone from parent_session s join parent_account a on a.id = s.account_id"
                          + " join guardian g on g.id = a.guardian_id where s.token_hash = ? and s.expires_at > now() and a.status = 'ACTIVE'")
                  .param(hash(token))
                  .query((rs, i) ->
                      new Caller(
                          org.id(),
                          org.name(),
                          rs.getObject(1, UUID.class),
                          rs.getObject(2, UUID.class),
                          rs.getString(3),
                          rs.getString(4)))
                  .optional()
                  .orElseThrow(() ->
                      new ApiException(
                          HttpStatus.UNAUTHORIZED, "signed_out", "Please sign in again."));
          jdbc.sql(
                  "update parent_session set last_seen_at = now() where token_hash = ? and last_seen_at < now() - interval '5 minutes'")
              .param(hash(token))
              .update();
          return view.apply(c);
        });
  }

  // ------------------------------------------------------------------ helpers

  private Organization org(String host) {
    return resolver.resolve(host).orElseThrow(() -> ApiException.notFound("School"));
  }

  private void limit(String key, int n, Duration window) {
    if (!limiter.tryAcquire(key, n, window)) {
      throw ApiException.tooManyRequests();
    }
  }

  static String pin(String v) {
    if (v == null || !v.matches("^[0-9]{4,6}$")) {
      throw ApiException.badRequest("invalid_pin", "Choose a PIN of 4 to 6 digits.");
    }
    if (v.chars().distinct().count() == 1
        || "01234567890".contains(v)
        || "09876543210".contains(v)) {
      throw ApiException.badRequest(
          "weak_pin", "Choose a PIN that isn't a repeated or running number.");
    }
    return v;
  }

  static String key(String phone) {
    String d = phone.replaceAll("\\D", "");
    return d.length() > 10 ? d.substring(d.length() - 10) : d;
  }

  static String token(int bytes) {
    byte[] b = new byte[bytes];
    RANDOM.nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  static String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String ip(String ip) {
    return ip == null || ip.isBlank() ? "unknown" : ip;
  }

  private static Instant ts(Timestamp t) {
    return t == null ? null : t.toInstant();
  }
}
