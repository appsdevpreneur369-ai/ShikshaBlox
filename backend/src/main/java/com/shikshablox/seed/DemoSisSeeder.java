package com.shikshablox.seed;

import com.shikshablox.modules.sis.SisService;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.OrganizationService;
import com.shikshablox.platform.tenancy.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Sample classes and students for the fictional Greenfield Demo School. Guardians reuse the demo
 * communication-hub parents (same phone numbers), so their message contacts pick up class tags.
 * Runs once (redone if a previous run stopped part-way). Phones use the 90000 5xxxx / 6xxxx ranges.
 */
@Component
@Order(36)
@ConditionalOnProperty(name = "shikshablox.seed.enabled", havingValue = "true")
class DemoSisSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoSisSeeder.class);

  private record Kid(
      String name,
      String gender,
      int age,
      String grade,
      String section,
      String guardian,
      String relation,
      String phone) {}

  private static final List<Kid> KIDS =
      List.of(
          new Kid(
              "Aarav Sharma",
              "MALE",
              6,
              "Class 1",
              "A",
              "Priya Sharma",
              "MOTHER",
              "+91 90000 50001"),
          new Kid(
              "Diya Verma",
              "FEMALE",
              6,
              "Class 1",
              "A",
              "Rahul Verma",
              "FATHER",
              "+91 90000 50002"),
          new Kid(
              "Kabir Khan", "MALE", 6, "Class 1", "B", "Fatima Khan", "MOTHER", "+91 90000 50009"),
          new Kid(
              "Arjun Joshi",
              "MALE",
              6,
              "Class 1",
              "B",
              "Deepak Joshi",
              "FATHER",
              "+91 90000 50012"),
          new Kid(
              "Vihaan Reddy",
              "MALE",
              8,
              "Class 3",
              "A",
              "Sneha Reddy",
              "MOTHER",
              "+91 90000 50003"),
          new Kid(
              "Anaya Kumar",
              "FEMALE",
              8,
              "Class 3",
              "A",
              "Kiran Kumar",
              "FATHER",
              "+91 90000 50004"),
          new Kid(
              "Ishaan Iyer", "MALE", 8, "Class 3", "A", "Meera Iyer", "MOTHER", "+91 90000 50005"),
          new Kid(
              "Aadhya Singh",
              "FEMALE",
              8,
              "Class 3",
              "B",
              "Vikram Singh",
              "FATHER",
              "+91 90000 50010"),
          new Kid(
              "Saanvi Nair",
              "FEMALE",
              10,
              "Class 5",
              "A",
              "Arjun Nair",
              "FATHER",
              "+91 90000 50006"),
          new Kid(
              "Reyansh Rao",
              "MALE",
              10,
              "Class 5",
              "A",
              "Lakshmi Rao",
              "MOTHER",
              "+91 90000 50007"),
          new Kid(
              "Myra Babu",
              "FEMALE",
              10,
              "Class 5",
              "A",
              "Suresh Babu",
              "FATHER",
              "+91 90000 50008"),
          new Kid(
              "Riya Mehta",
              "FEMALE",
              10,
              "Class 5",
              "A",
              "Anjali Mehta",
              "MOTHER",
              "+91 90000 50011"),
          new Kid(
              "Tara Sharma", "FEMALE", 4, "LKG", "A", "Priya Sharma", "MOTHER", "+91 90000 50001"),
          new Kid(
              "Neel Gupta", "MALE", 3, "Nursery", "A", "Ramesh Gupta", "FATHER", "+91 90000 60001"),
          new Kid("Zoya Ali", "FEMALE", 5, "UKG", "A", "Imran Ali", "FATHER", "+91 90000 60002"));

  private final OrganizationService organizations;
  private final SisService sis;
  private final JdbcClient jdbc;

  DemoSisSeeder(OrganizationService organizations, SisService sis, JdbcClient jdbc) {
    this.organizations = organizations;
    this.sis = sis;
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
            // The last sample student marks a complete run; a partial run is cleared and redone.
            if (jdbc.sql("select count(*) from student where name = 'Zoya Ali'")
                    .query(Long.class)
                    .single()
                > 0) {
              return null;
            }
            jdbc.sql("delete from student where address like '%(sample)%'").update();
            jdbc.sql(
                    "delete from guardian g where not exists"
                        + " (select 1 from student_guardian sg where sg.guardian_id = g.id)")
                .update();
            String year = sis.settings().currentYear();
            sis.saveSettings("GDS/", 1001);
            sis.addSections(
                year,
                List.of(
                    new SisService.GradeInput("Nursery", List.of("A")),
                    new SisService.GradeInput("LKG", List.of("A")),
                    new SisService.GradeInput("UKG", List.of("A")),
                    new SisService.GradeInput("Class 1", List.of("A", "B")),
                    new SisService.GradeInput("Class 2", List.of("A")),
                    new SisService.GradeInput("Class 3", List.of("A", "B")),
                    new SisService.GradeInput("Class 4", List.of("A")),
                    new SisService.GradeInput("Class 5", List.of("A"))));
            Map<String, UUID> sections =
                sis.sections(year).stream()
                    .collect(Collectors.toMap(SisService.Section::label, SisService.Section::id));
            LocalDate today = LocalDate.now(SisService.IST);
            int roll = 0;
            for (Kid k : KIDS) {
              roll++;
              boolean allergy = k.name().equals("Ishaan Iyer");
              sis.create(
                  new SisService.StudentInput(
                      null,
                      k.name(),
                      today.minusYears(k.age()).minusDays(30L * (roll % 11)),
                      k.gender(),
                      roll % 3 == 0 ? "B+" : "O+",
                      today.minusMonths(roll % 2 == 0 ? 8 : 20),
                      null,
                      "House " + roll + ", Demo Colony, Hyderabad (sample)",
                      roll % 2 == 0 ? "Blue" : "Green",
                      allergy ? "Peanut allergy: carries an EpiPen (sample)" : null,
                      k.name().equals("Myra Babu")
                          ? "Only the mother or grandmother may collect Myra (sample)"
                          : null,
                      roll % 4 == 0 ? String.format("90000000%04d", roll) : null,
                      roll % 4 == 0
                          ? "GENERATED"
                          : roll % 4 == 1 ? "CONSENT_PENDING" : "NOT_STARTED",
                      null,
                      null,
                      sections.get(SisService.labelOf(k.grade(), k.section())),
                      null,
                      List.of(
                          new SisService.GuardianInput(
                              null,
                              k.guardian(),
                              k.phone(),
                              null,
                              null,
                              null,
                              k.relation(),
                              true,
                              true,
                              true))));
            }
            log.info("Seeded sample classes and {} students for greenfield", KIDS.size());
            return null;
          });
    } catch (RuntimeException e) {
      log.warn("Demo student information seed skipped: {}", e.getMessage());
    }
  }
}
