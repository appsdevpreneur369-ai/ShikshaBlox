package com.shikshablox.modules.admissions;

import com.shikshablox.modules.website.EnquiryService;
import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.entitlement.EntitlementService;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.TenantContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * M03 Admissions CRM. Every website enquiry becomes a lead (when the school's plan includes the
 * CRM); counsellors add walk-in and phone leads, move them through the pipeline, log calls and
 * visits, and schedule follow-ups. Medium schools also get seat capacity per class and funnel
 * analytics.
 */
@Service
public class AdmissionsService {

  private static final Logger log = LoggerFactory.getLogger(AdmissionsService.class);
  static final String MODULE = "ADMISSIONS_CRM";
  static final ZoneId IST = ZoneId.of("Asia/Kolkata");

  /** Pipeline order. LOST is terminal and needs a reason. */
  public static final List<String> STAGES =
      List.of(
          "NEW",
          "CONTACTED",
          "VISIT_SCHEDULED",
          "VISITED",
          "APPLIED",
          "OFFERED",
          "ADMITTED",
          "LOST");

  static final Set<String> SOURCES =
      Set.of("WEBSITE", "WALK_IN", "PHONE", "WHATSAPP", "REFERRAL", "CAMPAIGN", "OTHER");
  static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH");
  static final Set<String> LOG_KINDS = Set.of("NOTE", "CALL", "VISIT", "MESSAGE");
  private static final Pattern PHONE = Pattern.compile("^[+0-9 \\-]{8,20}$");
  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
  private static final Pattern YEAR = Pattern.compile("^\\d{4}-\\d{2}$");

  public record Lead(
      UUID id,
      UUID enquiryId,
      String studentName,
      String guardianName,
      String phone,
      String email,
      String gradeSought,
      String academicYear,
      String source,
      String stage,
      String lostReason,
      String priority,
      UUID assignedTo,
      String assignedName,
      Instant nextFollowUpAt,
      String previousSchool,
      String notes,
      Instant createdAt,
      Instant updatedAt) {}

  public record Activity(
      UUID id,
      String kind,
      String body,
      String fromStage,
      String toStage,
      String actorName,
      Instant createdAt) {}

  public record LeadDetail(Lead lead, List<Activity> activities) {}

  /** Fields for a new lead or an edit; nulls mean "unchanged" on edits. */
  public record LeadInput(
      String studentName,
      String guardianName,
      String phone,
      String email,
      String gradeSought,
      String academicYear,
      String source,
      String priority,
      UUID assignedTo,
      Instant nextFollowUpAt,
      String previousSchool,
      String notes) {}

  public record Filter(
      String stage,
      String source,
      String academicYear,
      UUID assignedTo,
      String grade,
      String q,
      String due) {}

  public record Seat(String grade, int capacity, int sortOrder, long admitted, long offered) {}

  public record Funnel(
      String academicYear,
      Map<String, Long> byStage,
      Map<String, Long> bySource,
      long total,
      long admitted,
      long lost,
      Double conversionPercent) {}

  public record Counsellor(UUID id, String name, String email) {}

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final AuditService audit;
  private final EntitlementService entitlements;

  AdmissionsService(
      JdbcClient jdbc,
      TransactionTemplate tx,
      AuditService audit,
      EntitlementService entitlements) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.audit = audit;
    this.entitlements = entitlements;
  }

  /**
   * The admissions year that is open today. From October, enquiries are for the year starting next
   * June (Nov 2026 → 2027-28); from April to September they are mid-year joins (Jul 2027 →
   * 2027-28).
   */
  public static String currentAcademicYear(LocalDate today) {
    int start = today.getMonthValue() >= 10 ? today.getYear() + 1 : today.getYear();
    return start + "-" + String.format("%02d", (start + 1) % 100);
  }

  static String currentAcademicYear() {
    return currentAcademicYear(LocalDate.now(IST));
  }

  // ------------------------------------------------------------------ website enquiries

  /** Website enquiry → lead, for schools whose plan includes the CRM. */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  void onEnquiry(EnquiryService.EnquiryReceived e) {
    try (var ignored = TenantContext.forTenant(e.organizationId(), null)) {
      if (!entitlements.hasModule(MODULE)) {
        return;
      }
      tx.executeWithoutResult(s -> fromEnquiry(e.enquiryId()));
    } catch (RuntimeException ex) {
      log.warn("Could not create a lead from enquiry {}: {}", e.enquiryId(), ex.getMessage());
    }
  }

  private UUID fromEnquiry(UUID enquiryId) {
    record Enq(String name, String phone, String email, String grade, String message) {}
    Enq q =
        jdbc.sql("select name, phone, email, grade_sought, message from enquiry where id = ?")
            .param(enquiryId)
            .query((rs, i) ->
                new Enq(
                    rs.getString(1),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getString(5)))
            .optional()
            .orElse(null);
    if (q == null) {
      return null;
    }
    UUID id =
        jdbc.sql(
                "insert into admission_lead (organization_id, enquiry_id, guardian_name, phone, email,"
                    + " grade_sought, academic_year, source, notes, next_follow_up_at)"
                    + " values (?, ?, ?, ?, ?, ?, ?, 'WEBSITE', ?, now() + interval '1 day')"
                    + " on conflict (enquiry_id) do nothing returning id")
            .params(
                TenantContext.requireOrganizationId(),
                enquiryId,
                q.name(),
                q.phone(),
                q.email(),
                q.grade(),
                currentAcademicYear(),
                truncate(q.message(), 4000))
            .query(UUID.class)
            .optional()
            .orElse(null);
    if (id != null) {
      activity(id, "CREATED", "From the website enquiry form", null, "NEW");
    }
    return id;
  }

  /** Turns website enquiries that have no lead yet into leads (e.g. after an upgrade). */
  public int importEnquiries() {
    entitlements.requireModule(MODULE);
    return tx.execute(s -> {
      List<UUID> ids =
          jdbc.sql(
                  "select e.id from enquiry e where e.status <> 'SPAM' and not exists"
                      + " (select 1 from admission_lead l where l.enquiry_id = e.id)"
                      + " order by e.created_at limit 1000")
              .query(UUID.class)
              .list();
      int n = 0;
      for (UUID id : ids) {
        if (fromEnquiry(id) != null) {
          n++;
        }
      }
      audit.record(
          "admissions.enquiries_imported", "admission_lead", null, null, Map.of("count", n));
      return n;
    });
  }

  // ------------------------------------------------------------------ leads

  public List<Lead> list(Filter f) {
    entitlements.requireModule(MODULE);
    StringBuilder sql = new StringBuilder(LEAD_SELECT + " where true");
    List<Object> p = new ArrayList<>();
    if (notBlank(f.stage())) {
      sql.append(" and l.stage = ?");
      p.add(f.stage());
    }
    if (notBlank(f.source())) {
      sql.append(" and l.source = ?");
      p.add(f.source());
    }
    if (notBlank(f.academicYear())) {
      sql.append(" and l.academic_year = ?");
      p.add(f.academicYear());
    }
    if (f.assignedTo() != null) {
      sql.append(" and l.assigned_to = ?");
      p.add(f.assignedTo());
    }
    if (notBlank(f.grade())) {
      sql.append(" and l.grade_sought = ?");
      p.add(f.grade());
    }
    if (notBlank(f.q())) {
      String like = "%" + f.q().trim().replace("%", "") + "%";
      sql.append(
          " and (l.guardian_name ilike ? or coalesce(l.student_name, '') ilike ? or l.phone ilike ?"
              + " or coalesce(l.email, '') ilike ?)");
      p.add(like);
      p.add(like);
      p.add(like);
      p.add(like);
    }
    if ("overdue".equals(f.due())) {
      sql.append(
          " and l.stage not in ('ADMITTED', 'LOST') and l.next_follow_up_at < date_trunc('day', now() at time zone 'Asia/Kolkata') at time zone 'Asia/Kolkata'");
    } else if ("today".equals(f.due())) {
      sql.append(
          " and l.stage not in ('ADMITTED', 'LOST') and l.next_follow_up_at < (date_trunc('day', now() at time zone 'Asia/Kolkata') + interval '1 day') at time zone 'Asia/Kolkata'");
    }
    sql.append(
        " order by case when l.stage in ('ADMITTED', 'LOST') then 1 else 0 end,"
            + " l.next_follow_up_at nulls last, l.created_at desc limit 1000");
    return jdbc.sql(sql.toString()).params(p).query(AdmissionsService::mapLead).list();
  }

  public LeadDetail get(UUID id) {
    entitlements.requireModule(MODULE);
    return new LeadDetail(lead(id), activities(id));
  }

  public Lead create(LeadInput in) {
    entitlements.requireModule(MODULE);
    String guardian = required(in.guardianName(), 120, "Parent / guardian name");
    String phone = phone(required(in.phone(), 20, "Phone"));
    String source = in.source() == null ? "WALK_IN" : oneOf(in.source(), SOURCES, "source");
    if ("WEBSITE".equals(source)) {
      throw ApiException.badRequest("invalid_source", "Website leads come from the enquiry form.");
    }
    String year = in.academicYear() == null ? currentAcademicYear() : year(in.academicYear());
    return tx.execute(s -> {
      UUID id =
          jdbc.sql(
                  "insert into admission_lead (organization_id, student_name, guardian_name, phone, email,"
                      + " grade_sought, academic_year, source, priority, assigned_to, next_follow_up_at,"
                      + " previous_school, notes, created_by)"
                      + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id")
              .params(
                  TenantContext.requireOrganizationId(),
                  optional(in.studentName(), 120, "Student name"),
                  guardian,
                  phone,
                  email(in.email()),
                  optional(in.gradeSought(), 60, "Class"),
                  year,
                  source,
                  in.priority() == null ? "NORMAL" : oneOf(in.priority(), PRIORITIES, "priority"),
                  counsellor(in.assignedTo()),
                  ts(in.nextFollowUpAt()),
                  optional(in.previousSchool(), 200, "Previous school"),
                  optional(in.notes(), 4000, "Notes"),
                  TenantContext.userId().orElse(null))
              .query(UUID.class)
              .single();
      activity(id, "CREATED", label(source) + " lead added", null, "NEW");
      audit.record(
          "admissions.lead_created",
          "admission_lead",
          id.toString(),
          null,
          Map.of("source", source));
      return lead(id);
    });
  }

  public Lead update(UUID id, LeadInput in) {
    entitlements.requireModule(MODULE);
    return tx.execute(s -> {
      Lead before = lead(id);
      UUID assignee = in.assignedTo() == null ? before.assignedTo() : counsellor(in.assignedTo());
      jdbc.sql(
              "update admission_lead set student_name = ?, guardian_name = ?, phone = ?, email = ?,"
                  + " grade_sought = ?, academic_year = ?, priority = ?, assigned_to = ?,"
                  + " previous_school = ?, notes = ?, updated_at = now() where id = ?")
          .params(
              in.studentName() == null
                  ? before.studentName()
                  : optional(in.studentName(), 120, "Student name"),
              in.guardianName() == null
                  ? before.guardianName()
                  : required(in.guardianName(), 120, "Parent / guardian name"),
              in.phone() == null ? before.phone() : phone(in.phone()),
              in.email() == null ? before.email() : email(in.email()),
              in.gradeSought() == null
                  ? before.gradeSought()
                  : optional(in.gradeSought(), 60, "Class"),
              in.academicYear() == null ? before.academicYear() : year(in.academicYear()),
              in.priority() == null
                  ? before.priority()
                  : oneOf(in.priority(), PRIORITIES, "priority"),
              assignee,
              in.previousSchool() == null
                  ? before.previousSchool()
                  : optional(in.previousSchool(), 200, "Previous school"),
              in.notes() == null ? before.notes() : optional(in.notes(), 4000, "Notes"),
              id)
          .update();
      if (assignee != null && !assignee.equals(before.assignedTo())) {
        activity(id, "ASSIGNED", "Assigned to " + userName(assignee), null, null);
      }
      if (in.nextFollowUpAt() != null) {
        setFollowUp(id, in.nextFollowUpAt());
      }
      audit.record("admissions.lead_updated", "admission_lead", id.toString(), null, null);
      return lead(id);
    });
  }

  /** Moves a lead to another stage; LOST needs a reason. */
  public Lead move(UUID id, String stage, String reason) {
    entitlements.requireModule(MODULE);
    String to = oneOf(stage, Set.copyOf(STAGES), "stage");
    String why = "LOST".equals(to) ? required(reason, 200, "Reason") : null;
    return tx.execute(s -> {
      Lead before = lead(id);
      if (before.stage().equals(to)) {
        return before;
      }
      if ("ADMITTED".equals(to)
          && before.gradeSought() != null
          && seatsFull(before.academicYear(), before.gradeSought())) {
        throw ApiException.conflict(
            "seats_full", "All seats in " + before.gradeSought() + " are taken.");
      }
      boolean closed = "ADMITTED".equals(to) || "LOST".equals(to);
      jdbc.sql(
              "update admission_lead set stage = ?, lost_reason = ?, next_follow_up_at = case when ? then null"
                  + " else next_follow_up_at end, updated_at = now() where id = ?")
          .params(to, why, closed, id)
          .update();
      activity(id, "STAGE", why, before.stage(), to);
      audit.record(
          "admissions.stage_changed",
          "admission_lead",
          id.toString(),
          Map.of("stage", before.stage()),
          Map.of("stage", to));
      return lead(id);
    });
  }

  /** Logs a call, visit, message or note, optionally scheduling the next follow-up. */
  public LeadDetail log(UUID id, String kind, String body, Instant nextFollowUpAt) {
    entitlements.requireModule(MODULE);
    String k = oneOf(kind, LOG_KINDS, "kind");
    String text = required(body, 2000, "Note");
    return tx.execute(s -> {
      Lead l = lead(id);
      activity(id, k, text, null, null);
      if ("NEW".equals(l.stage()) && ("CALL".equals(k) || "MESSAGE".equals(k))) {
        jdbc.sql("update admission_lead set stage = 'CONTACTED', updated_at = now() where id = ?")
            .param(id)
            .update();
        activity(id, "STAGE", null, "NEW", "CONTACTED");
      }
      if (nextFollowUpAt != null) {
        setFollowUp(id, nextFollowUpAt);
      } else {
        jdbc.sql("update admission_lead set updated_at = now() where id = ?").param(id).update();
      }
      return new LeadDetail(lead(id), activities(id));
    });
  }

  private void setFollowUp(UUID id, Instant when) {
    jdbc.sql("update admission_lead set next_follow_up_at = ?, updated_at = now() where id = ?")
        .params(ts(when), id)
        .update();
    activity(id, "FOLLOW_UP", "Follow up on " + when.atZone(IST).toLocalDate(), null, null);
  }

  /** Stage, source and conversion counts for one academic year. */
  public Funnel funnel(String academicYear) {
    entitlements.requireModule(MODULE);
    String y =
        academicYear == null || academicYear.isBlank() ? currentAcademicYear() : year(academicYear);
    Map<String, Long> byStage = new LinkedHashMap<>();
    STAGES.forEach(st -> byStage.put(st, 0L));
    jdbc.sql("select stage, count(*) from admission_lead where academic_year = ? group by stage")
        .param(y)
        .query((rs, i) -> byStage.put(rs.getString(1), rs.getLong(2)))
        .list();
    Map<String, Long> bySource = new LinkedHashMap<>();
    jdbc.sql(
            "select source, count(*) from admission_lead where academic_year = ? group by source"
                + " order by count(*) desc")
        .param(y)
        .query((rs, i) -> bySource.put(rs.getString(1), rs.getLong(2)))
        .list();
    long total = byStage.values().stream().mapToLong(Long::longValue).sum();
    long admitted = byStage.get("ADMITTED");
    long lost = byStage.get("LOST");
    Double conversion = total == 0 ? null : Math.round(admitted * 1000.0 / total) / 10.0;
    return new Funnel(y, byStage, bySource, total, admitted, lost, conversion);
  }

  // ------------------------------------------------------------------ seats (medium)

  public List<Seat> seats(String academicYear) {
    entitlements.requireFeature(MODULE, "seat_matrix");
    String y =
        academicYear == null || academicYear.isBlank() ? currentAcademicYear() : year(academicYear);
    return jdbc.sql(
            "select s.grade, s.capacity, s.sort_order,"
                + " (select count(*) from admission_lead l where l.academic_year = s.academic_year"
                + "   and l.grade_sought = s.grade and l.stage = 'ADMITTED') admitted,"
                + " (select count(*) from admission_lead l where l.academic_year = s.academic_year"
                + "   and l.grade_sought = s.grade and l.stage = 'OFFERED') offered"
                + " from admission_seat s where s.academic_year = ? order by s.sort_order, s.grade")
        .param(y)
        .query((rs, i) ->
            new Seat(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getLong(4), rs.getLong(5)))
        .list();
  }

  public List<Seat> saveSeats(String academicYear, List<Seat> seats) {
    entitlements.requireFeature(MODULE, "seat_matrix");
    String y = year(academicYear);
    if (seats.size() > 40) {
      throw ApiException.badRequest("too_many", "Up to 40 classes.");
    }
    tx.executeWithoutResult(s -> {
      jdbc.sql("delete from admission_seat where academic_year = ?").param(y).update();
      int order = 0;
      for (Seat seat : seats) {
        String grade = required(seat.grade(), 60, "Class");
        if (seat.capacity() < 0 || seat.capacity() > 5000) {
          throw ApiException.badRequest("invalid_capacity", "Seats must be between 0 and 5000.");
        }
        jdbc.sql(
                "insert into admission_seat (organization_id, academic_year, grade, capacity, sort_order)"
                    + " values (?, ?, ?, ?, ?) on conflict (organization_id, academic_year, grade)"
                    + " do update set capacity = excluded.capacity, sort_order = excluded.sort_order")
            .params(TenantContext.requireOrganizationId(), y, grade, seat.capacity(), order++)
            .update();
      }
      audit.record(
          "admissions.seats_saved", "admission_seat", y, null, Map.of("classes", seats.size()));
    });
    return seats(y);
  }

  private boolean seatsFull(String year, String grade) {
    if (!entitlements.hasFeature(MODULE, "seat_matrix")) {
      return false;
    }
    return jdbc.sql(
            "select s.capacity <= (select count(*) from admission_lead l where l.academic_year = s.academic_year"
                + " and l.grade_sought = s.grade and l.stage = 'ADMITTED')"
                + " from admission_seat s where s.academic_year = ? and s.grade = ?")
        .params(year, grade)
        .query(Boolean.class)
        .optional()
        .orElse(false);
  }

  // ------------------------------------------------------------------ people, export

  /** Staff who can be assigned leads: school admins and counsellors. */
  public List<Counsellor> counsellors() {
    UUID org = TenantContext.requireOrganizationId();
    return jdbc.sql(
            "select distinct u.id, u.display_name, u.email from role_assignment r"
                + " join app_user u on u.id = r.user_id where r.organization_id = ?"
                + " and r.role in ('SCHOOL_ADMIN', 'ADMISSIONS_COUNSELLOR') and u.status = 'ACTIVE'"
                + " order by u.display_name")
        .param(org)
        .query((rs, i) ->
            new Counsellor(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)))
        .list();
  }

  public String csv(Filter f) {
    entitlements.requireFeature(MODULE, "csv_export");
    StringBuilder out =
        new StringBuilder(
            "Student,Parent/guardian,Phone,Email,Class,Year,Source,Stage,Lost reason,Counsellor,Next follow-up,Created\n");
    for (Lead l : list(f)) {
      out.append(
              String.join(
                  ",",
                  cell(l.studentName()),
                  cell(l.guardianName()),
                  cell(l.phone()),
                  cell(l.email()),
                  cell(l.gradeSought()),
                  cell(l.academicYear()),
                  cell(l.source()),
                  cell(l.stage()),
                  cell(l.lostReason()),
                  cell(l.assignedName()),
                  cell(
                      l.nextFollowUpAt() == null
                          ? null
                          : l.nextFollowUpAt().atZone(IST).toLocalDate().toString()),
                  cell(l.createdAt().atZone(IST).toLocalDate().toString())))
          .append('\n');
    }
    return out.toString();
  }

  /** Open follow-ups due by the end of today, per assignee (for the morning digest). */
  record DueSummary(UUID organizationId, UUID assignedTo, long due, long overdue) {}

  List<DueSummary> dueToday() {
    return jdbc
        .sql(
            "select organization_id, assigned_to,"
                + " count(*) filter (where next_follow_up_at < (date_trunc('day', now() at time zone 'Asia/Kolkata') + interval '1 day') at time zone 'Asia/Kolkata'),"
                + " count(*) filter (where next_follow_up_at < date_trunc('day', now() at time zone 'Asia/Kolkata') at time zone 'Asia/Kolkata')"
                + " from admission_lead where stage not in ('ADMITTED', 'LOST') and next_follow_up_at is not null"
                + " group by organization_id, assigned_to")
        .query((rs, i) ->
            new DueSummary(
                rs.getObject(1, UUID.class),
                rs.getObject(2, UUID.class),
                rs.getLong(3),
                rs.getLong(4)))
        .list()
        .stream()
        .filter(d -> d.due() > 0)
        .toList();
  }

  // ------------------------------------------------------------------ helpers

  private static final String LEAD_SELECT =
      "select l.*, u.display_name assigned_name from admission_lead l left join app_user u on u.id = l.assigned_to";

  private Lead lead(UUID id) {
    return jdbc.sql(LEAD_SELECT + " where l.id = ?")
        .param(id)
        .query(AdmissionsService::mapLead)
        .optional()
        .orElseThrow(() -> ApiException.notFound("Lead"));
  }

  private List<Activity> activities(UUID id) {
    return jdbc.sql(
            "select a.id, a.kind, a.body, a.from_stage, a.to_stage, u.display_name, a.created_at"
                + " from admission_activity a left join app_user u on u.id = a.actor_id"
                + " where a.lead_id = ? order by a.created_at desc, a.id limit 200")
        .param(id)
        .query((rs, i) ->
            new Activity(
                rs.getObject(1, UUID.class),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5),
                rs.getString(6),
                rs.getTimestamp(7).toInstant()))
        .list();
  }

  private void activity(UUID lead, String kind, String body, String from, String to) {
    jdbc.sql(
            "insert into admission_activity (organization_id, lead_id, kind, body, from_stage, to_stage, actor_id)"
                + " values (?, ?, ?, ?, ?, ?, ?)")
        .params(
            TenantContext.requireOrganizationId(),
            lead,
            kind,
            truncate(body, 2000),
            from,
            to,
            TenantContext.userId().orElse(null))
        .update();
  }

  /** Only staff of this school who may work admissions can be assigned. */
  private UUID counsellor(UUID userId) {
    if (userId == null) {
      return null;
    }
    boolean ok = counsellors().stream().anyMatch(c -> c.id().equals(userId));
    if (!ok) {
      throw ApiException.badRequest("invalid_assignee", "Choose a counsellor or school admin.");
    }
    return userId;
  }

  private String userName(UUID id) {
    return jdbc.sql("select display_name from app_user where id = ?")
        .param(id)
        .query(String.class)
        .optional()
        .orElse("a colleague");
  }

  private static String label(String source) {
    return switch (source) {
      case "WALK_IN" -> "Walk-in";
      case "PHONE" -> "Phone";
      case "WHATSAPP" -> "WhatsApp";
      case "REFERRAL" -> "Referral";
      case "CAMPAIGN" -> "Campaign";
      default -> "New";
    };
  }

  private static Timestamp ts(Instant i) {
    return i == null ? null : Timestamp.from(i);
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }

  private static String truncate(String s, int max) {
    return s == null || s.length() <= max ? s : s.substring(0, max);
  }

  private static String required(String v, int max, String label) {
    String t = v == null ? "" : v.trim();
    if (t.isEmpty()) {
      throw ApiException.badRequest("missing_field", label + " is required.");
    }
    if (t.length() > max) {
      throw ApiException.badRequest("too_long", label + " is too long.");
    }
    return t;
  }

  private static String optional(String v, int max, String label) {
    return v == null || v.isBlank() ? null : required(v, max, label);
  }

  private static String phone(String v) {
    String t = v.trim();
    if (!PHONE.matcher(t).matches() || t.replaceAll("\\D", "").length() < 8) {
      throw ApiException.badRequest("invalid_phone", "Enter a valid phone number.");
    }
    return t;
  }

  private static String email(String v) {
    if (v == null || v.isBlank()) {
      return null;
    }
    String t = v.trim();
    if (t.length() > 200 || !EMAIL.matcher(t).matches()) {
      throw ApiException.badRequest("invalid_email", "Enter a valid email address.");
    }
    return t;
  }

  private static String year(String v) {
    if (v == null || !YEAR.matcher(v).matches()) {
      throw ApiException.badRequest("invalid_year", "Academic year looks like 2027-28.");
    }
    int a = Integer.parseInt(v.substring(0, 4));
    if (Integer.parseInt(v.substring(5)) != (a + 1) % 100) {
      throw ApiException.badRequest("invalid_year", "Academic year looks like 2027-28.");
    }
    return v;
  }

  private static String oneOf(String v, Set<String> allowed, String field) {
    if (v == null || !allowed.contains(v)) {
      throw ApiException.badRequest("invalid_" + field, "Unknown " + field + ".");
    }
    return v;
  }

  /** CSV cell, neutralising spreadsheet formulas. */
  static String cell(String v) {
    if (v == null) {
      return "";
    }
    String s = v;
    if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
      s = "'" + s;
    }
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }

  private static Lead mapLead(ResultSet rs, int i) throws SQLException {
    Timestamp f = rs.getTimestamp("next_follow_up_at");
    return new Lead(
        rs.getObject("id", UUID.class),
        rs.getObject("enquiry_id", UUID.class),
        rs.getString("student_name"),
        rs.getString("guardian_name"),
        rs.getString("phone"),
        rs.getString("email"),
        rs.getString("grade_sought"),
        rs.getString("academic_year"),
        rs.getString("source"),
        rs.getString("stage"),
        rs.getString("lost_reason"),
        rs.getString("priority"),
        rs.getObject("assigned_to", UUID.class),
        rs.getString("assigned_name"),
        f == null ? null : f.toInstant(),
        rs.getString("previous_school"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }
}
