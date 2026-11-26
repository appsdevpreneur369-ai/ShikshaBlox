package com.shikshablox.modules.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shikshablox.platform.shared.web.ApiException;
import org.junit.jupiter.api.Test;

class PortalLogicTest {

  @Test
  void pinsAreFourToSixDigitsAndNotTrivial() {
    assertThat(PortalService.pin("2468")).isEqualTo("2468");
    assertThat(PortalService.pin("905173")).isEqualTo("905173");
    for (String bad :
        new String[] {"123", "1234567", "12a4", "0000", "1111", "1234", "4321", "567890", null}) {
      assertThatThrownBy(() -> PortalService.pin(bad))
          .as(String.valueOf(bad))
          .isInstanceOf(ApiException.class);
    }
  }

  @Test
  void tokensAreRandomAndOnlyTheirHashIsStored() {
    String a = PortalService.token(32);
    String b = PortalService.token(32);
    assertThat(a).hasSize(43).isNotEqualTo(b).matches("^[A-Za-z0-9_-]+$");
    assertThat(PortalService.hash(a))
        .hasSize(64)
        .isEqualTo(PortalService.hash(a))
        .isNotEqualTo(PortalService.hash(b));
  }

  @Test
  void phonesMatchOnTheLastTenDigits() {
    assertThat(PortalService.key("+91 90000 50001")).isEqualTo("9000050001");
    assertThat(PortalService.key("09000050001")).isEqualTo("9000050001");
  }
}
