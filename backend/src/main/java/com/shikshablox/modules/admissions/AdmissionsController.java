package com.shikshablox.modules.admissions;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Studio API for the admissions CRM (M03). */
@RestController
@RequestMapping("/api/admin/v1/admissions")
@PreAuthorize("hasAuthority('admissions:manage')")
@SecurityRequirement(name = "bearer")
@Tag(name = "Admissions CRM")
class AdmissionsController {

  record MoveRequest(String stage, String reason) {}

  record LogRequest(String kind, String body, Instant nextFollowUpAt) {}

  record SeatsRequest(String academicYear, List<AdmissionsService.Seat> seats) {}

  private final AdmissionsService admissions;

  AdmissionsController(AdmissionsService admissions) {
    this.admissions = admissions;
  }

  private static AdmissionsService.Filter filter(
      String stage,
      String source,
      String year,
      UUID assignedTo,
      String grade,
      String q,
      String due) {
    return new AdmissionsService.Filter(stage, source, year, assignedTo, grade, q, due);
  }

  @GetMapping("/leads")
  @Operation(
      operationId = "listLeads",
      summary = "Leads, filtered; open leads first by follow-up date")
  List<AdmissionsService.Lead> list(
      @RequestParam(required = false) String stage,
      @RequestParam(required = false) String source,
      @RequestParam(required = false) String year,
      @RequestParam(required = false) UUID assignedTo,
      @RequestParam(required = false) String grade,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String due) {
    return admissions.list(filter(stage, source, year, assignedTo, grade, q, due));
  }

  @PostMapping("/leads")
  @Operation(operationId = "createLead", summary = "Add a walk-in, phone or referral lead")
  AdmissionsService.Lead create(@RequestBody AdmissionsService.LeadInput in) {
    return admissions.create(in);
  }

  @GetMapping("/leads/{id}")
  @Operation(operationId = "getLead", summary = "Lead with its activity timeline")
  AdmissionsService.LeadDetail get(@PathVariable UUID id) {
    return admissions.get(id);
  }

  @PatchMapping("/leads/{id}")
  @Operation(operationId = "updateLead", summary = "Edit lead details, assignee or next follow-up")
  AdmissionsService.Lead update(
      @PathVariable UUID id, @RequestBody AdmissionsService.LeadInput in) {
    return admissions.update(id, in);
  }

  @PostMapping("/leads/{id}/stage")
  @Operation(operationId = "moveLead", summary = "Move a lead to another pipeline stage")
  AdmissionsService.Lead move(@PathVariable UUID id, @RequestBody MoveRequest req) {
    return admissions.move(id, req.stage(), req.reason());
  }

  @PostMapping("/leads/{id}/activities")
  @Operation(operationId = "logLeadActivity", summary = "Log a call, visit, message or note")
  AdmissionsService.LeadDetail log(@PathVariable UUID id, @RequestBody LogRequest req) {
    return admissions.log(id, req.kind(), req.body(), req.nextFollowUpAt());
  }

  @PostMapping("/import-enquiries")
  @Operation(
      operationId = "importEnquiries",
      summary = "Create leads for website enquiries that have none")
  Map<String, Integer> importEnquiries() {
    return Map.of("imported", admissions.importEnquiries());
  }

  @GetMapping("/funnel")
  @Operation(operationId = "admissionsFunnel", summary = "Counts by stage and source for a year")
  AdmissionsService.Funnel funnel(@RequestParam(required = false) String year) {
    return admissions.funnel(year);
  }

  @GetMapping("/seats")
  @Operation(
      operationId = "listSeats",
      summary = "Seats per class with admitted and offered counts")
  List<AdmissionsService.Seat> seats(@RequestParam(required = false) String year) {
    return admissions.seats(year);
  }

  @PutMapping("/seats")
  @Operation(operationId = "saveSeats", summary = "Replace seat capacities for a year")
  List<AdmissionsService.Seat> saveSeats(@RequestBody SeatsRequest req) {
    return admissions.saveSeats(req.academicYear(), req.seats());
  }

  @GetMapping("/counsellors")
  @Operation(operationId = "listCounsellors", summary = "Staff who can be assigned leads")
  List<AdmissionsService.Counsellor> counsellors() {
    return admissions.counsellors();
  }

  @GetMapping("/meta")
  @Operation(
      operationId = "admissionsMeta",
      summary = "Stages, sources and the current academic year")
  Map<String, Object> meta() {
    return Map.of(
        "stages", AdmissionsService.STAGES,
        "sources", AdmissionsService.SOURCES.stream().sorted().toList(),
        "academicYear", AdmissionsService.currentAcademicYear());
  }

  @GetMapping(value = "/leads.csv", produces = "text/csv")
  @Operation(operationId = "exportLeads", summary = "Leads as CSV")
  ResponseEntity<String> csv(
      @RequestParam(required = false) String stage,
      @RequestParam(required = false) String source,
      @RequestParam(required = false) String year,
      @RequestParam(required = false) UUID assignedTo,
      @RequestParam(required = false) String grade,
      @RequestParam(required = false) String q) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"admission-leads.csv\"")
        .contentType(new MediaType("text", "csv"))
        .body(admissions.csv(filter(stage, source, year, assignedTo, grade, q, null)));
  }
}
