package com.shikshablox.seed;

import com.shikshablox.modules.timetable.Generator;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.OrganizationService;
import com.shikshablox.platform.tenancy.TenantContext;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sample timetables for the fictional Greenfield Demo School: subject loads for Classes 1–5,
 * generated weekly timetables (Anita Desai teaches English, Mathematics and EVS in Class 1 A and
 * computers in Class 1 B), a Games period, and Anita marked away on the next school day so the
 * substitution screen has periods to cover. Runs once. Demo data only.
 */
@Component
@Order(42)
@ConditionalOnProperty(name = "shikshablox.seed.enabled", havingValue = "true")
class DemoTimetableSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoTimetableSeeder.class);
  private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
  private static final Map<String, Integer> LOAD =
      Map.of(
          "English",
          7,
          "Hindi",
          6,
          "Mathematics",
          8,
          "Environmental Studies",
          6,
          "Computer Science",
          2);

  private final OrganizationService organizations;
  private final JdbcClient jdbc;
  private final TransactionTemplate tx;

  DemoTimetableSeeder(OrganizationService organizations, JdbcClient jdbc, TransactionTemplate tx) {
    this.organizations = organizations;
    this.jdbc = jdbc;
    this.tx = tx;
  }

  @Override
  public void run(ApplicationArguments args) {
    Organization org = organizations.findBySlug("greenfield").orElse(null);
    if (org == null) {
      return;
    }
    try {
      TenantContext.callInTenant(
          org.id(),
          () -> {
            if (jdbc.sql("select count(*) from timetable_load").query(Long.class).single() > 0) {
              return null;
            }
            tx.executeWithoutResult(s -> seed(org.id()));
            log.info("Seeded sample timetables for greenfield");
            return null;
          });
    } catch (RuntimeException e) {
      log.warn("Demo timetable seed skipped: {}", e.getMessage());
    }
  }

  private void seed(UUID org) {
    String year =
        jdbc.sql("select current_year from sis_settings")
            .query(String.class)
            .optional()
            .orElse(null);
    if (year == null) {
      return;
    }
    jdbc.sql("delete from timetable_slot").update();
    jdbc.sql("insert into timetable_settings (organization_id) values (?) on conflict do nothing")
        .param(org)
        .update();
    UUID anita =
        jdbc.sql("select id from app_user where email = 'teacher@greenfield.test'")
            .query(UUID.class)
            .optional()
            .orElse(null);
    record Sec(UUID id, String grade, String section) {}
    List<Sec> sections =
        jdbc.sql(
                "select id, grade, section from class_section where academic_year = ? and grade ~ '[0-9]' order by grade_order, section")
            .param(year)
            .query(
                (rs, i) -> new Sec(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)))
            .list();
    List<Generator.Load> loads = new ArrayList<>();
    Map<UUID, java.util.Set<String>> busySection = new HashMap<>();
    for (Sec s : sections) {
      // A games period on Friday, period 8 (index 9 in the default bell schedule).
      jdbc.sql(
              "insert into timetable_slot (organization_id, class_section_id, day, period, label) values (?, ?, 5, 9, 'Games')")
          .params(org, s.id())
          .update();
      busySection.computeIfAbsent(s.id(), k -> new java.util.HashSet<>()).add("5:9");
      List<Object[]> subjects =
          jdbc.sql(
                  "select id, name from subject where academic_year = ? and grade = ? and kind = 'SCHOLASTIC'")
              .params(year, s.grade())
              .query((rs, i) -> new Object[] {rs.getObject(1, UUID.class), rs.getString(2)})
              .list();
      for (Object[] sub : subjects) {
        String name = (String) sub[1];
        int n = LOAD.getOrDefault(name, 4);
        UUID teacher = null;
        if (anita != null && "Class 1".equals(s.grade())) {
          if ("A".equals(s.section())
              && Set.of("English", "Mathematics", "Environmental Studies").contains(name)) {
            teacher = anita;
          } else if ("B".equals(s.section()) && "Computer Science".equals(name)) {
            teacher = anita;
          }
        }
        jdbc.sql(
                "insert into timetable_load (organization_id, class_section_id, subject_id, periods_per_week, teacher_id) values (?, ?, ?, ?, ?)")
            .params(org, s.id(), sub[0], n, teacher)
            .update();
        loads.add(new Generator.Load(s.id(), (UUID) sub[0], teacher, n));
      }
    }
    List<Integer> teaching = List.of(0, 1, 2, 4, 5, 7, 8, 9);
    Generator.Result r =
        Generator.generate(
            loads,
            List.of(1, 2, 3, 4, 5, 6),
            teaching,
            busySection,
            new HashMap<>(),
            new HashMap<>(),
            new HashMap<>(),
            2,
            25);
    for (Generator.Placement p : r.placed()) {
      jdbc.sql(
              "insert into timetable_slot (organization_id, class_section_id, day, period, subject_id, teacher_id) values (?, ?, ?, ?, ?, ?)")
          .params(org, p.section(), p.day(), p.period(), p.subject(), p.teacher())
          .update();
    }
    if (anita != null) {
      LocalDate next = LocalDate.now(IST).plusDays(1);
      if (next.getDayOfWeek() == DayOfWeek.SUNDAY) {
        next = next.plusDays(1);
      }
      jdbc.sql(
              "insert into teacher_absence (organization_id, date, user_id, note) values (?, ?, ?, 'Training workshop (sample)') on conflict do nothing")
          .params(org, next, anita)
          .update();
    }
  }
}
