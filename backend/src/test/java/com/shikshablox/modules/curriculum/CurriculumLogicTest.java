package com.shikshablox.modules.curriculum;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CurriculumLogicTest {

  @Test
  void schoolYearStartsInApril() {
    assertThat(CurriculumService.schoolMonth(4)).isZero();
    assertThat(CurriculumService.schoolMonth(3)).isEqualTo(11);
    assertThat(CurriculumService.schoolMonth(12)).isEqualTo(8);
  }

  @Test
  void aUnitIsBehindWhenItsMonthHasPassedWithTopicsLeft() {
    LocalDate nov = LocalDate.of(2026, 11, 27);
    assertThat(CurriculumService.behind(10, 3, 2, nov)).isTrue();
    assertThat(CurriculumService.behind(10, 3, 3, nov)).isFalse();
    assertThat(CurriculumService.behind(11, 3, 0, nov)).isFalse();
    assertThat(CurriculumService.behind(2, 3, 0, nov))
        .isFalse(); // February is later in the school year
    assertThat(CurriculumService.behind(null, 3, 0, nov)).isFalse();
  }
}
