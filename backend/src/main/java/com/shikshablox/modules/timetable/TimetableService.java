package com.shikshablox.modules.timetable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.entitlement.EntitlementService;
import com.shikshablox.platform.identity.CurrentUser;
import com.shikshablox.platform.identity.Permission;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.TenantContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Timetable (M08). The grid is per section of the current year; the database refuses a teacher or
 * room in two places in the same period, and the service turns that into a clear message.
 * Substitutions are per date: the office marks teachers away, sees their periods and picks a
 * substitute from teachers free in that period (fewest periods that day first).
 */
@Service
public class TimetableService {

  static final String MODULE = "TIMETABLE";
  static final ZoneId IST = ZoneId.of("Asia/Kolkata");
  static final String[] DAY_NAMES = {
    "", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
  };
  private static final TypeReference<List<Period>> PERIODS = new TypeReference<>() {};

  public record Period(
      String label, String start, String end, @JsonProperty("break") boolean breakTime) {}

  public record Settings(
      List<Integer> days,
      List<Period> periods,
      int maxSamePerDay,
      boolean generator,
      boolean substitutions,
      boolean canManage) {}

  public record SettingsInput(List<Integer> days, List<Period> periods, Integer maxSamePerDay) {}

  public record Slot(
      UUID id,
      UUID sectionId,
      String section,
      int day,
      int period,
      UUID subjectId,
      String subject,
      String label,
      UUID teacherId,
      String teacher,
      String room) {}

  public record SlotInput(
      int day, int period, UUID subjectId, String label, UUID teacherId, String room) {}

  public record Staff(UUID id, String name) {}

  public record SectionRef(UUID id, String label, String grade) {}

  public record LoadRow(
      UUID subjectId,
      String subject,
      int periodsPerWeek,
      UUID teacherId,
      String teacher,
      int scheduled) {}

  public record LoadInput(UUID subjectId, int periodsPerWeek, UUID teacherId) {}

  public record Grid(
      SectionRef section, Settings settings, List<Slot> slots, List<LoadRow> loads) {}

  public record Overview(Settings settings, List<SectionRef> sections, List<Staff> staff) {}

  public record GenerateResult(int placed, List<String> unplaced) {}

  public record Absence(UUID userId, String name, String note) {}

  public record Suggestion(UUID id, String name, int periodsToday) {}

  public record Need(
      UUID sectionId,
      String section,
      int period,
      String periodLabel,
      String subject,
      UUID absentTeacherId,
      String absentTeacher,
      UUID substituteId,
      String substitute,
      List<Suggestion> suggestions) {}

  public record SubDay(LocalDate date, String weekday, List<Absence> absences, List<Need> needs) {}

  public record ChildTimetable(
      UUID studentId,
      String name,
      String classLabel,
      List<Integer> days,
      List<Period> periods,
      List<Slot> slots,
      List<Slot> substitutionsToday) {}

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final AuditService audit;
  private final EntitlementService entitlements;
  private final ObjectMapper json;

  TimetableService(
      JdbcClient jdbc,
      TransactionTemplate tx,
      AuditService audit,
      @org.springframework.context.annotation.Lazy EntitlementService entitlements,
      ObjectMapper json) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.audit = audit;
    this.entitlements = entitlements;
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
            u.roles().stream().anyMatch(r -> r.permissions().contains(Permission.TIMETABLE_MANAGE)))
        .orElse(false);
  }

  private static void requireManage() {
    if (!canManage()) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "forbidden", "Only the timetable in-charge can do that.");
    }
  }

  private void requireFeature(String f, String what) {
    if (!feature(f)) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "feature_not_in_plan", what + " is part of ShikshaBlox Standard.");
    }
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

  // ------------------------------------------------------------------ settings

  Settings loadSettings() {
    jdbc.sql("insert into timetable_settings (organization_id) values (?) on conflict do nothing")
        .param(TenantContext.requireOrganizationId())
        .update();
    return jdbc.sql("select days, periods::text, max_same_subject_per_day from timetable_settings")
        .query((rs, i) ->
            new Settings(
                List.of((Integer[]) rs.getArray(1).getArray()),
                readPeriods(rs.getString(2)),
                rs.getInt(3),
                feature("generator"),
                feature("substitutions"),
                canManage()))
        .single();
  }

  public Overview overview() {
    requireModule();
    return new Overview(loadSettings(), sections(), canManage() ? staff() : List.of());
  }

  public Settings saveSettings(SettingsInput in) {
    requireModule();
    requireManage();
    Settings b = loadSettings();
    List<Integer> days =
        in.days() == null ? b.days() : List.copyOf(new java.util.TreeSet<>(in.days()));
    if (days.isEmpty() || days.stream().anyMatch(d -> d < 1 || d > 7)) {
      throw ApiException.badRequest("invalid_days", "Choose the school's working days.");
    }
    List<Period> periods = in.periods() == null ? b.periods() : cleanPeriods(in.periods());
    int max = in.maxSamePerDay() == null ? b.maxSamePerDay() : in.maxSamePerDay();
    if (max < 1 || max > 4) {
      throw ApiException.badRequest("invalid_number", "A subject may appear 1 to 4 times a day.");
    }
    long beyond =
        jdbc.sql(
                "select count(*) from timetable_slot where period >= ? or not (day = any(?::int[]))")
            .params(periods.size(), days.toArray(Integer[]::new))
            .query(Long.class)
            .single();
    if (beyond > 0) {
      throw ApiException.conflict(
          "slots_outside",
          beyond + " timetable entries fall outside the new days or periods. Clear them first.");
    }
    tx.executeWithoutResult(s -> {
      jdbc.sql(
              "update timetable_settings set days = ?, periods = ?::jsonb, max_same_subject_per_day = ?, updated_at = now()")
          .params(days.toArray(Integer[]::new), write(periods), max)
          .update();
      audit.record(
          "timetable.settings_saved",
          "timetable_settings",
          null,
          null,
          Map.of("periods", periods.size()));
    });
    return loadSettings();
  }

  static List<Period> cleanPeriods(List<Period> in) {
    if (in.isEmpty() || in.size() > 16) {
      throw ApiException.badRequest("invalid_periods", "Use 1 to 16 rows in the bell schedule.");
    }
    List<Period> out = new ArrayList<>();
    String last = null;
    for (Period p : in) {
      String label = p.label() == null ? "" : p.label().trim();
      if (label.isEmpty() || label.length() > 30) {
        throw ApiException.badRequest(
            "invalid_periods", "Give each row a name (up to 30 characters).");
      }
      if (p.start() == null
          || p.end() == null
          || !p.start().matches("^\\d{2}:\\d{2}$")
          || !p.end().matches("^\\d{2}:\\d{2}$")
          || p.end().compareTo(p.start()) <= 0) {
        throw ApiException.badRequest(
            "invalid_periods", label + ": give a start and end time (HH:MM), end after start.");
      }
      if (last != null && p.start().compareTo(last) < 0) {
        throw ApiException.badRequest(
            "invalid_periods", label + " starts before the previous row ends.");
      }
      last = p.end();
      out.add(new Period(label, p.start(), p.end(), p.breakTime()));
    }
    if (out.stream().allMatch(Period::breakTime)) {
      throw ApiException.badRequest("invalid_periods", "Keep at least one teaching period.");
    }
    return out;
  }

  // ------------------------------------------------------------------ grid

  List<SectionRef> sections() {
    return jdbc.sql(
            "select id, grade, section from class_section where academic_year = ? order by grade_order, grade, section")
        .param(currentYear())
        .query((rs, i) ->
            new SectionRef(
                rs.getObject(1, UUID.class),
                rs.getString(2).trim() + " " + rs.getString(3).trim(),
                rs.getString(2)))
        .list();
  }

  List<Staff> staff() {
    return jdbc.sql(
            "select distinct u.id, u.display_name from role_assignment r join app_user u on u.id = r.user_id"
                + " where r.organization_id = ? and r.role in ('SCHOOL_ADMIN', 'TEACHER', 'EXAM_CELL') and u.status = 'ACTIVE'"
                + " order by u.display_name")
        .param(TenantContext.requireOrganizationId())
        .query((rs, i) -> new Staff(rs.getObject(1, UUID.class), rs.getString(2)))
        .list();
  }

  private static final String SLOTS =
      "select t.*, c.grade, c.section, coalesce(s.name, t.label) subject_label, u.display_name teacher_name"
          + " from timetable_slot t join class_section c on c.id = t.class_section_id"
          + " left join subject s on s.id = t.subject_id left join app_user u on u.id = t.teacher_id";

  public Grid grid(UUID sectionId) {
    requireModule();
    SectionRef sec =
        sections().stream()
            .filter(s -> s.id().equals(sectionId))
            .findFirst()
            .orElseThrow(() -> ApiException.notFound("Class"));
    List<Slot> slots =
        jdbc.sql(SLOTS + " where t.class_section_id = ? order by t.day, t.period")
            .param(sectionId)
            .query((rs, i) -> mapSlot(rs))
            .list();
    return new Grid(sec, loadSettings(), slots, canManage() ? loads(sec) : List.of());
  }

  /** A teacher's week across sections (own timetable, or any teacher's for the office). */
  public List<Slot> teacher(UUID teacherId) {
    requireModule();
    UUID me = TenantContext.userId().orElse(null);
    UUID who = teacherId == null ? me : teacherId;
    if (!canManage() && !java.util.Objects.equals(who, me)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "You can see your own timetable.");
    }
    return jdbc.sql(SLOTS + " where t.teacher_id = ? order by t.day, t.period")
        .param(who)
        .query((rs, i) -> mapSlot(rs))
        .list();
  }

  public Slot setSlot(UUID sectionId, SlotInput in) {
    requireModule();
    requireManage();
    Settings st = loadSettings();
    if (!st.days().contains(in.day())
        || in.period() < 0
        || in.period() >= st.periods().size()
        || st.periods().get(in.period()).breakTime()) {
      throw ApiException.badRequest("invalid_slot", "Choose a teaching period on a working day.");
    }
    sections().stream()
        .filter(s -> s.id().equals(sectionId))
        .findFirst()
        .orElseThrow(() -> ApiException.notFound("Class"));
    String label = in.label() == null || in.label().isBlank() ? null : in.label().trim();
    if (label != null && label.length() > 60) {
      throw ApiException.badRequest("too_long", "Keep the label under 60 characters.");
    }
    if (in.subjectId() == null && label == null) {
      throw ApiException.badRequest("missing_field", "Choose a subject or give the period a name.");
    }
    String room = in.room() == null || in.room().isBlank() ? null : in.room().trim();
    if (room != null && room.length() > 30) {
      throw ApiException.badRequest("too_long", "Keep the room under 30 characters.");
    }
    if (in.teacherId() != null && staff().stream().noneMatch(s -> s.id().equals(in.teacherId()))) {
      throw ApiException.badRequest("invalid_teacher", "Choose a teacher of this school.");
    }
    clashCheck(sectionId, in.day(), in.period(), in.teacherId(), room);
    try {
      UUID id =
          tx.execute(s -> {
            UUID x =
                jdbc.sql(
                        "insert into timetable_slot (organization_id, class_section_id, day, period, subject_id, label, teacher_id, room)"
                            + " values (?, ?, ?, ?, ?, ?, ?, ?) on conflict (class_section_id, day, period) do update set"
                            + " subject_id = excluded.subject_id, label = excluded.label, teacher_id = excluded.teacher_id, room = excluded.room"
                            + " returning id")
                    .params(
                        TenantContext.requireOrganizationId(),
                        sectionId,
                        in.day(),
                        in.period(),
                        in.subjectId(),
                        label,
                        in.teacherId(),
                        room)
                    .query(UUID.class)
                    .single();
            audit.record(
                "timetable.slot_set",
                "timetable_slot",
                x.toString(),
                null,
                Map.of("day", in.day(), "period", in.period()));
            return x;
          });
      return jdbc.sql(SLOTS + " where t.id = ?").param(id).query((rs, i) -> mapSlot(rs)).single();
    } catch (DuplicateKeyException e) {
      clashCheck(sectionId, in.day(), in.period(), in.teacherId(), room);
      throw ApiException.conflict("clash", "That teacher or room is already busy in this period.");
    }
  }

  /** Explains a clash in words: who or what is already busy, and where. */
  private void clashCheck(UUID sectionId, int day, int period, UUID teacherId, String room) {
    String where = DAY_NAMES[day] + ", " + loadSettings().periods().get(period).label();
    if (teacherId != null) {
      jdbc.sql(
              SLOTS
                  + " where t.teacher_id = ? and t.day = ? and t.period = ? and t.class_section_id <> ?")
          .params(teacherId, day, period, sectionId)
          .query((rs, i) -> mapSlot(rs))
          .optional()
          .ifPresent(s -> {
            throw ApiException.conflict(
                "teacher_busy",
                s.teacher() + " already teaches " + s.section() + " on " + where + ".");
          });
    }
    if (room != null) {
      jdbc.sql(
              SLOTS
                  + " where lower(t.room) = lower(?) and t.day = ? and t.period = ? and t.class_section_id <> ?")
          .params(room, day, period, sectionId)
          .query((rs, i) -> mapSlot(rs))
          .optional()
          .ifPresent(s -> {
            throw ApiException.conflict(
                "room_busy", room + " is used by " + s.section() + " on " + where + ".");
          });
    }
  }

  public void clearSlot(UUID sectionId, int day, int period) {
    requireModule();
    requireManage();
    jdbc.sql("delete from timetable_slot where class_section_id = ? and day = ? and period = ?")
        .params(sectionId, day, period)
        .update();
    audit.record(
        "timetable.slot_cleared",
        "class_section",
        sectionId.toString(),
        null,
        Map.of("day", day, "period", period));
  }

  // ------------------------------------------------------------------ loads & generator

  private List<LoadRow> loads(SectionRef sec) {
    Map<UUID, Integer> scheduled = new HashMap<>();
    jdbc.sql(
            "select subject_id, count(*) from timetable_slot where class_section_id = ? and subject_id is not null group by 1")
        .param(sec.id())
        .query((rs, i) -> scheduled.put(rs.getObject(1, UUID.class), rs.getInt(2)))
        .list();
    return jdbc.sql(
            "select s.id, s.name, coalesce(l.periods_per_week, 0), coalesce(l.teacher_id,"
                + " (select st.user_id from subject_teacher st where st.class_section_id = ? and st.subject_id = s.id limit 1)), u.display_name"
                + " from subject s left join timetable_load l on l.subject_id = s.id and l.class_section_id = ?"
                + " left join app_user u on u.id = coalesce(l.teacher_id,"
                + "   (select st.user_id from subject_teacher st where st.class_section_id = ? and st.subject_id = s.id limit 1))"
                + " where s.academic_year = ? and s.grade = ? and s.kind = 'SCHOLASTIC' order by s.sort_order, s.name")
        .params(sec.id(), sec.id(), sec.id(), currentYear(), sec.grade())
        .query((rs, i) ->
            new LoadRow(
                rs.getObject(1, UUID.class),
                rs.getString(2),
                rs.getInt(3),
                rs.getObject(4, UUID.class),
                rs.getString(5),
                scheduled.getOrDefault(rs.getObject(1, UUID.class), 0)))
        .list();
  }

  public Grid saveLoads(UUID sectionId, List<LoadInput> in) {
    requireModule();
    requireManage();
    Grid g = grid(sectionId);
    Set<UUID> subjects = new HashSet<>();
    g.loads().forEach(l -> subjects.add(l.subjectId()));
    Set<UUID> teachers = new HashSet<>();
    staff().forEach(s -> teachers.add(s.id()));
    tx.executeWithoutResult(s -> {
      for (LoadInput l : in == null ? List.<LoadInput>of() : in) {
        if (l == null || !subjects.contains(l.subjectId())) {
          throw ApiException.badRequest("invalid_subject", "A subject is not part of this class.");
        }
        if (l.periodsPerWeek() < 0 || l.periodsPerWeek() > 20) {
          throw ApiException.badRequest("invalid_number", "Periods a week must be 0 to 20.");
        }
        if (l.teacherId() != null && !teachers.contains(l.teacherId())) {
          throw ApiException.badRequest("invalid_teacher", "Choose a teacher of this school.");
        }
        jdbc.sql(
                "insert into timetable_load (organization_id, class_section_id, subject_id, periods_per_week, teacher_id) values (?, ?, ?, ?, ?)"
                    + " on conflict (class_section_id, subject_id) do update set periods_per_week = excluded.periods_per_week, teacher_id = excluded.teacher_id")
            .params(
                TenantContext.requireOrganizationId(),
                sectionId,
                l.subjectId(),
                l.periodsPerWeek(),
                l.teacherId())
            .update();
      }
      audit.record("timetable.loads_saved", "class_section", sectionId.toString(), null, null);
    });
    return grid(sectionId);
  }

  /** Fills the empty periods of the chosen sections from their loads; existing entries stay. */
  public GenerateResult generate(List<UUID> sectionIds) {
    requireModule();
    requireManage();
    requireFeature("generator", "The timetable generator");
    Settings st = loadSettings();
    List<SectionRef> all = sections();
    List<SectionRef> chosen =
        all.stream().filter(s -> sectionIds != null && sectionIds.contains(s.id())).toList();
    if (chosen.isEmpty()) {
      throw ApiException.badRequest("missing_field", "Choose the classes to fill.");
    }
    List<Generator.Load> loads = new ArrayList<>();
    Map<UUID, String> subjectNames = new HashMap<>();
    Map<UUID, String> sectionNames = new HashMap<>();
    for (SectionRef s : chosen) {
      sectionNames.put(s.id(), s.label());
      for (LoadRow l : loads(s)) {
        subjectNames.put(l.subjectId(), l.subject());
        if (l.periodsPerWeek() > 0) {
          loads.add(new Generator.Load(s.id(), l.subjectId(), l.teacherId(), l.periodsPerWeek()));
        }
      }
    }
    Map<UUID, Set<String>> busySection = new HashMap<>();
    Map<UUID, Set<String>> busyTeacher = new HashMap<>();
    Map<String, Map<Integer, Integer>> perDay = new HashMap<>();
    Map<String, Integer> existing = new HashMap<>();
    jdbc.sql("select class_section_id, day, period, teacher_id, subject_id from timetable_slot")
        .query((rs, i) -> {
          UUID sec = rs.getObject(1, UUID.class);
          String slot = rs.getInt(2) + ":" + rs.getInt(3);
          busySection.computeIfAbsent(sec, k -> new HashSet<>()).add(slot);
          UUID t = rs.getObject(4, UUID.class);
          if (t != null) {
            busyTeacher.computeIfAbsent(t, k -> new HashSet<>()).add(slot);
          }
          UUID subj = rs.getObject(5, UUID.class);
          if (subj != null) {
            String key = sec + ":" + subj;
            existing.merge(key, 1, Integer::sum);
            perDay.computeIfAbsent(key, k -> new HashMap<>()).merge(rs.getInt(2), 1, Integer::sum);
          }
          return null;
        })
        .list();
    List<Integer> teaching = new ArrayList<>();
    for (int i = 0; i < st.periods().size(); i++) {
      if (!st.periods().get(i).breakTime()) {
        teaching.add(i);
      }
    }
    Generator.Result r =
        Generator.generate(
            loads,
            st.days(),
            teaching,
            busySection,
            busyTeacher,
            perDay,
            existing,
            st.maxSamePerDay(),
            25);
    tx.executeWithoutResult(s -> {
      for (Generator.Placement p : r.placed()) {
        jdbc.sql(
                "insert into timetable_slot (organization_id, class_section_id, day, period, subject_id, teacher_id) values (?, ?, ?, ?, ?, ?)")
            .params(
                TenantContext.requireOrganizationId(),
                p.section(),
                p.day(),
                p.period(),
                p.subject(),
                p.teacher())
            .update();
      }
      audit.record(
          "timetable.generated",
          "timetable_slot",
          null,
          null,
          Map.of("placed", r.placed().size(), "unplaced", r.unplaced().size()));
    });
    Map<String, Integer> missing = new java.util.LinkedHashMap<>();
    for (Generator.Lesson l : r.unplaced()) {
      missing.merge(
          sectionNames.get(l.section()) + " – " + subjectNames.get(l.subject()), 1, Integer::sum);
    }
    List<String> unplaced = new ArrayList<>();
    missing.forEach((k, v) -> unplaced.add(k + ": " + v + " period(s) could not be placed"));
    return new GenerateResult(r.placed().size(), unplaced);
  }

  // ------------------------------------------------------------------ substitutions

  public SubDay substitutions(LocalDate date) {
    requireModule();
    requireManage();
    requireFeature("substitutions", "Substitutions");
    LocalDate d = date == null ? LocalDate.now(IST) : date;
    int dow = d.getDayOfWeek().getValue();
    Settings st = loadSettings();
    List<Absence> absences =
        jdbc.sql(
                "select a.user_id, u.display_name, a.note from teacher_absence a join app_user u on u.id = a.user_id where a.date = ? order by 2")
            .param(d)
            .query((rs, i) ->
                new Absence(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)))
            .list();
    Set<UUID> away = new HashSet<>();
    absences.forEach(a -> away.add(a.userId()));
    // Busy teachers per period that day: their own lessons (unless away) plus substitutions given.
    Map<Integer, Set<UUID>> busy = new HashMap<>();
    Map<UUID, Integer> todayLoad = new HashMap<>();
    jdbc.sql(
            "select period, teacher_id from timetable_slot where day = ? and teacher_id is not null")
        .param(dow)
        .query((rs, i) -> {
          UUID t = rs.getObject(2, UUID.class);
          busy.computeIfAbsent(rs.getInt(1), k -> new HashSet<>()).add(t);
          todayLoad.merge(t, 1, Integer::sum);
          return null;
        })
        .list();
    Map<String, UUID[]> given = new HashMap<>();
    jdbc.sql("select class_section_id, period, substitute_id from substitution where date = ?")
        .param(d)
        .query((rs, i) -> {
          UUID sub = rs.getObject(3, UUID.class);
          given.put(rs.getString(1) + ":" + rs.getInt(2), new UUID[] {sub});
          if (sub != null) {
            busy.computeIfAbsent(rs.getInt(2), k -> new HashSet<>()).add(sub);
            todayLoad.merge(sub, 1, Integer::sum);
          }
          return null;
        })
        .list();
    List<Staff> staff = staff();
    Map<UUID, String> names = new HashMap<>();
    staff.forEach(s -> names.put(s.id(), s.name()));
    List<Need> needs = new ArrayList<>();
    if (!away.isEmpty()) {
      List<Slot> lessons =
          jdbc.sql(
                  SLOTS
                      + " where t.day = ? and t.teacher_id = any(?::uuid[]) order by t.period, c.grade_order, c.section")
              .params(dow, away.toArray(UUID[]::new))
              .query((rs, i) -> mapSlot(rs))
              .list();
      for (Slot l : lessons) {
        UUID[] g = given.get(l.sectionId() + ":" + l.period());
        UUID sub = g == null ? null : g[0];
        Set<UUID> taken = busy.getOrDefault(l.period(), Set.of());
        List<Suggestion> free =
            staff.stream()
                .filter(s -> !away.contains(s.id()) && !taken.contains(s.id()))
                .map(s -> new Suggestion(s.id(), s.name(), todayLoad.getOrDefault(s.id(), 0)))
                .sorted(
                    Comparator.comparingInt(Suggestion::periodsToday)
                        .thenComparing(Suggestion::name))
                .limit(6)
                .toList();
        needs.add(
            new Need(
                l.sectionId(),
                l.section(),
                l.period(),
                st.periods().get(l.period()).label(),
                l.subject(),
                l.teacherId(),
                l.teacher(),
                sub,
                sub == null ? null : names.get(sub),
                free));
      }
    }
    return new SubDay(d, DAY_NAMES[dow], absences, needs);
  }

  public SubDay markAway(LocalDate date, UUID userId, String note, boolean away) {
    requireModule();
    requireManage();
    requireFeature("substitutions", "Substitutions");
    if (staff().stream().noneMatch(s -> s.id().equals(userId))) {
      throw ApiException.badRequest("invalid_teacher", "Choose a teacher of this school.");
    }
    if (date.isBefore(LocalDate.now(IST).minusDays(7))) {
      throw ApiException.badRequest("invalid_date", "Choose today or a later date.");
    }
    String n = note == null || note.isBlank() ? null : note.trim();
    tx.executeWithoutResult(s -> {
      if (away) {
        jdbc.sql(
                "insert into teacher_absence (organization_id, date, user_id, note, created_by) values (?, ?, ?, ?, ?)"
                    + " on conflict (organization_id, date, user_id) do update set note = excluded.note")
            .params(
                TenantContext.requireOrganizationId(),
                date,
                userId,
                n == null || n.length() <= 120 ? n : n.substring(0, 120),
                TenantContext.userId().orElse(null))
            .update();
      } else {
        jdbc.sql("delete from teacher_absence where date = ? and user_id = ?")
            .params(date, userId)
            .update();
        jdbc.sql("delete from substitution where date = ? and absent_teacher_id = ?")
            .params(date, userId)
            .update();
      }
      audit.record(
          away ? "timetable.teacher_away" : "timetable.teacher_back",
          "app_user",
          userId.toString(),
          null,
          Map.of("date", date.toString()));
    });
    return substitutions(date);
  }

  public SubDay assign(LocalDate date, UUID sectionId, int period, UUID substituteId) {
    requireModule();
    requireManage();
    requireFeature("substitutions", "Substitutions");
    SubDay day = substitutions(date);
    Need need =
        day.needs().stream()
            .filter(n -> n.sectionId().equals(sectionId) && n.period() == period)
            .findFirst()
            .orElseThrow(() -> ApiException.notFound("Period needing a substitute"));
    if (substituteId != null
        && need.suggestions().stream().noneMatch(s -> s.id().equals(substituteId))
        && !substituteId.equals(need.substituteId())) {
      throw ApiException.conflict("teacher_busy", "That teacher is not free in this period.");
    }
    tx.executeWithoutResult(s -> {
      if (substituteId == null) {
        jdbc.sql("delete from substitution where class_section_id = ? and date = ? and period = ?")
            .params(sectionId, date, period)
            .update();
      } else {
        jdbc.sql(
                "insert into substitution (organization_id, date, class_section_id, period, absent_teacher_id, substitute_id, created_by)"
                    + " values (?, ?, ?, ?, ?, ?, ?) on conflict (class_section_id, date, period) do update set substitute_id = excluded.substitute_id")
            .params(
                TenantContext.requireOrganizationId(),
                date,
                sectionId,
                period,
                need.absentTeacherId(),
                substituteId,
                TenantContext.userId().orElse(null))
            .update();
      }
      audit.record(
          "timetable.substitute_assigned",
          "class_section",
          sectionId.toString(),
          null,
          Map.of("date", date.toString(), "period", period));
    });
    return substitutions(date);
  }

  /** Today's substitute periods for the signed-in teacher. */
  public List<Slot> mySubstitutions(LocalDate date) {
    requireModule();
    LocalDate d = date == null ? LocalDate.now(IST) : date;
    return jdbc.sql(
            "select x.id, x.class_section_id, c.grade, c.section, ? day, x.period, t.subject_id, coalesce(s.name, t.label) subject_label,"
                + " x.substitute_id teacher_id, u.display_name teacher_name, t.room, null label"
                + " from substitution x join class_section c on c.id = x.class_section_id"
                + " left join timetable_slot t on t.class_section_id = x.class_section_id and t.day = ? and t.period = x.period"
                + " left join subject s on s.id = t.subject_id left join app_user u on u.id = x.substitute_id"
                + " where x.date = ? and x.substitute_id = ? order by x.period")
        .params(
            d.getDayOfWeek().getValue(),
            d.getDayOfWeek().getValue(),
            d,
            TenantContext.userId().orElse(null))
        .query((rs, i) -> mapSlot(rs))
        .list();
  }

  // ------------------------------------------------------------------ parent portal

  public ChildTimetable forStudent(UUID studentId) {
    if (!entitlements.hasModule(MODULE) || !feature("timetable_parent_view")) {
      return null;
    }
    record Info(String name, UUID section, String label) {}
    Info info =
        jdbc.sql(
                "select s.name, c.id, c.grade, c.section from student s join student_enrolment e on e.student_id = s.id"
                    + " and e.academic_year = ? join class_section c on c.id = e.class_section_id where s.id = ?")
            .params(currentYear(), studentId)
            .query((rs, i) ->
                new Info(
                    rs.getString(1),
                    rs.getObject(2, UUID.class),
                    rs.getString(3).trim() + " " + rs.getString(4).trim()))
            .optional()
            .orElse(null);
    if (info == null) {
      return null;
    }
    List<Slot> slots =
        jdbc.sql(SLOTS + " where t.class_section_id = ? order by t.day, t.period")
            .param(info.section())
            .query((rs, i) -> mapSlot(rs))
            .list();
    if (slots.isEmpty()) {
      return null;
    }
    LocalDate today = LocalDate.now(IST);
    List<Slot> subs =
        jdbc.sql(
                "select x.id, x.class_section_id, c.grade, c.section, ? day, x.period, t.subject_id, coalesce(s.name, t.label) subject_label,"
                    + " x.substitute_id teacher_id, u.display_name teacher_name, t.room, null label"
                    + " from substitution x join class_section c on c.id = x.class_section_id"
                    + " left join timetable_slot t on t.class_section_id = x.class_section_id and t.day = ? and t.period = x.period"
                    + " left join subject s on s.id = t.subject_id left join app_user u on u.id = x.substitute_id"
                    + " where x.date = ? and x.class_section_id = ? order by x.period")
            .params(
                today.getDayOfWeek().getValue(),
                today.getDayOfWeek().getValue(),
                today,
                info.section())
            .query((rs, i) -> mapSlot(rs))
            .list();
    Settings st = loadSettings();
    return new ChildTimetable(
        studentId, info.name(), info.label(), st.days(), st.periods(), slots, subs);
  }

  // ------------------------------------------------------------------ helpers

  private static Slot mapSlot(ResultSet rs) throws SQLException {
    return new Slot(
        rs.getObject("id", UUID.class),
        rs.getObject("class_section_id", UUID.class),
        rs.getString("grade").trim() + " " + rs.getString("section").trim(),
        rs.getInt("day"),
        rs.getInt("period"),
        rs.getObject("subject_id", UUID.class),
        rs.getString("subject_label"),
        rs.getString("label"),
        rs.getObject("teacher_id", UUID.class),
        rs.getString("teacher_name"),
        rs.getString("room"));
  }

  private List<Period> readPeriods(String s) {
    try {
      return json.readValue(s, PERIODS);
    } catch (Exception e) {
      return List.of(new Period("Period 1", "09:00", "09:40", false));
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
