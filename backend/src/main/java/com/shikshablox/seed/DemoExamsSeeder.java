package com.shikshablox.seed;

import com.shikshablox.modules.exams.ExamService;
import com.shikshablox.platform.identity.Role;
import com.shikshablox.platform.identity.UserService;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.OrganizationService;
import com.shikshablox.platform.tenancy.TenantContext;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sample exams for the fictional Greenfield Demo School (CBSE): usual subjects for Classes 1–5,
 * Periodic Test 1 and the Half-Yearly Examination published with marks, co-scholastic grades and
 * remarks; Periodic Test 2 open for marks with a timetable next week (hall tickets), one class
 * subject partly entered and one submitted for approval; an exam cell login
 * (exams@greenfield.test). Runs once (redone if a previous run stopped part-way). Demo data only.
 */
@Component
@Order(39)
@ConditionalOnProperty(name = "shikshablox.seed.enabled", havingValue = "true")
class DemoExamsSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoExamsSeeder.class);
  private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
  static final String MARKER = "Periodic Test 2";
  private static final String[] REMARKS = {
    "A cheerful learner who takes part eagerly in class. Keep reading every day.",
    "Works neatly and listens well. Needs more practice with tables.",
    "Shows good curiosity in EVS projects. Should speak up more in class.",
    "Consistent effort across subjects. Handwriting has improved a lot."
  };

  private final OrganizationService organizations;
  private final UserService users;
  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final String password;

  DemoExamsSeeder(
      OrganizationService organizations,
      UserService users,
      JdbcClient jdbc,
      TransactionTemplate tx,
      @Value("${shikshablox.seed.password:}") String password) {
    this.organizations = organizations;
    this.users = users;
    this.jdbc = jdbc;
    this.tx = tx;
    this.password = password;
  }

  @Override
  public void run(ApplicationArguments args) {
    Organization org = organizations.findBySlug("greenfield").orElse(null);
    if (org == null || password == null || password.length() < 10) {
      return;
    }
    try {
      UUID cell = users.ensureUser("exams@greenfield.test", "Exam Cell", password);
      users.grantRole(cell, org.id(), Role.EXAM_CELL);
      TenantContext.callInTenant(
          org.id(),
          () -> {
            if (jdbc.sql("select count(*) from exam where name = ?")
                    .param(MARKER)
                    .query(Long.class)
                    .single()
                > 0) {
              return null;
            }
            tx.executeWithoutResult(s -> seed(org.id(), cell));
            log.info("Seeded sample exams for greenfield");
            return null;
          });
    } catch (RuntimeException e) {
      log.warn("Demo exams seed skipped: {}", e.getMessage());
    }
  }

  private record Kid(UUID id, String name, UUID section, String grade, String sectionName) {}

  private record Sub(UUID id, String grade, String name, String kind) {}

  private void seed(UUID org, UUID cell) {
    String year =
        jdbc.sql("select current_year from sis_settings")
            .query(String.class)
            .optional()
            .orElse(null);
    if (year == null) {
      return;
    }
    jdbc.sql("delete from exam").update();
    jdbc.sql("delete from subject").update();
    jdbc.sql(
            "insert into exam_settings (organization_id, principal_name) values (?, 'Dr. Kavita Rao') on conflict do nothing")
        .param(org)
        .update();
    List<String> grades =
        jdbc.sql(
                "select grade from class_section where academic_year = ? and grade ~ '[0-9]' group by grade order by min(grade_order)")
            .param(year)
            .query(String.class)
            .list();
    for (String g : grades) {
      int order = 0;
      for (String[] p : ExamService.preset("CBSE", g)) {
        jdbc.sql(
                "insert into subject (organization_id, academic_year, grade, name, kind, sort_order) values (?, ?, ?, ?, ?, ?)")
            .params(org, year, g, p[0], p[1], ++order)
            .update();
      }
    }
    List<Sub> subjects =
        jdbc.sql("select id, grade, name, kind from subject where academic_year = ?")
            .param(year)
            .query((rs, i) ->
                new Sub(
                    rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4)))
            .list();
    List<Kid> kids =
        jdbc.sql(
                "select s.id, s.name, c.id, c.grade, c.section from student_enrolment e join student s on s.id = e.student_id"
                    + " join class_section c on c.id = e.class_section_id where e.academic_year = ? and s.status = 'ENROLLED'"
                    + " and c.grade = any(?::text[])")
            .params(year, grades.toArray(String[]::new))
            .query((rs, i) ->
                new Kid(
                    rs.getObject(1, UUID.class),
                    rs.getString(2),
                    rs.getObject(3, UUID.class),
                    rs.getString(4),
                    rs.getString(5)))
            .list();
    int start = Integer.parseInt(year.substring(0, 4));
    String[] g = grades.toArray(String[]::new);
    UUID pt1 =
        exam(
            org,
            year,
            "Periodic Test 1",
            "Term 1",
            g,
            "[{\"key\":\"M\",\"label\":\"Marks\",\"max\":20}]",
            10,
            LocalDate.of(start, 7, 14),
            LocalDate.of(start, 7, 18),
            1);
    UUID hy =
        exam(
            org,
            year,
            "Half-Yearly Examination",
            "Term 1",
            g,
            "[{\"key\":\"TH\",\"label\":\"Theory\",\"max\":80},{\"key\":\"IA\",\"label\":\"Internal assessment\",\"max\":20}]",
            40,
            LocalDate.of(start, 9, 15),
            LocalDate.of(start, 9, 25),
            2);
    LocalDate today = LocalDate.now(IST);
    LocalDate monday = today.plusDays(1);
    while (monday.getDayOfWeek() != DayOfWeek.MONDAY) {
      monday = monday.plusDays(1);
    }
    UUID pt2 =
        exam(
            org,
            year,
            MARKER,
            "Term 2",
            g,
            "[{\"key\":\"M\",\"label\":\"Marks\",\"max\":20}]",
            10,
            monday,
            monday.plusDays(5),
            3);

    // Timetables: Half-Yearly (past) and Periodic Test 2 (next week), one scholastic paper a day.
    Map<String, Integer> day = new HashMap<>();
    for (Sub s : subjects) {
      if (!"SCHOLASTIC".equals(s.kind())) {
        continue;
      }
      int d = day.merge(s.grade(), 1, Integer::sum) - 1;
      paper(org, hy, s.id(), LocalDate.of(start, 9, 15).plusDays(d), LocalTime.of(9, 30), 150);
      LocalDate pd = monday.plusDays(d);
      if (pd.getDayOfWeek() == DayOfWeek.SUNDAY) {
        pd = pd.plusDays(1);
      }
      paper(org, pt2, s.id(), pd, LocalTime.of(9, 0), 60);
    }

    // Marks for the two published exams.
    int remark = 0;
    for (Kid k : kids) {
      int ability = 55 + Math.abs(k.name().hashCode()) % 40;
      for (Sub s : subjects) {
        if (!s.grade().equals(k.grade())) {
          continue;
        }
        if ("CO_SCHOLASTIC".equals(s.kind())) {
          String grade =
              ability > 80 ? "A" : ability > 65 ? (s.name().length() % 2 == 0 ? "A" : "B") : "B";
          mark(org, hy, s.id(), k.id(), "{}", grade, null, cell);
          continue;
        }
        int noise = Math.abs((k.name() + s.name()).hashCode()) % 21 - 10;
        double pct = Math.max(25, Math.min(99, ability + noise));
        if (k.name().equals("Kabir Khan") && s.name().equals("Hindi")) {
          mark(org, pt1, s.id(), k.id(), "{}", null, "AB", cell);
        } else {
          mark(org, pt1, s.id(), k.id(), "{\"M\":" + half(pct * 20 / 100) + "}", null, null, cell);
        }
        double th = half(Math.min(80, pct * 80 / 100 + (noise % 3)));
        double ia = half(Math.min(20, pct * 20 / 100 + 2));
        mark(org, hy, s.id(), k.id(), "{\"TH\":" + th + ",\"IA\":" + ia + "}", null, null, cell);
      }
      jdbc.sql(
              "insert into exam_remark (organization_id, exam_id, student_id, remark, updated_by) values (?, ?, ?, ?, ?)")
          .params(org, hy, k.id(), REMARKS[remark++ % REMARKS.length], cell)
          .update();
    }
    // Every class subject of the published exams is approved.
    for (UUID e : List.of(pt1, hy)) {
      jdbc.sql(
              "insert into exam_entry (organization_id, exam_id, subject_id, class_section_id, status, approved_by, approved_at)"
                  + " select distinct ?, ?, m.subject_id, en.class_section_id, 'APPROVED', ?, now() from exam_mark m"
                  + " join student_enrolment en on en.student_id = m.student_id and en.academic_year = ? where m.exam_id = ?")
          .params(org, e, cell, year, e)
          .update();
      jdbc.sql(
              "update exam set status = 'PUBLISHED', published_at = now(), published_by = ? where id = ?")
          .params(cell, e)
          .update();
    }
    // Periodic Test 2: Class 1 A English partly entered; Class 3 A Mathematics submitted.
    jdbc.sql("update exam set status = 'MARKS_OPEN' where id = ?").param(pt2).update();
    List<Kid> c1a =
        kids.stream()
            .filter(k -> k.grade().equals("Class 1") && k.sectionName().equals("A"))
            .toList();
    Sub eng1 = find(subjects, "Class 1", "English");
    if (eng1 != null && !c1a.isEmpty()) {
      mark(org, pt2, eng1.id(), c1a.get(0).id(), "{\"M\":17}", null, null, null);
    }
    List<Kid> c3a =
        kids.stream()
            .filter(k -> k.grade().equals("Class 3") && k.sectionName().equals("A"))
            .toList();
    Sub math3 = find(subjects, "Class 3", "Mathematics");
    if (math3 != null && !c3a.isEmpty()) {
      List<Double> m = new ArrayList<>(List.of(18.0, 15.5, 12.0, 19.0, 16.0));
      int i = 0;
      for (Kid k : c3a) {
        mark(
            org,
            pt2,
            math3.id(),
            k.id(),
            "{\"M\":" + m.get(i++ % m.size()) + "}",
            null,
            null,
            null);
      }
      jdbc.sql(
              "insert into exam_entry (organization_id, exam_id, subject_id, class_section_id, status, submitted_at)"
                  + " values (?, ?, ?, ?, 'SUBMITTED', now())")
          .params(org, pt2, math3.id(), c3a.get(0).section())
          .update();
    }
  }

  private UUID exam(
      UUID org,
      String year,
      String name,
      String term,
      String[] grades,
      String comps,
      int weight,
      LocalDate from,
      LocalDate to,
      int order) {
    return jdbc.sql(
            "insert into exam (organization_id, academic_year, name, term, grades, components, weight, start_date, end_date, sort_order)"
                + " values (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?) returning id")
        .params(org, year, name, term, grades, comps, weight, from, to, order)
        .query(UUID.class)
        .single();
  }

  private void paper(
      UUID org, UUID exam, UUID subject, LocalDate date, LocalTime time, int minutes) {
    jdbc.sql(
            "insert into exam_paper (organization_id, exam_id, subject_id, date, start_time, duration_min) values (?, ?, ?, ?, ?, ?)")
        .params(org, exam, subject, date, time, minutes)
        .update();
  }

  private void mark(
      UUID org,
      UUID exam,
      UUID subject,
      UUID student,
      String marks,
      String grade,
      String code,
      UUID by) {
    jdbc.sql(
            "insert into exam_mark (organization_id, exam_id, subject_id, student_id, marks, grade, code, updated_by)"
                + " values (?, ?, ?, ?, ?::jsonb, ?, ?, ?)")
        .params(org, exam, subject, student, marks, grade, code, by)
        .update();
  }

  private static Sub find(List<Sub> subjects, String grade, String name) {
    return subjects.stream()
        .filter(s -> s.grade().equals(grade) && s.name().equals(name))
        .findFirst()
        .orElse(null);
  }

  private static double half(double v) {
    return Math.round(v * 2) / 2.0;
  }
}
