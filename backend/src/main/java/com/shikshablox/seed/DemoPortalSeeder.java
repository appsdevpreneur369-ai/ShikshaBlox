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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * A demo parent account for the fictional Greenfield Demo School: Priya Sharma (+91 90000 50001,
 * PIN 2468), mother of Aarav (Class 1 A) and Tara (LKG A). Demo data only.
 */
@Component
@Order(37)
@ConditionalOnProperty(name = "shikshablox.seed.enabled", havingValue = "true")
class DemoPortalSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoPortalSeeder.class);
  static final String DEMO_PHONE_KEY = "9000050001";
  static final String DEMO_PIN = "2468";

  private final OrganizationService organizations;
  private final JdbcClient jdbc;
  private final PasswordEncoder passwords;

  DemoPortalSeeder(OrganizationService organizations, JdbcClient jdbc, PasswordEncoder passwords) {
    this.organizations = organizations;
    this.jdbc = jdbc;
    this.passwords = passwords;
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
            UUID guardian =
                jdbc.sql(
                        "select id from guardian where right(regexp_replace(phone, '\\D', '', 'g'), 10) = ?")
                    .param(DEMO_PHONE_KEY)
                    .query(UUID.class)
                    .optional()
                    .orElse(null);
            if (guardian == null) {
              return null;
            }
            int n =
                jdbc.sql(
                        "insert into parent_account (organization_id, guardian_id, status, pin_hash) values (?, ?, 'ACTIVE', ?)"
                            + " on conflict (guardian_id) do nothing")
                    .params(org.id(), guardian, passwords.encode(DEMO_PIN))
                    .update();
            if (n > 0) {
              log.info("Seeded a demo parent portal account for greenfield");
            }
            return null;
          });
    } catch (RuntimeException e) {
      log.warn("Demo parent portal seed skipped: {}", e.getMessage());
    }
  }
}
