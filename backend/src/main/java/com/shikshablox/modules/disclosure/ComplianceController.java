package com.shikshablox.modules.disclosure;

import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Studio API for the compliance calendar and evidence vault (M02). */
@RestController
@RequestMapping("/api/admin/v1/compliance")
@PreAuthorize("hasAuthority('compliance:manage')")
@SecurityRequirement(name = "bearer")
@Tag(name = "Compliance")
class ComplianceController {

  record CompleteRequest(LocalDate completedOn, String note, UUID documentId) {}

  record ActiveRequest(boolean active) {}

  record TemplatesRequest(List<String> keys) {}

  private final ComplianceService compliance;

  ComplianceController(ComplianceService compliance) {
    this.compliance = compliance;
  }

  @GetMapping("/summary")
  @Operation(operationId = "complianceSummary", summary = "Overdue, due soon and expiring counts")
  ComplianceService.Summary summary() {
    return compliance.summary();
  }

  @GetMapping("/items")
  @Operation(operationId = "listComplianceItems", summary = "Calendar items, soonest due first")
  List<ComplianceService.Item> items(@RequestParam(defaultValue = "false") boolean archived) {
    return compliance.items(archived);
  }

  @PostMapping("/items")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(operationId = "createComplianceItem", summary = "Add an obligation")
  ComplianceService.Item create(@RequestBody ComplianceService.ItemInput in) {
    return compliance.create(in);
  }

  @GetMapping("/items/{id}")
  @Operation(operationId = "getComplianceItem", summary = "Item with completions and documents")
  ComplianceService.ItemDetail item(@PathVariable UUID id) {
    return compliance.item(id);
  }

  @PatchMapping("/items/{id}")
  @Operation(operationId = "updateComplianceItem", summary = "Edit an obligation")
  ComplianceService.Item update(
      @PathVariable UUID id, @RequestBody ComplianceService.ItemInput in) {
    return compliance.update(id, in);
  }

  @DeleteMapping("/items/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "deleteComplianceItem", summary = "Delete an item and its history")
  void delete(@PathVariable UUID id) {
    compliance.delete(id);
  }

  @PostMapping("/items/{id}/active")
  @Operation(operationId = "archiveComplianceItem", summary = "Archive or restore an obligation")
  ComplianceService.Item active(@PathVariable UUID id, @RequestBody ActiveRequest req) {
    return compliance.setActive(id, req.active());
  }

  @PostMapping("/items/{id}/complete")
  @Operation(operationId = "completeComplianceItem", summary = "Mark the current cycle done")
  ComplianceService.ItemDetail complete(@PathVariable UUID id, @RequestBody CompleteRequest req) {
    return compliance.complete(id, req.completedOn(), req.note(), req.documentId());
  }

  @GetMapping("/templates")
  @Operation(operationId = "complianceTemplates", summary = "Suggested obligations")
  List<ComplianceService.Template> templates() {
    return compliance.templates();
  }

  @PostMapping("/templates")
  @Operation(operationId = "addComplianceTemplates", summary = "Add suggested obligations")
  Map<String, Integer> addTemplates(@RequestBody TemplatesRequest req) {
    return Map.of("added", compliance.addTemplates(req.keys()));
  }

  @GetMapping("/people")
  @Operation(operationId = "compliancePeople", summary = "Staff who can own items")
  List<ComplianceService.Person> people() {
    return compliance.people();
  }

  @GetMapping("/documents")
  @Operation(operationId = "listComplianceDocuments", summary = "Evidence vault")
  List<ComplianceService.Document> documents() {
    return compliance.documents();
  }

  @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(operationId = "uploadComplianceDocument", summary = "Upload a certificate or proof")
  ComplianceService.Document upload(
      @RequestPart("file") MultipartFile file,
      @RequestParam String title,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) UUID itemId,
      @RequestParam(required = false) String referenceNo,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate issuedOn,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate expiresOn) {
    if (file.getSize() > ComplianceService.MAX_FILE_BYTES) {
      throw ApiException.badRequest("file_too_large", "Upload files up to 10 MB.");
    }
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      throw ApiException.badRequest("upload_failed", "The upload could not be read.");
    }
    return compliance.upload(
        new ComplianceService.DocumentInput(
            itemId, title, category, referenceNo, issuedOn, expiresOn),
        new ComplianceService.Upload(file.getOriginalFilename(), bytes));
  }

  @PatchMapping("/documents/{id}")
  @Operation(operationId = "updateComplianceDocument", summary = "Edit document details")
  ComplianceService.Document updateDocument(
      @PathVariable UUID id, @RequestBody ComplianceService.DocumentInput in) {
    return compliance.updateDocument(id, in);
  }

  @DeleteMapping("/documents/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "deleteComplianceDocument", summary = "Delete a document")
  void deleteDocument(@PathVariable UUID id) {
    compliance.deleteDocument(id);
  }

  @GetMapping("/documents/{id}/download")
  @Operation(operationId = "complianceDocumentUrl", summary = "A 10-minute download link")
  Map<String, String> download(@PathVariable UUID id) {
    return Map.of("url", compliance.downloadUrl(id));
  }

  @GetMapping(value = "/export.zip", produces = "application/zip")
  @Operation(operationId = "complianceExport", summary = "Inspection pack: register + documents")
  ResponseEntity<StreamingResponseBody> export() {
    compliance.checkExport();
    String name = "compliance-pack-" + ComplianceService.today() + ".zip";
    // The body is written on another thread: carry the school and user over (RLS depends on it).
    UUID org = TenantContext.requireOrganizationId();
    UUID user = TenantContext.userId().orElse(null);
    StreamingResponseBody body =
        out -> {
          try (var ignored = TenantContext.forTenant(org, user)) {
            compliance.writeExport(out);
          }
        };
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
        .contentType(MediaType.parseMediaType("application/zip"))
        .body(body);
  }
}
