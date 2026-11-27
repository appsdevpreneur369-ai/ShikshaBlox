package com.shikshablox.seed;

import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.OrganizationService;
import com.shikshablox.platform.tenancy.TenantContext;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * Sample homework for the fictional Greenfield Demo School: Class 1 A English (handed in online,
 * with a rubric) due on the next school day, a marked piece from last week, and Class 3 A EVS.
 * Runs once. Demo data only.
 */
@Component
@Order(41)
@ConditionalOnProperty(name = "shikshablox.seed.enabled", havingValue = "true")
class DemoHomeworkSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoHomeworkSeeder.class);
  private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
  static final String MARKER = "Read pages 12–15 and write five new words (sample)";

  private final OrganizationService organizations;
  private final JdbcClient jdbc;
  private final TransactionTemplate tx;

  DemoHomeworkSeeder(OrganizationService organizations, JdbcClient jdbc, TransactionTemplate tx) {
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
            if (jdbc.sql("select count(*) from homework where title = ?").param(MARKER).query(Long.class).single() > 0) {
              return null;
            }
            tx.executeWithoutResult(s -> seed(org.id()));
            log.info("Seeded sample homework for greenfield");
            return null;
          });
    } catch (RuntimeException e) {
      log.warn("Demo homework seed skipped: {}", e.getMessage());
    }
  }

  private void seed(UUID org) {
    String year = jdbc.sql("select current_year from sis_settings").query(String.class).optional().orElse(null);
    if (year == null) {
      return;
    }
    jdbc.sql("delete from homework").update();
    UUID c1a = section(year, "Class 1", "A");
    UUID c3a = section(year, "Class 3", "A");
    UUID teacher =
        jdbc.sql("select id from app_user where email = 'teacher@greenfield.test'").query(UUID.class).optional().orElse(null);
    LocalDate today = LocalDate.now(IST);
    LocalDate next = today.plusDays(1);
    if (next.getDayOfWeek() == DayOfWeek.SUNDAY) {
      next = next.plusDays(1);
    }
    if (c1a != null) {
      insert(org, c1a, subject(year, "Class 1", "English"), MARKER,
          "Read the story on pages 12 to 15 of your English reader aloud with a parent. Write five new words you found, with a"
              + " picture for each.", today.minusDays(1), next, true, null,
          "[{\"criterion\":\"Five words written\",\"points\":5},{\"criterion\":\"Neat pictures\",\"points\":5}]", teacher);
      UUID done =
          insert(org, c1a, subject(year, "Class 1", "Mathematics"), "Count and colour: worksheet 6 (sample)",
              "Count the objects in each box and colour the right number.", today.minusDays(9), today.minusDays(6), true, 10.0,
              "[]", teacher);
      UUID aarav = jdbc.sql("select id from student where name = 'Aarav Sharma'").query(UUID.class).optional().orElse(null);
      if (aarav != null) {
        jdbc.sql(
                "insert into homework_submission (organization_id, homework_id, student_id, text, submitted_at, status, points, feedback,"
                    + " graded_by, graded_at) values (?, ?, ?, 'All boxes counted and coloured. (sample)', now() - interval '7 days', 'GRADED', 9,"
                    + " 'Very good counting, Aarav! Check box 4 again. (sample)', ?, now() - interval '5 days')")
            .params(org, done, aarav, teacher)
            .update();
      }
    }
    if (c3a != null) {
      insert(org, c3a, subject(year, "Class 3", "Environmental Studies"), "Plants around my home (sample)",
          "Collect three different leaves, paste them in your notebook and write where you found each.", today, today.plusDays(4),
          false, null, "[]", null);
    }
  }

  private UUID insert(UUID org, UUID section, UUID subject, String title, String details, LocalDate assigned, LocalDate due,
      boolean submissions, Double max, String rubric, UUID by) {
    return jdbc.sql(
            "insert into homework (organization_id, class_section_id, subject_id, title, details, assigned_on, due_on, allow_submissions,"
                + " max_points, rubric, created_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?) returning id")
        .params(org, section, subject, title, details, assigned, due, submissions,
            max == null ? null : java.math.BigDecimal.valueOf(max), rubric, by)
        .query(UUID.class)
        .single();
  }

  private UUID section(String year, String grade, String section) {
    return jdbc.sql("select id from class_section where academic_year = ? and grade = ? and section = ?")
        .params(year, grade, section)
        .query(UUID.class)
        .optional()
        .orElse(null);
  }

  private UUID subject(String year, String grade, String name) {
    return jdbc.sql("select id from subject where academic_year = ? and grade = ? and name = ?")
        .params(year, grade, name)
        .query(UUID.class)
        .optional()
        .orElse(null);
  }
}
