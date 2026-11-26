package com.shikshablox.modules.sis;

import com.shikshablox.platform.shared.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Studio API for student information (M04). */
@RestController
@RequestMapping("/api/admin/v1/sis")
@PreAuthorize("hasAuthority('students:manage')")
@SecurityRequirement(name = "bearer")
@Tag(name = "Student information")
class SisController {

  record SettingsRequest(String admissionPrefix, Integer nextAdmissionNo) {}

  record SectionsRequest(String academicYear, List<SisService.GradeInput> grades) {}

  record TeacherRequest(String classTeacher) {}

  record MoveRequest(UUID sectionId, Integer rollNo) {}

  record WithdrawRequest(LocalDate date, String reason, boolean alumni) {}

  record ReinstateRequest(UUID sectionId) {}

  record ImportRequest(String csv) {}

  private final SisService sis;
  private final SisOperations ops;

  SisController(SisService sis, SisOperations ops) {
    this.sis = sis;
    this.ops = ops;
  }

  @GetMapping("/settings")
  @Operation(operationId = "sisSettings", summary = "Current year and admission numbering")
  SisService.Settings settings() {
    return sis.settings();
  }

  @PutMapping("/settings")
  @Operation(operationId = "saveSisSettings", summary = "Admission number prefix and next number")
  SisService.Settings saveSettings(@RequestBody SettingsRequest req) {
    return sis.saveSettings(req.admissionPrefix(), req.nextAdmissionNo());
  }

  // ------------------------------------------------------------------ classes

  @GetMapping("/sections")
  @Operation(
      operationId = "listSections",
      summary = "Classes and sections of a year, with student counts")
  List<SisService.Section> sections(@RequestParam(required = false) String year) {
    return sis.sections(year);
  }

  @PostMapping("/sections")
  @Operation(operationId = "addSections", summary = "Add classes and sections to a year")
  List<SisService.Section> addSections(@RequestBody SectionsRequest req) {
    return sis.addSections(req.academicYear(), req.grades());
  }

  @PatchMapping("/sections/{id}")
  @Operation(operationId = "updateSection", summary = "Set the class teacher")
  SisService.Section updateSection(@PathVariable UUID id, @RequestBody TeacherRequest req) {
    return sis.updateSection(id, req.classTeacher());
  }

  @DeleteMapping("/sections/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "deleteSection", summary = "Delete an empty section")
  void deleteSection(@PathVariable UUID id) {
    sis.deleteSection(id);
  }

  // ------------------------------------------------------------------ students

  @GetMapping("/students")
  @Operation(
      operationId = "listStudents",
      summary = "Students of a year, by section, status or search")
  List<SisService.StudentRow> students(
      @RequestParam(required = false) String year,
      @RequestParam(required = false) UUID sectionId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String q) {
    return sis.students(year, sectionId, status, q);
  }

  @PostMapping("/students")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(operationId = "createStudent", summary = "Add a student with guardians")
  SisService.StudentDetail create(@RequestBody SisService.StudentInput in) {
    return sis.create(in);
  }

  @GetMapping("/students/{id}")
  @Operation(
      operationId = "getStudent",
      summary = "Student 360: profile, enrolments, guardians, documents")
  SisService.StudentDetail student(@PathVariable UUID id) {
    return sis.student(id);
  }

  @PutMapping("/students/{id}")
  @Operation(operationId = "updateStudent", summary = "Edit the student's profile")
  SisService.StudentDetail update(@PathVariable UUID id, @RequestBody SisService.StudentInput in) {
    return sis.update(id, in);
  }

  @DeleteMapping("/students/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "deleteStudent", summary = "Delete a student added by mistake")
  void delete(@PathVariable UUID id) {
    sis.delete(id);
  }

  @PostMapping("/students/{id}/section")
  @Operation(
      operationId = "moveStudent",
      summary = "Move to another section or set the roll number")
  SisService.StudentDetail move(@PathVariable UUID id, @RequestBody MoveRequest req) {
    return sis.moveSection(id, req.sectionId(), req.rollNo());
  }

  @PostMapping("/students/{id}/withdraw")
  @Operation(operationId = "withdrawStudent", summary = "Withdraw (or mark as alumni)")
  SisService.StudentDetail withdraw(@PathVariable UUID id, @RequestBody WithdrawRequest req) {
    return sis.withdraw(id, req.date(), req.reason(), req.alumni());
  }

  @PostMapping("/students/{id}/reinstate")
  @Operation(
      operationId = "reinstateStudent",
      summary = "Re-admit into a section of the current year")
  SisService.StudentDetail reinstate(@PathVariable UUID id, @RequestBody ReinstateRequest req) {
    return sis.reinstate(id, req.sectionId());
  }

  @PostMapping("/students/{id}/guardians")
  @Operation(operationId = "addGuardian", summary = "Link a new or existing guardian")
  SisService.StudentDetail addGuardian(
      @PathVariable UUID id, @RequestBody SisService.GuardianInput in) {
    return sis.addGuardian(id, in);
  }

  @PatchMapping("/students/{id}/guardians/{guardianId}")
  @Operation(operationId = "updateGuardian", summary = "Edit a guardian and their link")
  SisService.StudentDetail updateGuardian(
      @PathVariable UUID id,
      @PathVariable UUID guardianId,
      @RequestBody SisService.GuardianInput in) {
    return sis.updateGuardian(id, guardianId, in);
  }

  @DeleteMapping("/students/{id}/guardians/{guardianId}")
  @Operation(operationId = "removeGuardian", summary = "Unlink a guardian")
  SisService.StudentDetail removeGuardian(@PathVariable UUID id, @PathVariable UUID guardianId) {
    return sis.removeGuardian(id, guardianId);
  }

  @PostMapping(value = "/students/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(operationId = "uploadStudentDocument", summary = "Upload a document (PDF, JPG, PNG)")
  SisService.StudentDetail upload(
      @PathVariable UUID id, @RequestParam String title, @RequestPart("file") MultipartFile file) {
    if (file.getSize() > 10L * 1024 * 1024) {
      throw ApiException.badRequest("file_too_large", "Upload files up to 10 MB.");
    }
    try {
      return sis.uploadDocument(id, title, file.getOriginalFilename(), file.getBytes());
    } catch (IOException e) {
      throw ApiException.badRequest("upload_failed", "The upload could not be read.");
    }
  }

  @GetMapping("/documents/{id}/download")
  @Operation(operationId = "studentDocumentUrl", summary = "A 10-minute download link")
  Map<String, String> download(@PathVariable UUID id) {
    return Map.of("url", sis.documentUrl(id));
  }

  @DeleteMapping("/documents/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "deleteStudentDocument", summary = "Delete a document")
  void deleteDocument(@PathVariable UUID id) {
    sis.deleteDocument(id);
  }

  @PostMapping("/students/from-lead/{leadId}")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(operationId = "enrolFromLead", summary = "Enrol an admitted applicant as a student")
  SisService.StudentDetail enrolFromLead(
      @PathVariable UUID leadId, @RequestBody SisOperations.EnrolFromLead req) {
    return ops.enrolFromLead(leadId, req);
  }

  @PostMapping("/students/import")
  @Operation(
      operationId = "importStudents",
      summary = "Import students and guardians from CSV text")
  SisService.ImportResult importCsv(@RequestBody ImportRequest req) {
    return ops.importCsv(req.csv());
  }

  @GetMapping(value = "/students.csv", produces = "text/csv")
  @Operation(operationId = "exportStudents", summary = "Students as CSV")
  ResponseEntity<String> csv(
      @RequestParam(required = false) String year,
      @RequestParam(required = false) UUID sectionId,
      @RequestParam(required = false) String status) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"students.csv\"")
        .contentType(new MediaType("text", "csv"))
        .body(sis.csv(year, sectionId, status));
  }

  // ------------------------------------------------------------------ fields, readiness, promotion

  @GetMapping("/fields")
  @Operation(operationId = "listStudentFields", summary = "Custom student fields")
  List<SisService.Field> fields() {
    return sis.fields();
  }

  @PostMapping("/fields")
  @Operation(operationId = "addStudentField", summary = "Add a custom field")
  List<SisService.Field> addField(@RequestBody SisService.FieldInput in) {
    return sis.addField(in);
  }

  @DeleteMapping("/fields/{id}")
  @Operation(operationId = "deleteStudentField", summary = "Delete a custom field")
  List<SisService.Field> deleteField(@PathVariable UUID id) {
    return sis.deleteField(id);
  }

  @GetMapping("/readiness")
  @Operation(
      operationId = "udiseReadiness",
      summary = "UDISE+ readiness of enrolled students' records")
  SisService.Readiness readiness() {
    return sis.readiness();
  }

  @GetMapping("/promotion")
  @Operation(operationId = "promotionPlan", summary = "Sections, students and default next classes")
  SisOperations.PromotionPlan plan() {
    return ops.plan();
  }

  @PostMapping("/promotion")
  @Operation(operationId = "promote", summary = "Move the school into the next academic year")
  SisOperations.PromotionResult promote(@RequestBody SisOperations.PromotionRequest req) {
    return ops.promote(req);
  }
}
