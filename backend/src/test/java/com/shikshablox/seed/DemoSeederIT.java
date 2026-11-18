package com.shikshablox.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikshablox.modules.website.ContentService;
import com.shikshablox.modules.website.NavigationService;
import com.shikshablox.modules.website.PageService;
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
  @Autowired PageService pages;
  @Autowired NavigationService navigation;
  @Autowired ContentService content;

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

  @Test
  void seedsMoriahWebsiteWithLeadershipHomeAndTeachers() {
    var org = organizations.findBySlug("moriah").orElseThrow();
    TenantContext.callInTenant(
        org.id(),
        () -> {
          var list = pages.list("en");
          assertThat(list)
              .filteredOn(p -> p.slug().equals("leadership") || p.slug().equals("moriah-home"))
              .hasSize(2)
              .allSatisfy(p -> assertThat(p.status()).isEqualTo("PUBLISHED"));
          assertThat(list)
              .filteredOn(p -> "faculty".equals(p.templateKey()))
              .singleElement()
              .satisfies(p -> assertThat(p.title()).isEqualTo("Our Teachers"));
          assertThat(navigation.menu("header").toString())
              .contains("Founders & Leadership", "Moriah Home", "Our Teachers");
          assertThat(content.list("staff")).hasSize(12);
          assertThat(content.list("albums")).hasSizeGreaterThanOrEqualTo(7);
          return null;
        });
  }
}
