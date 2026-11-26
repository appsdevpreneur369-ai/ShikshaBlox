package com.shikshablox.modules.engagement;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** PTM, helpdesk and surveys (M26): public website endpoints and the Studio API. */
@RestController
@Tag(name = "PTM, helpdesk & surveys")
class EngagementController {

  private static final String A = "/api/admin/v1/engagement";
  private static final String P = "/api/public/v1/engagement";

  record BookingStatusRequest(String status, String note) {}

  record CancelRequest(String code, String phone) {}

  record LookupRequest(String ticketNo, String phone) {}

  record ParentReplyRequest(String ticketNo, String phone, String body) {}

  record ReplyRequest(String body, boolean isPublic, String status) {}

  private final PtmService ptm;
  private final HelpdeskService helpdesk;
  private final SurveyService surveys;

  EngagementController(PtmService ptm, HelpdeskService helpdesk, SurveyService surveys) {
    this.ptm = ptm;
    this.helpdesk = helpdesk;
    this.surveys = surveys;
  }

  // ------------------------------------------------------------------ public (school website)

  @GetMapping(P + "/ptm")
  @Operation(operationId = "publicPtmEvents", summary = "Open PTMs with free slots")
  List<PtmService.PublicEvent> publicPtm(@RequestParam String host) {
    return ptm.publicEvents(host);
  }

  @PostMapping(P + "/ptm/{eventId}/book")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(operationId = "bookPtmSlot", summary = "Book a PTM slot")
  PtmService.Booked book(
      @RequestParam String host,
      @PathVariable UUID eventId,
      @RequestBody PtmService.BookRequest req,
      @RequestHeader(value = "X-Client-IP", required = false) String ip) {
    return ptm.book(host, eventId, req, ip);
  }

  @PostMapping(P + "/ptm/cancel")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "cancelPtmSlot", summary = "Cancel a PTM booking with its code")
  void cancel(
      @RequestParam String host,
      @RequestBody CancelRequest req,
      @RequestHeader(value = "X-Client-IP", required = false) String ip) {
    ptm.cancel(host, req.code(), req.phone(), ip);
  }

  @PostMapping(P + "/helpdesk")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(operationId = "raiseHelpdeskRequest", summary = "Raise a helpdesk request")
  HelpdeskService.PublicTicket raise(
      @RequestParam String host,
      @RequestBody JsonNode body,
      @RequestHeader(value = "X-Client-IP", required = false) String ip) {
    return helpdesk.raise(host, body, ip);
  }

  @PostMapping(P + "/helpdesk/lookup")
  @Operation(
      operationId = "lookupHelpdeskRequest",
      summary = "Status of a request (number + phone)")
  HelpdeskService.PublicTicket lookup(
      @RequestParam String host,
      @RequestBody LookupRequest req,
      @RequestHeader(value = "X-Client-IP", required = false) String ip) {
    return helpdesk.lookup(host, req.ticketNo(), req.phone(), ip);
  }

  @PostMapping(P + "/helpdesk/reply")
  @Operation(operationId = "replyHelpdeskRequest", summary = "Parent adds a message to a request")
  HelpdeskService.PublicTicket parentReply(
      @RequestParam String host,
      @RequestBody ParentReplyRequest req,
      @RequestHeader(value = "X-Client-IP", required = false) String ip) {
    return helpdesk.requesterReply(host, req.ticketNo(), req.phone(), req.body(), ip);
  }

  @GetMapping(P + "/surveys/{slug}")
  @Operation(operationId = "publicSurvey", summary = "An open survey's questions")
  SurveyService.PublicSurvey publicSurvey(@RequestParam String host, @PathVariable String slug) {
    return surveys.publicSurvey(host, slug);
  }

  @PostMapping(P + "/surveys/{slug}")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(operationId = "answerSurvey", summary = "Submit survey answers")
  void answer(
      @RequestParam String host,
      @PathVariable String slug,
      @RequestBody JsonNode body,
      @RequestHeader(value = "X-Client-IP", required = false) String ip) {
    surveys.submit(host, slug, body, ip);
  }

  // ------------------------------------------------------------------ studio: PTM

  @GetMapping(A + "/ptm")
  @PreAuthorize("hasAuthority('engagement:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "listPtmEvents", summary = "PTM days")
  List<PtmService.EventSummary> ptmEvents() {
    return ptm.events();
  }

  @PostMapping(A + "/ptm")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('engagement:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "createPtmEvent", summary = "Create a PTM day with teachers")
  PtmService.EventSummary createPtm(@RequestBody PtmService.EventInput in) {
    return ptm.create(in);
  }

  @GetMapping(A + "/ptm/{id}")
  @PreAuthorize("hasAuthority('engagement:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "ptmSchedule", summary = "Each teacher's slots and bookings")
  PtmService.Schedule schedule(@PathVariable UUID id) {
    return ptm.schedule(id);
  }

  @PatchMapping(A + "/ptm/{id}")
  @PreAuthorize("hasAuthority('engagement:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "updatePtmEvent", summary = "Edit a PTM day or add teachers")
  PtmService.EventSummary updatePtm(@PathVariable UUID id, @RequestBody PtmService.EventInput in) {
    return ptm.update(id, in);
  }

  @DeleteMapping(A + "/ptm/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('engagement:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "deletePtmEvent", summary = "Delete a PTM day and its bookings")
  void deletePtm(@PathVariable UUID id) {
    ptm.deleteEvent(id);
  }

  @DeleteMapping(A + "/ptm/hosts/{hostId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('engagement:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "removePtmHost", summary = "Remove a teacher with no bookings")
  void removeHost(@PathVariable UUID hostId) {
    ptm.removeHost(hostId);
  }

  @PostMapping(A + "/ptm/bookings/{id}/status")
  @PreAuthorize("hasAuthority('engagement:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "setPtmBookingStatus", summary = "Attended, no-show or cancelled")
  PtmService.Booking bookingStatus(@PathVariable UUID id, @RequestBody BookingStatusRequest req) {
    return ptm.setBookingStatus(id, req.status(), req.note());
  }

  // ------------------------------------------------------------------ studio: helpdesk

  @GetMapping(A + "/helpdesk/summary")
  @PreAuthorize("hasAuthority('engagement:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "helpdeskSummary", summary = "Open, overdue and due-today counts")
  HelpdeskService.Summary helpdeskSummary() {
    return helpdesk.summary();
  }

  @GetMapping(A + "/helpdesk")
  @PreAuthorize("hasAuthority('engagement:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "listTickets", summary = "Helpdesk requests")
  List<HelpdeskService.Ticket> tickets(
      @RequestParam(required = false) String view, @RequestParam(required = false) String q) {
    return helpdesk.list(view, q);
  }

  @PostMapping(A + "/helpdesk")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('engagement:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "createTicket", summary = "Log a phone, walk-in or email request")
  HelpdeskService.TicketDetail createTicket(@RequestBody HelpdeskService.TicketInput in) {
    return helpdesk.create(in);
  }

  @GetMapping(A + "/helpdesk/{id}")
  @PreAuthorize("hasAuthority('engagement:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "getTicket", summary = "Request with its conversation")
  HelpdeskService.TicketDetail ticket(@PathVariable UUID id) {
    return helpdesk.get(id);
  }

  @PatchMapping(A + "/helpdesk/{id}")
  @PreAuthorize("hasAuthority('engagement:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "updateTicket", summary = "Status, priority, category or assignee")
  HelpdeskService.TicketDetail updateTicket(
      @PathVariable UUID id, @RequestBody HelpdeskService.TicketUpdate u) {
    return helpdesk.update(id, u);
  }

  @PostMapping(A + "/helpdesk/{id}/reply")
  @PreAuthorize("hasAuthority('engagement:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "replyTicket", summary = "Reply to the parent or add an internal note")
  HelpdeskService.TicketDetail reply(@PathVariable UUID id, @RequestBody ReplyRequest req) {
    return helpdesk.reply(id, req.body(), req.isPublic(), req.status());
  }

  @DeleteMapping(A + "/helpdesk/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('engagement:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "deleteTicket", summary = "Delete a request")
  void deleteTicket(@PathVariable UUID id) {
    helpdesk.delete(id);
  }

  @GetMapping(A + "/staff")
  @PreAuthorize("hasAuthority('engagement:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "engagementStaff", summary = "Staff who can be assigned requests")
  List<HelpdeskService.Staff> staff() {
    return helpdesk.staff();
  }

  // ------------------------------------------------------------------ studio: surveys

  @GetMapping(A + "/surveys")
  @PreAuthorize("hasAuthority('engagement:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "listSurveys", summary = "Surveys with response counts")
  List<SurveyService.Survey> surveys() {
    return surveys.list();
  }

  @PostMapping(A + "/surveys")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('engagement:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "createSurvey", summary = "Create a survey")
  SurveyService.Survey createSurvey(@RequestBody SurveyService.SurveyInput in) {
    return surveys.create(in);
  }

  @PatchMapping(A + "/surveys/{id}")
  @PreAuthorize("hasAuthority('engagement:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "updateSurvey", summary = "Edit, open or close a survey")
  SurveyService.Survey updateSurvey(
      @PathVariable UUID id, @RequestBody SurveyService.SurveyInput in) {
    return surveys.update(id, in);
  }

  @DeleteMapping(A + "/surveys/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('engagement:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "deleteSurvey", summary = "Delete a survey and its responses")
  void deleteSurvey(@PathVariable UUID id) {
    surveys.delete(id);
  }

  @GetMapping(A + "/surveys/{id}/results")
  @PreAuthorize("hasAuthority('engagement:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "surveyResults", summary = "Results per question")
  SurveyService.Results results(@PathVariable UUID id) {
    return surveys.results(id);
  }

  @GetMapping(value = A + "/surveys/{id}/responses.csv", produces = "text/csv")
  @PreAuthorize("hasAuthority('engagement:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "surveyCsv", summary = "Responses as CSV")
  ResponseEntity<String> csv(@PathVariable UUID id) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"survey-responses.csv\"")
        .contentType(new MediaType("text", "csv"))
        .body(surveys.csv(id));
  }
}
