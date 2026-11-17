package com.shikshablox.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.shikshablox.platform.notification.EmailSender;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.OrganizationService;
import com.shikshablox.platform.tenancy.TenantContext;
import com.shikshablox.support.PostgresTestSupport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

/** End-to-end authentication and authorization through the HTTP API. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthFlowIT extends PostgresTestSupport {

  static final String PASSWORD = "Correct-Horse-42";

  @TestConfiguration
  static class Mail {
    static final List<EmailSender.EmailMessage> SENT = new CopyOnWriteArrayList<>();

    @Bean
    @Primary
    EmailSender recordingEmailSender() {
      return SENT::add;
    }
  }

  @Autowired TestRestTemplate http;
  @Autowired OrganizationService organizations;
  @Autowired UserService users;
  @Autowired SecretCipher cipher;
  @Autowired JdbcClient jdbc;

  String ip; // unique client per test so rate limits do not interfere
  String suffix;

  @BeforeEach
  void client() {
    ip = "10.0." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
    suffix = UUID.randomUUID().toString().substring(0, 8);
  }

  // ------------------------------------------------------------------------- helpers

  Organization school(String name) {
    return organizations.create(name + "-" + suffix, "School " + name + " " + suffix);
  }

  String user(String local, Organization org, Role role) {
    String email = local + "-" + suffix + "@example.test";
    UUID id = users.ensureUser(email, local, PASSWORD);
    users.grantRole(id, org == null ? null : org.id(), role);
    return email;
  }

  ResponseEntity<JsonNode> post(String path, Object body, String bearer) {
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    h.set("X-Forwarded-For", ip);
    if (bearer != null) {
      h.setBearerAuth(bearer);
    }
    return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), JsonNode.class);
  }

  ResponseEntity<JsonNode> get(String path, String bearer) {
    HttpHeaders h = new HttpHeaders();
    h.set("X-Forwarded-For", ip);
    if (bearer != null) {
      h.setBearerAuth(bearer);
    }
    return http.exchange(path, HttpMethod.GET, new HttpEntity<>(h), JsonNode.class);
  }

  ResponseEntity<JsonNode> login(String email, String password) {
    return post("/api/auth/v1/login", Map.of("email", email, "password", password), null);
  }

  String accessToken(String email) {
    var res = login(email, PASSWORD);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody().get("status").asText()).isEqualTo("AUTHENTICATED");
    return res.getBody().at("/tokens/accessToken").asText();
  }

  String currentCode(String email) {
    String enc =
        TenantContext.callAsSystem(
            "test",
            () ->
                jdbc.sql("select mfa_secret_enc from app_user where email = ?")
                    .param(email)
                    .query(String.class)
                    .single());
    return Totp.generate(
        Totp.base32Decode(cipher.decrypt(enc)), System.currentTimeMillis() / 1000 / 30);
  }

  String nextStepCode(String email) {
    String enc =
        TenantContext.callAsSystem(
            "test",
            () ->
                jdbc.sql("select mfa_secret_enc from app_user where email = ?")
                    .param(email)
                    .query(String.class)
                    .single());
    return Totp.generate(
        Totp.base32Decode(cipher.decrypt(enc)), System.currentTimeMillis() / 1000 / 30 + 1);
  }

  // ------------------------------------------------------------------------- tests

  @Test
  void schoolAdminSignsInAndSeesOwnSchool() {
    Organization org = school("alpha");
    String email = user("admin", org, Role.SCHOOL_ADMIN);

    var me = get("/api/auth/v1/me", accessToken(email));
    assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(me.getBody().at("/activeOrganization/slug").asText()).isEqualTo(org.slug());
    assertThat(me.getBody().get("roles").toString()).contains("SCHOOL_ADMIN");
    assertThat(me.getBody().get("permissions").toString())
        .contains("content:edit", "school:manage")
        .doesNotContain("platform:manage");
  }

  @Test
  void wrongPasswordAndUnknownEmailLookTheSame() {
    Organization org = school("beta");
    String email = user("editor", org, Role.CONTENT_EDITOR);

    var wrong = login(email, "not-the-password");
    var unknown = login("nobody-" + suffix + "@example.test", "whatever-password");
    assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(wrong.getBody().get("code")).isEqualTo(unknown.getBody().get("code"));
    assertThat(wrong.getBody().get("detail")).isEqualTo(unknown.getBody().get("detail"));
  }

  @Test
  void accountLocksAfterRepeatedFailures() {
    Organization org = school("gamma");
    String email = user("viewer", org, Role.VIEWER);
    for (int i = 0; i < 5; i++) {
      assertThat(login(email, "wrong-password-" + i).getStatusCode())
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    assertThat(login(email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void refreshTokensRotateAndReuseRevokesTheFamily() {
    Organization org = school("delta");
    String email = user("editor", org, Role.CONTENT_EDITOR);
    String first = login(email, PASSWORD).getBody().at("/tokens/refreshToken").asText();

    var rotated = post("/api/auth/v1/refresh", Map.of("refreshToken", first), null);
    assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
    String second = rotated.getBody().get("refreshToken").asText();
    assertThat(second).isNotEqualTo(first);
    assertThat(
            get("/api/auth/v1/me", rotated.getBody().get("accessToken").asText()).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    // Replaying the old token = theft signal: it fails and kills the newer token too.
    assertThat(post("/api/auth/v1/refresh", Map.of("refreshToken", first), null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(post("/api/auth/v1/refresh", Map.of("refreshToken", second), null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void logoutRevokesTheSession() {
    Organization org = school("epsilon");
    String email = user("editor", org, Role.CONTENT_EDITOR);
    String refresh = login(email, PASSWORD).getBody().at("/tokens/refreshToken").asText();
    assertThat(post("/api/auth/v1/logout", Map.of("refreshToken", refresh), null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(post("/api/auth/v1/refresh", Map.of("refreshToken", refresh), null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void emailOtpSignInIsSingleUse() {
    Organization org = school("zeta");
    String email = user("approver", org, Role.CONTENT_APPROVER);

    assertThat(post("/api/auth/v1/otp/request", Map.of("email", email), null).getStatusCode())
        .isEqualTo(HttpStatus.ACCEPTED);
    // Unknown addresses get the same answer (no account discovery).
    assertThat(
            post(
                    "/api/auth/v1/otp/request",
                    Map.of("email", "ghost-" + suffix + "@example.test"),
                    null)
                .getStatusCode())
        .isEqualTo(HttpStatus.ACCEPTED);

    var mail =
        Mail.SENT.stream().filter(m -> m.to().equals(email)).reduce((x, y) -> y).orElseThrow();
    Matcher m = java.util.regex.Pattern.compile("(\\d{6})").matcher(mail.subject());
    assertThat(m.find()).isTrue();
    String code = m.group(1);

    var ok = post("/api/auth/v1/otp/verify", Map.of("email", email, "code", code), null);
    assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(ok.getBody().get("status").asText()).isEqualTo("AUTHENTICATED");
    assertThat(
            post("/api/auth/v1/otp/verify", Map.of("email", email, "code", code), null)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void superAdminMustEnrolTotpAndThenUseIt() {
    String email = user("platform", null, Role.PLATFORM_SUPER_ADMIN);

    var first = login(email, PASSWORD);
    assertThat(first.getBody().get("status").asText()).isEqualTo("MFA_ENROLLMENT_REQUIRED");
    assertThat(first.getBody().get("tokens")).isNull();
    String mfaToken = first.getBody().get("mfaToken").asText();

    // The challenge token is not an access token.
    assertThat(get("/api/auth/v1/me", mfaToken).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    var setup = post("/api/auth/v1/mfa/enroll/start", Map.of("mfaToken", mfaToken), null);
    assertThat(setup.getBody().get("otpauthUri").asText()).startsWith("otpauth://totp/ShikshaBlox");
    assertThat(
            post(
                    "/api/auth/v1/mfa/enroll/confirm",
                    Map.of("mfaToken", mfaToken, "code", "000000"),
                    null)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    var enrolled =
        post(
            "/api/auth/v1/mfa/enroll/confirm",
            Map.of("mfaToken", mfaToken, "code", currentCode(email)),
            null);
    assertThat(enrolled.getBody().get("status").asText()).isEqualTo("AUTHENTICATED");
    String access = enrolled.getBody().at("/tokens/accessToken").asText();
    assertThat(get("/api/platform/v1/organizations", access).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    // Next sign-in asks for the code; a code from an already used time step is rejected.
    var second = login(email, PASSWORD);
    assertThat(second.getBody().get("status").asText()).isEqualTo("MFA_REQUIRED");
    String challenge = second.getBody().get("mfaToken").asText();
    assertThat(
            post(
                    "/api/auth/v1/mfa/verify",
                    Map.of("mfaToken", challenge, "code", currentCode(email)),
                    null)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    var verified =
        post(
            "/api/auth/v1/mfa/verify",
            Map.of("mfaToken", challenge, "code", nextStepCode(email)),
            null);
    assertThat(verified.getBody().get("status").asText()).isEqualTo("AUTHENTICATED");
  }

  @Test
  void schoolRolesCannotUsePlatformEndpoints() {
    Organization org = school("eta");
    String admin = user("admin", org, Role.SCHOOL_ADMIN);
    String viewer = user("viewer", org, Role.VIEWER);

    assertThat(get("/api/platform/v1/organizations", null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(get("/api/platform/v1/organizations", accessToken(viewer)).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            post(
                    "/api/platform/v1/organizations",
                    Map.of("slug", "x-" + suffix, "name", "Nope"),
                    accessToken(admin))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void switchingSchoolRequiresMembership() {
    Organization a = school("theta");
    Organization b = school("iota");
    Organization other = school("kappa");
    String email = user("multi", a, Role.CONTENT_EDITOR);
    users.grantRole(users.ensureUser(email, "multi", PASSWORD), b.id(), Role.SCHOOL_ADMIN);

    var tokens = login(email, PASSWORD).getBody().get("tokens");
    String access = tokens.get("accessToken").asText();
    String refresh = tokens.get("refreshToken").asText();

    var me = get("/api/auth/v1/me", access);
    assertThat(me.getBody().get("memberships")).hasSize(2);

    var denied =
        post(
            "/api/auth/v1/switch-organization",
            Map.of("organizationId", other.id().toString(), "refreshToken", refresh),
            access);
    assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    var switched =
        post(
            "/api/auth/v1/switch-organization",
            Map.of("organizationId", b.id().toString(), "refreshToken", refresh),
            access);
    assertThat(switched.getStatusCode()).isEqualTo(HttpStatus.OK);
    var meB = get("/api/auth/v1/me", switched.getBody().get("accessToken").asText());
    assertThat(meB.getBody().at("/activeOrganization/slug").asText()).isEqualTo(b.slug());
    assertThat(meB.getBody().get("roles").toString()).contains("SCHOOL_ADMIN");
  }

  @Test
  void removingARoleTakesEffectImmediately() {
    Organization org = school("lambda");
    String email = user("editor", org, Role.CONTENT_EDITOR);
    String access = accessToken(email);
    assertThat(get("/api/auth/v1/me", access).getStatusCode()).isEqualTo(HttpStatus.OK);

    TenantContext.runAsSystem(
        "test",
        () ->
            jdbc.sql(
                    "delete from role_assignment where user_id = (select id from app_user where email = ?)")
                .param(email)
                .update());
    assertThat(get("/api/auth/v1/me", access).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void publicSiteResolution() {
    Organization org = school("mu");
    var res =
        get("/api/public/v1/sites/resolve?host=" + org.slug() + ".shikshablox.localhost", null);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody().get("name").asText()).isEqualTo(org.name());
    assertThat(
            get("/api/public/v1/sites/resolve?host=nope-" + suffix + ".shikshablox.localhost", null)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }
}
