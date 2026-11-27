package com.shikshablox.modules.sis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shikshablox.modules.comms.CommsService;
import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.entitlement.EntitlementService;
import com.shikshablox.platform.entitlement.UsageMeter;
import com.shikshablox.platform.files.MediaService;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.TenantContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Students and their records: classes and sections per year, yearly enrolments, guardians (shared
 * by siblings), documents, APAAR tracking and custom fields. Guardian changes are kept in step with
 * the communication hub's contacts (class tags), without touching opt-ins.
 */
@Service
public class SisService implements UsageMeter {

  static final String MODULE = "SIS";
  public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
  static final Set<String> GENDERS = Set.of("FEMALE", "MALE", "OTHER");
  static final Set<String> BLOOD = Set.of("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-");
  static final Set<String> RELATIONS =
      Set.of("MOTHER", "FATHER", "GUARDIAN", "GRANDPARENT", "OTHER");
  static final Set<String> APAAR =
      Set.of(
          "NOT_STARTED",
          "CONSENT_PENDING",
          "CONSENT_GIVEN",
          "CONSENT_REFUSED",
          "GENERATED",
          "EXCEPTION");
  static final Set<String> FIELD_TYPES = Set.of("TEXT", "NUMBER", "DATE", "CHOICE", "YES_NO");
  private static final Pattern PHONE = Pattern.compile("^[+0-9 \\-]{8,20}$");
  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
  private static final Pattern YEAR = Pattern.compile("^\\d{4}-\\d{2}$");

  // ------------------------------------------------------------------ records

  public record Section(
      UUID id,
      String academicYear,
      String grade,
      String section,
      String label,
      String classTeacher,
      int gradeOrder,
      long students) {}

  public record GradeInput(String grade, List<String> sections) {}

  public record StudentRow(
      UUID id,
      String admissionNo,
      String name,
      String status,
      LocalDate dateOfBirth,
      String gender,
      UUID sectionId,
      String sectionLabel,
      Integer rollNo,
      String guardianName,
      String guardianPhone,
      boolean medicalAlert,
      boolean custodyNote) {}

  public record Student(
      UUID id,
      String admissionNo,
      String name,
      LocalDate dateOfBirth,
      String gender,
      String bloodGroup,
      String status,
      LocalDate admissionDate,
      LocalDate leavingDate,
      String leavingReason,
      String previousSchool,
      String address,
      String house,
      String medicalAlerts,
      String custodyNote,
      String apaarId,
      String apaarStatus,
      String apaarNote,
      Map<String, Object> custom,
      UUID leadId,
      Instant createdAt) {}

  public record Enrolment(
      UUID id,
      String academicYear,
      UUID sectionId,
      String sectionLabel,
      Integer rollNo,
      String outcome) {}

  public record GuardianLink(
      UUID guardianId,
      String name,
      String phone,
      String email,
      String occupation,
      String language,
      String relation,
      boolean primary,
      boolean canPickup,
      boolean emergency,
      List<String> siblings) {}

  public record Document(
      UUID id, String title, String filename, long sizeBytes, Instant createdAt) {}

  public record Field(
      UUID id, String key, String label, String type, List<String> options, int sortOrder) {}

  public record StudentDetail(
      Student student,
      List<Enrolment> enrolments,
      List<GuardianLink> guardians,
      List<Document> documents,
      List<Field> fields) {}

  public record StudentInput(
      String admissionNo,
      String name,
      LocalDate dateOfBirth,
      String gender,
      String bloodGroup,
      LocalDate admissionDate,
      String previousSchool,
      String address,
      String house,
      String medicalAlerts,
      String custodyNote,
      String apaarId,
      String apaarStatus,
      String apaarNote,
      Map<String, Object> custom,
      UUID sectionId,
      Integer rollNo,
      List<GuardianInput> guardians) {}

  public record GuardianInput(
      UUID guardianId,
      String name,
      String phone,
      String email,
      String occupation,
      String language,
      String relation,
      Boolean primary,
      Boolean canPickup,
      Boolean emergency) {}

  public record FieldInput(String key, String label, String type, List<String> options) {}

  public record Settings(String currentYear, String admissionPrefix, int nextAdmissionNo) {}

  public record Readiness(
      long enrolled,
      long missingDob,
      long missingGender,
      long missingAddress,
      long missingGuardianPhone,
      long apaarGenerated,
      long apaarConsentPending,
      long apaarNotStarted,
      long withoutSection) {}

  public record ImportResult(int added, int updated, int skipped, List<String> errors) {}

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final AuditService audit;
  private final EntitlementService entitlements;
  private final MediaService media;
  private final CommsService comms;
  private final ObjectMapper json;

  SisService(
      JdbcClient jdbc,
      TransactionTemplate tx,
      AuditService audit,
      @org.springframework.context.annotation.Lazy EntitlementService entitlements,
      MediaService media,
      CommsService comms,
      ObjectMapper json) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.audit = audit;
    this.entitlements = entitlements;
    this.media = media;
    this.comms = comms;
    this.json = json;
  }

  /** The school year in progress: April–March (Nov 2026 → 2026-27). */
  public static String defaultYear(LocalDate today) {
    int start = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
    return start + "-" + String.format("%02d", (start + 1) % 100);
  }

  static String nextYear(String year) {
    int start = Integer.parseInt(year.substring(0, 4)) + 1;
    return start + "-" + String.format("%02d", (start + 1) % 100);
  }

  /**
   * Label shown everywhere and used as the communication hub tag, e.g. "Class 3 A", "Class LKG B".
   */
  /** Public form of label() for other modules and demo data. */
  public static String labelOf(String grade, String section) {
    return label(grade, section);
  }

  static String label(String grade, String section) {
    return grade.trim() + " " + section.trim();
  }

  // ------------------------------------------------------------------ settings

  public Settings settings() {
    entitlements.requireModule(MODULE);
    return loadSettings();
  }

  Settings loadSettings() {
    jdbc.sql(
            "insert into sis_settings (organization_id, current_year) values (?, ?) on conflict do nothing")
        .params(TenantContext.requireOrganizationId(), defaultYear(LocalDate.now(IST)))
        .update();
    return jdbc.sql("select current_year, admission_prefix, next_admission_no from sis_settings")
        .query((rs, i) -> new Settings(rs.getString(1), rs.getString(2), rs.getInt(3)))
        .single();
  }

  public Settings saveSettings(String prefix, Integer nextNo) {
    entitlements.requireModule(MODULE);
    Settings before = loadSettings();
    String p = prefix == null ? before.admissionPrefix() : prefix.trim();
    if (p.length() > 10 || !p.matches("^[A-Za-z0-9/-]*$")) {
      throw ApiException.badRequest(
          "invalid_prefix", "The prefix may use letters, digits, / and - (up to 10).");
    }
    int n = nextNo == null ? before.nextAdmissionNo() : nextNo;
    if (n < 1 || n > 9_999_999) {
      throw ApiException.badRequest(
          "invalid_number", "The next admission number must be 1 to 9,999,999.");
    }
    tx.executeWithoutResult(s -> {
      jdbc.sql(
              "update sis_settings set admission_prefix = ?, next_admission_no = ?, updated_at = now()")
          .params(p, n)
          .update();
      audit.record(
          "sis.settings_saved", "sis_settings", null, null, Map.of("prefix", p, "next", n));
    });
    return loadSettings();
  }

  // ------------------------------------------------------------------ classes

  public List<Section> sections(String year) {
    entitlements.requireModule(MODULE);
    String y = year == null || year.isBlank() ? loadSettings().currentYear() : year(year);
    return jdbc.sql(
            "select c.*, (select count(*) from student_enrolment e join student s on s.id = e.student_id"
                + "   where e.class_section_id = c.id and (s.status = 'ENROLLED' or e.outcome is not null)) students"
                + " from class_section c where c.academic_year = ? order by c.grade_order, c.grade, c.section")
        .param(y)
        .query(SisService::mapSection)
        .list();
  }

  /** Adds grades and sections for a year (existing ones are kept); order follows the list. */
  public List<Section> addSections(String year, List<GradeInput> grades) {
    entitlements.requireModule(MODULE);
    String y = year(year);
    if (grades == null || grades.isEmpty() || grades.size() > 30) {
      throw ApiException.badRequest("invalid_grades", "Add 1 to 30 classes.");
    }
    tx.executeWithoutResult(s -> {
      int order =
          jdbc.sql(
                  "select coalesce(max(grade_order), -1) + 1 from class_section where academic_year = ?")
              .param(y)
              .query(Integer.class)
              .single();
      for (GradeInput g : grades) {
        String grade = required(g.grade(), 40, "Class");
        Integer existingOrder =
            jdbc.sql(
                    "select coalesce(min(grade_order), -1) from class_section where academic_year = ? and grade = ?")
                .params(y, grade)
                .query(Integer.class)
                .single();
        int go = existingOrder >= 0 ? existingOrder : order++;
        List<String> secs =
            g.sections() == null || g.sections().isEmpty() ? List.of("A") : g.sections();
        if (secs.size() > 15) {
          throw ApiException.badRequest("too_many", "Up to 15 sections per class.");
        }
        for (String sec : secs) {
          jdbc.sql(
                  "insert into class_section (organization_id, academic_year, grade, section, grade_order) values (?, ?, ?, ?, ?)"
                      + " on conflict (organization_id, academic_year, grade, section) do nothing")
              .params(
                  TenantContext.requireOrganizationId(),
                  y,
                  grade,
                  required(sec, 10, "Section").toUpperCase(Locale.ROOT),
                  go)
              .update();
        }
      }
      audit.record(
          "sis.sections_added", "class_section", y, null, Map.of("classes", grades.size()));
    });
    return sections(y);
  }

  public Section updateSection(UUID id, String classTeacher) {
    entitlements.requireModule(MODULE);
    String t = optional(classTeacher, 120, "Class teacher");
    if (jdbc.sql("update class_section set class_teacher = ? where id = ?").params(t, id).update()
        == 0) {
      throw ApiException.notFound("Section");
    }
    return section(id);
  }

  public void deleteSection(UUID id) {
    entitlements.requireModule(MODULE);
    tx.executeWithoutResult(s -> {
      long used =
          jdbc.sql("select count(*) from student_enrolment where class_section_id = ?")
              .param(id)
              .query(Long.class)
              .single();
      if (used > 0) {
        throw ApiException.conflict("in_use", "This section has students. Move them first.");
      }
      if (jdbc.sql("delete from class_section where id = ?").param(id).update() == 0) {
        throw ApiException.notFound("Section");
      }
      audit.record("sis.section_deleted", "class_section", id.toString(), null, null);
    });
  }

  Section section(UUID id) {
    return jdbc.sql(
            "select c.*, (select count(*) from student_enrolment e where e.class_section_id = c.id) students"
                + " from class_section c where c.id = ?")
        .param(id)
        .query(SisService::mapSection)
        .optional()
        .orElseThrow(() -> ApiException.notFound("Section"));
  }

  // ------------------------------------------------------------------ students

  public List<StudentRow> students(String year, UUID sectionId, String status, String q) {
    entitlements.requireModule(MODULE);
    String y = year == null || year.isBlank() ? loadSettings().currentYear() : year(year);
    StringBuilder sql =
        new StringBuilder(
            "select s.id, s.admission_no, s.name, s.status, s.date_of_birth, s.gender, e.class_section_id,"
                + " c.grade, c.section, e.roll_no, s.medical_alerts is not null, s.custody_note is not null,"
                + " (select g.name from student_guardian sg join guardian g on g.id = sg.guardian_id where sg.student_id = s.id"
                + "   order by sg.is_primary desc, g.name limit 1),"
                + " (select g.phone from student_guardian sg join guardian g on g.id = sg.guardian_id where sg.student_id = s.id"
                + "   order by sg.is_primary desc, g.name limit 1)"
                + " from student s left join student_enrolment e on e.student_id = s.id and e.academic_year = ?"
                + " left join class_section c on c.id = e.class_section_id where true");
    List<Object> p = new ArrayList<>();
    p.add(y);
    if (sectionId != null) {
      sql.append(" and e.class_section_id = ?");
      p.add(sectionId);
    }
    if (status == null || status.isBlank()) {
      sql.append(" and s.status = 'ENROLLED'");
    } else if (!"ALL".equals(status)) {
      sql.append(" and s.status = ?");
      p.add(oneOf(status, Set.of("ENROLLED", "WITHDRAWN", "ALUMNI"), "status"));
    }
    if (q != null && !q.isBlank()) {
      String like = "%" + q.trim().replace("%", "") + "%";
      sql.append(
          " and (s.name ilike ? or s.admission_no ilike ? or exists (select 1 from student_guardian sg join guardian g"
              + " on g.id = sg.guardian_id where sg.student_id = s.id and (g.name ilike ? or coalesce(g.phone, '') ilike ?)))");
      p.add(like);
      p.add(like);
      p.add(like);
      p.add(like);
    }
    sql.append(
        " order by c.grade_order nulls last, c.grade, c.section, e.roll_no nulls last, s.name limit 5000");
    return jdbc.sql(sql.toString())
        .params(p)
        .query((rs, i) ->
            new StudentRow(
                rs.getObject(1, UUID.class),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getObject(5, LocalDate.class),
                rs.getString(6),
                rs.getObject(7, UUID.class),
                rs.getString(8) == null ? null : label(rs.getString(8), rs.getString(9)),
                (Integer) rs.getObject(10),
                rs.getString(13),
                rs.getString(14),
                rs.getBoolean(11),
                rs.getBoolean(12)))
        .list();
  }

  public StudentDetail student(UUID id) {
    entitlements.requireModule(MODULE);
    Student s = loadStudent(id);
    List<Enrolment> enr =
        jdbc.sql(
                "select e.id, e.academic_year, e.class_section_id, c.grade, c.section, e.roll_no, e.outcome from student_enrolment e"
                    + " join class_section c on c.id = e.class_section_id where e.student_id = ? order by e.academic_year desc")
            .param(id)
            .query((rs, i) ->
                new Enrolment(
                    rs.getObject(1, UUID.class),
                    rs.getString(2),
                    rs.getObject(3, UUID.class),
                    label(rs.getString(4), rs.getString(5)),
                    (Integer) rs.getObject(6),
                    rs.getString(7)))
            .list();
    List<Document> docs =
        jdbc.sql(
                "select id, title, original_filename, size_bytes, created_at from student_document where student_id = ? order by created_at desc")
            .param(id)
            .query((rs, i) ->
                new Document(
                    rs.getObject(1, UUID.class),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getLong(4),
                    rs.getTimestamp(5).toInstant()))
            .list();
    return new StudentDetail(s, enr, guardians(id), docs, fields());
  }

  public StudentDetail create(StudentInput in) {
    entitlements.requireModule(MODULE);
    UUID id = tx.execute(s -> insertStudent(in, null));
    syncContacts(id);
    return student(id);
  }

  /** Creates the student, their first enrolment and guardians (call inside a transaction). */
  UUID insertStudent(StudentInput in, UUID leadId) {
    Settings st = loadSettings();
    Clean c = clean(in, null);
    if (in.sectionId() == null) {
      throw ApiException.badRequest("missing_section", "Choose the class and section.");
    }
    Section sec = section(in.sectionId());
    if (!sec.academicYear().equals(st.currentYear())) {
      throw ApiException.badRequest("wrong_year", "Choose a section of " + st.currentYear() + ".");
    }
    String admNo = c.admissionNo != null ? c.admissionNo : nextAdmissionNo();
    UUID sid;
    try {
      sid =
          jdbc.sql(
                  "insert into student (organization_id, admission_no, name, date_of_birth, gender, blood_group, admission_date,"
                      + " previous_school, address, house, medical_alerts, custody_note, apaar_id, apaar_status, apaar_note, custom, lead_id)"
                      + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?) returning id")
              .params(
                  TenantContext.requireOrganizationId(),
                  admNo,
                  c.name,
                  c.dob,
                  c.gender,
                  c.blood,
                  c.admissionDate == null ? LocalDate.now(IST) : c.admissionDate,
                  c.previousSchool,
                  c.address,
                  c.house,
                  c.medical,
                  c.custody,
                  c.apaarId,
                  c.apaarStatus,
                  c.apaarNote,
                  c.custom,
                  leadId)
              .query(UUID.class)
              .single();
    } catch (DuplicateKeyException e) {
      throw ApiException.conflict(
          "duplicate", "Admission number or APAAR ID already used by another student.");
    }
    enrol(sid, st.currentYear(), sec.id(), in.rollNo());
    if (in.guardians() != null) {
      for (GuardianInput g : in.guardians()) {
        linkGuardian(sid, g);
      }
    }
    audit.record(
        "sis.student_created", "student", sid.toString(), null, Map.of("admissionNo", admNo));
    return sid;
  }

  public StudentDetail update(UUID id, StudentInput in) {
    entitlements.requireModule(MODULE);
    tx.executeWithoutResult(s -> {
      Student before = loadStudent(id);
      Clean c = clean(in, before);
      try {
        jdbc.sql(
                "update student set admission_no = ?, name = ?, date_of_birth = ?, gender = ?, blood_group = ?, admission_date = ?,"
                    + " previous_school = ?, address = ?, house = ?, medical_alerts = ?, custody_note = ?, apaar_id = ?,"
                    + " apaar_status = ?, apaar_note = ?, custom = ?::jsonb, updated_at = now() where id = ?")
            .params(
                c.admissionNo != null ? c.admissionNo : before.admissionNo(),
                c.name,
                c.dob,
                c.gender,
                c.blood,
                c.admissionDate != null ? c.admissionDate : before.admissionDate(),
                c.previousSchool,
                c.address,
                c.house,
                c.medical,
                c.custody,
                c.apaarId,
                c.apaarStatus,
                c.apaarNote,
                c.custom,
                id)
            .update();
      } catch (DuplicateKeyException e) {
        throw ApiException.conflict(
            "duplicate", "Admission number or APAAR ID already used by another student.");
      }
      audit.record(
          "sis.student_updated",
          "student",
          id.toString(),
          Map.of("apaar", before.apaarStatus()),
          Map.of("apaar", c.apaarStatus));
    });
    syncContacts(id);
    return student(id);
  }

  /** Moves the student to another section of the current year (or sets their roll number). */
  public StudentDetail moveSection(UUID id, UUID sectionId, Integer rollNo) {
    entitlements.requireModule(MODULE);
    String y = loadSettings().currentYear();
    tx.executeWithoutResult(s -> {
      Student st = loadStudent(id);
      if (!"ENROLLED".equals(st.status())) {
        throw ApiException.conflict("not_enrolled", "Only enrolled students can be moved.");
      }
      Section sec = section(sectionId);
      if (!sec.academicYear().equals(y)) {
        throw ApiException.badRequest("wrong_year", "Choose a section of " + y + ".");
      }
      enrol(id, y, sectionId, rollNo);
      audit.record(
          "sis.student_moved", "student", id.toString(), null, Map.of("section", sec.label()));
    });
    syncContacts(id);
    return student(id);
  }

  public StudentDetail withdraw(UUID id, LocalDate date, String reason, boolean alumni) {
    entitlements.requireModule(MODULE);
    String why = required(reason, 300, "Reason");
    LocalDate on = date == null ? LocalDate.now(IST) : date;
    String y = loadSettings().currentYear();
    tx.executeWithoutResult(s -> {
      Student st = loadStudent(id);
      if (!"ENROLLED".equals(st.status())) {
        throw ApiException.conflict("not_enrolled", "This student is not enrolled.");
      }
      jdbc.sql(
              "update student set status = ?, leaving_date = ?, leaving_reason = ?, updated_at = now() where id = ?")
          .params(alumni ? "ALUMNI" : "WITHDRAWN", on, why, id)
          .update();
      jdbc.sql(
              "update student_enrolment set outcome = ? where student_id = ? and academic_year = ?")
          .params(alumni ? "GRADUATED" : "LEFT", id, y)
          .update();
      audit.record("sis.student_withdrawn", "student", id.toString(), null, Map.of("reason", why));
    });
    syncContacts(id);
    return student(id);
  }

  /** Re-admits a withdrawn student into a section of the current year. */
  public StudentDetail reinstate(UUID id, UUID sectionId) {
    entitlements.requireModule(MODULE);
    String y = loadSettings().currentYear();
    tx.executeWithoutResult(s -> {
      Student st = loadStudent(id);
      if ("ENROLLED".equals(st.status())) {
        return;
      }
      Section sec = section(sectionId);
      if (!sec.academicYear().equals(y)) {
        throw ApiException.badRequest("wrong_year", "Choose a section of " + y + ".");
      }
      jdbc.sql(
              "update student set status = 'ENROLLED', leaving_date = null, leaving_reason = null, updated_at = now() where id = ?")
          .param(id)
          .update();
      jdbc.sql("delete from student_enrolment where student_id = ? and academic_year = ?")
          .params(id, y)
          .update();
      enrol(id, y, sectionId, null);
      audit.record("sis.student_reinstated", "student", id.toString(), null, null);
    });
    syncContacts(id);
    return student(id);
  }

  /** Deletes a student created by mistake (only while they have at most one year of records). */
  public void delete(UUID id) {
    entitlements.requireModule(MODULE);
    List<String> keys =
        tx.execute(s -> {
          Student st = loadStudent(id);
          long years =
              jdbc.sql("select count(*) from student_enrolment where student_id = ?")
                  .param(id)
                  .query(Long.class)
                  .single();
          if (years > 1) {
            throw ApiException.conflict(
                "has_history",
                "This student has records for several years. Withdraw them instead.");
          }
          long certificates =
              jdbc.sql(
                      "select count(*) from certificate where student_id = ? and status = 'ISSUED'")
                  .param(id)
                  .query(Long.class)
                  .single();
          if (certificates > 0) {
            throw ApiException.conflict(
                "has_certificates",
                "Certificates have been issued to this student. Withdraw them instead, or cancel the certificates first.");
          }
          List<String> k =
              jdbc.sql("select storage_key from student_document where student_id = ?")
                  .param(id)
                  .query(String.class)
                  .list();
          jdbc.sql("delete from student where id = ?").param(id).update();
          jdbc.sql(
                  "delete from guardian g where not exists (select 1 from student_guardian sg where sg.guardian_id = g.id)")
              .update();
          audit.record(
              "sis.student_deleted",
              "student",
              id.toString(),
              Map.of("admissionNo", st.admissionNo()),
              null);
          return k;
        });
    if (!keys.isEmpty()) {
      media.deletePrivate(keys);
    }
  }

  // ------------------------------------------------------------------ guardians

  public StudentDetail addGuardian(UUID studentId, GuardianInput in) {
    entitlements.requireModule(MODULE);
    tx.executeWithoutResult(s -> {
      loadStudent(studentId);
      linkGuardian(studentId, in);
      audit.record("sis.guardian_linked", "student", studentId.toString(), null, null);
    });
    syncContacts(studentId);
    return student(studentId);
  }

  /** Edits the guardian (shared by siblings) and their link to this student. */
  public StudentDetail updateGuardian(UUID studentId, UUID guardianId, GuardianInput in) {
    entitlements.requireModule(MODULE);
    tx.executeWithoutResult(s -> {
      GuardianLink before =
          guardians(studentId).stream()
              .filter(g -> g.guardianId().equals(guardianId))
              .findFirst()
              .orElseThrow(() -> ApiException.notFound("Guardian"));
      try {
        jdbc.sql(
                "update guardian set name = ?, phone = ?, email = ?, occupation = ?, language = ?, updated_at = now() where id = ?")
            .params(
                in.name() == null ? before.name() : required(in.name(), 120, "Guardian name"),
                in.phone() == null ? before.phone() : phone(in.phone()),
                in.email() == null ? before.email() : email(in.email()),
                in.occupation() == null
                    ? before.occupation()
                    : optional(in.occupation(), 120, "Occupation"),
                in.language() == null ? before.language() : language(in.language()),
                guardianId)
            .update();
      } catch (DuplicateKeyException e) {
        throw ApiException.conflict(
            "duplicate_phone", "Another guardian already has this phone number.");
      }
      boolean primary = in.primary() == null ? before.primary() : in.primary();
      if (primary) {
        jdbc.sql("update student_guardian set is_primary = false where student_id = ?")
            .param(studentId)
            .update();
      }
      jdbc.sql(
              "update student_guardian set relation = ?, is_primary = ?, can_pickup = ?, emergency = ? where student_id = ? and guardian_id = ?")
          .params(
              in.relation() == null
                  ? before.relation()
                  : oneOf(in.relation(), RELATIONS, "relation"),
              primary,
              in.canPickup() == null ? before.canPickup() : in.canPickup(),
              in.emergency() == null ? before.emergency() : in.emergency(),
              studentId,
              guardianId)
          .update();
      audit.record("sis.guardian_updated", "guardian", guardianId.toString(), null, null);
    });
    syncContacts(studentId);
    return student(studentId);
  }

  public StudentDetail removeGuardian(UUID studentId, UUID guardianId) {
    entitlements.requireModule(MODULE);
    tx.executeWithoutResult(s -> {
      if (jdbc.sql("delete from student_guardian where student_id = ? and guardian_id = ?")
              .params(studentId, guardianId)
              .update()
          == 0) {
        throw ApiException.notFound("Guardian");
      }
      jdbc.sql(
              "delete from guardian where id = ? and not exists (select 1 from student_guardian where guardian_id = ?)")
          .params(guardianId, guardianId)
          .update();
      audit.record("sis.guardian_unlinked", "student", studentId.toString(), null, null);
    });
    return student(studentId);
  }

  private void linkGuardian(UUID studentId, GuardianInput in) {
    UUID gid = in.guardianId();
    if (gid == null) {
      String phone = in.phone() == null || in.phone().isBlank() ? null : phone(in.phone());
      gid =
          phone == null
              ? null
              : jdbc.sql(
                      "select id from guardian where right(regexp_replace(phone, '\\D', '', 'g'), 10) = ?")
                  .param(key(phone))
                  .query(UUID.class)
                  .optional()
                  .orElse(null);
      if (gid == null) {
        gid =
            jdbc.sql(
                    "insert into guardian (organization_id, name, phone, email, occupation, language) values (?, ?, ?, ?, ?, ?) returning id")
                .params(
                    TenantContext.requireOrganizationId(),
                    required(in.name(), 120, "Guardian name"),
                    phone,
                    email(in.email()),
                    optional(in.occupation(), 120, "Occupation"),
                    language(in.language()))
                .query(UUID.class)
                .single();
      }
    }
    boolean first =
        jdbc.sql("select count(*) from student_guardian where student_id = ?")
                .param(studentId)
                .query(Long.class)
                .single()
            == 0;
    boolean primary = in.primary() != null ? in.primary() : first;
    if (primary) {
      jdbc.sql("update student_guardian set is_primary = false where student_id = ?")
          .param(studentId)
          .update();
    }
    jdbc.sql(
            "insert into student_guardian (organization_id, student_id, guardian_id, relation, is_primary, can_pickup, emergency)"
                + " values (?, ?, ?, ?, ?, ?, ?) on conflict (student_id, guardian_id) do update set relation = excluded.relation,"
                + " is_primary = excluded.is_primary, can_pickup = excluded.can_pickup, emergency = excluded.emergency")
        .params(
            TenantContext.requireOrganizationId(),
            studentId,
            gid,
            in.relation() == null ? "GUARDIAN" : oneOf(in.relation(), RELATIONS, "relation"),
            primary,
            in.canPickup() == null || in.canPickup(),
            in.emergency() == null || in.emergency())
        .update();
  }

  List<GuardianLink> guardians(UUID studentId) {
    return jdbc.sql(
            "select g.id, g.name, g.phone, g.email, g.occupation, g.language, sg.relation, sg.is_primary, sg.can_pickup, sg.emergency,"
                + " array(select s2.name from student_guardian sg2 join student s2 on s2.id = sg2.student_id"
                + "   where sg2.guardian_id = g.id and s2.id <> sg.student_id order by s2.name)"
                + " from student_guardian sg join guardian g on g.id = sg.guardian_id where sg.student_id = ?"
                + " order by sg.is_primary desc, g.name")
        .param(studentId)
        .query((rs, i) ->
            new GuardianLink(
                rs.getObject(1, UUID.class),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5),
                rs.getString(6),
                rs.getString(7),
                rs.getBoolean(8),
                rs.getBoolean(9),
                rs.getBoolean(10),
                List.of((String[]) rs.getArray(11).getArray())))
        .list();
  }

  /** Updates the communication hub contacts of the student's guardians (class tags, children). */
  void syncContacts(UUID studentId) {
    String y = loadSettings().currentYear();
    for (GuardianLink g : guardians(studentId)) {
      record Kid(String name, String label) {}
      List<Kid> kids =
          jdbc.sql(
                  "select s.name, c.grade, c.section from student_guardian sg join student s on s.id = sg.student_id"
                      + " left join student_enrolment e on e.student_id = s.id and e.academic_year = ?"
                      + " left join class_section c on c.id = e.class_section_id"
                      + " where sg.guardian_id = ? and s.status = 'ENROLLED' order by s.name")
              .params(y, g.guardianId())
              .query((rs, i) ->
                  new Kid(
                      rs.getString(1),
                      rs.getString(2) == null ? null : label(rs.getString(2), rs.getString(3))))
              .list();
      List<String> tags =
          kids.stream().map(Kid::label).filter(java.util.Objects::nonNull).distinct().toList();
      String names =
          String.join(
              ", ",
              kids.stream()
                  .map(k -> k.name() + (k.label() == null ? "" : " (" + k.label() + ")"))
                  .toList());
      comms.syncGuardian(
          g.name(),
          g.phone(),
          g.email(),
          g.language(),
          tags,
          allClassLabels(),
          names.isEmpty() ? null : names);
    }
  }

  /** Every class label the school has used (any year), so stale class tags can be replaced. */
  private List<String> allClassLabels() {
    return jdbc.sql("select distinct grade, section from class_section")
        .query((rs, i) -> label(rs.getString(1), rs.getString(2)))
        .list();
  }

  // ------------------------------------------------------------------ documents & fields

  public StudentDetail uploadDocument(
      UUID studentId, String title, String filename, byte[] content) {
    entitlements.requireFeature(MODULE, "documents");
    String t = required(title, 120, "Title");
    loadStudent(studentId);
    MediaService.StoredFile f =
        media.storePrivate("students/documents", filename, content, 10L * 1024 * 1024);
    try {
      tx.executeWithoutResult(s -> {
        jdbc.sql(
                "insert into student_document (organization_id, student_id, title, original_filename, content_type, size_bytes,"
                    + " storage_key, uploaded_by) values (?, ?, ?, ?, ?, ?, ?, ?)")
            .params(
                TenantContext.requireOrganizationId(),
                studentId,
                t,
                f.filename(),
                f.contentType(),
                f.sizeBytes(),
                f.storageKey(),
                TenantContext.userId().orElse(null))
            .update();
        audit.record(
            "sis.document_uploaded", "student", studentId.toString(), null, Map.of("title", t));
      });
    } catch (RuntimeException e) {
      media.deletePrivate(List.of(f.storageKey()));
      throw e;
    }
    return student(studentId);
  }

  public String documentUrl(UUID documentId) {
    entitlements.requireFeature(MODULE, "documents");
    record K(String key, String name) {}
    K k =
        jdbc.sql("select storage_key, original_filename from student_document where id = ?")
            .param(documentId)
            .query((rs, i) -> new K(rs.getString(1), rs.getString(2)))
            .optional()
            .orElseThrow(() -> ApiException.notFound("Document"));
    audit.record("sis.document_viewed", "student_document", documentId.toString(), null, null);
    return media.signedPrivateUrl(k.key(), k.name());
  }

  public void deleteDocument(UUID documentId) {
    entitlements.requireFeature(MODULE, "documents");
    String key =
        tx.execute(s -> {
          String k =
              jdbc.sql("select storage_key from student_document where id = ?")
                  .param(documentId)
                  .query(String.class)
                  .optional()
                  .orElseThrow(() -> ApiException.notFound("Document"));
          jdbc.sql("delete from student_document where id = ?").param(documentId).update();
          audit.record(
              "sis.document_deleted", "student_document", documentId.toString(), null, null);
          return k;
        });
    media.deletePrivate(List.of(key));
  }

  public List<Field> fields() {
    return jdbc.sql("select * from student_field order by sort_order, label")
        .query((rs, i) ->
            new Field(
                rs.getObject("id", UUID.class),
                rs.getString("key"),
                rs.getString("label"),
                rs.getString("type"),
                List.of((String[]) rs.getArray("options").getArray()),
                rs.getInt("sort_order")))
        .list();
  }

  public List<Field> addField(FieldInput in) {
    entitlements.requireFeature(MODULE, "custom_fields");
    entitlements.checkCanAdd(MODULE, "custom_fields", 1, "more custom fields");
    String label = required(in.label(), 80, "Label");
    String key = in.key() == null || in.key().isBlank() ? keyOf(label) : in.key();
    if (!key.matches("^[a-z][a-z0-9_]{1,39}$")) {
      throw ApiException.badRequest(
          "invalid_key", "Field keys use lowercase letters, digits and _.");
    }
    String type = in.type() == null ? "TEXT" : oneOf(in.type(), FIELD_TYPES, "type");
    List<String> options =
        "CHOICE".equals(type)
            ? (in.options() == null ? List.<String>of() : in.options())
                .stream()
                    .filter(o -> o != null && !o.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList()
            : List.of();
    if ("CHOICE".equals(type) && (options.size() < 2 || options.size() > 20)) {
      throw ApiException.badRequest("invalid_options", "Give 2 to 20 options.");
    }
    try {
      tx.executeWithoutResult(s -> {
        int order =
            jdbc.sql("select coalesce(max(sort_order), -1) + 1 from student_field")
                .query(Integer.class)
                .single();
        jdbc.sql(
                "insert into student_field (organization_id, key, label, type, options, sort_order) values (?, ?, ?, ?, ?, ?)")
            .params(
                TenantContext.requireOrganizationId(),
                key,
                label,
                type,
                options.toArray(String[]::new),
                order)
            .update();
        audit.record("sis.field_added", "student_field", key, null, Map.of("label", label));
      });
    } catch (DuplicateKeyException e) {
      throw ApiException.conflict("duplicate_field", "A field with this key exists.");
    }
    return fields();
  }

  public List<Field> deleteField(UUID id) {
    entitlements.requireFeature(MODULE, "custom_fields");
    tx.executeWithoutResult(s -> {
      if (jdbc.sql("delete from student_field where id = ?").param(id).update() == 0) {
        throw ApiException.notFound("Field");
      }
      audit.record("sis.field_deleted", "student_field", id.toString(), null, null);
    });
    return fields();
  }

  @Override
  public String module() {
    return MODULE;
  }

  @Override
  public String limitKey() {
    return "custom_fields";
  }

  @Override
  public long usage(UUID org) {
    return jdbc.sql("select count(*) from student_field where organization_id = ?")
        .param(org)
        .query(Long.class)
        .single();
  }

  // ------------------------------------------------------------------ readiness & export

  /** UDISE+ preparation: how complete the enrolled students' records are. */
  public Readiness readiness() {
    entitlements.requireModule(MODULE);
    String y = loadSettings().currentYear();
    return jdbc.sql(
            "select count(*),"
                + " count(*) filter (where s.date_of_birth is null), count(*) filter (where s.gender is null),"
                + " count(*) filter (where s.address is null),"
                + " count(*) filter (where not exists (select 1 from student_guardian sg join guardian g on g.id = sg.guardian_id"
                + "   where sg.student_id = s.id and g.phone is not null)),"
                + " count(*) filter (where s.apaar_status = 'GENERATED'),"
                + " count(*) filter (where s.apaar_status in ('CONSENT_PENDING', 'CONSENT_GIVEN')),"
                + " count(*) filter (where s.apaar_status = 'NOT_STARTED'),"
                + " count(*) filter (where not exists (select 1 from student_enrolment e where e.student_id = s.id and e.academic_year = ?))"
                + " from student s where s.status = 'ENROLLED'")
        .param(y)
        .query((rs, i) ->
            new Readiness(
                rs.getLong(1),
                rs.getLong(2),
                rs.getLong(3),
                rs.getLong(4),
                rs.getLong(5),
                rs.getLong(6),
                rs.getLong(7),
                rs.getLong(8),
                rs.getLong(9)))
        .single();
  }

  public String csv(String year, UUID sectionId, String status) {
    entitlements.requireModule(MODULE);
    StringBuilder out =
        new StringBuilder(
            "Admission no,Name,Class,Roll no,Date of birth,Gender,Status,Guardian,Guardian phone,APAAR status\n");
    for (StudentRow r : students(year, sectionId, status, null)) {
      String apaar =
          jdbc.sql("select apaar_status from student where id = ?")
              .param(r.id())
              .query(String.class)
              .single();
      out.append(
              String.join(
                  ",",
                  cell(r.admissionNo()),
                  cell(r.name()),
                  cell(r.sectionLabel()),
                  r.rollNo() == null ? "" : r.rollNo().toString(),
                  r.dateOfBirth() == null ? "" : r.dateOfBirth().toString(),
                  cell(r.gender()),
                  cell(r.status()),
                  cell(r.guardianName()),
                  cell(r.guardianPhone()),
                  cell(apaar)))
          .append('\n');
    }
    return out.toString();
  }

  // ------------------------------------------------------------------ helpers (shared)

  void enrol(UUID studentId, String year, UUID sectionId, Integer rollNo) {
    if (rollNo != null && (rollNo < 1 || rollNo > 999)) {
      throw ApiException.badRequest("invalid_roll", "Roll numbers are 1 to 999.");
    }
    jdbc.sql(
            "insert into student_enrolment (organization_id, student_id, academic_year, class_section_id, roll_no)"
                + " values (?, ?, ?, ?, ?) on conflict (student_id, academic_year) do update"
                + " set class_section_id = excluded.class_section_id, roll_no = coalesce(excluded.roll_no, student_enrolment.roll_no)")
        .params(TenantContext.requireOrganizationId(), studentId, year, sectionId, rollNo)
        .update();
  }

  String nextAdmissionNo() {
    for (int i = 0; i < 1000; i++) {
      Settings st =
          jdbc.sql(
                  "update sis_settings set next_admission_no = next_admission_no + 1 returning admission_prefix, next_admission_no - 1")
              .query((rs, n) -> new Settings(null, rs.getString(1), rs.getInt(2)))
              .single();
      String candidate = st.admissionPrefix() + String.format("%04d", st.nextAdmissionNo());
      boolean used =
          jdbc.sql("select count(*) from student where admission_no = ?")
                  .param(candidate)
                  .query(Long.class)
                  .single()
              > 0;
      if (!used) {
        return candidate;
      }
    }
    throw ApiException.conflict(
        "numbering", "Could not find a free admission number. Check the numbering in settings.");
  }

  Student loadStudent(UUID id) {
    return jdbc.sql("select * from student where id = ?")
        .param(id)
        .query(this::mapStudent)
        .optional()
        .orElseThrow(() -> ApiException.notFound("Student"));
  }

  private record Clean(
      String admissionNo,
      String name,
      LocalDate dob,
      String gender,
      String blood,
      LocalDate admissionDate,
      String previousSchool,
      String address,
      String house,
      String medical,
      String custody,
      String apaarId,
      String apaarStatus,
      String apaarNote,
      String custom,
      UUID leadId) {}

  /** Validates a create (before == null) or a full edit of the student's own fields. */
  private Clean clean(StudentInput in, Student before) {
    String adm = optional(in.admissionNo(), 30, "Admission number");
    if (adm != null && !adm.matches("^[A-Za-z0-9/_-]{1,30}$")) {
      throw ApiException.badRequest(
          "invalid_admission_no", "Admission numbers use letters, digits, / _ -.");
    }
    LocalDate dob = in.dateOfBirth();
    LocalDate today = LocalDate.now(IST);
    if (dob != null && (dob.isAfter(today.minusYears(1)) || dob.isBefore(today.minusYears(30)))) {
      throw ApiException.badRequest("invalid_dob", "Check the date of birth.");
    }
    String apaar = optional(in.apaarId(), 12, "APAAR ID");
    if (apaar != null && !apaar.matches("^[0-9]{12}$")) {
      throw ApiException.badRequest("invalid_apaar", "An APAAR ID has 12 digits.");
    }
    String apaarStatus =
        in.apaarStatus() == null
            ? (before == null
                ? (apaar != null ? "GENERATED" : "NOT_STARTED")
                : before.apaarStatus())
            : oneOf(in.apaarStatus(), APAAR, "APAAR status");
    if (apaar != null && !"GENERATED".equals(apaarStatus)) {
      apaarStatus = "GENERATED";
    }
    return new Clean(
        adm,
        required(in.name(), 120, "Student name"),
        dob,
        in.gender() == null || in.gender().isBlank() ? null : oneOf(in.gender(), GENDERS, "gender"),
        in.bloodGroup() == null || in.bloodGroup().isBlank()
            ? null
            : oneOf(in.bloodGroup(), BLOOD, "blood group"),
        in.admissionDate(),
        optional(in.previousSchool(), 200, "Previous school"),
        optional(in.address(), 500, "Address"),
        optional(in.house(), 40, "House"),
        optional(in.medicalAlerts(), 1000, "Medical alerts"),
        optional(in.custodyNote(), 1000, "Custody / pickup note"),
        apaar,
        apaarStatus,
        optional(in.apaarNote(), 300, "APAAR note"),
        custom(in.custom()),
        before == null ? null : before.leadId());
  }

  /** Keeps only defined custom fields, each checked against its type. */
  private String custom(Map<String, Object> in) {
    ObjectNode out = json.createObjectNode();
    if (in != null) {
      for (Field f : fields()) {
        Object v = in.get(f.key());
        if (v == null || v.toString().isBlank()) {
          continue;
        }
        String s = v.toString().trim();
        switch (f.type()) {
          case "NUMBER" -> {
            try {
              out.put(f.key(), Double.parseDouble(s));
            } catch (NumberFormatException e) {
              throw ApiException.badRequest("invalid_field", f.label() + " must be a number.");
            }
          }
          case "DATE" -> {
            try {
              out.put(f.key(), LocalDate.parse(s).toString());
            } catch (DateTimeParseException e) {
              throw ApiException.badRequest("invalid_field", f.label() + " must be a date.");
            }
          }
          case "CHOICE" -> {
            if (!f.options().contains(s)) {
              throw ApiException.badRequest(
                  "invalid_field", "Choose an option for " + f.label() + ".");
            }
            out.put(f.key(), s);
          }
          case "YES_NO" -> out.put(f.key(), Boolean.parseBoolean(s) || "yes".equalsIgnoreCase(s));
          default -> {
            if (s.length() > 300) {
              throw ApiException.badRequest("too_long", f.label() + " is too long.");
            }
            out.put(f.key(), s);
          }
        }
      }
    }
    return out.toString();
  }

  static String keyOf(String label) {
    String k =
        label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    if (k.isEmpty() || !Character.isLetter(k.charAt(0))) {
      k = "f_" + k;
    }
    return k.length() > 40 ? k.substring(0, 40) : k;
  }

  static String year(String v) {
    if (v == null
        || !YEAR.matcher(v).matches()
        || Integer.parseInt(v.substring(5)) != (Integer.parseInt(v.substring(0, 4)) + 1) % 100) {
      throw ApiException.badRequest("invalid_year", "Academic year looks like 2026-27.");
    }
    return v;
  }

  static String phone(String v) {
    String t = v.trim();
    if (!PHONE.matcher(t).matches() || t.replaceAll("\\D", "").length() < 8) {
      throw ApiException.badRequest("invalid_phone", "Enter a valid phone number: " + t);
    }
    return t;
  }

  static String key(String phone) {
    String d = phone.replaceAll("\\D", "");
    return d.length() > 10 ? d.substring(d.length() - 10) : d;
  }

  static String email(String v) {
    if (v == null || v.isBlank()) {
      return null;
    }
    String t = v.trim();
    if (t.length() > 200 || !EMAIL.matcher(t).matches()) {
      throw ApiException.badRequest("invalid_email", "Enter a valid email address: " + t);
    }
    return t;
  }

  static String language(String v) {
    if (v == null || v.isBlank()) {
      return "en";
    }
    String t = v.trim().toLowerCase(Locale.ROOT);
    if (!t.matches("^[a-z]{2}$")) {
      throw ApiException.badRequest(
          "invalid_language", "Use a two-letter language code, e.g. en, hi, te.");
    }
    return t;
  }

  static String required(String v, int max, String label) {
    String t = v == null ? "" : v.trim();
    if (t.isEmpty()) {
      throw ApiException.badRequest("missing_field", label + " is required.");
    }
    if (t.length() > max) {
      throw ApiException.badRequest("too_long", label + " is too long.");
    }
    return t;
  }

  static String optional(String v, int max, String label) {
    return v == null || v.isBlank() ? null : required(v, max, label);
  }

  static String oneOf(String v, Set<String> allowed, String field) {
    if (v == null || !allowed.contains(v)) {
      throw ApiException.badRequest("invalid_" + field.replace(' ', '_'), "Unknown " + field + ".");
    }
    return v;
  }

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

  private static Section mapSection(ResultSet rs, int i) throws SQLException {
    return new Section(
        rs.getObject("id", UUID.class),
        rs.getString("academic_year"),
        rs.getString("grade"),
        rs.getString("section"),
        label(rs.getString("grade"), rs.getString("section")),
        rs.getString("class_teacher"),
        rs.getInt("grade_order"),
        rs.getLong("students"));
  }

  private Student mapStudent(ResultSet rs, int i) throws SQLException {
    Map<String, Object> custom;
    try {
      custom =
          json.convertValue(
              json.readTree(rs.getString("custom")),
              new com.fasterxml.jackson.core.type.TypeReference<
                  LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      custom = Map.of();
    }
    return new Student(
        rs.getObject("id", UUID.class),
        rs.getString("admission_no"),
        rs.getString("name"),
        rs.getObject("date_of_birth", LocalDate.class),
        rs.getString("gender"),
        rs.getString("blood_group"),
        rs.getString("status"),
        rs.getObject("admission_date", LocalDate.class),
        rs.getObject("leaving_date", LocalDate.class),
        rs.getString("leaving_reason"),
        rs.getString("previous_school"),
        rs.getString("address"),
        rs.getString("house"),
        rs.getString("medical_alerts"),
        rs.getString("custody_note"),
        rs.getString("apaar_id"),
        rs.getString("apaar_status"),
        rs.getString("apaar_note"),
        custom,
        rs.getObject("lead_id", UUID.class),
        rs.getTimestamp("created_at").toInstant());
  }
}
