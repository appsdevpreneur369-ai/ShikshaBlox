package com.shikshablox.platform.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Data access for users and role assignments. All queries are subject to RLS. */
@Repository
class UserRepository {

  record AppUser(
      UUID id,
      String email,
      String displayName,
      String passwordHash,
      String status,
      String mfaSecretEnc,
      boolean mfaEnabled,
      Long mfaLastStep,
      int failedLoginCount,
      Instant lockedUntil) {

    boolean active() {
      return "ACTIVE".equals(status);
    }

    boolean locked(Instant now) {
      return lockedUntil != null && lockedUntil.isAfter(now);
    }
  }

  record Assignment(UUID organizationId, Role role) {}

  private static final String USER_COLUMNS =
      "id, email, display_name, password_hash, status, mfa_secret_enc, mfa_enabled, mfa_last_step,"
          + " failed_login_count, locked_until";

  private final JdbcClient jdbc;

  UserRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  Optional<AppUser> findByEmail(String email) {
    return jdbc.sql("select " + USER_COLUMNS + " from app_user where email = ?")
        .param(email)
        .query(UserRepository::map)
        .optional();
  }

  Optional<AppUser> findById(UUID id) {
    return jdbc.sql("select " + USER_COLUMNS + " from app_user where id = ?")
        .param(id)
        .query(UserRepository::map)
        .optional();
  }

  UUID insert(String email, String displayName, String passwordHash) {
    return jdbc.sql(
            "insert into app_user (email, display_name, password_hash) values (?, ?, ?)"
                + " returning id")
        .params(email, displayName, passwordHash)
        .query(UUID.class)
        .single();
  }

  void assign(UUID userId, UUID organizationId, Role role) {
    jdbc.sql(
            "insert into role_assignment (user_id, organization_id, role) values (?, ?, ?)"
                + " on conflict do nothing")
        .params(userId, organizationId, role.name())
        .update();
  }

  List<Assignment> assignments(UUID userId) {
    return jdbc.sql("select organization_id, role from role_assignment where user_id = ?")
        .param(userId)
        .query((rs, i) ->
            new Assignment(
                rs.getObject("organization_id", UUID.class), Role.valueOf(rs.getString("role"))))
        .list();
  }

  void recordFailedLogin(UUID id, int maxFailures, Instant lockUntil) {
    jdbc.sql(
            "update app_user set failed_login_count = failed_login_count + 1,"
                + " locked_until = case when failed_login_count + 1 >= ? then ? else locked_until end,"
                + " updated_at = now() where id = ?")
        .params(maxFailures, Timestamp.from(lockUntil), id)
        .update();
  }

  void recordSuccessfulLogin(UUID id) {
    jdbc.sql(
            "update app_user set failed_login_count = 0, locked_until = null,"
                + " last_login_at = now(), updated_at = now() where id = ?")
        .param(id)
        .update();
  }

  void setPendingMfaSecret(UUID id, String encryptedSecret) {
    jdbc.sql(
            "update app_user set mfa_secret_enc = ?, mfa_enabled = false, mfa_last_step = null,"
                + " updated_at = now() where id = ?")
        .params(encryptedSecret, id)
        .update();
  }

  void enableMfa(UUID id, long step) {
    jdbc.sql(
            "update app_user set mfa_enabled = true, mfa_last_step = ?, updated_at = now()"
                + " where id = ?")
        .params(step, id)
        .update();
  }

  /** Accepts a TOTP step only if it is newer than the last accepted one (atomic). */
  boolean advanceMfaStep(UUID id, long step) {
    return jdbc.sql(
                "update app_user set mfa_last_step = ? where id = ?"
                    + " and (mfa_last_step is null or mfa_last_step < ?)")
            .params(step, id, step)
            .update()
        == 1;
  }

  private static AppUser map(ResultSet rs, int row) throws SQLException {
    Timestamp locked = rs.getTimestamp("locked_until");
    Long lastStep = rs.getObject("mfa_last_step", Long.class);
    return new AppUser(
        rs.getObject("id", UUID.class),
        rs.getString("email"),
        rs.getString("display_name"),
        rs.getString("password_hash"),
        rs.getString("status"),
        rs.getString("mfa_secret_enc"),
        rs.getBoolean("mfa_enabled"),
        lastStep,
        rs.getInt("failed_login_count"),
        locked == null ? null : locked.toInstant());
  }
}
