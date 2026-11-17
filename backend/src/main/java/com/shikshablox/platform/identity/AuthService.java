package com.shikshablox.platform.identity;

import com.shikshablox.platform.identity.TokenService.IssuedTokens;
import com.shikshablox.platform.identity.UserRepository.AppUser;
import com.shikshablox.platform.identity.UserRepository.Assignment;
import com.shikshablox.platform.notification.EmailSender;
import com.shikshablox.platform.notification.EmailSender.EmailMessage;
import com.shikshablox.platform.shared.RateLimiter;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.OrganizationService;
import com.shikshablox.platform.tenancy.TenantContext;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Authentication flows: password, email OTP, TOTP MFA, refresh rotation, logout, switch school. */
@Service
class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);
  private static final SecureRandom RANDOM = new SecureRandom();
  static final String PURPOSE_VERIFY = "verify";
  static final String PURPOSE_ENROLL = "enroll";

  enum Status {
    AUTHENTICATED,
    MFA_REQUIRED,
    MFA_ENROLLMENT_REQUIRED
  }

  record AuthResult(Status status, IssuedTokens tokens, String mfaToken) {}

  record ClientInfo(String ip, String userAgent) {}

  record MfaSetup(String secret, String otpauthUri) {}

  private final UserRepository users;
  private final TokenService tokens;
  private final PasswordEncoder passwords;
  private final SecretCipher cipher;
  private final RateLimiter rateLimiter;
  private final EmailSender email;
  private final OrganizationService organizations;
  private final IdentityProperties props;
  private final ApplicationEventPublisher events;
  private final TransactionTemplate tx;
  private final JdbcClient jdbc;
  private final String dummyHash;

  AuthService(
      UserRepository users,
      TokenService tokens,
      PasswordEncoder passwords,
      SecretCipher cipher,
      RateLimiter rateLimiter,
      EmailSender email,
      OrganizationService organizations,
      IdentityProperties props,
      ApplicationEventPublisher events,
      TransactionTemplate tx,
      JdbcClient jdbc) {
    this.users = users;
    this.tokens = tokens;
    this.passwords = passwords;
    this.cipher = cipher;
    this.rateLimiter = rateLimiter;
    this.email = email;
    this.organizations = organizations;
    this.props = props;
    this.events = events;
    this.tx = tx;
    this.jdbc = jdbc;
    this.dummyHash = passwords.encode("timing-equaliser-" + UUID.randomUUID());
  }

  static ApiException invalidCredentials() {
    return new ApiException(
        HttpStatus.UNAUTHORIZED, "invalid_credentials", "The email or password is incorrect.");
  }

  static ApiException invalidCode() {
    return new ApiException(
        HttpStatus.UNAUTHORIZED, "invalid_code", "That code is incorrect or has expired.");
  }

  static ApiException sessionExpired() {
    return new ApiException(
        HttpStatus.UNAUTHORIZED, "session_expired", "Your session has expired. Please sign in.");
  }

  static String normaliseEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }

  // ------------------------------------------------------------------------------ password

  AuthResult loginWithPassword(String rawEmail, String password, ClientInfo client) {
    String emailAddr = normaliseEmail(rawEmail);
    limit("login-ip:" + client.ip(), 30, Duration.ofMinutes(5));
    limit("login-email:" + emailAddr, 10, Duration.ofMinutes(15));

    return TenantContext.callAsSystem(
        "password login",
        () -> {
          Optional<AppUser> found = users.findByEmail(emailAddr);
          if (found.isEmpty() || found.get().passwordHash() == null) {
            passwords.matches(password, dummyHash); // equalise timing
            publish(SecurityEvent.LOGIN_FAILED, null, null, client, false);
            throw invalidCredentials();
          }
          AppUser user = found.get();
          Instant now = tokens.now();
          if (!user.active() || user.locked(now)) {
            passwords.matches(password, dummyHash);
            publish(SecurityEvent.LOGIN_FAILED, user.id(), null, client, false);
            throw invalidCredentials();
          }
          if (!passwords.matches(password, user.passwordHash())) {
            users.recordFailedLogin(
                user.id(), props.maxFailedLogins(), now.plus(props.lockoutDuration()));
            boolean nowLocked = user.failedLoginCount() + 1 >= props.maxFailedLogins();
            publish(
                nowLocked ? SecurityEvent.ACCOUNT_LOCKED : SecurityEvent.LOGIN_FAILED,
                user.id(),
                null,
                client,
                false);
            throw invalidCredentials();
          }
          return completeFirstFactor(user, "pwd", client);
        });
  }

  // ------------------------------------------------------------------------------ email OTP

  /** Always succeeds from the caller's view, so it cannot be used to discover accounts. */
  void requestOtp(String rawEmail, ClientInfo client) {
    String emailAddr = normaliseEmail(rawEmail);
    limit("otp-ip:" + client.ip(), 10, Duration.ofMinutes(10));
    if (!rateLimiter.tryAcquire("otp-email:" + emailAddr, 3, Duration.ofMinutes(10))) {
      return; // silently drop: same response as success
    }
    TenantContext.runAsSystem(
        "email otp request",
        () -> {
          Optional<AppUser> found = users.findByEmail(emailAddr);
          if (found.isEmpty()
              || !found.get().active()
              || users.assignments(found.get().id()).isEmpty()) {
            return;
          }
          AppUser user = found.get();
          String code = String.format("%06d", RANDOM.nextInt(1_000_000));
          tx.executeWithoutResult(s -> {
            jdbc.sql(
                    "update login_otp set consumed_at = now()"
                        + " where user_id = ? and consumed_at is null")
                .param(user.id())
                .update();
            jdbc.sql("insert into login_otp (user_id, code_hash, expires_at) values (?, ?, ?)")
                .params(
                    user.id(),
                    otpHash(user.id(), code),
                    Timestamp.from(tokens.now().plus(props.otpTtl())))
                .update();
          });
          email.send(
              new EmailMessage(
                  user.email(),
                  "Your ShikshaBlox sign-in code: " + code,
                  "Hello "
                      + user.displayName()
                      + ",\n\nYour sign-in code is "
                      + code
                      + ". It expires in "
                      + props.otpTtl().toMinutes()
                      + " minutes.\n\nIf you did not ask for this code, you can ignore this email."));
          publish(SecurityEvent.OTP_SENT, user.id(), null, client, true);
        });
  }

  AuthResult verifyOtp(String rawEmail, String code, ClientInfo client) {
    String emailAddr = normaliseEmail(rawEmail);
    limit("otp-verify-ip:" + client.ip(), 30, Duration.ofMinutes(10));
    return TenantContext.callAsSystem(
        "email otp verify",
        () -> {
          AppUser user =
              users
                  .findByEmail(emailAddr)
                  .filter(AppUser::active)
                  .orElseThrow(AuthService::invalidCode);
          record Otp(UUID id, byte[] hash, int attempts) {}
          Optional<Otp> otp =
              jdbc.sql(
                      "select id, code_hash, attempts from login_otp where user_id = ?"
                          + " and consumed_at is null and expires_at > now()"
                          + " order by created_at desc limit 1")
                  .param(user.id())
                  .query((rs, i) ->
                      new Otp(
                          rs.getObject("id", UUID.class),
                          rs.getBytes("code_hash"),
                          rs.getInt("attempts")))
                  .optional();
          if (otp.isEmpty() || code == null || !code.matches("\\d{6}")) {
            throw invalidCode();
          }
          Otp o = otp.get();
          boolean match = MessageDigest.isEqual(o.hash(), otpHash(user.id(), code));
          if (!match) {
            jdbc.sql(
                    "update login_otp set attempts = attempts + 1,"
                        + " consumed_at = case when attempts + 1 >= 5 then now() else null end"
                        + " where id = ?")
                .param(o.id())
                .update();
            publish(SecurityEvent.LOGIN_FAILED, user.id(), null, client, false);
            throw invalidCode();
          }
          int consumed =
              jdbc.sql(
                      "update login_otp set consumed_at = now() where id = ? and consumed_at is null")
                  .param(o.id())
                  .update();
          if (consumed != 1) {
            throw invalidCode();
          }
          return completeFirstFactor(user, "otp", client);
        });
  }

  private static byte[] otpHash(UUID userId, String code) {
    return TokenService.sha256(userId + ":" + code);
  }

  // ------------------------------------------------------------------------------ MFA

  private AuthResult completeFirstFactor(AppUser user, String method, ClientInfo client) {
    List<Assignment> assignments = users.assignments(user.id());
    if (assignments.isEmpty()) {
      publish(SecurityEvent.LOGIN_FAILED, user.id(), null, client, false);
      throw invalidCredentials();
    }
    boolean superAdmin = assignments.stream().anyMatch(a -> a.role() == Role.PLATFORM_SUPER_ADMIN);
    if (user.mfaEnabled()) {
      return new AuthResult(
          Status.MFA_REQUIRED,
          null,
          tokens.issueMfaChallenge(user.id(), PURPOSE_VERIFY, List.of(method)));
    }
    if (superAdmin) {
      return new AuthResult(
          Status.MFA_ENROLLMENT_REQUIRED,
          null,
          tokens.issueMfaChallenge(user.id(), PURPOSE_ENROLL, List.of(method)));
    }
    users.recordSuccessfulLogin(user.id());
    publish(SecurityEvent.LOGIN, user.id(), null, client, true);
    return new AuthResult(
        Status.AUTHENTICATED,
        tokens.issue(
            user.id(),
            defaultOrganization(assignments),
            false,
            List.of(method),
            null,
            client.ip(),
            client.userAgent()),
        null);
  }

  /** Second factor with an enrolled authenticator app. */
  AuthResult verifyMfa(String mfaToken, String code, ClientInfo client) {
    var challenge =
        tokens
            .decodeMfaChallenge(mfaToken)
            .filter(c -> PURPOSE_VERIFY.equals(c.purpose()))
            .orElseThrow(AuthService::sessionExpired);
    limit("mfa:" + challenge.userId(), 5, Duration.ofMinutes(5));
    return TenantContext.callAsSystem(
        "mfa verify",
        () -> {
          AppUser user =
              users
                  .findById(challenge.userId())
                  .filter(AppUser::active)
                  .orElseThrow(AuthService::sessionExpired);
          if (!user.mfaEnabled() || user.mfaSecretEnc() == null) {
            throw sessionExpired();
          }
          var step =
              Totp.verify(cipher.decrypt(user.mfaSecretEnc()), code, tokens.now().getEpochSecond());
          if (step.isEmpty() || !users.advanceMfaStep(user.id(), step.getAsLong())) {
            publish(SecurityEvent.MFA_FAILED, user.id(), null, client, false);
            throw invalidCode();
          }
          return authenticated(user, withMfa(challenge.amr()), client);
        });
  }

  /** Starts authenticator enrolment during sign-in (mandatory for platform administrators). */
  MfaSetup startEnrollment(String mfaToken) {
    var challenge =
        tokens
            .decodeMfaChallenge(mfaToken)
            .filter(c -> PURPOSE_ENROLL.equals(c.purpose()))
            .orElseThrow(AuthService::sessionExpired);
    return TenantContext.callAsSystem("mfa enrolment start", () -> beginSetup(challenge.userId()));
  }

  AuthResult confirmEnrollment(String mfaToken, String code, ClientInfo client) {
    var challenge =
        tokens
            .decodeMfaChallenge(mfaToken)
            .filter(c -> PURPOSE_ENROLL.equals(c.purpose()))
            .orElseThrow(AuthService::sessionExpired);
    limit("mfa:" + challenge.userId(), 5, Duration.ofMinutes(5));
    return TenantContext.callAsSystem(
        "mfa enrolment confirm",
        () -> {
          AppUser user = confirmSetup(challenge.userId(), code, client);
          return authenticated(user, withMfa(challenge.amr()), client);
        });
  }

  /** Optional enrolment for a signed-in user (from their profile). */
  MfaSetup startSelfEnrollment(UUID userId) {
    try (var ignored = TenantContext.forUser(userId)) {
      return beginSetup(userId);
    }
  }

  void confirmSelfEnrollment(UUID userId, String code, ClientInfo client) {
    limit("mfa:" + userId, 5, Duration.ofMinutes(5));
    try (var ignored = TenantContext.forUser(userId)) {
      confirmSetup(userId, code, client);
    }
  }

  private MfaSetup beginSetup(UUID userId) {
    AppUser user =
        users.findById(userId).filter(AppUser::active).orElseThrow(AuthService::sessionExpired);
    if (user.mfaEnabled()) {
      throw ApiException.conflict("mfa_already_enabled", "Two-step verification is already on.");
    }
    String secret = Totp.newSecret();
    users.setPendingMfaSecret(user.id(), cipher.encrypt(secret));
    return new MfaSetup(secret, Totp.otpauthUri("ShikshaBlox", user.email(), secret));
  }

  private AppUser confirmSetup(UUID userId, String code, ClientInfo client) {
    AppUser user =
        users.findById(userId).filter(AppUser::active).orElseThrow(AuthService::sessionExpired);
    if (user.mfaEnabled() || user.mfaSecretEnc() == null) {
      throw sessionExpired();
    }
    var step =
        Totp.verify(cipher.decrypt(user.mfaSecretEnc()), code, tokens.now().getEpochSecond());
    if (step.isEmpty()) {
      publish(SecurityEvent.MFA_FAILED, user.id(), null, client, false);
      throw invalidCode();
    }
    users.enableMfa(user.id(), step.getAsLong());
    publish(SecurityEvent.MFA_ENROLLED, user.id(), null, client, true);
    return user;
  }

  private AuthResult authenticated(AppUser user, List<String> amr, ClientInfo client) {
    users.recordSuccessfulLogin(user.id());
    publish(SecurityEvent.LOGIN, user.id(), null, client, true);
    UUID org = defaultOrganization(users.assignments(user.id()));
    return new AuthResult(
        Status.AUTHENTICATED,
        tokens.issue(user.id(), org, true, amr, null, client.ip(), client.userAgent()),
        null);
  }

  private static List<String> withMfa(List<String> amr) {
    List<String> out = new ArrayList<>(amr);
    out.add("otp_totp");
    return out;
  }

  // ------------------------------------------------------------------------------ sessions

  IssuedTokens refresh(String refreshToken, ClientInfo client) {
    limit("refresh-ip:" + client.ip(), 60, Duration.ofMinutes(1));
    return TenantContext.callAsSystem(
        "refresh token rotation",
        () -> {
          var record = tokens.findRefresh(refreshToken).orElseThrow(AuthService::sessionExpired);
          if (record.revokedAt() != null) {
            throw sessionExpired();
          }
          if (record.usedAt() != null || !tokens.markUsed(record.id())) {
            // A used token presented again means it was copied: end every session in the family.
            tokens.revokeFamily(record.familyId());
            log.warn("Refresh token reuse detected; family {} revoked", record.familyId());
            publish(
                SecurityEvent.REFRESH_REUSE,
                record.userId(),
                record.organizationId(),
                client,
                false);
            throw sessionExpired();
          }
          if (record.expiresAt().isBefore(tokens.now())) {
            throw sessionExpired();
          }
          AppUser user =
              users
                  .findById(record.userId())
                  .filter(AppUser::active)
                  .orElseThrow(AuthService::sessionExpired);
          List<Assignment> assignments = users.assignments(user.id());
          UUID org = record.organizationId();
          if (org != null && !canEnter(assignments, org)) {
            org = defaultOrganization(assignments);
          }
          if (assignments.isEmpty()) {
            throw sessionExpired();
          }
          return tokens.issue(
              user.id(),
              org,
              record.mfa(),
              record.mfa() ? List.of("refresh", "mfa") : List.of("refresh"),
              record.familyId(),
              client.ip(),
              client.userAgent());
        });
  }

  void logout(String refreshToken, ClientInfo client) {
    TenantContext.runAsSystem(
        "logout",
        () ->
            tokens
                .findRefresh(refreshToken)
                .ifPresent(r -> {
                  tokens.revokeFamily(r.familyId());
                  publish(SecurityEvent.LOGOUT, r.userId(), r.organizationId(), client, true);
                }));
  }

  IssuedTokens switchOrganization(
      UUID userId, UUID organizationId, String refreshToken, ClientInfo client) {
    return TenantContext.callAsSystem(
        "switch organization",
        () -> {
          var record =
              tokens
                  .findRefresh(refreshToken)
                  .filter(
                      r -> r.userId().equals(userId) && r.revokedAt() == null && r.usedAt() == null)
                  .orElseThrow(AuthService::sessionExpired);
          List<Assignment> assignments = users.assignments(userId);
          if (!canEnter(assignments, organizationId)
              || organizations.findVisible(organizationId).filter(Organization::active).isEmpty()) {
            throw ApiException.forbidden();
          }
          if (!tokens.markUsed(record.id())) {
            throw sessionExpired();
          }
          publish(SecurityEvent.SWITCH_ORG, userId, organizationId, client, true);
          return tokens.issue(
              userId,
              organizationId,
              record.mfa(),
              List.of("switch"),
              record.familyId(),
              client.ip(),
              client.userAgent());
        });
  }

  static boolean canEnter(List<Assignment> assignments, UUID organizationId) {
    return assignments.stream()
        .anyMatch(a ->
            a.role() == Role.PLATFORM_SUPER_ADMIN || organizationId.equals(a.organizationId()));
  }

  private UUID defaultOrganization(List<Assignment> assignments) {
    List<UUID> orgIds =
        assignments.stream()
            .map(Assignment::organizationId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    return organizations.findByIds(orgIds).stream()
        .filter(Organization::active)
        .min(Comparator.comparing(Organization::name))
        .map(Organization::id)
        .orElse(null);
  }

  // ------------------------------------------------------------------------------ helpers

  private void limit(String key, int max, Duration window) {
    if (!rateLimiter.tryAcquire(key, max, window)) {
      throw ApiException.tooManyRequests();
    }
  }

  private void publish(String type, UUID userId, UUID orgId, ClientInfo client, boolean success) {
    events.publishEvent(
        new SecurityEvent(type, userId, orgId, client.ip(), success, Instant.now()));
  }
}
