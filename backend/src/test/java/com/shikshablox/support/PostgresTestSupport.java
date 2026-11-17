package com.shikshablox.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared PostgreSQL 16 container (one per JVM) configured exactly like production: Flyway runs as
 * the owner role, the application connects as the non-owner {@code sb_app} role so RLS applies.
 */
public abstract class PostgresTestSupport {

  protected static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
          .withInitScript("db/test-init/roles.sql");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", () -> "sb_app");
    registry.add("spring.datasource.password", () -> "sb_app_test");
    registry.add("spring.flyway.user", () -> "sb_owner");
    registry.add("spring.flyway.password", () -> "sb_owner_test");
  }
}
