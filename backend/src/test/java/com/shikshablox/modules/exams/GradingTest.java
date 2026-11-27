package com.shikshablox.modules.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shikshablox.platform.shared.web.ApiException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GradingTest {

  private static final List<Grading.Component> HY =
      List.of(
          new Grading.Component("TH", "Theory", 80), new Grading.Component("IA", "Internal", 20));
  private static final List<Grading.Band> CBSE =
      List.of(
          new Grading.Band(91, "A1", 10),
          new Grading.Band(81, "A2", 9),
          new Grading.Band(71, "B1", 8),
          new Grading.Band(33, "D", 4),
          new Grading.Band(0, "E", 0));

  @Test
  void totalsPercentAndGrade() {
    Grading.Result r = Grading.result(Map.of("TH", 62.5, "IA", 18.0), null, HY, CBSE);
    assertThat(r.total()).isEqualTo(80.5);
    assertThat(r.percent()).isEqualTo(80.5);
    assertThat(r.grade()).isEqualTo("B1");
    assertThat(Grading.result(Map.of("TH", 75.0, "IA", 20.0), null, HY, CBSE).grade())
        .isEqualTo("A1");
  }

  @Test
  void absentCountsAsZeroMedicalIsLeftOutPartialShowsNothing() {
    assertThat(Grading.result(Map.of(), "AB", HY, CBSE).percent()).isZero();
    Grading.Result ml = Grading.result(Map.of(), "ML", HY, CBSE);
    assertThat(ml.percent()).isNull();
    assertThat(ml.total()).isNull();
    assertThat(Grading.result(Map.of("TH", 70.0), null, HY, CBSE).percent()).isNull();
    Grading.Result a = Grading.result(Map.of("TH", 40.0, "IA", 10.0), null, HY, CBSE);
    assertThat(Grading.overall(List.of(a, ml))).isEqualTo(50.0);
  }

  @Test
  void passNeedsEverySubjectAtThePassMark() {
    Grading.Result good = Grading.result(Map.of("TH", 60.0, "IA", 15.0), null, HY, CBSE);
    Grading.Result low = Grading.result(Map.of("TH", 20.0, "IA", 10.0), null, HY, CBSE);
    assertThat(Grading.passed(List.of(good), 33)).isTrue();
    assertThat(Grading.passed(List.of(good, low), 33)).isFalse();
    assertThat(Grading.passed(List.of(good, Grading.result(Map.of(), "AB", HY, CBSE)), 33))
        .isFalse();
    assertThat(Grading.passed(List.of(), 33)).isNull();
  }

  @Test
  void weightsSkipExamsWithoutAResult() {
    assertThat(Grading.weighted(List.of(80.0, 60.0), List.of(10, 40))).isEqualTo(64.0);
    assertThat(Grading.weighted(Arrays.asList(80.0, null), List.of(10, 40))).isEqualTo(80.0);
    assertThat(Grading.weighted(List.of(80.0), List.of(0))).isNull();
  }

  @Test
  void marksAreCheckedAgainstTheParts() {
    assertThat(Grading.validate(Map.of("TH", 79.5), HY, "Aarav")).containsEntry("TH", 79.5);
    assertThatThrownBy(() -> Grading.validate(Map.of("TH", 81.0), HY, "Aarav"))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> Grading.validate(Map.of("TH", 10.25), HY, "Aarav"))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> Grading.validate(Map.of("XX", 1.0), HY, "Aarav"))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void componentsDefaultToOneHundredMarksAndAreValidated() {
    assertThat(Grading.components(null)).containsExactly(new Grading.Component("M", "Marks", 100));
    List<Grading.Component> c =
        Grading.components(
            List.of(
                Map.of("key", "th", "label", "Theory", "max", 80),
                Map.of("label", "Practical", "max", "20")));
    assertThat(c).extracting(Grading.Component::key).containsExactly("TH", "P2");
    assertThatThrownBy(() -> Grading.components(List.of(Map.of("label", "X", "max", 500))))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void boardPresetsAndBands() {
    assertThat(ExamService.preset("CBSE", "Class 3"))
        .extracting(p -> p[0])
        .contains("Hindi", "Environmental Studies", "Art Education");
    assertThat(ExamService.preset("ICSE", "Class 7"))
        .extracting(p -> p[0])
        .contains("Physics", "History & Civics");
    assertThat(ExamService.preset("CBSE", "LKG")).isEmpty();
    assertThatThrownBy(() -> ExamService.cleanBands(List.of(new Grading.Band(50, "P", 1))))
        .isInstanceOf(ApiException.class);
    assertThat(
            ExamService.cleanBands(
                List.of(new Grading.Band(0, "F", 0), new Grading.Band(40, "P", 1))))
        .extracting(Grading.Band::grade)
        .containsExactly("P", "F");
  }
}
