package com.shikshablox.modules.certificates;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shikshablox.modules.comms.BroadcastService;
import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.entitlement.EntitlementService;
import com.shikshablox.platform.identity.CurrentUser;
import com.shikshablox.platform.identity.Permission;
import com.shikshablox.platform.shared.RateLimiter;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.TenantContext;
import com.shikshablox.platform.tenancy.TenantResolver;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Certificates (M32). Staff pick a student and a template; the fields are pre-filled from the
 * student record (and, for a TC, attendance and subjects), edited, and issued with the next number
 * of the template's series and a QR verification code. With approval on, staff without school admin
 * rights send certificates to the principal. Issued certificates keep a snapshot; they are
 * cancelled or re-issued as duplicates, never edited.
 */
@Service
public class CertificateService {

  static final String MODULE = "CERTIFICATES";
  static final ZoneId IST = ZoneId.of("Asia/Kolkata");
  static final Set<String> KINDS = Set.of("TC", "BONAFIDE", "CHARACTER", "STUDY", "CUSTOM");
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final TypeReference<Map<String, String>> FIELDS = new TypeReference<>() {};

  public record Settings(
      String signatoryName,
      String signatoryTitle,
      String schoolAddress,
      String affiliationNo,
      String schoolCode,
      boolean requireApproval,
      List<String> noDuesItems,
      boolean customTemplates,
      boolean approvalFeature,
      boolean parentRequests,
      boolean canApprove) {}

  public record SettingsInput(
      String signatoryName,
      String signatoryTitle,
      String schoolAddress,
      String affiliationNo,
      String schoolCode,
      Boolean requireApproval,
      List<String> noDuesItems) {}

  public record Template(
      UUID id,
      String kind,
      String name,
      String title,
      String body,
      String prefix,
      int nextNumber,
      boolean parentRequestable,
      boolean active) {}

  public record TemplateInput(
      String kind,
      String name,
      String title,
      String body,
      String prefix,
      Integer nextNumber,
      Boolean parentRequestable,
      Boolean active) {}

  public record Field(String key, String label, String value) {}

  public record Draft(
      UUID templateId,
      String kind,
      String title,
      UUID studentId,
      String studentName,
      String classLabel,
      List<Field> fields,
      String body,
      List<String> noDuesItems,
      List<String> problems,
      boolean willNeedApproval) {}

  public record IssueInput(
      UUID templateId,
      UUID studentId,
      String purpose,
      Map<String, String> fields,
      String body,
      List<String> noDues,
      UUID requestId) {}

  public record Certificate(
      UUID id,
      UUID templateId,
      String kind,
      String title,
      String number,
      String verifyCode,
      String status,
      UUID studentId,
      String studentName,
      String admissionNo,
      String classLabel,
      String purpose,
      List<Field> fields,
      String body,
      UUID duplicateOf,
      String requestedBy,
      String createdBy,
      String approvedBy,
      String issuedBy,
      Instant issuedAt,
      String statusNote,
      Instant createdAt,
      String verifyUrl) {}

  public record PrintView(
      Certificate certificate,
      String school,
      String schoolAddress,
      String affiliationNo,
      String schoolCode,
      String signatoryName,
      String signatoryTitle) {}

  public record Verification(
      String school,
      String title,
      String number,
      String studentName,
      String classLabel,
      Instant issuedAt,
      String status,
      boolean duplicateIssued) {}

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final AuditService audit;
  private final EntitlementService entitlements;
  private final BroadcastService broadcasts;
  private final TenantResolver resolver;
  private final RateLimiter limiter;
  private final ObjectMapper json;

  CertificateService(
      JdbcClient jdbc,
      TransactionTemplate tx,
      AuditService audit,
      @org.springframework.context.annotation.Lazy EntitlementService entitlements,
      BroadcastService broadcasts,
      TenantResolver resolver,
      RateLimiter limiter,
      ObjectMapper json) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.audit = audit;
    this.entitlements = entitlements;
    this.broadcasts = broadcasts;
    this.resolver = resolver;
    this.limiter = limiter;
    this.json = json;
  }

  // ------------------------------------------------------------------ access

  private void requireModule() {
    entitlements.requireModule(MODULE);
  }

  private boolean feature(String f) {
    return entitlements.hasFeature(MODULE, f);
  }

  /** School admins (school:manage) approve, cancel and re-issue. */
  static boolean canApprove() {
    return CurrentUser.get()
        .map(u ->
            u.roles().stream().anyMatch(r -> r.permissions().contains(Permission.SCHOOL_MANAGE)))
        .orElse(false);
  }

  private static void requireApprover() {
    if (!canApprove()) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "forbidden", "Only the principal or a school admin can do that.");
    }
  }

  private UUID userId() {
    return TenantContext.userId().orElse(null);
  }

  // ------------------------------------------------------------------ settings & templates

  public Settings settings() {
    requireModule();
    return loadSettings();
  }

  Settings loadSettings() {
    jdbc.sql("insert into certificate_settings (organization_id) values (?) on conflict do nothing")
        .param(TenantContext.requireOrganizationId())
        .update();
    boolean approval = feature("approval");
    return jdbc.sql("select * from certificate_settings")
        .query((rs, i) ->
            new Settings(
                rs.getString("signatory_name"),
                rs.getString("signatory_title"),
                rs.getString("school_address"),
                rs.getString("affiliation_no"),
                rs.getString("school_code"),
                approval && rs.getBoolean("require_approval"),
                List.of((String[]) rs.getArray("no_dues_items").getArray()),
                feature("custom_templates"),
                approval,
                feature("parent_requests"),
                canApprove()))
        .single();
  }

  public Settings saveSettings(SettingsInput in) {
    requireModule();
    requireApprover();
    Settings b = loadSettings();
    String name = opt(in.signatoryName(), b.signatoryName(), 120, "Signatory name");
    String title = opt(in.signatoryTitle(), b.signatoryTitle(), 60, "Signatory title");
    if (title == null) {
      title = "Principal";
    }
    String address = opt(in.schoolAddress(), b.schoolAddress(), 300, "School address");
    String aff = opt(in.affiliationNo(), b.affiliationNo(), 40, "Affiliation number");
    String code = opt(in.schoolCode(), b.schoolCode(), 40, "School code");
    boolean approval = in.requireApproval() == null ? b.requireApproval() : in.requireApproval();
    if (approval && !b.approvalFeature()) {
      throw ApiException.badRequest(
          "feature_not_in_plan", "Approval is part of ShikshaBlox Standard.");
    }
    List<String> items = b.noDuesItems();
    if (in.noDuesItems() != null) {
      LinkedHashSet<String> c = new LinkedHashSet<>();
      for (String s : in.noDuesItems()) {
        String t = s == null ? "" : s.trim();
        if (!t.isEmpty()) {
          if (t.length() > 60) {
            throw ApiException.badRequest(
                "too_long", "Keep each no-dues item under 60 characters.");
          }
          c.add(t);
        }
      }
      if (c.size() > 10) {
        throw ApiException.badRequest("too_many", "Use up to 10 no-dues items.");
      }
      items = List.copyOf(c);
    }
    String t2 = title;
    List<String> items2 = items;
    tx.executeWithoutResult(s -> {
      jdbc.sql(
              "update certificate_settings set signatory_name = ?, signatory_title = ?, school_address = ?, affiliation_no = ?,"
                  + " school_code = ?, require_approval = ?, no_dues_items = ?, updated_at = now()")
          .params(name, t2, address, aff, code, approval, items2.toArray(String[]::new))
          .update();
      audit.record(
          "certificates.settings_saved",
          "certificate_settings",
          null,
          null,
          Map.of("approval", approval));
    });
    return loadSettings();
  }

  /** The school's templates; the four standard ones are created the first time. */
  public List<Template> templates() {
    requireModule();
    ensureDefaults();
    return jdbc.sql("select * from certificate_template order by sort_order, name")
        .query((rs, i) -> mapTemplate(rs))
        .list();
  }

  private void ensureDefaults() {
    long n = jdbc.sql("select count(*) from certificate_template").query(Long.class).single();
    if (n > 0) {
      return;
    }
    int order = 0;
    for (String[] d : CertText.DEFAULTS) {
      jdbc.sql(
              "insert into certificate_template (organization_id, kind, name, title, body, prefix, parent_requestable, sort_order)"
                  + " values (?, ?, ?, ?, ?, ?, ?, ?) on conflict do nothing")
          .params(
              TenantContext.requireOrganizationId(),
              d[0],
              d[1],
              d[2],
              d[3],
              d[4],
              Boolean.parseBoolean(d[5]),
              ++order)
          .update();
    }
  }

  private Template template(UUID id) {
    return jdbc.sql("select * from certificate_template where id = ?")
        .param(id)
        .query((rs, i) -> mapTemplate(rs))
        .optional()
        .orElseThrow(() -> ApiException.notFound("Template"));
  }

  public Template saveTemplate(UUID id, TemplateInput in) {
    requireModule();
    requireApprover();
    Template before = id == null ? null : template(id);
    boolean custom = feature("custom_templates");
    String kind = before != null ? before.kind() : "CUSTOM";
    if (before == null && !custom) {
      throw ApiException.badRequest(
          "feature_not_in_plan", "Your own templates are part of ShikshaBlox Standard.");
    }
    String name = req(in.name() == null && before != null ? before.name() : in.name(), 80, "Name");
    String title =
        req(in.title() == null && before != null ? before.title() : in.title(), 120, "Title");
    String body =
        req(in.body() == null && before != null ? before.body() : in.body(), 4000, "Text");
    if (before != null
        && !custom
        && (!body.equals(before.body()) || !title.equals(before.title()))) {
      throw ApiException.badRequest(
          "feature_not_in_plan", "Changing certificate wording is part of ShikshaBlox Standard.");
    }
    String prefix =
        in.prefix() == null ? (before == null ? "" : before.prefix()) : in.prefix().trim();
    if (prefix.length() > 20 || !prefix.matches("^[A-Za-z0-9/ _-]*$")) {
      throw ApiException.badRequest(
          "invalid_prefix", "The prefix may use letters, digits, spaces, / _ and - (up to 20).");
    }
    int next =
        in.nextNumber() == null ? (before == null ? 1 : before.nextNumber()) : in.nextNumber();
    if (next < 1 || next > 9_999_999) {
      throw ApiException.badRequest("invalid_number", "The next number must be 1 to 9,999,999.");
    }
    boolean parent =
        in.parentRequestable() == null
            ? before != null && before.parentRequestable()
            : in.parentRequestable();
    if ("TC".equals(kind)) {
      parent = false; // TCs follow withdrawal, not a portal request
    }
    boolean active = in.active() == null || in.active();
    boolean p2 = parent;
    try {
      UUID tid =
          tx.execute(s -> {
            UUID x;
            if (before == null) {
              x =
                  jdbc.sql(
                          "insert into certificate_template (organization_id, kind, name, title, body, prefix, next_number, parent_requestable, active, sort_order)"
                              + " values (?, 'CUSTOM', ?, ?, ?, ?, ?, ?, ?, (select coalesce(max(sort_order), 0) + 1 from certificate_template)) returning id")
                      .params(
                          TenantContext.requireOrganizationId(),
                          name,
                          title,
                          body,
                          prefix,
                          next,
                          p2,
                          active)
                      .query(UUID.class)
                      .single();
            } else {
              jdbc.sql(
                      "update certificate_template set name = ?, title = ?, body = ?, prefix = ?, next_number = ?, parent_requestable = ?, active = ? where id = ?")
                  .params(name, title, body, prefix, next, p2, active, id)
                  .update();
              x = id;
            }
            audit.record(
                "certificates.template_saved",
                "certificate_template",
                x.toString(),
                null,
                Map.of("name", name));
            return x;
          });
      return template(tid);
    } catch (org.springframework.dao.DuplicateKeyException e) {
      throw ApiException.conflict("duplicate", "There is already a template called " + name + ".");
    }
  }

  public record StudentHit(
      UUID id, String name, String admissionNo, String classLabel, String status) {}

  /**
   * Student search for staff who issue certificates (front office need not open student records).
   */
  public List<StudentHit> students(String q) {
    requireModule();
    String t = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
    if (t.length() < 2) {
      return List.of();
    }
    return jdbc.sql(
            "select s.id, s.name, s.admission_no, (select cs.grade || ' ' || cs.section from student_enrolment e"
                + " join class_section cs on cs.id = e.class_section_id where e.student_id = s.id order by e.academic_year desc limit 1), s.status"
                + " from student s where lower(s.name) like ? or lower(s.admission_no) like ? order by s.status, s.name limit 20")
        .params("%" + t + "%", "%" + t + "%")
        .query((rs, i) ->
            new StudentHit(
                rs.getObject(1, UUID.class),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5)))
        .list();
  }

  // ------------------------------------------------------------------ draft

  /** Pre-filled fields and text for a student and template, plus anything that blocks issuing. */
  public Draft draft(UUID templateId, UUID studentId, String purpose) {
    requireModule();
    ensureDefaults();
    Template t = template(templateId);
    Map<String, String> vars = vars(studentId, purpose, Map.of());
    List<Field> fields = new ArrayList<>();
    if ("TC".equals(t.kind())) {
      Map<String, String> tc = tcFields(studentId);
      for (CertText.Field f : CertText.TC_FIELDS) {
        fields.add(new Field(f.key(), f.label(), tc.getOrDefault(f.key(), "")));
      }
    } else if ("CHARACTER".equals(t.kind())) {
      fields.add(new Field("conduct", "Conduct and character", "good"));
    }
    Map<String, String> fv = new HashMap<>(vars);
    fields.forEach(f -> fv.put("fields." + f.key(), f.value()));
    Settings st = loadSettings();
    return new Draft(
        t.id(),
        t.kind(),
        t.title(),
        studentId,
        vars.get("student.name"),
        vars.get("student.class"),
        fields,
        CertText.render(t.body(), fv),
        "TC".equals(t.kind()) ? st.noDuesItems() : List.of(),
        problems(t, studentId),
        st.requireApproval() && !canApprove());
  }

  private List<String> problems(Template t, UUID studentId) {
    List<String> out = new ArrayList<>();
    if (!t.active()) {
      out.add("This template is switched off.");
    }
    String status =
        jdbc.sql("select status from student where id = ?")
            .param(studentId)
            .query(String.class)
            .single();
    if ("TC".equals(t.kind())) {
      if ("ENROLLED".equals(status)) {
        out.add(
            "Mark the student as left first (Students → student → Withdraw); a TC is issued when a student leaves.");
      }
      long issued =
          jdbc.sql(
                  "select count(*) from certificate where student_id = ? and kind = 'TC' and status in ('ISSUED', 'PENDING_APPROVAL') and duplicate_of is null")
              .param(studentId)
              .query(Long.class)
              .single();
      if (issued > 0) {
        out.add(
            "A TC has already been issued for this student. Issue a duplicate from the register instead.");
      }
    } else if (!"ENROLLED".equals(status) && !"CHARACTER".equals(t.kind())) {
      out.add("This student is no longer enrolled.");
    }
    return out;
  }

  /** Merge fields from the student record. */
  Map<String, String> vars(UUID studentId, String purpose, Map<String, String> fields) {
    String year = currentYear();
    Map<String, String> v = new LinkedHashMap<>();
    jdbc.sql(
            "select s.name, s.admission_no, s.gender, s.date_of_birth, s.admission_date, s.leaving_date, c.grade, c.section, en.roll_no,"
                + " (select g.name from student_guardian sg join guardian g on g.id = sg.guardian_id where sg.student_id = s.id"
                + "   order by sg.is_primary desc, g.name limit 1) guardian, o.name school"
                + " from student s join organization o on o.id = s.organization_id"
                + " left join lateral (select * from student_enrolment e where e.student_id = s.id order by e.academic_year desc limit 1) en on true"
                + " left join class_section c on c.id = en.class_section_id where s.id = ?")
        .param(studentId)
        .query((rs, i) -> {
          String g = rs.getString("gender");
          v.put("student.name", rs.getString("name"));
          v.put("student.admission_no", rs.getString("admission_no"));
          v.put("student.dob", CertText.date(rs.getObject("date_of_birth", LocalDate.class)));
          v.put(
              "student.dob_words",
              CertText.dateInWords(rs.getObject("date_of_birth", LocalDate.class)));
          v.put(
              "student.admission_date",
              CertText.date(rs.getObject("admission_date", LocalDate.class)));
          v.put(
              "student.leaving_date", CertText.date(rs.getObject("leaving_date", LocalDate.class)));
          v.put(
              "student.class",
              rs.getString("grade") == null
                  ? ""
                  : rs.getString("grade").trim() + " " + rs.getString("section").trim());
          v.put(
              "student.roll_no",
              rs.getObject("roll_no") == null ? "" : String.valueOf(rs.getInt("roll_no")));
          v.put(
              "student.relation",
              "FEMALE".equals(g) ? "daughter of" : "MALE".equals(g) ? "son of" : "ward of");
          v.put(
              "student.possessive",
              "FEMALE".equals(g) ? "Her" : "MALE".equals(g) ? "His" : "Their");
          v.put(
              "student.possessive_lower",
              "FEMALE".equals(g) ? "her" : "MALE".equals(g) ? "his" : "their");
          v.put("student.object", "FEMALE".equals(g) ? "her" : "MALE".equals(g) ? "him" : "them");
          v.put("guardian.name", rs.getString("guardian"));
          v.put("school.name", rs.getString("school"));
          return Boolean.TRUE;
        })
        .optional()
        .orElseThrow(() -> ApiException.notFound("Student"));
    v.put("year", year);
    v.put("date", CertText.date(LocalDate.now(IST)));
    v.put("purpose", purpose == null ? "" : purpose);
    fields.forEach((k, val) -> v.put("fields." + k, val));
    return v;
  }

  /** TC rows filled from the record: guardians by relation, last class, subjects, attendance. */
  Map<String, String> tcFields(UUID studentId) {
    Map<String, String> v = vars(studentId, null, Map.of());
    Map<String, String> out = new HashMap<>();
    out.put("name", v.get("student.name"));
    out.put("nationality", "Indian");
    out.put("dob", v.get("student.dob"));
    out.put("dob_words", v.get("student.dob_words"));
    out.put("admission", v.get("student.admission_date"));
    out.put("last_class", v.get("student.class"));
    out.put("failed", "No");
    out.put("concession", "No");
    out.put("ncc", "No");
    out.put("conduct", "Good");
    out.put("application_date", v.get("date"));
    out.put("leaving_date", v.get("student.leaving_date"));
    jdbc.sql(
            "select sg.relation, g.name from student_guardian sg join guardian g on g.id = sg.guardian_id where sg.student_id = ?"
                + " order by sg.is_primary desc")
        .param(studentId)
        .query((rs, i) -> {
          String rel = rs.getString(1);
          if ("MOTHER".equals(rel)) {
            out.putIfAbsent("mother", rs.getString(2));
          } else {
            out.putIfAbsent("father", rs.getString(2));
          }
          return null;
        })
        .list();
    jdbc.sql("select leaving_reason from student where id = ?")
        .param(studentId)
        .query(String.class)
        .optional()
        .ifPresent(r -> out.put("reason", r == null ? "" : r));
    record En(UUID section, String year, String grade, String outcome) {}
    En en =
        jdbc.sql(
                "select e.class_section_id, e.academic_year, c.grade, e.outcome from student_enrolment e join class_section c on c.id = e.class_section_id"
                    + " where e.student_id = ? order by e.academic_year desc limit 1")
            .param(studentId)
            .query((rs, i) ->
                new En(
                    rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4)))
            .optional()
            .orElse(null);
    if (en != null) {
      if (en.outcome() != null) {
        out.put(
            "promotion",
            switch (en.outcome()) {
              case "PROMOTED", "GRADUATED" -> "Yes";
              case "RETAINED" -> "No";
              default -> "";
            });
      }
      List<String> subjects =
          jdbc.sql(
                  "select name from subject where academic_year = ? and grade = ? and kind = 'SCHOLASTIC' order by sort_order, name")
              .params(en.year(), en.grade())
              .query(String.class)
              .list();
      out.put("subjects", String.join(", ", subjects));
      LocalDate from = LocalDate.of(Integer.parseInt(en.year().substring(0, 4)), 4, 1);
      LocalDate to = from.plusYears(1).minusDays(1);
      long working =
          jdbc.sql(
                  "select count(distinct date) from attendance_session where class_section_id = ? and date between ? and ?")
              .params(en.section(), from, to)
              .query(Long.class)
              .single();
      if (working > 0) {
        Double present =
            jdbc.sql(
                    "with ds as (select distinct on (date) id from attendance_session where class_section_id = ? and date between ? and ?"
                        + " order by date, period)"
                        + " select coalesce(sum(case when m.status in ('P', 'L') then 1 when m.status = 'H' then 0.5 else 0 end), 0)"
                        + " from ds join attendance_mark m on m.session_id = ds.id where m.student_id = ?")
                .params(en.section(), from, to, studentId)
                .query(Double.class)
                .single();
        out.put("working_days", String.valueOf(working));
        out.put(
            "present_days",
            present % 1 == 0 ? String.valueOf(present.longValue()) : String.valueOf(present));
      }
    }
    return out;
  }

  // ------------------------------------------------------------------ issue

  public Certificate issue(IssueInput in) {
    requireModule();
    ensureDefaults();
    if (in.templateId() == null || in.studentId() == null) {
      throw ApiException.badRequest("missing_field", "Choose a student and a certificate.");
    }
    Template t = template(in.templateId());
    List<String> problems = problems(t, in.studentId());
    if (!problems.isEmpty()) {
      throw ApiException.conflict("cannot_issue", problems.get(0));
    }
    Settings st = loadSettings();
    String purpose = in.purpose() == null || in.purpose().isBlank() ? null : in.purpose().trim();
    if (purpose != null && purpose.length() > 200) {
      throw ApiException.badRequest("too_long", "Keep the purpose under 200 characters.");
    }
    Map<String, String> fields = cleanFields(t, in.fields());
    if ("TC".equals(t.kind())) {
      Set<String> confirmed = in.noDues() == null ? Set.of() : Set.copyOf(in.noDues());
      List<String> missing = st.noDuesItems().stream().filter(x -> !confirmed.contains(x)).toList();
      if (!missing.isEmpty()) {
        throw ApiException.badRequest(
            "dues", "Confirm no dues for: " + String.join(", ", missing) + ".");
      }
      fields.put("no_dues", String.join(", ", st.noDuesItems()));
    }
    String body;
    if ("TC".equals(t.kind())) {
      body = t.body();
    } else if (in.body() != null && !in.body().isBlank() && feature("custom_templates")) {
      body = req(in.body(), 6000, "Text");
    } else {
      Map<String, String> v = vars(in.studentId(), purpose, fields);
      body = CertText.render(t.body(), v);
    }
    boolean needsApproval = st.requireApproval() && !canApprove();
    String finalBody = body;
    UUID id =
        tx.execute(s -> {
          UUID cid;
          String fieldsJson = write(fields);
          if (in.requestId() != null) {
            int n =
                jdbc.sql(
                        "update certificate set template_id = ?, kind = ?, title = ?, purpose = coalesce(?, purpose), fields = ?::jsonb, body = ?,"
                            + " status = 'PENDING_APPROVAL', created_by = ?, updated_at = now() where id = ? and status = 'REQUESTED' and student_id = ?")
                    .params(
                        t.id(),
                        t.kind(),
                        t.title(),
                        purpose,
                        fieldsJson,
                        finalBody,
                        userId(),
                        in.requestId(),
                        in.studentId())
                    .update();
            if (n == 0) {
              throw ApiException.conflict(
                  "not_requested", "This request has already been handled.");
            }
            cid = in.requestId();
          } else {
            cid =
                jdbc.sql(
                        "insert into certificate (organization_id, template_id, student_id, kind, title, status, purpose, fields, body, created_by)"
                            + " values (?, ?, ?, ?, ?, 'PENDING_APPROVAL', ?, ?::jsonb, ?, ?) returning id")
                    .params(
                        TenantContext.requireOrganizationId(),
                        t.id(),
                        in.studentId(),
                        t.kind(),
                        t.title(),
                        purpose,
                        fieldsJson,
                        finalBody,
                        userId())
                    .query(UUID.class)
                    .single();
          }
          if (!needsApproval) {
            finalise(cid, t.id(), null);
          }
          audit.record(
              needsApproval ? "certificates.sent_for_approval" : "certificates.issued",
              "certificate",
              cid.toString(),
              null,
              Map.of("kind", t.kind(), "student", in.studentId().toString()));
          return cid;
        });
    Certificate c = load(id);
    notifyParent(c);
    return c;
  }

  /** Gives the certificate its number and verification code and marks it issued. */
  private void finalise(UUID id, UUID templateId, String numberOverride) {
    String number = numberOverride;
    if (number == null) {
      record Next(String prefix, int n) {}
      Next next =
          jdbc.sql(
                  "update certificate_template set next_number = next_number + 1 where id = ? returning prefix, next_number - 1")
              .param(templateId)
              .query((rs, i) -> new Next(rs.getString(1), rs.getInt(2)))
              .single();
      number = next.prefix() + String.format("%04d", next.n());
    }
    jdbc.sql(
            "update certificate set number = ?, verify_code = ?, status = 'ISSUED', issued_by = ?, issued_at = now(),"
                + " approved_by = case when status = 'PENDING_APPROVAL' and ? then ? else approved_by end, updated_at = now() where id = ?")
        .params(number, code(), userId(), canApprove(), userId(), id)
        .update();
  }

  public Certificate decide(UUID id, boolean approve, String note) {
    requireModule();
    requireApprover();
    Certificate c = load(id);
    if (!"PENDING_APPROVAL".equals(c.status()) && !"REQUESTED".equals(c.status())) {
      throw ApiException.conflict("not_pending", "This certificate is not waiting for a decision.");
    }
    String n = note == null || note.isBlank() ? null : note.trim();
    if (!approve && n == null) {
      throw ApiException.badRequest("missing_note", "Give a reason.");
    }
    if (approve && "REQUESTED".equals(c.status())) {
      throw ApiException.conflict(
          "not_prepared", "Prepare the certificate first (open the request and issue it).");
    }
    tx.executeWithoutResult(s -> {
      if (approve) {
        finalise(id, c.templateId(), null);
      } else {
        jdbc.sql(
                "update certificate set status = 'REJECTED', status_note = ?, approved_by = ?, updated_at = now() where id = ?")
            .params(n, userId(), id)
            .update();
      }
      audit.record(
          approve ? "certificates.approved" : "certificates.rejected",
          "certificate",
          id.toString(),
          null,
          null);
    });
    Certificate after = load(id);
    notifyParent(after);
    return after;
  }

  public Certificate cancel(UUID id, String reason) {
    requireModule();
    requireApprover();
    Certificate c = load(id);
    if (!"ISSUED".equals(c.status())) {
      throw ApiException.conflict("not_issued", "Only issued certificates can be cancelled.");
    }
    String r = req(reason, 300, "Reason");
    tx.executeWithoutResult(s -> {
      jdbc.sql(
              "update certificate set status = 'CANCELLED', status_note = ?, updated_at = now() where id = ?")
          .params(r, id)
          .update();
      audit.record(
          "certificates.cancelled",
          "certificate",
          id.toString(),
          Map.of("number", c.number()),
          Map.of("reason", r));
    });
    return load(id);
  }

  /** Re-issue an issued certificate (lost or damaged): same text, number with a duplicate mark. */
  public Certificate duplicate(UUID id, String reason) {
    requireModule();
    requireApprover();
    Certificate c = load(id);
    if (!"ISSUED".equals(c.status()) || c.duplicateOf() != null) {
      throw ApiException.conflict(
          "not_issued", "Duplicates are made from the original issued certificate.");
    }
    String r = req(reason, 300, "Reason");
    UUID nid =
        tx.execute(s -> {
          long n =
              jdbc.sql("select count(*) from certificate where duplicate_of = ?")
                  .param(id)
                  .query(Long.class)
                  .single();
          UUID x =
              jdbc.sql(
                      "insert into certificate (organization_id, template_id, student_id, kind, title, status, purpose, fields, body,"
                          + " duplicate_of, status_note, created_by) select organization_id, template_id, student_id, kind, title,"
                          + " 'PENDING_APPROVAL', purpose, fields, body, id, ?, ? from certificate where id = ? returning id")
                  .params(r, userId(), id)
                  .query(UUID.class)
                  .single();
          finalise(x, c.templateId(), c.number() + "/D" + (n + 1));
          audit.record(
              "certificates.duplicate_issued",
              "certificate",
              x.toString(),
              null,
              Map.of("original", c.number(), "reason", r));
          return x;
        });
    return load(nid);
  }

  // ------------------------------------------------------------------ register & print

  public List<Certificate> register(String status, String kind, String q) {
    requireModule();
    StringBuilder sql = new StringBuilder(SELECT + " where true");
    List<Object> p = new ArrayList<>();
    if (status != null && !status.isBlank()) {
      sql.append(" and c.status = ?");
      p.add(status.toUpperCase(Locale.ROOT));
    }
    if (kind != null && !kind.isBlank()) {
      sql.append(" and c.kind = ?");
      p.add(kind.toUpperCase(Locale.ROOT));
    }
    if (q != null && !q.isBlank()) {
      sql.append(
          " and (lower(s.name) like ? or lower(coalesce(c.number, '')) like ? or lower(s.admission_no) like ?)");
      String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
      p.add(like);
      p.add(like);
      p.add(like);
    }
    sql.append(" order by c.created_at desc limit 300");
    String base = siteBase();
    return jdbc.sql(sql.toString()).params(p).query((rs, i) -> map(rs, base)).list();
  }

  public PrintView print(UUID id) {
    requireModule();
    Certificate c = load(id);
    if (!"ISSUED".equals(c.status())) {
      throw ApiException.conflict("not_issued", "Only issued certificates can be printed.");
    }
    return printView(c);
  }

  private PrintView printView(Certificate c) {
    Settings st = loadSettings();
    String school =
        jdbc.sql("select name from organization where id = ?")
            .param(TenantContext.requireOrganizationId())
            .query(String.class)
            .single();
    return new PrintView(
        c,
        school,
        st.schoolAddress(),
        st.affiliationNo(),
        st.schoolCode(),
        st.signatoryName(),
        st.signatoryTitle());
  }

  Certificate load(UUID id) {
    String base = siteBase();
    return jdbc.sql(SELECT + " where c.id = ?")
        .param(id)
        .query((rs, i) -> map(rs, base))
        .optional()
        .orElseThrow(() -> ApiException.notFound("Certificate"));
  }

  private static final String SELECT =
      "select c.*, s.name student_name, s.admission_no,"
          + " (select cs.grade || ' ' || cs.section from student_enrolment e join class_section cs on cs.id = e.class_section_id"
          + "   where e.student_id = s.id order by e.academic_year desc limit 1) class_label,"
          + " g.name requested_by_name, cu.display_name created_by_name, au.display_name approved_by_name, iu.display_name issued_by_name"
          + " from certificate c join student s on s.id = c.student_id"
          + " left join guardian g on g.id = c.requested_by_guardian left join app_user cu on cu.id = c.created_by"
          + " left join app_user au on au.id = c.approved_by left join app_user iu on iu.id = c.issued_by";

  private Certificate map(ResultSet rs, String base) throws SQLException {
    String kind = rs.getString("kind");
    Map<String, String> f = readFields(rs.getString("fields"));
    List<Field> fields = new ArrayList<>();
    if ("TC".equals(kind)) {
      for (CertText.Field tf : CertText.TC_FIELDS) {
        fields.add(new Field(tf.key(), tf.label(), f.getOrDefault(tf.key(), "")));
      }
      if (f.containsKey("no_dues")) {
        fields.add(new Field("no_dues", "No dues confirmed for", f.get("no_dues")));
      }
    } else {
      f.forEach((k, v) -> fields.add(new Field(k, k, v)));
    }
    String code = rs.getString("verify_code");
    return new Certificate(
        rs.getObject("id", UUID.class),
        rs.getObject("template_id", UUID.class),
        kind,
        rs.getString("title"),
        rs.getString("number"),
        code,
        rs.getString("status"),
        rs.getObject("student_id", UUID.class),
        rs.getString("student_name"),
        rs.getString("admission_no"),
        rs.getString("class_label"),
        rs.getString("purpose"),
        fields,
        rs.getString("body"),
        rs.getObject("duplicate_of", UUID.class),
        rs.getString("requested_by_name"),
        rs.getString("created_by_name"),
        rs.getString("approved_by_name"),
        rs.getString("issued_by_name"),
        rs.getTimestamp("issued_at") == null ? null : rs.getTimestamp("issued_at").toInstant(),
        rs.getString("status_note"),
        rs.getTimestamp("created_at").toInstant(),
        code == null ? null : base + "/verify/" + code);
  }

  // ------------------------------------------------------------------ parent portal

  public record Requestable(UUID templateId, String name) {}

  public List<Requestable> requestable() {
    if (!entitlements.hasModule(MODULE) || !feature("parent_requests")) {
      return List.of();
    }
    ensureDefaults();
    return jdbc.sql(
            "select id, name from certificate_template where parent_requestable and active and kind <> 'TC' order by sort_order")
        .query((rs, i) -> new Requestable(rs.getObject(1, UUID.class), rs.getString(2)))
        .list();
  }

  /** The guardian's children's certificates (requests and issued ones). */
  public List<Certificate> forGuardian(UUID guardianId) {
    if (!entitlements.hasModule(MODULE)) {
      return List.of();
    }
    String base = siteBase();
    return jdbc.sql(
            SELECT
                + " where c.student_id in (select student_id from student_guardian where guardian_id = ?)"
                + " and (c.requested_by_guardian = ? or c.status = 'ISSUED') and c.kind <> 'TC'"
                + " order by c.created_at desc limit 30")
        .params(guardianId, guardianId)
        .query((rs, i) -> map(rs, base))
        .list();
  }

  public Certificate request(UUID guardianId, UUID studentId, UUID templateId, String purpose) {
    if (requestable().stream().noneMatch(r -> r.templateId().equals(templateId))) {
      throw ApiException.notFound("Certificate type");
    }
    boolean mine =
        jdbc.sql(
                    "select count(*) from student_guardian sg join student s on s.id = sg.student_id where sg.guardian_id = ? and sg.student_id = ? and s.status = 'ENROLLED'")
                .params(guardianId, studentId)
                .query(Long.class)
                .single()
            > 0;
    if (!mine) {
      throw ApiException.notFound("Child");
    }
    String p = req(purpose, 200, "Purpose");
    long open =
        jdbc.sql(
                "select count(*) from certificate where requested_by_guardian = ? and status = 'REQUESTED'")
            .param(guardianId)
            .query(Long.class)
            .single();
    if (open >= 3) {
      throw ApiException.conflict(
          "too_many", "You have requests waiting already. The school will get back to you.");
    }
    Template t = template(templateId);
    UUID id =
        tx.execute(s -> {
          UUID x =
              jdbc.sql(
                      "insert into certificate (organization_id, template_id, student_id, kind, title, status, purpose, requested_by_guardian)"
                          + " values (?, ?, ?, ?, ?, 'REQUESTED', ?, ?) returning id")
                  .params(
                      TenantContext.requireOrganizationId(),
                      t.id(),
                      studentId,
                      t.kind(),
                      t.title(),
                      p,
                      guardianId)
                  .query(UUID.class)
                  .single();
          audit.record(
              "certificates.requested",
              "certificate",
              x.toString(),
              null,
              Map.of("kind", t.kind()));
          return x;
        });
    return load(id);
  }

  /** An issued certificate of the guardian's child, for viewing in the portal. */
  public PrintView forGuardianPrint(UUID guardianId, UUID id) {
    Certificate c = load(id);
    boolean mine =
        jdbc.sql("select count(*) from student_guardian where guardian_id = ? and student_id = ?")
                .params(guardianId, c.studentId())
                .query(Long.class)
                .single()
            > 0;
    if (!mine || !"ISSUED".equals(c.status()) || "TC".equals(c.kind())) {
      throw ApiException.notFound("Certificate");
    }
    return printView(c);
  }

  /** Tells a requesting parent when their certificate is ready (or declined). */
  private void notifyParent(Certificate c) {
    if (c.requestedBy() == null || !entitlements.hasModule("COMMS")) {
      return;
    }
    if (!"ISSUED".equals(c.status()) && !"REJECTED".equals(c.status())) {
      return;
    }
    try {
      List<String> phones =
          jdbc.sql(
                  "select phone from guardian where id = (select requested_by_guardian from certificate where id = ?) and phone is not null")
              .param(c.id())
              .query(String.class)
              .list();
      tx.executeWithoutResult(s ->
          broadcasts.alert(
              "ISSUED".equals(c.status())
                  ? c.title() + " for " + c.studentName() + " is ready"
                  : c.title() + " for " + c.studentName() + " could not be issued",
              "ISSUED".equals(c.status())
                  ? "You can see it in the parent portal, or collect a signed copy from the school office."
                  : "Reason: " + (c.statusNote() == null ? "" : c.statusNote()),
              "certificate_ready",
              "Dear {{name}}, {{title}}. {{link}}",
              phones));
    } catch (RuntimeException e) {
      // a failed alert must not undo the certificate
    }
  }

  // ------------------------------------------------------------------ public verification

  public Verification verify(String host, String code) {
    if (code == null || !code.matches("^[A-Z0-9]{8,16}$")) {
      throw ApiException.notFound("Certificate");
    }
    Organization org = resolver.resolve(host).orElseThrow(() -> ApiException.notFound("School"));
    if (!limiter.tryAcquire("cert-verify:" + org.id() + ":" + code, 60, Duration.ofMinutes(10))) {
      throw ApiException.tooManyRequests();
    }
    return TenantContext.callInTenant(
        org.id(),
        () -> {
          if (!entitlements.hasModule(MODULE)) {
            throw ApiException.notFound("Certificate");
          }
          return jdbc.sql(
                  "select c.title, c.number, s.name, (select cs.grade || ' ' || cs.section from student_enrolment e"
                      + " join class_section cs on cs.id = e.class_section_id where e.student_id = s.id order by e.academic_year desc limit 1),"
                      + " c.issued_at, c.status, exists (select 1 from certificate d where d.duplicate_of = c.id and d.status = 'ISSUED')"
                      + " from certificate c join student s on s.id = c.student_id where c.verify_code = ?")
              .param(code)
              .query((rs, i) ->
                  new Verification(
                      org.name(),
                      rs.getString(1),
                      rs.getString(2),
                      rs.getString(3),
                      rs.getString(4),
                      rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant(),
                      rs.getString(6),
                      rs.getBoolean(7)))
              .optional()
              .orElseThrow(() -> ApiException.notFound("Certificate"));
        });
  }

  // ------------------------------------------------------------------ helpers

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

  private String siteBase() {
    try {
      return broadcasts.siteUrl();
    } catch (RuntimeException e) {
      return "";
    }
  }

  static Map<String, String> cleanFields(Template t, Map<String, String> in) {
    Map<String, String> out = new LinkedHashMap<>();
    if (in == null) {
      return out;
    }
    Set<String> allowed = new java.util.HashSet<>();
    if ("TC".equals(t.kind())) {
      CertText.TC_FIELDS.forEach(f -> allowed.add(f.key()));
    } else {
      allowed.add("conduct");
    }
    for (Map.Entry<String, String> e : in.entrySet()) {
      if (!allowed.contains(e.getKey())) {
        continue;
      }
      String v = e.getValue() == null ? "" : e.getValue().trim();
      if (v.length() > 300) {
        throw ApiException.badRequest("too_long", "A field is too long (max 300 characters).");
      }
      out.put(e.getKey(), v);
    }
    if ("TC".equals(t.kind()) && (out.getOrDefault("name", "").isEmpty())) {
      throw ApiException.badRequest("missing_field", "The pupil's name is required.");
    }
    return out;
  }

  static String code() {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < 10; i++) {
      b.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
    }
    return b.toString();
  }

  private Map<String, String> readFields(String s) {
    try {
      return s == null ? Map.of() : json.readValue(s, FIELDS);
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

  private static Template mapTemplate(ResultSet rs) throws SQLException {
    return new Template(
        rs.getObject("id", UUID.class),
        rs.getString("kind"),
        rs.getString("name"),
        rs.getString("title"),
        rs.getString("body"),
        rs.getString("prefix"),
        rs.getInt("next_number"),
        rs.getBoolean("parent_requestable"),
        rs.getBoolean("active"));
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

  private static String opt(String v, String dflt, int max, String label) {
    if (v == null) {
      return dflt;
    }
    String t = v.trim();
    if (t.isEmpty()) {
      return null;
    }
    if (t.length() > max) {
      throw ApiException.badRequest("too_long", label + " is too long (max " + max + ").");
    }
    return t;
  }
}
