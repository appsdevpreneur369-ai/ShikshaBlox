package com.shikshablox.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.shikshablox.platform.identity.Role;
import com.shikshablox.platform.identity.UserService;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.OrganizationService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** HTTP-level test base: creates schools and users and signs them in. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class ApiTestSupport extends PostgresTestSupport {

  protected static final String PASSWORD = "Correct-Horse-42";

  @Autowired protected TestRestTemplate http;
  @Autowired protected OrganizationService organizations;
  @Autowired protected UserService users;

  protected String ip;
  protected String suffix;

  @BeforeEach
  void newClient() {
    ip = "10.1." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
    suffix = UUID.randomUUID().toString().substring(0, 8);
  }

  protected Organization school(String name) {
    return organizations.create(name + "-" + suffix, "School " + name + " " + suffix);
  }

  protected String user(String local, Organization org, Role role) {
    String email = local + "-" + suffix + "@example.test";
    UUID id = users.ensureUser(email, local, PASSWORD);
    users.grantRole(id, org == null ? null : org.id(), role);
    return email;
  }

  /** Signs in (no MFA) and returns the access token. */
  protected String token(String email) {
    var res =
        send(
            HttpMethod.POST,
            "/api/auth/v1/login",
            Map.of("email", email, "password", PASSWORD),
            null);
    return res.getBody().at("/tokens/accessToken").asText();
  }

  protected String tokenFor(Organization org, Role role) {
    return token(user(role.name().toLowerCase() + "-" + org.slug(), org, role));
  }

  protected ResponseEntity<JsonNode> send(
      HttpMethod method, String path, Object body, String bearer) {
    HttpHeaders h = new HttpHeaders();
    h.set("X-Forwarded-For", ip);
    if (body != null && !(body instanceof org.springframework.util.MultiValueMap)) {
      h.setContentType(MediaType.APPLICATION_JSON);
    } else if (body != null) {
      h.setContentType(MediaType.MULTIPART_FORM_DATA);
    }
    if (bearer != null) {
      h.setBearerAuth(bearer);
    }
    return http.exchange(path, method, new HttpEntity<>(body, h), JsonNode.class);
  }

  protected ResponseEntity<JsonNode> get(String path, String bearer) {
    return send(HttpMethod.GET, path, null, bearer);
  }
}
