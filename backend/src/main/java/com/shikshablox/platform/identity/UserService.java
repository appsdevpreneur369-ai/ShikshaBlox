package com.shikshablox.platform.identity;

import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** User provisioning used by onboarding, platform administration and seeding. */
@Service
public class UserService {

  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final ApplicationEventPublisher events;

  UserService(UserRepository users, PasswordEncoder passwords, ApplicationEventPublisher events) {
    this.users = users;
    this.passwords = passwords;
    this.events = events;
  }

  /** Returns the id of the user with {@code email}, creating the account if needed. */
  public UUID ensureUser(String email, String displayName, String password) {
    String normalised = AuthService.normaliseEmail(email);
    if (!normalised.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
      throw ApiException.badRequest("invalid_email", "Enter a valid email address.");
    }
    if (password != null && password.length() < 10) {
      throw ApiException.badRequest("weak_password", "Passwords must be at least 10 characters.");
    }
    return TenantContext.callAsSystem(
        "provision user",
        () -> {
          Optional<UserRepository.AppUser> existing = users.findByEmail(normalised);
          return existing
              .map(UserRepository.AppUser::id)
              .orElseGet(() ->
                  users.insert(
                      normalised,
                      displayName,
                      password == null ? null : passwords.encode(password)));
        });
  }

  /** Grants a role. School roles need an organization; the platform role must not have one. */
  public void grantRole(UUID userId, UUID organizationId, Role role) {
    if (role.platform() != (organizationId == null)) {
      throw ApiException.badRequest("invalid_role_scope", "That role does not fit this scope.");
    }
    TenantContext.runAsSystem("grant role", () -> users.assign(userId, organizationId, role));
    events.publishEvent(
        new SecurityEvent(
            SecurityEvent.ROLE_GRANTED,
            userId,
            organizationId,
            null,
            true,
            java.time.Instant.now()));
  }
}
