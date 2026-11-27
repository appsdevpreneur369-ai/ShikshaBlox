package com.shikshablox.seed;

import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.OrganizationService;
import com.shikshablox.platform.tenancy.TenantContext;
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
 * Sample curriculum for the fictional Greenfield Demo School: Class 1 English and Mathematics
 * syllabus (units by month), Class 1 A coverage up to last month with one unit behind, a lesson
 * plan waiting for review, and an observation of Anita Desai's class. Runs once. Demo data only.
 */
@Component
@Order(43)
@ConditionalOnProperty(name = "shikshablox.seed.enabled", havingValue = "true")
class DemoCurriculumSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoCurriculumSeeder.class);
  private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

  private record U(String title, int month, List<String> topics) {}

  private static final List<U> ENGLISH =
      List.of(
          new U(
              "Alphabet and phonics",
              4,
              List.of("Letter sounds a–m", "Letter sounds n–z", "Blending three-letter words")),
          new U(
              "My family and me",
              6,
              List.of("Naming words", "Reading: My Family", "Writing about my family")),
          new U(
              "Animals around us",
              8,
              List.of("Action words", "Poem: The Little Bird", "Picture composition")),
          new U(
              "Seasons and weather",
              10,
              List.of("Describing words", "Story: The Rainy Day", "Sentence making")),
          new U(
              "Festivals",
              12,
              List.of("Joining words: and, but", "Reading: Diwali at Home", "Letter to a friend")),
          new U("Revision", 2, List.of("Reading practice", "Dictation")));

  private static final List<U> MATHS =
      List.of(
          new U(
              "Numbers up to 50",
              4,
              List.of("Counting objects", "Before, after, between", "Writing numbers")),
          new U(
              "Addition",
              7,
              List.of("Adding on a number line", "Adding single digits", "Word problems")),
          new U(
              "Subtraction",
              9,
              List.of("Taking away", "Subtracting single digits", "Word problems")),
          new U("Shapes and patterns", 11, List.of("Flat shapes", "Patterns with shapes")),
          new U("Money and time", 1, List.of("Coins and notes", "Days of the week")));

  private final OrganizationService organizations;
  private final JdbcClient jdbc;
  private final TransactionTemplate tx;

  DemoCurriculumSeeder(OrganizationService organizations, JdbcClient jdbc, TransactionTemplate tx) {
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
            if (jdbc.sql("select count(*) from syllabus_unit").query(Long.class).single() > 0) {
              return null;
            }
            tx.executeWithoutResult(s -> seed(org.id()));
            log.info("Seeded sample curriculum for greenfield");
            return null;
          });
    } catch (RuntimeException e) {
      log.warn("Demo curriculum seed skipped: {}", e.getMessage());
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
    UUID c1a =
        jdbc.sql(
                "select id from class_section where academic_year = ? and grade = 'Class 1' and section = 'A'")
            .param(year)
            .query(UUID.class)
            .optional()
            .orElse(null);
    UUID anita =
        jdbc.sql("select id from app_user where email = 'teacher@greenfield.test'")
            .query(UUID.class)
            .optional()
            .orElse(null);
    UUID cell =
        jdbc.sql("select id from app_user where email = 'exams@greenfield.test'")
            .query(UUID.class)
            .optional()
            .orElse(null);
    LocalDate today = LocalDate.now(IST);
    int nowIdx = (today.getMonthValue() - 4 + 12) % 12;
    UUID firstEnglishTopic = null;
    for (Object[] pair : new Object[][] {{"English", ENGLISH}, {"Mathematics", MATHS}}) {
      UUID subject =
          jdbc.sql(
                  "select id from subject where academic_year = ? and grade = 'Class 1' and name = ?")
              .params(year, pair[0])
              .query(UUID.class)
              .optional()
              .orElse(null);
      if (subject == null) {
        continue;
      }
      @SuppressWarnings("unchecked")
      List<U> units = (List<U>) pair[1];
      int order = 0;
      for (U u : units) {
        UUID unit =
            jdbc.sql(
                    "insert into syllabus_unit (organization_id, subject_id, title, planned_month, sort_order) values (?, ?, ?, ?, ?) returning id")
                .params(org, subject, u.title(), u.month(), ++order)
                .query(UUID.class)
                .single();
        int idx = (u.month() - 4 + 12) % 12;
        int t = 0;
        for (String topic : u.topics()) {
          UUID tid =
              jdbc.sql(
                      "insert into syllabus_topic (organization_id, unit_id, title, sort_order) values (?, ?, ?, ?) returning id")
                  .params(org, unit, topic, ++t)
                  .query(UUID.class)
                  .single();
          if (firstEnglishTopic == null && "English".equals(pair[0]) && idx == nowIdx) {
            firstEnglishTopic = tid;
          }
          // Covered: every unit planned before this month, except the last topic of the most recent
          // one (behind plan).
          boolean recent = idx == nowIdx - 1 || (nowIdx == 0 && idx == 11);
          boolean covered =
              idx < nowIdx && !(recent && t == u.topics().size() && "English".equals(pair[0]));
          if (covered && c1a != null) {
            jdbc.sql(
                    "insert into topic_coverage (organization_id, topic_id, class_section_id, covered_on, covered_by) values (?, ?, ?, ?, ?)")
                .params(org, tid, c1a, today.minusDays(7L * (nowIdx - idx) + t), anita)
                .update();
          }
        }
      }
      if ("English".equals(pair[0]) && c1a != null && anita != null) {
        jdbc.sql(
                "insert into lesson_plan (organization_id, class_section_id, subject_id, topic_id, planned_for, title, objectives, activities,"
                    + " resources, assessment, status, created_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'SUBMITTED', ?)")
            .params(
                org,
                c1a,
                subject,
                firstEnglishTopic,
                today.plusDays(2),
                "Describing words with real objects (sample)",
                "Children name an object and use two describing words for it.",
                "1. Feely bag game (10 min)\n2. Picture cards in pairs (15 min)\n3. Draw and label one object (10 min)",
                "Feely bag, picture cards, crayons",
                "Each child says one sentence with a describing word.",
                anita)
            .update();
      }
    }
    if (anita != null && c1a != null) {
      jdbc.sql(
              "insert into classroom_observation (organization_id, teacher_id, class_section_id, observed_on, scores, strengths, suggestions,"
                  + " observer_id) values (?, ?, ?, ?, '{\"Planning\":4,\"Engagement\":3,\"Classroom management\":4,\"Assessment for learning\":3,"
                  + "\"Use of resources\":3}'::jsonb, 'Warm, well-paced lesson; every child spoke at least once. (sample)',"
                  + " 'Use exit cards to check who has understood before moving on. (sample)', ?)")
          .params(org, anita, c1a, today.minusDays(5), cell)
          .update();
    }
  }
}
