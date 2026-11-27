package com.shikshablox.modules.attendance;

import static com.shikshablox.modules.attendance.AttendanceAccess.today;
import static com.shikshablox.modules.attendance.AttendanceService.label;
import static com.shikshablox.modules.attendance.AttendanceService.optional;
import static com.shikshablox.modules.attendance.AttendanceService.required;

import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.TenantContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Leave applications and attendance corrections. Parents apply in the portal; the class teacher (or
 * the office) approves, which marks the child "on leave" on days already taken as absent. Teachers'
 * changes to past days wait here until the office approves them.
 */
@Service
public class LeaveService {

  static final Set<String> STATUSES = Set.of("PENDING", "APPROVED", "REJECTED", "CANCELLED");
  static final int MAX_DAYS = 30;
  static final int BACKDATE_DAYS = 7;

  public record Leave(
      UUID id,
      UUID studentId,
      String studentName,
      String classLabel,
      LocalDate fromDate,
      LocalDate toDate,
      int days,
      String reason,
      String source,
      String status,
      String requestedBy,
      String decidedBy,
      Instant decidedAt,
      String decisionNote,
      Instant createdAt) {}

  public record LeaveInput(UUID studentId, LocalDate fromDate, LocalDate toDate, String reason) {}

  public record Correction(
      UUID id,
      UUID studentId,
      String studentName,
      String classLabel,
      LocalDate date,
      int period,
      String fromStatus,
      String toStatus,
      String toReason,
      String reason,
      String status,
      String requestedBy,
      String decidedBy,
      Instant createdAt) {}

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final AuditService audit;
  private final AttendanceAccess access;

  LeaveService(
      JdbcClient jdbc, TransactionTemplate tx, AuditService audit, AttendanceAccess access) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.audit = audit;
    this.access = access;
  }

  private static final String LEAVE_SELECT =
      "select l.*, s.name student_name, c.grade, c.section,"
          + " coalesce(g.name, cu.display_name) requested_by_name, du.display_name decided_by_name"
          + " from leave_request l join student s on s.id = l.student_id"
          + " left join sis_settings st on st.organization_id = l.organization_id"
          + " left join student_enrolment e on e.student_id = s.id and e.academic_year = st.current_year"
          + " left join class_section c on c.id = e.class_section_id"
          + " left join guardian g on g.id = l.guardian_id"
          + " left join app_user cu on cu.id = l.created_by"
          + " left join app_user du on du.id = l.decided_by";

  // ------------------------------------------------------------------ staff: leave

  public List<Leave> list(String status) {
    access.requireModule();
    requireLeave();
    String st =
        status == null || status.isBlank() ? null : status.toUpperCase(java.util.Locale.ROOT);
    if (st != null && !STATUSES.contains(st)) {
      throw ApiException.badRequest("invalid_status", "Unknown leave status.");
    }
    List<UUID> sections = access.sectionIds();
    if (sections.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            LEAVE_SELECT
                + " where e.class_section_id = any(?::uuid[])"
                + (st == null
                    ? " and (l.status = 'PENDING' or l.to_date >= ?)"
                    : " and l.status = ?")
                + " order by case when l.status = 'PENDING' then 0 else 1 end, l.from_date desc limit 300")
        .params(sections.toArray(UUID[]::new), st == null ? today().minusDays(30) : st)
        .query(LeaveService::mapLeave)
        .list();
  }

  /** Leave recorded by staff (e.g. a parent phoned in): approved straight away. */
  public Leave create(LeaveInput in) {
    access.requireModule();
    requireLeave();
    if (in.studentId() == null) {
      throw ApiException.badRequest("missing_field", "Choose a student.");
    }
    access.requireStudent(in.studentId());
    LocalDate[] range = range(in.fromDate(), in.toDate(), true);
    String reason = required(in.reason(), 500, "Reason");
    UUID id =
        tx.execute(s -> {
          UUID lid =
              jdbc.sql(
                      "insert into leave_request (organization_id, student_id, from_date, to_date, reason, source, status,"
                          + " decided_by, decided_at, created_by) values (?, ?, ?, ?, ?, 'STAFF', 'APPROVED', ?, now(), ?) returning id")
                  .params(
                      TenantContext.requireOrganizationId(),
                      in.studentId(),
                      range[0],
                      range[1],
                      reason,
                      access.userId(),
                      access.userId())
                  .query(UUID.class)
                  .single();
          int marked = applyLeave(in.studentId(), range[0], range[1]);
          audit.record(
              "attendance.leave_recorded",
              "leave_request",
              lid.toString(),
              null,
              Map.of("from", range[0].toString(), "to", range[1].toString(), "marked", marked));
          return lid;
        });
    return load(id);
  }

  public Leave decide(UUID id, boolean approve, String note) {
    access.requireModule();
    requireLeave();
    Leave l = load(id);
    access.requireStudent(l.studentId());
    if (!"PENDING".equals(l.status())) {
      throw ApiException.conflict(
          "already_decided", "This leave request has already been decided.");
    }
    String n = optional(note, 300, "Note");
    if (!approve && n == null) {
      throw ApiException.badRequest(
          "missing_note", "Tell the parent why the leave is not approved.");
    }
    tx.executeWithoutResult(s -> {
      jdbc.sql(
              "update leave_request set status = ?, decided_by = ?, decided_at = now(), decision_note = ? where id = ? and status = 'PENDING'")
          .params(approve ? "APPROVED" : "REJECTED", access.userId(), n, id)
          .update();
      int marked = approve ? applyLeave(l.studentId(), l.fromDate(), l.toDate()) : 0;
      audit.record(
          approve ? "attendance.leave_approved" : "attendance.leave_rejected",
          "leave_request",
          id.toString(),
          null,
          Map.of("marked", marked));
    });
    return load(id);
  }

  /** Days already taken as absent in the leave range become "on leave". */
  private int applyLeave(UUID studentId, LocalDate from, LocalDate to) {
    return jdbc.sql(
            "update attendance_mark m set status = 'LV' from attendance_session x"
                + " where x.id = m.session_id and m.student_id = ? and m.status = 'A' and x.date between ? and ?")
        .params(studentId, from, to)
        .update();
  }

  // ------------------------------------------------------------------ staff: corrections

  public List<Correction> corrections(String status) {
    access.requireModule();
    String st =
        status == null || status.isBlank() ? "PENDING" : status.toUpperCase(java.util.Locale.ROOT);
    if (!Set.of("PENDING", "APPROVED", "REJECTED").contains(st)) {
      throw ApiException.badRequest("invalid_status", "Unknown status.");
    }
    boolean manager = access.canManage();
    return jdbc.sql(
            "select k.*, x.date, x.period, s.name student_name, c.grade, c.section, ru.display_name req_name, du.display_name dec_name"
                + " from attendance_correction k join attendance_session x on x.id = k.session_id"
                + " join class_section c on c.id = x.class_section_id join student s on s.id = k.student_id"
                + " left join app_user ru on ru.id = k.requested_by left join app_user du on du.id = k.decided_by"
                + " where k.status = ?"
                + (manager ? "" : " and k.requested_by = ?")
                + " order by k.created_at desc limit 300")
        .params(manager ? List.of(st) : List.of(st, access.userId()))
        .query((rs, i) ->
            new Correction(
                rs.getObject("id", UUID.class),
                rs.getObject("student_id", UUID.class),
                rs.getString("student_name"),
                label(rs.getString("grade"), rs.getString("section")),
                rs.getObject("date", LocalDate.class),
                rs.getInt("period"),
                rs.getString("from_status"),
                rs.getString("to_status"),
                rs.getString("to_reason"),
                rs.getString("reason"),
                rs.getString("status"),
                rs.getString("req_name"),
                rs.getString("dec_name"),
                rs.getTimestamp("created_at").toInstant()))
        .list();
  }

  public void decideCorrection(UUID id, boolean approve) {
    access.requireModule();
    access.requireManage();
    tx.executeWithoutResult(s -> {
      record K(
          UUID session, UUID student, String from, String to, String toReason, String status) {}
      K k =
          jdbc.sql(
                  "select session_id, student_id, from_status, to_status, to_reason, status from attendance_correction where id = ? for update")
              .param(id)
              .query((rs, i) ->
                  new K(
                      rs.getObject(1, UUID.class),
                      rs.getObject(2, UUID.class),
                      rs.getString(3),
                      rs.getString(4),
                      rs.getString(5),
                      rs.getString(6)))
              .optional()
              .orElseThrow(() -> ApiException.notFound("Correction"));
      if (!"PENDING".equals(k.status())) {
        throw ApiException.conflict("already_decided", "This correction has already been decided.");
      }
      jdbc.sql(
              "update attendance_correction set status = ?, decided_by = ?, decided_at = now() where id = ?")
          .params(approve ? "APPROVED" : "REJECTED", access.userId(), id)
          .update();
      if (approve) {
        jdbc.sql(
                "insert into attendance_mark (organization_id, session_id, student_id, status, reason) values (?, ?, ?, ?, ?)"
                    + " on conflict (session_id, student_id) do update set status = excluded.status, reason = excluded.reason")
            .params(
                TenantContext.requireOrganizationId(),
                k.session(),
                k.student(),
                k.to(),
                k.toReason())
            .update();
      }
      audit.record(
          approve ? "attendance.correction_approved" : "attendance.correction_rejected",
          "attendance_correction",
          id.toString(),
          k.from() == null ? null : Map.of("status", k.from()),
          Map.of("status", approve ? k.to() : k.from() == null ? "" : k.from()));
    });
  }

  // ------------------------------------------------------------------ parent portal

  /** The guardian's children's leave requests (recent and upcoming). */
  public List<Leave> forGuardian(UUID guardianId) {
    if (!access.hasModule(AttendanceAccess.MODULE) || !access.feature("leave")) {
      return List.of();
    }
    return jdbc.sql(
            LEAVE_SELECT
                + " where l.student_id in (select student_id from student_guardian where guardian_id = ?)"
                + " and (l.status = 'PENDING' or l.to_date >= ?) order by l.from_date desc limit 50")
        .params(guardianId, today().minusDays(60))
        .query(LeaveService::mapLeave)
        .list();
  }

  public Leave apply(UUID guardianId, LeaveInput in) {
    if (!access.hasModule(AttendanceAccess.MODULE) || !access.feature("leave")) {
      throw ApiException.notFound("Leave");
    }
    if (in.studentId() == null) {
      throw ApiException.badRequest("missing_field", "Choose your child.");
    }
    boolean mine =
        jdbc.sql(
                    "select count(*) from student_guardian sg join student s on s.id = sg.student_id"
                        + " where sg.guardian_id = ? and sg.student_id = ? and s.status = 'ENROLLED'")
                .params(guardianId, in.studentId())
                .query(Long.class)
                .single()
            > 0;
    if (!mine) {
      throw ApiException.notFound("Child");
    }
    LocalDate[] range = range(in.fromDate(), in.toDate(), false);
    String reason = required(in.reason(), 500, "Reason");
    long open =
        jdbc.sql("select count(*) from leave_request where student_id = ? and status = 'PENDING'")
            .param(in.studentId())
            .query(Long.class)
            .single();
    if (open >= 5) {
      throw ApiException.conflict(
          "too_many_pending",
          "You already have several requests waiting. Please wait for the school.");
    }
    long overlap =
        jdbc.sql(
                "select count(*) from leave_request where student_id = ? and status in ('PENDING', 'APPROVED')"
                    + " and from_date <= ? and to_date >= ?")
            .params(in.studentId(), range[1], range[0])
            .query(Long.class)
            .single();
    if (overlap > 0) {
      throw ApiException.conflict("overlap", "There is already a leave request for these days.");
    }
    UUID id =
        tx.execute(s -> {
          UUID lid =
              jdbc.sql(
                      "insert into leave_request (organization_id, student_id, guardian_id, from_date, to_date, reason)"
                          + " values (?, ?, ?, ?, ?, ?) returning id")
                  .params(
                      TenantContext.requireOrganizationId(),
                      in.studentId(),
                      guardianId,
                      range[0],
                      range[1],
                      reason)
                  .query(UUID.class)
                  .single();
          audit.record(
              "attendance.leave_applied",
              "leave_request",
              lid.toString(),
              null,
              Map.of("from", range[0].toString(), "to", range[1].toString()));
          return lid;
        });
    return load(id);
  }

  /** Parents can withdraw a request until it's decided, or an approved one before it starts. */
  public Leave cancel(UUID guardianId, UUID id) {
    Leave l = load(id);
    boolean mine =
        jdbc.sql("select count(*) from student_guardian where guardian_id = ? and student_id = ?")
                .params(guardianId, l.studentId())
                .query(Long.class)
                .single()
            > 0;
    if (!mine) {
      throw ApiException.notFound("Leave");
    }
    boolean ok =
        "PENDING".equals(l.status())
            || ("APPROVED".equals(l.status()) && l.fromDate().isAfter(today()));
    if (!ok) {
      throw ApiException.conflict("cannot_cancel", "This leave can no longer be withdrawn here.");
    }
    tx.executeWithoutResult(s -> {
      jdbc.sql("update leave_request set status = 'CANCELLED' where id = ?").param(id).update();
      audit.record("attendance.leave_cancelled", "leave_request", id.toString(), null, null);
    });
    return load(id);
  }

  // ------------------------------------------------------------------ helpers

  private void requireLeave() {
    if (!access.feature("leave")) {
      throw new ApiException(
          org.springframework.http.HttpStatus.FORBIDDEN,
          "feature_not_in_plan",
          "Leave is not part of your plan.");
    }
  }

  /** Validates a leave range: up to 30 days, from at most a week back, up to 60 days ahead. */
  static LocalDate[] range(LocalDate from, LocalDate to, boolean staff) {
    return range(from, to, staff, today());
  }

  static LocalDate[] range(LocalDate from, LocalDate to, boolean staff, LocalDate today) {
    if (from == null) {
      throw ApiException.badRequest("missing_field", "Choose the first day of leave.");
    }
    LocalDate t = to == null ? from : to;
    if (t.isBefore(from)) {
      throw ApiException.badRequest("invalid_range", "The last day can't be before the first day.");
    }
    if (ChronoUnit.DAYS.between(from, t) + 1 > MAX_DAYS) {
      throw ApiException.badRequest("too_long", "Apply for up to " + MAX_DAYS + " days at a time.");
    }
    if (!staff && from.isBefore(today.minusDays(BACKDATE_DAYS))) {
      throw ApiException.badRequest(
          "too_old",
          "Leave can be applied up to " + BACKDATE_DAYS + " days back. Please contact the school.");
    }
    if (from.isAfter(today.plusDays(60))) {
      throw ApiException.badRequest("too_far", "Apply for leave up to 60 days ahead.");
    }
    return new LocalDate[] {from, t};
  }

  private Leave load(UUID id) {
    return jdbc.sql(LEAVE_SELECT + " where l.id = ?")
        .param(id)
        .query(LeaveService::mapLeave)
        .optional()
        .orElseThrow(() -> ApiException.notFound("Leave"));
  }

  private static Leave mapLeave(ResultSet rs, int i) throws SQLException {
    LocalDate from = rs.getObject("from_date", LocalDate.class);
    LocalDate to = rs.getObject("to_date", LocalDate.class);
    String grade = rs.getString("grade");
    return new Leave(
        rs.getObject("id", UUID.class),
        rs.getObject("student_id", UUID.class),
        rs.getString("student_name"),
        grade == null ? null : label(grade, rs.getString("section")),
        from,
        to,
        (int) ChronoUnit.DAYS.between(from, to) + 1,
        rs.getString("reason"),
        rs.getString("source"),
        rs.getString("status"),
        rs.getString("requested_by_name"),
        rs.getString("decided_by_name"),
        ts(rs.getTimestamp("decided_at")),
        rs.getString("decision_note"),
        rs.getTimestamp("created_at").toInstant());
  }

  private static Instant ts(Timestamp t) {
    return t == null ? null : t.toInstant();
  }
}
