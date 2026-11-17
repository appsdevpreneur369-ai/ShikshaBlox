package com.shikshablox.platform.audit;

import com.shikshablox.platform.audit.AuditService.Entry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Audit")
@SecurityRequirement(name = "bearer")
class AuditController {

  private final AuditService audit;

  AuditController(AuditService audit) {
    this.audit = audit;
  }

  /** The current school's audit trail, newest first (keyset pagination via {@code beforeId}). */
  @GetMapping("/api/admin/v1/audit")
  @PreAuthorize("hasAuthority('audit:view')")
  @Operation(operationId = "searchAudit", summary = "Search the school's audit log")
  List<Entry> search(
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String entityType,
      @RequestParam(required = false) String entityId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(required = false) Long beforeId) {
    return audit.search(
        new AuditService.Query(action, entityType, entityId, from, to, limit, beforeId));
  }
}
