package com.shikshablox.platform.identity;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security settings. Secrets come from the environment; the built-in values are for local
 * development only and are rejected outside the local profiles (see {@link SecretGuard}).
 */
@ConfigurationProperties(prefix = "shikshablox.security")
record IdentityProperties(
    String jwtSecret,
    String dataKey,
    String issuer,
    Duration accessTokenTtl,
    Duration refreshTokenTtl,
    Duration mfaChallengeTtl,
    Duration otpTtl,
    int maxFailedLogins,
    Duration lockoutDuration) {

  IdentityProperties {
    if (issuer == null || issuer.isBlank()) {
      issuer = "shikshablox";
    }
    accessTokenTtl = accessTokenTtl == null ? Duration.ofMinutes(15) : accessTokenTtl;
    refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(30) : refreshTokenTtl;
    mfaChallengeTtl = mfaChallengeTtl == null ? Duration.ofMinutes(5) : mfaChallengeTtl;
    otpTtl = otpTtl == null ? Duration.ofMinutes(10) : otpTtl;
    maxFailedLogins = maxFailedLogins <= 0 ? 5 : maxFailedLogins;
    lockoutDuration = lockoutDuration == null ? Duration.ofMinutes(15) : lockoutDuration;
    if (jwtSecret == null || jwtSecret.length() < 32) {
      throw new IllegalStateException("shikshablox.security.jwt-secret must be at least 32 chars");
    }
    if (dataKey == null || dataKey.length() < 16) {
      throw new IllegalStateException("shikshablox.security.data-key must be at least 16 chars");
    }
  }
}
