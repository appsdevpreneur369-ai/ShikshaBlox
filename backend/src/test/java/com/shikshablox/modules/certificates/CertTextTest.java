package com.shikshablox.modules.certificates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shikshablox.platform.shared.web.ApiException;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CertTextTest {

  @Test
  void datesInWords() {
    assertThat(CertText.dateInWords(LocalDate.of(2020, 10, 28)))
        .isEqualTo("Twenty-Eighth October Two Thousand Twenty");
    assertThat(CertText.dateInWords(LocalDate.of(2019, 3, 1)))
        .isEqualTo("First March Two Thousand Nineteen");
    assertThat(CertText.dateInWords(LocalDate.of(2011, 1, 30)))
        .isEqualTo("Thirtieth January Two Thousand Eleven");
    assertThat(CertText.dateInWords(LocalDate.of(1999, 12, 31)))
        .isEqualTo("Thirty-First December One Thousand Nine Hundred Ninety-Nine");
    assertThat(CertText.dateInWords(null)).isEmpty();
  }

  @Test
  void mergeFieldsAndBlanksToFillByHand() {
    String out =
        CertText.render(
            "{{student.name}}, {{ student.relation }} {{guardian.name}}, for {{purpose}}.",
            Map.of(
                "student.name",
                "Tara Sharma",
                "student.relation",
                "daughter of",
                "guardian.name",
                "Priya Sharma",
                "purpose",
                ""));
    assertThat(out).isEqualTo("Tara Sharma, daughter of Priya Sharma, for ________.");
  }

  @Test
  void tcFieldsAreLimitedAndNamed() {
    var tc = new CertificateService.Template(null, "TC", "TC", "TC", "x", "TC/", 1, false, true);
    Map<String, String> f =
        CertificateService.cleanFields(tc, Map.of("name", " Aarav ", "hack", "x"));
    assertThat(f).containsEntry("name", "Aarav").doesNotContainKey("hack");
    assertThatThrownBy(() -> CertificateService.cleanFields(tc, Map.of("conduct", "Good")))
        .isInstanceOf(ApiException.class);
    assertThat(CertificateService.code()).hasSize(10).matches("^[A-Z2-9]+$");
    assertThat(CertText.TC_FIELDS).hasSize(23);
  }
}
