package com.shikshablox.modules.sis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shikshablox.platform.shared.web.ApiException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SisLogicTest {

  @Test
  void schoolYearRunsAprilToMarch() {
    assertThat(SisService.defaultYear(LocalDate.of(2026, 11, 27))).isEqualTo("2026-27");
    assertThat(SisService.defaultYear(LocalDate.of(2027, 3, 31))).isEqualTo("2026-27");
    assertThat(SisService.defaultYear(LocalDate.of(2027, 4, 1))).isEqualTo("2027-28");
    assertThat(SisService.nextYear("2026-27")).isEqualTo("2027-28");
    assertThat(SisService.nextYear("2099-00")).isEqualTo("2100-01");
    assertThat(SisService.year("2026-27")).isEqualTo("2026-27");
    assertThatThrownBy(() -> SisService.year("2026-28")).isInstanceOf(ApiException.class);
  }

  @Test
  void sectionLabelsDoubleAsMessageTags() {
    assertThat(SisService.label("Class 3", "A")).isEqualTo("Class 3 A");
    assertThat(SisService.label("LKG", "B")).isEqualTo("LKG B");
    assertThat(SisService.label(" Nursery ", "A")).isEqualTo("Nursery A");
  }

  @Test
  void importAcceptsIndianDatesAndCommonGenderCodes() {
    assertThat(SisOperations.date("15-06-2019")).isEqualTo(LocalDate.of(2019, 6, 15));
    assertThat(SisOperations.date("15/06/2019")).isEqualTo(LocalDate.of(2019, 6, 15));
    assertThat(SisOperations.date("2019-06-15")).isEqualTo(LocalDate.of(2019, 6, 15));
    assertThat(SisOperations.date(null)).isNull();
    assertThatThrownBy(() -> SisOperations.date("31-02-2019")).isInstanceOf(ApiException.class);
    assertThat(SisOperations.gender("f")).isEqualTo("FEMALE");
    assertThat(SisOperations.gender("Boy")).isEqualTo("MALE");
    assertThatThrownBy(() -> SisOperations.gender("x")).isInstanceOf(ApiException.class);
    assertThat(SisOperations.relationCode("mother")).isEqualTo("MOTHER");
    assertThat(SisOperations.relationCode("uncle")).isEqualTo("GUARDIAN");
    assertThat(SisOperations.split("A1,\"Rao, Aarav\",Class 3"))
        .containsExactly("A1", "Rao, Aarav", "Class 3");
  }

  @Test
  void customFieldKeysComeFromLabels() {
    assertThat(SisService.keyOf("Mother tongue")).isEqualTo("mother_tongue");
    assertThat(SisService.keyOf("2nd language")).isEqualTo("f_2nd_language");
  }
}
