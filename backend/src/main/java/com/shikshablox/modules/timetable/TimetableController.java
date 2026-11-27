package com.shikshablox.modules.timetable;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Studio API for timetables and substitutions (M08). Teachers may read their own week. */
@RestController
@RequestMapping("/api/admin/v1/timetable")
@PreAuthorize(
    "hasAnyAuthority('timetable:manage', 'attendance:mark', 'marks:enter', 'homework:post')")
@SecurityRequirement(name = "bearer")
@Tag(name = "Timetable")
class TimetableController {

  record GenerateRequest(List<UUID> sectionIds) {}

  record AwayRequest(LocalDate date, UUID userId, String note, boolean away) {}

  record AssignRequest(LocalDate date, UUID sectionId, int period, UUID substituteId) {}

  private final TimetableService timetable;

  TimetableController(TimetableService timetable) {
    this.timetable = timetable;
  }

  @GetMapping("/overview")
  @Operation(operationId = "timetableOverview", summary = "Bell schedule, classes and teachers")
  TimetableService.Overview overview() {
    return timetable.overview();
  }

  @PutMapping("/settings")
  @Operation(operationId = "saveTimetableSettings", summary = "Working days and bell schedule")
  TimetableService.Settings saveSettings(@RequestBody TimetableService.SettingsInput in) {
    return timetable.saveSettings(in);
  }

  @GetMapping("/sections/{sectionId}")
  @Operation(operationId = "classTimetable", summary = "A class's week, with subject loads")
  TimetableService.Grid grid(@PathVariable UUID sectionId) {
    return timetable.grid(sectionId);
  }

  @PutMapping("/sections/{sectionId}/slots")
  @Operation(
      operationId = "setTimetableSlot",
      summary = "Set a period (refused if teacher or room is busy)")
  TimetableService.Slot setSlot(
      @PathVariable UUID sectionId, @RequestBody TimetableService.SlotInput in) {
    return timetable.setSlot(sectionId, in);
  }

  @DeleteMapping("/sections/{sectionId}/slots")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "clearTimetableSlot", summary = "Clear a period")
  void clearSlot(@PathVariable UUID sectionId, @RequestParam int day, @RequestParam int period) {
    timetable.clearSlot(sectionId, day, period);
  }

  @PutMapping("/sections/{sectionId}/loads")
  @Operation(operationId = "saveTimetableLoads", summary = "Periods a week and teacher per subject")
  TimetableService.Grid saveLoads(
      @PathVariable UUID sectionId, @RequestBody List<TimetableService.LoadInput> in) {
    return timetable.saveLoads(sectionId, in);
  }

  @PostMapping("/generate")
  @Operation(operationId = "generateTimetable", summary = "Fill empty periods from subject loads")
  TimetableService.GenerateResult generate(@RequestBody GenerateRequest req) {
    return timetable.generate(req.sectionIds());
  }

  @GetMapping("/teacher")
  @Operation(
      operationId = "teacherTimetable",
      summary = "A teacher's week (own, or any for the office)")
  List<TimetableService.Slot> teacher(@RequestParam(required = false) UUID teacherId) {
    return timetable.teacher(teacherId);
  }

  @GetMapping("/my-substitutions")
  @Operation(operationId = "mySubstitutions", summary = "My substitute periods on a date")
  List<TimetableService.Slot> mySubstitutions(@RequestParam(required = false) LocalDate date) {
    return timetable.mySubstitutions(date);
  }

  @GetMapping("/substitutions")
  @Operation(
      operationId = "substitutions",
      summary = "Teachers away and periods needing a substitute")
  TimetableService.SubDay substitutions(@RequestParam(required = false) LocalDate date) {
    return timetable.substitutions(date);
  }

  @PostMapping("/substitutions/away")
  @Operation(operationId = "markTeacherAway", summary = "Mark a teacher away (or back) on a date")
  TimetableService.SubDay away(@RequestBody AwayRequest req) {
    return timetable.markAway(req.date(), req.userId(), req.note(), req.away());
  }

  @PostMapping("/substitutions/assign")
  @Operation(operationId = "assignSubstitute", summary = "Give a period to a free teacher")
  TimetableService.SubDay assign(@RequestBody AssignRequest req) {
    return timetable.assign(req.date(), req.sectionId(), req.period(), req.substituteId());
  }
}
