package com.shikshablox.seed;

import com.shikshablox.platform.identity.Role;
import com.shikshablox.platform.identity.UserService;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.OrganizationService;
import com.shikshablox.platform.tenancy.TenantContext;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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
 * Sample attendance for the fictional Greenfield Demo School: a demo teacher (Anita Desai,
 * teacher@greenfield.test, class teacher of Class 1 A), the last four weeks of daily roll calls
 * (not today, so today can be demoed), one chronically absent child, an approved and a pending
 * leave, a pending correction, and an approved attendance alert template (test mode). Runs once
 * (redone if a previous run stopped part-way). Demo data only.
 */
@Component
@Order(38)
@ConditionalOnProperty(name = "shikshablox.seed.enabled", havingValue = "true")
class DemoAttendanceSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoAttendanceSeeder.class);
  private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
  static final String MARKER = "Family wedding in Pune (sample)";

  private record Kid(UUID id, String name, UUID section) {}

  private final OrganizationService organizations;
  private final UserService users;
  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final String password;

  DemoAttendanceSeeder(
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
      // Outside any transaction: provisioning runs in the system context.
      UUID teacher = users.ensureUser("teacher@greenfield.test", "Anita Desai", password);
      users.grantRole(teacher, org.id(), Role.TEACHER);
      TenantContext.callInTenant(
          org.id(),
          () -> {
            if (jdbc.sql("select count(*) from leave_request where reason = ?")
                    .param(MARKER)
                    .query(Long.class)
                    .single()
                > 0) {
              return null;
            }
            tx.executeWithoutResult(s -> seed(org.id(), teacher));
            log.info("Seeded sample attendance for greenfield");
            return null;
          });
    } catch (RuntimeException e) {
      log.warn("Demo attendance seed skipped: {}", e.getMessage());
    }
  }

  private void seed(UUID org, UUID teacher) {
    jdbc.sql("delete from attendance_session").update();
    jdbc.sql("delete from leave_request").update();
    jdbc.sql("delete from attendance_alert").update();
    jdbc.sql("insert into attendance_settings (organization_id) values (?) on conflict do nothing")
        .param(org)
        .update();
    String year =
        jdbc.sql("select current_year from sis_settings")
            .query(String.class)
            .optional()
            .orElse(null);
    if (year == null) {
      return;
    }
    UUID class1a =
        jdbc.sql(
                "select id from class_section where academic_year = ? and grade = 'Class 1' and section = 'A'")
            .param(year)
            .query(UUID.class)
            .optional()
            .orElse(null);
    if (class1a == null) {
      return;
    }
    jdbc.sql(
            "insert into attendance_teacher (organization_id, class_section_id, user_id) values (?, ?, ?) on conflict do nothing")
        .params(org, class1a, teacher)
        .update();
    jdbc.sql("update class_section set class_teacher = 'Anita Desai' where id = ?")
        .param(class1a)
        .update();

    for (String[] t :
        new String[][] {
          {
            "en",
            "Dear {{name}}, {{title}}. If this is unexpected, please contact the school. {{link}}"
          },
          {
            "hi",
            "प्रिय {{name}}, {{title}}। यदि यह अपेक्षित नहीं है, तो कृपया स्कूल से संपर्क करें। {{link}}"
          }
        }) {
      jdbc.sql(
              "insert into comms_template (organization_id, name, language, category, subject, body, whatsapp_status)"
                  + " values (?, 'attendance_alert', ?, 'UTILITY', '{{school}}: {{title}}', ?, 'APPROVED')"
                  + " on conflict (organization_id, name, language) do update set whatsapp_status = 'APPROVED'")
          .params(org, t[0], t[1])
          .update();
    }

    // The last 20 school days (Monday to Saturday), not including today.
    LocalDate today = LocalDate.now(IST);
    List<LocalDate> days = new ArrayList<>();
    for (LocalDate d = today.minusDays(1); days.size() < 20; d = d.minusDays(1)) {
      if (d.getDayOfWeek() != DayOfWeek.SUNDAY) {
        days.add(0, d);
      }
    }
    List<Kid> kids =
        jdbc.sql(
                "select s.id, s.name, e.class_section_id from student_enrolment e join student s on s.id = e.student_id"
                    + " where e.academic_year = ? and s.status = 'ENROLLED'")
            .param(year)
            .query((rs, i) ->
                new Kid(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class)))
            .list();
    List<UUID> sections = kids.stream().map(Kid::section).distinct().toList();
    for (LocalDate d : days) {
      for (UUID sec : sections) {
        UUID sid =
            jdbc.sql(
                    "insert into attendance_session (organization_id, class_section_id, date, period, marked_by, marked_at, updated_at)"
                        + " values (?, ?, ?, 0, ?, ?::date + time '09:05', ?::date + time '09:05') returning id")
                .params(org, sec, d, sec.equals(class1a) ? teacher : null, d, d)
                .query(UUID.class)
                .single();
        for (Kid k : kids) {
          if (!k.section().equals(sec)) {
            continue;
          }
          String status = status(k.name(), d, days);
          String reason =
              "A".equals(status)
                  ? (Math.abs((k.name() + d).hashCode()) % 2 == 0 ? "Sick" : "Not informed")
                  : null;
          jdbc.sql(
                  "insert into attendance_mark (organization_id, session_id, student_id, status, reason) values (?, ?, ?, ?, ?)")
              .params(org, sid, k.id(), status, reason)
              .update();
        }
      }
    }

    UUID diya = kid(kids, "Diya Verma");
    UUID tara = kid(kids, "Tara Sharma");
    UUID aarav = kid(kids, "Aarav Sharma");
    // Approved leave for Diya three school days ago (phoned in by her father).
    if (diya != null) {
      LocalDate d = days.get(days.size() - 3);
      jdbc.sql(
              "insert into leave_request (organization_id, student_id, from_date, to_date, reason, source, status, decided_by,"
                  + " decided_at, created_by) values (?, ?, ?, ?, 'Fever (sample)', 'STAFF', 'APPROVED', ?, now(), ?)")
          .params(org, diya, d, d, teacher, teacher)
          .update();
      jdbc.sql(
              "update attendance_mark m set status = 'LV', reason = null from attendance_session x"
                  + " where x.id = m.session_id and m.student_id = ? and x.date = ?")
          .params(diya, d)
          .update();
    }
    // A pending correction from the class teacher.
    if (aarav != null) {
      LocalDate d = days.get(days.size() - 2);
      UUID sid =
          jdbc.sql(
                  "select id from attendance_session where class_section_id = ? and date = ? and period = 0")
              .params(class1a, d)
              .query(UUID.class)
              .single();
      jdbc.sql("update attendance_mark set status = 'P' where session_id = ? and student_id = ?")
          .params(sid, aarav)
          .update();
      jdbc.sql(
              "insert into attendance_correction (organization_id, session_id, student_id, from_status, to_status, reason,"
                  + " requested_by) values (?, ?, ?, 'P', 'L', 'Arrived 20 minutes late; marked present by mistake (sample)', ?)")
          .params(org, sid, aarav, teacher)
          .update();
    }
    // A pending leave request from the demo parent (Priya Sharma) for next Monday and Tuesday.
    if (tara != null) {
      LocalDate monday = today.plusDays(1);
      while (monday.getDayOfWeek() != DayOfWeek.MONDAY) {
        monday = monday.plusDays(1);
      }
      UUID guardian =
          jdbc.sql(
                  "select id from guardian where right(regexp_replace(phone, '\\D', '', 'g'), 10) = '9000050001'")
              .query(UUID.class)
              .optional()
              .orElse(null);
      jdbc.sql(
              "insert into leave_request (organization_id, student_id, guardian_id, from_date, to_date, reason)"
                  + " values (?, ?, ?, ?, ?, ?)")
          .params(org, tara, guardian, monday, monday.plusDays(1), MARKER)
          .update();
    }
  }

  /** Mostly present; Neel (Nursery) is often away and absent the last four days. */
  static String status(String name, LocalDate d, List<LocalDate> days) {
    int idx = days.indexOf(d);
    int h = Math.abs((name + d).hashCode()) % 100;
    if (name.equals("Neel Gupta")) {
      return idx >= days.size() - 4 || h < 30 ? "A" : "P";
    }
    return h < 5 ? "A" : h < 9 ? "L" : h < 10 ? "H" : "P";
  }

  private static UUID kid(List<Kid> kids, String name) {
    return kids.stream().filter(k -> k.name().equals(name)).map(Kid::id).findFirst().orElse(null);
  }
}
