package com.shikshablox;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikshablox.support.PostgresTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationIT extends PostgresTestSupport {

  @Autowired TestRestTemplate http;
  @Autowired JdbcTemplate jdbc;

  @Test
  void healthIsUpAndPublic() {
    var res = http.getForEntity("/actuator/health", String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody()).contains("UP");
  }

  @Test
  void otherActuatorEndpointsAreDenied() {
    assertThat(http.getForEntity("/actuator/env", String.class).getStatusCode().is4xxClientError())
        .isTrue();
  }

  @Test
  void unknownApiIsDeniedByDefault() {
    assertThat(http.getForEntity("/api/admin/v1/anything", String.class).getStatusCode())
        .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
  }

  @Test
  void pingReturnsRequestIdHeader() {
    var res = http.getForEntity("/api/public/v1/system/ping", String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getHeaders().getFirst("X-Request-Id")).isNotBlank();
  }

  @Test
  void runtimeRoleIsNotTableOwnerAndCannotBypassRls() {
    assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("sb_app");
    Boolean bypass =
        jdbc.queryForObject(
            "select rolbypassrls or rolsuper from pg_roles where rolname = current_user",
            Boolean.class);
    assertThat(bypass).isFalse();
  }

  /** Exports the OpenAPI document consumed by packages/api-client (CI checks for drift). */
  @Test
  void exportsOpenApiDocument() throws Exception {
    var res = http.getForEntity("/v3/api-docs", String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    Path out = Path.of("..", "docs", "api", "openapi.json");
    Files.createDirectories(out.getParent());
    Files.writeString(out, prettyJson(res.getBody()) + "\n");
  }

  private static String prettyJson(String json) throws Exception {
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    var tree = mapper.readTree(json);
    // Server URL depends on the random test port; strip it so the file is stable.
    if (tree instanceof com.fasterxml.jackson.databind.node.ObjectNode obj) {
      obj.remove("servers");
    }
    return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree);
  }
}
