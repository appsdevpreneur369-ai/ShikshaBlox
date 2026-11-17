package com.shikshablox.platform.consent;

import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.TenantContext;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Minimal consent registry for publishing identifiable student photos and videos. A school either
 * links specific guardian consent records (kept as references, never student data) or makes a
 * school-level declaration that consent has been collected. The SIS Consent Ledger replaces the
 * data source later ({@code source}) without changing callers.
 */
@Service
public class ConsentService {

  public enum Kind {
    SCHOOL_DECLARATION,
    GUARDIAN_CONSENT
  }

  public record ConsentRecord(
      UUID id,
      Kind kind,
      String purpose,
      String reference,
      String statement,
      String declaredBy,
      String source,
      LocalDate validFrom,
      LocalDate validUntil,
      Instant revokedAt,
      Instant createdAt) {

    public boolean activeOn(LocalDate day) {
      return revokedAt == null
          && !validFrom.isAfter(day)
          && (validUntil == null || !validUntil.isBefore(day));
    }
  }

  public static final String WEBSITE_MEDIA = "website_media";

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final AuditService audit;

  ConsentService(JdbcClient jdbc, TransactionTemplate tx, AuditService audit) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.audit = audit;
  }

  public ConsentRecord create(
      Kind kind,
      String reference,
      String statement,
      String declaredBy,
      LocalDate validFrom,
      LocalDate validUntil) {
    UUID org = TenantContext.requireOrganizationId();
    if (reference == null || reference.isBlank() || declaredBy == null || declaredBy.isBlank()) {
      throw ApiException.badRequest("invalid_consent", "Reference and declared-by are required.");
    }
    LocalDate from = validFrom == null ? LocalDate.now() : validFrom;
    if (validUntil != null && validUntil.isBefore(from)) {
      throw ApiException.badRequest("invalid_dates", "The end date must be after the start date.");
    }
    return tx.execute(s -> {
      ConsentRecord rec =
          jdbc.sql(
                  "insert into consent_record (organization_id, kind, purpose, reference, statement,"
                      + " declared_by, valid_from, valid_until, created_by)"
                      + " values (?, ?, ?, ?, ?, ?, ?, ?, ?) returning *")
              .params(
                  org,
                  kind.name(),
                  WEBSITE_MEDIA,
                  reference.trim(),
                  statement,
                  declaredBy.trim(),
                  Date.valueOf(from),
                  validUntil == null ? null : Date.valueOf(validUntil),
                  TenantContext.userId().orElse(null))
              .query(ConsentService::map)
              .single();
      audit.record(
          "consent.created",
          "consent",
          rec.id().toString(),
          null,
          Map.of("kind", kind, "reference", rec.reference()));
      return rec;
    });
  }

  public void revoke(UUID id) {
    tx.executeWithoutResult(s -> {
      int n =
          jdbc.sql(
                  "update consent_record set revoked_at = now() where id = ? and revoked_at is null")
              .param(id)
              .update();
      if (n == 0) {
        throw ApiException.notFound("Consent record");
      }
      audit.record("consent.revoked", "consent", id.toString(), null, null);
    });
  }

  public List<ConsentRecord> list() {
    return jdbc.sql("select * from consent_record order by created_at desc")
        .query(ConsentService::map)
        .list();
  }

  public Optional<ConsentRecord> find(UUID id) {
    return jdbc.sql("select * from consent_record where id = ?")
        .param(id)
        .query(ConsentService::map)
        .optional();
  }

  /** Whether the school currently has an active school-level declaration. */
  public boolean hasActiveDeclaration() {
    LocalDate today = LocalDate.now();
    return list().stream().anyMatch(r -> r.kind() == Kind.SCHOOL_DECLARATION && r.activeOn(today));
  }

  /**
   * The publishing rule for media: non-identifiable media is always fine; identifiable media needs
   * an active linked consent record or an active school declaration.
   */
  public boolean mayPublish(boolean containsIdentifiableStudents, UUID consentRecordId) {
    if (!containsIdentifiableStudents) {
      return true;
    }
    LocalDate today = LocalDate.now();
    if (consentRecordId != null
        && find(consentRecordId).map(r -> r.activeOn(today)).orElse(false)) {
      return true;
    }
    return hasActiveDeclaration();
  }

  private static ConsentRecord map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
    Date until = rs.getDate("valid_until");
    java.sql.Timestamp revoked = rs.getTimestamp("revoked_at");
    return new ConsentRecord(
        rs.getObject("id", UUID.class),
        Kind.valueOf(rs.getString("kind")),
        rs.getString("purpose"),
        rs.getString("reference"),
        rs.getString("statement"),
        rs.getString("declared_by"),
        rs.getString("source"),
        rs.getDate("valid_from").toLocalDate(),
        until == null ? null : until.toLocalDate(),
        revoked == null ? null : revoked.toInstant(),
        rs.getTimestamp("created_at").toInstant());
  }
}
