package com.shikshablox.modules.sis;

import static com.shikshablox.modules.sis.SisService.nextYear;

import com.shikshablox.modules.admissions.AdmissionsService;
import com.shikshablox.modules.admissions.ApplicationService;
import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.entitlement.EntitlementService;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.TenantContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Bulk operations on student records: year-end promotion (with retained and leaving students), CSV
 * import, and enrolling an admitted applicant from the admissions CRM.
 */
@Service
public class SisOperations {

  public record PlanSection(
      UUID sectionId,
      String label,
      String grade,
      String section,
      String targetGrade,
      List<PlanStudent> students) {}

  public record PlanStudent(UUID id, String name, String admissionNo, Integer rollNo) {}

  public record PromotionPlan(
      String fromYear, String toYear, List<String> grades, List<PlanSection> sections) {}

  /** targetGrade null = the section graduates (leaves school as alumni). */
  public record SectionTarget(UUID sectionId, String targetGrade) {}

  public record PromotionRequest(
      String fromYear,
      List<SectionTarget> targets,
      List<UUID> retained,
      List<UUID> leaving,
      String leavingReason) {}

  public record PromotionResult(
      String newYear, int promoted, int retained, int graduated, int left) {}

  public record EnrolFromLead(UUID sectionId, String admissionNo) {}

  private final SisService sis;
  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final AuditService audit;
  private final EntitlementService entitlements;
  private final AdmissionsService admissions;
  private final ApplicationService applications;

  SisOperations(
      SisService sis,
      JdbcClient jdbc,
      TransactionTemplate tx,
      AuditService audit,
      EntitlementService entitlements,
      AdmissionsService admissions,
      ApplicationService applications) {
    this.sis = sis;
    this.jdbc = jdbc;
    this.tx = tx;
    this.audit = audit;
    this.entitlements = entitlements;
    this.admissions = admissions;
    this.applications = applications;
  }

  // ------------------------------------------------------------------ promotion

  /** Each section of the current year, its enrolled students, and the default next grade. */
  public PromotionPlan plan() {
    entitlements.requireFeature(SisService.MODULE, "promotion");
    String year = sis.loadSettings().currentYear();
    List<SisService.Section> sections = sis.sections(year);
    List<String> grades = sections.stream().map(SisService.Section::grade).distinct().toList();
    List<PlanSection> out = new ArrayList<>();
    for (SisService.Section s : sections) {
      int gi = grades.indexOf(s.grade());
      String target = gi + 1 < grades.size() ? grades.get(gi + 1) : null;
      List<PlanStudent> students =
          jdbc.sql(
                  "select s.id, s.name, s.admission_no, e.roll_no from student_enrolment e join student s on s.id = e.student_id"
                      + " where e.class_section_id = ? and s.status = 'ENROLLED' and e.outcome is null order by e.roll_no nulls last, s.name")
              .param(s.id())
              .query((rs, i) ->
                  new PlanStudent(
                      rs.getObject(1, UUID.class),
                      rs.getString(2),
                      rs.getString(3),
                      (Integer) rs.getObject(4)))
              .list();
      out.add(new PlanSection(s.id(), s.label(), s.grade(), s.section(), target, students));
    }
    return new PromotionPlan(year, nextYear(year), grades, out);
  }

  /**
   * Moves the school into the next academic year: students go to the target grade (same section
   * letter), retained students stay in their grade, leaving students are withdrawn, and sections
   * with no target graduate. Next year's sections are created as needed (class teachers are not
   * copied). The current year then becomes the new year.
   */
  public PromotionResult promote(PromotionRequest req) {
    entitlements.requireFeature(SisService.MODULE, "promotion");
    PromotionPlan plan = plan();
    if (req.fromYear() == null || !req.fromYear().equals(plan.fromYear())) {
      throw ApiException.conflict(
          "stale", "The school year has changed since this plan was made. Reload and try again.");
    }
    Map<UUID, String> targets = new HashMap<>();
    if (req.targets() != null) {
      for (SectionTarget t : req.targets()) {
        if (t.targetGrade() != null && !plan.grades().contains(t.targetGrade())) {
          throw ApiException.badRequest("invalid_grade", "Unknown class: " + t.targetGrade());
        }
        targets.put(t.sectionId(), t.targetGrade());
      }
    }
    Set<UUID> retained = new HashSet<>(req.retained() == null ? List.of() : req.retained());
    Set<UUID> leaving = new HashSet<>(req.leaving() == null ? List.of() : req.leaving());
    String reason =
        req.leavingReason() == null || req.leavingReason().isBlank()
            ? "Left at the end of " + plan.fromYear()
            : req.leavingReason().trim();
    String to = plan.toYear();
    int[] n = new int[4];
    List<UUID> touched = new ArrayList<>();
    tx.executeWithoutResult(st -> {
      Map<String, Integer> order = new HashMap<>();
      for (int i = 0; i < plan.grades().size(); i++) {
        order.put(plan.grades().get(i), i);
      }
      for (PlanSection s : plan.sections()) {
        String target =
            targets.containsKey(s.sectionId()) ? targets.get(s.sectionId()) : s.targetGrade();
        for (PlanStudent p : s.students()) {
          touched.add(p.id());
          if (leaving.contains(p.id())) {
            outcome(p.id(), plan.fromYear(), "LEFT");
            jdbc.sql(
                    "update student set status = 'WITHDRAWN', leaving_date = current_date, leaving_reason = ?, updated_at = now() where id = ?")
                .params(reason, p.id())
                .update();
            n[3]++;
          } else if (retained.contains(p.id())) {
            outcome(p.id(), plan.fromYear(), "RETAINED");
            sis.enrol(p.id(), to, sectionFor(to, s.grade(), s.section(), order), null);
            n[1]++;
          } else if (target == null) {
            outcome(p.id(), plan.fromYear(), "GRADUATED");
            jdbc.sql(
                    "update student set status = 'ALUMNI', leaving_date = current_date, leaving_reason = ?, updated_at = now() where id = ?")
                .params("Completed " + s.grade(), p.id())
                .update();
            n[2]++;
          } else {
            outcome(p.id(), plan.fromYear(), "PROMOTED");
            sis.enrol(p.id(), to, sectionFor(to, target, s.section(), order), p.rollNo());
            n[0]++;
          }
        }
      }
      // Empty sections still carry over, so next year's structure is complete.
      for (PlanSection s : plan.sections()) {
        sectionFor(to, s.grade(), s.section(), order);
      }
      jdbc.sql("update sis_settings set current_year = ?, updated_at = now()").param(to).update();
      audit.record(
          "sis.promoted",
          "sis_settings",
          plan.fromYear(),
          Map.of("year", plan.fromYear()),
          Map.of("year", to, "promoted", n[0], "retained", n[1], "graduated", n[2], "left", n[3]));
    });
    for (UUID id : touched) {
      sis.syncContacts(id);
    }
    return new PromotionResult(to, n[0], n[1], n[2], n[3]);
  }

  private void outcome(UUID studentId, String year, String outcome) {
    jdbc.sql("update student_enrolment set outcome = ? where student_id = ? and academic_year = ?")
        .params(outcome, studentId, year)
        .update();
  }

  /** The section for grade + letter in a year, created if missing. */
  private UUID sectionFor(String year, String grade, String section, Map<String, Integer> order) {
    jdbc.sql(
            "insert into class_section (organization_id, academic_year, grade, section, grade_order) values (?, ?, ?, ?, ?)"
                + " on conflict (organization_id, academic_year, grade, section) do nothing")
        .params(
            TenantContext.requireOrganizationId(),
            year,
            grade,
            section,
            order.getOrDefault(grade, 99))
        .update();
    return jdbc.sql(
            "select id from class_section where academic_year = ? and grade = ? and section = ?")
        .params(year, grade, section)
        .query(UUID.class)
        .single();
  }

  // ------------------------------------------------------------------ admissions -> student

  /** Turns an admitted (or offered) applicant into a student, using their application details. */
  public SisService.StudentDetail enrolFromLead(UUID leadId, EnrolFromLead req) {
    entitlements.requireModule(SisService.MODULE);
    long already =
        jdbc.sql("select count(*) from student where lead_id = ?")
            .param(leadId)
            .query(Long.class)
            .single();
    if (already > 0) {
      throw ApiException.conflict("already_enrolled", "This applicant is already a student.");
    }
    AdmissionsService.Lead lead = admissions.get(leadId).lead();
    ApplicationService.Application app = null;
    if (entitlements.hasFeature("ADMISSIONS_CRM", "online_application")) {
      List<ApplicationService.Summary> apps = applications.list(null, null, leadId, null);
      if (!apps.isEmpty()) {
        app = applications.get(apps.get(0).id());
      }
    }
    String studentName = app != null ? app.studentName() : lead.studentName();
    if (studentName == null || studentName.isBlank()) {
      throw ApiException.badRequest("missing_name", "Add the student's name to the lead first.");
    }
    List<SisService.GuardianInput> guardians = new ArrayList<>();
    guardians.add(
        new SisService.GuardianInput(
            null,
            app != null ? app.guardianName() : lead.guardianName(),
            app != null ? app.phone() : lead.phone(),
            app != null ? app.email() : lead.email(),
            null,
            null,
            relation(app == null ? null : app.relationship()),
            true,
            true,
            true));
    if (app != null && app.secondGuardianName() != null && app.secondGuardianPhone() != null) {
      guardians.add(
          new SisService.GuardianInput(
              null,
              app.secondGuardianName(),
              app.secondGuardianPhone(),
              null,
              null,
              null,
              "GUARDIAN",
              false,
              true,
              true));
    }
    SisService.StudentInput in =
        new SisService.StudentInput(
            req == null ? null : req.admissionNo(),
            studentName,
            app != null ? app.dateOfBirth() : null,
            app != null ? app.gender() : null,
            null,
            LocalDate.now(SisService.IST),
            app != null ? app.previousSchool() : lead.previousSchool(),
            app != null ? app.address() : null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            req == null ? null : req.sectionId(),
            null,
            guardians);
    final String leadStage = lead.stage();
    UUID id =
        tx.execute(s -> {
          if (!"ADMITTED".equals(leadStage)) {
            admissions.move(leadId, "ADMITTED", null);
          }
          UUID sid = sis.insertStudent(in, leadId);
          audit.record(
              "sis.enrolled_from_admissions",
              "student",
              sid.toString(),
              null,
              Map.of("leadId", leadId.toString()));
          return sid;
        });
    sis.syncContacts(id);
    return sis.student(id);
  }

  private static String relation(String r) {
    if (r == null) {
      return "GUARDIAN";
    }
    return switch (r.trim().toLowerCase(Locale.ROOT)) {
      case "mother" -> "MOTHER";
      case "father" -> "FATHER";
      default -> "GUARDIAN";
    };
  }

  // ------------------------------------------------------------------ CSV import

  /**
   * Header row required. Columns: admission_no, name, dob (YYYY-MM-DD), gender, class, section,
   * roll_no, address, blood_group, guardian_name, guardian_relation, guardian_phone,
   * guardian_email, guardian2_name, guardian2_relation, guardian2_phone. Students are matched by
   * admission number (updated and moved) or added; classes and sections of the current year are
   * created as needed.
   */
  public SisService.ImportResult importCsv(String csv) {
    entitlements.requireFeature(SisService.MODULE, "import");
    List<String> lines = csv == null ? List.of() : csv.lines().filter(l -> !l.isBlank()).toList();
    if (lines.size() < 2) {
      throw ApiException.badRequest(
          "empty_import", "The file has no students below the header row.");
    }
    if (lines.size() > 5001) {
      throw ApiException.badRequest("too_many", "Import up to 5,000 students at a time.");
    }
    List<String> header =
        split(lines.get(0)).stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
    if (!header.contains("name") || !header.contains("class")) {
      throw ApiException.badRequest(
          "invalid_header", "The first row must name the columns, including name and class.");
    }
    String year = sis.loadSettings().currentYear();
    int added = 0;
    int updated = 0;
    int skipped = 0;
    List<String> errors = new ArrayList<>();
    List<UUID> touched = new ArrayList<>();
    for (int r = 1; r < lines.size(); r++) {
      List<String> c = split(lines.get(r));
      try {
        String grade = SisService.required(cell(c, header, "class"), 40, "Class");
        String section =
            cell(c, header, "section") == null
                ? "A"
                : cell(c, header, "section").toUpperCase(Locale.ROOT);
        String adm = cell(c, header, "admission_no");
        List<SisService.GuardianInput> guardians = new ArrayList<>();
        for (String g : List.of("guardian", "guardian2")) {
          String gName = cell(c, header, g + "_name");
          if (gName != null) {
            guardians.add(
                new SisService.GuardianInput(
                    null,
                    gName,
                    cell(c, header, g + "_phone"),
                    cell(c, header, g + "_email"),
                    null,
                    null,
                    relationCode(cell(c, header, g + "_relation")),
                    guardians.isEmpty(),
                    true,
                    true));
          }
        }
        String rollRaw = cell(c, header, "roll_no");
        Integer roll;
        try {
          roll = rollRaw == null ? null : Integer.valueOf(rollRaw);
        } catch (NumberFormatException e) {
          throw ApiException.badRequest("invalid_roll", "Roll number must be a number.");
        }
        SisService.StudentInput in =
            new SisService.StudentInput(
                adm,
                cell(c, header, "name"),
                date(cell(c, header, "dob")),
                gender(cell(c, header, "gender")),
                cell(c, header, "blood_group"),
                null,
                null,
                cell(c, header, "address"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                roll,
                guardians);
        UUID existing =
            adm == null
                ? null
                : jdbc.sql("select id from student where admission_no = ?")
                    .param(adm)
                    .query(UUID.class)
                    .optional()
                    .orElse(null);
        UUID id =
            tx.execute(st -> {
              UUID sec = section(year, grade, section);
              if (existing != null) {
                SisService.Student before = sis.loadStudent(existing);
                jdbc.sql(
                        "update student set name = ?, date_of_birth = coalesce(?, date_of_birth), gender = coalesce(?, gender),"
                            + " address = coalesce(?, address), updated_at = now() where id = ?")
                    .params(
                        SisService.required(in.name(), 120, "Name"),
                        in.dateOfBirth(),
                        in.gender(),
                        in.address(),
                        existing)
                    .update();
                if ("ENROLLED".equals(before.status())) {
                  sis.enrol(existing, year, sec, roll);
                }
                return existing;
              }
              return sis.insertStudent(
                  new SisService.StudentInput(
                      in.admissionNo(),
                      in.name(),
                      in.dateOfBirth(),
                      in.gender(),
                      in.bloodGroup(),
                      null,
                      null,
                      in.address(),
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      sec,
                      roll,
                      guardians),
                  null);
            });
        touched.add(id);
        if (existing != null) {
          updated++;
        } else {
          added++;
        }
      } catch (ApiException e) {
        skipped++;
        if (errors.size() < 25) {
          errors.add("Row " + (r + 1) + ": " + e.getMessage());
        }
      }
    }
    for (UUID id : touched) {
      sis.syncContacts(id);
    }
    audit.record(
        "sis.imported",
        "student",
        null,
        null,
        Map.of("added", added, "updated", updated, "skipped", skipped));
    return new SisService.ImportResult(added, updated, skipped, errors);
  }

  private UUID section(String year, String grade, String section) {
    UUID id =
        jdbc.sql(
                "select id from class_section where academic_year = ? and lower(grade) = lower(?) and section = ?")
            .params(year, grade, section)
            .query(UUID.class)
            .optional()
            .orElse(null);
    if (id != null) {
      return id;
    }
    Integer order =
        jdbc.sql(
                "select min(grade_order) from class_section where academic_year = ? and lower(grade) = lower(?)")
            .params(year, grade)
            .query(Integer.class)
            .single();
    int go =
        order != null
            ? order
            : jdbc.sql(
                    "select coalesce(max(grade_order), -1) + 1 from class_section where academic_year = ?")
                .param(year)
                .query(Integer.class)
                .single();
    return jdbc.sql(
            "insert into class_section (organization_id, academic_year, grade, section, grade_order) values (?, ?, ?, ?, ?) returning id")
        .params(TenantContext.requireOrganizationId(), year, grade, section, go)
        .query(UUID.class)
        .single();
  }

  static String relationCode(String v) {
    if (v == null) {
      return "GUARDIAN";
    }
    String t = v.trim().toUpperCase(Locale.ROOT);
    return SisService.RELATIONS.contains(t) ? t : "GUARDIAN";
  }

  static String gender(String v) {
    if (v == null) {
      return null;
    }
    return switch (v.trim().toUpperCase(Locale.ROOT)) {
      case "F", "FEMALE", "GIRL" -> "FEMALE";
      case "M", "MALE", "BOY" -> "MALE";
      case "O", "OTHER" -> "OTHER";
      default -> throw ApiException.badRequest("invalid_gender", "Gender should be F, M or O.");
    };
  }

  static LocalDate date(String v) {
    if (v == null) {
      return null;
    }
    String t = v.trim();
    try {
      if (t.matches("^\\d{2}[/-]\\d{2}[/-]\\d{4}$")) {
        // Indian DD-MM-YYYY / DD/MM/YYYY
        return LocalDate.of(
            Integer.parseInt(t.substring(6)),
            Integer.parseInt(t.substring(3, 5)),
            Integer.parseInt(t.substring(0, 2)));
      }
      return LocalDate.parse(t);
    } catch (java.time.DateTimeException | NumberFormatException e) {
      throw ApiException.badRequest(
          "invalid_dob", "Dates should be YYYY-MM-DD or DD-MM-YYYY: " + t);
    }
  }

  static List<String> split(String line) {
    List<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (quoted) {
        if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          cur.append('"');
          i++;
        } else if (ch == '"') {
          quoted = false;
        } else {
          cur.append(ch);
        }
      } else if (ch == '"') {
        quoted = true;
      } else if (ch == ',') {
        out.add(cur.toString());
        cur.setLength(0);
      } else {
        cur.append(ch);
      }
    }
    out.add(cur.toString());
    return out;
  }

  private static String cell(List<String> cells, List<String> header, String col) {
    int i = header.indexOf(col);
    if (i < 0 || i >= cells.size()) {
      return null;
    }
    String v = cells.get(i).trim();
    return v.isEmpty() ? null : v;
  }
}
