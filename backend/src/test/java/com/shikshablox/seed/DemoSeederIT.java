package com.shikshablox.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikshablox.platform.entitlement.EntitlementService;
import com.shikshablox.platform.tenancy.OrganizationService;
import com.shikshablox.platform.tenancy.TenantContext;
import com.shikshablox.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {"shikshablox.seed.enabled=true", "shikshablox.seed.password=Seed-Password-123"})
class DemoSeederIT extends PostgresTestSupport {

  @Autowired DemoSeeder seeder;
  @Autowired OrganizationService organizations;
  @Autowired EntitlementService entitlements;

  private String planOf(String slug) {
    var org = organizations.findBySlug(slug).orElseThrow();
    return TenantContext.callInTenant(org.id(), () -> entitlements.currentPlan().orElse(null));
  }

  @Test
  void seedsDemoSchoolsWithTheirPlansAndIsIdempotent() {
    assertThat(planOf("moriah")).isEqualTo("WEB_PRO");
    assertThat(planOf("horizon")).isEqualTo("WEB_PRO");
    assertThat(planOf("greenfield")).isEqualTo("WEB_FREE");
    assertThat(planOf("sunrise")).isEqualTo("WEB_FREE");

    seeder.run(null); // second run changes nothing and does not fail
    assertThat(planOf("moriah")).isEqualTo("WEB_PRO");
    assertThat(organizations.listAll().stream().filter(o -> o.slug().equals("moriah"))).hasSize(1);
  }
}
