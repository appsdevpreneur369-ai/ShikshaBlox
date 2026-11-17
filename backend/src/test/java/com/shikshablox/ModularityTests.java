package com.shikshablox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Fails the build if any module reaches into another module's internals. */
class ModularityTests {

  private final ApplicationModules modules = ApplicationModules.of(ShikshaBloxApplication.class);

  @Test
  void verifiesModuleBoundaries() {
    modules.verify();
  }

  @Test
  void detectsDeclaredModules() {
    assertThat(modules.stream().map(m -> m.getIdentifier().toString()))
        .contains(
            "shared",
            "tenancy",
            "identity",
            "entitlement",
            "config",
            "audit",
            "files",
            "consent",
            "notification",
            "website",
            "disclosure",
            "admissions",
            "recruitment");
  }

  @Test
  void writesModuleDocumentation() {
    new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
  }
}
