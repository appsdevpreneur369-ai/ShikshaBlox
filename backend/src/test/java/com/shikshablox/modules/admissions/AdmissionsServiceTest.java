package com.shikshablox.modules.admissions;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AdmissionsServiceTest {

  @Test
  void admissionsYearRollsOverInOctober() {
    assertThat(AdmissionsService.currentAcademicYear(LocalDate.of(2026, 11, 26)))
        .isEqualTo("2027-28");
    assertThat(AdmissionsService.currentAcademicYear(LocalDate.of(2027, 2, 1)))
        .isEqualTo("2027-28");
    assertThat(AdmissionsService.currentAcademicYear(LocalDate.of(2027, 7, 15)))
        .isEqualTo("2027-28");
    assertThat(AdmissionsService.currentAcademicYear(LocalDate.of(2027, 10, 1)))
        .isEqualTo("2028-29");
    assertThat(AdmissionsService.currentAcademicYear(LocalDate.of(2099, 12, 1)))
        .isEqualTo("2100-01");
  }

  @Test
  void csvCellsAreQuotedAndFormulaSafe() {
    assertThat(AdmissionsService.cell(null)).isEmpty();
    assertThat(AdmissionsService.cell("Ravi \"K\"")).isEqualTo("\"Ravi \"\"K\"\"\"");
    assertThat(AdmissionsService.cell("=HYPERLINK(1)")).isEqualTo("\"'=HYPERLINK(1)\"");
    assertThat(AdmissionsService.cell("+91 90000 00000")).isEqualTo("\"'+91 90000 00000\"");
  }
}
