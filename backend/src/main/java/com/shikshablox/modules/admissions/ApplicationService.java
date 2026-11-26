package com.shikshablox.modules.admissions;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shikshablox.modules.website.SiteChanged;
import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.entitlement.EntitlementService;
import com.shikshablox.platform.files.MediaService;
import com.shikshablox.platform.notification.NotificationService;
import com.shikshablox.platform.shared.RateLimiter;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.TenantContext;
import com.shikshablox.platform.tenancy.TenantResolver;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * M03b online applications. Parents apply on the school website with the student's details and the
 * documents the school asks for; every application is attached to a lead (an existing open lead
 * with the same phone number, or a new one) and moves it to Applied. The office verifies each
 * document, records the application fee (paid at school or by transfer) and reviews the application
 * in Studio.
 */
@Service
public class ApplicationService {

  private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);
  static final String FEATURE = "online_application";
  static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
  static final int MAX_FILES = 8;
  static final Set<String> STATUSES =
      Set.of("SUBMITTED", "UNDER_REVIEW", "DOCUMENTS_PENDING", "VERIFIED", "REJECTED", "WITHDRAWN");
  static final Set<String> FEE_STATUSES = Set.of("NOT_REQUIRED", "DUE", "PAID", "WAIVED");
  static final Set<String> DOC_STATUSES = Set.of("PENDING", "VERIFIED", "REJECTED");
  static final List<String> DEFAULT_GRADES =
      List.of(
          "Nursery",
          "LKG",
          "UKG",
          "Class 1",
          "Class 2",
          "Class 3",
          "Class 4",
          "Class 5",
          "Class 6",
          "Class 7",
          "Class 8",
          "Class 9",
          "Class 10",
          "Class 11",
          "Class 12");
  private static final Pattern PHONE = Pattern.compile("^[+0-9 \\-]{8,20}$");
  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
  private static final Pattern CODE = Pattern.compile("^[a-z0-9_]{1,40}$");

  public record DocRequirement(String code, String label, boolean required) {}

  public record FormSettings(
      boolean open,
      List<String> grades,
      List<DocRequirement> documents,
      int feeAmount,
      String feeInstructions,
      String intro) {}

  /** What the public form needs. */
  public record PublicForm(
      String school,
      String academicYear,
      List<String> grades,
      List<DocRequirement> documents,
      int feeAmount,
      String feeInstructions,
      String intro,
      long maxFileBytes) {}

  public record Submitted(String applicationNo, int feeAmount, String feeInstructions) {}

  /** An uploaded file from the public form or Studio. */
  public record IncomingFile(String docCode, String filename, byte[] content) {}

  public record Summary(
      UUID id,
      UUID leadId,
      String applicationNo,
      String academicYear,
      String grade,
      String studentName,
      String guardianName,
      String phone,
      String status,
      String feeStatus,
      int feeAmount,
      int documents,
      int verified,
      int rejected,
      Instant createdAt) {}

  public record Document(
      UUID id,
      String docCode,
      String label,
      String filename,
      String contentType,
      long sizeBytes,
      String status,
      String note,
      String reviewedByName,
      Instant reviewedAt,
      Instant createdAt) {}

  public record Application(
      UUID id,
      UUID leadId,
      String applicationNo,
      String academicYear,
      String grade,
      String studentName,
      LocalDate dateOfBirth,
      String gender,
      String previousSchool,
      String guardianName,
      String relationship,
      String phone,
      String email,
      String address,
      String secondGuardianName,
      String secondGuardianPhone,
      String status,
      String reviewNote,
      int feeAmount,
      String feeStatus,
      String feeReference,
      Instant feePaidAt,
      Instant createdAt,
      Instant updatedAt,
      List<Document> documents,
      List<DocRequirement> missing) {}

  /** Sent after commit to alert the school and acknowledge the parent. */
  record ApplicationSubmitted(UUID organizationId, UUID applicationId) {}

  private final TenantResolver resolver;
  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final RateLimiter limiter;
  private final AuditService audit;
  private final EntitlementService entitlements;
  private final MediaService media;
  private final NotificationService notifications;
  private final ApplicationEventPublisher events;
  private final ObjectMapper json;
  private final String adminBaseUrl;

  ApplicationService(
      TenantResolver resolver,
      JdbcClient jdbc,
      TransactionTemplate tx,
      RateLimiter limiter,
      AuditService audit,
      EntitlementService entitlements,
      MediaService media,
      NotificationService notifications,
      ApplicationEventPublisher events,
      ObjectMapper json,
      @Value("${shikshablox.admin-base-url:http://admin.shikshablox.localhost}")
          String adminBaseUrl) {
    this.resolver = resolver;
    this.jdbc = jdbc;
    this.tx = tx;
    this.limiter = limiter;
    this.audit = audit;
    this.entitlements = entitlements;
    this.media = media;
    this.notifications = notifications;
    this.events = events;
    this.json = json;
    this.adminBaseUrl = adminBaseUrl;
  }

  // ------------------------------------------------------------------ settings

  public FormSettings settings() {
    entitlements.requireFeature(AdmissionsService.MODULE, FEATURE);
    return loadSettings();
  }

  private FormSettings loadSettings() {
    return jdbc.sql(
            "select is_open, grades, documents::text, fee_amount, fee_instructions, intro"
                + " from admission_form_settings")
        .query((rs, i) ->
            new FormSettings(
                rs.getBoolean(1),
                List.of((String[]) rs.getArray(2).getArray()),
                readDocs(rs.getString(3)),
                rs.getInt(4),
                rs.getString(5),
                rs.getString(6)))
        .optional()
        .orElseGet(() ->
            new FormSettings(
                false,
                List.of(),
                List.of(
                    new DocRequirement("birth_certificate", "Birth certificate", true),
                    new DocRequirement("photo", "Student photograph", true),
                    new DocRequirement("address_proof", "Address proof", false),
                    new DocRequirement("report_card", "Previous report card / TC", false)),
                0,
                null,
                null));
  }

  public FormSettings saveSettings(FormSettings in) {
    entitlements.requireFeature(AdmissionsService.MODULE, FEATURE);
    List<String> grades = new ArrayList<>(new LinkedHashSet<>(clean(in.grades())));
    if (grades.size() > 40 || grades.stream().anyMatch(g -> g.length() > 60)) {
      throw ApiException.badRequest("invalid_grades", "Up to 40 classes, 60 characters each.");
    }
    List<DocRequirement> docs = new ArrayList<>();
    Set<String> codes = new LinkedHashSet<>();
    for (DocRequirement d : in.documents() == null ? List.<DocRequirement>of() : in.documents()) {
      String label = required(d.label(), 80, "Document name");
      String code = d.code() == null || d.code().isBlank() ? slug(label) : d.code();
      if (!CODE.matcher(code).matches() || !codes.add(code)) {
        throw ApiException.badRequest("invalid_document", "Each document needs a different name.");
      }
      docs.add(new DocRequirement(code, label, d.required()));
    }
    if (docs.size() > MAX_FILES) {
      throw ApiException.badRequest("too_many", "Ask for up to " + MAX_FILES + " documents.");
    }
    if (in.feeAmount() < 0 || in.feeAmount() > 100_000) {
      throw ApiException.badRequest("invalid_fee", "The fee must be between ₹0 and ₹1,00,000.");
    }
    String instructions = optional(in.feeInstructions(), 1000, "Fee instructions");
    String intro = optional(in.intro(), 2000, "Introduction");
    UUID org = TenantContext.requireOrganizationId();
    tx.executeWithoutResult(s -> {
      jdbc.sql(
              "insert into admission_form_settings (organization_id, is_open, grades, documents,"
                  + " fee_amount, fee_instructions, intro) values (?, ?, ?, ?::jsonb, ?, ?, ?)"
                  + " on conflict (organization_id) do update set is_open = excluded.is_open,"
                  + " grades = excluded.grades, documents = excluded.documents,"
                  + " fee_amount = excluded.fee_amount, fee_instructions = excluded.fee_instructions,"
                  + " intro = excluded.intro, updated_at = now()")
          .params(
              org,
              in.open(),
              grades.toArray(String[]::new),
              write(docs),
              in.feeAmount(),
              instructions,
              intro)
          .update();
      audit.record(
          "admissions.form_settings_saved",
          "admission_form_settings",
          org.toString(),
          null,
          Map.of("open", in.open(), "documents", docs.size(), "fee", in.feeAmount()));
      events.publishEvent(new SiteChanged(org));
    });
    return loadSettings();
  }

  // ------------------------------------------------------------------ public form

  /** The form for a school's website; 404 when the school has not opened applications. */
  public PublicForm publicForm(String host) {
    Organization org = resolver.resolve(host).orElseThrow(() -> ApiException.notFound("School"));
    return TenantContext.callInTenant(
        org.id(),
        () -> {
          FormSettings s = openSettings();
          String year = AdmissionsService.currentAcademicYear();
          return new PublicForm(
              org.name(),
              year,
              grades(s, year),
              s.documents(),
              s.feeAmount(),
              s.feeInstructions(),
              s.intro(),
              MAX_FILE_BYTES);
        });
  }

  private FormSettings openSettings() {
    if (!entitlements.hasFeature(AdmissionsService.MODULE, FEATURE)) {
      throw ApiException.notFound("Application form");
    }
    FormSettings s = loadSettings();
    if (!s.open()) {
      throw ApiException.notFound("Application form");
    }
    return s;
  }

  /** Configured classes, else the seat-plan classes for the year, else a standard list. */
  private List<String> grades(FormSettings s, String year) {
    if (!s.grades().isEmpty()) {
      return s.grades();
    }
    List<String> seats =
        jdbc.sql(
                "select grade from admission_seat where academic_year = ? and capacity > 0"
                    + " order by sort_order, grade")
            .param(year)
            .query(String.class)
            .list();
    return seats.isEmpty() ? DEFAULT_GRADES : seats;
  }

  /** Public submission with documents. Honeypot hits are accepted and dropped. */
  public Submitted submit(String host, JsonNode in, List<IncomingFile> files, String clientIp) {
    Organization org = resolver.resolve(host).orElseThrow(() -> ApiException.notFound("School"));
    String ipKey = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
    if (!limiter.tryAcquire("application:" + org.id() + ":" + ipKey, 5, Duration.ofMinutes(30))
        || !limiter.tryAcquire("application:" + org.id(), 100, Duration.ofHours(1))) {
      throw ApiException.tooManyRequests();
    }
    if (in == null || !in.isObject()) {
      throw ApiException.badRequest("invalid_application", "Please fill in the form.");
    }
    if (!in.path("website").asText("").isBlank()) {
      log.info("Application honeypot triggered for {}", org.slug());
      return new Submitted("—", 0, null);
    }
    return TenantContext.callInTenant(org.id(), () -> submitInTenant(in, files));
  }

  /**
   * Submits in the current school without rate limits or honeypot, e.g. for imports and the demo
   * school's sample data. Same validation as the public form.
   */
  public Submitted submitInCurrentSchool(JsonNode in, List<IncomingFile> files) {
    return submitInTenant(in, files);
  }

  private Submitted submitInTenant(JsonNode in, List<IncomingFile> files) {
    FormSettings s = openSettings();
    String year = AdmissionsService.currentAcademicYear();
    String grade = text(in, "grade", 60, true, "Class");
    if (!grades(s, year).contains(grade)) {
      throw ApiException.badRequest("invalid_grade", "Choose a class from the list.");
    }
    String student = text(in, "studentName", 120, true, "Student's full name");
    LocalDate dob = dateOfBirth(text(in, "dateOfBirth", 10, true, "Date of birth"));
    String gender = text(in, "gender", 10, false, "Gender");
    if (gender != null && !Set.of("FEMALE", "MALE", "OTHER").contains(gender)) {
      throw ApiException.badRequest("invalid_gender", "Unknown gender.");
    }
    String previous = text(in, "previousSchool", 200, false, "Previous school");
    String guardian = text(in, "guardianName", 120, true, "Parent / guardian name");
    String relationship = text(in, "relationship", 40, false, "Relationship");
    String phone = phone(text(in, "phone", 20, true, "Phone"));
    String email = email(text(in, "email", 200, false, "Email"));
    String address = text(in, "address", 500, true, "Address");
    String guardian2 = text(in, "secondGuardianName", 120, false, "Second parent / guardian");
    String phone2 = text(in, "secondGuardianPhone", 20, false, "Second phone");
    if (phone2 != null) {
      phone2 = phone(phone2);
    }
    if (!in.path("consent").asBoolean(false)) {
      throw ApiException.badRequest(
          "consent_required",
          "Please agree that the school may use these details and documents for admission.");
    }
    Map<String, DocRequirement> wanted = new LinkedHashMap<>();
    s.documents().forEach(d -> wanted.put(d.code(), d));
    List<IncomingFile> uploads = files == null ? List.of() : files;
    if (uploads.size() > MAX_FILES) {
      throw ApiException.badRequest("too_many", "Upload up to " + MAX_FILES + " files.");
    }
    Set<String> given = new LinkedHashSet<>();
    for (IncomingFile f : uploads) {
      if (!wanted.containsKey(f.docCode()) || !given.add(f.docCode())) {
        throw ApiException.badRequest("invalid_document", "Unexpected document upload.");
      }
    }
    for (DocRequirement d : s.documents()) {
      if (d.required() && !given.contains(d.code())) {
        throw ApiException.badRequest("missing_document", "Please upload: " + d.label() + ".");
      }
    }

    // Files first (outside the transaction), removed again if the database insert fails.
    List<MediaService.StoredFile> stored = new ArrayList<>();
    List<String> storedCodes = new ArrayList<>();
    try {
      for (IncomingFile f : uploads) {
        stored.add(
            media.storePrivate(
                "admissions/applications", f.filename(), f.content(), MAX_FILE_BYTES));
        storedCodes.add(f.docCode());
      }
      final String fPhone2 = phone2;
      return tx.execute(st -> {
        UUID leadId = findOrCreateLead(student, guardian, phone, email, grade, year, previous);
        String no = nextNumber(year);
        String feeStatus = s.feeAmount() > 0 ? "DUE" : "NOT_REQUIRED";
        UUID id =
            jdbc.sql(
                    "insert into admission_application (organization_id, lead_id, application_no,"
                        + " academic_year, grade, student_name, date_of_birth, gender, previous_school,"
                        + " guardian_name, relationship, phone, email, address, second_guardian_name,"
                        + " second_guardian_phone, fee_amount, fee_status, consent_given)"
                        + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true)"
                        + " returning id")
                .params(
                    TenantContext.requireOrganizationId(),
                    leadId,
                    no,
                    year,
                    grade,
                    student,
                    dob,
                    gender,
                    previous,
                    guardian,
                    relationship,
                    phone,
                    email,
                    address,
                    guardian2,
                    fPhone2,
                    s.feeAmount(),
                    feeStatus)
                .query(UUID.class)
                .single();
        for (int i = 0; i < stored.size(); i++) {
          insertDocument(id, wanted.get(storedCodes.get(i)), stored.get(i));
        }
        activity(
            leadId, "Online application " + no + " submitted (" + stored.size() + " documents)");
        moveToApplied(leadId);
        audit.record(
            "admissions.application_submitted",
            "admission_application",
            id.toString(),
            null,
            Map.of("applicationNo", no, "grade", grade, "documents", stored.size()));
        events.publishEvent(new ApplicationSubmitted(TenantContext.requireOrganizationId(), id));
        return new Submitted(no, s.feeAmount(), s.feeInstructions());
      });
    } catch (RuntimeException e) {
      if (!stored.isEmpty()) {
        media.deletePrivate(stored.stream().map(MediaService.StoredFile::storageKey).toList());
      }
      throw e;
    }
  }

  /** An open lead for this phone number this year, or a new website lead. */
  private UUID findOrCreateLead(
      String student,
      String guardian,
      String phone,
      String email,
      String grade,
      String year,
      String previous) {
    String digits = phone.replaceAll("\\D", "");
    String last10 = digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
    UUID existing =
        jdbc.sql(
                "select id from admission_lead where academic_year = ? and stage not in ('ADMITTED', 'LOST')"
                    + " and right(regexp_replace(phone, '\\D', '', 'g'), 10) = ?"
                    + " order by updated_at desc limit 1")
            .params(year, last10)
            .query(UUID.class)
            .optional()
            .orElse(null);
    if (existing != null) {
      jdbc.sql(
              "update admission_lead set student_name = coalesce(student_name, ?),"
                  + " email = coalesce(email, ?), grade_sought = coalesce(grade_sought, ?),"
                  + " previous_school = coalesce(previous_school, ?), updated_at = now() where id = ?")
          .params(student, email, grade, previous, existing)
          .update();
      return existing;
    }
    UUID id =
        jdbc.sql(
                "insert into admission_lead (organization_id, student_name, guardian_name, phone, email,"
                    + " grade_sought, academic_year, source, previous_school, next_follow_up_at)"
                    + " values (?, ?, ?, ?, ?, ?, ?, 'WEBSITE', ?, now() + interval '1 day') returning id")
            .params(
                TenantContext.requireOrganizationId(),
                student,
                guardian,
                phone,
                email,
                grade,
                year,
                previous)
            .query(UUID.class)
            .single();
    jdbc.sql(
            "insert into admission_activity (organization_id, lead_id, kind, body, to_stage)"
                + " values (?, ?, 'CREATED', 'From the online application form', 'NEW')")
        .params(TenantContext.requireOrganizationId(), id)
        .update();
    return id;
  }

  private void moveToApplied(UUID leadId) {
    String stage =
        jdbc.sql("select stage from admission_lead where id = ?")
            .param(leadId)
            .query(String.class)
            .single();
    int applied = AdmissionsService.STAGES.indexOf("APPLIED");
    if (AdmissionsService.STAGES.indexOf(stage) < applied) {
      jdbc.sql("update admission_lead set stage = 'APPLIED', updated_at = now() where id = ?")
          .param(leadId)
          .update();
      jdbc.sql(
              "insert into admission_activity (organization_id, lead_id, kind, from_stage, to_stage, actor_id)"
                  + " values (?, ?, 'STAGE', ?, 'APPLIED', ?)")
          .params(
              TenantContext.requireOrganizationId(),
              leadId,
              stage,
              TenantContext.userId().orElse(null))
          .update();
    }
  }

  /** APP-2027-0001: per school and admissions year, serialised by an advisory lock. */
  private String nextNumber(String year) {
    UUID org = TenantContext.requireOrganizationId();
    jdbc.sql("select pg_advisory_xact_lock(hashtext(?))")
        .param("admission_application:" + org + ":" + year)
        .query((rs, i) -> 1)
        .list();
    long n =
        jdbc.sql("select count(*) from admission_application where academic_year = ?")
                .param(year)
                .query(Long.class)
                .single()
            + 1;
    return "APP-" + year.substring(0, 4) + "-" + String.format("%04d", n);
  }

  private void insertDocument(UUID applicationId, DocRequirement d, MediaService.StoredFile f) {
    jdbc.sql(
            "insert into admission_document (organization_id, application_id, doc_code, label,"
                + " original_filename, content_type, size_bytes, sha256, storage_key)"
                + " values (?, ?, ?, ?, ?, ?, ?, ?, ?)")
        .params(
            TenantContext.requireOrganizationId(),
            applicationId,
            d.code(),
            d.label(),
            f.filename(),
            f.contentType(),
            f.sizeBytes(),
            f.sha256(),
            f.storageKey())
        .update();
  }

  @org.springframework.transaction.event.TransactionalEventListener(
      phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT,
      fallbackExecution = true)
  void notify(ApplicationSubmitted e) {
    Thread.ofVirtual()
        .start(() -> {
          try (var ignored = TenantContext.forTenant(e.organizationId(), null)) {
            Application a = load(e.applicationId());
            String school =
                jdbc.sql("select name from organization where id = ?")
                    .param(e.organizationId())
                    .query(String.class)
                    .optional()
                    .orElse("the school");
            List<String> staff =
                TenantContext.callAsSystem(
                    "application alert recipients",
                    () ->
                        jdbc.sql(
                                "select distinct u.email from role_assignment r join app_user u on u.id = r.user_id"
                                    + " where r.organization_id = ? and r.role in ('SCHOOL_ADMIN', 'ADMISSIONS_COUNSELLOR')"
                                    + " and u.status = 'ACTIVE'")
                            .param(e.organizationId())
                            .query(String.class)
                            .list());
            notifications.email(
                NotificationService.Template.APPLICATION_RECEIVED,
                staff,
                Map.of(
                    "school", school,
                    "applicationNo", a.applicationNo(),
                    "grade", a.grade(),
                    "documents", String.valueOf(a.documents().size()),
                    "link", adminBaseUrl + "/admissions?view=applications"));
            if (a.email() != null) {
              notifications.email(
                  NotificationService.Template.APPLICATION_ACKNOWLEDGED,
                  List.of(a.email()),
                  Map.of(
                      "school",
                      school,
                      "applicationNo",
                      a.applicationNo(),
                      "grade",
                      a.grade(),
                      "fee",
                      a.feeAmount() > 0
                          ? "\nApplication fee: ₹"
                              + a.feeAmount()
                              + " (see the school's payment instructions).\n"
                          : ""));
            }
          } catch (RuntimeException ex) {
            log.warn("Application alert failed: {}", ex.getMessage());
          }
        });
  }

  // ------------------------------------------------------------------ studio

  public List<Summary> list(String year, String status, UUID leadId, String q) {
    entitlements.requireFeature(AdmissionsService.MODULE, FEATURE);
    StringBuilder sql =
        new StringBuilder(
            "select a.id, a.lead_id, a.application_no, a.academic_year, a.grade, a.student_name,"
                + " a.guardian_name, a.phone, a.status, a.fee_status, a.fee_amount,"
                + " (select count(*) from admission_document d where d.application_id = a.id),"
                + " (select count(*) from admission_document d where d.application_id = a.id and d.status = 'VERIFIED'),"
                + " (select count(*) from admission_document d where d.application_id = a.id and d.status = 'REJECTED'),"
                + " a.created_at from admission_application a where true");
    List<Object> p = new ArrayList<>();
    if (leadId != null) {
      sql.append(" and a.lead_id = ?");
      p.add(leadId);
    } else if (year != null && !year.isBlank()) {
      sql.append(" and a.academic_year = ?");
      p.add(year);
    }
    if (status != null && !status.isBlank()) {
      sql.append(" and a.status = ?");
      p.add(oneOf(status, STATUSES, "status"));
    }
    if (q != null && !q.isBlank()) {
      String like = "%" + q.trim().replace("%", "") + "%";
      sql.append(
          " and (a.student_name ilike ? or a.guardian_name ilike ? or a.phone ilike ? or a.application_no ilike ?)");
      p.add(like);
      p.add(like);
      p.add(like);
      p.add(like);
    }
    sql.append(" order by a.created_at desc limit 500");
    return jdbc.sql(sql.toString())
        .params(p)
        .query((rs, i) ->
            new Summary(
                rs.getObject(1, UUID.class),
                rs.getObject(2, UUID.class),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5),
                rs.getString(6),
                rs.getString(7),
                rs.getString(8),
                rs.getString(9),
                rs.getString(10),
                rs.getInt(11),
                rs.getInt(12),
                rs.getInt(13),
                rs.getInt(14),
                rs.getTimestamp(15).toInstant()))
        .list();
  }

  public Application get(UUID id) {
    entitlements.requireFeature(AdmissionsService.MODULE, FEATURE);
    return load(id);
  }

  /** Review status and note. VERIFIED needs every required document verified. */
  public Application review(UUID id, String status, String note) {
    entitlements.requireFeature(AdmissionsService.MODULE, FEATURE);
    String to = oneOf(status, STATUSES, "status");
    String n = optional(note, 1000, "Note");
    return tx.execute(s -> {
      Application before = load(id);
      if ("VERIFIED".equals(to)) {
        Set<String> verified = new LinkedHashSet<>();
        before.documents().stream()
            .filter(d -> "VERIFIED".equals(d.status()))
            .forEach(d -> verified.add(d.docCode()));
        List<String> pending =
            loadSettings().documents().stream()
                .filter(d -> d.required() && !verified.contains(d.code()))
                .map(DocRequirement::label)
                .toList();
        if (!pending.isEmpty()) {
          throw ApiException.conflict(
              "documents_not_verified",
              "Verify these documents first: " + String.join(", ", pending) + ".");
        }
      }
      jdbc.sql(
              "update admission_application set status = ?, review_note = ?, updated_at = now() where id = ?")
          .params(to, n, id)
          .update();
      if (!before.status().equals(to)) {
        activity(
            before.leadId(),
            "Application "
                + before.applicationNo()
                + ": "
                + statusLabel(to)
                + (n == null ? "" : " — " + n));
      }
      audit.record(
          "admissions.application_reviewed",
          "admission_application",
          id.toString(),
          Map.of("status", before.status()),
          Map.of("status", to));
      return load(id);
    });
  }

  public Application recordFee(UUID id, String status, String reference) {
    entitlements.requireFeature(AdmissionsService.MODULE, FEATURE);
    String to = oneOf(status, FEE_STATUSES, "fee status");
    String ref = optional(reference, 120, "Receipt / reference");
    return tx.execute(s -> {
      Application before = load(id);
      jdbc.sql(
              "update admission_application set fee_status = ?, fee_reference = ?,"
                  + " fee_paid_at = case when ? = 'PAID' then coalesce(fee_paid_at, now()) else null end,"
                  + " updated_at = now() where id = ?")
          .params(to, ref, to, id)
          .update();
      if (!before.feeStatus().equals(to)) {
        activity(
            before.leadId(),
            "Application fee for "
                + before.applicationNo()
                + ": "
                + to.toLowerCase(Locale.ROOT).replace('_', ' ')
                + (ref == null ? "" : " (" + ref + ")"));
      }
      audit.record(
          "admissions.application_fee",
          "admission_application",
          id.toString(),
          Map.of("feeStatus", before.feeStatus()),
          Map.of("feeStatus", to));
      return load(id);
    });
  }

  /** Verify or reject one document; a rejection marks the application as waiting for documents. */
  public Application reviewDocument(UUID documentId, String status, String note) {
    entitlements.requireFeature(AdmissionsService.MODULE, FEATURE);
    String to = oneOf(status, DOC_STATUSES, "document status");
    String n = "REJECTED".equals(to) ? required(note, 300, "Reason") : optional(note, 300, "Note");
    return tx.execute(s -> {
      UUID appId = documentApplication(documentId);
      jdbc.sql(
              "update admission_document set status = ?, note = ?, reviewed_by = ?, reviewed_at = now() where id = ?")
          .params(to, n, TenantContext.userId().orElse(null), documentId)
          .update();
      if ("REJECTED".equals(to)) {
        jdbc.sql(
                "update admission_application set status = 'DOCUMENTS_PENDING', updated_at = now()"
                    + " where id = ? and status in ('SUBMITTED', 'UNDER_REVIEW', 'VERIFIED')")
            .param(appId)
            .update();
      } else {
        jdbc.sql(
                "update admission_application set status = 'UNDER_REVIEW', updated_at = now()"
                    + " where id = ? and status = 'SUBMITTED'")
            .param(appId)
            .update();
      }
      audit.record(
          "admissions.document_reviewed",
          "admission_document",
          documentId.toString(),
          null,
          Map.of("status", to));
      return load(appId);
    });
  }

  /** The office adds or replaces a document (e.g. a copy the parent brought or sent later). */
  public Application addDocument(UUID applicationId, IncomingFile file) {
    entitlements.requireFeature(AdmissionsService.MODULE, FEATURE);
    Application a = load(applicationId);
    DocRequirement d =
        loadSettings().documents().stream()
            .filter(x -> x.code().equals(file.docCode()))
            .findFirst()
            .orElse(new DocRequirement("other", "Other document", false));
    MediaService.StoredFile f =
        media.storePrivate(
            "admissions/applications", file.filename(), file.content(), MAX_FILE_BYTES);
    try {
      return tx.execute(s -> {
        insertDocument(applicationId, d, f);
        activity(a.leadId(), d.label() + " added to " + a.applicationNo());
        jdbc.sql(
                "update admission_application set status = 'UNDER_REVIEW', updated_at = now()"
                    + " where id = ? and status = 'DOCUMENTS_PENDING'")
            .param(applicationId)
            .update();
        audit.record(
            "admissions.document_added",
            "admission_application",
            applicationId.toString(),
            null,
            Map.of("document", d.code()));
        return load(applicationId);
      });
    } catch (RuntimeException e) {
      media.deletePrivate(List.of(f.storageKey()));
      throw e;
    }
  }

  public String downloadUrl(UUID documentId) {
    entitlements.requireFeature(AdmissionsService.MODULE, FEATURE);
    record Key(String key, String name) {}
    Key k =
        jdbc.sql("select storage_key, original_filename from admission_document where id = ?")
            .param(documentId)
            .query((rs, i) -> new Key(rs.getString(1), rs.getString(2)))
            .optional()
            .orElseThrow(() -> ApiException.notFound("Document"));
    audit.record(
        "admissions.document_viewed", "admission_document", documentId.toString(), null, null);
    return media.signedPrivateUrl(k.key(), k.name());
  }

  /** Deletes an application and its files (e.g. on the family's request). The lead stays. */
  public void delete(UUID id) {
    entitlements.requireFeature(AdmissionsService.MODULE, FEATURE);
    List<String> keys =
        tx.execute(s -> {
          Application a = load(id);
          List<String> k =
              jdbc.sql("select storage_key from admission_document where application_id = ?")
                  .param(id)
                  .query(String.class)
                  .list();
          jdbc.sql("delete from admission_application where id = ?").param(id).update();
          activity(
              a.leadId(), "Application " + a.applicationNo() + " and its documents were deleted");
          audit.record(
              "admissions.application_deleted",
              "admission_application",
              id.toString(),
              Map.of("applicationNo", a.applicationNo()),
              null);
          return k;
        });
    media.deletePrivate(keys);
  }

  // ------------------------------------------------------------------ helpers

  private UUID documentApplication(UUID documentId) {
    return jdbc.sql("select application_id from admission_document where id = ?")
        .param(documentId)
        .query(UUID.class)
        .optional()
        .orElseThrow(() -> ApiException.notFound("Document"));
  }

  private Application load(UUID id) {
    List<Document> docs =
        jdbc.sql(
                "select d.id, d.doc_code, d.label, d.original_filename, d.content_type, d.size_bytes,"
                    + " d.status, d.note, u.display_name, d.reviewed_at, d.created_at"
                    + " from admission_document d left join app_user u on u.id = d.reviewed_by"
                    + " where d.application_id = ? order by d.created_at, d.id")
            .param(id)
            .query((rs, i) ->
                new Document(
                    rs.getObject(1, UUID.class),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getString(5),
                    rs.getLong(6),
                    rs.getString(7),
                    rs.getString(8),
                    rs.getString(9),
                    instant(rs.getTimestamp(10)),
                    rs.getTimestamp(11).toInstant()))
            .list();
    Set<String> have = new LinkedHashSet<>();
    docs.stream().filter(d -> !"REJECTED".equals(d.status())).forEach(d -> have.add(d.docCode()));
    List<DocRequirement> missing =
        loadSettings().documents().stream().filter(d -> !have.contains(d.code())).toList();
    return jdbc.sql("select * from admission_application where id = ?")
        .param(id)
        .query((rs, i) -> map(rs, docs, missing))
        .optional()
        .orElseThrow(() -> ApiException.notFound("Application"));
  }

  private static Application map(ResultSet rs, List<Document> docs, List<DocRequirement> missing)
      throws SQLException {
    return new Application(
        rs.getObject("id", UUID.class),
        rs.getObject("lead_id", UUID.class),
        rs.getString("application_no"),
        rs.getString("academic_year"),
        rs.getString("grade"),
        rs.getString("student_name"),
        rs.getObject("date_of_birth", LocalDate.class),
        rs.getString("gender"),
        rs.getString("previous_school"),
        rs.getString("guardian_name"),
        rs.getString("relationship"),
        rs.getString("phone"),
        rs.getString("email"),
        rs.getString("address"),
        rs.getString("second_guardian_name"),
        rs.getString("second_guardian_phone"),
        rs.getString("status"),
        rs.getString("review_note"),
        rs.getInt("fee_amount"),
        rs.getString("fee_status"),
        rs.getString("fee_reference"),
        instant(rs.getTimestamp("fee_paid_at")),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(),
        docs,
        missing);
  }

  private void activity(UUID leadId, String body) {
    jdbc.sql(
            "insert into admission_activity (organization_id, lead_id, kind, body, actor_id)"
                + " values (?, ?, 'APPLICATION', ?, ?)")
        .params(
            TenantContext.requireOrganizationId(),
            leadId,
            body.length() > 2000 ? body.substring(0, 2000) : body,
            TenantContext.userId().orElse(null))
        .update();
  }

  static String statusLabel(String s) {
    return switch (s) {
      case "SUBMITTED" -> "submitted";
      case "UNDER_REVIEW" -> "under review";
      case "DOCUMENTS_PENDING" -> "documents pending";
      case "VERIFIED" -> "verified";
      case "REJECTED" -> "not accepted";
      case "WITHDRAWN" -> "withdrawn";
      default -> s;
    };
  }

  /** Date of birth: ISO date, age between 1 and 20 today. */
  static LocalDate dateOfBirth(String v) {
    LocalDate d;
    try {
      d = LocalDate.parse(v);
    } catch (DateTimeParseException e) {
      throw ApiException.badRequest("invalid_dob", "Enter the date of birth as a date.");
    }
    int age = Period.between(d, LocalDate.now(AdmissionsService.IST)).getYears();
    if (d.isAfter(LocalDate.now(AdmissionsService.IST)) || age < 1 || age > 20) {
      throw ApiException.badRequest("invalid_dob", "Check the date of birth.");
    }
    return d;
  }

  static String slug(String label) {
    String s =
        label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    if (s.isEmpty()) {
      s = "document";
    }
    return s.length() > 40 ? s.substring(0, 40) : s;
  }

  private List<DocRequirement> readDocs(String raw) {
    try {
      return json.readValue(raw, new TypeReference<List<DocRequirement>>() {});
    } catch (Exception e) {
      log.warn("Invalid admission form documents: {}", e.getMessage());
      return List.of();
    }
  }

  private String write(Object o) {
    try {
      return json.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static List<String> clean(List<String> in) {
    return in == null
        ? List.of()
        : in.stream().filter(x -> x != null && !x.isBlank()).map(String::trim).toList();
  }

  private static Instant instant(Timestamp t) {
    return t == null ? null : t.toInstant();
  }

  private static String text(JsonNode in, String key, int max, boolean req, String label) {
    String v = in.path(key).asText("").trim();
    if (v.isEmpty()) {
      if (req) {
        throw ApiException.badRequest("missing_field", label + " is required.");
      }
      return null;
    }
    if (v.length() > max) {
      throw ApiException.badRequest("too_long", label + " is too long.");
    }
    return v;
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
    if (v == null) {
      return null;
    }
    if (!EMAIL.matcher(v).matches()) {
      throw ApiException.badRequest("invalid_email", "Enter a valid email address.");
    }
    return v;
  }

  private static String oneOf(String v, Set<String> allowed, String field) {
    if (v == null || !allowed.contains(v)) {
      throw ApiException.badRequest("invalid_" + field.replace(' ', '_'), "Unknown " + field + ".");
    }
    return v;
  }
}
