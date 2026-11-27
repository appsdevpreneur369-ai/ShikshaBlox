package com.shikshablox.seed;

import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.OrganizationService;
import com.shikshablox.platform.tenancy.TenantContext;
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
 * Sample certificates for the fictional Greenfield Demo School: signatory and school details, an
 * issued bonafide and character certificate, and a bonafide request from the demo parent (Priya
 * Sharma) waiting in the register. Templates are created by the app on first use. Demo data only.
 */
@Component
@Order(40)
@ConditionalOnProperty(name = "shikshablox.seed.enabled", havingValue = "true")
class DemoCertificatesSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoCertificatesSeeder.class);
  static final String MARKER = "Passport application (sample)";

  private final OrganizationService organizations;
  private final JdbcClient jdbc;
  private final TransactionTemplate tx;

  DemoCertificatesSeeder(
      OrganizationService organizations, JdbcClient jdbc, TransactionTemplate tx) {
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
            if (jdbc.sql("select count(*) from certificate where purpose = ?")
                    .param(MARKER)
                    .query(Long.class)
                    .single()
                > 0) {
              return null;
            }
            tx.executeWithoutResult(s -> seed(org.id()));
            log.info("Seeded sample certificates for greenfield");
            return null;
          });
    } catch (RuntimeException e) {
      log.warn("Demo certificates seed skipped: {}", e.getMessage());
    }
  }

  private void seed(UUID org) {
    jdbc.sql("delete from certificate").update();
    jdbc.sql("delete from certificate_template").update();
    jdbc.sql(
            "insert into certificate_settings (organization_id, signatory_name, school_address, affiliation_no, school_code)"
                + " values (?, 'Dr. Kavita Rao', 'Demo campus (fictional school), Hyderabad – 500001, Telangana', 'DEMO-0000', 'GDS-01')"
                + " on conflict (organization_id) do update set signatory_name = excluded.signatory_name, school_address = excluded.school_address,"
                + " affiliation_no = excluded.affiliation_no, school_code = excluded.school_code")
        .param(org)
        .update();
    String[][] templates = {
      {
        "TC",
        "Transfer certificate",
        "Transfer Certificate",
        "Certified that the above information is in accordance with the school register.",
        "TC/",
        "false"
      },
      {
        "BONAFIDE",
        "Bonafide certificate",
        "Bonafide Certificate",
        "This is to certify that {{student.name}}, {{student.relation}} {{guardian.name}}, admission number {{student.admission_no}},"
            + " is a bonafide student of {{school.name}}, studying in {{student.class}} in the academic year {{year}}."
            + " {{student.possessive}} date of birth as per the school register is {{student.dob}}.\n\n"
            + "This certificate is issued on request for the purpose of {{purpose}}.",
        "BC/",
        "true"
      },
      {
        "CHARACTER",
        "Character certificate",
        "Character Certificate",
        "This is to certify that {{student.name}}, {{student.relation}} {{guardian.name}}, admission number {{student.admission_no}},"
            + " has been a student of {{school.name}} since {{student.admission_date}} and is in {{student.class}}.\n\n"
            + "To the best of our knowledge, {{student.possessive_lower}} conduct and character have been {{fields.conduct}}."
            + " We wish {{student.object}} every success.",
        "CC/",
        "true"
      },
      {
        "STUDY",
        "Study certificate",
        "Study Certificate",
        "This is to certify that {{student.name}}, {{student.relation}} {{guardian.name}}, admission number {{student.admission_no}},"
            + " studied in {{school.name}} from {{student.admission_date}} and is studying in {{student.class}} in the academic year"
            + " {{year}}. {{student.possessive}} date of birth as per the school register is {{student.dob}}.",
        "SC/",
        "true"
      }
    };
    int order = 0;
    for (String[] t : templates) {
      jdbc.sql(
              "insert into certificate_template (organization_id, kind, name, title, body, prefix, parent_requestable, sort_order)"
                  + " values (?, ?, ?, ?, ?, ?, ?, ?)")
          .params(org, t[0], t[1], t[2], t[3], t[4], Boolean.parseBoolean(t[5]), ++order)
          .update();
    }
    UUID saanvi = student("Saanvi Nair");
    UUID reyansh = student("Reyansh Rao");
    UUID aarav = student("Aarav Sharma");
    if (saanvi != null) {
      issued(
          org,
          "BONAFIDE",
          saanvi,
          "BC/0001",
          "DEMOBC0001",
          "This is to certify that Saanvi Nair, daughter of Arjun Nair, is a bonafide student of Greenfield Demo School,"
              + " studying in Class 5 A in the current academic year.\n\nThis certificate is issued on request for the purpose of"
              + " a scholarship application (sample).",
          "Scholarship application (sample)");
    }
    if (reyansh != null) {
      issued(
          org,
          "CHARACTER",
          reyansh,
          "CC/0001",
          "DEMOCC0001",
          "This is to certify that Reyansh Rao, son of Lakshmi Rao, has been a student of Greenfield Demo School and is in Class 5 A."
              + "\n\nTo the best of our knowledge, his conduct and character have been very good. We wish him every success.",
          "Inter-school competition (sample)");
    }
    jdbc.sql(
            "update certificate_template set next_number = 2 where kind in ('BONAFIDE', 'CHARACTER')")
        .update();
    if (aarav != null) {
      UUID guardian =
          jdbc.sql(
                  "select id from guardian where right(regexp_replace(phone, '\\D', '', 'g'), 10) = '9000050001'")
              .query(UUID.class)
              .optional()
              .orElse(null);
      jdbc.sql(
              "insert into certificate (organization_id, template_id, student_id, kind, title, status, purpose, requested_by_guardian)"
                  + " select ?, id, ?, kind, title, 'REQUESTED', ?, ? from certificate_template where kind = 'BONAFIDE'")
          .params(org, aarav, MARKER, guardian)
          .update();
    }
  }

  private UUID student(String name) {
    return jdbc.sql("select id from student where name = ?")
        .param(name)
        .query(UUID.class)
        .optional()
        .orElse(null);
  }

  private void issued(
      UUID org,
      String kind,
      UUID student,
      String number,
      String code,
      String body,
      String purpose) {
    String fields = "CHARACTER".equals(kind) ? "{\"conduct\":\"very good\"}" : "{}";
    jdbc.sql(
            "insert into certificate (organization_id, template_id, student_id, kind, title, number, verify_code, status, purpose,"
                + " fields, body, issued_at) select ?, id, ?, kind, title, ?, ?, 'ISSUED', ?, ?::jsonb, ?, now() - interval '3 days'"
                + " from certificate_template where kind = ?")
        .params(org, student, number, code, purpose, fields, body, kind)
        .update();
  }
}
