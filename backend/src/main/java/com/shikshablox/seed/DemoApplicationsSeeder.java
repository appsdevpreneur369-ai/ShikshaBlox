package com.shikshablox.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shikshablox.modules.admissions.ApplicationService;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.OrganizationService;
import com.shikshablox.platform.tenancy.TenantContext;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * Opens the online application form for the fictional Greenfield Demo School and adds two sample
 * applications with placeholder PDF documents. Runs once (skipped when settings exist).
 */
@Component
@Order(31)
@ConditionalOnProperty(name = "shikshablox.seed.enabled", havingValue = "true")
class DemoApplicationsSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoApplicationsSeeder.class);

  private final OrganizationService organizations;
  private final ApplicationService applications;
  private final JdbcClient jdbc;
  private final ObjectMapper json;

  DemoApplicationsSeeder(
      OrganizationService organizations,
      ApplicationService applications,
      JdbcClient jdbc,
      ObjectMapper json) {
    this.organizations = organizations;
    this.applications = applications;
    this.jdbc = jdbc;
    this.json = json;
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
                jdbc.sql("select count(*) from admission_form_settings").query(Long.class).single();
            if (existing > 0) {
              return null;
            }
            ApplicationService.FormSettings defaults = applications.settings();
            applications.saveSettings(
                new ApplicationService.FormSettings(
                    true,
                    List.of(),
                    defaults.documents(),
                    500,
                    "Pay ₹500 at the school office (cash or UPI) or by bank transfer, quoting your"
                        + " application number. Demo school: no real payment is needed.",
                    "Fill in your child's details and upload the documents below. The admissions"
                        + " office will call you within two working days."));
            sample("Riya", "Anjali Mehta", "+91 90000 10001", "Class 1", 6, true);
            sample("Arjun", "Deepak Joshi", "+91 90000 10002", "LKG", 4, false);
            log.info("Seeded the online application form and 2 sample applications for greenfield");
            return null;
          });
    } catch (RuntimeException e) {
      log.warn("Demo applications seed skipped: {}", e.getMessage());
    }
  }

  private void sample(
      String student, String guardian, String phone, String grade, int age, boolean allDocs) {
    ObjectNode in = json.createObjectNode();
    in.put("studentName", student + " " + guardian.substring(guardian.indexOf(' ') + 1));
    in.put(
        "dateOfBirth",
        LocalDate.now(ZoneId.of("Asia/Kolkata")).minusYears(age).minusDays(40).toString());
    in.put("gender", student.endsWith("a") ? "FEMALE" : "MALE");
    in.put("grade", grade);
    in.put("guardianName", guardian);
    in.put("relationship", "Mother");
    in.put("phone", phone);
    in.put("email", guardian.toLowerCase().replace(' ', '.') + "@example.test");
    in.put("address", "12 Sample Street, Demo Nagar (sample data)");
    in.put("consent", true);
    List<ApplicationService.IncomingFile> files =
        allDocs
            ? List.of(
                pdf("birth_certificate", "birth-certificate.pdf"),
                pdf("photo", "photo.pdf"),
                pdf("address_proof", "address-proof.pdf"))
            : List.of(pdf("birth_certificate", "birth-certificate.pdf"), pdf("photo", "photo.pdf"));
    applications.submitInCurrentSchool(in, files);
  }

  private static ApplicationService.IncomingFile pdf(String code, String name) {
    String body =
        "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
            + "2 0 obj<</Type/Pages/Kids[]/Count 0>>endobj\n"
            + "trailer<</Root 1 0 R>>\n%% Sample document for the ShikshaBlox demo school\n%%EOF\n";
    return new ApplicationService.IncomingFile(
        code, name, body.getBytes(StandardCharsets.US_ASCII));
  }
}
