package com.shikshablox.modules.earlyyears;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.entitlement.EntitlementService;
import com.shikshablox.platform.files.MediaService;
import com.shikshablox.platform.identity.CurrentUser;
import com.shikshablox.platform.identity.Permission;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.TenantContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Early years (M31) for pre-primary sections (classes without a number, e.g. Nursery, LKG, UKG).
 * Class teachers fill the diary and observations of their class; school admins see every class.
 * Photos live in private storage and are shown only to staff and the child's own family.
 */
@Service
public class EarlyYearsService {

  static final String MODULE = "EARLY_YEARS";
  static final ZoneId IST = ZoneId.of("Asia/Kolkata");
  static final Set<String> MEALS = Set.of("ALL", "MOST", "SOME", "NONE");
  static final Set<String> MOODS = Set.of("HAPPY", "CALM", "TIRED", "UPSET");
  static final Set<String> STATUSES = Set.of("NOT_YET", "EMERGING", "ACHIEVED");
  static final long MAX_PHOTO = 6L * 1024 * 1024;
  public static final List<String> MEAL_NAMES = List.of("Snack", "Lunch");
  static final String[][] DEFAULT_MILESTONES = {
    {"Physical development", "Runs, jumps and balances on one foot"},
    {"Physical development", "Holds a crayon with a tripod grip"},
    {"Physical development", "Cuts along a line with child-safe scissors"},
    {"Socio-emotional and ethical", "Takes turns and shares with others"},
    {"Socio-emotional and ethical", "Names own feelings (happy, sad, angry)"},
    {"Socio-emotional and ethical", "Follows simple classroom routines"},
    {"Cognitive", "Sorts objects by colour, shape or size"},
    {"Cognitive", "Counts objects up to 10"},
    {"Cognitive", "Recognises and continues simple patterns"},
    {"Language and literacy", "Listens to and retells a short story"},
    {"Language and literacy", "Recognises the letters of own name"},
    {"Language and literacy", "Speaks in full sentences"},
    {"Aesthetic and cultural", "Joins in songs, rhymes and movement"},
    {"Aesthetic and cultural", "Draws and talks about own drawings"},
    {"Positive learning habits", "Stays with an activity for about 10 minutes"},
    {"Positive learning habits", "Washes hands before meals without reminders"}
  };
  private static final TypeReference<Map<String, String>> MEAL_MAP = new TypeReference<>() {};

  public record SectionRef(UUID id, String label) {}

  public record Milestone(UUID id, String domain, String statement) {}

  public record Overview(
      List<SectionRef> sections,
      List<Milestone> milestones,
      List<String> meals,
      boolean photos,
      boolean stories,
      boolean pickup) {}

  public record DiaryRow(
      UUID studentId,
      String name,
      LocalTime arrival,
      Map<String, String> meals,
      LocalTime napFrom,
      LocalTime napTo,
      Integer toilet,
      String mood,
      String activities,
      String note,
      boolean saved) {}

  public record DiaryInput(
      UUID studentId,
      LocalTime arrival,
      Map<String, String> meals,
      LocalTime napFrom,
      LocalTime napTo,
      Integer toilet,
      String mood,
      String activities,
      String note) {}

  public record ChildMilestones(UUID studentId, String name, Map<UUID, String> status) {}

  public record MilestoneBoard(List<Milestone> milestones, List<ChildMilestones> children) {}

  public record Observation(
      UUID id,
      UUID studentId,
      String studentName,
      LocalDate observedOn,
      String domain,
      String note,
      boolean hasPhoto,
      String photoUrl,
      boolean shareWithFamily,
      String by) {}

  public record Pickup(
      UUID id, String name, String relation, String phone, boolean hasPhoto, String photoUrl) {}

  public record Story(
      String school,
      String child,
      String classLabel,
      LocalDate from,
      LocalDate to,
      List<Observation> observations,
      Map<String, List<String>> achieved,
      Map<String, List<String>> emerging) {}

  public record ChildFeed(
      UUID studentId,
      String name,
      DiaryRow today,
      List<Observation> observations,
      List<Pickup> pickups,
      int achieved,
      int total) {}

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final AuditService audit;
  private final EntitlementService entitlements;
  private final MediaService media;
  private final ObjectMapper json;

  EarlyYearsService(
      JdbcClient jdbc,
      TransactionTemplate tx,
      AuditService audit,
      @org.springframework.context.annotation.Lazy EntitlementService entitlements,
      MediaService media,
      ObjectMapper json) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.audit = audit;
    this.entitlements = entitlements;
    this.media = media;
    this.json = json;
  }

  // ------------------------------------------------------------------ access

  private void requireModule() {
    entitlements.requireModule(MODULE);
  }

  private boolean feature(String f) {
    return entitlements.hasFeature(MODULE, f);
  }

  private void requireFeature(String f, String what) {
    if (!feature(f)) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "feature_not_in_plan", what + " is part of ShikshaBlox Standard.");
    }
  }

  static boolean admin() {
    return CurrentUser.get()
        .map(u ->
            u.roles().stream().anyMatch(r -> r.permissions().contains(Permission.SCHOOL_MANAGE)))
        .orElse(false);
  }

  private UUID me() {
    return TenantContext.userId().orElse(null);
  }

  private String year() {
    return jdbc.sql("select current_year from sis_settings")
        .query(String.class)
        .optional()
        .orElseGet(() -> {
          LocalDate d = LocalDate.now(IST);
          int start = d.getMonthValue() >= 4 ? d.getYear() : d.getYear() - 1;
          return start + "-" + String.format("%02d", (start + 1) % 100);
        });
  }

  /** Pre-primary sections this user may work with. */
  List<SectionRef> sections() {
    boolean all = admin();
    return jdbc.sql(
            "select c.id, c.grade, c.section from class_section c where c.academic_year = ? and c.grade !~ '[0-9]'"
                + (all
                    ? ""
                    : " and exists (select 1 from attendance_teacher t where t.class_section_id = c.id and t.user_id = ?)")
                + " order by c.grade_order, c.grade, c.section")
        .params(all ? List.of(year()) : List.of(year(), me()))
        .query((rs, i) ->
            new SectionRef(
                rs.getObject(1, UUID.class), rs.getString(2).trim() + " " + rs.getString(3).trim()))
        .list();
  }

  private void requireSection(UUID sectionId) {
    if (sections().stream().noneMatch(s -> s.id().equals(sectionId))) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "not_your_class",
          "You are not the teacher of this pre-primary class.");
    }
  }

  /** The child's current section, which must be one this user works with. */
  private UUID requireChild(UUID studentId) {
    UUID sec =
        jdbc.sql(
                "select e.class_section_id from student_enrolment e where e.student_id = ? and e.academic_year = ?")
            .params(studentId, year())
            .query(UUID.class)
            .optional()
            .orElseThrow(() -> ApiException.notFound("Child"));
    requireSection(sec);
    return sec;
  }

  private record Kid(UUID id, String name) {}

  private List<Kid> children(UUID sectionId) {
    return jdbc.sql(
            "select s.id, s.name from student_enrolment e join student s on s.id = e.student_id"
                + " where e.class_section_id = ? and s.status = 'ENROLLED' order by e.roll_no nulls last, s.name")
        .param(sectionId)
        .query((rs, i) -> new Kid(rs.getObject(1, UUID.class), rs.getString(2)))
        .list();
  }

  // ------------------------------------------------------------------ overview & diary

  List<Milestone> milestones() {
    long n = jdbc.sql("select count(*) from ey_milestone").query(Long.class).single();
    if (n == 0) {
      int order = 0;
      for (String[] m : DEFAULT_MILESTONES) {
        jdbc.sql(
                "insert into ey_milestone (organization_id, domain, statement, sort_order) values (?, ?, ?, ?)")
            .params(TenantContext.requireOrganizationId(), m[0], m[1], ++order)
            .update();
      }
    }
    return jdbc.sql(
            "select id, domain, statement from ey_milestone where active order by sort_order, domain")
        .query(
            (rs, i) -> new Milestone(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)))
        .list();
  }

  public Overview overview() {
    requireModule();
    return new Overview(
        sections(),
        milestones(),
        MEAL_NAMES,
        feature("photos"),
        feature("learning_stories"),
        feature("pickup"));
  }

  public List<DiaryRow> diary(UUID sectionId, LocalDate date) {
    requireModule();
    requireSection(sectionId);
    LocalDate d = date == null ? LocalDate.now(IST) : date;
    Map<UUID, DiaryRow> saved = new HashMap<>();
    jdbc.sql(
            "select * from ey_diary where date = ? and student_id in (select student_id from student_enrolment where class_section_id = ?)")
        .params(d, sectionId)
        .query((rs, i) -> saved.put(rs.getObject("student_id", UUID.class), mapDiary(rs, null)))
        .list();
    List<DiaryRow> out = new ArrayList<>();
    for (Kid k : children(sectionId)) {
      DiaryRow r = saved.get(k.id());
      out.add(
          r == null
              ? new DiaryRow(
                  k.id(), k.name(), null, Map.of(), null, null, null, null, null, null, false)
              : new DiaryRow(
                  k.id(),
                  k.name(),
                  r.arrival(),
                  r.meals(),
                  r.napFrom(),
                  r.napTo(),
                  r.toilet(),
                  r.mood(),
                  r.activities(),
                  r.note(),
                  true));
    }
    return out;
  }

  public List<DiaryRow> saveDiary(UUID sectionId, LocalDate date, List<DiaryInput> rows) {
    requireModule();
    requireSection(sectionId);
    LocalDate d = date == null ? LocalDate.now(IST) : date;
    if (d.isAfter(LocalDate.now(IST)) || d.isBefore(LocalDate.now(IST).minusDays(7))) {
      throw ApiException.badRequest("invalid_date", "Fill the diary for today or the past week.");
    }
    Set<UUID> roster = new java.util.HashSet<>();
    children(sectionId).forEach(k -> roster.add(k.id()));
    tx.executeWithoutResult(s -> {
      for (DiaryInput r : rows == null ? List.<DiaryInput>of() : rows) {
        if (r == null || !roster.contains(r.studentId())) {
          throw ApiException.badRequest("invalid_child", "A child is not in this class.");
        }
        Map<String, String> meals = new LinkedHashMap<>();
        if (r.meals() != null) {
          r.meals()
              .forEach((k, v) -> {
                if (MEAL_NAMES.contains(k) && v != null && !v.isBlank()) {
                  if (!MEALS.contains(v)) {
                    throw ApiException.badRequest(
                        "invalid_meal", "Meals: all, most, some or none.");
                  }
                  meals.put(k, v);
                }
              });
        }
        if (r.mood() != null && !r.mood().isBlank() && !MOODS.contains(r.mood())) {
          throw ApiException.badRequest("invalid_mood", "Choose a mood.");
        }
        if (r.toilet() != null && (r.toilet() < 0 || r.toilet() > 20)) {
          throw ApiException.badRequest("invalid_number", "Toilet visits: 0 to 20.");
        }
        if (r.napFrom() != null && r.napTo() != null && r.napTo().isBefore(r.napFrom())) {
          throw ApiException.badRequest("invalid_nap", "The nap can't end before it starts.");
        }
        jdbc.sql(
                "insert into ey_diary (organization_id, student_id, date, arrival, meals, nap_from, nap_to, toilet, mood, activities, note, updated_by)"
                    + " values (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?) on conflict (student_id, date) do update set arrival = excluded.arrival,"
                    + " meals = excluded.meals, nap_from = excluded.nap_from, nap_to = excluded.nap_to, toilet = excluded.toilet, mood = excluded.mood,"
                    + " activities = excluded.activities, note = excluded.note, updated_by = excluded.updated_by, updated_at = now()")
            .params(
                TenantContext.requireOrganizationId(),
                r.studentId(),
                d,
                r.arrival(),
                write(meals),
                r.napFrom(),
                r.napTo(),
                r.toilet(),
                r.mood() == null || r.mood().isBlank() ? null : r.mood(),
                clip(r.activities(), 500),
                clip(r.note(), 500),
                me())
            .update();
      }
      audit.record(
          "earlyyears.diary_saved",
          "class_section",
          sectionId.toString(),
          null,
          Map.of("date", d.toString()));
    });
    return diary(sectionId, d);
  }

  // ------------------------------------------------------------------ milestones

  public MilestoneBoard board(UUID sectionId) {
    requireModule();
    requireSection(sectionId);
    List<Milestone> ms = milestones();
    Map<UUID, Map<UUID, String>> st = new HashMap<>();
    jdbc.sql(
            "select student_id, milestone_id, status from ey_milestone_status where student_id in"
                + " (select student_id from student_enrolment where class_section_id = ?)")
        .param(sectionId)
        .query((rs, i) ->
            st.computeIfAbsent(rs.getObject(1, UUID.class), k -> new HashMap<>())
                .put(rs.getObject(2, UUID.class), rs.getString(3)))
        .list();
    List<ChildMilestones> kids =
        children(sectionId).stream()
            .map(k -> new ChildMilestones(k.id(), k.name(), st.getOrDefault(k.id(), Map.of())))
            .toList();
    return new MilestoneBoard(ms, kids);
  }

  public void setMilestone(UUID studentId, UUID milestoneId, String status) {
    requireModule();
    requireChild(studentId);
    if (status == null || status.isBlank()) {
      jdbc.sql("delete from ey_milestone_status where student_id = ? and milestone_id = ?")
          .params(studentId, milestoneId)
          .update();
      return;
    }
    if (!STATUSES.contains(status)) {
      throw ApiException.badRequest("invalid_status", "Choose not yet, emerging or achieved.");
    }
    jdbc.sql(
            "insert into ey_milestone_status (organization_id, student_id, milestone_id, status, updated_on, updated_by) values (?, ?, ?, ?, ?, ?)"
                + " on conflict (student_id, milestone_id) do update set status = excluded.status, updated_on = excluded.updated_on, updated_by = excluded.updated_by")
        .params(
            TenantContext.requireOrganizationId(),
            studentId,
            milestoneId,
            status,
            LocalDate.now(IST),
            me())
        .update();
  }

  // ------------------------------------------------------------------ observations

  private static final String OBS =
      "select o.*, s.name student_name, u.display_name by_name from ey_observation o join student s on s.id = o.student_id"
          + " left join app_user u on u.id = o.created_by";

  public List<Observation> observations(UUID sectionId) {
    requireModule();
    requireSection(sectionId);
    return jdbc.sql(
            OBS
                + " where o.student_id in (select student_id from student_enrolment where class_section_id = ?)"
                + " order by o.observed_on desc, o.created_at desc limit 200")
        .param(sectionId)
        .query((rs, i) -> mapObs(rs, true))
        .list();
  }

  public Observation addObservation(
      UUID studentId,
      LocalDate date,
      String domain,
      String note,
      boolean share,
      String filename,
      byte[] photo) {
    requireModule();
    requireFeature("ey_observations", "Observations");
    requireChild(studentId);
    LocalDate d = date == null ? LocalDate.now(IST) : date;
    if (d.isAfter(LocalDate.now(IST))) {
      throw ApiException.badRequest("invalid_date", "Choose today or an earlier date.");
    }
    String n = note == null ? "" : note.trim();
    if (n.isEmpty() || n.length() > 2000) {
      throw ApiException.badRequest(
          "missing_field", "Write what you observed (up to 2,000 characters).");
    }
    String dom = domain == null || domain.isBlank() ? null : domain.trim();
    if (dom != null && dom.length() > 60) {
      throw ApiException.badRequest("too_long", "The area name is too long.");
    }
    MediaService.StoredFile f = null;
    if (photo != null && photo.length > 0) {
      requireFeature("photos", "Photos");
      f = media.storePrivate("early-years", filename, photo, MAX_PHOTO);
    }
    MediaService.StoredFile ff = f;
    try {
      UUID id =
          jdbc.sql(
                  "insert into ey_observation (organization_id, student_id, observed_on, domain, note, photo_key, photo_type, share_with_family, created_by)"
                      + " values (?, ?, ?, ?, ?, ?, ?, ?, ?) returning id")
              .params(
                  TenantContext.requireOrganizationId(),
                  studentId,
                  d,
                  dom,
                  n,
                  ff == null ? null : ff.storageKey(),
                  ff == null ? null : ff.contentType(),
                  share,
                  me())
              .query(UUID.class)
              .single();
      audit.record(
          "earlyyears.observation_added",
          "student",
          studentId.toString(),
          null,
          Map.of("photo", ff != null));
      return jdbc.sql(OBS + " where o.id = ?")
          .param(id)
          .query((rs, i) -> mapObs(rs, true))
          .single();
    } catch (RuntimeException e) {
      if (ff != null) {
        media.deletePrivate(List.of(ff.storageKey()));
      }
      throw e;
    }
  }

  public void deleteObservation(UUID id) {
    requireModule();
    record O(UUID student, String key) {}
    O o =
        jdbc.sql("select student_id, photo_key from ey_observation where id = ?")
            .param(id)
            .query((rs, i) -> new O(rs.getObject(1, UUID.class), rs.getString(2)))
            .optional()
            .orElseThrow(() -> ApiException.notFound("Observation"));
    requireChild(o.student());
    jdbc.sql("delete from ey_observation where id = ?").param(id).update();
    if (o.key() != null) {
      media.deletePrivate(List.of(o.key()));
    }
  }

  // ------------------------------------------------------------------ learning story

  public Story story(UUID studentId, LocalDate from, LocalDate to) {
    requireModule();
    requireFeature("learning_stories", "Learning stories");
    requireChild(studentId);
    return buildStory(studentId, from, to, false);
  }

  private Story buildStory(UUID studentId, LocalDate from, LocalDate to, boolean familyOnly) {
    LocalDate t = to == null ? LocalDate.now(IST) : to;
    LocalDate f = from == null ? t.minusMonths(3) : from;
    record Info(String school, String child, String label) {}
    Info info =
        jdbc.sql(
                "select o.name, s.name, c.grade, c.section from student s join organization o on o.id = s.organization_id"
                    + " join student_enrolment e on e.student_id = s.id and e.academic_year = ? join class_section c on c.id = e.class_section_id where s.id = ?")
            .params(year(), studentId)
            .query((rs, i) ->
                new Info(
                    rs.getString(1),
                    rs.getString(2),
                    rs.getString(3).trim() + " " + rs.getString(4).trim()))
            .single();
    List<Observation> obs =
        jdbc.sql(
                OBS
                    + " where o.student_id = ? and o.observed_on between ? and ?"
                    + (familyOnly ? " and o.share_with_family" : "")
                    + " order by o.observed_on")
            .params(studentId, f, t)
            .query((rs, i) -> mapObs(rs, true))
            .list();
    Map<String, List<String>> achieved = new LinkedHashMap<>();
    Map<String, List<String>> emerging = new LinkedHashMap<>();
    jdbc.sql(
            "select m.domain, m.statement, x.status from ey_milestone_status x join ey_milestone m on m.id = x.milestone_id"
                + " where x.student_id = ? order by m.sort_order")
        .param(studentId)
        .query((rs, i) -> {
          (("ACHIEVED".equals(rs.getString(3)))
                  ? achieved
                  : "EMERGING".equals(rs.getString(3))
                      ? emerging
                      : new HashMap<String, List<String>>())
              .computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
              .add(rs.getString(2));
          return null;
        })
        .list();
    return new Story(info.school(), info.child(), info.label(), f, t, obs, achieved, emerging);
  }

  // ------------------------------------------------------------------ pickup

  public List<Pickup> pickups(UUID studentId) {
    requireModule();
    requireFeature("pickup", "Pickup authorisation");
    requireChild(studentId);
    return listPickups(studentId);
  }

  private List<Pickup> listPickups(UUID studentId) {
    return jdbc.sql(
            "select id, name, relation, phone, photo_key from ey_pickup where student_id = ? order by created_at")
        .param(studentId)
        .query((rs, i) ->
            new Pickup(
                rs.getObject(1, UUID.class),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5) != null,
                rs.getString(5) == null ? null : media.signedPrivateUrl(rs.getString(5), "pickup")))
        .list();
  }

  public List<Pickup> addPickup(
      UUID studentId, String name, String relation, String phone, String filename, byte[] photo) {
    requireModule();
    requireFeature("pickup", "Pickup authorisation");
    requireChild(studentId);
    String n = name == null ? "" : name.trim();
    if (n.isEmpty() || n.length() > 120) {
      throw ApiException.badRequest("missing_field", "Give the adult's name.");
    }
    if (listPickups(studentId).size() >= 6) {
      throw ApiException.badRequest("too_many", "Up to 6 pickup adults per child.");
    }
    MediaService.StoredFile f =
        photo != null && photo.length > 0
            ? media.storePrivate("early-years/pickup", filename, photo, MAX_PHOTO)
            : null;
    jdbc.sql(
            "insert into ey_pickup (organization_id, student_id, name, relation, phone, photo_key, photo_type) values (?, ?, ?, ?, ?, ?, ?)")
        .params(
            TenantContext.requireOrganizationId(),
            studentId,
            n,
            clip(relation, 60),
            clip(phone, 20),
            f == null ? null : f.storageKey(),
            f == null ? null : f.contentType())
        .update();
    audit.record(
        "earlyyears.pickup_added", "student", studentId.toString(), null, Map.of("name", n));
    return listPickups(studentId);
  }

  public List<Pickup> removePickup(UUID pickupId) {
    requireModule();
    record P(UUID student, String key) {}
    P p =
        jdbc.sql("select student_id, photo_key from ey_pickup where id = ?")
            .param(pickupId)
            .query((rs, i) -> new P(rs.getObject(1, UUID.class), rs.getString(2)))
            .optional()
            .orElseThrow(() -> ApiException.notFound("Pickup adult"));
    requireChild(p.student());
    jdbc.sql("delete from ey_pickup where id = ?").param(pickupId).update();
    if (p.key() != null) {
      media.deletePrivate(List.of(p.key()));
    }
    audit.record("earlyyears.pickup_removed", "student", p.student().toString(), null, null);
    return listPickups(p.student());
  }

  // ------------------------------------------------------------------ parent feed

  public List<ChildFeed> feed(UUID guardianId) {
    if (!entitlements.hasModule(MODULE) || !feature("parent_feed")) {
      return List.of();
    }
    LocalDate today = LocalDate.now(IST);
    List<Kid> kids =
        jdbc.sql(
                "select s.id, s.name from student_guardian sg join student s on s.id = sg.student_id and s.status = 'ENROLLED'"
                    + " join student_enrolment e on e.student_id = s.id and e.academic_year = ? join class_section c on c.id = e.class_section_id"
                    + " where sg.guardian_id = ? and c.grade !~ '[0-9]' order by s.name")
            .params(year(), guardianId)
            .query((rs, i) -> new Kid(rs.getObject(1, UUID.class), rs.getString(2)))
            .list();
    int total = milestones().size();
    List<ChildFeed> out = new ArrayList<>();
    for (Kid k : kids) {
      DiaryRow d =
          jdbc.sql("select * from ey_diary where student_id = ? and date = ?")
              .params(k.id(), today)
              .query((rs, i) -> mapDiary(rs, k.name()))
              .optional()
              .orElse(null);
      List<Observation> obs =
          jdbc.sql(
                  OBS
                      + " where o.student_id = ? and o.share_with_family and o.observed_on >= ? order by o.observed_on desc limit 10")
              .params(k.id(), today.minusDays(30))
              .query((rs, i) -> mapObs(rs, true))
              .list();
      int achieved =
          jdbc.sql(
                  "select count(*) from ey_milestone_status x join ey_milestone m on m.id = x.milestone_id"
                      + " where x.student_id = ? and x.status = 'ACHIEVED' and m.active")
              .param(k.id())
              .query(Integer.class)
              .single();
      out.add(
          new ChildFeed(
              k.id(),
              k.name(),
              d,
              obs,
              feature("pickup") ? listPickups(k.id()) : List.of(),
              achieved,
              total));
    }
    return out;
  }

  // ------------------------------------------------------------------ helpers

  private DiaryRow mapDiary(ResultSet rs, String name) throws SQLException {
    Map<String, String> meals;
    try {
      meals = json.readValue(rs.getString("meals"), MEAL_MAP);
    } catch (Exception e) {
      meals = Map.of();
    }
    return new DiaryRow(
        rs.getObject("student_id", UUID.class),
        name,
        rs.getObject("arrival", LocalTime.class),
        meals,
        rs.getObject("nap_from", LocalTime.class),
        rs.getObject("nap_to", LocalTime.class),
        (Integer) rs.getObject("toilet"),
        rs.getString("mood"),
        rs.getString("activities"),
        rs.getString("note"),
        true);
  }

  private Observation mapObs(ResultSet rs, boolean withUrl) throws SQLException {
    String key = rs.getString("photo_key");
    return new Observation(
        rs.getObject("id", UUID.class),
        rs.getObject("student_id", UUID.class),
        rs.getString("student_name"),
        rs.getObject("observed_on", LocalDate.class),
        rs.getString("domain"),
        rs.getString("note"),
        key != null,
        key != null && withUrl ? media.signedPrivateUrl(key, "observation") : null,
        rs.getBoolean("share_with_family"),
        rs.getString("by_name"));
  }

  private String write(Object o) {
    try {
      return json.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static String clip(String v, int max) {
    if (v == null || v.isBlank()) {
      return null;
    }
    String t = v.trim();
    return t.length() > max ? t.substring(0, max) : t;
  }
}
