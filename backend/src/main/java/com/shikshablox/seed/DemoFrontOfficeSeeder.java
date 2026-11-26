package com.shikshablox.seed;

import com.shikshablox.modules.frontoffice.FrontOfficeService;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.OrganizationService;
import com.shikshablox.platform.tenancy.TenantContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Sample front-office records for the fictional Greenfield Demo School: walk-ins and calls,
 * upcoming appointments, post and lost & found. Runs once (skipped when the register has entries).
 * Phone numbers use the 90000 3xxxx sample range.
 */
@Component
@Order(33)
@ConditionalOnProperty(name = "shikshablox.seed.enabled", havingValue = "true")
class DemoFrontOfficeSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoFrontOfficeSeeder.class);

  private final OrganizationService organizations;
  private final FrontOfficeService office;
  private final JdbcClient jdbc;

  DemoFrontOfficeSeeder(
      OrganizationService organizations, FrontOfficeService office, JdbcClient jdbc) {
    this.organizations = organizations;
    this.office = office;
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
            if (jdbc.sql("select count(*) from front_desk_entry").query(Long.class).single() > 0) {
              return null;
            }
            office.addEntry(
                new FrontOfficeService.EntryInput(
                    "WALK_IN",
                    "Ramesh Gupta",
                    "+91 90000 30001",
                    "FEES",
                    "Accounts office",
                    1,
                    "Asked for the term 2 fee receipt. (sample)"));
            office.addEntry(
                new FrontOfficeService.EntryInput(
                    "WALK_IN",
                    "Pooja Desai",
                    "+91 90000 30002",
                    "ADMISSION",
                    null,
                    2,
                    "Wants a campus tour for Class 4. (sample)"));
            office.addEntry(
                new FrontOfficeService.EntryInput(
                    "CALL_IN",
                    "Sunil Patil",
                    "+91 90000 30003",
                    "OTHER",
                    null,
                    1,
                    "Asked about the school bus route to Kondapur. (sample)"));
            office.addEntry(
                new FrontOfficeService.EntryInput(
                    "WALK_IN", "Blue Dart courier", null, "DELIVERY", null, 1, "(sample)"));

            Instant tomorrow10 =
                LocalDate.now(ZoneId.of("Asia/Kolkata"))
                    .plusDays(1)
                    .atTime(10, 0)
                    .atZone(ZoneId.of("Asia/Kolkata"))
                    .toInstant();
            office.book(
                new FrontOfficeService.AppointmentInput(
                    "Nisha Kapoor",
                    "+91 90000 30004",
                    null,
                    "Principal",
                    "Discuss Class 7 transfer (sample)",
                    tomorrow10,
                    30,
                    null));
            office.book(
                new FrontOfficeService.AppointmentInput(
                    "Anil Menon",
                    "+91 90000 30005",
                    null,
                    "Class 3 teacher",
                    "Parent-teacher meeting (sample)",
                    tomorrow10.plus(2, ChronoUnit.HOURS),
                    15,
                    null));

            office.logPost(
                new FrontOfficeService.PostInput(
                    "INWARD",
                    "Board regional office",
                    "Principal",
                    "Speed Post",
                    "EX123456789IN",
                    "Circular (sample)",
                    null));
            office.logPost(
                new FrontOfficeService.PostInput(
                    "OUTWARD",
                    "District education office",
                    "Office",
                    "Speed Post",
                    null,
                    "Annual return copy (sample)",
                    null));

            office.addLostItem(
                "Blue water bottle with name sticker \"Aarav\"", "Playground", null, null, null);
            office.addLostItem("Black school sweater, size 28", "Library", null, null, null);
            log.info("Seeded sample front-office records for greenfield");
            return null;
          });
    } catch (RuntimeException e) {
      log.warn("Demo front-office seed skipped: {}", e.getMessage());
    }
  }
}
