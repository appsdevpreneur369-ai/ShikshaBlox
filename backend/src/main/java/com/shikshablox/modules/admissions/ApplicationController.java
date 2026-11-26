package com.shikshablox.modules.admissions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shikshablox.platform.shared.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/** Online application form (public) and application review (Studio), M03b. */
@RestController
@Tag(name = "Admission applications")
class ApplicationController {

  record ReviewRequest(String status, String note) {}

  record FeeRequest(String status, String reference) {}

  private final ApplicationService applications;
  private final ObjectMapper json;

  ApplicationController(ApplicationService applications, ObjectMapper json) {
    this.applications = applications;
    this.json = json;
  }

  // ------------------------------------------------------------------ public

  @GetMapping("/api/public/v1/applications/form")
  @Operation(operationId = "applicationForm", summary = "The school's online application form")
  ApplicationService.PublicForm form(@RequestParam String host) {
    return applications.publicForm(host);
  }

  /** Multipart: a {@code data} JSON field plus one file per document, named {@code doc_<code>}. */
  @PostMapping(
      value = "/api/public/v1/applications",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(operationId = "submitApplication", summary = "Submit an online application")
  ApplicationService.Submitted submit(
      @RequestParam String host,
      MultipartHttpServletRequest request,
      @RequestHeader(value = "X-Client-IP", required = false) String clientIp) {
    String data = request.getParameter("data");
    if (data == null || data.length() > 20_000) {
      throw ApiException.badRequest("invalid_application", "Please fill in the form.");
    }
    JsonNode body;
    try {
      body = json.readTree(data);
    } catch (IOException e) {
      throw ApiException.badRequest("invalid_application", "Please fill in the form.");
    }
    List<ApplicationService.IncomingFile> files = new ArrayList<>();
    for (Map.Entry<String, MultipartFile> e : request.getFileMap().entrySet()) {
      if (!e.getKey().startsWith("doc_") || e.getValue().isEmpty()) {
        continue;
      }
      files.add(file(e.getKey().substring(4), e.getValue()));
    }
    return applications.submit(host, body, files, clientIp);
  }

  private static ApplicationService.IncomingFile file(String code, MultipartFile f) {
    if (f.getSize() > ApplicationService.MAX_FILE_BYTES) {
      throw ApiException.badRequest(
          "file_too_large", f.getOriginalFilename() + " is larger than 5 MB.");
    }
    try {
      return new ApplicationService.IncomingFile(code, f.getOriginalFilename(), f.getBytes());
    } catch (IOException e) {
      throw ApiException.badRequest("upload_failed", "The upload could not be read.");
    }
  }

  // ------------------------------------------------------------------ studio

  @GetMapping("/api/admin/v1/admissions/application-settings")
  @PreAuthorize("hasAuthority('admissions:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "applicationSettings", summary = "Online application form settings")
  ApplicationService.FormSettings settings() {
    return applications.settings();
  }

  @PutMapping("/api/admin/v1/admissions/application-settings")
  @PreAuthorize("hasAuthority('admissions:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(
      operationId = "saveApplicationSettings",
      summary = "Open/close the form; documents and fee")
  ApplicationService.FormSettings saveSettings(@RequestBody ApplicationService.FormSettings in) {
    return applications.saveSettings(in);
  }

  @GetMapping("/api/admin/v1/admissions/applications")
  @PreAuthorize("hasAuthority('admissions:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "listApplications", summary = "Applications, newest first")
  List<ApplicationService.Summary> list(
      @RequestParam(required = false) String year,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) UUID leadId,
      @RequestParam(required = false) String q) {
    return applications.list(year, status, leadId, q);
  }

  @GetMapping("/api/admin/v1/admissions/applications/{id}")
  @PreAuthorize("hasAuthority('admissions:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "getApplication", summary = "Application with its documents")
  ApplicationService.Application get(@PathVariable UUID id) {
    return applications.get(id);
  }

  @PatchMapping("/api/admin/v1/admissions/applications/{id}")
  @PreAuthorize("hasAuthority('admissions:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "reviewApplication", summary = "Set review status and note")
  ApplicationService.Application review(@PathVariable UUID id, @RequestBody ReviewRequest req) {
    return applications.review(id, req.status(), req.note());
  }

  @PostMapping("/api/admin/v1/admissions/applications/{id}/fee")
  @PreAuthorize("hasAuthority('admissions:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "recordApplicationFee", summary = "Record the application fee status")
  ApplicationService.Application fee(@PathVariable UUID id, @RequestBody FeeRequest req) {
    return applications.recordFee(id, req.status(), req.reference());
  }

  @PostMapping(
      value = "/api/admin/v1/admissions/applications/{id}/documents",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAuthority('admissions:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "addApplicationDocument", summary = "Upload a document for the family")
  ApplicationService.Application addDocument(
      @PathVariable UUID id,
      @RequestParam String docCode,
      @RequestPart("file") MultipartFile file) {
    return applications.addDocument(id, file(docCode, file));
  }

  @DeleteMapping("/api/admin/v1/admissions/applications/{id}")
  @PreAuthorize("hasAuthority('admissions:manage')")
  @SecurityRequirement(name = "bearer")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "deleteApplication", summary = "Delete an application and its files")
  void delete(@PathVariable UUID id) {
    applications.delete(id);
  }

  @PostMapping("/api/admin/v1/admissions/documents/{id}/review")
  @PreAuthorize("hasAuthority('admissions:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "reviewApplicationDocument", summary = "Verify or reject a document")
  ApplicationService.Application reviewDocument(
      @PathVariable UUID id, @RequestBody ReviewRequest req) {
    return applications.reviewDocument(id, req.status(), req.note());
  }

  @GetMapping("/api/admin/v1/admissions/documents/{id}/download")
  @PreAuthorize("hasAuthority('admissions:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "applicationDocumentUrl", summary = "A 10-minute download link")
  Map<String, String> download(@PathVariable UUID id) {
    return Map.of("url", applications.downloadUrl(id));
  }
}
