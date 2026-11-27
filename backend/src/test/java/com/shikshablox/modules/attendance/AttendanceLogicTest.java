package com.shikshablox.modules.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shikshablox.platform.shared.web.ApiException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AttendanceLogicTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 27);

  @Test
  void teachersMarkTodayAndForgottenDaysButCorrectPastDaysThroughTheOffice() {
    assertThat(AttendanceService.editMode(TODAY, true, TODAY, false, true)).isEqualTo("DIRECT");
    assertThat(AttendanceService.editMode(TODAY.plusDays(1), false, TODAY, true, true))
        .isEqualTo("READ_ONLY");
    assertThat(AttendanceService.editMode(TODAY.minusDays(3), false, TODAY, false, true))
        .isEqualTo("DIRECT");
    assertThat(AttendanceService.editMode(TODAY.minusDays(30), false, TODAY, false, true))
        .isEqualTo("READ_ONLY");
    assertThat(AttendanceService.editMode(TODAY.minusDays(1), true, TODAY, false, true))
        .isEqualTo("CORRECTION");
    assertThat(AttendanceService.editMode(TODAY.minusDays(1), true, TODAY, false, false))
        .isEqualTo("READ_ONLY");
    assertThat(AttendanceService.editMode(TODAY.minusDays(40), true, TODAY, true, true))
        .isEqualTo("DIRECT");
  }

  @Test
  void lateCountsAsPresentHalfDayAsHalfAndLeaveAsNotPresent() {
    assertThat(AttendanceService.percent(AttendanceService.counts(List.of("P", "P", "L", "A"))))
        .isEqualTo(75.0);
    assertThat(AttendanceService.percent(AttendanceService.counts(List.of("P", "H"))))
        .isEqualTo(75.0);
    assertThat(AttendanceService.percent(AttendanceService.counts(List.of("P", "LV", "P"))))
        .isEqualTo(66.7);
    assertThat(AttendanceService.percent(AttendanceService.counts(List.of()))).isNull();
  }

  @Test
  void streakCountsTrailingAbsencesAndLeaveDoesNotBreakIt() {
    assertThat(AttendanceService.streak(List.of("P", "A", "A", "A"))).isEqualTo(3);
    assertThat(AttendanceService.streak(List.of("A", "A", "LV", "A"))).isEqualTo(3);
    assertThat(AttendanceService.streak(List.of("A", "A", "P"))).isZero();
    assertThat(AttendanceService.streak(List.of())).isZero();
  }

  @Test
  void parentsApplyUpToAWeekBackAndThirtyDaysAtATime() {
    LocalDate[] r = LeaveService.range(TODAY.plusDays(3), null, false, TODAY);
    assertThat(r[0]).isEqualTo(r[1]);
    assertThat(LeaveService.range(TODAY.minusDays(7), TODAY, false, TODAY)).hasSize(2);
    assertThatThrownBy(() -> LeaveService.range(TODAY.minusDays(8), TODAY, false, TODAY))
        .isInstanceOf(ApiException.class);
    assertThat(LeaveService.range(TODAY.minusDays(20), TODAY.minusDays(19), true, TODAY))
        .hasSize(2);
    assertThatThrownBy(() -> LeaveService.range(TODAY, TODAY.plusDays(30), false, TODAY))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> LeaveService.range(TODAY, TODAY.minusDays(1), false, TODAY))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> LeaveService.range(null, TODAY, false, TODAY))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void registerCellsAreSafeForSpreadsheets() {
    assertThat(AttendanceService.cell("=SUM(A1)")).isEqualTo("'=SUM(A1)");
    assertThat(AttendanceService.cell("Rao, Reyansh")).isEqualTo("\"Rao, Reyansh\"");
    assertThat(AttendanceService.temporaryPassword()).hasSize(14).matches("^[A-Za-z2-9]+$");
  }
}
