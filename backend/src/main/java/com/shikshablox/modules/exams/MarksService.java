package com.shikshablox.modules.exams;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.TenantContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Marks entry (M11): one sheet per exam, section and subject. Teachers enter while the exam is
 * open; with moderation (Standard) they submit the class's marks and the exam cell approves or
 * sends them back. Class teachers also write a remark per student.
 */
@Service
public class MarksService {

  private static final TypeReference<Map<String, Double>> MARKS = new TypeReference<>() {};

  public record Row(
      UUID studentId,
      String name,
      Integer rollNo,
      Map<String, Double> marks,
      String grade,
      String code,
      Double total,
      Double percent,
      String resultGrade) {}

  public record Sheet(
      UUID examId,
      String exam,
      String examStatus,
      UUID sectionId,
      String section,
      UUID subjectId,
      String subject,
      String kind,
      List<Grading.Component> components,
      List<String> coGrades,
      String entryStatus,
      String note,
      boolean editable,
      boolean moderation,
      boolean canManage,
      List<Row> rows) {}

  public record RowInput(UUID studentId, Map<String, Double> marks, String grade, String code) {}

  public record Task(
      UUID sectionId,
      String section,
      UUID subjectId,
      String subject,
      String kind,
      int students,
      int entered,
      String status) {}

  public record Remark(UUID studentId, String name, Integer rollNo, String remark) {}

  public record RemarkInput(UUID studentId, String remark) {}

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final AuditService audit;
  private final ExamAccess access;
  private final ExamService exams;
  private final ObjectMapper json;

  MarksService(
      JdbcClient jdbc,
      TransactionTemplate tx,
      AuditService audit,
      ExamAccess access,
      ExamService exams,
      ObjectMapper json) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.audit = audit;
    this.access = access;
    this.exams = exams;
    this.json = json;
  }

  // ------------------------------------------------------------------ progress / my work

  /** Marks progress of an exam: every class subject for the exam cell, own ones for teachers. */
  public List<Task> progress(UUID examId) {
    access.requireModule();
    ExamService.Exam e = exams.load(examId);
    boolean manager = access.canManage();
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
    Set<UUID> classTeacherOf =
        Set.copyOf(
            jdbc.sql("select class_section_id from attendance_teacher where user_id = ?")
                .param(access.userId())
                .query(UUID.class)
                .list());
    Set<String> teaches = new java.util.HashSet<>();
    jdbc.sql("select class_section_id, subject_id from subject_teacher where user_id = ?")
        .param(access.userId())
        .query((rs, i) -> teaches.add(rs.getString(1) + rs.getString(2)))
        .list();
    Map<String, String> status = new HashMap<>();
    jdbc.sql("select class_section_id, subject_id, status from exam_entry where exam_id = ?")
        .param(examId)
        .query((rs, i) -> status.put(rs.getString(1) + rs.getString(2), rs.getString(3)))
        .list();
    Map<String, Integer> entered = new HashMap<>();
    jdbc.sql(
            "select en.class_section_id, m.subject_id, count(*) from exam_mark m join student_enrolment en"
                + " on en.student_id = m.student_id and en.academic_year = ? where m.exam_id = ?"
                + " and (m.code is not null or m.grade is not null or m.marks <> '{}'::jsonb) group by 1, 2")
        .params(access.currentYear(), examId)
        .query((rs, i) -> entered.put(rs.getString(1) + rs.getString(2), rs.getInt(3)))
        .list();
    Map<UUID, Integer> size = new HashMap<>();
    jdbc.sql(
            "select en.class_section_id, count(*) from student_enrolment en join student s on s.id = en.student_id"
                + " where en.academic_year = ? and s.status = 'ENROLLED' group by 1")
        .param(access.currentYear())
        .query((rs, i) -> size.put(rs.getObject(1, UUID.class), rs.getInt(2)))
        .list();
    List<Task> out = new ArrayList<>();
    for (Sec s : sections) {
      for (ExamService.Subject sub : exams.examSubjects(e, s.grade())) {
        String k = s.id().toString() + sub.id();
        if (!manager && !classTeacherOf.contains(s.id()) && !teaches.contains(k)) {
          continue;
        }
        out.add(
            new Task(
                s.id(),
                s.label(),
                sub.id(),
                sub.name(),
                sub.kind(),
                size.getOrDefault(s.id(), 0),
                entered.getOrDefault(k, 0),
                status.getOrDefault(k, "OPEN")));
      }
    }
    return out;
  }

  // ------------------------------------------------------------------ sheet

  public Sheet sheet(UUID examId, UUID sectionId, UUID subjectId) {
    access.requireModule();
    access.requireEntry(sectionId, subjectId);
    return load(examId, sectionId, subjectId);
  }

  private Sheet load(UUID examId, UUID sectionId, UUID subjectId) {
    ExamService.Exam e = exams.load(examId);
    if ("DRAFT".equals(e.status())) {
      throw ApiException.conflict("not_open", "Marks entry for this exam has not opened yet.");
    }
    record Sec(String grade, String label) {}
    Sec sec =
        jdbc.sql("select grade, section from class_section where id = ?")
            .param(sectionId)
            .query((rs, i) ->
                new Sec(rs.getString(1), rs.getString(1).trim() + " " + rs.getString(2).trim()))
            .optional()
            .orElseThrow(() -> ApiException.notFound("Class"));
    ExamService.Subject sub =
        exams.examSubjects(e, sec.grade()).stream()
            .filter(s -> s.id().equals(subjectId))
            .findFirst()
            .orElseThrow(() -> ApiException.notFound("Subject in this exam"));
    ExamService.Settings st = exams.loadSettings();
    record Entry(String status, String note) {}
    Entry entry =
        jdbc.sql(
                "select status, note from exam_entry where exam_id = ? and subject_id = ? and class_section_id = ?")
            .params(examId, subjectId, sectionId)
            .query((rs, i) -> new Entry(rs.getString(1), rs.getString(2)))
            .optional()
            .orElse(new Entry("OPEN", null));
    boolean co = "CO_SCHOLASTIC".equals(sub.kind());
    List<Row> rows =
        jdbc.sql(
                "select s.id, s.name, en.roll_no, m.marks::text, m.grade, m.code from student_enrolment en"
                    + " join student s on s.id = en.student_id"
                    + " left join exam_mark m on m.exam_id = ? and m.subject_id = ? and m.student_id = s.id"
                    + " where en.class_section_id = ? and (s.status = 'ENROLLED' or m.student_id is not null)"
                    + " order by en.roll_no nulls last, s.name")
            .params(examId, subjectId, sectionId)
            .query((rs, i) -> {
              Map<String, Double> marks = parse(rs.getString(4));
              String code = rs.getString(6);
              Grading.Result r =
                  co ? null : Grading.result(marks, code, e.components(), st.bands());
              return new Row(
                  rs.getObject(1, UUID.class),
                  rs.getString(2),
                  (Integer) rs.getObject(3),
                  marks,
                  rs.getString(5),
                  code,
                  r == null ? null : r.total(),
                  r == null ? null : r.percent(),
                  r == null ? null : r.grade());
            })
            .list();
    boolean manager = access.canManage();
    boolean editable =
        "MARKS_OPEN".equals(e.status())
            && ("OPEN".equals(entry.status()) || (manager && "SUBMITTED".equals(entry.status())));
    return new Sheet(
        examId,
        e.name(),
        e.status(),
        sectionId,
        sec.label(),
        subjectId,
        sub.name(),
        sub.kind(),
        co ? List.of() : e.components(),
        st.coGrades(),
        entry.status(),
        entry.note(),
        editable,
        st.moderation(),
        manager,
        rows);
  }

  public Sheet save(UUID examId, UUID sectionId, UUID subjectId, List<RowInput> in) {
    access.requireModule();
    access.requireEntry(sectionId, subjectId);
    Sheet sh = load(examId, sectionId, subjectId);
    if (!sh.editable()) {
      throw ApiException.conflict(
          "locked",
          "PUBLISHED".equals(sh.examStatus())
              ? "Results are published. The exam cell must unpublish them before marks can change."
              : "These marks have been submitted. Ask the exam cell to send them back for changes.");
    }
    Map<UUID, Row> roster = new HashMap<>();
    sh.rows().forEach(r -> roster.put(r.studentId(), r));
    boolean co = "CO_SCHOLASTIC".equals(sh.kind());
    Set<String> coGrades = Set.copyOf(sh.coGrades());
    List<Object[]> writes = new ArrayList<>();
    for (RowInput r : in == null ? List.<RowInput>of() : in) {
      if (r == null || r.studentId() == null || !roster.containsKey(r.studentId())) {
        throw ApiException.badRequest("invalid_student", "A student is not in this class.");
      }
      String who = roster.get(r.studentId()).name();
      String code =
          r.code() == null || r.code().isBlank()
              ? null
              : r.code().trim().toUpperCase(java.util.Locale.ROOT);
      if (code != null && !Set.of("AB", "ML").contains(code)) {
        throw ApiException.badRequest("invalid_code", "Use AB (absent) or ML (medical leave).");
      }
      Map<String, Double> marks =
          code != null || co ? Map.of() : Grading.validate(r.marks(), sh.components(), who);
      String grade = null;
      if (co && code == null && r.grade() != null && !r.grade().isBlank()) {
        grade = r.grade().trim();
        if (!coGrades.contains(grade)) {
          throw ApiException.badRequest(
              "invalid_grade", who + ": choose one of " + String.join(", ", sh.coGrades()) + ".");
        }
      }
      writes.add(new Object[] {r.studentId(), write(marks), grade, code});
    }
    tx.executeWithoutResult(s -> {
      for (Object[] w : writes) {
        jdbc.sql(
                "insert into exam_mark (organization_id, exam_id, subject_id, student_id, marks, grade, code, updated_by)"
                    + " values (?, ?, ?, ?, ?::jsonb, ?, ?, ?) on conflict (exam_id, subject_id, student_id) do update set"
                    + " marks = excluded.marks, grade = excluded.grade, code = excluded.code, updated_by = excluded.updated_by, updated_at = now()")
            .params(
                TenantContext.requireOrganizationId(),
                examId,
                subjectId,
                w[0],
                w[1],
                w[2],
                w[3],
                access.userId())
            .update();
      }
      audit.record(
          "exams.marks_saved",
          "exam",
          examId.toString(),
          null,
          Map.of("section", sectionId.toString(), "subject", sh.subject(), "rows", writes.size()));
    });
    return load(examId, sectionId, subjectId);
  }

  /** Teacher hands in the class's marks (moderation): every student needs marks or a code. */
  public Sheet submit(UUID examId, UUID sectionId, UUID subjectId) {
    access.requireModule();
    access.requireFeature("moderation", "Submitting marks for approval");
    access.requireEntry(sectionId, subjectId);
    Sheet sh = load(examId, sectionId, subjectId);
    if (!"MARKS_OPEN".equals(sh.examStatus()) || !"OPEN".equals(sh.entryStatus())) {
      throw ApiException.conflict("not_open", "These marks can't be submitted now.");
    }
    boolean co = "CO_SCHOLASTIC".equals(sh.kind());
    long missing =
        sh.rows().stream()
            .filter(r ->
                r.code() == null
                    && (co ? r.grade() == null : r.marks().size() < sh.components().size()))
            .count();
    if (missing > 0) {
      throw ApiException.badRequest(
          "incomplete",
          missing + " student(s) have no marks yet. Enter marks, or AB / ML, for everyone.");
    }
    setEntry(examId, subjectId, sectionId, "SUBMITTED", null);
    audit.record(
        "exams.marks_submitted",
        "exam",
        examId.toString(),
        null,
        Map.of("subject", sh.subject(), "section", sh.section()));
    return load(examId, sectionId, subjectId);
  }

  /** Exam cell approves the class's marks, or sends them back with a note. */
  public Sheet moderate(UUID examId, UUID sectionId, UUID subjectId, boolean approve, String note) {
    access.requireModule();
    access.requireManage();
    access.requireFeature("moderation", "Moderation");
    Sheet sh = load(examId, sectionId, subjectId);
    String n = note == null || note.isBlank() ? null : note.trim();
    if (n != null && n.length() > 300) {
      throw ApiException.badRequest("too_long", "Keep the note under 300 characters.");
    }
    if (!approve && n == null) {
      throw ApiException.badRequest("missing_note", "Tell the teacher what needs to change.");
    }
    if (approve
        && sh.rows().stream()
            .anyMatch(r -> r.code() == null && r.grade() == null && r.marks().isEmpty())) {
      throw ApiException.badRequest("incomplete", "Some students have no marks yet.");
    }
    setEntry(examId, subjectId, sectionId, approve ? "APPROVED" : "OPEN", n);
    audit.record(
        approve ? "exams.marks_approved" : "exams.marks_returned",
        "exam",
        examId.toString(),
        null,
        Map.of("subject", sh.subject(), "section", sh.section()));
    return load(examId, sectionId, subjectId);
  }

  private void setEntry(UUID examId, UUID subjectId, UUID sectionId, String status, String note) {
    boolean approved = "APPROVED".equals(status);
    boolean submitted = "SUBMITTED".equals(status);
    jdbc.sql(
            "insert into exam_entry (organization_id, exam_id, subject_id, class_section_id, status, note, submitted_by, submitted_at,"
                + " approved_by, approved_at) values (?, ?, ?, ?, ?, ?, ?, case when ? then now() end, ?, case when ? then now() end)"
                + " on conflict (exam_id, subject_id, class_section_id) do update set status = excluded.status, note = excluded.note,"
                + " submitted_by = coalesce(excluded.submitted_by, exam_entry.submitted_by),"
                + " submitted_at = coalesce(excluded.submitted_at, exam_entry.submitted_at),"
                + " approved_by = excluded.approved_by, approved_at = excluded.approved_at")
        .params(
            TenantContext.requireOrganizationId(),
            examId,
            subjectId,
            sectionId,
            status,
            note,
            submitted ? access.userId() : null,
            submitted,
            approved ? access.userId() : null,
            approved)
        .update();
  }

  // ------------------------------------------------------------------ remarks

  public List<Remark> remarks(UUID examId, UUID sectionId) {
    access.requireModule();
    access.requireClass(sectionId);
    exams.load(examId);
    return jdbc.sql(
            "select s.id, s.name, en.roll_no, r.remark from student_enrolment en join student s on s.id = en.student_id"
                + " left join exam_remark r on r.exam_id = ? and r.student_id = s.id"
                + " where en.class_section_id = ? and s.status = 'ENROLLED' order by en.roll_no nulls last, s.name")
        .params(examId, sectionId)
        .query((rs, i) ->
            new Remark(
                rs.getObject(1, UUID.class),
                rs.getString(2),
                (Integer) rs.getObject(3),
                rs.getString(4)))
        .list();
  }

  public List<Remark> saveRemarks(UUID examId, UUID sectionId, List<RemarkInput> in) {
    access.requireModule();
    access.requireClass(sectionId);
    ExamService.Exam e = exams.load(examId);
    if (!"MARKS_OPEN".equals(e.status())) {
      throw ApiException.conflict(
          "locked", "Remarks can be written while the exam is open for marks.");
    }
    Map<UUID, Remark> roster = new LinkedHashMap<>();
    remarks(examId, sectionId).forEach(r -> roster.put(r.studentId(), r));
    tx.executeWithoutResult(s -> {
      for (RemarkInput r : in == null ? List.<RemarkInput>of() : in) {
        if (r == null || !roster.containsKey(r.studentId())) {
          throw ApiException.badRequest("invalid_student", "A student is not in this class.");
        }
        String t = r.remark() == null ? "" : r.remark().trim();
        if (t.length() > 500) {
          throw ApiException.badRequest("too_long", "Keep remarks under 500 characters.");
        }
        if (t.isEmpty()) {
          jdbc.sql("delete from exam_remark where exam_id = ? and student_id = ?")
              .params(examId, r.studentId())
              .update();
        } else {
          jdbc.sql(
                  "insert into exam_remark (organization_id, exam_id, student_id, remark, updated_by) values (?, ?, ?, ?, ?)"
                      + " on conflict (exam_id, student_id) do update set remark = excluded.remark, updated_by = excluded.updated_by, updated_at = now()")
              .params(
                  TenantContext.requireOrganizationId(), examId, r.studentId(), t, access.userId())
              .update();
        }
      }
      audit.record(
          "exams.remarks_saved",
          "exam",
          examId.toString(),
          null,
          Map.of("section", sectionId.toString()));
    });
    return remarks(examId, sectionId);
  }

  // ------------------------------------------------------------------ helpers

  Map<String, Double> parse(String s) {
    if (s == null || s.isBlank()) {
      return Map.of();
    }
    try {
      return json.readValue(s, MARKS);
    } catch (Exception e) {
      return Map.of();
    }
  }

  private String write(Object o) {
    try {
      return json.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
