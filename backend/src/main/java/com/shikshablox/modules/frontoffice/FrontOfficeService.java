package com.shikshablox.modules.frontoffice;

import com.shikshablox.modules.admissions.AdmissionsService;
import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.entitlement.EntitlementService;
import com.shikshablox.platform.files.MediaService;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.TenantContext;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * M43 front office. Reception logs walk-ins and calls (an admission enquiry becomes a CRM lead in
 * one click), books appointments with staff, records inward/outward post and keeps a lost & found
 * register with photos.
 */
@Service
public class FrontOfficeService {

  static final String MODULE = "FRONT_OFFICE";
  static final ZoneId IST = ZoneId.of("Asia/Kolkata");
  static final long MAX_PHOTO_BYTES = 8L * 1024 * 1024;
  static final Set<String> KINDS = Set.of("WALK_IN", "CALL_IN", "CALL_OUT");
  static final Set<String> PURPOSES =
      Set.of("ADMISSION", "FEES", "MEET_STAFF", "DOCUMENTS", "COMPLAINT", "DELIVERY", "OTHER");
  static final Set<String> APPOINTMENT_STATUSES =
      Set.of("SCHEDULED", "ARRIVED", "DONE", "CANCELLED", "NO_SHOW");
  private static final Pattern PHONE = Pattern.compile("^[+0-9 \\-]{8,20}$");

  public record Entry(
      UUID id,
      String kind,
      String name,
      String phone,
      String purpose,
      String meetWith,
      int peopleCount,
      String notes,
      Instant inAt,
      Instant outAt,
      UUID leadId,
      String createdByName) {}

  public record EntryInput(
      String kind,
      String name,
      String phone,
      String purpose,
      String meetWith,
      Integer peopleCount,
      String notes) {}

  public record Appointment(
      UUID id,
      String visitorName,
      String phone,
      UUID staffId,
      String staffName,
      String meetWith,
      String purpose,
      Instant startsAt,
      int minutes,
      String status,
      String notes) {}

  public record AppointmentInput(
      String visitorName,
      String phone,
      UUID staffId,
      String meetWith,
      String purpose,
      Instant startsAt,
      Integer minutes,
      String notes) {}

  public record Post(
      UUID id,
      String direction,
      String counterparty,
      String addressee,
      String carrier,
      String trackingNo,
      String description,
      LocalDate loggedOn,
      String status,
      String handedTo,
      Instant closedAt) {}

  public record PostInput(
      String direction,
      String counterparty,
      String addressee,
      String carrier,
      String trackingNo,
      String description,
      LocalDate loggedOn) {}

  public record LostItem(
      UUID id,
      String description,
      String foundAt,
      LocalDate foundOn,
      boolean hasPhoto,
      String status,
      String returnedTo,
      Instant closedAt) {}

  public record Today(
      long walkIns,
      long calls,
      long inside,
      long appointments,
      long postToHandOver,
      long postToDispatch,
      long lostItems) {}

  public record Person(UUID id, String name) {}

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final AuditService audit;
  private final EntitlementService entitlements;
  private final MediaService media;
  private final AdmissionsService admissions;

  FrontOfficeService(
      JdbcClient jdbc,
      TransactionTemplate tx,
      AuditService audit,
      EntitlementService entitlements,
      MediaService media,
      AdmissionsService admissions) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.audit = audit;
    this.entitlements = entitlements;
    this.media = media;
    this.admissions = admissions;
  }

  static LocalDate today() {
    return LocalDate.now(IST);
  }

  private static Timestamp startOf(LocalDate d) {
    return Timestamp.from(d.atStartOfDay(IST).toInstant());
  }

  public Today summary() {
    entitlements.requireModule(MODULE);
    Timestamp from = startOf(today());
    Timestamp to = startOf(today().plusDays(1));
    return jdbc.sql(
            "select"
                + " (select count(*) from front_desk_entry where kind = 'WALK_IN' and in_at >= ? and in_at < ?),"
                + " (select count(*) from front_desk_entry where kind <> 'WALK_IN' and in_at >= ? and in_at < ?),"
                + " (select count(*) from front_desk_entry where kind = 'WALK_IN' and out_at is null and in_at >= ? and in_at < ?),"
                + " (select count(*) from front_desk_appointment where starts_at >= ? and starts_at < ? and status in ('SCHEDULED', 'ARRIVED')),"
                + " (select count(*) from front_desk_post where status = 'RECEIVED'),"
                + " (select count(*) from front_desk_post where status = 'READY'),"
                + " (select count(*) from front_desk_lost_item where status = 'FOUND')")
        .params(from, to, from, to, from, to, from, to)
        .query((rs, i) ->
            new Today(
                rs.getLong(1),
                rs.getLong(2),
                rs.getLong(3),
                rs.getLong(4),
                rs.getLong(5),
                rs.getLong(6),
                rs.getLong(7)))
        .single();
  }

  public List<Person> staff() {
    UUID org = TenantContext.requireOrganizationId();
    return TenantContext.callAsSystem(
        "front office staff",
        () ->
            jdbc.sql(
                    "select distinct u.id, u.display_name from role_assignment r join app_user u on u.id = r.user_id"
                        + " where r.organization_id = ? and u.status = 'ACTIVE' order by u.display_name")
                .param(org)
                .query((rs, i) -> new Person(rs.getObject(1, UUID.class), rs.getString(2)))
                .list());
  }

  // ------------------------------------------------------------------ register

  public List<Entry> entries(LocalDate day) {
    entitlements.requireFeature(MODULE, "register");
    LocalDate d = day == null ? today() : day;
    return jdbc.sql(
            ENTRY_SELECT + " where e.in_at >= ? and e.in_at < ? order by e.in_at desc limit 500")
        .params(startOf(d), startOf(d.plusDays(1)))
        .query(FrontOfficeService::mapEntry)
        .list();
  }

  public Entry addEntry(EntryInput in) {
    entitlements.requireFeature(MODULE, "register");
    String kind = in.kind() == null ? "WALK_IN" : oneOf(in.kind(), KINDS, "kind");
    String name = required(in.name(), 120, "Name");
    String phone = phone(in.phone(), false);
    String purpose = in.purpose() == null ? "OTHER" : oneOf(in.purpose(), PURPOSES, "purpose");
    int people = in.peopleCount() == null ? 1 : in.peopleCount();
    if (people < 1 || people > 50) {
      throw ApiException.badRequest("invalid_count", "Number of people must be 1 to 50.");
    }
    return tx.execute(s -> {
      UUID id =
          jdbc.sql(
                  "insert into front_desk_entry (organization_id, kind, name, phone, purpose, meet_with,"
                      + " people_count, notes, out_at, created_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                      + " returning id")
              .params(
                  TenantContext.requireOrganizationId(),
                  kind,
                  name,
                  phone,
                  purpose,
                  optional(in.meetWith(), 120, "Meeting"),
                  people,
                  optional(in.notes(), 1000, "Notes"),
                  // Calls have no "inside" time.
                  "WALK_IN".equals(kind) ? null : Timestamp.from(Instant.now()),
                  TenantContext.userId().orElse(null))
              .query(UUID.class)
              .single();
      audit.record(
          "frontoffice.entry_added", "front_desk_entry", id.toString(), null, Map.of("kind", kind));
      return entry(id);
    });
  }

  public Entry checkOut(UUID id) {
    entitlements.requireFeature(MODULE, "register");
    return tx.execute(s -> {
      entry(id);
      jdbc.sql("update front_desk_entry set out_at = coalesce(out_at, now()) where id = ?")
          .param(id)
          .update();
      return entry(id);
    });
  }

  /** Admission enquiry at the desk → a lead in the admissions CRM (when the plan has it). */
  public Entry handOffToAdmissions(UUID id, String studentName, String gradeSought) {
    entitlements.requireFeature(MODULE, "register");
    if (!entitlements.hasModule("ADMISSIONS_CRM")) {
      throw ApiException.badRequest(
          "module_not_in_plan", "The admissions CRM is not part of your plan.");
    }
    return tx.execute(s -> {
      Entry e = entry(id);
      if (e.leadId() != null) {
        return e;
      }
      if (e.phone() == null) {
        throw ApiException.badRequest(
            "missing_phone", "Add the visitor's phone number before sending to admissions.");
      }
      AdmissionsService.Lead lead =
          admissions.create(
              new AdmissionsService.LeadInput(
                  optional(studentName, 120, "Student name"),
                  e.name(),
                  e.phone(),
                  null,
                  optional(gradeSought, 60, "Class"),
                  null,
                  "WALK_IN".equals(e.kind()) ? "WALK_IN" : "PHONE",
                  null,
                  null,
                  Instant.now().plusSeconds(86400),
                  null,
                  e.notes() == null
                      ? "From the front office register"
                      : "Front office: " + e.notes()));
      jdbc.sql("update front_desk_entry set lead_id = ?, purpose = 'ADMISSION' where id = ?")
          .params(lead.id(), id)
          .update();
      audit.record(
          "frontoffice.handed_to_admissions",
          "front_desk_entry",
          id.toString(),
          null,
          Map.of("leadId", lead.id().toString()));
      return entry(id);
    });
  }

  public void deleteEntry(UUID id) {
    entitlements.requireFeature(MODULE, "register");
    tx.executeWithoutResult(s -> {
      entry(id);
      jdbc.sql("delete from front_desk_entry where id = ?").param(id).update();
      audit.record("frontoffice.entry_deleted", "front_desk_entry", id.toString(), null, null);
    });
  }

  // ------------------------------------------------------------------ appointments

  public List<Appointment> appointments(LocalDate from, LocalDate to) {
    entitlements.requireFeature(MODULE, "appointments");
    LocalDate f = from == null ? today() : from;
    LocalDate t = to == null ? f.plusDays(7) : to;
    if (t.isBefore(f) || t.isAfter(f.plusDays(62))) {
      throw ApiException.badRequest("invalid_range", "Choose up to two months.");
    }
    return jdbc.sql(
            APPOINTMENT_SELECT + " where a.starts_at >= ? and a.starts_at < ? order by a.starts_at")
        .params(startOf(f), startOf(t.plusDays(1)))
        .query(FrontOfficeService::mapAppointment)
        .list();
  }

  public Appointment book(AppointmentInput in) {
    entitlements.requireFeature(MODULE, "appointments");
    String visitor = required(in.visitorName(), 120, "Visitor name");
    String phone = phone(in.phone(), false);
    String with = optional(in.meetWith(), 120, "Meeting with");
    UUID staff = in.staffId();
    if (staff == null && with == null) {
      throw ApiException.badRequest("missing_field", "Choose who they are meeting.");
    }
    if (staff != null && staff().stream().noneMatch(p -> p.id().equals(staff))) {
      throw ApiException.badRequest("invalid_staff", "Choose someone from your school.");
    }
    if (in.startsAt() == null) {
      throw ApiException.badRequest("missing_field", "Date and time are required.");
    }
    if (in.startsAt().isBefore(Instant.now().minusSeconds(86400 * 2))) {
      throw ApiException.badRequest("invalid_time", "The appointment is in the past.");
    }
    int minutes = in.minutes() == null ? 15 : in.minutes();
    if (minutes < 5 || minutes > 240) {
      throw ApiException.badRequest("invalid_minutes", "Appointments last 5 to 240 minutes.");
    }
    if (staff != null) {
      Instant end = in.startsAt().plusSeconds(minutes * 60L);
      long clash =
          jdbc.sql(
                  "select count(*) from front_desk_appointment where staff_id = ? and status in ('SCHEDULED', 'ARRIVED')"
                      + " and starts_at < ? and starts_at + make_interval(mins => minutes) > ?")
              .params(staff, Timestamp.from(end), Timestamp.from(in.startsAt()))
              .query(Long.class)
              .single();
      if (clash > 0) {
        throw ApiException.conflict("slot_taken", "They already have an appointment at that time.");
      }
    }
    return tx.execute(s -> {
      UUID id =
          jdbc.sql(
                  "insert into front_desk_appointment (organization_id, visitor_name, phone, staff_id, meet_with,"
                      + " purpose, starts_at, minutes, notes, created_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                      + " returning id")
              .params(
                  TenantContext.requireOrganizationId(),
                  visitor,
                  phone,
                  staff,
                  with,
                  optional(in.purpose(), 300, "Purpose"),
                  Timestamp.from(in.startsAt()),
                  minutes,
                  optional(in.notes(), 1000, "Notes"),
                  TenantContext.userId().orElse(null))
              .query(UUID.class)
              .single();
      audit.record(
          "frontoffice.appointment_booked", "front_desk_appointment", id.toString(), null, null);
      return appointment(id);
    });
  }

  /** ARRIVED also adds the visitor to today's walk-in register. */
  public Appointment setAppointmentStatus(UUID id, String status) {
    entitlements.requireFeature(MODULE, "appointments");
    String to = oneOf(status, APPOINTMENT_STATUSES, "status");
    return tx.execute(s -> {
      Appointment a = appointment(id);
      jdbc.sql("update front_desk_appointment set status = ?, updated_at = now() where id = ?")
          .params(to, id)
          .update();
      if ("ARRIVED".equals(to) && !"ARRIVED".equals(a.status())) {
        jdbc.sql(
                "insert into front_desk_entry (organization_id, kind, name, phone, purpose, meet_with, notes,"
                    + " created_by) values (?, 'WALK_IN', ?, ?, 'MEET_STAFF', ?, ?, ?)")
            .params(
                TenantContext.requireOrganizationId(),
                a.visitorName(),
                a.phone(),
                a.staffName() != null ? a.staffName() : a.meetWith(),
                "Appointment" + (a.purpose() == null ? "" : ": " + a.purpose()),
                TenantContext.userId().orElse(null))
            .update();
      }
      audit.record(
          "frontoffice.appointment_status",
          "front_desk_appointment",
          id.toString(),
          Map.of("status", a.status()),
          Map.of("status", to));
      return appointment(id);
    });
  }

  // ------------------------------------------------------------------ post

  public List<Post> post(String direction, boolean openOnly) {
    entitlements.requireFeature(MODULE, "post");
    StringBuilder sql = new StringBuilder("select * from front_desk_post where true");
    List<Object> p = new ArrayList<>();
    if (direction != null && !direction.isBlank()) {
      sql.append(" and direction = ?");
      p.add(oneOf(direction, Set.of("INWARD", "OUTWARD"), "direction"));
    }
    if (openOnly) {
      sql.append(" and status in ('RECEIVED', 'READY')");
    }
    sql.append(" order by logged_on desc, created_at desc limit 500");
    return jdbc.sql(sql.toString()).params(p).query(FrontOfficeService::mapPost).list();
  }

  public Post logPost(PostInput in) {
    entitlements.requireFeature(MODULE, "post");
    String dir = oneOf(in.direction(), Set.of("INWARD", "OUTWARD"), "direction");
    LocalDate on = in.loggedOn() == null ? today() : in.loggedOn();
    if (on.isAfter(today())) {
      throw ApiException.badRequest("invalid_date", "The date cannot be in the future.");
    }
    return tx.execute(s -> {
      UUID id =
          jdbc.sql(
                  "insert into front_desk_post (organization_id, direction, counterparty, addressee, carrier,"
                      + " tracking_no, description, logged_on, status, created_by)"
                      + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id")
              .params(
                  TenantContext.requireOrganizationId(),
                  dir,
                  required(in.counterparty(), 160, "INWARD".equals(dir) ? "Sender" : "Recipient"),
                  optional(in.addressee(), 120, "INWARD".equals(dir) ? "For" : "From"),
                  optional(in.carrier(), 80, "Carrier"),
                  optional(in.trackingNo(), 80, "Tracking number"),
                  optional(in.description(), 300, "Description"),
                  on,
                  "INWARD".equals(dir) ? "RECEIVED" : "READY",
                  TenantContext.userId().orElse(null))
              .query(UUID.class)
              .single();
      audit.record(
          "frontoffice.post_logged",
          "front_desk_post",
          id.toString(),
          null,
          Map.of("direction", dir));
      return post(id);
    });
  }

  /** Inward: handed over to {@code person}. Outward: dispatched (optionally with tracking no). */
  public Post closePost(UUID id, String person, String trackingNo) {
    entitlements.requireFeature(MODULE, "post");
    return tx.execute(s -> {
      Post p = post(id);
      boolean inward = "INWARD".equals(p.direction());
      jdbc.sql(
              "update front_desk_post set status = ?, handed_to = ?, tracking_no = coalesce(?, tracking_no),"
                  + " closed_at = now() where id = ?")
          .params(
              inward ? "HANDED_OVER" : "DISPATCHED",
              inward ? required(person, 120, "Handed to") : optional(person, 120, "Sent by"),
              optional(trackingNo, 80, "Tracking number"),
              id)
          .update();
      audit.record("frontoffice.post_closed", "front_desk_post", id.toString(), null, null);
      return post(id);
    });
  }

  // ------------------------------------------------------------------ lost & found

  public List<LostItem> lostItems(boolean openOnly) {
    entitlements.requireFeature(MODULE, "lost_found");
    return jdbc.sql(
            "select * from front_desk_lost_item"
                + (openOnly ? " where status = 'FOUND'" : "")
                + " order by case when status = 'FOUND' then 0 else 1 end, found_on desc limit 500")
        .query(FrontOfficeService::mapLost)
        .list();
  }

  public LostItem addLostItem(
      String description, String foundAt, LocalDate foundOn, String photoName, byte[] photo) {
    entitlements.requireFeature(MODULE, "lost_found");
    String d = required(description, 300, "Description");
    LocalDate on = foundOn == null ? today() : foundOn;
    if (on.isAfter(today())) {
      throw ApiException.badRequest("invalid_date", "The date cannot be in the future.");
    }
    MediaService.StoredFile f =
        photo == null || photo.length == 0
            ? null
            : media.storePrivate("front-office/lost-found", photoName, photo, MAX_PHOTO_BYTES);
    try {
      return tx.execute(s -> {
        UUID id =
            jdbc.sql(
                    "insert into front_desk_lost_item (organization_id, description, found_at, found_on, photo_key,"
                        + " created_by) values (?, ?, ?, ?, ?, ?) returning id")
                .params(
                    TenantContext.requireOrganizationId(),
                    d,
                    optional(foundAt, 120, "Found at"),
                    on,
                    f == null ? null : f.storageKey(),
                    TenantContext.userId().orElse(null))
                .query(UUID.class)
                .single();
        audit.record(
            "frontoffice.lost_item_added", "front_desk_lost_item", id.toString(), null, null);
        return lostItem(id);
      });
    } catch (RuntimeException e) {
      if (f != null) {
        media.deletePrivate(List.of(f.storageKey()));
      }
      throw e;
    }
  }

  public LostItem closeLostItem(UUID id, String status, String returnedTo) {
    entitlements.requireFeature(MODULE, "lost_found");
    String to = oneOf(status, Set.of("RETURNED", "DISPOSED", "FOUND"), "status");
    return tx.execute(s -> {
      lostItem(id);
      jdbc.sql(
              "update front_desk_lost_item set status = ?, returned_to = ?,"
                  + " closed_at = case when ? = 'FOUND' then null else now() end where id = ?")
          .params(
              to, "RETURNED".equals(to) ? required(returnedTo, 160, "Returned to") : null, to, id)
          .update();
      audit.record(
          "frontoffice.lost_item_status",
          "front_desk_lost_item",
          id.toString(),
          null,
          Map.of("status", to));
      return lostItem(id);
    });
  }

  public String lostItemPhotoUrl(UUID id) {
    entitlements.requireFeature(MODULE, "lost_found");
    String key =
        jdbc.sql("select photo_key from front_desk_lost_item where id = ?")
            .param(id)
            .query(String.class)
            .optional()
            .orElse(null);
    if (key == null) {
      throw ApiException.notFound("Photo");
    }
    return media.signedPrivateUrl(key, "lost-item" + key.substring(key.lastIndexOf('.')));
  }

  /** Deletes a mistaken appointment, post entry or lost-item entry (and its photo). */
  public void delete(String what, UUID id) {
    String table =
        switch (what) {
          case "appointment" -> "front_desk_appointment";
          case "post" -> "front_desk_post";
          case "lost" -> "front_desk_lost_item";
          default -> throw ApiException.notFound("Record");
        };
    entitlements.requireFeature(
        MODULE,
        switch (what) {
          case "appointment" -> "appointments";
          case "post" -> "post";
          default -> "lost_found";
        });
    String photo =
        tx.execute(s -> {
          String key =
              "lost".equals(what)
                  ? jdbc.sql("select photo_key from front_desk_lost_item where id = ?")
                      .param(id)
                      .query(String.class)
                      .optional()
                      .orElse(null)
                  : null;
          int n = jdbc.sql("delete from " + table + " where id = ?").param(id).update();
          if (n == 0) {
            throw ApiException.notFound("Record");
          }
          audit.record("frontoffice." + what + "_deleted", table, id.toString(), null, null);
          return key;
        });
    if (photo != null) {
      media.deletePrivate(List.of(photo));
    }
  }

  // ------------------------------------------------------------------ helpers

  private static final String ENTRY_SELECT =
      "select e.*, u.display_name created_by_name from front_desk_entry e"
          + " left join app_user u on u.id = e.created_by";
  private static final String APPOINTMENT_SELECT =
      "select a.*, u.display_name staff_name from front_desk_appointment a"
          + " left join app_user u on u.id = a.staff_id";

  private Entry entry(UUID id) {
    return jdbc.sql(ENTRY_SELECT + " where e.id = ?")
        .param(id)
        .query(FrontOfficeService::mapEntry)
        .optional()
        .orElseThrow(() -> ApiException.notFound("Register entry"));
  }

  private Appointment appointment(UUID id) {
    return jdbc.sql(APPOINTMENT_SELECT + " where a.id = ?")
        .param(id)
        .query(FrontOfficeService::mapAppointment)
        .optional()
        .orElseThrow(() -> ApiException.notFound("Appointment"));
  }

  private Post post(UUID id) {
    return jdbc.sql("select * from front_desk_post where id = ?")
        .param(id)
        .query(FrontOfficeService::mapPost)
        .optional()
        .orElseThrow(() -> ApiException.notFound("Post"));
  }

  private LostItem lostItem(UUID id) {
    return jdbc.sql("select * from front_desk_lost_item where id = ?")
        .param(id)
        .query(FrontOfficeService::mapLost)
        .optional()
        .orElseThrow(() -> ApiException.notFound("Item"));
  }

  private static Entry mapEntry(ResultSet rs, int i) throws SQLException {
    return new Entry(
        rs.getObject("id", UUID.class),
        rs.getString("kind"),
        rs.getString("name"),
        rs.getString("phone"),
        rs.getString("purpose"),
        rs.getString("meet_with"),
        rs.getInt("people_count"),
        rs.getString("notes"),
        rs.getTimestamp("in_at").toInstant(),
        instant(rs.getTimestamp("out_at")),
        rs.getObject("lead_id", UUID.class),
        rs.getString("created_by_name"));
  }

  private static Appointment mapAppointment(ResultSet rs, int i) throws SQLException {
    return new Appointment(
        rs.getObject("id", UUID.class),
        rs.getString("visitor_name"),
        rs.getString("phone"),
        rs.getObject("staff_id", UUID.class),
        rs.getString("staff_name"),
        rs.getString("meet_with"),
        rs.getString("purpose"),
        rs.getTimestamp("starts_at").toInstant(),
        rs.getInt("minutes"),
        rs.getString("status"),
        rs.getString("notes"));
  }

  private static Post mapPost(ResultSet rs, int i) throws SQLException {
    return new Post(
        rs.getObject("id", UUID.class),
        rs.getString("direction"),
        rs.getString("counterparty"),
        rs.getString("addressee"),
        rs.getString("carrier"),
        rs.getString("tracking_no"),
        rs.getString("description"),
        date(rs.getDate("logged_on")),
        rs.getString("status"),
        rs.getString("handed_to"),
        instant(rs.getTimestamp("closed_at")));
  }

  private static LostItem mapLost(ResultSet rs, int i) throws SQLException {
    return new LostItem(
        rs.getObject("id", UUID.class),
        rs.getString("description"),
        rs.getString("found_at"),
        date(rs.getDate("found_on")),
        rs.getString("photo_key") != null,
        rs.getString("status"),
        rs.getString("returned_to"),
        instant(rs.getTimestamp("closed_at")));
  }

  private static Instant instant(Timestamp t) {
    return t == null ? null : t.toInstant();
  }

  private static LocalDate date(Date d) {
    return d == null ? null : d.toLocalDate();
  }

  static String phone(String v, boolean required) {
    if (v == null || v.isBlank()) {
      if (required) {
        throw ApiException.badRequest("missing_field", "Phone is required.");
      }
      return null;
    }
    String t = v.trim();
    if (!PHONE.matcher(t).matches() || t.replaceAll("\\D", "").length() < 8) {
      throw ApiException.badRequest("invalid_phone", "Enter a valid phone number.");
    }
    return t;
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
