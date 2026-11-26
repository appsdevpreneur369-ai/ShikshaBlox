package com.shikshablox.modules.disclosure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ComplianceServiceTest {

  private static final LocalDate D = LocalDate.of(2026, 11, 30);

  @Test
  void recurringItemsMoveToTheNextCycleAfterTheDueDate() {
    assertThat(ComplianceService.nextDue("YEARLY", D, D.minusDays(3))).isEqualTo(D.plusYears(1));
    assertThat(ComplianceService.nextDue("QUARTERLY", D, D)).isEqualTo(D.plusMonths(3));
    assertThat(ComplianceService.nextDue("HALF_YEARLY", D, D)).isEqualTo(D.plusMonths(6));
    // Month ends clamp (Nov 30 -> Dec 30).
    assertThat(ComplianceService.nextDue("MONTHLY", D, D)).isEqualTo(LocalDate.of(2026, 12, 30));
  }

  @Test
  void lateCompletionSkipsCyclesAlreadyInThePast() {
    // Monthly item due Nov 30, finally done on Feb 10: next due Feb 28, not Dec 30.
    assertThat(ComplianceService.nextDue("MONTHLY", D, LocalDate.of(2027, 2, 10)))
        .isEqualTo(LocalDate.of(2027, 2, 28));
    assertThat(ComplianceService.nextDue("YEARLY", D, D.plusYears(2))).isEqualTo(D.plusYears(3));
  }

  @Test
  void oneTimeItemsCloseAndUndatedItemsStartFromCompletion() {
    assertThat(ComplianceService.nextDue("ONCE", D, D)).isNull();
    assertThat(ComplianceService.nextDue("QUARTERLY", null, D)).isEqualTo(D.plusMonths(3));
  }

  @Test
  void exportNamesAndCellsAreSafe() {
    assertThat(ComplianceService.safe("Fire NOC 2026/27: main block"))
        .isEqualTo("Fire-NOC-202627-main-block");
    assertThat(ComplianceService.safe("///")).isEqualTo("document");
    assertThat(ComplianceService.cell("=1+1")).isEqualTo("\"'=1+1\"");
  }
}
