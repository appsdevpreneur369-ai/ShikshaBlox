package com.shikshablox.modules.earlyyears;

import com.shikshablox.platform.shared.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Studio API for early years (M31). */
@RestController
@RequestMapping("/api/admin/v1/early-years")
@PreAuthorize("hasAnyAuthority('homework:post', 'school:manage')")
@SecurityRequirement(name = "bearer")
@Tag(name = "Early years")
class EarlyYearsController {

  record StatusRequest(UUID studentId, UUID milestoneId, String status) {}

  private final EarlyYearsService ey;

  EarlyYearsController(EarlyYearsService ey) {
    this.ey = ey;
  }

  @GetMapping("/overview")
  @Operation(operationId = "earlyYearsOverview", summary = "My pre-primary classes and milestones")
  EarlyYearsService.Overview overview() {
    return ey.overview();
  }

  @GetMapping("/diary")
  @Operation(operationId = "eyDiary", summary = "A class's diary for a day")
  List<EarlyYearsService.DiaryRow> diary(
      @RequestParam UUID sectionId, @RequestParam(required = false) LocalDate date) {
    return ey.diary(sectionId, date);
  }

  @PutMapping("/diary")
  @Operation(operationId = "saveEyDiary", summary = "Save a class's diary for a day")
  List<EarlyYearsService.DiaryRow> saveDiary(
      @RequestParam UUID sectionId,
      @RequestParam(required = false) LocalDate date,
      @RequestBody List<EarlyYearsService.DiaryInput> rows) {
    return ey.saveDiary(sectionId, date, rows);
  }

  @GetMapping("/milestones")
  @Operation(operationId = "eyMilestones", summary = "Milestones of a class")
  EarlyYearsService.MilestoneBoard board(@RequestParam UUID sectionId) {
    return ey.board(sectionId);
  }

  @PutMapping("/milestones")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "setEyMilestone", summary = "Set a child's milestone status")
  void setMilestone(@RequestBody StatusRequest req) {
    ey.setMilestone(req.studentId(), req.milestoneId(), req.status());
  }

  @GetMapping("/observations")
  @Operation(operationId = "eyObservations", summary = "Observations of a class")
  List<EarlyYearsService.Observation> observations(@RequestParam UUID sectionId) {
    return ey.observations(sectionId);
  }

  @PostMapping(value = "/observations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(operationId = "addEyObservation", summary = "Add an observation (optional photo)")
  EarlyYearsService.Observation addObservation(
      @RequestParam UUID studentId,
      @RequestParam(required = false) LocalDate date,
      @RequestParam(required = false) String domain,
      @RequestParam String note,
      @RequestParam(defaultValue = "true") boolean share,
      @RequestPart(value = "photo", required = false) MultipartFile photo) {
    return ey.addObservation(
        studentId,
        date,
        domain,
        note,
        share,
        photo == null ? null : photo.getOriginalFilename(),
        bytes(photo));
  }

  @DeleteMapping("/observations/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "deleteEyObservation", summary = "Delete an observation")
  void deleteObservation(@PathVariable UUID id) {
    ey.deleteObservation(id);
  }

  @GetMapping("/story")
  @Operation(operationId = "eyStory", summary = "Learning story of a child")
  EarlyYearsService.Story story(
      @RequestParam UUID studentId,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to) {
    return ey.story(studentId, from, to);
  }

  @GetMapping("/pickups")
  @Operation(operationId = "eyPickups", summary = "Adults allowed to collect a child")
  List<EarlyYearsService.Pickup> pickups(@RequestParam UUID studentId) {
    return ey.pickups(studentId);
  }

  @PostMapping(value = "/pickups", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(operationId = "addEyPickup", summary = "Add an adult allowed to collect the child")
  List<EarlyYearsService.Pickup> addPickup(
      @RequestParam UUID studentId,
      @RequestParam String name,
      @RequestParam(required = false) String relation,
      @RequestParam(required = false) String phone,
      @RequestPart(value = "photo", required = false) MultipartFile photo) {
    return ey.addPickup(
        studentId,
        name,
        relation,
        phone,
        photo == null ? null : photo.getOriginalFilename(),
        bytes(photo));
  }

  @DeleteMapping("/pickups/{id}")
  @Operation(operationId = "removeEyPickup", summary = "Remove a pickup adult")
  List<EarlyYearsService.Pickup> removePickup(@PathVariable UUID id) {
    return ey.removePickup(id);
  }

  static byte[] bytes(MultipartFile f) {
    if (f == null || f.isEmpty()) {
      return null;
    }
    try {
      return f.getBytes();
    } catch (IOException e) {
      throw ApiException.badRequest("upload_failed", "The photo could not be read.");
    }
  }
}
