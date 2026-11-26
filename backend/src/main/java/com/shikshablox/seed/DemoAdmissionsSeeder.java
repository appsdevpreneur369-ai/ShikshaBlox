package com.shikshablox.seed;

import com.shikshablox.modules.admissions.AdmissionsService;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.OrganizationService;
import com.shikshablox.platform.tenancy.TenantContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Sample admission leads and seats for the fictional Greenfield Demo School, so the CRM can be
 * demonstrated. Runs once (skipped when the school already has leads). Phone numbers use the
 * reserved-looking 90000 0xxxx range and emails the reserved {@code .test} domain.
 */
@Component
@Order(30)
@ConditionalOnProperty(name = "shikshablox.seed.enabled", havingValue = "true")
class DemoAdmissionsSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoAdmissionsSeeder.class);

  private record Sample(
      String student,
      String guardian,
      String grade,
      String source,
      String stage,
      int followUpDays,
      String note) {}

  private static final List<Sample> SAMPLES =
      List.of(
          new Sample(
              "Aarav",
              "Priya Sharma",
              "Class 1",
              "WALK_IN",
              "NEW",
              0,
              "Visited the office with her son; asked about transport."),
          new Sample(
              "Diya",
              "Rahul Verma",
              "UKG",
              "PHONE",
              "CONTACTED",
              1,
              "Wants to know the fee structure and timings."),
          new Sample(
              "Vihaan",
              "Sneha Reddy",
              "Class 5",
              "REFERRAL",
              "VISIT_SCHEDULED",
              2,
              "Referred by a current parent. Campus visit on Saturday."),
          new Sample(
              "Anaya",
              "Kiran Kumar",
              "Class 3",
              "WHATSAPP",
              "VISITED",
              -1,
              "Liked the library and labs. Comparing with one other school."),
          new Sample(
              "Ishaan",
              "Meera Iyer",
              "Class 6",
              "CAMPAIGN",
              "APPLIED",
              3,
              "Application submitted; documents pending."),
          new Sample(
              "Saanvi",
              "Arjun Nair",
              "Nursery",
              "WALK_IN",
              "OFFERED",
              1,
              "Offer letter shared; family deciding this week."),
          new Sample(
              "Reyansh",
              "Lakshmi Rao",
              "Class 2",
              "REFERRAL",
              "ADMITTED",
              0,
              "Admission fee paid."),
          new Sample(
              "Myra",
              "Suresh Babu",
              "Class 9",
              "PHONE",
              "LOST",
              0,
              "Chose a school closer to home."),
          new Sample(
              "Kabir",
              "Fatima Khan",
              "Class 1",
              "WALK_IN",
              "CONTACTED",
              -2,
              "Asked for a call back after salary day."),
          new Sample(
              "Aadhya",
              "Vikram Singh",
              "LKG",
              "CAMPAIGN",
              "NEW",
              0,
              "Came from the admissions open banner."));

  private final OrganizationService organizations;
  private final AdmissionsService admissions;
  private final JdbcClient jdbc;

  DemoAdmissionsSeeder(
      OrganizationService organizations, AdmissionsService admissions, JdbcClient jdbc) {
    this.organizations = organizations;
    this.admissions = admissions;
    this.jdbc = jdbc;
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
            long existing =
                jdbc.sql("select count(*) from admission_lead").query(Long.class).single();
            if (existing > 0) {
              return null;
            }
            String year =
                AdmissionsService.currentAcademicYear(
                    java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")));
            admissions.saveSeats(
                year,
                List.of(
                    new AdmissionsService.Seat("Nursery", 40, 0, 0, 0),
                    new AdmissionsService.Seat("LKG", 40, 1, 0, 0),
                    new AdmissionsService.Seat("UKG", 40, 2, 0, 0),
                    new AdmissionsService.Seat("Class 1", 35, 3, 0, 0),
                    new AdmissionsService.Seat("Class 2", 10, 4, 0, 0),
                    new AdmissionsService.Seat("Class 3", 8, 5, 0, 0),
                    new AdmissionsService.Seat("Class 5", 6, 6, 0, 0),
                    new AdmissionsService.Seat("Class 6", 12, 7, 0, 0),
                    new AdmissionsService.Seat("Class 9", 5, 8, 0, 0)));
            int i = 0;
            for (Sample s : SAMPLES) {
              Instant follow = Instant.now().plus(s.followUpDays(), ChronoUnit.DAYS);
              AdmissionsService.Lead lead =
                  admissions.create(
                      new AdmissionsService.LeadInput(
                          s.student(),
                          s.guardian(),
                          String.format("+91 90000 %05d", ++i),
                          s.guardian().toLowerCase().replace(' ', '.') + "@example.test",
                          s.grade(),
                          year,
                          s.source(),
                          i % 4 == 0 ? "HIGH" : "NORMAL",
                          null,
                          follow,
                          null,
                          null));
              admissions.log(lead.id(), "NOTE", s.note() + " (sample data)", null);
              if (!"NEW".equals(s.stage())) {
                admissions.move(
                    lead.id(),
                    s.stage(),
                    "LOST".equals(s.stage()) ? "Chose a school closer to home" : null);
              }
            }
            log.info("Seeded {} sample admission leads for greenfield", SAMPLES.size());
            return null;
          });
    } catch (RuntimeException e) {
      log.warn("Demo admissions seed skipped: {}", e.getMessage());
    }
  }
}
