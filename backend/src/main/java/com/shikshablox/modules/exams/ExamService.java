package com.shikshablox.modules.exams;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shikshablox.modules.comms.BroadcastService;
import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.TenantContext;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Exam set-up (M11): settings and grade scale, subjects per class (from a board preset), subject
 * teachers, exams with components and weights, the paper timetable, and the exam lifecycle (draft →
 * marks open → published, with a parent alert on publishing).
 */
@Service
public class ExamService {

  private static final Logger log = LoggerFactory.getLogger(ExamService.class);
  static final Set<String> BOARDS = Set.of("CBSE", "ICSE", "STATE", "OTHER");
  static final String RESULTS_TEMPLATE = "results_published";
  static final String RESULTS_BODY =
      "Dear {{name}}, {{title}}. Sign in to the parent portal to see the report card. {{link}}";
  private static final Pattern NUMBER = Pattern.compile("(\\d{1,2})");
  private static final TypeReference<List<Map<String, Object>>> LIST = new TypeReference<>() {};

  public record Settings(
      String board,
      int passPercent,
      List<Grading.Band> bands,
      List<String> coGrades,
      String principalName,
      boolean showRank,
      boolean timetable,
      boolean hallTickets,
      boolean moderation,
      boolean analysis,
      boolean canManage) {}

  public record SettingsInput(
      String board,
      Integer passPercent,
      List<Grading.Band> bands,
      List<String> coGrades,
      String principalName,
      Boolean showRank) {}

  public record Subject(UUID id, String grade, String name, String kind, int sortOrder) {}

  public record SubjectInput(String grade, String name, String kind) {}

  public record Exam(
      UUID id,
      String name,
      String term,
      List<String> grades,
      List<Grading.Component> components,
      double maxTotal,
      int weight,
      LocalDate startDate,
      LocalDate endDate,
      String status,
      java.time.Instant publishedAt) {}

  public record ExamInput(
      String name,
      String term,
      List<String> grades,
      List<Map<String, Object>> components,
      Integer weight,
      LocalDate startDate,
      LocalDate endDate) {}

  public record Paper(
      UUID subjectId,
      String subject,
      String grade,
      String kind,
      boolean included,
      LocalDate date,
      LocalTime startTime,
      Integer durationMin) {}

  public record PaperInput(
      UUID subjectId, boolean included, LocalDate date, LocalTime startTime, Integer durationMin) {}

  public record Staff(UUID id, String name) {}

  public record TeacherSlot(UUID subjectId, String subject, List<Staff> teachers) {}

  public record SectionTeachers(
      UUID sectionId, String label, String grade, List<TeacherSlot> subjects) {}

  public record Teachers(List<Staff> staff, List<SectionTeachers> sections) {}

  public record Overview(String year, Settings settings, List<String> grades, List<Exam> exams) {}

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final AuditService audit;
  private final ExamAccess access;
  private final BroadcastService broadcasts;
  private final ObjectMapper json;

  ExamService(
      JdbcClient jdbc,
      TransactionTemplate tx,
      AuditService audit,
      ExamAccess access,
      BroadcastService broadcasts,
      ObjectMapper json) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.audit = audit;
    this.access = access;
    this.broadcasts = broadcasts;
    this.json = json;
  }

  // ------------------------------------------------------------------ overview & settings

  public Overview overview() {
    access.requireModule();
    String year = access.currentYear();
    return new Overview(year, loadSettings(), grades(year), exams(year));
  }

  Settings loadSettings() {
    jdbc.sql("insert into exam_settings (organization_id) values (?) on conflict do nothing")
        .param(TenantContext.requireOrganizationId())
        .update();
    return jdbc.sql("select * from exam_settings")
        .query((rs, i) ->
            new Settings(
                rs.getString("board"),
                rs.getInt("pass_percent"),
                bands(rs.getString("grade_bands")),
                List.of((String[]) rs.getArray("co_grades").getArray()),
                rs.getString("principal_name"),
                rs.getBoolean("show_rank"),
                access.feature("timetable"),
                access.feature("hall_tickets"),
                access.feature("moderation"),
                access.feature("analysis"),
                access.canManage()))
        .single();
  }

  public Settings saveSettings(SettingsInput in) {
    access.requireModule();
    access.requireManage();
    Settings b = loadSettings();
    String board = in.board() == null ? b.board() : in.board().toUpperCase(Locale.ROOT);
    if (!BOARDS.contains(board)) {
      throw ApiException.badRequest("invalid_board", "Choose CBSE, ICSE, State board or other.");
    }
    int pass = in.passPercent() == null ? b.passPercent() : in.passPercent();
    if (pass < 0 || pass > 100) {
      throw ApiException.badRequest("invalid_pass", "The pass mark must be 0 to 100%.");
    }
    List<Grading.Band> bands = in.bands() == null ? b.bands() : cleanBands(in.bands());
    List<String> co = b.coGrades();
    if (in.coGrades() != null) {
      LinkedHashSet<String> c = new LinkedHashSet<>();
      for (String g : in.coGrades()) {
        String t = g == null ? "" : g.trim();
        if (!t.isEmpty()) {
          if (t.length() > 4) {
            throw ApiException.badRequest(
                "invalid_grade", "Co-scholastic grades are up to 4 characters.");
          }
          c.add(t);
        }
      }
      if (c.isEmpty() || c.size() > 10) {
        throw ApiException.badRequest("invalid_grade", "Use 1 to 10 co-scholastic grades.");
      }
      co = List.copyOf(c);
    }
    String principal =
        in.principalName() == null ? b.principalName() : blankToNull(in.principalName().trim());
    if (principal != null && principal.length() > 120) {
      throw ApiException.badRequest("too_long", "The principal's name is too long.");
    }
    boolean rank = in.showRank() == null ? b.showRank() : in.showRank();
    String bandsJson = write(bands);
    List<String> coFinal = co;
    tx.executeWithoutResult(s -> {
      jdbc.sql(
              "update exam_settings set board = ?, pass_percent = ?, grade_bands = ?::jsonb, co_grades = ?, principal_name = ?,"
                  + " show_rank = ?, updated_at = now()")
          .params(board, pass, bandsJson, coFinal.toArray(String[]::new), principal, rank)
          .update();
      audit.record(
          "exams.settings_saved",
          "exam_settings",
          null,
          null,
          Map.of("board", board, "pass", pass));
    });
    return loadSettings();
  }

  static List<Grading.Band> cleanBands(List<Grading.Band> in) {
    if (in.isEmpty() || in.size() > 12) {
      throw ApiException.badRequest("invalid_bands", "Use 1 to 12 grade bands.");
    }
    List<Grading.Band> sorted = new ArrayList<>(in);
    sorted.sort((a, b) -> Double.compare(b.min(), a.min()));
    Set<String> names = new java.util.HashSet<>();
    for (Grading.Band b : sorted) {
      if (b.grade() == null
          || b.grade().isBlank()
          || b.grade().trim().length() > 4
          || !names.add(b.grade().trim())) {
        throw ApiException.badRequest(
            "invalid_bands", "Each band needs a different grade of up to 4 characters.");
      }
      if (b.min() < 0 || b.min() > 100) {
        throw ApiException.badRequest("invalid_bands", "Band minimums must be 0 to 100%.");
      }
    }
    if (sorted.get(sorted.size() - 1).min() != 0) {
      throw ApiException.badRequest("invalid_bands", "The lowest band must start at 0%.");
    }
    return sorted.stream()
        .map(b -> new Grading.Band(b.min(), b.grade().trim(), b.point()))
        .toList();
  }

  List<Grading.Band> bands(String s) {
    try {
      return List.of(json.readValue(s, Grading.Band[].class));
    } catch (Exception e) {
      return List.of(new Grading.Band(0, "-", 0));
    }
  }

  // ------------------------------------------------------------------ subjects

  List<String> grades(String year) {
    return jdbc.sql(
            "select grade from class_section where academic_year = ? group by grade order by min(grade_order), grade")
        .param(year)
        .query(String.class)
        .list();
  }

  public List<Subject> subjects(String grade) {
    access.requireModule();
    String year = access.currentYear();
    return jdbc.sql(
            "select id, grade, name, kind, sort_order from subject where academic_year = ?"
                + (grade == null || grade.isBlank() ? "" : " and grade = ?")
                + " order by grade, kind, sort_order, name")
        .params(grade == null || grade.isBlank() ? List.of(year) : List.of(year, grade))
        .query((rs, i) ->
            new Subject(
                rs.getObject(1, UUID.class),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getInt(5)))
        .list();
  }

  public List<Subject> addSubject(SubjectInput in) {
    access.requireModule();
    access.requireManage();
    String year = access.currentYear();
    String grade = required(in.grade(), 40, "Class");
    if (!grades(year).contains(grade)) {
      throw ApiException.badRequest("invalid_class", "Choose one of the school's classes.");
    }
    String name = required(in.name(), 80, "Subject");
    String kind = in.kind() == null ? "SCHOLASTIC" : in.kind().toUpperCase(Locale.ROOT);
    if (!Set.of("SCHOLASTIC", "CO_SCHOLASTIC").contains(kind)) {
      throw ApiException.badRequest("invalid_kind", "Choose scholastic or co-scholastic.");
    }
    int n =
        jdbc.sql(
                "insert into subject (organization_id, academic_year, grade, name, kind, sort_order)"
                    + " values (?, ?, ?, ?, ?, (select coalesce(max(sort_order), 0) + 1 from subject where academic_year = ? and grade = ?))"
                    + " on conflict do nothing")
            .params(TenantContext.requireOrganizationId(), year, grade, name, kind, year, grade)
            .update();
    if (n == 0) {
      throw ApiException.conflict("duplicate", grade + " already has " + name + ".");
    }
    audit.record(
        "exams.subject_added", "subject", null, null, Map.of("grade", grade, "name", name));
    return subjects(grade);
  }

  public void deleteSubject(UUID id) {
    access.requireModule();
    access.requireManage();
    long marks =
        jdbc.sql("select count(*) from exam_mark where subject_id = ?")
            .param(id)
            .query(Long.class)
            .single();
    if (marks > 0) {
      throw ApiException.conflict(
          "has_marks", "This subject already has marks, so it can't be removed.");
    }
    int n = jdbc.sql("delete from subject where id = ?").param(id).update();
    if (n == 0) {
      throw ApiException.notFound("Subject");
    }
    audit.record("exams.subject_deleted", "subject", id.toString(), null, null);
  }

  /** Adds the board's usual subjects to the given classes (existing subjects are kept). */
  public List<Subject> applyPreset(List<String> grades) {
    access.requireModule();
    access.requireManage();
    String year = access.currentYear();
    String board = loadSettings().board();
    List<String> known = grades(year);
    int added = 0;
    for (String g : grades == null ? known : grades) {
      if (!known.contains(g)) {
        continue;
      }
      List<String[]> preset = preset(board, g);
      int order = 0;
      for (String[] p : preset) {
        added +=
            jdbc.sql(
                    "insert into subject (organization_id, academic_year, grade, name, kind, sort_order) values (?, ?, ?, ?, ?, ?)"
                        + " on conflict do nothing")
                .params(TenantContext.requireOrganizationId(), year, g, p[0], p[1], ++order)
                .update();
      }
    }
    audit.record(
        "exams.preset_applied", "subject", null, null, Map.of("board", board, "added", added));
    return subjects(null);
  }

  /** Usual subjects by board and class number; pre-primary classes get none (early years). */
  public static List<String[]> preset(String board, String grade) {
    Matcher m = NUMBER.matcher(grade);
    if (!m.find()) {
      return List.of();
    }
    int n = Integer.parseInt(m.group(1));
    String second = "CBSE".equals(board) ? "Hindi" : "Second Language";
    List<String> sch =
        n <= 5
            ? List.of("English", second, "Mathematics", "Environmental Studies", "Computer Science")
            : n <= 8
                ? List.of(
                    "English",
                    second,
                    "Third Language",
                    "Mathematics",
                    "Science",
                    "Social Science",
                    "Computer Science")
                : n <= 10
                    ? List.of(
                        "English",
                        second,
                        "Mathematics",
                        "Science",
                        "Social Science",
                        "Information Technology")
                    : List.of("English", "Physical Education");
    if ("CBSE".equals(board) && n >= 6 && n <= 8) {
      sch =
          List.of(
              "English",
              "Hindi",
              "Sanskrit",
              "Mathematics",
              "Science",
              "Social Science",
              "Computer Science");
    }
    if ("ICSE".equals(board) && n >= 6 && n <= 10) {
      sch =
          List.of(
              "English",
              second,
              "Mathematics",
              "Physics",
              "Chemistry",
              "Biology",
              "History & Civics",
              "Geography",
              "Computer Applications");
    }
    List<String[]> out = new ArrayList<>();
    sch.forEach(s -> out.add(new String[] {s, "SCHOLASTIC"}));
    for (String c :
        List.of("Art Education", "Health & Physical Education", "Work Education", "Discipline")) {
      out.add(new String[] {c, "CO_SCHOLASTIC"});
    }
    return out;
  }

  // ------------------------------------------------------------------ subject teachers

  public Teachers teachers() {
    access.requireModule();
    access.requireManage();
    String year = access.currentYear();
    UUID org = TenantContext.requireOrganizationId();
    List<Staff> staff =
        jdbc.sql(
                "select distinct u.id, u.display_name from role_assignment r join app_user u on u.id = r.user_id"
                    + " where r.organization_id = ? and r.role in ('SCHOOL_ADMIN', 'TEACHER', 'EXAM_CELL') and u.status = 'ACTIVE'"
                    + " order by u.display_name")
            .param(org)
            .query((rs, i) -> new Staff(rs.getObject(1, UUID.class), rs.getString(2)))
            .list();
    Map<UUID, Staff> byId = new HashMap<>();
    staff.forEach(s -> byId.put(s.id(), s));
    Map<String, List<Staff>> assigned = new HashMap<>();
    jdbc.sql("select class_section_id, subject_id, user_id from subject_teacher")
        .query((rs, i) -> {
          Staff s = byId.get(rs.getObject(3, UUID.class));
          if (s != null) {
            assigned
                .computeIfAbsent(rs.getString(1) + rs.getString(2), k -> new ArrayList<>())
                .add(s);
          }
          return null;
        })
        .list();
    Map<String, List<Subject>> subjectsByGrade = new HashMap<>();
    for (Subject s : subjects(null)) {
      if ("SCHOLASTIC".equals(s.kind())) {
        subjectsByGrade.computeIfAbsent(s.grade(), k -> new ArrayList<>()).add(s);
      }
    }
    List<SectionTeachers> out =
        jdbc.sql(
                "select id, grade, section from class_section where academic_year = ? order by grade_order, grade, section")
            .param(year)
            .query((rs, i) -> {
              UUID id = rs.getObject(1, UUID.class);
              String grade = rs.getString(2);
              List<TeacherSlot> slots = new ArrayList<>();
              for (Subject s : subjectsByGrade.getOrDefault(grade, List.of())) {
                slots.add(
                    new TeacherSlot(
                        s.id(),
                        s.name(),
                        assigned.getOrDefault(id.toString() + s.id(), List.of())));
              }
              return new SectionTeachers(
                  id, grade.trim() + " " + rs.getString(3).trim(), grade, slots);
            })
            .list();
    return new Teachers(staff, out);
  }

  public Teachers assign(UUID sectionId, UUID subjectId, List<UUID> userIds) {
    access.requireModule();
    access.requireManage();
    Teachers t = teachers();
    Set<UUID> allowed = new java.util.HashSet<>();
    t.staff().forEach(s -> allowed.add(s.id()));
    boolean valid =
        t.sections().stream()
            .anyMatch(s ->
                s.sectionId().equals(sectionId)
                    && s.subjects().stream().anyMatch(x -> x.subjectId().equals(subjectId)));
    if (!valid) {
      throw ApiException.notFound("Class subject");
    }
    List<UUID> ids = userIds == null ? List.of() : List.copyOf(new LinkedHashSet<>(userIds));
    if (ids.size() > 5 || !allowed.containsAll(ids)) {
      throw ApiException.badRequest("invalid_teacher", "Choose up to 5 teachers of this school.");
    }
    tx.executeWithoutResult(s -> {
      jdbc.sql("delete from subject_teacher where class_section_id = ? and subject_id = ?")
          .params(sectionId, subjectId)
          .update();
      for (UUID u : ids) {
        jdbc.sql(
                "insert into subject_teacher (organization_id, class_section_id, subject_id, user_id) values (?, ?, ?, ?)")
            .params(TenantContext.requireOrganizationId(), sectionId, subjectId, u)
            .update();
      }
      audit.record(
          "exams.subject_teachers",
          "class_section",
          sectionId.toString(),
          null,
          Map.of("teachers", ids.size()));
    });
    return teachers();
  }

  // ------------------------------------------------------------------ exams

  List<Exam> exams(String year) {
    return jdbc.sql(
            "select * from exam where academic_year = ? order by sort_order, start_date nulls last, created_at")
        .param(year)
        .query((rs, i) -> mapExam(rs))
        .list();
  }

  Exam load(UUID id) {
    return jdbc.sql("select * from exam where id = ?")
        .param(id)
        .query((rs, i) -> mapExam(rs))
        .optional()
        .orElseThrow(() -> ApiException.notFound("Exam"));
  }

  private Exam mapExam(java.sql.ResultSet rs) throws java.sql.SQLException {
    List<Grading.Component> comps = components(rs.getString("components"));
    return new Exam(
        rs.getObject("id", UUID.class),
        rs.getString("name"),
        rs.getString("term"),
        List.of((String[]) rs.getArray("grades").getArray()),
        comps,
        Grading.maxTotal(comps),
        rs.getInt("weight"),
        rs.getObject("start_date", LocalDate.class),
        rs.getObject("end_date", LocalDate.class),
        rs.getString("status"),
        rs.getTimestamp("published_at") == null
            ? null
            : rs.getTimestamp("published_at").toInstant());
  }

  List<Grading.Component> components(String s) {
    try {
      List<Map<String, Object>> raw = json.readValue(s, LIST);
      List<Grading.Component> out = new ArrayList<>();
      for (Map<String, Object> m : raw) {
        out.add(
            new Grading.Component(
                String.valueOf(m.get("key")),
                String.valueOf(m.get("label")),
                Double.parseDouble(String.valueOf(m.get("max")))));
      }
      return out;
    } catch (Exception e) {
      return List.of(new Grading.Component("M", "Marks", 100));
    }
  }

  public Exam save(UUID id, ExamInput in) {
    access.requireModule();
    access.requireManage();
    String year = access.currentYear();
    String name = required(in.name(), 80, "Exam name");
    String term = blankToNull(in.term() == null ? null : in.term().trim());
    if (term != null && term.length() > 40) {
      throw ApiException.badRequest("too_long", "The term name is too long.");
    }
    List<String> known = grades(year);
    List<String> grades =
        in.grades() == null ? List.of() : List.copyOf(new LinkedHashSet<>(in.grades()));
    if (grades.isEmpty() || !known.containsAll(grades)) {
      throw ApiException.badRequest("invalid_classes", "Choose the classes that sit this exam.");
    }
    List<Grading.Component> comps = Grading.components(in.components());
    int weight = in.weight() == null ? 0 : in.weight();
    if (weight < 0 || weight > 100) {
      throw ApiException.badRequest("invalid_weight", "The weight must be 0 to 100%.");
    }
    if (in.startDate() != null && in.endDate() != null && in.endDate().isBefore(in.startDate())) {
      throw ApiException.badRequest("invalid_dates", "The exam can't end before it starts.");
    }
    String compsJson = write(comps);
    try {
      UUID eid =
          tx.execute(s -> {
            if (id == null) {
              return jdbc.sql(
                      "insert into exam (organization_id, academic_year, name, term, grades, components, weight, start_date, end_date, sort_order)"
                          + " values (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, (select coalesce(max(sort_order), 0) + 1 from exam where academic_year = ?)) returning id")
                  .params(
                      TenantContext.requireOrganizationId(),
                      year,
                      name,
                      term,
                      grades.toArray(String[]::new),
                      compsJson,
                      weight,
                      in.startDate(),
                      in.endDate(),
                      year)
                  .query(UUID.class)
                  .single();
            }
            Exam before = load(id);
            long marks =
                jdbc.sql("select count(*) from exam_mark where exam_id = ?")
                    .param(id)
                    .query(Long.class)
                    .single();
            if (marks > 0 && !sameParts(before.components(), comps)) {
              throw ApiException.conflict(
                  "has_marks",
                  "Marks have been entered, so the exam's parts and maximum marks can't change.");
            }
            jdbc.sql(
                    "update exam set name = ?, term = ?, grades = ?, components = ?::jsonb, weight = ?, start_date = ?, end_date = ? where id = ?")
                .params(
                    name,
                    term,
                    grades.toArray(String[]::new),
                    compsJson,
                    weight,
                    in.startDate(),
                    in.endDate(),
                    id)
                .update();
            return id;
          });
      audit.record("exams.exam_saved", "exam", eid.toString(), null, Map.of("name", name));
      return load(eid);
    } catch (org.springframework.dao.DuplicateKeyException e) {
      throw ApiException.conflict(
          "duplicate", "There is already an exam called " + name + " this year.");
    }
  }

  static boolean sameParts(List<Grading.Component> a, List<Grading.Component> b) {
    if (a.size() != b.size()) {
      return false;
    }
    for (int i = 0; i < a.size(); i++) {
      if (!a.get(i).key().equals(b.get(i).key()) || a.get(i).max() != b.get(i).max()) {
        return false;
      }
    }
    return true;
  }

  public void delete(UUID id) {
    access.requireModule();
    access.requireManage();
    Exam e = load(id);
    if ("PUBLISHED".equals(e.status())) {
      throw ApiException.conflict("published", "Unpublish the results before deleting the exam.");
    }
    long marks =
        jdbc.sql("select count(*) from exam_mark where exam_id = ?")
            .param(id)
            .query(Long.class)
            .single();
    if (marks > 0) {
      throw ApiException.conflict("has_marks", "This exam has marks, so it can't be deleted.");
    }
    jdbc.sql("delete from exam where id = ?").param(id).update();
    audit.record("exams.exam_deleted", "exam", id.toString(), Map.of("name", e.name()), null);
  }

  /** DRAFT → MARKS_OPEN → PUBLISHED, and back from PUBLISHED to MARKS_OPEN (unpublish). */
  public Exam setStatus(UUID id, String status) {
    access.requireModule();
    access.requireManage();
    Exam e = load(id);
    String to = status == null ? "" : status.toUpperCase(Locale.ROOT);
    switch (to) {
      case "MARKS_OPEN" -> {
        jdbc.sql(
                "update exam set status = 'MARKS_OPEN', published_at = null, published_by = null where id = ?")
            .param(id)
            .update();
        audit.record(
            "PUBLISHED".equals(e.status()) ? "exams.unpublished" : "exams.marks_opened",
            "exam",
            id.toString(),
            null,
            null);
      }
      case "PUBLISHED" -> {
        if (!"MARKS_OPEN".equals(e.status())) {
          throw ApiException.conflict("not_open", "Open the exam for marks first.");
        }
        if (access.feature("moderation")) {
          long waiting =
              jdbc.sql(
                      "select count(*) from (select distinct m.subject_id, en.class_section_id from exam_mark m"
                          + " join student_enrolment en on en.student_id = m.student_id and en.academic_year = (select academic_year from exam where id = ?)"
                          + " where m.exam_id = ?) x where not exists (select 1 from exam_entry y where y.exam_id = ? and y.subject_id = x.subject_id"
                          + " and y.class_section_id = x.class_section_id and y.status = 'APPROVED')")
                  .params(id, id, id)
                  .query(Long.class)
                  .single();
          if (waiting > 0) {
            throw ApiException.conflict(
                "not_approved",
                waiting
                    + " class subject(s) are not approved yet. Approve them under Marks progress first.");
          }
        }
        jdbc.sql(
                "update exam set status = 'PUBLISHED', published_at = now(), published_by = ? where id = ?")
            .params(access.userId(), id)
            .update();
        audit.record("exams.published", "exam", id.toString(), null, Map.of("name", e.name()));
        alertParents(load(id));
      }
      case "DRAFT" -> {
        long marks =
            jdbc.sql("select count(*) from exam_mark where exam_id = ?")
                .param(id)
                .query(Long.class)
                .single();
        if (marks > 0) {
          throw ApiException.conflict("has_marks", "Marks have been entered; the exam stays open.");
        }
        jdbc.sql("update exam set status = 'DRAFT' where id = ?").param(id).update();
      }
      default -> throw ApiException.badRequest("invalid_status", "Unknown status.");
    }
    return load(id);
  }

  private void alertParents(Exam e) {
    if (!access.hasModule("COMMS")) {
      return;
    }
    try {
      List<String> phones =
          jdbc.sql(
                  "select distinct g.phone from student_enrolment en join class_section c on c.id = en.class_section_id"
                      + " join student s on s.id = en.student_id and s.status = 'ENROLLED'"
                      + " join student_guardian sg on sg.student_id = s.id join guardian g on g.id = sg.guardian_id"
                      + " where en.academic_year = ? and c.grade = any(?::text[]) and g.phone is not null")
              .params(access.currentYear(), e.grades().toArray(String[]::new))
              .query(String.class)
              .list();
      if (phones.isEmpty()) {
        return;
      }
      tx.executeWithoutResult(s ->
          broadcasts.alert(
              "Results of " + e.name() + " are published",
              "Sign in to the parent portal on the school website to see your child's report card.",
              RESULTS_TEMPLATE,
              RESULTS_BODY,
              phones));
    } catch (RuntimeException ex) {
      log.warn("Results alert for exam {} failed: {}", e.id(), ex.getMessage());
    }
  }

  // ------------------------------------------------------------------ timetable

  public List<Paper> papers(UUID examId) {
    access.requireModule();
    Exam e = load(examId);
    Map<UUID, Paper> set = new HashMap<>();
    jdbc.sql("select subject_id, date, start_time, duration_min from exam_paper where exam_id = ?")
        .param(examId)
        .query((rs, i) ->
            set.put(
                rs.getObject(1, UUID.class),
                new Paper(
                    rs.getObject(1, UUID.class),
                    null,
                    null,
                    null,
                    true,
                    rs.getObject(2, LocalDate.class),
                    rs.getObject(3, LocalTime.class),
                    (Integer) rs.getObject(4))))
        .list();
    boolean configured = !set.isEmpty();
    List<Paper> out = new ArrayList<>();
    for (Subject s : subjects(null)) {
      if (!e.grades().contains(s.grade())) {
        continue;
      }
      Paper p = set.get(s.id());
      // Until the timetable is set, every subject of the classes is part of the exam.
      out.add(
          new Paper(
              s.id(),
              s.name(),
              s.grade(),
              s.kind(),
              p != null || !configured,
              p == null ? null : p.date(),
              p == null ? null : p.startTime(),
              p == null ? null : p.durationMin()));
    }
    return out;
  }

  public List<Paper> savePapers(UUID examId, List<PaperInput> in) {
    access.requireModule();
    access.requireManage();
    access.requireFeature("timetable", "The exam timetable");
    Exam e = load(examId);
    Map<UUID, Subject> valid = new HashMap<>();
    subjects(null).stream()
        .filter(s -> e.grades().contains(s.grade()))
        .forEach(s -> valid.put(s.id(), s));
    tx.executeWithoutResult(s -> {
      jdbc.sql("delete from exam_paper where exam_id = ?").param(examId).update();
      for (PaperInput p : in == null ? List.<PaperInput>of() : in) {
        if (p == null || p.subjectId() == null || !valid.containsKey(p.subjectId())) {
          throw ApiException.badRequest(
              "invalid_subject", "A subject is not part of this exam's classes.");
        }
        if (!p.included()) {
          continue;
        }
        if (p.durationMin() != null && (p.durationMin() < 10 || p.durationMin() > 300)) {
          throw ApiException.badRequest("invalid_duration", "Papers last 10 to 300 minutes.");
        }
        jdbc.sql(
                "insert into exam_paper (organization_id, exam_id, subject_id, date, start_time, duration_min) values (?, ?, ?, ?, ?, ?)")
            .params(
                TenantContext.requireOrganizationId(),
                examId,
                p.subjectId(),
                p.date(),
                p.startTime(),
                p.durationMin())
            .update();
      }
      audit.record("exams.timetable_saved", "exam", examId.toString(), null, null);
    });
    return papers(examId);
  }

  /** Subjects of a grade that are part of the exam (all, until a timetable is set). */
  List<Subject> examSubjects(Exam e, String grade) {
    List<UUID> chosen =
        jdbc.sql("select subject_id from exam_paper where exam_id = ?")
            .param(e.id())
            .query(UUID.class)
            .list();
    return subjects(grade).stream()
        .filter(
            s -> e.grades().contains(s.grade()) && (chosen.isEmpty() || chosen.contains(s.id())))
        .toList();
  }

  // ------------------------------------------------------------------ helpers

  private String write(Object o) {
    try {
      return json.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
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

  static String blankToNull(String v) {
    return v == null || v.isBlank() ? null : v;
  }

  static Map<String, Object> ordered(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }
}
