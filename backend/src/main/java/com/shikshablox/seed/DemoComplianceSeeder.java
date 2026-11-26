package com.shikshablox.seed;

import com.shikshablox.modules.disclosure.ComplianceService;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.OrganizationService;
import com.shikshablox.platform.tenancy.TenantContext;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * A sample compliance calendar for the fictional Greenfield Demo School: items that are overdue,
 * due soon and later, a completed drill and certificates (placeholder PDFs), one expiring soon.
 * Runs once (skipped when the school already has items).
 */
@Component
@Order(32)
@ConditionalOnProperty(name = "shikshablox.seed.enabled", havingValue = "true")
class DemoComplianceSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoComplianceSeeder.class);

  private final OrganizationService organizations;
  private final ComplianceService compliance;
  private final JdbcClient jdbc;

  DemoComplianceSeeder(
      OrganizationService organizations, ComplianceService compliance, JdbcClient jdbc) {
    this.organizations = organizations;
    this.compliance = compliance;
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
            if (jdbc.sql("select count(*) from compliance_item").query(Long.class).single() > 0) {
              return null;
            }
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
            ComplianceService.Item fire =
                item(
                    "Fire safety certificate (NOC) renewal",
                    "SAFETY",
                    "Fire department",
                    "YEARLY",
                    today.plusDays(21));
            item(
                "Drinking water quality test",
                "HEALTH",
                "Approved laboratory",
                "QUARTERLY",
                today.minusDays(4));
            ComplianceService.Item drill =
                item("Fire / evacuation drill", "SAFETY", null, "QUARTERLY", today.minusDays(2));
            item(
                "Mandatory public disclosure review (website)",
                "DISCLOSURE",
                "Board",
                "HALF_YEARLY",
                today.plusDays(45));
            item(
                "UDISE+ annual data submission",
                "STATUTORY",
                "UDISE+",
                "YEARLY",
                today.plusDays(120));
            item(
                "TDS quarterly return",
                "FINANCE",
                "Income Tax Department",
                "QUARTERLY",
                today.plusDays(9));
            item(
                "School safety / POCSO committee meeting",
                "SAFETY",
                null,
                "QUARTERLY",
                today.plusDays(30));
            item(
                "School vehicle fitness, permit and insurance",
                "TRANSPORT",
                "Transport department",
                "YEARLY",
                today.plusDays(75));

            ComplianceService.Document minutes =
                compliance.upload(
                    new ComplianceService.DocumentInput(
                        drill.id(),
                        "Evacuation drill report",
                        "SAFETY",
                        null,
                        today.minusDays(1),
                        null),
                    pdf("drill-report.pdf"));
            compliance.complete(
                drill.id(),
                today.minusDays(1),
                "All classes evacuated in 4 minutes. (sample data)",
                minutes.id());
            compliance.upload(
                new ComplianceService.DocumentInput(
                    fire.id(),
                    "Fire NOC (current)",
                    "SAFETY",
                    "FS/DEMO/2025/114",
                    today.minusYears(1).plusDays(21),
                    today.plusDays(21)),
                pdf("fire-noc.pdf"));
            compliance.upload(
                new ComplianceService.DocumentInput(
                    null,
                    "Building safety certificate",
                    "SAFETY",
                    "BSC/DEMO/2024/07",
                    today.minusMonths(8),
                    today.plusMonths(16)),
                pdf("building-safety.pdf"));
            log.info("Seeded a sample compliance calendar for greenfield");
            return null;
          });
    } catch (RuntimeException e) {
      log.warn("Demo compliance seed skipped: {}", e.getMessage());
    }
  }

  private ComplianceService.Item item(
      String title, String category, String authority, String recurrence, LocalDate due) {
    return compliance.create(
        new ComplianceService.ItemInput(
            title,
            category,
            authority,
            "Sample item for the demo school.",
            recurrence,
            due,
            30,
            null));
  }

  private static ComplianceService.Upload pdf(String name) {
    String body =
        "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
            + "2 0 obj<</Type/Pages/Kids[]/Count 0>>endobj\n"
            + "trailer<</Root 1 0 R>>\n%% Sample document for the ShikshaBlox demo school\n%%EOF\n";
    return new ComplianceService.Upload(name, body.getBytes(StandardCharsets.US_ASCII));
  }
}
