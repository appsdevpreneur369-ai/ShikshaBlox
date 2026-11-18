package com.shikshablox.modules.website;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Enquiries")
class EnquiryController {

  record UpdateRequest(String status, @Size(max = 4000) String notes) {}

  private final EnquiryService enquiries;

  EnquiryController(EnquiryService enquiries) {
    this.enquiries = enquiries;
  }

  @PostMapping("/api/public/v1/enquiries")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(operationId = "submitEnquiry", summary = "Website enquiry form submission")
  Map<String, String> submit(
      @RequestParam String host,
      @RequestBody JsonNode body,
      @RequestHeader(value = "X-Client-IP", required = false) String clientIp) {
    enquiries.submit(host, body, clientIp);
    return Map.of("status", "received");
  }

  @GetMapping("/api/admin/v1/enquiries")
  @PreAuthorize("hasAuthority('enquiry:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "listEnquiries", summary = "Enquiry inbox")
  List<EnquiryService.Enquiry> list(
      @RequestParam(required = false) String status, @RequestParam(required = false) String q) {
    return enquiries.list(status, q);
  }

  @GetMapping("/api/admin/v1/enquiries/counts")
  @PreAuthorize("hasAuthority('enquiry:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "enquiryCounts", summary = "Enquiries per status")
  Map<String, Long> counts() {
    return enquiries.counts();
  }

  @PutMapping("/api/admin/v1/enquiries/{id}")
  @PreAuthorize("hasAuthority('enquiry:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "updateEnquiry", summary = "Change status or notes")
  EnquiryService.Enquiry update(@PathVariable UUID id, @RequestBody UpdateRequest req) {
    return enquiries.update(id, req.status(), req.notes());
  }
}
