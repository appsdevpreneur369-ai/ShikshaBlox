package com.shikshablox.platform.identity;

import com.shikshablox.platform.shared.web.ApiException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** The authenticated principal of the current API request. */
public record CurrentUser(UUID userId, UUID organizationId, boolean mfa, Set<Role> roles) {

  public static Optional<CurrentUser> get() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof CurrentUser cu) {
      return Optional.of(cu);
    }
    return Optional.empty();
  }

  public static CurrentUser require() {
    return get()
        .orElseThrow(() ->
            new ApiException(
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "unauthenticated",
                "Please sign in."));
  }

  /** The active school; requests that need one fail cleanly when it is missing. */
  public UUID requireOrganization() {
    if (organizationId == null) {
      throw ApiException.badRequest("no_school_selected", "Choose a school first.");
    }
    return organizationId;
  }
}
