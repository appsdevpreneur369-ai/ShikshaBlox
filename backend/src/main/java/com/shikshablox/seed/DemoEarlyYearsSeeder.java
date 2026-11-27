package com.shikshablox.seed;

import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.OrganizationService;
import com.shikshablox.platform.tenancy.TenantContext;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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
 * Sample early-years records for the fictional Greenfield Demo School: Anita Desai also teaches LKG
 * A (Tara Sharma's class, so the demo parent sees the feed), the last school day's diary, milestone
 * progress, two observations and a grandparent allowed to collect Tara. Runs once.
 */
@Component
@Order(44)
@ConditionalOnProperty(name = "shikshablox.seed.enabled", havingValue = "true")
class DemoEarlyYearsSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoEarlyYearsSeeder.class);
  private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

  private final OrganizationService organizations;
  private final JdbcClient jdbc;
  private final TransactionTemplate tx;

  DemoEarlyYearsSeeder(OrganizationService organizations, JdbcClient jdbc, TransactionTemplate tx) {
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
            if (jdbc.sql("select count(*) from ey_observation").query(Long.class).single() > 0) {
              return null;
            }
            tx.executeWithoutResult(s -> seed(org.id()));
            log.info("Seeded sample early-years records for greenfield");
            return null;
          });
    } catch (RuntimeException e) {
      log.warn("Demo early-years seed skipped: {}", e.getMessage());
    }
  }

  private void seed(UUID org) {
    String year =
        jdbc.sql("select current_year from sis_settings")
            .query(String.class)
            .optional()
            .orElse(null);
    UUID anita =
        jdbc.sql("select id from app_user where email = 'teacher@greenfield.test'")
            .query(UUID.class)
            .optional()
            .orElse(null);
    UUID lkg =
        jdbc.sql(
                "select id from class_section where academic_year = ? and grade = 'LKG' and section = 'A'")
            .param(year)
            .query(UUID.class)
            .optional()
            .orElse(null);
    UUID tara =
        jdbc.sql("select id from student where name = 'Tara Sharma'")
            .query(UUID.class)
            .optional()
            .orElse(null);
    if (year == null || anita == null || lkg == null || tara == null) {
      return;
    }
    jdbc.sql(
            "insert into attendance_teacher (organization_id, class_section_id, user_id) values (?, ?, ?) on conflict do nothing")
        .params(org, lkg, anita)
        .update();
    String[][] defaults = {
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
    if (jdbc.sql("select count(*) from ey_milestone").query(Long.class).single() == 0) {
      int order = 0;
      for (String[] m : defaults) {
        jdbc.sql(
                "insert into ey_milestone (organization_id, domain, statement, sort_order) values (?, ?, ?, ?)")
            .params(org, m[0], m[1], ++order)
            .update();
      }
    }
    List<UUID> ms =
        jdbc.sql("select id from ey_milestone order by sort_order").query(UUID.class).list();
    for (int i = 0; i < ms.size(); i++) {
      String st = i % 3 == 0 ? "EMERGING" : i % 5 == 0 ? "NOT_YET" : "ACHIEVED";
      jdbc.sql(
              "insert into ey_milestone_status (organization_id, student_id, milestone_id, status, updated_on, updated_by) values (?, ?, ?, ?, ?, ?)")
          .params(org, tara, ms.get(i), st, LocalDate.now(IST).minusDays(10), anita)
          .update();
    }
    LocalDate day = LocalDate.now(IST);
    while (day.getDayOfWeek() == DayOfWeek.SUNDAY) {
      day = day.minusDays(1);
    }
    jdbc.sql(
            "insert into ey_diary (organization_id, student_id, date, arrival, meals, nap_from, nap_to, toilet, mood, activities, note, updated_by)"
                + " values (?, ?, ?, '08:40', '{\"Snack\":\"ALL\",\"Lunch\":\"MOST\"}'::jsonb, '12:50', '13:30', 2, 'HAPPY',"
                + " 'Sand play, rhymes, colouring leaves (sample)', 'Tara helped a friend tidy up. (sample)', ?)")
        .params(org, tara, day, anita)
        .update();
    jdbc.sql(
            "insert into ey_observation (organization_id, student_id, observed_on, domain, note, created_by) values"
                + " (?, ?, ?, 'Language and literacy', 'Tara retold the story of the thirsty crow in her own words, with actions. (sample)', ?),"
                + " (?, ?, ?, 'Cognitive', 'Sorted 12 buttons by colour and counted each group correctly up to 5. (sample)', ?)")
        .params(org, tara, day.minusDays(3), anita, org, tara, day.minusDays(8), anita)
        .update();
    jdbc.sql(
            "insert into ey_pickup (organization_id, student_id, name, relation, phone) values (?, ?, 'Kamala Sharma (sample)', 'Grandmother', '+91 90000 50091')")
        .params(org, tara)
        .update();
  }
}
