package com.shikshablox.modules.curriculum;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.entitlement.EntitlementService;
import com.shikshablox.platform.identity.CurrentUser;
import com.shikshablox.platform.identity.Permission;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.TenantContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Curriculum (M09). The academic coordinator (curriculum:manage) keeps the syllabus per subject and
 * reviews lesson plans; teachers mark topics covered and write lesson plans for the classes they
 * teach. Coverage is compared with each unit's planned month (April-start school year).
 */
@Service
public class CurriculumService {

  static final String MODULE = "CURRICULUM";
  static final ZoneId IST = ZoneId.of("Asia/Kolkata");
  public static final List<String> CRITERIA =
      List.of(
          "Planning",
          "Engagement",
          "Classroom management",
          "Assessment for learning",
          "Use of resources");
  private static final TypeReference<Map<String, Integer>> SCORES = new TypeReference<>() {};

  public record SectionRef(UUID id, String label, String grade) {}

  public record SubjectRef(UUID id, String grade, String name) {}

  public record Staff(UUID id, String name) {}

  public record Overview(
      List<SectionRef> sections,
      List<SubjectRef> subjects,
      List<Staff> staff,
      List<String> criteria,
      boolean canManage,
      boolean lessonPlans,
      boolean observations) {}

  public record Topic(UUID id, String title, LocalDate coveredOn, String coveredBy) {}

  public record Unit(
      UUID id, String title, Integer plannedMonth, boolean behind, List<Topic> topics) {}

  public record Coverage(
      UUID subjectId,
      String subject,
      UUID sectionId,
      String section,
      int topics,
      int covered,
      Integer percent,
      int behindUnits,
      List<Unit> units) {}

  public record CoverageCell(
      UUID sectionId,
      String section,
      UUID subjectId,
      String subject,
      int topics,
      int covered,
      Integer percent,
      int behindUnits) {}

  public record UnitInput(UUID subjectId, String title, Integer plannedMonth) {}

  public record LessonPlan(
      UUID id,
      UUID sectionId,
      String section,
      UUID subjectId,
      String subject,
      UUID topicId,
      String topic,
      LocalDate plannedFor,
      String title,
      String objectives,
      String activities,
      String resources,
      String assessment,
      String status,
      String reviewNote,
      String reviewedBy,
      String createdBy,
      Instant updatedAt) {}

  public record LessonPlanInput(
      UUID sectionId,
      UUID subjectId,
      UUID topicId,
      LocalDate plannedFor,
      String title,
      String objectives,
      String activities,
      String resources,
      String assessment,
      Boolean submit) {}

  public record Observation(
      UUID id,
      UUID teacherId,
      String teacher,
      String section,
      String subject,
      LocalDate observedOn,
      Map<String, Integer> scores,
      Double average,
      String strengths,
      String suggestions,
      String observer,
      Instant acknowledgedAt,
      String reflection) {}

  public record ObservationInput(
      UUID teacherId,
      UUID sectionId,
      UUID subjectId,
      LocalDate observedOn,
      Map<String, Integer> scores,
      String strengths,
      String suggestions) {}

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final AuditService audit;
  private final EntitlementService entitlements;
  private final ObjectMapper json;

  CurriculumService(
      JdbcClient jdbc,
      TransactionTemplate tx,
      AuditService audit,
      @org.springframework.context.annotation.Lazy EntitlementService entitlements,
      ObjectMapper json) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.audit = audit;
    this.entitlements = entitlements;
    this.json = json;
  }

  // ------------------------------------------------------------------ access

  private void requireModule() {
    entitlements.requireModule(MODULE);
  }

  private void requireFeature(String f, String what) {
    if (!entitlements.hasFeature(MODULE, f)) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "feature_not_in_plan", what + " are part of ShikshaBlox Standard.");
    }
  }

  static boolean canManage() {
    return CurrentUser.get()
        .map(u ->
            u.roles().stream()
                .anyMatch(r -> r.permissions().contains(Permission.CURRICULUM_MANAGE)))
        .orElse(false);
  }

  private static void requireManage() {
    if (!canManage()) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "forbidden", "Only the academic coordinator can do that.");
    }
  }

  private UUID me() {
    return TenantContext.userId().orElse(null);
  }

  private String year() {
    return jdbc.sql("select current_year from sis_settings")
        .query(String.class)
        .optional()
        .orElseGet(() -> {
          LocalDate d = LocalDate.now(IST);
          int start = d.getMonthValue() >= 4 ? d.getYear() : d.getYear() - 1;
          return start + "-" + String.format("%02d", (start + 1) % 100);
        });
  }

  List<SectionRef> sections() {
    boolean all = canManage();
    return jdbc.sql(
            "select c.id, c.grade, c.section from class_section c where c.academic_year = ?"
                + (all
                    ? ""
                    : " and (exists (select 1 from attendance_teacher t where t.class_section_id = c.id and t.user_id = ?)"
                        + " or exists (select 1 from subject_teacher t where t.class_section_id = c.id and t.user_id = ?))")
                + " order by c.grade_order, c.grade, c.section")
        .params(all ? List.of(year()) : List.of(year(), me(), me()))
        .query((rs, i) ->
            new SectionRef(
                rs.getObject(1, UUID.class),
                rs.getString(2).trim() + " " + rs.getString(3).trim(),
                rs.getString(2)))
        .list();
  }

  private SectionRef section(UUID id) {
    return sections().stream()
        .filter(s -> s.id().equals(id))
        .findFirst()
        .orElseThrow(() ->
            new ApiException(
                HttpStatus.FORBIDDEN, "not_your_class", "You don't teach this class."));
  }

  private SubjectRef subject(UUID id) {
    return jdbc.sql("select id, grade, name from subject where id = ?")
        .param(id)
        .query((rs, i) ->
            new SubjectRef(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)))
        .optional()
        .orElseThrow(() -> ApiException.notFound("Subject"));
  }

  /** April = 0 ... March = 11, so units planned for earlier months are "due" by now. */
  static int schoolMonth(int month) {
    return (month - 4 + 12) % 12;
  }

  static boolean behind(Integer plannedMonth, int topics, int covered, LocalDate today) {
    return plannedMonth != null
        && topics > covered
        && schoolMonth(plannedMonth) < schoolMonth(today.getMonthValue());
  }

  // ------------------------------------------------------------------ overview & syllabus

  public Overview overview() {
    requireModule();
    List<SectionRef> secs = sections();
    List<String> grades = secs.stream().map(SectionRef::grade).distinct().toList();
    List<SubjectRef> subjects =
        grades.isEmpty()
            ? List.of()
            : jdbc.sql(
                    "select id, grade, name from subject where academic_year = ? and grade = any(?::text[]) and kind = 'SCHOLASTIC'"
                        + " order by grade, sort_order, name")
                .params(year(), grades.toArray(String[]::new))
                .query((rs, i) ->
                    new SubjectRef(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)))
                .list();
    List<Staff> staff =
        canManage()
            ? jdbc.sql(
                    "select distinct u.id, u.display_name from role_assignment r join app_user u on u.id = r.user_id"
                        + " where r.organization_id = ? and r.role in ('SCHOOL_ADMIN', 'TEACHER', 'EXAM_CELL') and u.status = 'ACTIVE' order by 2")
                .param(TenantContext.requireOrganizationId())
                .query((rs, i) -> new Staff(rs.getObject(1, UUID.class), rs.getString(2)))
                .list()
            : List.of();
    return new Overview(
        secs,
        subjects,
        staff,
        CRITERIA,
        canManage(),
        entitlements.hasFeature(MODULE, "lesson_plans"),
        entitlements.hasFeature(MODULE, "observations"));
  }

  public Coverage coverage(UUID subjectId, UUID sectionId) {
    requireModule();
    SubjectRef sub = subject(subjectId);
    SectionRef sec = sectionId == null ? null : section(sectionId);
    if (sec != null && !sec.grade().equals(sub.grade())) {
      throw ApiException.badRequest("invalid_subject", "This subject is not taught in this class.");
    }
    LocalDate today = LocalDate.now(IST);
    Map<UUID, Topic> coveredTopics = new HashMap<>();
    if (sec != null) {
      jdbc.sql(
              "select c.topic_id, c.covered_on, u.display_name from topic_coverage c left join app_user u on u.id = c.covered_by"
                  + " where c.class_section_id = ?")
          .param(sec.id())
          .query((rs, i) ->
              coveredTopics.put(
                  rs.getObject(1, UUID.class),
                  new Topic(
                      rs.getObject(1, UUID.class),
                      null,
                      rs.getObject(2, LocalDate.class),
                      rs.getString(3))))
          .list();
    }
    Map<UUID, List<Topic>> topics = new LinkedHashMap<>();
    jdbc.sql(
            "select t.id, t.unit_id, t.title from syllabus_topic t join syllabus_unit u on u.id = t.unit_id where u.subject_id = ?"
                + " order by u.sort_order, t.sort_order, t.title")
        .param(subjectId)
        .query((rs, i) -> {
          UUID id = rs.getObject(1, UUID.class);
          Topic c = coveredTopics.get(id);
          topics
              .computeIfAbsent(rs.getObject(2, UUID.class), k -> new ArrayList<>())
              .add(
                  new Topic(
                      id,
                      rs.getString(3),
                      c == null ? null : c.coveredOn(),
                      c == null ? null : c.coveredBy()));
          return null;
        })
        .list();
    List<Unit> units = new ArrayList<>();
    int total = 0;
    int done = 0;
    int behind = 0;
    for (Object[] u :
        jdbc.sql(
                "select id, title, planned_month from syllabus_unit where subject_id = ? order by sort_order, title")
            .param(subjectId)
            .query((rs, i) ->
                new Object[] {
                  rs.getObject(1, UUID.class), rs.getString(2), (Integer) rs.getObject(3)
                })
            .list()) {
      List<Topic> ts = topics.getOrDefault((UUID) u[0], List.of());
      int c = (int) ts.stream().filter(t -> t.coveredOn() != null).count();
      boolean b = sec != null && behind((Integer) u[2], ts.size(), c, today);
      units.add(new Unit((UUID) u[0], (String) u[1], (Integer) u[2], b, ts));
      total += ts.size();
      done += c;
      behind += b ? 1 : 0;
    }
    return new Coverage(
        sub.id(),
        sub.name(),
        sec == null ? null : sec.id(),
        sec == null ? null : sec.label(),
        total,
        done,
        total == 0 ? null : Math.round(done * 100f / total),
        behind,
        units);
  }

  /** Completion of every subject in every section (coordinator). */
  public List<CoverageCell> coverageOverview() {
    requireModule();
    requireManage();
    List<CoverageCell> out = new ArrayList<>();
    for (SectionRef s : sections()) {
      for (Object[] sub :
          jdbc.sql(
                  "select id, name from subject where academic_year = ? and grade = ? and kind = 'SCHOLASTIC' order by sort_order")
              .params(year(), s.grade())
              .query((rs, i) -> new Object[] {rs.getObject(1, UUID.class), rs.getString(2)})
              .list()) {
        Coverage c = coverage((UUID) sub[0], s.id());
        if (c.topics() > 0) {
          out.add(
              new CoverageCell(
                  s.id(),
                  s.label(),
                  c.subjectId(),
                  c.subject(),
                  c.topics(),
                  c.covered(),
                  c.percent(),
                  c.behindUnits()));
        }
      }
    }
    return out;
  }

  public Coverage addUnit(UnitInput in) {
    requireModule();
    requireManage();
    if (in.subjectId() == null) {
      throw ApiException.badRequest("missing_field", "Choose a subject.");
    }
    subject(in.subjectId());
    String title = req(in.title(), 120, "Unit name");
    month(in.plannedMonth());
    jdbc.sql(
            "insert into syllabus_unit (organization_id, subject_id, title, planned_month, sort_order)"
                + " values (?, ?, ?, ?, (select coalesce(max(sort_order), 0) + 1 from syllabus_unit where subject_id = ?))")
        .params(
            TenantContext.requireOrganizationId(),
            in.subjectId(),
            title,
            in.plannedMonth(),
            in.subjectId())
        .update();
    audit.record(
        "curriculum.unit_added",
        "subject",
        in.subjectId().toString(),
        null,
        Map.of("title", title));
    return coverage(in.subjectId(), null);
  }

  public Coverage updateUnit(UUID unitId, UnitInput in) {
    requireModule();
    requireManage();
    UUID subjectId = unitSubject(unitId);
    String title = req(in.title(), 120, "Unit name");
    month(in.plannedMonth());
    jdbc.sql("update syllabus_unit set title = ?, planned_month = ? where id = ?")
        .params(title, in.plannedMonth(), unitId)
        .update();
    return coverage(subjectId, null);
  }

  public Coverage deleteUnit(UUID unitId) {
    requireModule();
    requireManage();
    UUID subjectId = unitSubject(unitId);
    jdbc.sql("delete from syllabus_unit where id = ?").param(unitId).update();
    audit.record("curriculum.unit_deleted", "syllabus_unit", unitId.toString(), null, null);
    return coverage(subjectId, null);
  }

  /** Adds topics to a unit, one per line. */
  public Coverage addTopics(UUID unitId, List<String> titles) {
    requireModule();
    requireManage();
    UUID subjectId = unitSubject(unitId);
    List<String> clean = new ArrayList<>();
    for (String t : titles == null ? List.<String>of() : titles) {
      String x = t == null ? "" : t.trim();
      if (!x.isEmpty()) {
        if (x.length() > 160) {
          throw ApiException.badRequest("too_long", "Keep topic names under 160 characters.");
        }
        clean.add(x);
      }
    }
    if (clean.isEmpty() || clean.size() > 40) {
      throw ApiException.badRequest("missing_field", "Add 1 to 40 topics, one per line.");
    }
    tx.executeWithoutResult(s -> {
      int base =
          jdbc.sql("select coalesce(max(sort_order), 0) from syllabus_topic where unit_id = ?")
              .param(unitId)
              .query(Integer.class)
              .single();
      int n = base;
      for (String t : clean) {
        jdbc.sql(
                "insert into syllabus_topic (organization_id, unit_id, title, sort_order) values (?, ?, ?, ?)")
            .params(TenantContext.requireOrganizationId(), unitId, t, ++n)
            .update();
      }
    });
    return coverage(subjectId, null);
  }

  public Coverage deleteTopic(UUID topicId) {
    requireModule();
    requireManage();
    UUID unitId =
        jdbc.sql("select unit_id from syllabus_topic where id = ?")
            .param(topicId)
            .query(UUID.class)
            .optional()
            .orElseThrow(() -> ApiException.notFound("Topic"));
    UUID subjectId = unitSubject(unitId);
    jdbc.sql("delete from syllabus_topic where id = ?").param(topicId).update();
    return coverage(subjectId, null);
  }

  public Coverage markCovered(UUID sectionId, UUID topicId, boolean covered, LocalDate date) {
    requireModule();
    SectionRef sec = section(sectionId);
    UUID subjectId =
        jdbc.sql(
                "select u.subject_id from syllabus_topic t join syllabus_unit u on u.id = t.unit_id where t.id = ?")
            .param(topicId)
            .query(UUID.class)
            .optional()
            .orElseThrow(() -> ApiException.notFound("Topic"));
    if (!subject(subjectId).grade().equals(sec.grade())) {
      throw ApiException.badRequest(
          "invalid_topic", "This topic is not part of this class's syllabus.");
    }
    LocalDate d = date == null ? LocalDate.now(IST) : date;
    if (d.isAfter(LocalDate.now(IST))) {
      throw ApiException.badRequest("invalid_date", "A topic can't be covered in the future.");
    }
    if (covered) {
      jdbc.sql(
              "insert into topic_coverage (organization_id, topic_id, class_section_id, covered_on, covered_by) values (?, ?, ?, ?, ?)"
                  + " on conflict (topic_id, class_section_id) do update set covered_on = excluded.covered_on, covered_by = excluded.covered_by")
          .params(TenantContext.requireOrganizationId(), topicId, sectionId, d, me())
          .update();
    } else {
      jdbc.sql("delete from topic_coverage where topic_id = ? and class_section_id = ?")
          .params(topicId, sectionId)
          .update();
    }
    return coverage(subjectId, sectionId);
  }

  private UUID unitSubject(UUID unitId) {
    return jdbc.sql("select subject_id from syllabus_unit where id = ?")
        .param(unitId)
        .query(UUID.class)
        .optional()
        .orElseThrow(() -> ApiException.notFound("Unit"));
  }

  private static void month(Integer m) {
    if (m != null && (m < 1 || m > 12)) {
      throw ApiException.badRequest("invalid_month", "Choose a month.");
    }
  }

  // ------------------------------------------------------------------ lesson plans

  private static final String PLANS =
      "select p.*, c.grade, c.section, s.name subject_name, t.title topic_title, ru.display_name reviewer, cu.display_name author"
          + " from lesson_plan p join class_section c on c.id = p.class_section_id left join subject s on s.id = p.subject_id"
          + " left join syllabus_topic t on t.id = p.topic_id left join app_user ru on ru.id = p.reviewed_by"
          + " left join app_user cu on cu.id = p.created_by";

  public List<LessonPlan> plans(String status) {
    requireModule();
    requireFeature("lesson_plans", "Lesson plans");
    boolean all = canManage();
    String st =
        status == null || status.isBlank() ? null : status.toUpperCase(java.util.Locale.ROOT);
    StringBuilder sql = new StringBuilder(PLANS + " where p.planned_for >= ?");
    List<Object> p = new ArrayList<>(List.of(LocalDate.now(IST).minusDays(45)));
    if (!all) {
      sql.append(" and p.created_by = ?");
      p.add(me());
    } else {
      sql.append(" and (p.status <> 'DRAFT' or p.created_by = ?)");
      p.add(me());
    }
    if (st != null) {
      sql.append(" and p.status = ?");
      p.add(st);
    }
    sql.append(
        " order by case p.status when 'SUBMITTED' then 0 else 1 end, p.planned_for desc limit 200");
    return jdbc.sql(sql.toString()).params(p).query((rs, i) -> mapPlan(rs)).list();
  }

  public LessonPlan savePlan(UUID id, LessonPlanInput in) {
    requireModule();
    requireFeature("lesson_plans", "Lesson plans");
    SectionRef sec = section(in.sectionId());
    if (in.subjectId() != null && !subject(in.subjectId()).grade().equals(sec.grade())) {
      throw ApiException.badRequest("invalid_subject", "This subject is not taught in this class.");
    }
    if (in.plannedFor() == null) {
      throw ApiException.badRequest("missing_field", "Choose the date of the lesson.");
    }
    String title = req(in.title(), 160, "Title");
    String objectives = opt(in.objectives(), 2000, "Objectives");
    String activities = opt(in.activities(), 4000, "Activities");
    String resources = opt(in.resources(), 1000, "Resources");
    String assessment = opt(in.assessment(), 1000, "Assessment");
    String status = Boolean.TRUE.equals(in.submit()) ? "SUBMITTED" : "DRAFT";
    UUID pid =
        tx.execute(s -> {
          if (id == null) {
            return jdbc.sql(
                    "insert into lesson_plan (organization_id, class_section_id, subject_id, topic_id, planned_for, title, objectives,"
                        + " activities, resources, assessment, status, created_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id")
                .params(
                    TenantContext.requireOrganizationId(),
                    sec.id(),
                    in.subjectId(),
                    in.topicId(),
                    in.plannedFor(),
                    title,
                    objectives,
                    activities,
                    resources,
                    assessment,
                    status,
                    me())
                .query(UUID.class)
                .single();
          }
          LessonPlan before = plan(id);
          if (!isAuthor(id) || "REVIEWED".equals(before.status())) {
            throw ApiException.conflict(
                "locked", "Only the author can change a plan before it is reviewed.");
          }
          jdbc.sql(
                  "update lesson_plan set class_section_id = ?, subject_id = ?, topic_id = ?, planned_for = ?, title = ?, objectives = ?,"
                      + " activities = ?, resources = ?, assessment = ?, status = ?, updated_at = now() where id = ?")
              .params(
                  sec.id(),
                  in.subjectId(),
                  in.topicId(),
                  in.plannedFor(),
                  title,
                  objectives,
                  activities,
                  resources,
                  assessment,
                  status,
                  id)
              .update();
          return id;
        });
    audit.record(
        "SUBMITTED".equals(status) ? "curriculum.plan_submitted" : "curriculum.plan_saved",
        "lesson_plan",
        pid.toString(),
        null,
        Map.of("title", title));
    return plan(pid);
  }

  public LessonPlan review(UUID id, String note) {
    requireModule();
    requireFeature("lesson_plans", "Lesson plans");
    requireManage();
    LessonPlan p = plan(id);
    if (!"SUBMITTED".equals(p.status())) {
      throw ApiException.conflict("not_submitted", "Only submitted plans are reviewed.");
    }
    String n = opt(note, 1000, "Note");
    jdbc.sql(
            "update lesson_plan set status = 'REVIEWED', review_note = ?, reviewed_by = ?, reviewed_at = now() where id = ?")
        .params(n, me(), id)
        .update();
    audit.record("curriculum.plan_reviewed", "lesson_plan", id.toString(), null, null);
    return plan(id);
  }

  public void deletePlan(UUID id) {
    requireModule();
    plan(id);
    if (!isAuthor(id) && !canManage()) {
      throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "You can delete your own plans.");
    }
    jdbc.sql("delete from lesson_plan where id = ?").param(id).update();
  }

  private LessonPlan plan(UUID id) {
    return jdbc.sql(PLANS + " where p.id = ?")
        .param(id)
        .query((rs, i) -> mapPlan(rs))
        .optional()
        .orElseThrow(() -> ApiException.notFound("Lesson plan"));
  }

  private boolean isAuthor(UUID planId) {
    return jdbc.sql("select count(*) from lesson_plan where id = ? and created_by = ?")
            .params(planId, me())
            .query(Long.class)
            .single()
        > 0;
  }

  // ------------------------------------------------------------------ observations

  private static final String OBS =
      "select o.*, t.display_name teacher_name, ob.display_name observer_name, c.grade, c.section, s.name subject_name"
          + " from classroom_observation o join app_user t on t.id = o.teacher_id left join app_user ob on ob.id = o.observer_id"
          + " left join class_section c on c.id = o.class_section_id left join subject s on s.id = o.subject_id";

  public List<Observation> observations() {
    requireModule();
    requireFeature("observations", "Classroom observations");
    return (canManage()
            ? jdbc.sql(OBS + " order by o.observed_on desc limit 200")
            : jdbc.sql(OBS + " where o.teacher_id = ? order by o.observed_on desc").param(me()))
        .query((rs, i) -> mapObs(rs))
        .list();
  }

  public Observation observe(ObservationInput in) {
    requireModule();
    requireFeature("observations", "Classroom observations");
    requireManage();
    if (in.teacherId() == null || in.observedOn() == null) {
      throw ApiException.badRequest("missing_field", "Choose the teacher and the date.");
    }
    if (overview().staff().stream().noneMatch(s -> s.id().equals(in.teacherId()))) {
      throw ApiException.badRequest("invalid_teacher", "Choose a teacher of this school.");
    }
    if (in.observedOn().isAfter(LocalDate.now(IST))) {
      throw ApiException.badRequest("invalid_date", "Choose today or an earlier date.");
    }
    Map<String, Integer> scores = new LinkedHashMap<>();
    for (String c : CRITERIA) {
      Integer v = in.scores() == null ? null : in.scores().get(c);
      if (v != null) {
        if (v < 1 || v > 4) {
          throw ApiException.badRequest("invalid_score", c + ": choose 1 to 4.");
        }
        scores.put(c, v);
      }
    }
    if (scores.isEmpty()) {
      throw ApiException.badRequest("missing_field", "Score at least one area.");
    }
    UUID id =
        jdbc.sql(
                "insert into classroom_observation (organization_id, teacher_id, class_section_id, subject_id, observed_on, scores, strengths,"
                    + " suggestions, observer_id) values (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?) returning id")
            .params(
                TenantContext.requireOrganizationId(),
                in.teacherId(),
                in.sectionId(),
                in.subjectId(),
                in.observedOn(),
                write(scores),
                opt(in.strengths(), 2000, "Strengths"),
                opt(in.suggestions(), 2000, "Suggestions"),
                me())
            .query(UUID.class)
            .single();
    audit.record(
        "curriculum.observation_recorded", "classroom_observation", id.toString(), null, null);
    return jdbc.sql(OBS + " where o.id = ?").param(id).query((rs, i) -> mapObs(rs)).single();
  }

  public void deleteObservation(UUID id) {
    requireModule();
    requireManage();
    jdbc.sql("delete from classroom_observation where id = ?").param(id).update();
    audit.record(
        "curriculum.observation_deleted", "classroom_observation", id.toString(), null, null);
  }

  /** The observed teacher acknowledges, with an optional reflection. */
  public Observation acknowledge(UUID id, String reflection) {
    requireModule();
    requireFeature("observations", "Classroom observations");
    int n =
        jdbc.sql(
                "update classroom_observation set acknowledged_at = now(), reflection = ? where id = ? and teacher_id = ?")
            .params(opt(reflection, 2000, "Reflection"), id, me())
            .update();
    if (n == 0) {
      throw ApiException.notFound("Observation");
    }
    audit.record(
        "curriculum.observation_acknowledged", "classroom_observation", id.toString(), null, null);
    return jdbc.sql(OBS + " where o.id = ?").param(id).query((rs, i) -> mapObs(rs)).single();
  }

  // ------------------------------------------------------------------ helpers

  private LessonPlan mapPlan(ResultSet rs) throws SQLException {
    return new LessonPlan(
        rs.getObject("id", UUID.class),
        rs.getObject("class_section_id", UUID.class),
        rs.getString("grade").trim() + " " + rs.getString("section").trim(),
        rs.getObject("subject_id", UUID.class),
        rs.getString("subject_name"),
        rs.getObject("topic_id", UUID.class),
        rs.getString("topic_title"),
        rs.getObject("planned_for", LocalDate.class),
        rs.getString("title"),
        rs.getString("objectives"),
        rs.getString("activities"),
        rs.getString("resources"),
        rs.getString("assessment"),
        rs.getString("status"),
        rs.getString("review_note"),
        rs.getString("reviewer"),
        rs.getString("author"),
        rs.getTimestamp("updated_at").toInstant());
  }

  private Observation mapObs(ResultSet rs) throws SQLException {
    Map<String, Integer> scores;
    try {
      scores = json.readValue(rs.getString("scores"), SCORES);
    } catch (Exception e) {
      scores = Map.of();
    }
    double avg = scores.values().stream().mapToInt(Integer::intValue).average().orElse(0);
    String grade = rs.getString("grade");
    return new Observation(
        rs.getObject("id", UUID.class),
        rs.getObject("teacher_id", UUID.class),
        rs.getString("teacher_name"),
        grade == null ? null : grade.trim() + " " + rs.getString("section").trim(),
        rs.getString("subject_name"),
        rs.getObject("observed_on", LocalDate.class),
        scores,
        scores.isEmpty() ? null : Math.round(avg * 10) / 10.0,
        rs.getString("strengths"),
        rs.getString("suggestions"),
        rs.getString("observer_name"),
        rs.getTimestamp("acknowledged_at") == null
            ? null
            : rs.getTimestamp("acknowledged_at").toInstant(),
        rs.getString("reflection"));
  }

  private String write(Object o) {
    try {
      return json.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static String req(String v, int max, String label) {
    String t = v == null ? "" : v.trim();
    if (t.isEmpty()) {
      throw ApiException.badRequest("missing_field", label + " is required.");
    }
    if (t.length() > max) {
      throw ApiException.badRequest("too_long", label + " is too long (max " + max + ").");
    }
    return t;
  }

  static String opt(String v, int max, String label) {
    return v == null || v.isBlank() ? null : req(v, max, label);
  }
}
