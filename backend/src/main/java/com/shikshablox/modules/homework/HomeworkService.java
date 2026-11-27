package com.shikshablox.modules.homework;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shikshablox.modules.comms.BroadcastService;
import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.entitlement.EntitlementService;
import com.shikshablox.platform.files.MediaService;
import com.shikshablox.platform.identity.CurrentUser;
import com.shikshablox.platform.identity.Permission;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.TenantContext;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Homework (M10). Teachers post to the sections they teach (class or subject teacher), school
 * admins to any. Parents of the section get one alert per homework and see it in the portal; with
 * submissions on, a parent hands in work for their child, which the teacher marks.
 */
@Service
public class HomeworkService {

  private static final Logger log = LoggerFactory.getLogger(HomeworkService.class);
  static final String MODULE = "HOMEWORK";
  static final ZoneId IST = ZoneId.of("Asia/Kolkata");
  static final long MAX_FILE = 10L * 1024 * 1024;
  static final long MAX_SUBMISSION_FILE = 8L * 1024 * 1024;
  private static final DateTimeFormatter DAY =
      DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
  private static final TypeReference<List<Criterion>> RUBRIC = new TypeReference<>() {};
  private static final TypeReference<Map<String, Double>> SCORES = new TypeReference<>() {};

  public record Criterion(String criterion, double points) {}

  public record SectionRef(UUID id, String label, String grade) {}

  public record SubjectRef(UUID id, String grade, String name) {}

  public record Overview(
      List<SectionRef> sections,
      List<SubjectRef> subjects,
      boolean canManage,
      boolean submissions,
      boolean rubrics,
      boolean alerts) {}

  public record FileRef(UUID id, String name, long sizeBytes) {}

  public record Homework(
      UUID id,
      UUID sectionId,
      String section,
      UUID subjectId,
      String subject,
      String title,
      String details,
      LocalDate assignedOn,
      LocalDate dueOn,
      boolean allowSubmissions,
      Double maxPoints,
      List<Criterion> rubric,
      String latePolicy,
      boolean notifyParents,
      String createdBy,
      List<FileRef> files,
      int students,
      int submitted,
      int graded) {}

  public record HomeworkInput(
      List<UUID> sectionIds,
      UUID subjectId,
      String subjectName,
      String title,
      String details,
      LocalDate dueOn,
      Boolean allowSubmissions,
      Double maxPoints,
      List<Criterion> rubric,
      String latePolicy,
      Boolean notifyParents) {}

  public record Submission(
      UUID id,
      UUID studentId,
      String studentName,
      Integer rollNo,
      String status,
      String text,
      String fileName,
      Instant submittedAt,
      boolean late,
      Double points,
      Map<String, Double> rubricScores,
      String feedback,
      Instant gradedAt) {}

  public record GradeInput(
      Double points, Map<String, Double> rubricScores, String feedback, boolean returnForRedo) {}

  /** What the parent sees for one homework of one child. */
  public record ChildHomework(
      UUID id,
      UUID studentId,
      String studentName,
      String subject,
      String title,
      String details,
      LocalDate assignedOn,
      LocalDate dueOn,
      List<FileRef> files,
      boolean allowSubmissions,
      boolean closed,
      Double maxPoints,
      Submission submission) {}

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final AuditService audit;
  private final EntitlementService entitlements;
  private final MediaService media;
  private final BroadcastService broadcasts;
  private final ObjectMapper json;

  HomeworkService(
      JdbcClient jdbc,
      TransactionTemplate tx,
      AuditService audit,
      @org.springframework.context.annotation.Lazy EntitlementService entitlements,
      MediaService media,
      BroadcastService broadcasts,
      ObjectMapper json) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.audit = audit;
    this.entitlements = entitlements;
    this.media = media;
    this.broadcasts = broadcasts;
    this.json = json;
  }

  // ------------------------------------------------------------------ access

  private void requireModule() {
    entitlements.requireModule(MODULE);
  }

  private boolean feature(String f) {
    return entitlements.hasFeature(MODULE, f);
  }

  static boolean canManage() {
    return CurrentUser.get()
        .map(u ->
            u.roles().stream().anyMatch(r -> r.permissions().contains(Permission.SCHOOL_MANAGE)))
        .orElse(false);
  }

  private UUID userId() {
    return TenantContext.userId().orElse(null);
  }

  private String currentYear() {
    return jdbc.sql("select current_year from sis_settings")
        .query(String.class)
        .optional()
        .orElseGet(() -> {
          LocalDate d = LocalDate.now(IST);
          int start = d.getMonthValue() >= 4 ? d.getYear() : d.getYear() - 1;
          return start + "-" + String.format("%02d", (start + 1) % 100);
        });
  }

  /**
   * Sections of the current year this user teaches (class or subject teacher), or all for admins.
   */
  List<SectionRef> mySections() {
    String year = currentYear();
    String sql =
        "select c.id, c.grade, c.section from class_section c where c.academic_year = ?"
            + (canManage()
                ? ""
                : " and (exists (select 1 from attendance_teacher t where t.class_section_id = c.id and t.user_id = ?)"
                    + " or exists (select 1 from subject_teacher t where t.class_section_id = c.id and t.user_id = ?))")
            + " order by c.grade_order, c.grade, c.section";
    return jdbc.sql(sql)
        .params(canManage() ? List.of(year) : List.of(year, userId(), userId()))
        .query((rs, i) ->
            new SectionRef(
                rs.getObject(1, UUID.class),
                rs.getString(2).trim() + " " + rs.getString(3).trim(),
                rs.getString(2)))
        .list();
  }

  private void requireSection(UUID sectionId) {
    if (mySections().stream().noneMatch(s -> s.id().equals(sectionId))) {
      throw new ApiException(HttpStatus.FORBIDDEN, "not_your_class", "You don't teach this class.");
    }
  }

  // ------------------------------------------------------------------ staff

  public Overview overview() {
    requireModule();
    List<SectionRef> sections = mySections();
    Set<String> grades = new LinkedHashSet<>();
    sections.forEach(s -> grades.add(s.grade()));
    List<SubjectRef> subjects =
        grades.isEmpty()
            ? List.of()
            : jdbc.sql(
                    "select id, grade, name from subject where academic_year = ? and grade = any(?::text[]) and kind = 'SCHOLASTIC'"
                        + " order by grade, sort_order, name")
                .params(currentYear(), grades.toArray(String[]::new))
                .query((rs, i) ->
                    new SubjectRef(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)))
                .list();
    return new Overview(
        sections,
        subjects,
        canManage(),
        feature("submissions"),
        feature("rubrics"),
        feature("homework_alerts") && entitlements.hasModule("COMMS"));
  }

  public List<Homework> list(UUID sectionId) {
    requireModule();
    List<UUID> ids = mySections().stream().map(SectionRef::id).toList();
    if (sectionId != null) {
      requireSection(sectionId);
      ids = List.of(sectionId);
    }
    if (ids.isEmpty()) {
      return List.of();
    }
    return jdbc
        .sql(
            SELECT
                + " where h.class_section_id = any(?::uuid[]) and h.due_on >= ? order by h.due_on desc, h.created_at desc limit 200")
        .params(ids.toArray(UUID[]::new), LocalDate.now(IST).minusDays(60))
        .query((rs, i) -> map(rs))
        .list()
        .stream()
        .map(this::withFiles)
        .toList();
  }

  private static final String SELECT =
      "select h.*, c.grade, c.section, coalesce(s.name, h.subject_name) subject_label, u.display_name created_by_name,"
          + " (select count(*) from student_enrolment e join student st on st.id = e.student_id"
          + "   where e.class_section_id = h.class_section_id and st.status = 'ENROLLED') students,"
          + " (select count(*) from homework_submission x where x.homework_id = h.id) submitted,"
          + " (select count(*) from homework_submission x where x.homework_id = h.id and x.status = 'GRADED') graded"
          + " from homework h join class_section c on c.id = h.class_section_id"
          + " left join subject s on s.id = h.subject_id left join app_user u on u.id = h.created_by";

  Homework load(UUID id) {
    return withFiles(
        jdbc.sql(SELECT + " where h.id = ?")
            .param(id)
            .query((rs, i) -> map(rs))
            .optional()
            .orElseThrow(() -> ApiException.notFound("Homework")));
  }

  private Homework withFiles(Homework h) {
    List<FileRef> files =
        jdbc.sql(
                "select id, original_filename, size_bytes from homework_file where homework_id = ? order by created_at")
            .param(h.id())
            .query(
                (rs, i) -> new FileRef(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3)))
            .list();
    return new Homework(
        h.id(),
        h.sectionId(),
        h.section(),
        h.subjectId(),
        h.subject(),
        h.title(),
        h.details(),
        h.assignedOn(),
        h.dueOn(),
        h.allowSubmissions(),
        h.maxPoints(),
        h.rubric(),
        h.latePolicy(),
        h.notifyParents(),
        h.createdBy(),
        files,
        h.students(),
        h.submitted(),
        h.graded());
  }

  /** Posts the homework to each chosen section (one copy per section) and alerts parents. */
  public List<Homework> create(HomeworkInput in) {
    requireModule();
    List<UUID> sections =
        in.sectionIds() == null ? List.of() : List.copyOf(new LinkedHashSet<>(in.sectionIds()));
    if (sections.isEmpty() || sections.size() > 20) {
      throw ApiException.badRequest("missing_field", "Choose the classes for this homework.");
    }
    sections.forEach(this::requireSection);
    Valid v = validate(in, null);
    LocalDate today = LocalDate.now(IST);
    if (v.due.isBefore(today)) {
      throw ApiException.badRequest("invalid_date", "The due date can't be in the past.");
    }
    List<UUID> ids = new ArrayList<>();
    tx.executeWithoutResult(s -> {
      for (UUID sec : sections) {
        UUID subjectId = v.subjectId;
        if (subjectId != null) {
          // The same subject name in each section's own class.
          subjectId =
              jdbc.sql(
                      "select s2.id from subject s1 join class_section c on c.id = ? join subject s2 on s2.academic_year = s1.academic_year"
                          + " and s2.grade = c.grade and s2.name = s1.name where s1.id = ?")
                  .params(sec, v.subjectId)
                  .query(UUID.class)
                  .optional()
                  .orElse(null);
        }
        ids.add(
            jdbc.sql(
                    "insert into homework (organization_id, class_section_id, subject_id, subject_name, title, details, assigned_on, due_on,"
                        + " allow_submissions, max_points, rubric, late_policy, notify_parents, created_by)"
                        + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?) returning id")
                .params(
                    TenantContext.requireOrganizationId(),
                    sec,
                    subjectId,
                    v.subjectName,
                    v.title,
                    v.details,
                    today,
                    v.due,
                    v.allow,
                    v.maxPoints == null ? null : BigDecimal.valueOf(v.maxPoints),
                    write(v.rubric),
                    v.late,
                    v.alertParents,
                    userId())
                .query(UUID.class)
                .single());
      }
      audit.record(
          "homework.posted",
          "homework",
          ids.get(0).toString(),
          null,
          Map.of("title", v.title, "sections", ids.size()));
    });
    if (v.alertParents && feature("homework_alerts") && entitlements.hasModule("COMMS")) {
      for (UUID id : ids) {
        alert(load(id));
      }
    }
    return ids.stream().map(this::load).toList();
  }

  public Homework update(UUID id, HomeworkInput in) {
    requireModule();
    Homework before = load(id);
    requireSection(before.sectionId());
    Valid v = validate(in, before);
    if (before.submitted() > 0 && before.allowSubmissions() && !v.allow) {
      throw ApiException.conflict(
          "has_submissions", "Work has been handed in, so submissions stay on.");
    }
    tx.executeWithoutResult(s -> {
      jdbc.sql(
              "update homework set subject_id = ?, subject_name = ?, title = ?, details = ?, due_on = ?, allow_submissions = ?, max_points = ?,"
                  + " rubric = ?::jsonb, late_policy = ?, updated_at = now() where id = ?")
          .params(
              v.subjectId,
              v.subjectName,
              v.title,
              v.details,
              v.due,
              v.allow,
              v.maxPoints == null ? null : BigDecimal.valueOf(v.maxPoints),
              write(v.rubric),
              v.late,
              id)
          .update();
      audit.record("homework.updated", "homework", id.toString(), null, Map.of("title", v.title));
    });
    return load(id);
  }

  public void delete(UUID id) {
    requireModule();
    Homework h = load(id);
    requireSection(h.sectionId());
    List<String> keys = new ArrayList<>();
    tx.executeWithoutResult(s -> {
      keys.addAll(
          jdbc.sql("select storage_key from homework_file where homework_id = ?")
              .param(id)
              .query(String.class)
              .list());
      keys.addAll(
          jdbc.sql(
                  "select storage_key from homework_submission where homework_id = ? and storage_key is not null")
              .param(id)
              .query(String.class)
              .list());
      jdbc.sql("delete from homework where id = ?").param(id).update();
      audit.record("homework.deleted", "homework", id.toString(), Map.of("title", h.title()), null);
    });
    if (!keys.isEmpty()) {
      media.deletePrivate(keys);
    }
  }

  public Homework addFile(UUID id, String filename, byte[] content) {
    requireModule();
    if (!feature("attachments")) {
      throw ApiException.badRequest(
          "feature_not_in_plan", "Attachments are not part of your plan.");
    }
    Homework h = load(id);
    requireSection(h.sectionId());
    if (h.files().size() >= 5) {
      throw ApiException.badRequest("too_many", "Attach up to 5 files.");
    }
    MediaService.StoredFile f = media.storePrivate("homework", filename, content, MAX_FILE);
    try {
      jdbc.sql(
              "insert into homework_file (organization_id, homework_id, original_filename, content_type, size_bytes, storage_key)"
                  + " values (?, ?, ?, ?, ?, ?)")
          .params(
              TenantContext.requireOrganizationId(),
              id,
              f.filename(),
              f.contentType(),
              f.sizeBytes(),
              f.storageKey())
          .update();
    } catch (RuntimeException e) {
      media.deletePrivate(List.of(f.storageKey()));
      throw e;
    }
    return load(id);
  }

  public Homework removeFile(UUID fileId) {
    requireModule();
    record F(UUID homework, String key) {}
    F f =
        jdbc.sql("select homework_id, storage_key from homework_file where id = ?")
            .param(fileId)
            .query((rs, i) -> new F(rs.getObject(1, UUID.class), rs.getString(2)))
            .optional()
            .orElseThrow(() -> ApiException.notFound("File"));
    requireSection(load(f.homework()).sectionId());
    jdbc.sql("delete from homework_file where id = ?").param(fileId).update();
    media.deletePrivate(List.of(f.key()));
    return load(f.homework());
  }

  /** A short-lived download link for a homework attachment (staff). */
  public String fileUrl(UUID fileId) {
    requireModule();
    record F(UUID homework, String key, String name) {}
    F f =
        jdbc.sql(
                "select homework_id, storage_key, original_filename from homework_file where id = ?")
            .param(fileId)
            .query((rs, i) -> new F(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)))
            .optional()
            .orElseThrow(() -> ApiException.notFound("File"));
    requireSection(load(f.homework()).sectionId());
    return media.signedPrivateUrl(f.key(), f.name());
  }

  // ------------------------------------------------------------------ submissions

  public List<Submission> submissions(UUID id) {
    requireModule();
    Homework h = load(id);
    requireSection(h.sectionId());
    return jdbc.sql(
            "select x.id, s.id sid, s.name, e.roll_no, x.status, x.text, x.file_name, x.submitted_at, x.late, x.points,"
                + " x.rubric_scores::text scores, x.feedback, x.graded_at from student_enrolment e join student s on s.id = e.student_id"
                + " left join homework_submission x on x.homework_id = ? and x.student_id = s.id"
                + " where e.class_section_id = ? and (s.status = 'ENROLLED' or x.id is not null) order by e.roll_no nulls last, s.name")
        .params(id, h.sectionId())
        .query((rs, i) -> mapSubmission(rs))
        .list();
  }

  public Submission grade(UUID submissionId, GradeInput in) {
    requireModule();
    if (!feature("feedback")) {
      throw ApiException.badRequest(
          "feature_not_in_plan", "Marking submissions is part of ShikshaBlox Standard.");
    }
    UUID homeworkId =
        jdbc.sql("select homework_id from homework_submission where id = ?")
            .param(submissionId)
            .query(UUID.class)
            .optional()
            .orElseThrow(() -> ApiException.notFound("Submission"));
    Homework h = load(homeworkId);
    requireSection(h.sectionId());
    Map<String, Double> scores = new LinkedHashMap<>();
    Double points = in.points();
    if (!h.rubric().isEmpty() && in.rubricScores() != null && feature("rubrics")) {
      double total = 0;
      for (Criterion c : h.rubric()) {
        Double v = in.rubricScores().get(c.criterion());
        if (v == null) {
          continue;
        }
        if (v < 0 || v > c.points()) {
          throw ApiException.badRequest(
              "invalid_points", c.criterion() + ": 0 to " + fmt(c.points()) + ".");
        }
        scores.put(c.criterion(), v);
        total += v;
      }
      points = scores.isEmpty() ? points : total;
    }
    double max =
        h.maxPoints() != null
            ? h.maxPoints()
            : h.rubric().stream().mapToDouble(Criterion::points).sum();
    if (points != null && (points < 0 || (max > 0 && points > max))) {
      throw ApiException.badRequest("invalid_points", "Points must be 0 to " + fmt(max) + ".");
    }
    String feedback =
        in.feedback() == null || in.feedback().isBlank() ? null : in.feedback().trim();
    if (feedback != null && feedback.length() > 2000) {
      throw ApiException.badRequest("too_long", "Keep feedback under 2,000 characters.");
    }
    if (in.returnForRedo() && feedback == null) {
      throw ApiException.badRequest("missing_feedback", "Tell the child what to redo.");
    }
    Double finalPoints = points;
    tx.executeWithoutResult(s -> {
      jdbc.sql(
              "update homework_submission set status = ?, points = ?, rubric_scores = ?::jsonb, feedback = ?, graded_by = ?, graded_at = now()"
                  + " where id = ?")
          .params(
              in.returnForRedo() ? "RETURNED" : "GRADED",
              finalPoints == null ? null : BigDecimal.valueOf(finalPoints),
              write(scores),
              feedback,
              userId(),
              submissionId)
          .update();
      audit.record(
          in.returnForRedo() ? "homework.returned" : "homework.graded",
          "homework_submission",
          submissionId.toString(),
          null,
          null);
    });
    return submissions(homeworkId).stream()
        .filter(x -> submissionId.equals(x.id()))
        .findFirst()
        .orElseThrow();
  }

  public String submissionFileUrl(UUID submissionId) {
    requireModule();
    record F(UUID homework, String key, String name) {}
    F f =
        jdbc.sql(
                "select homework_id, storage_key, file_name from homework_submission where id = ? and storage_key is not null")
            .param(submissionId)
            .query((rs, i) -> new F(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)))
            .optional()
            .orElseThrow(() -> ApiException.notFound("File"));
    requireSection(load(f.homework()).sectionId());
    return media.signedPrivateUrl(f.key(), f.name());
  }

  // ------------------------------------------------------------------ parent portal

  /** Homework of the guardian's children: due in the last two weeks or later. */
  public List<ChildHomework> forGuardian(UUID guardianId) {
    if (!entitlements.hasModule(MODULE)) {
      return List.of();
    }
    LocalDate today = LocalDate.now(IST);
    record Row(UUID id, UUID student, String name) {}
    List<Row> rows =
        jdbc.sql(
                "select h.id, s.id, s.name from student_guardian sg join student s on s.id = sg.student_id and s.status = 'ENROLLED'"
                    + " join student_enrolment e on e.student_id = s.id and e.academic_year = (select current_year from sis_settings)"
                    + " join homework h on h.class_section_id = e.class_section_id where sg.guardian_id = ? and h.due_on >= ?"
                    + " order by h.due_on, h.created_at limit 60")
            .params(guardianId, today.minusDays(14))
            .query((rs, i) ->
                new Row(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3)))
            .list();
    boolean subs = feature("submissions");
    List<ChildHomework> out = new ArrayList<>();
    for (Row r : rows) {
      Homework h = load(r.id());
      Submission sub =
          jdbc.sql(
                  "select x.id, s.id sid, s.name, null::int roll_no, x.status, x.text, x.file_name, x.submitted_at, x.late, x.points,"
                      + " x.rubric_scores::text scores, x.feedback, x.graded_at from homework_submission x join student s on s.id = x.student_id"
                      + " where x.homework_id = ? and x.student_id = ?")
              .params(r.id(), r.student())
              .query((rs, i) -> mapSubmission(rs))
              .optional()
              .orElse(null);
      out.add(
          new ChildHomework(
              h.id(),
              r.student(),
              r.name(),
              h.subject(),
              h.title(),
              h.details(),
              h.assignedOn(),
              h.dueOn(),
              h.files(),
              subs && h.allowSubmissions(),
              "CLOSE".equals(h.latePolicy()) && h.dueOn().isBefore(today),
              h.maxPoints(),
              sub));
    }
    return out;
  }

  private void requireGuardianHomework(UUID guardianId, UUID homeworkId, UUID studentId) {
    boolean ok =
        jdbc.sql(
                    "select count(*) from homework h join student_enrolment e on e.class_section_id = h.class_section_id"
                        + " join student_guardian sg on sg.student_id = e.student_id where h.id = ? and sg.guardian_id = ? and e.student_id = ?")
                .params(homeworkId, guardianId, studentId)
                .query(Long.class)
                .single()
            > 0;
    if (!ok) {
      throw ApiException.notFound("Homework");
    }
  }

  /** A download link for an attachment of a child's homework. */
  public String fileUrlForGuardian(UUID guardianId, UUID fileId) {
    record F(UUID homework, String key, String name) {}
    F f =
        jdbc.sql(
                "select homework_id, storage_key, original_filename from homework_file where id = ?")
            .param(fileId)
            .query((rs, i) -> new F(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)))
            .optional()
            .orElseThrow(() -> ApiException.notFound("File"));
    boolean ok =
        jdbc.sql(
                    "select count(*) from homework h join student_enrolment e on e.class_section_id = h.class_section_id"
                        + " join student_guardian sg on sg.student_id = e.student_id where h.id = ? and sg.guardian_id = ?")
                .params(f.homework(), guardianId)
                .query(Long.class)
                .single()
            > 0;
    if (!ok) {
      throw ApiException.notFound("File");
    }
    return media.signedPrivateUrl(f.key(), f.name());
  }

  /** A parent hands in (or re-submits) their child's work: text and/or one file. */
  public Submission submit(
      UUID guardianId,
      UUID homeworkId,
      UUID studentId,
      String text,
      String filename,
      byte[] content) {
    if (!entitlements.hasModule(MODULE) || !feature("submissions")) {
      throw ApiException.notFound("Homework");
    }
    requireGuardianHomework(guardianId, homeworkId, studentId);
    Homework h = load(homeworkId);
    if (!h.allowSubmissions()) {
      throw ApiException.badRequest("no_submissions", "This homework is not handed in online.");
    }
    LocalDate today = LocalDate.now(IST);
    boolean late = today.isAfter(h.dueOn());
    if (late && "CLOSE".equals(h.latePolicy())) {
      throw ApiException.conflict(
          "closed", "The due date has passed, so work can no longer be handed in here.");
    }
    String t = text == null || text.isBlank() ? null : text.trim();
    if (t != null && t.length() > 4000) {
      throw ApiException.badRequest("too_long", "Keep the answer under 4,000 characters.");
    }
    boolean hasFile = content != null && content.length > 0;
    if (t == null && !hasFile) {
      throw ApiException.badRequest("empty", "Write an answer or attach a file.");
    }
    record Prev(UUID id, String status, String key) {}
    Prev prev =
        jdbc.sql(
                "select id, status, storage_key from homework_submission where homework_id = ? and student_id = ?")
            .params(homeworkId, studentId)
            .query(
                (rs, i) -> new Prev(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)))
            .optional()
            .orElse(null);
    if (prev != null && "GRADED".equals(prev.status())) {
      throw ApiException.conflict("graded", "This work has already been marked.");
    }
    MediaService.StoredFile f =
        hasFile
            ? media.storePrivate("homework/submissions", filename, content, MAX_SUBMISSION_FILE)
            : null;
    try {
      tx.executeWithoutResult(s -> {
        jdbc.sql(
                "insert into homework_submission (organization_id, homework_id, student_id, guardian_id, text, file_name, content_type, size_bytes,"
                    + " storage_key, late) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict (homework_id, student_id) do update set"
                    + " guardian_id = excluded.guardian_id, text = excluded.text,"
                    + " file_name = coalesce(excluded.file_name, homework_submission.file_name),"
                    + " content_type = coalesce(excluded.content_type, homework_submission.content_type),"
                    + " size_bytes = coalesce(excluded.size_bytes, homework_submission.size_bytes),"
                    + " storage_key = coalesce(excluded.storage_key, homework_submission.storage_key),"
                    + " submitted_at = now(), late = excluded.late, status = 'SUBMITTED'")
            .params(
                TenantContext.requireOrganizationId(),
                homeworkId,
                studentId,
                guardianId,
                t,
                f == null ? null : f.filename(),
                f == null ? null : f.contentType(),
                f == null ? null : f.sizeBytes(),
                f == null ? null : f.storageKey(),
                late)
            .update();
        audit.record(
            "homework.submitted",
            "homework",
            homeworkId.toString(),
            null,
            Map.of("student", studentId.toString(), "late", late));
      });
    } catch (RuntimeException e) {
      if (f != null) {
        media.deletePrivate(List.of(f.storageKey()));
      }
      throw e;
    }
    if (f != null && prev != null && prev.key() != null) {
      media.deletePrivate(List.of(prev.key()));
    }
    return forGuardian(guardianId).stream()
        .filter(x -> x.id().equals(homeworkId) && x.studentId().equals(studentId))
        .findFirst()
        .map(ChildHomework::submission)
        .orElse(null);
  }

  // ------------------------------------------------------------------ helpers

  private void alert(Homework h) {
    try {
      List<String> phones =
          jdbc.sql(
                  "select distinct g.phone from student_enrolment e join student s on s.id = e.student_id and s.status = 'ENROLLED'"
                      + " join student_guardian sg on sg.student_id = s.id join guardian g on g.id = sg.guardian_id"
                      + " where e.class_section_id = ? and g.phone is not null")
              .param(h.sectionId())
              .query(String.class)
              .list();
      if (phones.isEmpty()) {
        return;
      }
      tx.executeWithoutResult(s ->
          broadcasts.alert(
              "Homework for "
                  + h.section()
                  + (h.subject() == null ? "" : " (" + h.subject() + ")")
                  + ": "
                  + h.title()
                  + ", due "
                  + DAY.format(h.dueOn()),
              (h.details() == null ? "" : h.details() + "\n\n")
                  + "See it in the parent portal on the school website.",
              "homework_posted",
              "Dear {{name}}, {{title}}. {{link}}",
              phones));
    } catch (RuntimeException e) {
      log.warn("Homework alert {} failed: {}", h.id(), e.getMessage());
    }
  }

  private record Valid(
      UUID subjectId,
      String subjectName,
      String title,
      String details,
      LocalDate due,
      boolean allow,
      Double maxPoints,
      List<Criterion> rubric,
      String late,
      boolean alertParents) {}

  private Valid validate(HomeworkInput in, Homework before) {
    String title = req(in.title(), 160, "Title");
    String details = in.details() == null || in.details().isBlank() ? null : in.details().trim();
    if (details != null && details.length() > 4000) {
      throw ApiException.badRequest("too_long", "Keep the details under 4,000 characters.");
    }
    LocalDate due = in.dueOn();
    if (due == null) {
      throw ApiException.badRequest("missing_field", "Choose the due date.");
    }
    LocalDate start = before == null ? LocalDate.now(IST) : before.assignedOn();
    if (due.isBefore(start) || due.isAfter(start.plusDays(90))) {
      throw ApiException.badRequest("invalid_date", "The due date must be within 90 days.");
    }
    String subjectName =
        in.subjectName() == null || in.subjectName().isBlank() ? null : in.subjectName().trim();
    if (subjectName != null && subjectName.length() > 80) {
      throw ApiException.badRequest("too_long", "The subject name is too long.");
    }
    UUID subjectId = in.subjectId();
    if (subjectId != null
        && jdbc.sql("select count(*) from subject where id = ?")
                .param(subjectId)
                .query(Long.class)
                .single()
            == 0) {
      throw ApiException.badRequest("invalid_subject", "Unknown subject.");
    }
    boolean allow = Boolean.TRUE.equals(in.allowSubmissions());
    if (allow && !feature("submissions")) {
      throw ApiException.badRequest(
          "feature_not_in_plan", "Handing in work online is part of ShikshaBlox Standard.");
    }
    Double max = in.maxPoints();
    if (max != null && (max <= 0 || max > 1000)) {
      throw ApiException.badRequest("invalid_points", "Maximum points must be 1 to 1000.");
    }
    List<Criterion> rubric = new ArrayList<>();
    if (in.rubric() != null && !in.rubric().isEmpty()) {
      if (!feature("rubrics")) {
        throw ApiException.badRequest(
            "feature_not_in_plan", "Rubrics are part of ShikshaBlox Standard.");
      }
      Set<String> names = new java.util.HashSet<>();
      for (Criterion c : in.rubric()) {
        String n = c == null || c.criterion() == null ? "" : c.criterion().trim();
        if (n.isEmpty()
            || n.length() > 60
            || !names.add(n)
            || c.points() <= 0
            || c.points() > 100) {
          throw ApiException.badRequest(
              "invalid_rubric", "Each rubric row needs a different name and 1 to 100 points.");
        }
        rubric.add(new Criterion(n, c.points()));
      }
      if (rubric.size() > 8) {
        throw ApiException.badRequest("invalid_rubric", "Use up to 8 rubric rows.");
      }
    }
    String late = in.latePolicy() == null ? "ACCEPT" : in.latePolicy().toUpperCase(Locale.ROOT);
    if (!Set.of("ACCEPT", "CLOSE").contains(late)) {
      throw ApiException.badRequest("invalid_policy", "Choose whether late work is accepted.");
    }
    boolean notify =
        in.notifyParents() == null ? before == null || before.notifyParents() : in.notifyParents();
    return new Valid(subjectId, subjectName, title, details, due, allow, max, rubric, late, notify);
  }

  private Homework map(ResultSet rs) throws SQLException {
    BigDecimal max = rs.getBigDecimal("max_points");
    return new Homework(
        rs.getObject("id", UUID.class),
        rs.getObject("class_section_id", UUID.class),
        rs.getString("grade").trim() + " " + rs.getString("section").trim(),
        rs.getObject("subject_id", UUID.class),
        rs.getString("subject_label"),
        rs.getString("title"),
        rs.getString("details"),
        rs.getObject("assigned_on", LocalDate.class),
        rs.getObject("due_on", LocalDate.class),
        rs.getBoolean("allow_submissions"),
        max == null ? null : max.doubleValue(),
        readRubric(rs.getString("rubric")),
        rs.getString("late_policy"),
        rs.getBoolean("notify_parents"),
        rs.getString("created_by_name"),
        List.of(),
        rs.getInt("students"),
        rs.getInt("submitted"),
        rs.getInt("graded"));
  }

  private Submission mapSubmission(ResultSet rs) throws SQLException {
    BigDecimal pts = rs.getBigDecimal("points");
    return new Submission(
        rs.getObject("id", UUID.class),
        rs.getObject("sid", UUID.class),
        rs.getString("name"),
        (Integer) rs.getObject("roll_no"),
        rs.getObject("id") == null ? "NOT_SUBMITTED" : rs.getString("status"),
        rs.getString("text"),
        rs.getString("file_name"),
        rs.getTimestamp("submitted_at") == null
            ? null
            : rs.getTimestamp("submitted_at").toInstant(),
        rs.getBoolean("late"),
        pts == null ? null : pts.doubleValue(),
        readScores(rs.getString("scores")),
        rs.getString("feedback"),
        rs.getTimestamp("graded_at") == null ? null : rs.getTimestamp("graded_at").toInstant());
  }

  private List<Criterion> readRubric(String s) {
    try {
      return s == null ? List.of() : json.readValue(s, RUBRIC);
    } catch (Exception e) {
      return List.of();
    }
  }

  private Map<String, Double> readScores(String s) {
    try {
      return s == null ? Map.of() : json.readValue(s, SCORES);
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

  static String fmt(double v) {
    return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
  }
}
