package com.shikshablox.modules.comms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shikshablox.platform.shared.web.ApiException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommsLogicTest {

  private static final CommsService.Settings QUIET_9PM_7AM =
      new CommsService.Settings(LocalTime.of(21, 0), LocalTime.of(7, 0), 5000, "TEST", 0, 0, 0, 0);

  private static BroadcastService.Broadcast broadcast(boolean emergency, Instant sendAt) {
    return new BroadcastService.Broadcast(
        UUID.randomUUID(),
        "t",
        null,
        "x",
        List.of("EMAIL"),
        List.of(),
        false,
        null,
        emergency,
        "DRAFT",
        sendAt,
        null,
        Instant.now(),
        null);
  }

  private static Instant ist(int y, int mo, int d, int h, int mi) {
    return ZonedDateTime.of(y, mo, d, h, mi, 0, 0, BroadcastService.IST).toInstant();
  }

  @Test
  void quietHoursDeferToSevenAm() {
    Instant late = ist(2026, 11, 27, 22, 30);
    assertThat(BroadcastService.whenToSend(broadcast(false, null), QUIET_9PM_7AM, late))
        .isEqualTo(ist(2026, 11, 28, 7, 0));
    Instant early = ist(2026, 11, 28, 5, 15);
    assertThat(BroadcastService.whenToSend(broadcast(false, null), QUIET_9PM_7AM, early))
        .isEqualTo(ist(2026, 11, 28, 7, 0));
    Instant day = ist(2026, 11, 27, 11, 0);
    assertThat(BroadcastService.whenToSend(broadcast(false, null), QUIET_9PM_7AM, day))
        .isEqualTo(day);
  }

  @Test
  void emergenciesIgnoreQuietHoursAndSchedulesAreKept() {
    Instant late = ist(2026, 11, 27, 23, 0);
    assertThat(BroadcastService.whenToSend(broadcast(true, null), QUIET_9PM_7AM, late))
        .isEqualTo(late);
    Instant later = ist(2026, 11, 29, 9, 30);
    assertThat(
            BroadcastService.whenToSend(
                broadcast(false, later), QUIET_9PM_7AM, ist(2026, 11, 27, 10, 0)))
        .isEqualTo(later);
  }

  @Test
  void messagesAlwaysCarryTheLink() {
    assertThat(
            BroadcastService.render(
                "Dear {{name}}, {{school}}: {{title}}. Read: {{link}}",
                "Priya",
                "GDS",
                "Holiday",
                "https://x/c/abc"))
        .isEqualTo("Dear Priya, GDS: Holiday. Read: https://x/c/abc");
    assertThat(
            BroadcastService.render("New notice: {{title}}", "P", "S", "Fees", "https://x/c/abc"))
        .isEqualTo("New notice: Fees https://x/c/abc");
  }

  @Test
  void languageVariantFallsBackToEnglish() {
    CommsService.Template en =
        new CommsService.Template(
            UUID.randomUUID(), "n", "en", "UTILITY", null, "en", "APPROVED", null);
    CommsService.Template hi =
        new CommsService.Template(
            UUID.randomUUID(), "n", "hi", "UTILITY", null, "hi", "APPROVED", null);
    assertThat(CommsService.pick(List.of(en, hi), "hi")).isSameAs(hi);
    assertThat(CommsService.pick(List.of(en, hi), "te")).isSameAs(en);
    assertThat(CommsService.pick(List.of(hi), "te")).isSameAs(hi);
    assertThat(CommsService.pick(List.of(), "en")).isNull();
  }

  @Test
  void csvTagsAndConsentValues() {
    assertThat(CommsService.split("\"Rao, Lakshmi\",+91 90000 1,\"say \"\"hi\"\"\""))
        .containsExactly("Rao, Lakshmi", "+91 90000 1", "say \"hi\"");
    assertThat(CommsService.cleanTags(List.of(" Class 3A ", "Class 3A", "", "Bus-4")))
        .containsExactly("Class 3A", "Bus-4");
    assertThatThrownBy(() -> CommsService.cleanTags(List.of("<script>")))
        .isInstanceOf(ApiException.class);
    assertThat(CommsService.yes("Yes")).isTrue();
    assertThat(CommsService.yes("no")).isFalse();
    assertThat(CommsService.yes("maybe")).isNull();
  }

  @Test
  void addressesAreMaskedInReports() {
    assertThat(BroadcastService.mask("+91 98480 22338")).isEqualTo("******2338");
    assertThat(BroadcastService.mask("priya@example.test")).isEqualTo("p***@example.test");
  }
}
