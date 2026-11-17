package com.shikshablox.platform.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shikshablox.support.PostgresTestSupport;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cross-tenant isolation at the database layer: raw SQL as the runtime role must not read or write
 * another school's rows, whatever the WHERE clause says.
 */
@SpringBootTest
class RlsIsolationIT extends PostgresTestSupport {

  @Autowired OrganizationService organizations;
  @Autowired TenantResolver resolver;
  @Autowired JdbcClient jdbc;
  @Autowired TransactionTemplate tx;

  Organization a;
  Organization b;

  @BeforeEach
  void schools() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    a = organizations.create("rls-a-" + suffix, "School A " + suffix);
    b = organizations.create("rls-b-" + suffix, "School B " + suffix);
  }

  /** A raw connection as sb_app (bypasses the application entirely). */
  private Connection raw(UUID org) throws SQLException {
    Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "sb_app", "sb_app_test");
    try (PreparedStatement ps = c.prepareStatement("select set_config('app.org_id', ?, false)")) {
      ps.setString(1, org == null ? "" : org.toString());
      ps.execute();
    }
    return c;
  }

  private static int count(Connection c, String sql, Object... args) throws SQLException {
    try (PreparedStatement ps = c.prepareStatement(sql)) {
      for (int i = 0; i < args.length; i++) {
        ps.setObject(i + 1, args[i]);
      }
      try (var rs = ps.executeQuery()) {
        rs.next();
        return rs.getInt(1);
      }
    }
  }

  @Test
  void rawSqlCannotReadAnotherTenant() throws Exception {
    try (Connection c = raw(a.id())) {
      assertThat(count(c, "select count(*) from organization where id = ?", b.id())).isZero();
      assertThat(count(c, "select count(*) from campus where organization_id = ?", b.id()))
          .isZero();
      assertThat(count(c, "select count(*) from campus where organization_id = ?", a.id()))
          .isEqualTo(1);
      assertThat(count(c, "select count(*) from organization")).isEqualTo(1);
    }
  }

  @Test
  void noTenantContextMeansNoRows() throws Exception {
    try (Connection c = raw(null)) {
      assertThat(count(c, "select count(*) from organization")).isZero();
      assertThat(count(c, "select count(*) from campus")).isZero();
      assertThat(count(c, "select count(*) from app_user")).isZero();
      assertThat(count(c, "select count(*) from refresh_token")).isZero();
      assertThat(count(c, "select count(*) from login_otp")).isZero();
    }
  }

  @Test
  void rawSqlCannotWriteIntoAnotherTenant() throws Exception {
    try (Connection c = raw(a.id())) {
      try (PreparedStatement ps =
          c.prepareStatement("insert into campus (organization_id, name) values (?, 'Sneaky')")) {
        ps.setObject(1, b.id());
        assertThatThrownBy(ps::executeUpdate).hasMessageContaining("row-level security");
      }
      try (PreparedStatement ps =
          c.prepareStatement("update campus set name = 'Hijacked' where organization_id = ?")) {
        ps.setObject(1, b.id());
        assertThat(ps.executeUpdate()).isZero();
      }
      try (PreparedStatement ps = c.prepareStatement("delete from organization where id = ?")) {
        ps.setObject(1, b.id());
        assertThat(ps.executeUpdate()).isZero();
      }
      // Moving an own row into another tenant is rejected as well.
      try (PreparedStatement ps =
          c.prepareStatement("update campus set organization_id = ? where organization_id = ?")) {
        ps.setObject(1, b.id());
        ps.setObject(2, a.id());
        assertThatThrownBy(ps::executeUpdate).hasMessageContaining("row-level security");
      }
    }
  }

  @Test
  void runtimeRoleCannotDisableRls() throws Exception {
    try (Connection c = raw(a.id());
        var st = c.createStatement()) {
      assertThatThrownBy(() -> st.execute("alter table campus disable row level security"))
          .isInstanceOf(SQLException.class);
    }
  }

  @Test
  void serviceLayerOnlySeesCurrentTenant() {
    try (var ignored = TenantContext.forTenant(a.id(), null)) {
      assertThat(organizations.findVisible(a.id())).isPresent();
      assertThat(organizations.findVisible(b.id())).isEmpty();
      assertThat(jdbc.sql("select count(*) from campus").query(Integer.class).single())
          .isEqualTo(1);
    }
    assertThat(organizations.findVisible(a.id())).isEmpty(); // context closed => nothing
  }

  @Test
  void pooledConnectionsDoNotLeakTenant() {
    try (var ignored = TenantContext.forTenant(a.id(), null)) {
      assertThat(jdbc.sql("select current_setting('app.org_id')").query(String.class).single())
          .isEqualTo(a.id().toString());
    }
    assertThat(jdbc.sql("select current_setting('app.org_id', true)").query(String.class).single())
        .isEmpty();
  }

  @Test
  void hostResolution() {
    assertThat(resolver.resolve(a.slug() + ".shikshablox.localhost"))
        .map(Organization::id)
        .hasValue(a.id());
    assertThat(resolver.resolve(a.slug().toUpperCase() + ".ShikshaBlox.localhost:80")).isPresent();
    assertThat(resolver.resolve("unknown-school.shikshablox.localhost")).isEmpty();
    assertThat(resolver.resolve("admin.shikshablox.localhost")).isEmpty();
    assertThat(resolver.resolve("a.b.shikshablox.localhost")).isEmpty();
    assertThat(resolver.resolve("bad host")).isEmpty();

    String custom = "www." + a.slug() + ".example";
    assertThat(resolver.resolve(custom)).isEmpty();
    organizations.addVerifiedDomain(a.id(), custom);
    assertThat(resolver.resolve(custom)).map(Organization::id).hasValue(a.id());
  }

  @Test
  void contextCannotChangeInsideATransaction() {
    assertThatThrownBy(
            () -> tx.executeWithoutResult(s -> TenantContext.forTenant(b.id(), null).close()))
        .isInstanceOf(IllegalStateException.class);
  }
}
