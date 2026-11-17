package com.shikshablox.platform.identity;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * Issues and validates tokens.
 *
 * <ul>
 *   <li>Access token: JWT, 15 minutes, {@code typ=access}, claims {@code sub} (user), {@code org}
 *       (active organization, optional), {@code mfa}, {@code amr}.
 *   <li>MFA challenge: JWT, 5 minutes, {@code typ=mfa_challenge}; never accepted by the API.
 *   <li>Refresh token: opaque random value, stored as SHA-256, one-time use, rotated on every
 *       refresh; presenting a used token revokes its whole family (theft detection).
 * </ul>
 */
@Component
class TokenService {

  static final String TYP_ACCESS = "access";
  static final String TYP_MFA = "mfa_challenge";

  record IssuedTokens(
      String accessToken,
      Instant accessTokenExpiresAt,
      String refreshToken,
      Instant refreshTokenExpiresAt) {}

  record MfaChallenge(UUID userId, String purpose, List<String> amr) {}

  record RefreshRecord(
      UUID id,
      UUID userId,
      UUID familyId,
      UUID organizationId,
      boolean mfa,
      Instant expiresAt,
      Instant usedAt,
      Instant revokedAt) {}

  private static final SecureRandom RANDOM = new SecureRandom();

  private final IdentityProperties props;
  private final NimbusJwtEncoder encoder;
  private final NimbusJwtDecoder accessDecoder;
  private final NimbusJwtDecoder mfaDecoder;
  private final JdbcClient jdbc;
  private final Clock clock;

  TokenService(IdentityProperties props, JdbcClient jdbc) {
    this.props = props;
    this.jdbc = jdbc;
    this.clock = Clock.systemUTC();
    SecretKey key =
        new SecretKeySpec(props.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    this.accessDecoder = decoder(key, props.issuer(), TYP_ACCESS);
    this.mfaDecoder = decoder(key, props.issuer(), TYP_MFA);
  }

  private static NimbusJwtDecoder decoder(SecretKey key, String issuer, String typ) {
    NimbusJwtDecoder d =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    OAuth2TokenValidator<Jwt> typeValidator =
        jwt ->
            typ.equals(jwt.getClaimAsString("typ"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Wrong token type", null));
    d.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(), new JwtIssuerValidator(issuer), typeValidator));
    return d;
  }

  JwtDecoder accessTokenDecoder() {
    return accessDecoder;
  }

  // ------------------------------------------------------------------------------- access / MFA

  String encodeAccess(
      UUID userId, UUID organizationId, boolean mfa, List<String> amr, Instant exp) {
    Instant now = clock.instant();
    JwtClaimsSet.Builder claims =
        JwtClaimsSet.builder()
            .issuer(props.issuer())
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(exp)
            .id(UUID.randomUUID().toString())
            .claim("typ", TYP_ACCESS)
            .claim("mfa", mfa)
            .claim("amr", amr);
    if (organizationId != null) {
      claims.claim("org", organizationId.toString());
    }
    return encode(claims.build());
  }

  String issueMfaChallenge(UUID userId, String purpose, List<String> amr) {
    Instant now = clock.instant();
    return encode(
        JwtClaimsSet.builder()
            .issuer(props.issuer())
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(now.plus(props.mfaChallengeTtl()))
            .id(UUID.randomUUID().toString())
            .claim("typ", TYP_MFA)
            .claim("purpose", purpose)
            .claim("amr", amr)
            .build());
  }

  Optional<MfaChallenge> decodeMfaChallenge(String token) {
    try {
      Jwt jwt = mfaDecoder.decode(token);
      List<String> amr = jwt.getClaimAsStringList("amr");
      return Optional.of(
          new MfaChallenge(
              UUID.fromString(jwt.getSubject()),
              jwt.getClaimAsString("purpose"),
              amr == null ? List.of() : amr));
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private String encode(JwtClaimsSet claims) {
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  // ------------------------------------------------------------------------------ token pairs

  /** Issues an access token and a refresh token in {@code familyId} (new family when null). */
  IssuedTokens issue(
      UUID userId,
      UUID organizationId,
      boolean mfa,
      List<String> amr,
      UUID familyId,
      String ip,
      String userAgent) {
    Instant now = clock.instant();
    Instant accessExp = now.plus(props.accessTokenTtl());
    Instant refreshExp = now.plus(props.refreshTokenTtl());
    String raw = randomToken();
    jdbc.sql(
            "insert into refresh_token (user_id, family_id, token_hash, organization_id, mfa,"
                + " expires_at, created_ip, user_agent) values (?, ?, ?, ?, ?, ?, ?, ?)")
        .params(
            userId,
            familyId == null ? UUID.randomUUID() : familyId,
            sha256(raw),
            organizationId,
            mfa,
            Timestamp.from(refreshExp),
            truncate(ip, 64),
            truncate(userAgent, 300))
        .update();
    return new IssuedTokens(
        encodeAccess(userId, organizationId, mfa, amr, accessExp), accessExp, raw, refreshExp);
  }

  Optional<RefreshRecord> findRefresh(String raw) {
    if (raw == null || raw.isBlank() || raw.length() > 200) {
      return Optional.empty();
    }
    return jdbc.sql(
            "select id, user_id, family_id, organization_id, mfa, expires_at, used_at, revoked_at"
                + " from refresh_token where token_hash = ?")
        .param(sha256(raw))
        .query((rs, i) ->
            new RefreshRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("family_id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getBoolean("mfa"),
                rs.getTimestamp("expires_at").toInstant(),
                toInstant(rs.getTimestamp("used_at")),
                toInstant(rs.getTimestamp("revoked_at"))))
        .optional();
  }

  /** Marks a refresh token used; false if it was already used concurrently. */
  boolean markUsed(UUID id) {
    return jdbc.sql("update refresh_token set used_at = now() where id = ? and used_at is null")
            .param(id)
            .update()
        == 1;
  }

  void revokeFamily(UUID familyId) {
    jdbc.sql(
            "update refresh_token set revoked_at = now() where family_id = ? and revoked_at is null")
        .param(familyId)
        .update();
  }

  void revokeAllForUser(UUID userId) {
    jdbc.sql("update refresh_token set revoked_at = now() where user_id = ? and revoked_at is null")
        .param(userId)
        .update();
  }

  Instant now() {
    return clock.instant();
  }

  static String randomToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Instant toInstant(Timestamp ts) {
    return ts == null ? null : ts.toInstant();
  }

  private static String truncate(String s, int max) {
    return s == null ? null : s.length() <= max ? s : s.substring(0, max);
  }
}
