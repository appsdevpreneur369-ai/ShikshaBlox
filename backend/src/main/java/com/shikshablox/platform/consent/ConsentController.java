package com.shikshablox.platform.consent;

import com.shikshablox.platform.consent.ConsentService.ConsentRecord;
import com.shikshablox.platform.consent.ConsentService.Kind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Consent")
@SecurityRequirement(name = "bearer")
class ConsentController {

  record CreateConsentRequest(
      @NotNull Kind kind,
      @NotBlank @Size(min = 3, max = 300) String reference,
      @Size(max = 4000) String statement,
      @NotBlank @Size(max = 200) String declaredBy,
      LocalDate validFrom,
      LocalDate validUntil) {}

  record ConsentSummary(boolean activeDeclaration, List<ConsentRecord> records) {}

  private final ConsentService consents;

  ConsentController(ConsentService consents) {
    this.consents = consents;
  }

  @GetMapping("/api/admin/v1/consents")
  @PreAuthorize("hasAuthority('content:view')")
  @Operation(operationId = "listConsents", summary = "Consent records for publishing student media")
  ConsentSummary list() {
    return new ConsentSummary(consents.hasActiveDeclaration(), consents.list());
  }

  @PostMapping("/api/admin/v1/consents")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('school:manage')")
  @Operation(
      operationId = "createConsent",
      summary = "Record a school declaration or guardian consent reference")
  ConsentRecord create(@Valid @RequestBody CreateConsentRequest req) {
    return consents.create(
        req.kind(),
        req.reference(),
        req.statement(),
        req.declaredBy(),
        req.validFrom(),
        req.validUntil());
  }

  @DeleteMapping("/api/admin/v1/consents/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('school:manage')")
  @Operation(operationId = "revokeConsent", summary = "Revoke a consent record")
  void revoke(@PathVariable UUID id) {
    consents.revoke(id);
  }
}
