package com.shikshablox.modules.comms;

import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.entitlement.EntitlementService;
import com.shikshablox.platform.entitlement.UsageMeter;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.TenantContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
 * Contacts (guardians) with a verifiable opt-in register, message templates with language variants,
 * and the school's messaging settings and prepaid wallet.
 */
@Service
public class CommsService implements UsageMeter {

  static final String MODULE = "COMMS";
  static final Set<String> CHANNELS = Set.of("WHATSAPP", "SMS", "EMAIL");
  private static final Pattern PHONE = Pattern.compile("^[+0-9 \\-]{8,20}$");
  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
  private static final Pattern NAME = Pattern.compile("^[a-z0-9_]{3,60}$");
  private static final Pattern LANG = Pattern.compile("^[a-z]{2}$");
  private static final Pattern TAG = Pattern.compile("^[\\p{L}\\p{N} ._-]{1,40}$");

  public record Contact(
      UUID id,
      String name,
      String phone,
      String email,
      String language,
      List<String> tags,
      String studentNames,
      boolean whatsappOptIn,
      boolean smsOptIn,
      boolean emailOptIn,
      Instant createdAt) {}

  public record ContactInput(
      String name,
      String phone,
      String email,
      String language,
      List<String> tags,
      String studentNames,
      Boolean whatsappOptIn,
      Boolean smsOptIn,
      Boolean emailOptIn,
      String consentNote) {}

  public record ConsentEntry(
      String channel,
      boolean optedIn,
      String source,
      String note,
      String actorName,
      Instant createdAt) {}

  public record ContactDetail(Contact contact, List<ConsentEntry> consent) {}

  public record ImportResult(int added, int updated, int skipped, List<String> errors) {}

  public record Template(
      UUID id,
      String name,
      String language,
      String category,
      String subject,
      String body,
      String whatsappStatus,
      String smsDltId) {}

  public record TemplateInput(
      String name,
      String language,
      String category,
      String subject,
      String body,
      String whatsappStatus,
      String smsDltId) {}

  public record Settings(
      LocalTime quietStart,
      LocalTime quietEnd,
      int monthlyCap,
      String provider,
      int whatsappRatePaise,
      int smsRatePaise,
      long balancePaise,
      long sentThisMonth) {}

  public record WalletEntry(long amountPaise, String reason, String actorName, Instant createdAt) {}

  public record TagCount(String tag, long contacts) {}

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final AuditService audit;
  private final EntitlementService entitlements;

  CommsService(
      JdbcClient jdbc,
      TransactionTemplate tx,
      AuditService audit,
      @org.springframework.context.annotation.Lazy EntitlementService entitlements) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.audit = audit;
    this.entitlements = entitlements;
  }

  // ------------------------------------------------------------------ usage meter

  @Override
  public String module() {
    return MODULE;
  }

  @Override
  public String limitKey() {
    return "contacts";
  }

  @Override
  public long usage(UUID organizationId) {
    return jdbc.sql("select count(*) from comms_contact where organization_id = ?")
        .param(organizationId)
        .query(Long.class)
        .single();
  }

  // ------------------------------------------------------------------ contacts

  public List<Contact> contacts(String tag, String q) {
    entitlements.requireModule(MODULE);
    StringBuilder sql = new StringBuilder("select * from comms_contact where true");
    List<Object> p = new ArrayList<>();
    if (tag != null && !tag.isBlank()) {
      sql.append(" and ? = any (tags)");
      p.add(tag);
    }
    if (q != null && !q.isBlank()) {
      String like = "%" + q.trim().replace("%", "") + "%";
      sql.append(
          " and (name ilike ? or coalesce(phone, '') ilike ? or coalesce(email, '') ilike ? or coalesce(student_names, '') ilike ?)");
      for (int i = 0; i < 4; i++) {
        p.add(like);
      }
    }
    sql.append(" order by name limit 2000");
    return jdbc.sql(sql.toString()).params(p).query(CommsService::mapContact).list();
  }

  public List<TagCount> tags() {
    entitlements.requireModule(MODULE);
    return jdbc.sql("select t, count(*) from comms_contact, unnest(tags) t group by t order by t")
        .query((rs, i) -> new TagCount(rs.getString(1), rs.getLong(2)))
        .list();
  }

  public ContactDetail contact(UUID id) {
    entitlements.requireModule(MODULE);
    List<ConsentEntry> log =
        jdbc.sql(
                "select c.channel, c.opted_in, c.source, c.note, u.display_name, c.created_at from comms_consent_log c"
                    + " left join app_user u on u.id = c.actor_id where c.contact_id = ? order by c.created_at desc limit 100")
            .param(id)
            .query((rs, i) ->
                new ConsentEntry(
                    rs.getString(1),
                    rs.getBoolean(2),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getString(5),
                    rs.getTimestamp(6).toInstant()))
            .list();
    return new ContactDetail(loadContact(id), log);
  }

  public Contact create(ContactInput in, String source) {
    entitlements.requireModule(MODULE);
    entitlements.checkCanAdd(MODULE, "contacts", 1, "more contacts");
    Clean c = clean(in, null);
    try {
      return tx.execute(s -> {
        UUID id =
            jdbc.sql(
                    "insert into comms_contact (organization_id, name, phone, email, language, tags, student_names,"
                        + " whatsapp_opt_in, sms_opt_in, email_opt_in) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id")
                .params(
                    TenantContext.requireOrganizationId(),
                    c.name,
                    c.phone,
                    c.email,
                    c.language,
                    c.tags.toArray(String[]::new),
                    c.studentNames,
                    c.whatsapp,
                    c.sms,
                    c.emailOk)
                .query(UUID.class)
                .single();
        logConsentChanges(id, null, c, source, in.consentNote());
        audit.record("comms.contact_created", "comms_contact", id.toString(), null, null);
        return loadContact(id);
      });
    } catch (DuplicateKeyException e) {
      throw ApiException.conflict(
          "duplicate_phone", "A contact with this phone number already exists.");
    }
  }

  public Contact update(UUID id, ContactInput in) {
    entitlements.requireModule(MODULE);
    try {
      return tx.execute(s -> {
        Contact before = loadContact(id);
        Clean c = clean(in, before);
        jdbc.sql(
                "update comms_contact set name = ?, phone = ?, email = ?, language = ?, tags = ?, student_names = ?,"
                    + " whatsapp_opt_in = ?, sms_opt_in = ?, email_opt_in = ?, updated_at = now() where id = ?")
            .params(
                c.name,
                c.phone,
                c.email,
                c.language,
                c.tags.toArray(String[]::new),
                c.studentNames,
                c.whatsapp,
                c.sms,
                c.emailOk,
                id)
            .update();
        logConsentChanges(id, before, c, "STAFF", in.consentNote());
        audit.record("comms.contact_updated", "comms_contact", id.toString(), null, null);
        return loadContact(id);
      });
    } catch (DuplicateKeyException e) {
      throw ApiException.conflict(
          "duplicate_phone", "A contact with this phone number already exists.");
    }
  }

  public void delete(UUID id) {
    entitlements.requireModule(MODULE);
    tx.executeWithoutResult(s -> {
      loadContact(id);
      jdbc.sql("delete from comms_contact where id = ?").param(id).update();
      audit.record("comms.contact_deleted", "comms_contact", id.toString(), null, null);
    });
  }

  /**
   * CSV import: name, phone, email, language, tags (separated by ;), students, whatsapp, sms, email
   * (yes/no). Existing contacts (same phone) are updated. The first row must be a header.
   */
  public ImportResult importCsv(String csv, String consentNote) {
    entitlements.requireModule(MODULE);
    List<String> lines = csv == null ? List.of() : csv.lines().filter(l -> !l.isBlank()).toList();
    if (lines.size() < 2) {
      throw ApiException.badRequest(
          "empty_import", "The file has no contacts below the header row.");
    }
    if (lines.size() > 5001) {
      throw ApiException.badRequest("too_many", "Import up to 5,000 contacts at a time.");
    }
    List<String> header =
        split(lines.get(0)).stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
    int iName = header.indexOf("name");
    int iPhone = header.indexOf("phone");
    if (iName < 0 || (iPhone < 0 && !header.contains("email"))) {
      throw ApiException.badRequest(
          "invalid_header",
          "The first row must name the columns, including name and phone or email.");
    }
    int added = 0;
    int updated = 0;
    int skipped = 0;
    List<String> errors = new ArrayList<>();
    for (int r = 1; r < lines.size(); r++) {
      List<String> cells = split(lines.get(r));
      try {
        ContactInput in =
            new ContactInput(
                cell(cells, header, "name"),
                cell(cells, header, "phone"),
                cell(cells, header, "email"),
                cell(cells, header, "language"),
                cell(cells, header, "tags") == null
                    ? null
                    : List.of(cell(cells, header, "tags").split(";")),
                cell(cells, header, "students"),
                yes(cell(cells, header, "whatsapp")),
                yes(cell(cells, header, "sms")),
                yes(
                    cell(cells, header, "email_opt_in") != null
                        ? cell(cells, header, "email_opt_in")
                        : cell(cells, header, "email_ok")),
                consentNote);
        String phone = in.phone() == null ? null : phone(in.phone());
        UUID existing =
            phone == null
                ? null
                : jdbc.sql(
                        "select id from comms_contact where right(regexp_replace(phone, '\\D', '', 'g'), 10) = ?")
                    .param(key(phone))
                    .query(UUID.class)
                    .optional()
                    .orElse(null);
        if (existing != null) {
          Contact before = loadContact(existing);
          ContactInput merged =
              new ContactInput(
                  in.name(),
                  in.phone(),
                  in.email() != null ? in.email() : before.email(),
                  in.language() != null ? in.language() : before.language(),
                  in.tags() != null ? union(before.tags(), in.tags()) : before.tags(),
                  in.studentNames() != null ? in.studentNames() : before.studentNames(),
                  in.whatsappOptIn() != null ? in.whatsappOptIn() : before.whatsappOptIn(),
                  in.smsOptIn() != null ? in.smsOptIn() : before.smsOptIn(),
                  in.emailOptIn() != null ? in.emailOptIn() : before.emailOptIn(),
                  consentNote);
          tx.executeWithoutResult(s -> {
            Clean c = clean(merged, before);
            jdbc.sql(
                    "update comms_contact set name = ?, email = ?, language = ?, tags = ?, student_names = ?,"
                        + " whatsapp_opt_in = ?, sms_opt_in = ?, email_opt_in = ?, updated_at = now() where id = ?")
                .params(
                    c.name,
                    c.email,
                    c.language,
                    c.tags.toArray(String[]::new),
                    c.studentNames,
                    c.whatsapp,
                    c.sms,
                    c.emailOk,
                    existing)
                .update();
            logConsentChanges(existing, before, c, "IMPORT", consentNote);
          });
          updated++;
        } else {
          create(in, "IMPORT");
          added++;
        }
      } catch (ApiException e) {
        skipped++;
        if (errors.size() < 20) {
          errors.add("Row " + (r + 1) + ": " + e.getMessage());
        }
      }
    }
    audit.record(
        "comms.contacts_imported",
        "comms_contact",
        null,
        null,
        Map.of("added", added, "updated", updated, "skipped", skipped));
    return new ImportResult(added, updated, skipped, errors);
  }

  /**
   * Keeps a guardian's contact in step with student records (matched by phone, else email): name,
   * email, language, children and the class tags (any known class label is replaced). Opt-ins are
   * never changed here; consent is recorded separately. Does nothing when the school has no
   * communication hub or the contact limit is reached.
   */
  public void syncGuardian(
      String name,
      String phone,
      String email,
      String language,
      List<String> classTags,
      List<String> allClassTags,
      String students) {
    if (!entitlements.hasModule(MODULE) || (phone == null && email == null)) {
      return;
    }
    UUID existing =
        phone != null
            ? jdbc.sql(
                    "select id from comms_contact where right(regexp_replace(phone, '\\D', '', 'g'), 10) = ?")
                .param(key(phone))
                .query(UUID.class)
                .optional()
                .orElse(null)
            : jdbc.sql("select id from comms_contact where lower(email) = lower(?) limit 1")
                .param(email)
                .query(UUID.class)
                .optional()
                .orElse(null);
    List<String> classes = cleanTags(classTags == null ? List.of() : classTags);
    String lang = language != null && LANG.matcher(language).matches() ? language : "en";
    String kids =
        students == null ? null : (students.length() > 300 ? students.substring(0, 300) : students);
    if (existing == null) {
      if (usage(TenantContext.requireOrganizationId()) >= limitOrUnlimited()) {
        return;
      }
      jdbc.sql(
              "insert into comms_contact (organization_id, name, phone, email, language, tags, student_names)"
                  + " values (?, ?, ?, ?, ?, ?, ?) on conflict do nothing")
          .params(
              TenantContext.requireOrganizationId(),
              name,
              phone,
              email,
              lang,
              classes.toArray(String[]::new),
              kids)
          .update();
      return;
    }
    Contact c = loadContact(existing);
    LinkedHashSet<String> tags = new LinkedHashSet<>();
    // Class tags from student records are replaced; the school's own tags are kept.
    java.util.Set<String> managed =
        new java.util.HashSet<>(allClassTags == null ? List.of() : allClassTags);
    c.tags().stream().filter(t -> !managed.contains(t)).forEach(tags::add);
    tags.addAll(classes);
    jdbc.sql(
            "update comms_contact set name = ?, email = coalesce(?, email), language = ?, tags = ?, student_names = ?,"
                + " updated_at = now() where id = ?")
        .params(name, email, lang, tags.toArray(String[]::new), kids, existing)
        .update();
  }

  private long limitOrUnlimited() {
    long limit = entitlements.limit(MODULE, "contacts");
    return limit < 0 ? Long.MAX_VALUE : limit;
  }

  /** The contact withdraws consent on every channel (link on a circular, or a reply). */
  void withdrawAll(UUID contactId, String source) {
    Contact c = loadContact(contactId);
    jdbc.sql(
            "update comms_contact set whatsapp_opt_in = false, sms_opt_in = false, email_opt_in = false, updated_at = now()"
                + " where id = ?")
        .param(contactId)
        .update();
    for (String ch : List.of("WHATSAPP", "SMS", "EMAIL")) {
      boolean was =
          switch (ch) {
            case "WHATSAPP" -> c.whatsappOptIn();
            case "SMS" -> c.smsOptIn();
            default -> c.emailOptIn();
          };
      if (was) {
        consent(contactId, ch, false, source, "Stopped from a message link");
      }
    }
  }

  // ------------------------------------------------------------------ templates

  public List<Template> templates() {
    entitlements.requireModule(MODULE);
    return jdbc.sql(
            "select * from comms_template order by name, case when language = 'en' then 0 else 1 end, language")
        .query(CommsService::mapTemplate)
        .list();
  }

  public Template saveTemplate(UUID id, TemplateInput in) {
    entitlements.requireModule(MODULE);
    String name = in.name() == null ? "" : in.name().trim();
    if (!NAME.matcher(name).matches()) {
      throw ApiException.badRequest(
          "invalid_name", "Template names use lowercase letters, digits and _ (3–60).");
    }
    String lang = in.language() == null || in.language().isBlank() ? "en" : in.language();
    if (!LANG.matcher(lang).matches()) {
      throw ApiException.badRequest(
          "invalid_language", "Use a two-letter language code, e.g. en, hi, te.");
    }
    String category =
        in.category() == null
            ? "UTILITY"
            : oneOf(in.category(), Set.of("UTILITY", "MARKETING"), "category");
    String body = required(in.body(), 1000, "Message");
    String status =
        in.whatsappStatus() == null
            ? "DRAFT"
            : oneOf(
                in.whatsappStatus(),
                Set.of("DRAFT", "SUBMITTED", "APPROVED", "REJECTED"),
                "status");
    String dlt = optional(in.smsDltId(), 25, "DLT template id");
    if (dlt != null && !dlt.matches("^[0-9]{10,25}$")) {
      throw ApiException.badRequest(
          "invalid_dlt", "The DLT template id is the 10–25 digit number from your DLT portal.");
    }
    try {
      return tx.execute(s -> {
        UUID tid;
        if (id == null) {
          tid =
              jdbc.sql(
                      "insert into comms_template (organization_id, name, language, category, subject, body, whatsapp_status,"
                          + " sms_dlt_id) values (?, ?, ?, ?, ?, ?, ?, ?) returning id")
                  .params(
                      TenantContext.requireOrganizationId(),
                      name,
                      lang,
                      category,
                      optional(in.subject(), 160, "Subject"),
                      body,
                      status,
                      dlt)
                  .query(UUID.class)
                  .single();
        } else {
          Template before = template(id);
          // Changing the wording invalidates a WhatsApp approval.
          String st =
              !before.body().equals(body)
                      && "APPROVED".equals(before.whatsappStatus())
                      && "APPROVED".equals(status)
                  ? "DRAFT"
                  : status;
          jdbc.sql(
                  "update comms_template set name = ?, language = ?, category = ?, subject = ?, body = ?, whatsapp_status = ?,"
                      + " sms_dlt_id = ?, updated_at = now() where id = ?")
              .params(
                  name, lang, category, optional(in.subject(), 160, "Subject"), body, st, dlt, id)
              .update();
          tid = id;
        }
        audit.record(
            "comms.template_saved",
            "comms_template",
            tid.toString(),
            null,
            Map.of("name", name, "language", lang));
        return template(tid);
      });
    } catch (DuplicateKeyException e) {
      throw ApiException.conflict(
          "duplicate_template", "This template already has a " + lang + " version.");
    }
  }

  public void deleteTemplate(UUID id) {
    entitlements.requireModule(MODULE);
    tx.executeWithoutResult(s -> {
      Template t = template(id);
      jdbc.sql("delete from comms_template where id = ?").param(id).update();
      audit.record(
          "comms.template_deleted",
          "comms_template",
          id.toString(),
          Map.of("name", t.name()),
          null);
    });
  }

  Template template(UUID id) {
    return jdbc.sql("select * from comms_template where id = ?")
        .param(id)
        .query(CommsService::mapTemplate)
        .optional()
        .orElseThrow(() -> ApiException.notFound("Template"));
  }

  /** Variants of a named template, keyed by language. */
  List<Template> variants(String name) {
    return jdbc.sql("select * from comms_template where name = ?")
        .param(name)
        .query(CommsService::mapTemplate)
        .list();
  }

  /** The best variant for a contact: their language, else English, else any. */
  static Template pick(List<Template> variants, String language) {
    return variants.stream()
        .filter(t -> t.language().equals(language))
        .findFirst()
        .or(() -> variants.stream().filter(t -> t.language().equals("en")).findFirst())
        .or(() -> variants.stream().findFirst())
        .orElse(null);
  }

  // ------------------------------------------------------------------ settings & wallet

  public Settings settings() {
    entitlements.requireModule(MODULE);
    return loadSettings();
  }

  Settings loadSettings() {
    jdbc.sql("insert into comms_settings (organization_id) values (?) on conflict do nothing")
        .param(TenantContext.requireOrganizationId())
        .update();
    long sent =
        jdbc.sql(
                "select count(*) from comms_delivery where status in ('SENT', 'DELIVERED')"
                    + " and sent_at >= date_trunc('month', now() at time zone 'Asia/Kolkata') at time zone 'Asia/Kolkata'")
            .query(Long.class)
            .single();
    return jdbc.sql("select * from comms_settings")
        .query((rs, i) ->
            new Settings(
                rs.getObject("quiet_start", LocalTime.class),
                rs.getObject("quiet_end", LocalTime.class),
                rs.getInt("monthly_cap"),
                rs.getString("provider"),
                rs.getInt("whatsapp_rate_paise"),
                rs.getInt("sms_rate_paise"),
                rs.getLong("balance_paise"),
                sent))
        .single();
  }

  /** School-editable: quiet hours and the monthly message cap. */
  public Settings saveSettings(LocalTime quietStart, LocalTime quietEnd, Integer monthlyCap) {
    entitlements.requireModule(MODULE);
    Settings before = loadSettings();
    int cap = monthlyCap == null ? before.monthlyCap() : monthlyCap;
    if (cap < 0 || cap > 1_000_000) {
      throw ApiException.badRequest(
          "invalid_cap", "The monthly cap must be 0 to 10,00,000 messages.");
    }
    LocalTime qs = quietStart == null ? before.quietStart() : quietStart;
    LocalTime qe = quietEnd == null ? before.quietEnd() : quietEnd;
    tx.executeWithoutResult(s -> {
      jdbc.sql(
              "update comms_settings set quiet_start = ?, quiet_end = ?, monthly_cap = ?, updated_at = now()")
          .params(qs, qe, cap)
          .update();
      audit.record("comms.settings_saved", "comms_settings", null, null, Map.of("cap", cap));
    });
    return loadSettings();
  }

  public List<WalletEntry> wallet() {
    entitlements.requireModule(MODULE);
    return jdbc.sql(
            "select w.amount_paise, w.reason, u.display_name, w.created_at from comms_wallet_entry w"
                + " left join app_user u on u.id = w.actor_id order by w.created_at desc limit 100")
        .query((rs, i) ->
            new WalletEntry(
                rs.getLong(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant()))
        .list();
  }

  /** Platform operation: credit a school's wallet after payment, or adjust it. */
  public Settings topUp(UUID organizationId, long amountPaise, String reason) {
    if (amountPaise == 0 || Math.abs(amountPaise) > 100_000_000L) {
      throw ApiException.badRequest("invalid_amount", "Enter an amount up to ₹10,00,000.");
    }
    String why = required(reason, 200, "Reason");
    return TenantContext.callInTenant(
        organizationId,
        () ->
            tx.execute(s -> {
              loadSettings();
              jdbc.sql(
                      "update comms_settings set balance_paise = balance_paise + ?, updated_at = now()")
                  .param(amountPaise)
                  .update();
              jdbc.sql(
                      "insert into comms_wallet_entry (organization_id, amount_paise, reason, actor_id) values (?, ?, ?, ?)")
                  .params(organizationId, amountPaise, why, TenantContext.userId().orElse(null))
                  .update();
              audit.record(
                  "comms.wallet_adjusted",
                  "comms_settings",
                  organizationId.toString(),
                  null,
                  Map.of("paise", amountPaise));
              return loadSettings();
            }));
  }

  // ------------------------------------------------------------------ helpers

  private record Clean(
      String name,
      String phone,
      String email,
      String language,
      List<String> tags,
      String studentNames,
      boolean whatsapp,
      boolean sms,
      boolean emailOk) {}

  private static Clean clean(ContactInput in, Contact before) {
    boolean edit = before != null;
    String name = in.name() == null && edit ? before.name() : required(in.name(), 120, "Name");
    String phone =
        in.phone() == null
            ? (edit ? before.phone() : null)
            : (in.phone().isBlank() ? null : phone(in.phone()));
    String email =
        in.email() == null
            ? (edit ? before.email() : null)
            : (in.email().isBlank() ? null : email(in.email()));
    if (phone == null && email == null) {
      throw ApiException.badRequest("missing_contact", "Give a phone number or an email address.");
    }
    String lang =
        in.language() == null || in.language().isBlank()
            ? (edit ? before.language() : "en")
            : in.language().trim().toLowerCase(Locale.ROOT);
    if (!LANG.matcher(lang).matches()) {
      throw ApiException.badRequest(
          "invalid_language", "Use a two-letter language code, e.g. en, hi, te.");
    }
    List<String> tags =
        in.tags() == null ? (edit ? before.tags() : List.of()) : cleanTags(in.tags());
    String students =
        in.studentNames() == null && edit
            ? before.studentNames()
            : optional(in.studentNames(), 300, "Students");
    boolean wa = in.whatsappOptIn() == null ? edit && before.whatsappOptIn() : in.whatsappOptIn();
    boolean sms = in.smsOptIn() == null ? edit && before.smsOptIn() : in.smsOptIn();
    boolean em = in.emailOptIn() == null ? edit && before.emailOptIn() : in.emailOptIn();
    if ((wa || sms) && phone == null) {
      throw ApiException.badRequest("missing_phone", "WhatsApp and SMS need a phone number.");
    }
    if (em && email == null) {
      throw ApiException.badRequest("missing_email", "Email needs an email address.");
    }
    return new Clean(name, phone, email, lang, tags, students, wa, sms, em);
  }

  static List<String> cleanTags(List<String> raw) {
    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (String t : raw) {
      if (t == null || t.isBlank()) {
        continue;
      }
      String v = t.trim();
      if (!TAG.matcher(v).matches()) {
        throw ApiException.badRequest(
            "invalid_tag", "Tags use letters, digits, spaces, . _ - (up to 40): " + v);
      }
      out.add(v);
    }
    if (out.size() > 20) {
      throw ApiException.badRequest("too_many_tags", "Up to 20 tags per contact.");
    }
    return List.copyOf(out);
  }

  private void logConsentChanges(UUID id, Contact before, Clean after, String source, String note) {
    String n = optional(note, 300, "Consent note");
    if ((before == null ? after.whatsapp : before.whatsappOptIn() != after.whatsapp)
        && (before != null || after.whatsapp)) {
      consent(id, "WHATSAPP", after.whatsapp, source, n);
    }
    if ((before == null ? after.sms : before.smsOptIn() != after.sms)
        && (before != null || after.sms)) {
      consent(id, "SMS", after.sms, source, n);
    }
    if ((before == null ? after.emailOk : before.emailOptIn() != after.emailOk)
        && (before != null || after.emailOk)) {
      consent(id, "EMAIL", after.emailOk, source, n);
    }
  }

  private void consent(UUID contactId, String channel, boolean in, String source, String note) {
    jdbc.sql(
            "insert into comms_consent_log (organization_id, contact_id, channel, opted_in, source, note, actor_id)"
                + " values (?, ?, ?, ?, ?, ?, ?)")
        .params(
            TenantContext.requireOrganizationId(),
            contactId,
            channel,
            in,
            source,
            note,
            TenantContext.userId().orElse(null))
        .update();
  }

  Contact loadContact(UUID id) {
    return jdbc.sql("select * from comms_contact where id = ?")
        .param(id)
        .query(CommsService::mapContact)
        .optional()
        .orElseThrow(() -> ApiException.notFound("Contact"));
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

  static Boolean yes(String v) {
    if (v == null) {
      return null;
    }
    return switch (v.trim().toLowerCase(Locale.ROOT)) {
      case "yes", "y", "true", "1", "haan", "ha" -> true;
      case "no", "n", "false", "0" -> false;
      default -> null;
    };
  }

  private static List<String> union(List<String> a, List<String> b) {
    LinkedHashSet<String> s = new LinkedHashSet<>(a);
    b.stream().filter(x -> x != null && !x.isBlank()).map(String::trim).forEach(s::add);
    return List.copyOf(s);
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

  private static String email(String v) {
    String t = v.trim();
    if (t.length() > 200 || !EMAIL.matcher(t).matches()) {
      throw ApiException.badRequest("invalid_email", "Enter a valid email address: " + t);
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
      throw ApiException.badRequest("invalid_" + field, "Unknown " + field + ".");
    }
    return v;
  }

  private static Contact mapContact(ResultSet rs, int i) throws SQLException {
    return new Contact(
        rs.getObject("id", UUID.class),
        rs.getString("name"),
        rs.getString("phone"),
        rs.getString("email"),
        rs.getString("language"),
        List.of((String[]) rs.getArray("tags").getArray()),
        rs.getString("student_names"),
        rs.getBoolean("whatsapp_opt_in"),
        rs.getBoolean("sms_opt_in"),
        rs.getBoolean("email_opt_in"),
        rs.getTimestamp("created_at").toInstant());
  }

  private static Template mapTemplate(ResultSet rs, int i) throws SQLException {
    return new Template(
        rs.getObject("id", UUID.class),
        rs.getString("name"),
        rs.getString("language"),
        rs.getString("category"),
        rs.getString("subject"),
        rs.getString("body"),
        rs.getString("whatsapp_status"),
        rs.getString("sms_dlt_id"));
  }
}
