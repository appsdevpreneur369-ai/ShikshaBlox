package com.shikshablox.modules.engagement;

import static com.shikshablox.modules.engagement.Inputs.optional;
import static com.shikshablox.modules.engagement.Inputs.required;

import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.entitlement.EntitlementService;
import com.shikshablox.platform.shared.RateLimiter;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.TenantContext;
import com.shikshablox.platform.tenancy.TenantResolver;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PTM days: the school sets a date, time window, slot length and hosts (teachers or desks); parents
 * book one slot per host on the website and get a code to cancel with. Staff see each host's
 * schedule and mark who attended.
 */
@Service
public class PtmService {

  static final String MODULE = "ENGAGEMENT";
  static final Set<String> BOOKING_STATUSES = Set.of("BOOKED", "CANCELLED", "ATTENDED", "NO_SHOW");

  public record HostInput(String name, String label, String room) {}

  public record EventInput(
      String title,
      LocalDate eventDate,
      LocalTime startTime,
      LocalTime endTime,
      Integer slotMinutes,
      String instructions,
      Boolean bookingOpen,
      List<HostInput> hosts) {}

  public record EventSummary(
      UUID id,
      String title,
      LocalDate eventDate,
      LocalTime startTime,
      LocalTime endTime,
      int slotMinutes,
      String instructions,
      boolean bookingOpen,
      int hosts,
      int slots,
      int booked) {}

  public record Booking(
      UUID id,
      LocalTime slotStart,
      String parentName,
      String phone,
      String studentName,
      String studentClass,
      String code,
      String status,
      String staffNote) {}

  public record Slot(LocalTime start, Booking booking) {}

  public record HostSchedule(UUID id, String name, String label, String room, List<Slot> slots) {}

  public record Schedule(EventSummary event, List<HostSchedule> hosts) {}

  /** Public view: free slot times only, no parent data. */
  public record PublicHost(UUID id, String name, String label, String room, List<LocalTime> free) {}

  public record PublicEvent(
      UUID id,
      String title,
      LocalDate eventDate,
      LocalTime startTime,
      LocalTime endTime,
      int slotMinutes,
      String instructions,
      List<PublicHost> hosts) {}

  public record BookRequest(
      UUID hostId,
      LocalTime slotStart,
      String parentName,
      String phone,
      String studentName,
      String studentClass) {}

  public record Booked(
      String code,
      String title,
      LocalDate eventDate,
      LocalTime slotStart,
      String hostName,
      String room) {}

  private final TenantResolver resolver;
  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final RateLimiter limiter;
  private final AuditService audit;
  private final EntitlementService entitlements;

  PtmService(
      TenantResolver resolver,
      JdbcClient jdbc,
      TransactionTemplate tx,
      RateLimiter limiter,
      AuditService audit,
      EntitlementService entitlements) {
    this.resolver = resolver;
    this.jdbc = jdbc;
    this.tx = tx;
    this.limiter = limiter;
    this.audit = audit;
    this.entitlements = entitlements;
  }

  /** Slot start times from start to end (a slot must end by the end time). */
  static List<LocalTime> slots(LocalTime start, LocalTime end, int minutes) {
    // Minutes of the day, so a slot ending at midnight is never mistaken for an early one.
    int from = start.toSecondOfDay() / 60;
    int to = end.toSecondOfDay() / 60;
    List<LocalTime> out = new ArrayList<>();
    for (int m = from; m + minutes <= to && out.size() < 200; m += minutes) {
      out.add(LocalTime.of(m / 60, m % 60));
    }
    return out;
  }

  // ------------------------------------------------------------------ staff

  public List<EventSummary> events() {
    entitlements.requireFeature(MODULE, "ptm");
    return jdbc.sql(
            EVENT_SELECT
                + " where e.event_date >= ? order by e.event_date desc, e.start_time limit 100")
        .param(Inputs.today().minusDays(90))
        .query(PtmService::mapEvent)
        .list();
  }

  public Schedule schedule(UUID eventId) {
    entitlements.requireFeature(MODULE, "ptm");
    EventSummary e = event(eventId);
    List<LocalTime> times = slots(e.startTime(), e.endTime(), e.slotMinutes());
    Map<UUID, Map<LocalTime, Booking>> booked = new HashMap<>();
    jdbc.sql(
            "select b.*, b.host_id from ptm_booking b join ptm_host h on h.id = b.host_id"
                + " where h.event_id = ? and b.status <> 'CANCELLED'")
        .param(eventId)
        .query((rs, i) -> {
          booked
              .computeIfAbsent(rs.getObject("host_id", UUID.class), k -> new HashMap<>())
              .put(rs.getObject("slot_start", LocalTime.class), mapBooking(rs));
          return null;
        })
        .list();
    List<HostSchedule> hosts =
        jdbc.sql("select * from ptm_host where event_id = ? order by sort_order, name")
            .param(eventId)
            .query((rs, i) -> {
              UUID id = rs.getObject("id", UUID.class);
              Map<LocalTime, Booking> mine = booked.getOrDefault(id, Map.of());
              return new HostSchedule(
                  id,
                  rs.getString("name"),
                  rs.getString("label"),
                  rs.getString("room"),
                  times.stream().map(t -> new Slot(t, mine.get(t))).toList());
            })
            .list();
    return new Schedule(e, hosts);
  }

  public EventSummary create(EventInput in) {
    entitlements.requireFeature(MODULE, "ptm");
    Valid v = validate(in, null);
    if (in.hosts() == null || in.hosts().isEmpty()) {
      throw ApiException.badRequest("missing_hosts", "Add at least one teacher or desk.");
    }
    return tx.execute(s -> {
      UUID id =
          jdbc.sql(
                  "insert into ptm_event (organization_id, title, event_date, start_time, end_time, slot_minutes,"
                      + " instructions, booking_open, created_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?) returning id")
              .params(
                  TenantContext.requireOrganizationId(),
                  v.title,
                  v.date,
                  v.start,
                  v.end,
                  v.minutes,
                  v.instructions,
                  v.open,
                  TenantContext.userId().orElse(null))
              .query(UUID.class)
              .single();
      addHosts(id, in.hosts());
      audit.record("ptm.event_created", "ptm_event", id.toString(), null, Map.of("title", v.title));
      return event(id);
    });
  }

  /**
   * Edits an event. Changing the time window or slot length is refused once parents have booked, so
   * no booking ends up outside the schedule.
   */
  public EventSummary update(UUID id, EventInput in) {
    entitlements.requireFeature(MODULE, "ptm");
    return tx.execute(s -> {
      EventSummary before = event(id);
      Valid v = validate(in, before);
      boolean timesChanged =
          !v.start.equals(before.startTime())
              || !v.end.equals(before.endTime())
              || v.minutes != before.slotMinutes()
              || !v.date.equals(before.eventDate());
      if (timesChanged && before.booked() > 0) {
        throw ApiException.conflict(
            "has_bookings",
            "Parents have already booked. Cancel their bookings before changing the date or times.");
      }
      jdbc.sql(
              "update ptm_event set title = ?, event_date = ?, start_time = ?, end_time = ?, slot_minutes = ?,"
                  + " instructions = ?, booking_open = ? where id = ?")
          .params(v.title, v.date, v.start, v.end, v.minutes, v.instructions, v.open, id)
          .update();
      if (in.hosts() != null && !in.hosts().isEmpty()) {
        addHosts(id, in.hosts());
      }
      audit.record("ptm.event_updated", "ptm_event", id.toString(), null, null);
      return event(id);
    });
  }

  public void removeHost(UUID hostId) {
    entitlements.requireFeature(MODULE, "ptm");
    tx.executeWithoutResult(s -> {
      long booked =
          jdbc.sql("select count(*) from ptm_booking where host_id = ? and status <> 'CANCELLED'")
              .param(hostId)
              .query(Long.class)
              .single();
      if (booked > 0) {
        throw ApiException.conflict(
            "has_bookings", "This teacher has bookings. Cancel them first.");
      }
      if (jdbc.sql("delete from ptm_host where id = ?").param(hostId).update() == 0) {
        throw ApiException.notFound("Teacher");
      }
      audit.record("ptm.host_removed", "ptm_host", hostId.toString(), null, null);
    });
  }

  public void deleteEvent(UUID id) {
    entitlements.requireFeature(MODULE, "ptm");
    tx.executeWithoutResult(s -> {
      EventSummary e = event(id);
      jdbc.sql("delete from ptm_event where id = ?").param(id).update();
      audit.record(
          "ptm.event_deleted", "ptm_event", id.toString(), Map.of("title", e.title()), null);
    });
  }

  public Booking setBookingStatus(UUID bookingId, String status, String note) {
    entitlements.requireFeature(MODULE, "ptm");
    String to = Inputs.oneOf(status, BOOKING_STATUSES, "status");
    return tx.execute(s -> {
      try {
        int n =
            jdbc.sql(
                    "update ptm_booking set status = ?, staff_note = coalesce(?, staff_note), updated_at = now() where id = ?")
                .params(to, optional(note, 1000, "Note"), bookingId)
                .update();
        if (n == 0) {
          throw ApiException.notFound("Booking");
        }
      } catch (DuplicateKeyException e) {
        throw ApiException.conflict("slot_taken", "Someone else has booked that slot since.");
      }
      audit.record(
          "ptm.booking_status", "ptm_booking", bookingId.toString(), null, Map.of("status", to));
      return jdbc.sql("select * from ptm_booking where id = ?")
          .param(bookingId)
          .query((rs, i) -> mapBooking(rs))
          .single();
    });
  }

  // ------------------------------------------------------------------ public

  public List<PublicEvent> publicEvents(String host) {
    Organization org = resolver.resolve(host).orElseThrow(() -> ApiException.notFound("School"));
    return TenantContext.callInTenant(
        org.id(),
        () -> {
          if (!entitlements.hasFeature(MODULE, "ptm")) {
            return List.<PublicEvent>of();
          }
          List<UUID> ids =
              jdbc.sql(
                      "select id from ptm_event where booking_open and event_date >= ? order by event_date, start_time limit 10")
                  .param(Inputs.today())
                  .query(UUID.class)
                  .list();
          return ids.stream().map(this::publicEvent).toList();
        });
  }

  private PublicEvent publicEvent(UUID id) {
    Schedule s = schedule(id);
    EventSummary e = s.event();
    // Today, only slots that haven't started yet.
    LocalTime now = LocalTime.now(Inputs.IST);
    boolean isToday = e.eventDate().equals(Inputs.today());
    return new PublicEvent(
        e.id(),
        e.title(),
        e.eventDate(),
        e.startTime(),
        e.endTime(),
        e.slotMinutes(),
        e.instructions(),
        s.hosts().stream()
            .map(h ->
                new PublicHost(
                    h.id(),
                    h.name(),
                    h.label(),
                    h.room(),
                    h.slots().stream()
                        .filter(sl -> sl.booking() == null && (!isToday || sl.start().isAfter(now)))
                        .map(Slot::start)
                        .toList()))
            .toList());
  }

  public Booked book(String host, UUID eventId, BookRequest in, String clientIp) {
    Organization org = resolver.resolve(host).orElseThrow(() -> ApiException.notFound("School"));
    String ip = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
    if (!limiter.tryAcquire("ptm:" + org.id() + ":" + ip, 10, Duration.ofMinutes(30))) {
      throw ApiException.tooManyRequests();
    }
    String parent = required(in.parentName(), 120, "Your name");
    String phone = Inputs.phone(in.phone());
    String student = required(in.studentName(), 120, "Student's name");
    String cls = optional(in.studentClass(), 60, "Class");
    if (in.hostId() == null || in.slotStart() == null) {
      throw ApiException.badRequest("missing_field", "Choose a teacher and a time.");
    }
    return TenantContext.callInTenant(
        org.id(),
        () -> {
          if (!entitlements.hasFeature(MODULE, "ptm")) {
            throw ApiException.notFound("PTM");
          }
          PublicEvent e = publicEvent(eventId);
          EventSummary ev = event(eventId);
          if (!ev.bookingOpen() || ev.eventDate().isBefore(Inputs.today())) {
            throw ApiException.conflict("closed", "Booking for this PTM is closed.");
          }
          PublicHost h =
              e.hosts().stream()
                  .filter(x -> x.id().equals(in.hostId()))
                  .findFirst()
                  .orElseThrow(() -> ApiException.notFound("Teacher"));
          if (!h.free().contains(in.slotStart())) {
            throw ApiException.conflict(
                "slot_taken", "That time has just been taken. Please choose another.");
          }
          long already =
              jdbc.sql(
                      "select count(*) from ptm_booking where host_id = ? and status = 'BOOKED'"
                          + " and right(regexp_replace(phone, '\\D', '', 'g'), 10) = ?")
                  .params(h.id(), Inputs.phoneKey(phone))
                  .query(Long.class)
                  .single();
          if (already > 0) {
            throw ApiException.conflict(
                "already_booked",
                "You already have a slot with this teacher. Cancel it first to change the time.");
          }
          for (int attempt = 0; ; attempt++) {
            String code = Inputs.code(6);
            try {
              tx.executeWithoutResult(s -> {
                jdbc.sql(
                        "insert into ptm_booking (organization_id, host_id, slot_start, parent_name, phone, student_name,"
                            + " student_class, code) values (?, ?, ?, ?, ?, ?, ?, ?)")
                    .params(org.id(), h.id(), in.slotStart(), parent, phone, student, cls, code)
                    .update();
                audit.record(
                    "ptm.booked",
                    "ptm_event",
                    eventId.toString(),
                    null,
                    Map.of("slot", in.slotStart().toString()));
              });
              return new Booked(code, e.title(), e.eventDate(), in.slotStart(), h.name(), h.room());
            } catch (DuplicateKeyException ex) {
              // Either the slot was taken a moment ago or (rarely) the code collided.
              boolean taken =
                  jdbc.sql(
                              "select count(*) from ptm_booking where host_id = ? and slot_start = ? and status <> 'CANCELLED'")
                          .params(h.id(), in.slotStart())
                          .query(Long.class)
                          .single()
                      > 0;
              if (taken || attempt >= 3) {
                throw ApiException.conflict(
                    "slot_taken", "That time has just been taken. Please choose another.");
              }
            }
          }
        });
  }

  /** Parent cancels with the booking code and the phone number used to book. */
  public void cancel(String host, String code, String phone, String clientIp) {
    Organization org = resolver.resolve(host).orElseThrow(() -> ApiException.notFound("School"));
    String ip = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
    if (!limiter.tryAcquire("ptm-cancel:" + org.id() + ":" + ip, 10, Duration.ofMinutes(30))) {
      throw ApiException.tooManyRequests();
    }
    String c = required(code, 12, "Booking code").toUpperCase(java.util.Locale.ROOT);
    String key = Inputs.phoneKey(Inputs.phone(phone));
    TenantContext.callInTenant(
        org.id(),
        () ->
            tx.execute(s -> {
              int n =
                  jdbc.sql(
                          "update ptm_booking set status = 'CANCELLED', updated_at = now() where code = ? and status = 'BOOKED'"
                              + " and right(regexp_replace(phone, '\\D', '', 'g'), 10) = ?")
                      .params(c, key)
                      .update();
              if (n == 0) {
                throw ApiException.notFound("Booking");
              }
              audit.record("ptm.cancelled_by_parent", "ptm_booking", c, null, null);
              return null;
            }));
  }

  // ------------------------------------------------------------------ helpers

  private static final String EVENT_SELECT =
      "select e.*, (select count(*) from ptm_host h where h.event_id = e.id) hosts,"
          + " (select count(*) from ptm_booking b join ptm_host h on h.id = b.host_id"
          + "   where h.event_id = e.id and b.status <> 'CANCELLED') booked from ptm_event e";

  private EventSummary event(UUID id) {
    return jdbc.sql(EVENT_SELECT + " where e.id = ?")
        .param(id)
        .query(PtmService::mapEvent)
        .optional()
        .orElseThrow(() -> ApiException.notFound("PTM"));
  }

  private void addHosts(UUID eventId, List<HostInput> hosts) {
    if (hosts.size() > 80) {
      throw ApiException.badRequest("too_many", "Up to 80 teachers per PTM.");
    }
    int order =
        jdbc.sql("select coalesce(max(sort_order), -1) + 1 from ptm_host where event_id = ?")
            .param(eventId)
            .query(Integer.class)
            .single();
    for (HostInput h : hosts) {
      jdbc.sql(
              "insert into ptm_host (organization_id, event_id, name, label, room, sort_order) values (?, ?, ?, ?, ?, ?)")
          .params(
              TenantContext.requireOrganizationId(),
              eventId,
              required(h.name(), 120, "Teacher name"),
              optional(h.label(), 120, "Class / subject"),
              optional(h.room(), 60, "Room"),
              order++)
          .update();
    }
  }

  private record Valid(
      String title,
      LocalDate date,
      LocalTime start,
      LocalTime end,
      int minutes,
      String instructions,
      boolean open) {}

  private static Valid validate(EventInput in, EventSummary before) {
    boolean edit = before != null;
    String title = in.title() == null && edit ? before.title() : required(in.title(), 160, "Title");
    LocalDate date = in.eventDate() == null && edit ? before.eventDate() : in.eventDate();
    LocalTime start = in.startTime() == null && edit ? before.startTime() : in.startTime();
    LocalTime end = in.endTime() == null && edit ? before.endTime() : in.endTime();
    int minutes = in.slotMinutes() == null ? (edit ? before.slotMinutes() : 10) : in.slotMinutes();
    if (date == null || start == null || end == null) {
      throw ApiException.badRequest("missing_field", "Date, start and end time are required.");
    }
    if (!edit && date.isBefore(Inputs.today())) {
      throw ApiException.badRequest("invalid_date", "The PTM date is in the past.");
    }
    if (!end.isAfter(start)) {
      throw ApiException.badRequest("invalid_time", "The end time must be after the start time.");
    }
    if (minutes < 5 || minutes > 60) {
      throw ApiException.badRequest("invalid_slot", "Slots must be 5 to 60 minutes.");
    }
    if (slots(start, end, minutes).isEmpty()) {
      throw ApiException.badRequest("invalid_time", "The time window is shorter than one slot.");
    }
    String instructions =
        in.instructions() == null && edit
            ? before.instructions()
            : optional(in.instructions(), 1000, "Instructions");
    boolean open = in.bookingOpen() == null ? (!edit || before.bookingOpen()) : in.bookingOpen();
    return new Valid(title, date, start, end, minutes, instructions, open);
  }

  private static EventSummary mapEvent(ResultSet rs, int i) throws SQLException {
    LocalTime start = rs.getObject("start_time", LocalTime.class);
    LocalTime end = rs.getObject("end_time", LocalTime.class);
    int minutes = rs.getInt("slot_minutes");
    int hosts = rs.getInt("hosts");
    return new EventSummary(
        rs.getObject("id", UUID.class),
        rs.getString("title"),
        rs.getObject("event_date", LocalDate.class),
        start,
        end,
        minutes,
        rs.getString("instructions"),
        rs.getBoolean("booking_open"),
        hosts,
        hosts * slots(start, end, minutes).size(),
        rs.getInt("booked"));
  }

  private static Booking mapBooking(ResultSet rs) throws SQLException {
    return new Booking(
        rs.getObject("id", UUID.class),
        rs.getObject("slot_start", LocalTime.class),
        rs.getString("parent_name"),
        rs.getString("phone"),
        rs.getString("student_name"),
        rs.getString("student_class"),
        rs.getString("code"),
        rs.getString("status"),
        rs.getString("staff_note"));
  }
}
