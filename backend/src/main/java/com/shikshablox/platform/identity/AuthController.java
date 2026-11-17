package com.shikshablox.platform.identity;

import com.shikshablox.platform.identity.AuthService.AuthResult;
import com.shikshablox.platform.identity.AuthService.ClientInfo;
import com.shikshablox.platform.identity.TokenService.IssuedTokens;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication API, called by the admin app's server (backend-for-frontend). Tokens are returned
 * in the body; the admin server stores them in httpOnly cookies.
 */
@RestController
@RequestMapping("/api/auth/v1")
@Tag(name = "Auth")
class AuthController {

  // ---------------------------------------------------------------------- request/response DTOs

  record PasswordLoginRequest(
      @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 200) String password) {}

  record OtpRequest(@NotBlank @Email @Size(max = 254) String email) {}

  record OtpVerifyRequest(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Pattern(regexp = "\\d{6}") String code) {}

  record MfaTokenRequest(@NotBlank @Size(max = 2000) String mfaToken) {}

  record MfaCodeRequest(
      @NotBlank @Size(max = 2000) String mfaToken,
      @NotBlank @Pattern(regexp = "\\d{6}") String code) {}

  record CodeRequest(@NotBlank @Pattern(regexp = "\\d{6}") String code) {}

  record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {}

  record SwitchOrganizationRequest(
      @NotNull UUID organizationId, @NotBlank @Size(max = 200) String refreshToken) {}

  record TokenPair(
      String accessToken,
      Instant accessTokenExpiresAt,
      String refreshToken,
      Instant refreshTokenExpiresAt) {
    static TokenPair of(IssuedTokens t) {
      return new TokenPair(
          t.accessToken(), t.accessTokenExpiresAt(), t.refreshToken(), t.refreshTokenExpiresAt());
    }
  }

  /** {@code status} tells the client what to do next; exactly one of tokens/mfaToken is set. */
  record LoginResponse(String status, TokenPair tokens, String mfaToken) {
    static LoginResponse of(AuthResult r) {
      return new LoginResponse(
          r.status().name(), r.tokens() == null ? null : TokenPair.of(r.tokens()), r.mfaToken());
    }
  }

  record MfaSetupResponse(String secret, String otpauthUri) {}

  record Membership(UUID organizationId, String slug, String name, List<String> roles) {}

  record MeResponse(
      UUID id,
      String email,
      String displayName,
      boolean mfaEnabled,
      boolean platformAdmin,
      Membership activeOrganization,
      List<String> roles,
      List<String> permissions,
      List<Membership> memberships) {}

  // ---------------------------------------------------------------------- endpoints

  private final AuthService auth;
  private final UserRepository users;
  private final OrganizationService organizations;

  AuthController(AuthService auth, UserRepository users, OrganizationService organizations) {
    this.auth = auth;
    this.users = users;
    this.organizations = organizations;
  }

  @PostMapping("/login")
  @Operation(operationId = "login", summary = "Sign in with email and password")
  LoginResponse login(@Valid @RequestBody PasswordLoginRequest req, HttpServletRequest http) {
    return LoginResponse.of(auth.loginWithPassword(req.email(), req.password(), client(http)));
  }

  @PostMapping("/otp/request")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(operationId = "requestOtp", summary = "Email a one-time sign-in code")
  void requestOtp(@Valid @RequestBody OtpRequest req, HttpServletRequest http) {
    auth.requestOtp(req.email(), client(http));
  }

  @PostMapping("/otp/verify")
  @Operation(operationId = "verifyOtp", summary = "Sign in with an emailed code")
  LoginResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest req, HttpServletRequest http) {
    return LoginResponse.of(auth.verifyOtp(req.email(), req.code(), client(http)));
  }

  @PostMapping("/mfa/verify")
  @Operation(operationId = "verifyMfa", summary = "Complete sign-in with an authenticator code")
  LoginResponse verifyMfa(@Valid @RequestBody MfaCodeRequest req, HttpServletRequest http) {
    return LoginResponse.of(auth.verifyMfa(req.mfaToken(), req.code(), client(http)));
  }

  @PostMapping("/mfa/enroll/start")
  @Operation(
      operationId = "startMfaEnrollment",
      summary = "Start authenticator setup during sign-in")
  MfaSetupResponse startEnrollment(@Valid @RequestBody MfaTokenRequest req) {
    var s = auth.startEnrollment(req.mfaToken());
    return new MfaSetupResponse(s.secret(), s.otpauthUri());
  }

  @PostMapping("/mfa/enroll/confirm")
  @Operation(
      operationId = "confirmMfaEnrollment",
      summary = "Confirm authenticator setup and sign in")
  LoginResponse confirmEnrollment(@Valid @RequestBody MfaCodeRequest req, HttpServletRequest http) {
    return LoginResponse.of(auth.confirmEnrollment(req.mfaToken(), req.code(), client(http)));
  }

  @PostMapping("/refresh")
  @Operation(
      operationId = "refresh",
      summary = "Rotate the refresh token and get a new access token")
  TokenPair refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest http) {
    return TokenPair.of(auth.refresh(req.refreshToken(), client(http)));
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "logout", summary = "End the session (revokes the refresh token family)")
  void logout(@Valid @RequestBody RefreshRequest req, HttpServletRequest http) {
    auth.logout(req.refreshToken(), client(http));
  }

  @GetMapping("/me")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "me", summary = "The signed-in user, active school and permissions")
  MeResponse me() {
    CurrentUser cu = CurrentUser.require();
    var user = users.findById(cu.userId()).orElseThrow(AuthService::sessionExpired);
    var assignments = users.assignments(cu.userId());
    boolean platformAdmin = cu.roles().contains(Role.PLATFORM_SUPER_ADMIN);

    Map<UUID, Set<String>> rolesByOrg = new LinkedHashMap<>();
    assignments.stream()
        .filter(a -> a.organizationId() != null)
        .forEach(a ->
            rolesByOrg
                .computeIfAbsent(a.organizationId(), k -> new TreeSet<>())
                .add(a.role().name()));
    List<Membership> memberships =
        organizations.findByIds(rolesByOrg.keySet()).stream()
            .filter(Organization::active)
            .map(o ->
                new Membership(o.id(), o.slug(), o.name(), List.copyOf(rolesByOrg.get(o.id()))))
            .toList();

    Membership active = null;
    if (cu.organizationId() != null) {
      active =
          memberships.stream()
              .filter(m -> m.organizationId().equals(cu.organizationId()))
              .findFirst()
              .orElseGet(() ->
                  organizations
                      .findVisible(cu.organizationId())
                      .map(o -> new Membership(o.id(), o.slug(), o.name(), List.of()))
                      .orElse(null));
    }
    List<String> permissions =
        cu.roles().stream()
            .flatMap(r -> r.permissions().stream())
            .map(Permission::authority)
            .distinct()
            .sorted()
            .toList();
    return new MeResponse(
        user.id(),
        user.email(),
        user.displayName(),
        user.mfaEnabled(),
        platformAdmin,
        active,
        cu.roles().stream().map(Enum::name).sorted().toList(),
        permissions,
        memberships);
  }

  @PostMapping("/switch-organization")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "switchOrganization", summary = "Make another school the active one")
  TokenPair switchOrganization(
      @Valid @RequestBody SwitchOrganizationRequest req, HttpServletRequest http) {
    CurrentUser cu = CurrentUser.require();
    return TokenPair.of(
        auth.switchOrganization(
            cu.userId(), req.organizationId(), req.refreshToken(), client(http)));
  }

  @PostMapping("/me/mfa/setup")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "startMyMfaSetup", summary = "Start turning on two-step verification")
  MfaSetupResponse startMyMfa() {
    var s = auth.startSelfEnrollment(CurrentUser.require().userId());
    return new MfaSetupResponse(s.secret(), s.otpauthUri());
  }

  @PostMapping("/me/mfa/confirm")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "confirmMyMfaSetup", summary = "Confirm two-step verification")
  void confirmMyMfa(@Valid @RequestBody CodeRequest req, HttpServletRequest http) {
    auth.confirmSelfEnrollment(CurrentUser.require().userId(), req.code(), client(http));
  }

  /** Client IP as seen by the trusted proxy (Caddy / Cloud Run front end). */
  static ClientInfo client(HttpServletRequest http) {
    String ua = http.getHeader("User-Agent");
    return new ClientInfo(Objects.requireNonNullElse(http.getRemoteAddr(), "unknown"), ua);
  }
}
