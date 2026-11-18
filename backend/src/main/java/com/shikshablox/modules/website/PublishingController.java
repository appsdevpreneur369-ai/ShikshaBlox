package com.shikshablox.modules.website;

import com.fasterxml.jackson.databind.JsonNode;
import com.shikshablox.platform.shared.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** M5 publishing workflow, previews and disclosure review. */
@RestController
@Tag(name = "Publishing")
class PublishingController {

  record NoteRequest(@Size(max = 500) String note) {}

  record ScheduleRequest(@NotNull Instant at) {}

  record RollbackRequest(@NotNull UUID revisionId) {}

  private static final String A = "/api/admin/v1/pages/{id}";

  private final PublishingService publishing;

  PublishingController(PublishingService publishing) {
    this.publishing = publishing;
  }

  @PostMapping(A + "/submit")
  @PreAuthorize("hasAuthority('content:edit')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "submitPage", summary = "Submit the draft for approval")
  PageService.PageDetail submit(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "en") String lang,
      @RequestBody(required = false) NoteRequest req) {
    return publishing.submitForReview(id, lang, req == null ? null : req.note());
  }

  @PostMapping(A + "/reject")
  @PreAuthorize("hasAuthority('content:publish')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "rejectPage", summary = "Send the draft back with a note")
  PageService.PageDetail reject(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "en") String lang,
      @RequestBody(required = false) NoteRequest req) {
    return publishing.reject(id, lang, req == null ? null : req.note());
  }

  @PostMapping(A + "/schedule")
  @PreAuthorize("hasAuthority('content:publish')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "schedulePage", summary = "Publish the draft at a future time")
  PageService.PageDetail schedule(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "en") String lang,
      @RequestBody ScheduleRequest req) {
    return publishing.schedule(id, lang, req.at());
  }

  @PostMapping(A + "/schedule/cancel")
  @PreAuthorize("hasAuthority('content:publish')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "cancelSchedule", summary = "Cancel a scheduled publish")
  PageService.PageDetail cancelSchedule(
      @PathVariable UUID id, @RequestParam(defaultValue = "en") String lang) {
    return publishing.cancelSchedule(id, lang);
  }

  @PostMapping(A + "/rollback")
  @PreAuthorize("hasAuthority('content:publish')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "rollbackPage", summary = "Restore and publish an earlier revision")
  PageService.PageDetail rollback(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "en") String lang,
      @RequestBody RollbackRequest req) {
    return publishing.rollback(id, lang, req.revisionId());
  }

  @PostMapping(A + "/preview-token")
  @PreAuthorize("hasAuthority('content:view')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "previewToken", summary = "Short-lived link to preview the draft")
  Map<String, String> previewToken(
      @PathVariable UUID id, @RequestParam(defaultValue = "en") String lang) {
    return Map.of("token", publishing.previewToken(id, lang));
  }

  @GetMapping("/api/public/v1/site/preview")
  @Operation(operationId = "publicPreview", summary = "Draft page for a valid preview token")
  ResponseEntity<JsonNode> preview(@RequestParam String host, @RequestParam String token) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(publishing.preview(host, token).orElseThrow(() -> ApiException.notFound("Preview")));
  }

  @GetMapping("/api/admin/v1/disclosures/review")
  @PreAuthorize("hasAuthority('content:view')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "disclosureReview", summary = "When disclosures were last reviewed")
  Map<String, Object> disclosureReview() {
    Map<String, Object> out = new HashMap<>();
    Instant at = publishing.disclosureReviewedAt().orElse(null);
    out.put("reviewedAt", at);
    out.put(
        "due",
        at == null || at.isBefore(Instant.now().minus(PublishingService.DISCLOSURE_REVIEW_EVERY)));
    return out;
  }

  @PostMapping("/api/admin/v1/disclosures/review")
  @PreAuthorize("hasAuthority('school:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "markDisclosureReviewed", summary = "Mark disclosures as reviewed today")
  Map<String, Object> markReviewed() {
    Map<String, Object> out = new HashMap<>();
    out.put("reviewedAt", publishing.markDisclosureReviewed());
    out.put("due", false);
    return out;
  }
}
