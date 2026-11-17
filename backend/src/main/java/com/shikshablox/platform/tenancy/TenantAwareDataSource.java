package com.shikshablox.platform.tenancy;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.flyway.FlywayDataSource;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

/**
 * Binds every checked-out connection to the current {@link TenantContext} by setting the session
 * variables {@code app.org_id}, {@code app.user_id} and {@code app.system} that the RLS policies
 * read. The values are overwritten on every checkout (blank when absent), so a pooled connection
 * can never carry a previous request's tenant.
 */
class TenantAwareDataSource extends DelegatingDataSource {

  private static final String BIND_SQL =
      "select set_config('app.org_id', ?, false), set_config('app.user_id', ?, false),"
          + " set_config('app.system', ?, false)";

  TenantAwareDataSource(DataSource target) {
    super(target);
  }

  @Override
  public Connection getConnection() throws SQLException {
    return bind(super.getConnection());
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    return bind(super.getConnection(username, password));
  }

  private static Connection bind(Connection connection) throws SQLException {
    TenantContext.Snapshot ctx = TenantContext.current();
    try (PreparedStatement ps = connection.prepareStatement(BIND_SQL)) {
      ps.setString(1, ctx.organizationId() == null ? "" : ctx.organizationId().toString());
      ps.setString(2, ctx.userId() == null ? "" : ctx.userId().toString());
      ps.setString(3, ctx.system() ? "on" : "");
      ps.execute();
    } catch (SQLException e) {
      connection.close();
      throw e;
    }
    return connection;
  }

  /**
   * The application pool (runtime role, wrapped for RLS binding) and a separate migration
   * connection for Flyway (owner role). Defined explicitly because Flyway cannot derive its
   * connection from a wrapped pool.
   */
  @Configuration(proxyBeanMethods = false)
  static class Registration {

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource applicationPool(DataSourceProperties properties) {
      return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    DataSource dataSource(HikariDataSource applicationPool) {
      return new TenantAwareDataSource(applicationPool);
    }

    @Bean
    @FlywayDataSource
    DataSource flywayDataSource(DataSourceProperties properties, FlywayProperties flyway) {
      SimpleDriverDataSource ds = new SimpleDriverDataSource();
      ds.setDriverClass(org.postgresql.Driver.class);
      ds.setUrl(flyway.getUrl() != null ? flyway.getUrl() : properties.determineUrl());
      ds.setUsername(flyway.getUser());
      ds.setPassword(flyway.getPassword());
      return ds;
    }
  }
}
