package com.shikshablox.seed;

import com.shikshablox.platform.entitlement.EntitlementService;
import com.shikshablox.platform.identity.Role;
import com.shikshablox.platform.identity.UserService;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.OrganizationService;
import com.shikshablox.platform.tenancy.TenantContext;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Idempotent seed: a platform administrator, Moriah School (first customer) and three demo schools,
 * each with one user per school role ({@code <role>@<slug>.test}). The {@code .test} domain is
 * reserved, so no seeded address can reach a real person.
 */
@Component
@Order(10)
@ConditionalOnProperty(name = "shikshablox.seed.enabled", havingValue = "true")
class DemoSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

  record School(String slug, String name, String plan) {}

  static final List<School> SCHOOLS =
      List.of(
          new School("moriah", "Moriah School", "WEB_PRO"),
          new School("greenfield", "Greenfield Public School", "WEB_FREE"),
          new School("sunrise", "Sunrise High School", "WEB_FREE"),
          new School("horizon", "Horizon International School", "WEB_PRO"));

  private final OrganizationService organizations;
  private final UserService users;
  private final EntitlementService entitlements;
  private final String password;
  private final String superadminEmail;
  private final String extraDomains;

  DemoSeeder(
      OrganizationService organizations,
      UserService users,
      EntitlementService entitlements,
      @Value("${shikshablox.seed.password:}") String password,
      @Value("${shikshablox.seed.superadmin-email}") String superadminEmail,
      @Value("${shikshablox.seed.extra-domains:}") String extraDomains) {
    this.organizations = organizations;
    this.users = users;
    this.entitlements = entitlements;
    this.password = password;
    this.superadminEmail = superadminEmail;
    this.extraDomains = extraDomains;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (password == null || password.length() < 10) {
      log.warn("Seeding skipped: set SEED_PASSWORD (10+ characters)");
      return;
    }
    UUID admin = users.ensureUser(superadminEmail, "Platform Administrator", password);
    users.grantRole(admin, null, Role.PLATFORM_SUPER_ADMIN);

    for (School s : SCHOOLS) {
      Organization org =
          organizations
              .findBySlug(s.slug())
              .orElseGet(() -> organizations.create(s.slug(), s.name()));
      String plan =
          TenantContext.callInTenant(org.id(), () -> entitlements.currentPlan().orElse(null));
      if (!s.plan().equals(plan)) {
        entitlements.applyPlan(org.id(), s.plan());
      }
      seedUser(org, "admin", "School Administrator", Role.SCHOOL_ADMIN);
      seedUser(org, "editor", "Content Editor", Role.CONTENT_EDITOR);
      seedUser(org, "approver", "Content Approver", Role.CONTENT_APPROVER);
      seedUser(org, "viewer", "Viewer", Role.VIEWER);
    }

    for (String pair : extraDomains.split(",")) {
      String[] kv = pair.trim().split("=", 2);
      if (kv.length == 2 && !kv[1].isBlank()) {
        organizations
            .findBySlug(kv[0].trim())
            .ifPresent(o -> organizations.addVerifiedDomain(o.id(), kv[1].trim()));
      }
    }
    log.info("Seed data ready ({} schools)", SCHOOLS.size());
  }

  private void seedUser(Organization org, String local, String name, Role role) {
    UUID id = users.ensureUser(local + "@" + org.slug() + ".test", name, password);
    users.grantRole(id, org.id(), role);
  }
}
