package com.shikshablox.modules.exams;

import com.shikshablox.modules.attendance.AttendanceService;
import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.shared.web.ApiException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Report cards, hall tickets and subject analysis (M11). A report card shows every exam of the year
 * for the student's class side by side (parents see published exams only), the weighted year result
 * when exams carry weights, co-scholastic grades, remarks and attendance.
 */
@Service
public class ReportService {

  public record Cell(
      UUID examId,
      Map<String, Double> marks,
      Double total,
      double max,
      Double percent,
      String grade,
      String code) {}

  public record SubjectLine(
      String subject, List<Cell> cells, Double weightedPercent, String weightedGrade) {}

  public record CoLine(String subject, Map<UUID, String> grades) {}

  public record ExamTotal(
      UUID examId,
      Double percent,
      String grade,
      Boolean passed,
      Integer rank,
      Integer of,
      String remark) {}

  public record ExamHead(
      UUID examId,
      String name,
      String term,
      int weight,
      boolean published,
      List<Grading.Component> components) {}

  public record StudentHead(
      UUID id,
      String name,
      String admissionNo,
      String classLabel,
      Integer rollNo,
      LocalDate dateOfBirth,
      String guardians) {}

  public record ReportCard(
      String school,
      String board,
      String academicYear,
      StudentHead student,
      List<ExamHead> exams,
      List<SubjectLine> subjects,
      List<CoLine> coScholastic,
      List<ExamTotal> totals,
      Double weightedPercent,
      String weightedGrade,
      Double attendancePercent,
      Integer daysMarked,
      int passPercent,
      String principal,
      boolean draft,
      Instant generatedAt) {}

  public record PaperLine(
      String subject, LocalDate date, LocalTime startTime, Integer durationMin) {}

  public record HallTicket(StudentHead student, List<PaperLine> papers) {}

  public record HallTickets(
      String school,
      String exam,
      String section,
      List<String> instructions,
      List<HallTicket> tickets) {}

  public record SubjectStats(
      String subject, Double average, Double highest, Double lowest, int passed, int withResult) {}

  public record Topper(String name, Double percent) {}

  public record Analysis(
      UUID sectionId,
      String section,
      int students,
      List<SubjectStats> subjects,
      List<Topper> toppers,
      Double classAverage) {}

  private final JdbcClient jdbc;
  private final AuditService audit;
  private final ExamAccess access;
  private final ExamService exams;
  private final MarksService marks;
  private final AttendanceService attendance;

  ReportService(
      JdbcClient jdbc,
      AuditService audit,
      ExamAccess access,
      ExamService exams,
      MarksService marks,
      AttendanceService attendance) {
    this.jdbc = jdbc;
    this.audit = audit;
    this.access = access;
    this.exams = exams;
    this.marks = marks;
    this.attendance = attendance;
  }

  // ------------------------------------------------------------------ report cards

  /**
   * Report cards of a section (staff: class teacher or exam cell; unpublished exams marked draft).
   */
  public List<ReportCard> forSection(UUID sectionId) {
    access.requireModule();
    access.requireClass(sectionId);
    List<UUID> ids =
        jdbc.sql(
                "select s.id from student_enrolment en join student s on s.id = en.student_id"
                    + " where en.class_section_id = ? and s.status = 'ENROLLED' order by en.roll_no nulls last, s.name")
            .param(sectionId)
            .query(UUID.class)
            .list();
    List<ReportCard> out = new ArrayList<>();
    for (UUID id : ids) {
      out.add(build(id, false));
    }
    audit.record(
        "exams.report_cards_viewed",
        "class_section",
        sectionId.toString(),
        null,
        Map.of("students", ids.size()));
    return out;
  }

  /** A parent's view: published exams only; null when nothing is published yet. */
  public ReportCard forParent(UUID studentId) {
    if (!access.hasModule() || !access.feature("report_cards")) {
      return null;
    }
    ReportCard r = build(studentId, true);
    return r == null || r.exams().isEmpty() ? null : r;
  }

  ReportCard build(UUID studentId, boolean publishedOnly) {
    String year = access.currentYear();
    record Info(StudentHead head, String grade, UUID section) {}
    Info info =
        jdbc.sql(
                "select s.id, s.name, s.admission_no, c.grade, c.section, en.roll_no, s.date_of_birth, c.id,"
                    + " (select string_agg(g.name, ', ' order by sg.is_primary desc, g.name) from student_guardian sg"
                    + "   join guardian g on g.id = sg.guardian_id where sg.student_id = s.id) guardians"
                    + " from student s join student_enrolment en on en.student_id = s.id and en.academic_year = ?"
                    + " join class_section c on c.id = en.class_section_id where s.id = ?")
            .params(year, studentId)
            .query((rs, i) ->
                new Info(
                    new StudentHead(
                        rs.getObject(1, UUID.class),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4).trim() + " " + rs.getString(5).trim(),
                        (Integer) rs.getObject(6),
                        rs.getObject(7, LocalDate.class),
                        rs.getString(9)),
                    rs.getString(4),
                    rs.getObject(8, UUID.class)))
            .optional()
            .orElse(null);
    if (info == null) {
      return null;
    }
    ExamService.Settings st = exams.loadSettings();
    List<ExamService.Exam> list =
        exams.exams(year).stream()
            .filter(e -> e.grades().contains(info.grade()))
            .filter(
                e -> publishedOnly ? "PUBLISHED".equals(e.status()) : !"DRAFT".equals(e.status()))
            .toList();
    List<ExamService.Subject> subjects = exams.subjects(info.grade());
    Map<String, Object[]> m = new HashMap<>();
    jdbc.sql(
            "select exam_id, subject_id, marks::text, grade, code from exam_mark where student_id = ?")
        .param(studentId)
        .query((rs, i) ->
            m.put(
                rs.getString(1) + rs.getString(2),
                new Object[] {marks.parse(rs.getString(3)), rs.getString(4), rs.getString(5)}))
        .list();
    Map<UUID, List<UUID>> examined = new HashMap<>();
    for (ExamService.Exam e : list) {
      examined.put(
          e.id(),
          exams.examSubjects(e, info.grade()).stream().map(ExamService.Subject::id).toList());
    }
    List<SubjectLine> lines = new ArrayList<>();
    List<CoLine> co = new ArrayList<>();
    Map<UUID, List<Grading.Result>> perExam = new HashMap<>();
    for (ExamService.Subject s : subjects) {
      if ("CO_SCHOLASTIC".equals(s.kind())) {
        Map<UUID, String> g = new java.util.LinkedHashMap<>();
        for (ExamService.Exam e : list) {
          Object[] v = m.get(e.id().toString() + s.id());
          if (v != null) {
            g.put(e.id(), v[2] != null ? (String) v[2] : (String) v[1]);
          }
        }
        if (!g.isEmpty()) {
          co.add(new CoLine(s.name(), g));
        }
        continue;
      }
      List<Cell> cells = new ArrayList<>();
      List<Double> pcts = new ArrayList<>();
      List<Integer> weights = new ArrayList<>();
      boolean any = false;
      for (ExamService.Exam e : list) {
        if (!examined.get(e.id()).contains(s.id())) {
          cells.add(null);
          continue;
        }
        Object[] v = m.get(e.id().toString() + s.id());
        @SuppressWarnings("unchecked")
        Map<String, Double> mk = v == null ? Map.of() : (Map<String, Double>) v[0];
        Grading.Result r =
            Grading.result(mk, v == null ? null : (String) v[2], e.components(), st.bands());
        perExam.computeIfAbsent(e.id(), k -> new ArrayList<>()).add(r);
        cells.add(new Cell(e.id(), mk, r.total(), r.max(), r.percent(), r.grade(), r.code()));
        pcts.add(r.percent());
        weights.add(e.weight());
        any = any || v != null;
      }
      if (any || !list.isEmpty()) {
        Double w = Grading.weighted(pcts, weights);
        lines.add(
            new SubjectLine(s.name(), cells, w, w == null ? null : Grading.grade(w, st.bands())));
      }
    }
    Map<UUID, String> remarks = new HashMap<>();
    jdbc.sql("select exam_id, remark from exam_remark where student_id = ?")
        .param(studentId)
        .query((rs, i) -> remarks.put(rs.getObject(1, UUID.class), rs.getString(2)))
        .list();
    List<ExamTotal> totals = new ArrayList<>();
    List<Double> overallPcts = new ArrayList<>();
    List<Integer> overallWeights = new ArrayList<>();
    for (ExamService.Exam e : list) {
      List<Grading.Result> rs = perExam.getOrDefault(e.id(), List.of());
      Double pct = Grading.overall(rs);
      Integer rank = null;
      Integer of = null;
      if (st.showRank() && pct != null) {
        List<Double> all = sectionOverall(e, info.section(), info.grade(), st);
        of = all.size();
        rank = 1 + (int) all.stream().filter(p -> p > pct).count();
      }
      totals.add(
          new ExamTotal(
              e.id(),
              pct,
              pct == null ? null : Grading.grade(pct, st.bands()),
              Grading.passed(rs, st.passPercent()),
              rank,
              of,
              remarks.get(e.id())));
      overallPcts.add(pct);
      overallWeights.add(e.weight());
    }
    Double weighted = Grading.weighted(overallPcts, overallWeights);
    AttendanceService.ChildAttendance att = attendance.child(studentId);
    String school =
        jdbc.sql(
                "select name from organization where id = (select organization_id from student where id = ?)")
            .param(studentId)
            .query(String.class)
            .single();
    List<ExamHead> heads =
        list.stream()
            .map(e ->
                new ExamHead(
                    e.id(),
                    e.name(),
                    e.term(),
                    e.weight(),
                    "PUBLISHED".equals(e.status()),
                    e.components()))
            .toList();
    return new ReportCard(
        school,
        st.board(),
        year,
        info.head(),
        heads,
        lines,
        co,
        totals,
        weighted,
        weighted == null ? null : Grading.grade(weighted, st.bands()),
        att == null ? null : att.yearPercent(),
        att == null ? null : att.daysMarked(),
        st.passPercent(),
        st.principalName(),
        heads.stream().anyMatch(h -> !h.published()),
        Instant.now());
  }

  /** Overall % of every student of the section in an exam (for rank). */
  private List<Double> sectionOverall(
      ExamService.Exam e, UUID section, String grade, ExamService.Settings st) {
    List<UUID> subjects =
        exams.examSubjects(e, grade).stream()
            .filter(s -> "SCHOLASTIC".equals(s.kind()))
            .map(ExamService.Subject::id)
            .toList();
    Map<UUID, List<Grading.Result>> by = new HashMap<>();
    jdbc.sql(
            "select m.student_id, m.subject_id, m.marks::text, m.code from exam_mark m join student_enrolment en"
                + " on en.student_id = m.student_id and en.class_section_id = ? where m.exam_id = ?")
        .params(section, e.id())
        .query((rs, i) -> {
          if (subjects.contains(rs.getObject(2, UUID.class))) {
            by.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                .add(
                    Grading.result(
                        marks.parse(rs.getString(3)), rs.getString(4), e.components(), st.bands()));
          }
          return null;
        })
        .list();
    List<Double> out = new ArrayList<>();
    by.values()
        .forEach(v -> {
          Double p = Grading.overall(v);
          if (p != null) {
            out.add(p);
          }
        });
    return out;
  }

  // ------------------------------------------------------------------ hall tickets

  public HallTickets hallTickets(UUID examId, UUID sectionId) {
    access.requireModule();
    access.requireFeature("hall_tickets", "Hall tickets");
    access.requireClass(sectionId);
    ExamService.Exam e = exams.load(examId);
    record Sec(String grade, String label) {}
    Sec sec =
        jdbc.sql("select grade, section from class_section where id = ?")
            .param(sectionId)
            .query((rs, i) ->
                new Sec(rs.getString(1), rs.getString(1).trim() + " " + rs.getString(2).trim()))
            .optional()
            .orElseThrow(() -> ApiException.notFound("Class"));
    if (!e.grades().contains(sec.grade())) {
      throw ApiException.badRequest("not_in_exam", sec.label() + " does not sit this exam.");
    }
    List<PaperLine> papers =
        jdbc.sql(
                "select s.name, p.date, p.start_time, p.duration_min from exam_paper p join subject s on s.id = p.subject_id"
                    + " where p.exam_id = ? and s.grade = ? and s.kind = 'SCHOLASTIC' order by p.date nulls last, p.start_time, s.sort_order")
            .params(examId, sec.grade())
            .query((rs, i) ->
                new PaperLine(
                    rs.getString(1),
                    rs.getObject(2, LocalDate.class),
                    rs.getObject(3, LocalTime.class),
                    (Integer) rs.getObject(4)))
            .list();
    if (papers.isEmpty()) {
      throw ApiException.conflict("no_timetable", "Set the exam timetable first.");
    }
    List<HallTicket> tickets =
        jdbc.sql(
                "select s.id, s.name, s.admission_no, en.roll_no, s.date_of_birth from student_enrolment en"
                    + " join student s on s.id = en.student_id where en.class_section_id = ? and s.status = 'ENROLLED'"
                    + " order by en.roll_no nulls last, s.name")
            .param(sectionId)
            .query((rs, i) ->
                new HallTicket(
                    new StudentHead(
                        rs.getObject(1, UUID.class),
                        rs.getString(2),
                        rs.getString(3),
                        sec.label(),
                        (Integer) rs.getObject(4),
                        rs.getObject(5, LocalDate.class),
                        null),
                    papers))
            .list();
    String school =
        jdbc.sql(
                "select name from organization where id = (select organization_id from exam where id = ?)")
            .param(examId)
            .query(String.class)
            .single();
    return new HallTickets(
        school,
        e.name(),
        sec.label(),
        List.of(
            "Bring this hall ticket to every paper.",
            "Reach the exam hall 15 minutes before the start time.",
            "Mobile phones and smart watches are not allowed in the exam hall.",
            "Write your name and roll number on every answer sheet."),
        tickets);
  }

  // ------------------------------------------------------------------ analysis

  public List<Analysis> analysis(UUID examId) {
    access.requireModule();
    access.requireManage();
    access.requireFeature("analysis", "Result analysis");
    ExamService.Exam e = exams.load(examId);
    ExamService.Settings st = exams.loadSettings();
    record Sec(UUID id, String grade, String label) {}
    List<Sec> sections =
        jdbc.sql(
                "select id, grade, section from class_section where academic_year = ? and grade = any(?::text[])"
                    + " order by grade_order, grade, section")
            .params(access.currentYear(), e.grades().toArray(String[]::new))
            .query((rs, i) ->
                new Sec(
                    rs.getObject(1, UUID.class),
                    rs.getString(2),
                    rs.getString(2).trim() + " " + rs.getString(3).trim()))
            .list();
    List<Analysis> out = new ArrayList<>();
    for (Sec sec : sections) {
      List<ExamService.Subject> subs =
          exams.examSubjects(e, sec.grade()).stream()
              .filter(s -> "SCHOLASTIC".equals(s.kind()))
              .toList();
      record M(UUID student, String name, UUID subject, Grading.Result r) {}
      List<M> rows =
          jdbc.sql(
                  "select s.id, s.name, m.subject_id, m.marks::text, m.code from exam_mark m join student s on s.id = m.student_id"
                      + " join student_enrolment en on en.student_id = s.id and en.class_section_id = ? where m.exam_id = ?")
              .params(sec.id(), examId)
              .query((rs, i) ->
                  new M(
                      rs.getObject(1, UUID.class),
                      rs.getString(2),
                      rs.getObject(3, UUID.class),
                      Grading.result(
                          marks.parse(rs.getString(4)),
                          rs.getString(5),
                          e.components(),
                          st.bands())))
              .list();
      List<SubjectStats> stats = new ArrayList<>();
      for (ExamService.Subject s : subs) {
        List<Double> p =
            rows.stream()
                .filter(r -> r.subject().equals(s.id()) && r.r().percent() != null)
                .map(r -> r.r().percent())
                .toList();
        stats.add(
            new SubjectStats(
                s.name(),
                p.isEmpty()
                    ? null
                    : Grading.round1(
                        p.stream().mapToDouble(Double::doubleValue).average().orElse(0)),
                p.stream().max(Double::compare).orElse(null),
                p.stream().min(Double::compare).orElse(null),
                (int) p.stream().filter(x -> x >= st.passPercent()).count(),
                p.size()));
      }
      Map<UUID, List<Grading.Result>> byStudent = new HashMap<>();
      Map<UUID, String> names = new HashMap<>();
      List<UUID> subIds = subs.stream().map(ExamService.Subject::id).toList();
      for (M r : rows) {
        if (subIds.contains(r.subject())) {
          byStudent.computeIfAbsent(r.student(), k -> new ArrayList<>()).add(r.r());
          names.put(r.student(), r.name());
        }
      }
      List<Topper> all = new ArrayList<>();
      byStudent.forEach((k, v) -> {
        Double o = Grading.overall(v);
        if (o != null) {
          all.add(new Topper(names.get(k), o));
        }
      });
      all.sort(Comparator.comparing(Topper::percent).reversed());
      Double avg =
          all.isEmpty()
              ? null
              : Grading.round1(all.stream().mapToDouble(Topper::percent).average().orElse(0));
      out.add(
          new Analysis(
              sec.id(), sec.label(), byStudent.size(), stats, all.stream().limit(3).toList(), avg));
    }
    return out;
  }
}
