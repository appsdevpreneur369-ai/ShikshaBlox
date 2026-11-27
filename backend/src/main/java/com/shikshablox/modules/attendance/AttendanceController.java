package com.shikshablox.modules.attendance;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Studio API for attendance and leave (M07). Teachers (attendance:mark) work with their assigned
 * sections; the office (attendance:manage) with all of them, plus settings and corrections.
 */
@RestController
@RequestMapping("/api/admin/v1/attendance")
@PreAuthorize("hasAnyAuthority('attendance:mark', 'attendance:manage')")
@SecurityRequirement(name = "bearer")
@Tag(name = "Attendance")
class AttendanceController {

  record TeacherRequest(String name, String email) {}

  record AssignRequest(List<UUID> userIds) {}

  record DecisionRequest(String note) {}

  private final AttendanceService attendance;
  private final LeaveService leave;

  AttendanceController(AttendanceService attendance, LeaveService leave) {
    this.attendance = attendance;
    this.leave = leave;
  }

  @GetMapping("/overview")
  @Operation(operationId = "attendanceOverview", summary = "Today's roll calls of my classes")
  AttendanceService.Overview overview(@RequestParam(required = false) LocalDate date) {
    return attendance.overview(date);
  }

  @GetMapping("/settings")
  @Operation(operationId = "attendanceSettings", summary = "Attendance settings")
  AttendanceService.Settings settings() {
    return attendance.settings();
  }

  @PutMapping("/settings")
  @Operation(operationId = "saveAttendanceSettings", summary = "Change attendance settings")
  AttendanceService.Settings saveSettings(@RequestBody AttendanceService.SettingsInput in) {
    return attendance.saveSettings(in);
  }

  // ------------------------------------------------------------------ roll call

  @GetMapping("/sheet")
  @Operation(
      operationId = "attendanceSheet",
      summary = "A class's roll call for a date (and period)")
  AttendanceService.Sheet sheet(
      @RequestParam UUID sectionId,
      @RequestParam(required = false) LocalDate date,
      @RequestParam(required = false) Integer period) {
    return attendance.sheet(sectionId, date, period);
  }

  @PutMapping("/sheet")
  @Operation(
      operationId = "saveAttendanceSheet",
      summary = "Save a roll call (past days by teachers become correction requests)")
  AttendanceService.SaveResult save(@RequestBody AttendanceService.SheetInput in) {
    return attendance.save(in);
  }

  // ------------------------------------------------------------------ teachers

  @GetMapping("/teachers")
  @Operation(operationId = "attendanceTeachers", summary = "Teachers and their classes")
  AttendanceService.Teachers teachers() {
    return attendance.teachers();
  }

  @PostMapping("/teachers")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(operationId = "addTeacher", summary = "Add a teacher account")
  AttendanceService.NewTeacher addTeacher(@RequestBody TeacherRequest req) {
    return attendance.addTeacher(req.name(), req.email());
  }

  @PutMapping("/sections/{sectionId}/teachers")
  @Operation(operationId = "assignTeachers", summary = "Who takes attendance for a class")
  AttendanceService.Teachers assign(@PathVariable UUID sectionId, @RequestBody AssignRequest req) {
    return attendance.assign(sectionId, req.userIds());
  }

  // ------------------------------------------------------------------ registers and concerns

  @GetMapping("/register")
  @Operation(operationId = "attendanceRegister", summary = "Monthly register of a class")
  AttendanceService.Register register(
      @RequestParam UUID sectionId, @RequestParam(required = false) String month) {
    return attendance.register(sectionId, month);
  }

  @GetMapping(value = "/register.csv", produces = "text/csv")
  @Operation(operationId = "attendanceRegisterCsv", summary = "Monthly register as CSV")
  ResponseEntity<byte[]> registerCsv(
      @RequestParam UUID sectionId, @RequestParam(required = false) String month) {
    String csv = attendance.registerCsv(sectionId, month);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"attendance-register-"
                + (month == null ? "month" : month.replaceAll("[^0-9-]", ""))
                + ".csv\"")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(("﻿" + csv).getBytes(StandardCharsets.UTF_8));
  }

  @GetMapping("/concerns")
  @Operation(operationId = "attendanceConcerns", summary = "Students with chronic absence")
  List<AttendanceService.Concern> concerns() {
    return attendance.concerns();
  }

  // ------------------------------------------------------------------ leave

  @GetMapping("/leave")
  @Operation(operationId = "listLeave", summary = "Leave requests of my classes")
  List<LeaveService.Leave> leave(@RequestParam(required = false) String status) {
    return leave.list(status);
  }

  @PostMapping("/leave")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(operationId = "recordLeave", summary = "Record leave for a student (approved)")
  LeaveService.Leave recordLeave(@RequestBody LeaveService.LeaveInput in) {
    return leave.create(in);
  }

  @PostMapping("/leave/{id}/approve")
  @Operation(operationId = "approveLeave", summary = "Approve a leave request")
  LeaveService.Leave approve(
      @PathVariable UUID id, @RequestBody(required = false) DecisionRequest req) {
    return leave.decide(id, true, req == null ? null : req.note());
  }

  @PostMapping("/leave/{id}/reject")
  @Operation(operationId = "rejectLeave", summary = "Decline a leave request")
  LeaveService.Leave reject(
      @PathVariable UUID id, @RequestBody(required = false) DecisionRequest req) {
    return leave.decide(id, false, req == null ? null : req.note());
  }

  // ------------------------------------------------------------------ corrections

  @GetMapping("/corrections")
  @Operation(operationId = "listCorrections", summary = "Corrections of past days")
  List<LeaveService.Correction> corrections(@RequestParam(required = false) String status) {
    return leave.corrections(status);
  }

  @PostMapping("/corrections/{id}/approve")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "approveCorrection", summary = "Approve a correction")
  void approveCorrection(@PathVariable UUID id) {
    leave.decideCorrection(id, true);
  }

  @PostMapping("/corrections/{id}/reject")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "rejectCorrection", summary = "Reject a correction")
  void rejectCorrection(@PathVariable UUID id) {
    leave.decideCorrection(id, false);
  }
}
