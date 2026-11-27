package com.shikshablox.modules.attendance;

import static com.shikshablox.modules.attendance.AttendanceAccess.today;

import com.shikshablox.modules.comms.BroadcastService;
import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.identity.Role;
import com.shikshablox.platform.identity.UserService;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.TenantContext;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Attendance (M07). A roll call is a section on a date (period 0 in daily mode, periods 1..n in
 * period mode; the lowest period counts as the day's attendance). Teachers mark today and can still
 * mark a forgotten day up to a week back; changing a day already marked in the past needs a
 * correction approved by the office. Absent (and optionally late) students' guardians get an alert
 * through the communication hub once per day.
 */
@Service
public class AttendanceService {

  private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);
  static final Set<String> STATUSES = Set.of("P", "A", "L", "H", "LV");
  static final String TEMPLATE = "attendance_alert";
  static final String DEFAULT_BODY =
      "Dear {{name}}, {{title}}. If this is unexpected, please contact the school. {{link}}";
  static final int LATE_MARKING_DAYS = 7;
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

  // ------------------------------------------------------------------ records

  public record Settings(
      String mode,
      int periodsPerDay,
      boolean alertAbsent,
      boolean alertLate,
      int minPercent,
      int concernPercent,
      int concernStreak,
      List<String> reasonCodes,
      boolean periodsAllowed,
      boolean reasonsAllowed,
      boolean concernsAllowed,
      boolean messagingAvailable) {}

  public record SettingsInput(
      String mode,
      Integer periodsPerDay,
      Boolean alertAbsent,
      Boolean alertLate,
      Integer minPercent,
      Integer concernPercent,
      Integer concernStreak,
      List<String> reasonCodes) {}

  public record SectionDay(
      UUID id,
      String label,
      String classTeacher,
      int students,
      boolean marked,
      int present,
      int absent,
      int late,
      int onLeave) {}

  public record Overview(
      LocalDate date,
      String year,
      boolean canManage,
      Settings settings,
      List<SectionDay> sections,
      long pendingLeave,
      long pendingCorrections) {}

  public record SheetRow(
      UUID studentId,
      String name,
      Integer rollNo,
      String admissionNo,
      String status,
      String reason,
      String note,
      String leave,
      String alert,
      boolean correctionPending) {}

  public record Sheet(
      UUID sectionId,
      String label,
      LocalDate date,
      int period,
      String subject,
      boolean marked,
      String markedBy,
      Instant markedAt,
      String editMode,
      List<SheetRow> rows) {}

  public record MarkInput(UUID studentId, String status, String reason, String note) {}

  public record SheetInput(
      UUID sectionId,
      LocalDate date,
      Integer period,
      String subject,
      List<MarkInput> marks,
      String correctionReason) {}

  public record SaveResult(Sheet sheet, int saved, int corrections, int alerts, String message) {}

  public record Teacher(UUID id, String name, String email) {}

  public record Assignment(
      UUID sectionId, String label, String classTeacher, List<Teacher> teachers) {}

  public record Teachers(List<Teacher> staff, List<Assignment> sections) {}

  public record NewTeacher(Teacher teacher, String temporaryPassword, boolean existingAccount) {}

  public record RegisterRow(
      UUID studentId,
      String name,
      Integer rollNo,
      Map<String, String> marks,
      int present,
      int absent,
      int late,
      int halfDays,
      int leave,
      Double percent,
      Double yearPercent,
      boolean belowMinimum) {}

  public record Register(
      UUID sectionId,
      String label,
      String month,
      List<LocalDate> days,
      int minPercent,
      List<RegisterRow> rows) {}

  public record Concern(
      UUID studentId,
      String name,
      String classLabel,
      Double percent,
      int streak,
      LocalDate lastAbsent,
      String why) {}

  public record Absence(LocalDate date, String status, String reason) {}

  public record ChildAttendance(
      UUID studentId,
      String name,
      Double monthPercent,
      Double yearPercent,
      int daysMarked,
      List<Absence> recent) {}

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final AuditService audit;
  private final AttendanceAccess access;
  private final BroadcastService broadcasts;
  private final UserService users;

  AttendanceService(
      JdbcClient jdbc,
      TransactionTemplate tx,
      AuditService audit,
      AttendanceAccess access,
      BroadcastService broadcasts,
      UserService users) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.audit = audit;
    this.access = access;
    this.broadcasts = broadcasts;
    this.users = users;
  }

  // ------------------------------------------------------------------ settings

  public Settings settings() {
    access.requireModule();
    return loadSettings();
  }

  Settings loadSettings() {
    jdbc.sql("insert into attendance_settings (organization_id) values (?) on conflict do nothing")
        .param(TenantContext.requireOrganizationId())
        .update();
    boolean periods = access.feature("periods");
    return jdbc.sql("select * from attendance_settings")
        .query((rs, i) ->
            new Settings(
                periods ? rs.getString("mode") : "DAILY",
                rs.getInt("periods_per_day"),
                rs.getBoolean("alert_absent"),
                rs.getBoolean("alert_late"),
                rs.getInt("min_percent"),
                rs.getInt("concern_percent"),
                rs.getInt("concern_streak"),
                List.of((String[]) rs.getArray("reason_codes").getArray()),
                periods,
                access.feature("reasons"),
                access.feature("concerns"),
                access.hasModule("COMMS") && access.feature("alerts")))
        .single();
  }

  public Settings saveSettings(SettingsInput in) {
    access.requireModule();
    access.requireManage();
    Settings b = loadSettings();
    String mode = in.mode() == null ? b.mode() : in.mode();
    if (!Set.of("DAILY", "PERIOD").contains(mode)) {
      throw ApiException.badRequest("invalid_mode", "Choose daily or period-wise attendance.");
    }
    if ("PERIOD".equals(mode) && !b.periodsAllowed()) {
      throw ApiException.badRequest(
          "feature_not_in_plan", "Period-wise attendance is part of ShikshaBlox Standard.");
    }
    int periods = bounded(in.periodsPerDay(), b.periodsPerDay(), 1, 12, "Periods per day");
    int min = bounded(in.minPercent(), b.minPercent(), 0, 100, "Minimum attendance");
    int concern = bounded(in.concernPercent(), b.concernPercent(), 0, 100, "Concern level");
    int streak = bounded(in.concernStreak(), b.concernStreak(), 2, 30, "Days absent in a row");
    List<String> codes = b.reasonCodes();
    if (in.reasonCodes() != null) {
      LinkedHashSet<String> clean = new LinkedHashSet<>();
      for (String c : in.reasonCodes()) {
        String t = c == null ? "" : c.trim();
        if (!t.isEmpty()) {
          if (t.length() > 40) {
            throw ApiException.badRequest("invalid_reason", "Keep reasons under 40 characters.");
          }
          clean.add(t);
        }
      }
      if (clean.size() > 15) {
        throw ApiException.badRequest("too_many_reasons", "Use up to 15 reasons.");
      }
      codes = List.copyOf(clean);
    }
    boolean alertAbsent = in.alertAbsent() == null ? b.alertAbsent() : in.alertAbsent();
    boolean alertLate = in.alertLate() == null ? b.alertLate() : in.alertLate();
    List<String> finalCodes = codes;
    tx.executeWithoutResult(s -> {
      jdbc.sql(
              "update attendance_settings set mode = ?, periods_per_day = ?, alert_absent = ?, alert_late = ?, min_percent = ?,"
                  + " concern_percent = ?, concern_streak = ?, reason_codes = ?, updated_at = now()")
          .params(
              mode,
              periods,
              alertAbsent,
              alertLate,
              min,
              concern,
              streak,
              finalCodes.toArray(String[]::new))
          .update();
      audit.record(
          "attendance.settings_saved",
          "attendance_settings",
          null,
          null,
          Map.of("mode", mode, "alertAbsent", alertAbsent, "alertLate", alertLate));
    });
    return loadSettings();
  }

  // ------------------------------------------------------------------ overview

  public Overview overview(LocalDate date) {
    access.requireModule();
    LocalDate d = date == null ? today() : date;
    List<UUID> ids = access.sectionIds();
    List<SectionDay> sections =
        ids.isEmpty()
            ? List.of()
            : jdbc.sql(
                    "select c.id, c.grade, c.section, c.class_teacher,"
                        + " (select count(*) from student_enrolment e join student s on s.id = e.student_id"
                        + "   where e.class_section_id = c.id and s.status = 'ENROLLED') students,"
                        + " ds.id is not null marked,"
                        + " (select count(*) from attendance_mark m where m.session_id = ds.id and m.status in ('P', 'L', 'H')) present,"
                        + " (select count(*) from attendance_mark m where m.session_id = ds.id and m.status = 'A') absent,"
                        + " (select count(*) from attendance_mark m where m.session_id = ds.id and m.status = 'L') late,"
                        + " (select count(*) from attendance_mark m where m.session_id = ds.id and m.status = 'LV') on_leave"
                        + " from class_section c left join lateral (select id from attendance_session x"
                        + "   where x.class_section_id = c.id and x.date = ? order by x.period limit 1) ds on true"
                        + " where c.id = any(?::uuid[]) order by c.grade_order, c.grade, c.section")
                .params(d, ids.toArray(UUID[]::new))
                .query((rs, i) ->
                    new SectionDay(
                        rs.getObject("id", UUID.class),
                        label(rs.getString("grade"), rs.getString("section")),
                        rs.getString("class_teacher"),
                        rs.getInt("students"),
                        rs.getBoolean("marked"),
                        rs.getInt("present"),
                        rs.getInt("absent"),
                        rs.getInt("late"),
                        rs.getInt("on_leave")))
                .list();
    UUID[] arr = ids.toArray(UUID[]::new);
    long leave =
        jdbc.sql(
                "select count(*) from leave_request l where l.status = 'PENDING' and exists (select 1 from student_enrolment e"
                    + " where e.student_id = l.student_id and e.class_section_id = any(?::uuid[]))")
            .param(arr)
            .query(Long.class)
            .single();
    long corrections =
        access.canManage()
            ? jdbc.sql("select count(*) from attendance_correction where status = 'PENDING'")
                .query(Long.class)
                .single()
            : jdbc.sql(
                    "select count(*) from attendance_correction where status = 'PENDING' and requested_by = ?")
                .param(access.userId())
                .query(Long.class)
                .single();
    return new Overview(
        d, access.currentYear(), access.canManage(), loadSettings(), sections, leave, corrections);
  }

  // ------------------------------------------------------------------ roll call

  public Sheet sheet(UUID sectionId, LocalDate date, Integer period) {
    access.requireModule();
    access.requireSection(sectionId);
    Settings st = loadSettings();
    int p = period(st, period);
    LocalDate d = date == null ? today() : date;
    return loadSheet(sectionId, d, p, st);
  }

  private Sheet loadSheet(UUID sectionId, LocalDate d, int p, Settings st) {
    record Head(String grade, String section) {}
    Head h =
        jdbc.sql("select grade, section from class_section where id = ?")
            .param(sectionId)
            .query((rs, i) -> new Head(rs.getString(1), rs.getString(2)))
            .single();
    record Sess(UUID id, String subject, String by, Instant at) {}
    Sess s =
        jdbc.sql(
                "select x.id, x.subject, u.display_name, x.updated_at from attendance_session x"
                    + " left join app_user u on u.id = x.marked_by where x.class_section_id = ? and x.date = ? and x.period = ?")
            .params(sectionId, d, p)
            .query((rs, i) ->
                new Sess(
                    rs.getObject(1, UUID.class),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getTimestamp(4).toInstant()))
            .optional()
            .orElse(null);
    List<SheetRow> rows =
        jdbc.sql(
                "select s.id, s.name, e.roll_no, s.admission_no, m.status, m.reason, m.note,"
                    + " (select max(l.status) from leave_request l where l.student_id = s.id and l.status in ('APPROVED', 'PENDING')"
                    + "   and ? between l.from_date and l.to_date) leave,"
                    + " (select coalesce(a.broadcast_id::text, '') || '|' || coalesce(a.skipped_reason, '') from attendance_alert a"
                    + "   where a.student_id = s.id and a.date = ? order by a.kind limit 1) alert,"
                    + " exists (select 1 from attendance_correction c where c.session_id = ? and c.student_id = s.id and c.status = 'PENDING') corr"
                    + " from student_enrolment e join student s on s.id = e.student_id"
                    + " left join attendance_mark m on m.session_id = ? and m.student_id = s.id"
                    + " where e.class_section_id = ? and (s.status = 'ENROLLED' or m.status is not null)"
                    + " order by e.roll_no nulls last, s.name")
            .params(d, d, s == null ? null : s.id(), s == null ? null : s.id(), sectionId)
            .query((rs, i) ->
                new SheetRow(
                    rs.getObject(1, UUID.class),
                    rs.getString(2),
                    (Integer) rs.getObject(3),
                    rs.getString(4),
                    rs.getString(5),
                    rs.getString(6),
                    rs.getString(7),
                    rs.getString(8),
                    rs.getString(9),
                    rs.getBoolean(10)))
            .list();
    rows = withAlertStatus(rows);
    return new Sheet(
        sectionId,
        label(h.grade(), h.section()),
        d,
        p,
        s == null ? null : s.subject(),
        s != null,
        s == null ? null : s.by(),
        s == null ? null : s.at(),
        editMode(d, s != null),
        rows);
  }

  /** Turns "broadcastId|skipReason" into a short status for the teacher. */
  private List<SheetRow> withAlertStatus(List<SheetRow> rows) {
    List<UUID> ids = new ArrayList<>();
    for (SheetRow r : rows) {
      if (r.alert() != null && !r.alert().startsWith("|")) {
        ids.add(UUID.fromString(r.alert().substring(0, r.alert().indexOf('|'))));
      }
    }
    Map<UUID, BroadcastService.Stats> stats = ids.isEmpty() ? Map.of() : broadcasts.stats(ids);
    List<SheetRow> out = new ArrayList<>();
    for (SheetRow r : rows) {
      String a = r.alert();
      String text = null;
      if (a != null) {
        int bar = a.indexOf('|');
        if (bar == 0) {
          text = "Not sent: " + a.substring(1);
        } else {
          BroadcastService.Stats st = stats.get(UUID.fromString(a.substring(0, bar)));
          text =
              st == null
                  ? "Alert removed"
                  : st.sent() > 0
                      ? (st.testMode() > 0 ? "Alert sent (test mode)" : "Alert sent")
                      : st.total() > 0 && st.skipped() + st.failed() == st.total()
                          ? "Alert not delivered"
                          : "Alert queued";
        }
      }
      out.add(
          new SheetRow(
              r.studentId(),
              r.name(),
              r.rollNo(),
              r.admissionNo(),
              r.status(),
              r.reason(),
              r.note(),
              r.leave(),
              text,
              r.correctionPending()));
    }
    return out;
  }

  /** DIRECT: save as is. CORRECTION: changes wait for approval. READ_ONLY: nothing can change. */
  String editMode(LocalDate d, boolean marked) {
    return editMode(d, marked, today(), access.canManage(), access.feature("corrections"));
  }

  static String editMode(
      LocalDate d, boolean marked, LocalDate today, boolean manager, boolean corrections) {
    if (d.isAfter(today)) {
      return "READ_ONLY";
    }
    if (d.equals(today) || manager) {
      return "DIRECT";
    }
    if (!marked) {
      return d.isBefore(today.minusDays(LATE_MARKING_DAYS)) ? "READ_ONLY" : "DIRECT";
    }
    return corrections ? "CORRECTION" : "READ_ONLY";
  }

  public SaveResult save(SheetInput in) {
    access.requireModule();
    if (in.sectionId() == null || in.date() == null || in.marks() == null) {
      throw ApiException.badRequest("missing_field", "Class, date and marks are required.");
    }
    access.requireSection(in.sectionId());
    Settings st = loadSettings();
    int p = period(st, in.period());
    LocalDate d = in.date();
    if (in.marks().size() > 500) {
      throw ApiException.badRequest("too_many", "Too many students in one roll call.");
    }
    String subject = optional(in.subject(), 60, "Subject");
    boolean exists =
        jdbc.sql(
                    "select count(*) from attendance_session where class_section_id = ? and date = ? and period = ?")
                .params(in.sectionId(), d, p)
                .query(Long.class)
                .single()
            > 0;
    String mode = editMode(d, exists);
    if ("READ_ONLY".equals(mode)) {
      throw ApiException.conflict(
          "read_only",
          d.isAfter(today())
              ? "Attendance can't be marked for a future date."
              : "This day can no longer be changed here. Please ask the school office.");
    }
    Set<UUID> roster =
        new LinkedHashSet<>(
            jdbc.sql(
                    "select e.student_id from student_enrolment e join student s on s.id = e.student_id"
                        + " where e.class_section_id = ? and (s.status = 'ENROLLED' or exists (select 1 from attendance_mark m"
                        + "   join attendance_session x on x.id = m.session_id where m.student_id = s.id and x.class_section_id = ?"
                        + "   and x.date = ? and x.period = ?))")
                .params(in.sectionId(), in.sectionId(), d, p)
                .query(UUID.class)
                .list());
    Set<String> codes = Set.copyOf(st.reasonCodes());
    List<MarkInput> marks = new ArrayList<>();
    for (MarkInput m : in.marks()) {
      if (m == null || m.studentId() == null || !roster.contains(m.studentId())) {
        throw ApiException.badRequest("invalid_student", "A student is not in this class.");
      }
      String status = m.status() == null ? "" : m.status().trim().toUpperCase(Locale.ROOT);
      if (!STATUSES.contains(status)) {
        throw ApiException.badRequest(
            "invalid_status", "Use present, absent, late, half day or leave.");
      }
      String reason = optional(m.reason(), 60, "Reason");
      if (reason != null && (!st.reasonsAllowed() || "P".equals(status))) {
        reason = null;
      }
      if (reason != null && !codes.contains(reason)) {
        throw ApiException.badRequest(
            "invalid_reason", "Choose one of the school's absence reasons.");
      }
      marks.add(new MarkInput(m.studentId(), status, reason, optional(m.note(), 300, "Note")));
    }
    if ("CORRECTION".equals(mode)) {
      return requestCorrections(in, d, p, marks, st);
    }
    boolean past = d.isBefore(today()) && exists;
    String why = optional(in.correctionReason(), 300, "Reason for the change");
    UUID sessionId =
        tx.execute(s -> {
          UUID sid =
              jdbc.sql(
                      "insert into attendance_session (organization_id, class_section_id, date, period, subject, marked_by)"
                          + " values (?, ?, ?, ?, ?, ?) on conflict (class_section_id, date, period) do update set"
                          + " subject = excluded.subject, marked_by = excluded.marked_by, updated_at = now() returning id")
                  .params(
                      TenantContext.requireOrganizationId(),
                      in.sectionId(),
                      d,
                      p,
                      subject,
                      access.userId())
                  .query(UUID.class)
                  .single();
          Map<UUID, String> before = new HashMap<>();
          jdbc.sql("select student_id, status from attendance_mark where session_id = ?")
              .param(sid)
              .query((rs, i) -> before.put(rs.getObject(1, UUID.class), rs.getString(2)))
              .list();
          List<Map<String, Object>> changes = new ArrayList<>();
          for (MarkInput m : marks) {
            jdbc.sql(
                    "insert into attendance_mark (organization_id, session_id, student_id, status, reason, note)"
                        + " values (?, ?, ?, ?, ?, ?) on conflict (session_id, student_id) do update set"
                        + " status = excluded.status, reason = excluded.reason, note = excluded.note")
                .params(
                    TenantContext.requireOrganizationId(),
                    sid,
                    m.studentId(),
                    m.status(),
                    m.reason(),
                    m.note())
                .update();
            String was = before.get(m.studentId());
            if (past && was != null && !was.equals(m.status())) {
              changes.add(
                  Map.of("student", m.studentId().toString(), "from", was, "to", m.status()));
            }
          }
          if (!changes.isEmpty()) {
            jdbc.sql(
                    "update attendance_correction set status = 'APPROVED', decided_by = ?, decided_at = now()"
                        + " where session_id = ? and status = 'PENDING'")
                .params(access.userId(), sid)
                .update();
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("changes", changes);
            after.put("reason", why == null ? "" : why);
            audit.record("attendance.corrected", "attendance_session", sid.toString(), null, after);
          }
          audit.record(
              "attendance.marked",
              "attendance_session",
              sid.toString(),
              null,
              Map.of(
                  "date", d.toString(),
                  "period", p,
                  "absent", marks.stream().filter(m -> "A".equals(m.status())).count()));
          return sid;
        });
    int alerts = 0;
    if (d.equals(today()) && p == firstPeriod(in.sectionId(), d)) {
      alerts = sendAlerts(marks, d, st);
    }
    return new SaveResult(
        loadSheet(in.sectionId(), d, p, st),
        marks.size(),
        0,
        alerts,
        alerts > 0 ? "Saved. Guardians of " + alerts + " student(s) are being alerted." : "Saved.");
  }

  /** The lowest period marked for the day (that one counts as the day's attendance). */
  private int firstPeriod(UUID sectionId, LocalDate d) {
    return jdbc.sql(
            "select coalesce(min(period), 0) from attendance_session where class_section_id = ? and date = ?")
        .params(sectionId, d)
        .query(Integer.class)
        .single();
  }

  private SaveResult requestCorrections(
      SheetInput in, LocalDate d, int p, List<MarkInput> marks, Settings st) {
    String why = optional(in.correctionReason(), 300, "Reason for the change");
    if (why == null) {
      throw ApiException.badRequest(
          "missing_reason", "Say why the attendance of a past day needs to change.");
    }
    int n =
        tx.execute(s -> {
          UUID sid =
              jdbc.sql(
                      "select id from attendance_session where class_section_id = ? and date = ? and period = ?")
                  .params(in.sectionId(), d, p)
                  .query(UUID.class)
                  .single();
          Map<UUID, String[]> before = new HashMap<>();
          jdbc.sql("select student_id, status, reason from attendance_mark where session_id = ?")
              .param(sid)
              .query((rs, i) ->
                  before.put(
                      rs.getObject(1, UUID.class), new String[] {rs.getString(2), rs.getString(3)}))
              .list();
          int count = 0;
          for (MarkInput m : marks) {
            String[] was = before.get(m.studentId());
            if (was != null
                && was[0].equals(m.status())
                && java.util.Objects.equals(was[1], m.reason())) {
              continue;
            }
            jdbc.sql(
                    "delete from attendance_correction where session_id = ? and student_id = ? and status = 'PENDING'")
                .params(sid, m.studentId())
                .update();
            jdbc.sql(
                    "insert into attendance_correction (organization_id, session_id, student_id, from_status, to_status,"
                        + " to_reason, reason, requested_by) values (?, ?, ?, ?, ?, ?, ?, ?)")
                .params(
                    TenantContext.requireOrganizationId(),
                    sid,
                    m.studentId(),
                    was == null ? null : was[0],
                    m.status(),
                    m.reason(),
                    why,
                    access.userId())
                .update();
            count++;
          }
          if (count > 0) {
            audit.record(
                "attendance.correction_requested",
                "attendance_session",
                sid.toString(),
                null,
                Map.of("changes", count, "reason", why));
          }
          return count;
        });
    return new SaveResult(
        loadSheet(in.sectionId(), d, p, st),
        0,
        n,
        0,
        n == 0 ? "Nothing changed." : n + " change(s) sent to the school office for approval.");
  }

  /** One alert per student per day and kind; never for students on approved leave. */
  private int sendAlerts(List<MarkInput> marks, LocalDate d, Settings st) {
    if (!st.messagingAvailable()) {
      return 0;
    }
    int sent = 0;
    for (MarkInput m : marks) {
      String kind =
          "A".equals(m.status()) && st.alertAbsent()
              ? "ABSENT"
              : "L".equals(m.status()) && st.alertLate() ? "LATE" : null;
      if (kind == null) {
        continue;
      }
      try {
        boolean done =
            Boolean.TRUE.equals(
                tx.execute(s -> {
                  boolean already =
                      jdbc.sql(
                                  "select count(*) from attendance_alert where student_id = ? and date = ? and kind = ?")
                              .params(m.studentId(), d, kind)
                              .query(Long.class)
                              .single()
                          > 0;
                  boolean onLeave =
                      jdbc.sql(
                                  "select count(*) from leave_request where student_id = ? and status = 'APPROVED'"
                                      + " and ? between from_date and to_date")
                              .params(m.studentId(), d)
                              .query(Long.class)
                              .single()
                          > 0;
                  if (already || onLeave) {
                    return false;
                  }
                  String name =
                      jdbc.sql("select name from student where id = ?")
                          .param(m.studentId())
                          .query(String.class)
                          .single();
                  List<String> phones =
                      jdbc.sql(
                              "select g.phone from student_guardian sg join guardian g on g.id = sg.guardian_id"
                                  + " where sg.student_id = ? and g.phone is not null")
                          .param(m.studentId())
                          .query(String.class)
                          .list();
                  String title =
                      "ABSENT".equals(kind)
                          ? name + " was marked absent today (" + DAY.format(d) + ")"
                          : name + " arrived late today (" + DAY.format(d) + ")";
                  BroadcastService.AlertResult r =
                      broadcasts.alert(
                          title,
                          "ABSENT".equals(kind)
                              ? "If your child is unwell or away, you can apply for leave in the parent portal on the school website."
                                  + " If you believe this is a mistake, please contact the class teacher."
                              : "Please help your child reach school on time.",
                          TEMPLATE,
                          DEFAULT_BODY,
                          phones);
                  jdbc.sql(
                          "insert into attendance_alert (organization_id, student_id, date, kind, broadcast_id, skipped_reason)"
                              + " values (?, ?, ?, ?, ?, ?)")
                      .params(
                          TenantContext.requireOrganizationId(),
                          m.studentId(),
                          d,
                          kind,
                          r.broadcastId(),
                          r.skippedReason())
                      .update();
                  return r.broadcastId() != null;
                }));
        if (done) {
          sent++;
        }
      } catch (RuntimeException e) {
        log.warn("Attendance alert for {} failed: {}", m.studentId(), e.getMessage());
      }
    }
    return sent;
  }

  // ------------------------------------------------------------------ teachers

  public Teachers teachers() {
    access.requireModule();
    access.requireManage();
    UUID org = TenantContext.requireOrganizationId();
    List<Teacher> staff =
        jdbc.sql(
                "select distinct u.id, u.display_name, u.email from role_assignment r join app_user u on u.id = r.user_id"
                    + " where r.organization_id = ? and r.role in ('SCHOOL_ADMIN', 'TEACHER') and u.status = 'ACTIVE'"
                    + " order by u.display_name")
            .param(org)
            .query((rs, i) ->
                new Teacher(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)))
            .list();
    Map<UUID, Teacher> byId = new HashMap<>();
    staff.forEach(t -> byId.put(t.id(), t));
    Map<UUID, List<Teacher>> assigned = new HashMap<>();
    jdbc.sql("select class_section_id, user_id from attendance_teacher")
        .query((rs, i) -> {
          Teacher t = byId.get(rs.getObject(2, UUID.class));
          if (t != null) {
            assigned.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>()).add(t);
          }
          return null;
        })
        .list();
    List<Assignment> sections =
        jdbc.sql(
                "select id, grade, section, class_teacher from class_section where academic_year = ?"
                    + " order by grade_order, grade, section")
            .param(access.currentYear())
            .query((rs, i) -> {
              UUID id = rs.getObject(1, UUID.class);
              return new Assignment(
                  id,
                  label(rs.getString(2), rs.getString(3)),
                  rs.getString(4),
                  assigned.getOrDefault(id, List.of()));
            })
            .list();
    return new Teachers(staff, sections);
  }

  /** Creates a teacher account (or gives an existing account the teacher role). */
  public NewTeacher addTeacher(String name, String email) {
    access.requireModule();
    access.requireManage();
    String n = required(name, 120, "Name");
    String e = required(email, 200, "Email").toLowerCase(Locale.ROOT);
    UUID org = TenantContext.requireOrganizationId();
    boolean existing = users.exists(e);
    String password = existing ? null : temporaryPassword();
    // Not inside a transaction: provisioning runs in the system context.
    UUID id = users.ensureUser(e, n, password);
    users.grantRole(id, org, Role.TEACHER);
    audit.record("attendance.teacher_added", "app_user", id.toString(), null, Map.of("email", e));
    String display =
        jdbc.sql("select display_name from app_user where id = ?")
            .param(id)
            .query(String.class)
            .optional()
            .orElse(n);
    return new NewTeacher(new Teacher(id, display, e), password, password == null);
  }

  public Teachers assign(UUID sectionId, List<UUID> userIds) {
    access.requireModule();
    access.requireManage();
    access.requireSection(sectionId);
    Teachers t = teachers();
    Set<UUID> allowed = new java.util.HashSet<>();
    t.staff().forEach(x -> allowed.add(x.id()));
    List<UUID> ids = userIds == null ? List.of() : List.copyOf(new LinkedHashSet<>(userIds));
    for (UUID u : ids) {
      if (!allowed.contains(u)) {
        throw ApiException.badRequest("invalid_teacher", "Choose teachers of this school.");
      }
    }
    if (ids.size() > 10) {
      throw ApiException.badRequest("too_many", "Assign up to 10 teachers to a class.");
    }
    tx.executeWithoutResult(s -> {
      jdbc.sql("delete from attendance_teacher where class_section_id = ?")
          .param(sectionId)
          .update();
      for (UUID u : ids) {
        jdbc.sql(
                "insert into attendance_teacher (organization_id, class_section_id, user_id) values (?, ?, ?)")
            .params(TenantContext.requireOrganizationId(), sectionId, u)
            .update();
      }
      // The first teacher becomes the class teacher shown to parents, if none is set yet.
      if (!ids.isEmpty()) {
        jdbc.sql(
                "update class_section set class_teacher = (select display_name from app_user where id = ?)"
                    + " where id = ? and (class_teacher is null or class_teacher = '')")
            .params(ids.get(0), sectionId)
            .update();
      }
      audit.record(
          "attendance.teachers_assigned",
          "class_section",
          sectionId.toString(),
          null,
          Map.of("teachers", ids.size()));
    });
    return teachers();
  }

  // ------------------------------------------------------------------ registers

  private static final String DAY_SESSIONS =
      "with ds as (select distinct on (class_section_id, date) id, class_section_id, date from attendance_session"
          + " where date between ? and ? order by class_section_id, date, period) ";

  public Register register(UUID sectionId, String month) {
    access.requireModule();
    access.requireSection(sectionId);
    YearMonth ym;
    try {
      ym = month == null || month.isBlank() ? YearMonth.from(today()) : YearMonth.parse(month);
    } catch (RuntimeException e) {
      throw ApiException.badRequest("invalid_month", "Use a month like 2026-11.");
    }
    record Head(String grade, String section, String year) {}
    Head h =
        jdbc.sql("select grade, section, academic_year from class_section where id = ?")
            .param(sectionId)
            .query((rs, i) -> new Head(rs.getString(1), rs.getString(2), rs.getString(3)))
            .single();
    LocalDate from = ym.atDay(1);
    LocalDate to = ym.atEndOfMonth();
    List<LocalDate> days =
        jdbc.sql(DAY_SESSIONS + "select date from ds where class_section_id = ? order by date")
            .params(from, to, sectionId)
            .query((rs, i) -> rs.getObject(1, LocalDate.class))
            .list();
    record Student(UUID id, String name, Integer roll) {}
    List<Student> students =
        jdbc.sql(
                "select s.id, s.name, e.roll_no from student_enrolment e join student s on s.id = e.student_id"
                    + " where e.class_section_id = ? order by e.roll_no nulls last, s.name")
            .param(sectionId)
            .query((rs, i) ->
                new Student(
                    rs.getObject(1, UUID.class), rs.getString(2), (Integer) rs.getObject(3)))
            .list();
    Map<UUID, Map<String, String>> marks = new HashMap<>();
    jdbc.sql(
            DAY_SESSIONS
                + "select m.student_id, ds.date, m.status from ds join attendance_mark m on m.session_id = ds.id"
                + " where ds.class_section_id = ?")
        .params(from, to, sectionId)
        .query((rs, i) ->
            marks
                .computeIfAbsent(rs.getObject(1, UUID.class), k -> new LinkedHashMap<>())
                .put(rs.getObject(2, LocalDate.class).toString(), rs.getString(3)))
        .list();
    Map<UUID, Double> year =
        yearPercents(
            students.stream().map(Student::id).toList(),
            AttendanceAccess.yearStart(h.year()),
            AttendanceAccess.yearEnd(h.year()));
    Settings st = loadSettings();
    List<RegisterRow> rows = new ArrayList<>();
    for (Student s : students) {
      Map<String, String> m = marks.getOrDefault(s.id(), Map.of());
      if (m.isEmpty()
          && jdbc.sql("select status from student where id = ?")
              .param(s.id())
              .query(String.class)
              .single()
              .equals("WITHDRAWN")) {
        continue;
      }
      int[] c = counts(m.values());
      Double yp = year.get(s.id());
      rows.add(
          new RegisterRow(
              s.id(),
              s.name(),
              s.roll(),
              m,
              c[0],
              c[1],
              c[2],
              c[3],
              c[4],
              percent(c),
              yp,
              yp != null && yp < st.minPercent()));
    }
    return new Register(
        sectionId, label(h.grade(), h.section()), ym.toString(), days, st.minPercent(), rows);
  }

  public String registerCsv(UUID sectionId, String month) {
    Register r = register(sectionId, month);
    StringBuilder out = new StringBuilder("Roll no,Student");
    for (LocalDate d : r.days()) {
      out.append(',').append(d.getDayOfMonth());
    }
    out.append(",Present,Absent,Late,Half day,Leave,Month %,Year %\n");
    for (RegisterRow row : r.rows()) {
      out.append(row.rollNo() == null ? "" : row.rollNo()).append(',').append(cell(row.name()));
      for (LocalDate d : r.days()) {
        out.append(',').append(row.marks().getOrDefault(d.toString(), ""));
      }
      out.append(',')
          .append(row.present())
          .append(',')
          .append(row.absent())
          .append(',')
          .append(row.late())
          .append(',')
          .append(row.halfDays())
          .append(',')
          .append(row.leave())
          .append(',')
          .append(row.percent() == null ? "" : row.percent())
          .append(',')
          .append(row.yearPercent() == null ? "" : row.yearPercent())
          .append('\n');
    }
    audit.record(
        "attendance.register_exported",
        "class_section",
        sectionId.toString(),
        null,
        Map.of("month", r.month()));
    return out.toString();
  }

  /** Students' attendance % over a date range (only days they were marked). */
  Map<UUID, Double> yearPercents(List<UUID> students, LocalDate from, LocalDate to) {
    Map<UUID, List<String>> by = new HashMap<>();
    if (students.isEmpty()) {
      return Map.of();
    }
    jdbc.sql(
            DAY_SESSIONS
                + "select m.student_id, m.status from ds join attendance_mark m on m.session_id = ds.id"
                + " where m.student_id = any(?::uuid[])")
        .params(from, to, students.toArray(UUID[]::new))
        .query((rs, i) ->
            by.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                .add(rs.getString(2)))
        .list();
    Map<UUID, Double> out = new HashMap<>();
    by.forEach((k, v) -> out.put(k, percent(counts(v))));
    return out;
  }

  /** present, absent, late, half days, leave. */
  static int[] counts(java.util.Collection<String> statuses) {
    int[] c = new int[5];
    for (String s : statuses) {
      switch (s) {
        case "P" -> c[0]++;
        case "A" -> c[1]++;
        case "L" -> c[2]++;
        case "H" -> c[3]++;
        default -> c[4]++;
      }
    }
    return c;
  }

  /** Late counts as present, a half day as half; leave counts as not present. */
  static Double percent(int[] c) {
    int total = c[0] + c[1] + c[2] + c[3] + c[4];
    if (total == 0) {
      return null;
    }
    double present = c[0] + c[2] + c[3] * 0.5;
    return Math.round(present * 1000.0 / total) / 10.0;
  }

  // ------------------------------------------------------------------ concerns

  public List<Concern> concerns() {
    access.requireModule();
    if (!access.feature("concerns")) {
      throw new ApiException(
          org.springframework.http.HttpStatus.FORBIDDEN,
          "feature_not_in_plan",
          "Chronic-absence alerts are part of ShikshaBlox Standard.");
    }
    Settings st = loadSettings();
    String year = access.currentYear();
    List<UUID> sections = access.sectionIds();
    if (sections.isEmpty()) {
      return List.of();
    }
    record Row(UUID id, String name, String grade, String section) {}
    Map<UUID, Row> students = new LinkedHashMap<>();
    jdbc.sql(
            "select s.id, s.name, c.grade, c.section from student_enrolment e join student s on s.id = e.student_id"
                + " join class_section c on c.id = e.class_section_id where e.class_section_id = any(?::uuid[])"
                + " and s.status = 'ENROLLED' order by c.grade_order, c.grade, c.section, s.name")
        .param(sections.toArray(UUID[]::new))
        .query((rs, i) ->
            students.put(
                rs.getObject(1, UUID.class),
                new Row(
                    rs.getObject(1, UUID.class),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getString(4))))
        .list();
    if (students.isEmpty()) {
      return List.of();
    }
    Map<UUID, List<String[]>> marks = new HashMap<>();
    jdbc.sql(
            DAY_SESSIONS
                + "select m.student_id, ds.date, m.status from ds join attendance_mark m on m.session_id = ds.id"
                + " where m.student_id = any(?::uuid[]) order by ds.date")
        .params(
            AttendanceAccess.yearStart(year),
            AttendanceAccess.yearEnd(year),
            students.keySet().toArray(UUID[]::new))
        .query((rs, i) ->
            marks
                .computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                .add(new String[] {rs.getString(2), rs.getString(3)}))
        .list();
    List<Concern> out = new ArrayList<>();
    for (Row r : students.values()) {
      List<String[]> m = marks.getOrDefault(r.id(), List.of());
      if (m.isEmpty()) {
        continue;
      }
      Double pct = percent(counts(m.stream().map(x -> x[1]).toList()));
      int streak = streak(m.stream().map(x -> x[1]).toList());
      LocalDate last = null;
      for (String[] x : m) {
        if ("A".equals(x[1])) {
          last = LocalDate.parse(x[0]);
        }
      }
      boolean low = m.size() >= 5 && pct != null && pct < st.concernPercent();
      boolean run = streak >= st.concernStreak();
      if (low || run) {
        String why =
            run && low
                ? "Absent " + streak + " days in a row; attendance " + pct + "%"
                : run ? "Absent " + streak + " days in a row" : "Attendance " + pct + "%";
        out.add(
            new Concern(r.id(), r.name(), label(r.grade(), r.section()), pct, streak, last, why));
      }
    }
    out.sort(
        java.util.Comparator.comparing((Concern c) -> -c.streak())
            .thenComparing(c -> c.percent() == null ? 100 : c.percent()));
    return out;
  }

  /** Absences in a row up to the latest marked day (leave doesn't break or extend it). */
  static int streak(List<String> statuses) {
    int n = 0;
    for (int i = statuses.size() - 1; i >= 0; i--) {
      String s = statuses.get(i);
      if ("A".equals(s)) {
        n++;
      } else if (!"LV".equals(s)) {
        break;
      }
    }
    return n;
  }

  // ------------------------------------------------------------------ parent portal

  /** A child's attendance for the parent portal (caller has checked the child is theirs). */
  public ChildAttendance child(UUID studentId) {
    if (!access.hasModule(AttendanceAccess.MODULE)) {
      return null;
    }
    String name =
        jdbc.sql("select name from student where id = ?")
            .param(studentId)
            .query(String.class)
            .single();
    String year = access.currentYear();
    LocalDate today = today();
    Map<UUID, Double> y =
        yearPercents(
            List.of(studentId), AttendanceAccess.yearStart(year), AttendanceAccess.yearEnd(year));
    Map<UUID, Double> mo = yearPercents(List.of(studentId), today.withDayOfMonth(1), today);
    long days =
        jdbc.sql(
                DAY_SESSIONS
                    + "select count(*) from ds join attendance_mark m on m.session_id = ds.id where m.student_id = ?")
            .params(AttendanceAccess.yearStart(year), AttendanceAccess.yearEnd(year), studentId)
            .query(Long.class)
            .single();
    List<Absence> recent =
        jdbc.sql(
                DAY_SESSIONS
                    + "select ds.date, m.status, m.reason from ds join attendance_mark m on m.session_id = ds.id"
                    + " where m.student_id = ? and m.status <> 'P' order by ds.date desc limit 10")
            .params(today.minusDays(90), today, studentId)
            .query((rs, i) ->
                new Absence(rs.getObject(1, LocalDate.class), rs.getString(2), rs.getString(3)))
            .list();
    return new ChildAttendance(
        studentId, name, mo.get(studentId), y.get(studentId), (int) days, recent);
  }

  // ------------------------------------------------------------------ helpers

  private int period(Settings st, Integer period) {
    if ("DAILY".equals(st.mode())) {
      return 0;
    }
    int p = period == null ? 1 : period;
    if (p < 1 || p > st.periodsPerDay()) {
      throw ApiException.badRequest(
          "invalid_period", "Choose a period from 1 to " + st.periodsPerDay() + ".");
    }
    return p;
  }

  static String label(String grade, String section) {
    return grade.trim() + " " + section.trim();
  }

  private static int bounded(Integer v, int dflt, int min, int max, String label) {
    int x = v == null ? dflt : v;
    if (x < min || x > max) {
      throw ApiException.badRequest(
          "invalid_number", label + " must be between " + min + " and " + max + ".");
    }
    return x;
  }

  static String required(String v, int max, String label) {
    String t = v == null ? "" : v.trim();
    if (t.isEmpty()) {
      throw ApiException.badRequest("missing_field", label + " is required.");
    }
    if (t.length() > max) {
      throw ApiException.badRequest("too_long", label + " is too long (max " + max + ").");
    }
    return t;
  }

  static String optional(String v, int max, String label) {
    return v == null || v.isBlank() ? null : required(v, max, label);
  }

  static String cell(String v) {
    if (v == null) {
      return "";
    }
    String t = v.matches("^[=+\\-@].*") ? "'" + v : v;
    return t.contains(",") || t.contains("\"") || t.contains("\n")
        ? "\"" + t.replace("\"", "\"\"") + "\""
        : t;
  }

  static String temporaryPassword() {
    String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < 14; i++) {
      b.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
    }
    return b.toString();
  }

  static Timestamp ts(Instant i) {
    return i == null ? null : Timestamp.from(i);
  }
}
