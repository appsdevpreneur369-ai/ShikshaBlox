package com.shikshablox.modules.frontoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shikshablox.platform.shared.web.ApiException;
import org.junit.jupiter.api.Test;

class FrontOfficeServiceTest {

  @Test
  void phoneIsOptionalButValidatedWhenGiven() {
    assertThat(FrontOfficeService.phone(null, false)).isNull();
    assertThat(FrontOfficeService.phone("  ", false)).isNull();
    assertThat(FrontOfficeService.phone(" +91 98480 22338 ", false)).isEqualTo("+91 98480 22338");
    assertThatThrownBy(() -> FrontOfficeService.phone("12345", false))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> FrontOfficeService.phone(null, true)).isInstanceOf(ApiException.class);
  }
}
