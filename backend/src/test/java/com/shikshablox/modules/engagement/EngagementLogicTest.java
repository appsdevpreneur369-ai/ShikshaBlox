package com.shikshablox.modules.engagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shikshablox.platform.shared.web.ApiException;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class EngagementLogicTest {

  private final ObjectMapper json = new ObjectMapper();

  @Test
  void ptmSlotsFitInsideTheWindow() {
    assertThat(PtmService.slots(LocalTime.of(9, 0), LocalTime.of(9, 50), 15))
        .containsExactly(LocalTime.of(9, 0), LocalTime.of(9, 15), LocalTime.of(9, 30));
    assertThat(PtmService.slots(LocalTime.of(9, 0), LocalTime.of(9, 5), 10)).isEmpty();
    assertThat(PtmService.slots(LocalTime.of(23, 30), LocalTime.of(23, 59), 10)).hasSize(2);
  }

  @Test
  void helpdeskSlaByPriority() {
    assertThat(HelpdeskService.sla("HIGH")).isEqualTo(Duration.ofHours(24));
    assertThat(HelpdeskService.sla("NORMAL")).isEqualTo(Duration.ofDays(3));
    assertThat(HelpdeskService.sla("LOW")).isEqualTo(Duration.ofDays(7));
  }

  @Test
  void phoneKeysAndCodes() {
    assertThat(Inputs.phoneKey("+91 98480-22338")).isEqualTo("9848022338");
    assertThat(Inputs.phoneKey("098480 22338")).isEqualTo("9848022338");
    assertThat(Inputs.code(6)).hasSize(6).doesNotContainPattern("[01OIL]");
  }

  @Test
  void surveyQuestionsGetIdsAndValidOptions() {
    List<SurveyService.Question> qs =
        SurveyService.questions(
            List.of(
                new SurveyService.Question(null, "RATING", "How was the PTM?", null, true),
                new SurveyService.Question(
                    null, "CHOICE", "Best day?", List.of("Sat", " Sat", "Sun", ""), false),
                new SurveyService.Question("q1", "TEXT", "Anything else?", null, false)));
    assertThat(qs).extracting(SurveyService.Question::id).containsExactly("q1", "q2", "q3");
    assertThat(qs.get(1).options()).containsExactly("Sat", "Sun");
    assertThatThrownBy(() ->
            SurveyService.questions(
                List.of(
                    new SurveyService.Question(null, "CHOICE", "One option", List.of("A"), false))))
        .isInstanceOf(ApiException.class);
    assertThat(SurveyService.slugOf("Annual Day Feedback 2026!"))
        .isEqualTo("annual-day-feedback-2026");
  }

  @Test
  void surveyAnswersAreCheckedAndAggregated() throws Exception {
    List<SurveyService.Question> qs =
        List.of(
            new SurveyService.Question("q1", "RATING", "Rate", List.of(), true),
            new SurveyService.Question("q2", "MULTI", "Pick", List.of("A", "B", "C"), false),
            new SurveyService.Question("q3", "TEXT", "Say", List.of(), false));
    JsonNode ok =
        SurveyService.validateAnswers(
            qs, json.readTree("{\"q1\":4,\"q2\":[\"A\",\"C\",\"A\"],\"x\":1}"), json);
    assertThat(ok.toString()).isEqualTo("{\"q1\":4,\"q2\":[\"A\",\"C\"]}");
    assertThatThrownBy(() -> SurveyService.validateAnswers(qs, json.readTree("{\"q1\":6}"), json))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> SurveyService.validateAnswers(qs, json.readTree("{}"), json))
        .isInstanceOf(ApiException.class);

    List<JsonNode> answers =
        List.of(
            json.readTree("{\"q1\":4,\"q2\":[\"A\"]}"),
            json.readTree("{\"q1\":5,\"q3\":\"Great\"}"));
    SurveyService.QuestionResult rating = SurveyService.aggregate(qs.get(0), answers);
    assertThat(rating.average()).isEqualTo(4.5);
    assertThat(rating.counts())
        .containsEntry("4", 1L)
        .containsEntry("5", 1L)
        .containsEntry("1", 0L);
    assertThat(SurveyService.aggregate(qs.get(1), answers).counts())
        .containsEntry("A", 1L)
        .containsEntry("B", 0L);
    assertThat(SurveyService.aggregate(qs.get(2), answers).texts()).containsExactly("Great");
  }
}
