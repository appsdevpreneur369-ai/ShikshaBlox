package com.shikshablox.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TotpTest {

  private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

  /** RFC 6238 appendix B (SHA-1), truncated to 6 digits. */
  @ParameterizedTest
  @CsvSource({"59, 287082", "1111111109, 081804", "1111111111, 050471", "1234567890, 005924"})
  void matchesRfcVectors(long time, String expected) {
    assertThat(Totp.generate(RFC_KEY, time / 30)).isEqualTo(expected);
  }

  @Test
  void base32RoundTripAndVerifyWithDrift() {
    String secret = Totp.base32Encode(RFC_KEY);
    assertThat(Totp.base32Decode(secret)).isEqualTo(RFC_KEY);
    assertThat(Totp.verify(secret, "287082", 59)).hasValue(1);
    assertThat(Totp.verify(secret, "287082", 59 + 30)).hasValue(1); // one step late still ok
    assertThat(Totp.verify(secret, "287082", 59 + 90)).isEmpty();
    assertThat(Totp.verify(secret, "abc", 59)).isEmpty();
  }

  @Test
  void newSecretsAreRandom() {
    assertThat(Totp.newSecret()).hasSize(32).isNotEqualTo(Totp.newSecret());
  }
}
