package com.shikshablox.modules.disclosure;

import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.entitlement.EntitlementService;
import com.shikshablox.platform.files.MediaService;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.TenantContext;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * M02 compliance calendar: recurring obligations with a next due date, an owner and reminders;
 * marking one done logs a completion (optionally with evidence) and rolls the due date forward. The
 * evidence vault keeps certificates with expiry dates; medium schools can download an inspection
 * pack (register + documents as a ZIP).
 */
@Service
public class ComplianceService {

  static final String MODULE = "COMPLIANCE";
  static final ZoneId IST = ZoneId.of("Asia/Kolkata");
  static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
  static final long MAX_EXPORT_BYTES = 300L * 1024 * 1024;
  static final Set<String> CATEGORIES =
      Set.of(
          "AFFILIATION",
          "SAFETY",
          "HEALTH",
          "STATUTORY",
          "FINANCE",
          "STAFF",
          "TRANSPORT",
          "DISCLOSURE",
          "OTHER");
  static final Set<String> RECURRENCES =
      Set.of("ONCE", "MONTHLY", "QUARTERLY", "HALF_YEARLY", "YEARLY");

  /** Calendar state of an item today. */
  public enum State {
    OVERDUE,
    DUE_SOON,
    UPCOMING,
    NO_DATE,
    DONE
  }

  public record Item(
      UUID id,
      String title,
      String category,
      String authority,
      String description,
      String recurrence,
      LocalDate dueDate,
      int remindDays,
      UUID ownerId,
      String ownerName,
      boolean active,
      State state,
      Long daysLeft,
      LocalDate lastCompletedOn,
      int documents) {}

  public record ItemInput(
      String title,
      String category,
      String authority,
      String description,
      String recurrence,
      LocalDate dueDate,
      Integer remindDays,
      UUID ownerId) {}

  public record Completion(
      UUID id,
      LocalDate dueDate,
      LocalDate completedOn,
      String note,
      UUID documentId,
      String documentTitle,
      String completedByName,
      Instant createdAt) {}

  public record ItemDetail(Item item, List<Completion> completions, List<Document> documents) {}

  public record Document(
      UUID id,
      UUID itemId,
      String itemTitle,
      String title,
      String category,
      String referenceNo,
      LocalDate issuedOn,
      LocalDate expiresOn,
      String filename,
      String contentType,
      long sizeBytes,
      String expiry,
      Long daysLeft,
      String uploadedByName,
      Instant createdAt) {}

  public record DocumentInput(
      UUID itemId,
      String title,
      String category,
      String referenceNo,
      LocalDate issuedOn,
      LocalDate expiresOn) {}

  public record Upload(String filename, byte[] content) {}

  public record Summary(
      long overdue, long dueSoon, long noDate, long expiringDocuments, long expiredDocuments) {}

  public record Template(
      String key,
      String title,
      String category,
      String authority,
      String recurrence,
      String description) {}

  public record Person(UUID id, String name) {}

  /**
   * Common obligations for Indian schools, offered as suggestions. Frequencies are typical, not
   * legal advice: the school confirms them with its board and local authorities.
   */
  static final List<Template> TEMPLATES =
      List.of(
          new Template(
              "fire_noc",
              "Fire safety certificate (NOC) renewal",
              "SAFETY",
              "Fire department",
              "YEARLY",
              "Keep the current certificate in the evidence vault with its expiry date."),
          new Template(
              "fire_drill",
              "Fire / evacuation drill",
              "SAFETY",
              null,
              "QUARTERLY",
              "Record the date, participation and observations."),
          new Template(
              "extinguishers",
              "Fire extinguisher inspection and refill",
              "SAFETY",
              null,
              "YEARLY",
              null),
          new Template(
              "building_safety",
              "Building safety / structural stability certificate",
              "SAFETY",
              "Public works / local authority",
              "YEARLY",
              null),
          new Template(
              "water_test",
              "Drinking water quality test",
              "HEALTH",
              "Approved laboratory",
              "QUARTERLY",
              "Upload the lab report."),
          new Template(
              "sanitation",
              "Health and sanitation certificate",
              "HEALTH",
              "Health department",
              "YEARLY",
              null),
          new Template(
              "safety_committee",
              "School safety / POCSO committee meeting",
              "SAFETY",
              null,
              "QUARTERLY",
              "Upload the minutes."),
          new Template(
              "disclosure_review",
              "Mandatory public disclosure review (website)",
              "DISCLOSURE",
              "Board",
              "HALF_YEARLY",
              "Check every disclosure on the website and mark it reviewed in Studio."),
          new Template(
              "affiliation",
              "Board affiliation renewal / extension",
              "AFFILIATION",
              "Board",
              "ONCE",
              "Set the due date well before the current affiliation ends."),
          new Template(
              "udise", "UDISE+ annual data submission", "STATUTORY", "UDISE+", "YEARLY", null),
          new Template(
              "audit",
              "Annual audit of accounts",
              "FINANCE",
              "Chartered accountant",
              "YEARLY",
              null),
          new Template(
              "tds", "TDS quarterly return", "FINANCE", "Income Tax Department", "QUARTERLY", null),
          new Template(
              "pf_esi", "PF and ESI monthly returns", "STAFF", "EPFO / ESIC", "MONTHLY", null),
          new Template(
              "police_verification",
              "Police verification of new staff and drivers",
              "STAFF",
              "Police",
              "YEARLY",
              "Check that everyone who joined this year is verified."),
          new Template(
              "vehicles",
              "School vehicle fitness, permit and insurance",
              "TRANSPORT",
              "Transport department",
              "YEARLY",
              "Keep each vehicle's certificates in the evidence vault with expiry dates."));

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final AuditService audit;
  private final EntitlementService entitlements;
  private final MediaService media;

  ComplianceService(
      JdbcClient jdbc,
      TransactionTemplate tx,
      AuditService audit,
      EntitlementService entitlements,
      MediaService media) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.audit = audit;
    this.entitlements = entitlements;
    this.media = media;
  }

  static LocalDate today() {
    return LocalDate.now(IST);
  }

  // ------------------------------------------------------------------ calendar

  public List<Item> items(boolean includeInactive) {
    entitlements.requireFeature(MODULE, "calendar");
    return jdbc.sql(
            ITEM_SELECT
                + (includeInactive ? "" : " where i.active")
                + " order by i.due_date nulls first, i.title")
        .query(this::mapItem)
        .list();
  }

  public Summary summary() {
    entitlements.requireFeature(MODULE, "calendar");
    LocalDate today = today();
    long[] c = new long[3];
    for (Item i : items(false)) {
      switch (i.state()) {
        case OVERDUE -> c[0]++;
        case DUE_SOON -> c[1]++;
        case NO_DATE -> c[2]++;
        default -> {}
      }
    }
    long expiring =
        jdbc.sql(
                "select count(*) from compliance_document where expires_on >= ? and expires_on <= ?")
            .params(today, today.plusDays(60))
            .query(Long.class)
            .single();
    long expired =
        jdbc.sql(
                "select count(*) from compliance_document d where expires_on < ? and not exists"
                    + " (select 1 from compliance_document n where n.item_id = d.item_id and n.item_id is not null"
                    + "  and n.id <> d.id and (n.expires_on is null or n.expires_on >= ?))")
            .params(today, today)
            .query(Long.class)
            .single();
    return new Summary(c[0], c[1], c[2], expiring, expired);
  }

  public ItemDetail item(UUID id) {
    entitlements.requireFeature(MODULE, "calendar");
    List<Completion> completions =
        jdbc.sql(
                "select c.id, c.due_date, c.completed_on, c.note, c.document_id, d.title, u.display_name,"
                    + " c.created_at from compliance_completion c"
                    + " left join compliance_document d on d.id = c.document_id"
                    + " left join app_user u on u.id = c.completed_by"
                    + " where c.item_id = ? order by c.completed_on desc, c.created_at desc limit 100")
            .param(id)
            .query((rs, i) ->
                new Completion(
                    rs.getObject(1, UUID.class),
                    date(rs, 2),
                    date(rs, 3),
                    rs.getString(4),
                    rs.getObject(5, UUID.class),
                    rs.getString(6),
                    rs.getString(7),
                    rs.getTimestamp(8).toInstant()))
            .list();
    return new ItemDetail(loadItem(id), completions, documentsWhere("d.item_id = ?", id));
  }

  public Item create(ItemInput in) {
    entitlements.requireFeature(MODULE, "calendar");
    Validated v = validate(in, null);
    return tx.execute(s -> {
      UUID id =
          jdbc.sql(
                  "insert into compliance_item (organization_id, title, category, authority, description,"
                      + " recurrence, due_date, remind_days, owner_id, created_by)"
                      + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id")
              .params(
                  TenantContext.requireOrganizationId(),
                  v.title,
                  v.category,
                  v.authority,
                  v.description,
                  v.recurrence,
                  v.dueDate,
                  v.remindDays,
                  v.ownerId,
                  TenantContext.userId().orElse(null))
              .query(UUID.class)
              .single();
      audit.record(
          "compliance.item_created",
          "compliance_item",
          id.toString(),
          null,
          Map.of("title", v.title));
      return loadItem(id);
    });
  }

  public Item update(UUID id, ItemInput in) {
    entitlements.requireFeature(MODULE, "calendar");
    return tx.execute(s -> {
      Item before = loadItem(id);
      Validated v = validate(in, before);
      jdbc.sql(
              "update compliance_item set title = ?, category = ?, authority = ?, description = ?,"
                  + " recurrence = ?, due_date = ?, remind_days = ?, owner_id = ?, updated_at = now()"
                  + " where id = ?")
          .params(
              v.title,
              v.category,
              v.authority,
              v.description,
              v.recurrence,
              v.dueDate,
              v.remindDays,
              v.ownerId,
              id)
          .update();
      audit.record(
          "compliance.item_updated",
          "compliance_item",
          id.toString(),
          Map.of("dueDate", String.valueOf(before.dueDate())),
          Map.of("dueDate", String.valueOf(v.dueDate)));
      return loadItem(id);
    });
  }

  /** Archives (or restores) an item; its history and documents are kept. */
  public Item setActive(UUID id, boolean active) {
    entitlements.requireFeature(MODULE, "calendar");
    return tx.execute(s -> {
      loadItem(id);
      jdbc.sql("update compliance_item set active = ?, updated_at = now() where id = ?")
          .params(active, id)
          .update();
      audit.record(
          active ? "compliance.item_restored" : "compliance.item_archived",
          "compliance_item",
          id.toString(),
          null,
          null);
      return loadItem(id);
    });
  }

  /** Deletes an item and its history (e.g. added by mistake). Its documents stay in the vault. */
  public void delete(UUID id) {
    entitlements.requireFeature(MODULE, "calendar");
    tx.executeWithoutResult(s -> {
      Item item = loadItem(id);
      jdbc.sql("delete from compliance_item where id = ?").param(id).update();
      audit.record(
          "compliance.item_deleted",
          "compliance_item",
          id.toString(),
          Map.of("title", item.title()),
          null);
    });
  }

  /**
   * Marks the current cycle done. Recurring items move to the next due date after the one just
   * closed (skipping cycles already in the past); one-time items are closed.
   */
  public ItemDetail complete(UUID id, LocalDate completedOn, String note, UUID documentId) {
    entitlements.requireFeature(MODULE, "calendar");
    LocalDate on = completedOn == null ? today() : completedOn;
    if (on.isAfter(today())) {
      throw ApiException.badRequest("invalid_date", "The completion date cannot be in the future.");
    }
    String n = optional(note, 1000, "Note");
    tx.executeWithoutResult(s -> {
      Item item = loadItem(id);
      if (documentId != null) {
        requireDocument(documentId);
      }
      jdbc.sql(
              "insert into compliance_completion (organization_id, item_id, due_date, completed_on, note,"
                  + " document_id, completed_by) values (?, ?, ?, ?, ?, ?, ?)")
          .params(
              TenantContext.requireOrganizationId(),
              id,
              item.dueDate(),
              on,
              n,
              documentId,
              TenantContext.userId().orElse(null))
          .update();
      LocalDate next = nextDue(item.recurrence(), item.dueDate(), on);
      jdbc.sql("update compliance_item set due_date = ?, updated_at = now() where id = ?")
          .params(next, id)
          .update();
      if (documentId != null) {
        jdbc.sql("update compliance_document set item_id = coalesce(item_id, ?) where id = ?")
            .params(id, documentId)
            .update();
      }
      audit.record(
          "compliance.item_completed",
          "compliance_item",
          id.toString(),
          Map.of("dueDate", String.valueOf(item.dueDate())),
          Map.of("completedOn", on.toString(), "nextDue", String.valueOf(next)));
    });
    return item(id);
  }

  /** The next due date after closing {@code due} on {@code completedOn}. */
  static LocalDate nextDue(String recurrence, LocalDate due, LocalDate completedOn) {
    if ("ONCE".equals(recurrence)) {
      return null;
    }
    LocalDate base = due == null ? completedOn : due;
    LocalDate next = step(recurrence, base);
    while (!next.isAfter(completedOn)) {
      next = step(recurrence, next);
    }
    return next;
  }

  private static LocalDate step(String recurrence, LocalDate d) {
    return switch (recurrence) {
      case "MONTHLY" -> d.plusMonths(1);
      case "QUARTERLY" -> d.plusMonths(3);
      case "HALF_YEARLY" -> d.plusMonths(6);
      default -> d.plusYears(1);
    };
  }

  public List<Template> templates() {
    entitlements.requireFeature(MODULE, "calendar");
    return TEMPLATES;
  }

  /** Adds suggested items that the school doesn't have yet (matched by title), without dates. */
  public int addTemplates(List<String> keys) {
    entitlements.requireFeature(MODULE, "calendar");
    Set<String> wanted = new HashSet<>(keys == null ? List.of() : keys);
    return tx.execute(s -> {
      Set<String> existing =
          new HashSet<>(
              jdbc.sql("select lower(title) from compliance_item").query(String.class).list());
      int n = 0;
      for (Template t : TEMPLATES) {
        if (!wanted.contains(t.key()) || existing.contains(t.title().toLowerCase(Locale.ROOT))) {
          continue;
        }
        jdbc.sql(
                "insert into compliance_item (organization_id, title, category, authority, description,"
                    + " recurrence, created_by) values (?, ?, ?, ?, ?, ?, ?)")
            .params(
                TenantContext.requireOrganizationId(),
                t.title(),
                t.category(),
                t.authority(),
                t.description(),
                t.recurrence(),
                TenantContext.userId().orElse(null))
            .update();
        n++;
      }
      audit.record("compliance.templates_added", "compliance_item", null, null, Map.of("count", n));
      return n;
    });
  }

  /** Staff who can own items (school users with any role). */
  public List<Person> people() {
    UUID org = TenantContext.requireOrganizationId();
    return jdbc.sql(
            "select distinct u.id, u.display_name from role_assignment r join app_user u on u.id = r.user_id"
                + " where r.organization_id = ? and u.status = 'ACTIVE' order by u.display_name")
        .param(org)
        .query((rs, i) -> new Person(rs.getObject(1, UUID.class), rs.getString(2)))
        .list();
  }

  // ------------------------------------------------------------------ evidence vault

  public List<Document> documents() {
    entitlements.requireFeature(MODULE, "evidence_vault");
    return documentsWhere("true");
  }

  public Document upload(DocumentInput in, Upload file) {
    entitlements.requireFeature(MODULE, "evidence_vault");
    String title = required(in.title(), 160, "Title");
    String category =
        in.category() == null || in.category().isBlank()
            ? "OTHER"
            : oneOf(in.category(), CATEGORIES, "category");
    String ref = optional(in.referenceNo(), 120, "Reference number");
    checkDates(in.issuedOn(), in.expiresOn());
    if (in.itemId() != null) {
      loadItem(in.itemId());
    }
    MediaService.StoredFile f =
        media.storePrivate("compliance", file.filename(), file.content(), MAX_FILE_BYTES);
    try {
      return tx.execute(s -> {
        UUID id =
            jdbc.sql(
                    "insert into compliance_document (organization_id, item_id, title, category, reference_no,"
                        + " issued_on, expires_on, original_filename, content_type, size_bytes, sha256,"
                        + " storage_key, uploaded_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id")
                .params(
                    TenantContext.requireOrganizationId(),
                    in.itemId(),
                    title,
                    category,
                    ref,
                    in.issuedOn(),
                    in.expiresOn(),
                    f.filename(),
                    f.contentType(),
                    f.sizeBytes(),
                    f.sha256(),
                    f.storageKey(),
                    TenantContext.userId().orElse(null))
                .query(UUID.class)
                .single();
        audit.record(
            "compliance.document_uploaded",
            "compliance_document",
            id.toString(),
            null,
            Map.of("title", title, "expiresOn", String.valueOf(in.expiresOn())));
        return requireDocument(id);
      });
    } catch (RuntimeException e) {
      media.deletePrivate(List.of(f.storageKey()));
      throw e;
    }
  }

  public Document updateDocument(UUID id, DocumentInput in) {
    entitlements.requireFeature(MODULE, "evidence_vault");
    return tx.execute(s -> {
      Document before = requireDocument(id);
      String title = in.title() == null ? before.title() : required(in.title(), 160, "Title");
      String category =
          in.category() == null ? before.category() : oneOf(in.category(), CATEGORIES, "category");
      checkDates(in.issuedOn(), in.expiresOn());
      if (in.itemId() != null) {
        loadItem(in.itemId());
      }
      jdbc.sql(
              "update compliance_document set item_id = ?, title = ?, category = ?, reference_no = ?,"
                  + " issued_on = ?, expires_on = ? where id = ?")
          .params(
              in.itemId(),
              title,
              category,
              optional(in.referenceNo(), 120, "Reference number"),
              in.issuedOn(),
              in.expiresOn(),
              id)
          .update();
      audit.record("compliance.document_updated", "compliance_document", id.toString(), null, null);
      return requireDocument(id);
    });
  }

  public void deleteDocument(UUID id) {
    entitlements.requireFeature(MODULE, "evidence_vault");
    String key =
        tx.execute(s -> {
          Document d = requireDocument(id);
          String k =
              jdbc.sql("select storage_key from compliance_document where id = ?")
                  .param(id)
                  .query(String.class)
                  .single();
          jdbc.sql("delete from compliance_document where id = ?").param(id).update();
          audit.record(
              "compliance.document_deleted",
              "compliance_document",
              id.toString(),
              Map.of("title", d.title()),
              null);
          return k;
        });
    media.deletePrivate(List.of(key));
  }

  public String downloadUrl(UUID id) {
    entitlements.requireFeature(MODULE, "evidence_vault");
    record Key(String key, String name) {}
    Key k =
        jdbc.sql("select storage_key, original_filename from compliance_document where id = ?")
            .param(id)
            .query((rs, i) -> new Key(rs.getString(1), rs.getString(2)))
            .optional()
            .orElseThrow(() -> ApiException.notFound("Document"));
    return media.signedPrivateUrl(k.key(), k.name());
  }

  // ------------------------------------------------------------------ export pack (medium)

  /** Checks the plan and size before streaming starts, so errors are proper HTTP responses. */
  public void checkExport() {
    entitlements.requireFeature(MODULE, "export_pack");
    long bytes =
        jdbc.sql("select coalesce(sum(size_bytes), 0) from compliance_document")
            .query(Long.class)
            .single();
    if (bytes > MAX_EXPORT_BYTES) {
      throw ApiException.badRequest(
          "export_too_large", "The evidence vault is over 300 MB. Download documents by category.");
    }
  }

  /** Writes register.csv, documents.csv and every document (by category) as a ZIP. */
  public void writeExport(OutputStream out) throws IOException {
    entitlements.requireFeature(MODULE, "export_pack");
    List<Item> items = items(true);
    List<Document> docs = documentsWhere("true");
    try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
      StringBuilder reg =
          new StringBuilder(
              "Item,Category,Authority,Frequency,Next due,Status,Last completed,Owner,Documents,Archived\n");
      for (Item i : items) {
        reg.append(
                String.join(
                    ",",
                    cell(i.title()),
                    cell(i.category()),
                    cell(i.authority()),
                    cell(i.recurrence()),
                    cell(str(i.dueDate())),
                    cell(i.state().name()),
                    cell(str(i.lastCompletedOn())),
                    cell(i.ownerName()),
                    String.valueOf(i.documents()),
                    i.active() ? "" : "yes"))
            .append('\n');
      }
      put(zip, "register.csv", reg.toString().getBytes(StandardCharsets.UTF_8));

      StringBuilder hist = new StringBuilder("Item,Due date,Completed on,Note,By\n");
      jdbc.sql(
              "select i.title, c.due_date, c.completed_on, c.note, u.display_name from compliance_completion c"
                  + " join compliance_item i on i.id = c.item_id left join app_user u on u.id = c.completed_by"
                  + " order by i.title, c.completed_on desc")
          .query((rs, n) ->
              hist.append(
                      String.join(
                          ",",
                          cell(rs.getString(1)),
                          cell(str(date(rs, 2))),
                          cell(str(date(rs, 3))),
                          cell(rs.getString(4)),
                          cell(rs.getString(5))))
                  .append('\n'))
          .list();
      put(zip, "completions.csv", hist.toString().getBytes(StandardCharsets.UTF_8));

      StringBuilder list =
          new StringBuilder("File,Title,Category,Item,Reference,Issued,Expires,Status\n");
      Set<String> names = new HashSet<>();
      for (Document d : docs) {
        String ext =
            d.filename().contains(".") ? d.filename().substring(d.filename().lastIndexOf('.')) : "";
        String base = "documents/" + d.category().toLowerCase(Locale.ROOT) + "/" + safe(d.title());
        String name = base + ext;
        for (int k = 2; !names.add(name); k++) {
          name = base + "-" + k + ext;
        }
        String key =
            jdbc.sql("select storage_key from compliance_document where id = ?")
                .param(d.id())
                .query(String.class)
                .single();
        put(zip, name, media.readPrivate(key));
        list.append(
                String.join(
                    ",",
                    cell(name),
                    cell(d.title()),
                    cell(d.category()),
                    cell(d.itemTitle()),
                    cell(d.referenceNo()),
                    cell(str(d.issuedOn())),
                    cell(str(d.expiresOn())),
                    cell(d.expiry())))
            .append('\n');
      }
      put(zip, "documents.csv", list.toString().getBytes(StandardCharsets.UTF_8));
    }
    audit.record(
        "compliance.exported", "compliance_item", null, null, Map.of("documents", docs.size()));
  }

  private static void put(ZipOutputStream zip, String name, byte[] content) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(content);
    zip.closeEntry();
  }

  static String safe(String title) {
    String s = title.replaceAll("[^A-Za-z0-9 _-]", "").trim().replaceAll("\\s+", "-");
    if (s.isEmpty()) {
      s = "document";
    }
    return s.length() > 80 ? s.substring(0, 80) : s;
  }

  // ------------------------------------------------------------------ reminders

  record Reminder(UUID organizationId, UUID ownerId, String line) {}

  /**
   * Items due today, in 1 or 7 days, or at the item's reminder lead time; overdue items on Mondays;
   * documents expiring in 60, 30, 7 or 0 days. Runs as system across schools.
   */
  List<Reminder> dueReminders(LocalDate today) {
    List<Reminder> out = new ArrayList<>();
    boolean monday = today.getDayOfWeek() == java.time.DayOfWeek.MONDAY;
    jdbc.sql(
            "select i.organization_id, i.owner_id, i.title, i.due_date, i.remind_days from compliance_item i"
                + " join entitlement e on e.organization_id = i.organization_id and e.module_code = 'COMPLIANCE'"
                + "  and 'reminders' = any (e.features) and (e.expires_at is null or e.expires_at > now())"
                + " where i.active and i.due_date is not null and i.due_date <= ?")
        .param(today.plusDays(180))
        .query((rs, n) -> {
          LocalDate due = date(rs, 4);
          long days = ChronoUnit.DAYS.between(today, due);
          int lead = rs.getInt(5);
          String line = null;
          if (days < 0 && monday) {
            line = "OVERDUE since " + due + ": " + rs.getString(3);
          } else if (days == 0) {
            line = "Due TODAY: " + rs.getString(3);
          } else if (days == 1 || days == 7 || (days == lead && lead > 0)) {
            line =
                "Due in "
                    + days
                    + " day"
                    + (days == 1 ? "" : "s")
                    + " ("
                    + due
                    + "): "
                    + rs.getString(3);
          }
          if (line != null) {
            out.add(new Reminder(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), line));
          }
          return null;
        })
        .list();
    jdbc.sql(
            "select d.organization_id, d.title, d.expires_on from compliance_document d"
                + " join entitlement e on e.organization_id = d.organization_id and e.module_code = 'COMPLIANCE'"
                + "  and 'reminders' = any (e.features) and (e.expires_at is null or e.expires_at > now())"
                + " where d.expires_on in (?, ?, ?, ?)")
        .params(today, today.plusDays(7), today.plusDays(30), today.plusDays(60))
        .query((rs, n) -> {
          LocalDate exp = date(rs, 3);
          long days = ChronoUnit.DAYS.between(today, exp);
          out.add(
              new Reminder(
                  rs.getObject(1, UUID.class),
                  null,
                  (days == 0
                          ? "Certificate expires TODAY: "
                          : "Certificate expires in " + days + " days (" + exp + "): ")
                      + rs.getString(2)));
          return null;
        })
        .list();
    return out;
  }

  // ------------------------------------------------------------------ helpers

  private static final String ITEM_SELECT =
      "select i.*, u.display_name owner_name,"
          + " (select max(c.completed_on) from compliance_completion c where c.item_id = i.id) last_completed,"
          + " (select count(*) from compliance_document d where d.item_id = i.id) documents"
          + " from compliance_item i left join app_user u on u.id = i.owner_id";

  private Item loadItem(UUID id) {
    return jdbc.sql(ITEM_SELECT + " where i.id = ?")
        .param(id)
        .query(this::mapItem)
        .optional()
        .orElseThrow(() -> ApiException.notFound("Compliance item"));
  }

  private Item mapItem(ResultSet rs, int n) throws SQLException {
    LocalDate due = date(rs, "due_date");
    LocalDate last = date(rs, "last_completed");
    int remind = rs.getInt("remind_days");
    String recurrence = rs.getString("recurrence");
    LocalDate today = today();
    Long days = due == null ? null : ChronoUnit.DAYS.between(today, due);
    State state;
    if (due == null) {
      state = "ONCE".equals(recurrence) && last != null ? State.DONE : State.NO_DATE;
    } else if (days < 0) {
      state = State.OVERDUE;
    } else if (days <= remind) {
      state = State.DUE_SOON;
    } else {
      state = State.UPCOMING;
    }
    return new Item(
        rs.getObject("id", UUID.class),
        rs.getString("title"),
        rs.getString("category"),
        rs.getString("authority"),
        rs.getString("description"),
        recurrence,
        due,
        remind,
        rs.getObject("owner_id", UUID.class),
        rs.getString("owner_name"),
        rs.getBoolean("active"),
        state,
        days,
        last,
        rs.getInt("documents"));
  }

  private List<Document> documentsWhere(String where, Object... params) {
    LocalDate today = today();
    return jdbc.sql(
            "select d.*, i.title item_title, u.display_name uploaded_by_name from compliance_document d"
                + " left join compliance_item i on i.id = d.item_id left join app_user u on u.id = d.uploaded_by"
                + " where "
                + where
                + " order by d.expires_on nulls last, d.title")
        .params(List.of(params))
        .query((rs, n) -> {
          LocalDate exp = date(rs, "expires_on");
          Long days = exp == null ? null : ChronoUnit.DAYS.between(today, exp);
          String expiry =
              exp == null ? "NO_EXPIRY" : days < 0 ? "EXPIRED" : days <= 60 ? "EXPIRING" : "VALID";
          return new Document(
              rs.getObject("id", UUID.class),
              rs.getObject("item_id", UUID.class),
              rs.getString("item_title"),
              rs.getString("title"),
              rs.getString("category"),
              rs.getString("reference_no"),
              date(rs, "issued_on"),
              exp,
              rs.getString("original_filename"),
              rs.getString("content_type"),
              rs.getLong("size_bytes"),
              expiry,
              days,
              rs.getString("uploaded_by_name"),
              rs.getTimestamp("created_at").toInstant());
        })
        .list();
  }

  private Document requireDocument(UUID id) {
    List<Document> d = documentsWhere("d.id = ?", id);
    if (d.isEmpty()) {
      throw ApiException.notFound("Document");
    }
    return d.get(0);
  }

  private record Validated(
      String title,
      String category,
      String authority,
      String description,
      String recurrence,
      LocalDate dueDate,
      int remindDays,
      UUID ownerId) {}

  /** Validates an input; on edits ({@code before} set) a null field keeps the old value. */
  private Validated validate(ItemInput in, Item before) {
    boolean edit = before != null;
    String title = in.title() == null && edit ? before.title() : required(in.title(), 160, "Title");
    String category =
        in.category() == null
            ? (edit ? before.category() : "OTHER")
            : oneOf(in.category(), CATEGORIES, "category");
    String recurrence =
        in.recurrence() == null
            ? (edit ? before.recurrence() : "YEARLY")
            : oneOf(in.recurrence(), RECURRENCES, "frequency");
    String authority =
        in.authority() == null && edit
            ? before.authority()
            : optional(in.authority(), 120, "Authority");
    String description =
        in.description() == null && edit
            ? before.description()
            : optional(in.description(), 2000, "Description");
    LocalDate due = in.dueDate() == null && edit ? before.dueDate() : in.dueDate();
    if (due != null
        && (due.isBefore(today().minusYears(5)) || due.isAfter(today().plusYears(10)))) {
      throw ApiException.badRequest("invalid_date", "Check the due date.");
    }
    int remind = in.remindDays() == null ? (edit ? before.remindDays() : 30) : in.remindDays();
    if (remind < 0 || remind > 180) {
      throw ApiException.badRequest("invalid_remind", "Reminders can start 0 to 180 days before.");
    }
    UUID owner = in.ownerId() == null && edit ? before.ownerId() : in.ownerId();
    if (owner != null) {
      if (!entitlements.hasFeature(MODULE, "owners")) {
        throw ApiException.badRequest(
            "feature_not_in_plan", "Assigning owners is part of ShikshaBlox Standard.");
      }
      UUID o = owner;
      if (people().stream().noneMatch(p -> p.id().equals(o))) {
        throw ApiException.badRequest("invalid_owner", "Choose someone from your school.");
      }
    }
    return new Validated(title, category, authority, description, recurrence, due, remind, owner);
  }

  private static void checkDates(LocalDate issued, LocalDate expires) {
    if (issued != null && issued.isAfter(today())) {
      throw ApiException.badRequest("invalid_date", "The issue date cannot be in the future.");
    }
    if (issued != null && expires != null && expires.isBefore(issued)) {
      throw ApiException.badRequest("invalid_date", "The expiry date is before the issue date.");
    }
  }

  private static LocalDate date(ResultSet rs, int col) throws SQLException {
    Date d = rs.getDate(col);
    return d == null ? null : d.toLocalDate();
  }

  private static LocalDate date(ResultSet rs, String col) throws SQLException {
    Date d = rs.getDate(col);
    return d == null ? null : d.toLocalDate();
  }

  private static String str(Object o) {
    return o == null ? null : o.toString();
  }

  /** CSV cell, neutralising spreadsheet formulas. */
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

  private static String oneOf(String v, Set<String> allowed, String field) {
    if (v == null || !allowed.contains(v)) {
      throw ApiException.badRequest("invalid_" + field, "Unknown " + field + ".");
    }
    return v;
  }
}
