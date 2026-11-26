package com.shikshablox.modules.admissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shikshablox.platform.shared.web.ApiException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ApplicationServiceTest {

  @Test
  void documentCodesAreSlugsOfTheirLabels() {
    assertThat(ApplicationService.slug("Birth certificate")).isEqualTo("birth_certificate");
    assertThat(ApplicationService.slug("Previous report card / TC"))
        .isEqualTo("previous_report_card_tc");
    assertThat(ApplicationService.slug("  ")).isEqualTo("document");
    assertThat(ApplicationService.slug("x".repeat(60))).hasSize(40);
  }

  @Test
  void dateOfBirthMustBeAPlausibleSchoolAge() {
    LocalDate fiveYearsAgo = LocalDate.now(AdmissionsService.IST).minusYears(5);
    assertThat(ApplicationService.dateOfBirth(fiveYearsAgo.toString())).isEqualTo(fiveYearsAgo);
    assertThatThrownBy(() -> ApplicationService.dateOfBirth("12/05/2020"))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> ApplicationService.dateOfBirth(LocalDate.now().plusDays(3).toString()))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () -> ApplicationService.dateOfBirth(LocalDate.now().minusYears(30).toString()))
        .isInstanceOf(ApiException.class);
  }
}
