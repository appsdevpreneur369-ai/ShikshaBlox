package com.shikshablox.modules.frontoffice;

import com.shikshablox.platform.shared.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Studio API for the front office (M43). */
@RestController
@RequestMapping("/api/admin/v1/front-office")
@PreAuthorize("hasAuthority('frontoffice:manage')")
@SecurityRequirement(name = "bearer")
@Tag(name = "Front office")
class FrontOfficeController {

  record HandOffRequest(String studentName, String gradeSought) {}

  record StatusRequest(String status) {}

  record ClosePostRequest(String person, String trackingNo) {}

  record CloseLostRequest(String status, String returnedTo) {}

  private final FrontOfficeService office;

  FrontOfficeController(FrontOfficeService office) {
    this.office = office;
  }

  @GetMapping("/summary")
  @Operation(operationId = "frontOfficeSummary", summary = "Today at the front desk")
  FrontOfficeService.Today summary() {
    return office.summary();
  }

  @GetMapping("/staff")
  @Operation(operationId = "frontOfficeStaff", summary = "Staff who can be booked")
  List<FrontOfficeService.Person> staff() {
    return office.staff();
  }

  @GetMapping("/entries")
  @Operation(operationId = "listFrontDeskEntries", summary = "Walk-ins and calls for a day")
  List<FrontOfficeService.Entry> entries(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate day) {
    return office.entries(day);
  }

  @PostMapping("/entries")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(operationId = "addFrontDeskEntry", summary = "Log a walk-in or call")
  FrontOfficeService.Entry add(@RequestBody FrontOfficeService.EntryInput in) {
    return office.addEntry(in);
  }

  @PostMapping("/entries/{id}/out")
  @Operation(operationId = "checkOutVisitor", summary = "Visitor has left")
  FrontOfficeService.Entry out(@PathVariable UUID id) {
    return office.checkOut(id);
  }

  @PostMapping("/entries/{id}/admissions")
  @Operation(operationId = "handOffToAdmissions", summary = "Create an admissions lead")
  FrontOfficeService.Entry handOff(@PathVariable UUID id, @RequestBody HandOffRequest req) {
    return office.handOffToAdmissions(id, req.studentName(), req.gradeSought());
  }

  @DeleteMapping("/entries/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "deleteFrontDeskEntry", summary = "Delete a register entry")
  void deleteEntry(@PathVariable UUID id) {
    office.deleteEntry(id);
  }

  @GetMapping("/appointments")
  @Operation(operationId = "listAppointments", summary = "Appointments in a date range")
  List<FrontOfficeService.Appointment> appointments(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return office.appointments(from, to);
  }

  @PostMapping("/appointments")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(operationId = "bookAppointment", summary = "Book an appointment")
  FrontOfficeService.Appointment book(@RequestBody FrontOfficeService.AppointmentInput in) {
    return office.book(in);
  }

  @PostMapping("/appointments/{id}/status")
  @Operation(operationId = "setAppointmentStatus", summary = "Arrived, done, cancelled, no-show")
  FrontOfficeService.Appointment status(@PathVariable UUID id, @RequestBody StatusRequest req) {
    return office.setAppointmentStatus(id, req.status());
  }

  @GetMapping("/post")
  @Operation(operationId = "listPost", summary = "Inward and outward post")
  List<FrontOfficeService.Post> post(
      @RequestParam(required = false) String direction,
      @RequestParam(defaultValue = "false") boolean open) {
    return office.post(direction, open);
  }

  @PostMapping("/post")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(operationId = "logPost", summary = "Log inward or outward post")
  FrontOfficeService.Post logPost(@RequestBody FrontOfficeService.PostInput in) {
    return office.logPost(in);
  }

  @PostMapping("/post/{id}/close")
  @Operation(operationId = "closePost", summary = "Handed over (inward) or dispatched (outward)")
  FrontOfficeService.Post closePost(@PathVariable UUID id, @RequestBody ClosePostRequest req) {
    return office.closePost(id, req.person(), req.trackingNo());
  }

  @GetMapping("/lost-found")
  @Operation(operationId = "listLostItems", summary = "Lost & found register")
  List<FrontOfficeService.LostItem> lost(@RequestParam(defaultValue = "false") boolean open) {
    return office.lostItems(open);
  }

  @PostMapping(value = "/lost-found", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(operationId = "addLostItem", summary = "Log a found item, with an optional photo")
  FrontOfficeService.LostItem addLost(
      @RequestParam String description,
      @RequestParam(required = false) String foundAt,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate foundOn,
      @RequestPart(value = "photo", required = false) MultipartFile photo) {
    byte[] bytes = null;
    String name = null;
    if (photo != null && !photo.isEmpty()) {
      if (photo.getSize() > FrontOfficeService.MAX_PHOTO_BYTES) {
        throw ApiException.badRequest("file_too_large", "Photos up to 8 MB.");
      }
      try {
        bytes = photo.getBytes();
      } catch (IOException e) {
        throw ApiException.badRequest("upload_failed", "The photo could not be read.");
      }
      name = photo.getOriginalFilename();
    }
    return office.addLostItem(description, foundAt, foundOn, name, bytes);
  }

  @PostMapping("/lost-found/{id}/status")
  @Operation(operationId = "closeLostItem", summary = "Returned, disposed or back to found")
  FrontOfficeService.LostItem closeLost(@PathVariable UUID id, @RequestBody CloseLostRequest req) {
    return office.closeLostItem(id, req.status(), req.returnedTo());
  }

  @DeleteMapping("/{what:appointments|post|lost-found}/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "deleteFrontOfficeRecord", summary = "Delete a mistaken record")
  void delete(@PathVariable String what, @PathVariable UUID id) {
    office.delete(
        switch (what) {
          case "appointments" -> "appointment";
          case "post" -> "post";
          default -> "lost";
        },
        id);
  }

  @GetMapping("/lost-found/{id}/photo")
  @Operation(operationId = "lostItemPhoto", summary = "A 10-minute link to the photo")
  Map<String, String> photo(@PathVariable UUID id) {
    return Map.of("url", office.lostItemPhotoUrl(id));
  }
}
