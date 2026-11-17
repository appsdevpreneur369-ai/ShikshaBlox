package com.shikshablox.platform.identity;

import com.shikshablox.platform.identity.UserRepository.AppUser;
import com.shikshablox.platform.identity.UserRepository.Assignment;
import com.shikshablox.platform.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stateless JWT security, deny by default. Permissions are re-read from the database on every
 * request, so revoking a role or disabling a user takes effect immediately rather than when the
 * access token expires.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(IdentityProperties.class)
class SecurityConfig {

  @Bean
  PasswordEncoder passwordEncoder() {
    return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, TokenService tokens, UserRepository users) throws Exception {
    http.csrf(csrf -> csrf.disable()) // bearer tokens only; no cookie authentication on the API
        .cors(cors -> cors.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(b -> b.disable())
        .formLogin(f -> f.disable())
        .logout(l -> l.disable())
        .headers(h ->
            h.contentSecurityPolicy(
                    csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER)))
        .oauth2ResourceServer(o ->
            o.jwt(j ->
                j.decoder(tokens.accessTokenDecoder())
                    .jwtAuthenticationConverter(new PermissionConverter(users))))
        .addFilterAfter(new TenantContextFilter(), BearerTokenAuthenticationFilter.class)
        .authorizeHttpRequests(auth ->
            auth.requestMatchers(EndpointRequest.to(HealthEndpoint.class, InfoEndpoint.class))
                .permitAll()
                .requestMatchers(EndpointRequest.toAnyEndpoint())
                .denyAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                .permitAll()
                .requestMatchers("/api/public/**")
                .permitAll()
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/auth/v1/login",
                    "/api/auth/v1/otp/request",
                    "/api/auth/v1/otp/verify",
                    "/api/auth/v1/mfa/verify",
                    "/api/auth/v1/mfa/enroll/start",
                    "/api/auth/v1/mfa/enroll/confirm",
                    "/api/auth/v1/refresh",
                    "/api/auth/v1/logout")
                .permitAll()
                .requestMatchers("/api/auth/v1/**", "/api/admin/**", "/api/platform/**")
                .authenticated()
                .anyRequest()
                .denyAll());
    return http.build();
  }

  /** Turns a verified access token into authorities from the user's current role assignments. */
  static final class PermissionConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;

    PermissionConverter(UserRepository users) {
      this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
      UUID userId;
      UUID orgId;
      try {
        userId = UUID.fromString(jwt.getSubject());
        String org = jwt.getClaimAsString("org");
        orgId = org == null ? null : UUID.fromString(org);
      } catch (IllegalArgumentException e) {
        throw new InvalidBearerTokenException("Malformed token");
      }
      boolean mfa = Boolean.TRUE.equals(jwt.getClaimAsBoolean("mfa"));

      AppUser user;
      List<Assignment> assignments;
      try (var ignored = TenantContext.forUser(userId)) {
        user = users.findById(userId).filter(AppUser::active).orElse(null);
        assignments = user == null ? List.of() : users.assignments(userId);
      }
      if (user == null) {
        throw new InvalidBearerTokenException("User is not active");
      }
      if (orgId != null && !AuthService.canEnter(assignments, orgId)) {
        throw new InvalidBearerTokenException("No longer a member of this school");
      }

      Set<Role> roles = EnumSet.noneOf(Role.class);
      for (Assignment a : assignments) {
        if (a.role() == Role.PLATFORM_SUPER_ADMIN) {
          if (mfa) { // platform powers only with a second factor
            roles.add(a.role());
          }
        } else if (orgId != null && orgId.equals(a.organizationId())) {
          roles.add(a.role());
        }
      }
      Set<GrantedAuthority> authorities = new HashSet<>();
      for (Role r : roles) {
        authorities.add(new SimpleGrantedAuthority("ROLE_" + r.name()));
        r.permissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p.authority())));
      }
      JwtAuthenticationToken token =
          new JwtAuthenticationToken(jwt, authorities, userId.toString());
      token.setDetails(new CurrentUser(userId, orgId, mfa, Set.copyOf(roles)));
      return token;
    }
  }

  /** Opens the tenant context (RLS binding) for the rest of an authenticated request. */
  static final class TenantContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
      var auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth != null && auth.getDetails() instanceof CurrentUser cu) {
        try (var ignored =
            cu.organizationId() == null
                ? TenantContext.forUser(cu.userId())
                : TenantContext.forTenant(cu.organizationId(), cu.userId())) {
          chain.doFilter(request, response);
        }
      } else {
        chain.doFilter(request, response);
      }
    }
  }
}
