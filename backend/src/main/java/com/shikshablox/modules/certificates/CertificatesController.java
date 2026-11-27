package com.shikshablox.modules.certificates;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Certificates (M32): Studio API and the public verification endpoint. */
@RestController
@Tag(name = "Certificates")
class CertificatesController {

  private static final String A = "/api/admin/v1/certificates";

  record DecisionRequest(boolean approve, String note) {}

  record ReasonRequest(String reason) {}

  private final CertificateService certs;

  CertificatesController(CertificateService certs) {
    this.certs = certs;
  }

  @GetMapping(A + "/settings")
  @PreAuthorize("hasAuthority('certificates:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(
      operationId = "certificateSettings",
      summary = "Signatory, school details, approval, no-dues items")
  CertificateService.Settings settings() {
    return certs.settings();
  }

  @PutMapping(A + "/settings")
  @PreAuthorize("hasAuthority('certificates:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "saveCertificateSettings", summary = "Change certificate settings")
  CertificateService.Settings saveSettings(@RequestBody CertificateService.SettingsInput in) {
    return certs.saveSettings(in);
  }

  @GetMapping(A + "/templates")
  @PreAuthorize("hasAuthority('certificates:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "certificateTemplates", summary = "Certificate templates")
  List<CertificateService.Template> templates() {
    return certs.templates();
  }

  @PostMapping(A + "/templates")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('certificates:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "createCertificateTemplate", summary = "Add the school's own template")
  CertificateService.Template createTemplate(@RequestBody CertificateService.TemplateInput in) {
    return certs.saveTemplate(null, in);
  }

  @PutMapping(A + "/templates/{id}")
  @PreAuthorize("hasAuthority('certificates:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(
      operationId = "updateCertificateTemplate",
      summary = "Change a template or its numbering")
  CertificateService.Template updateTemplate(
      @PathVariable UUID id, @RequestBody CertificateService.TemplateInput in) {
    return certs.saveTemplate(id, in);
  }

  @GetMapping(A + "/students")
  @PreAuthorize("hasAuthority('certificates:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "certificateStudents", summary = "Find a student")
  List<CertificateService.StudentHit> students(@RequestParam(required = false) String q) {
    return certs.students(q);
  }

  @GetMapping(A + "/draft")
  @PreAuthorize("hasAuthority('certificates:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "certificateDraft", summary = "Pre-filled certificate for a student")
  CertificateService.Draft draft(
      @RequestParam UUID templateId,
      @RequestParam UUID studentId,
      @RequestParam(required = false) String purpose) {
    return certs.draft(templateId, studentId, purpose);
  }

  @PostMapping(A)
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('certificates:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(
      operationId = "issueCertificate",
      summary = "Issue (or send for approval) a certificate")
  CertificateService.Certificate issue(@RequestBody CertificateService.IssueInput in) {
    return certs.issue(in);
  }

  @GetMapping(A)
  @PreAuthorize("hasAuthority('certificates:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(
      operationId = "certificateRegister",
      summary = "Issue register, requests and approvals")
  List<CertificateService.Certificate> register(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String kind,
      @RequestParam(required = false) String q) {
    return certs.register(status, kind, q);
  }

  @GetMapping(A + "/{id}/print")
  @PreAuthorize("hasAuthority('certificates:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "printCertificate", summary = "An issued certificate for printing")
  CertificateService.PrintView print(@PathVariable UUID id) {
    return certs.print(id);
  }

  @PostMapping(A + "/{id}/decision")
  @PreAuthorize("hasAuthority('certificates:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "decideCertificate", summary = "Approve or reject")
  CertificateService.Certificate decide(@PathVariable UUID id, @RequestBody DecisionRequest req) {
    return certs.decide(id, req.approve(), req.note());
  }

  @PostMapping(A + "/{id}/cancel")
  @PreAuthorize("hasAuthority('certificates:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "cancelCertificate", summary = "Cancel an issued certificate")
  CertificateService.Certificate cancel(@PathVariable UUID id, @RequestBody ReasonRequest req) {
    return certs.cancel(id, req.reason());
  }

  @PostMapping(A + "/{id}/duplicate")
  @PreAuthorize("hasAuthority('certificates:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "duplicateCertificate", summary = "Issue a duplicate")
  CertificateService.Certificate duplicate(@PathVariable UUID id, @RequestBody ReasonRequest req) {
    return certs.duplicate(id, req.reason());
  }

  @GetMapping("/api/public/v1/certificates/verify")
  @Operation(operationId = "verifyCertificate", summary = "Check a certificate by its QR code")
  CertificateService.Verification verify(@RequestParam String host, @RequestParam String code) {
    return certs.verify(host, code);
  }
}
