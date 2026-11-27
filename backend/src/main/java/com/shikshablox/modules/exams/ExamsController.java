package com.shikshablox.modules.exams;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

/** Studio API for exams, marks and report cards (M11). */
@RestController
@RequestMapping("/api/admin/v1/exams")
@PreAuthorize("hasAnyAuthority('marks:enter', 'exams:manage')")
@SecurityRequirement(name = "bearer")
@Tag(name = "Exams")
class ExamsController {

  record PresetRequest(List<String> grades) {}

  record AssignRequest(List<UUID> userIds) {}

  record StatusRequest(String status) {}

  record ModerateRequest(boolean approve, String note) {}

  private final ExamService exams;
  private final MarksService marks;
  private final ReportService reports;

  ExamsController(ExamService exams, MarksService marks, ReportService reports) {
    this.exams = exams;
    this.marks = marks;
    this.reports = reports;
  }

  @GetMapping("/overview")
  @Operation(operationId = "examsOverview", summary = "Settings, classes and exams of the year")
  ExamService.Overview overview() {
    return exams.overview();
  }

  @PutMapping("/settings")
  @Operation(operationId = "saveExamSettings", summary = "Board, pass mark, grade scale")
  ExamService.Settings saveSettings(@RequestBody ExamService.SettingsInput in) {
    return exams.saveSettings(in);
  }

  // ------------------------------------------------------------------ subjects & teachers

  @GetMapping("/subjects")
  @Operation(operationId = "listSubjects", summary = "Subjects of the year (optionally one class)")
  List<ExamService.Subject> subjects(@RequestParam(required = false) String grade) {
    return exams.subjects(grade);
  }

  @PostMapping("/subjects")
  @Operation(operationId = "addSubject", summary = "Add a subject to a class")
  List<ExamService.Subject> addSubject(@RequestBody ExamService.SubjectInput in) {
    return exams.addSubject(in);
  }

  @PostMapping("/subjects/preset")
  @Operation(operationId = "applySubjectPreset", summary = "Add the board's usual subjects")
  List<ExamService.Subject> preset(@RequestBody PresetRequest req) {
    return exams.applyPreset(req.grades());
  }

  @DeleteMapping("/subjects/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "deleteSubject", summary = "Remove a subject without marks")
  void deleteSubject(@PathVariable UUID id) {
    exams.deleteSubject(id);
  }

  @GetMapping("/teachers")
  @Operation(operationId = "subjectTeachers", summary = "Subject teachers per class")
  ExamService.Teachers teachers() {
    return exams.teachers();
  }

  @PutMapping("/sections/{sectionId}/subjects/{subjectId}/teachers")
  @Operation(operationId = "assignSubjectTeachers", summary = "Who enters marks for a subject")
  ExamService.Teachers assign(
      @PathVariable UUID sectionId, @PathVariable UUID subjectId, @RequestBody AssignRequest req) {
    return exams.assign(sectionId, subjectId, req.userIds());
  }

  // ------------------------------------------------------------------ exams

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(operationId = "createExam", summary = "Create an exam")
  ExamService.Exam create(@RequestBody ExamService.ExamInput in) {
    return exams.save(null, in);
  }

  @PutMapping("/{id}")
  @Operation(operationId = "updateExam", summary = "Change an exam")
  ExamService.Exam update(@PathVariable UUID id, @RequestBody ExamService.ExamInput in) {
    return exams.save(id, in);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "deleteExam", summary = "Delete an exam without marks")
  void delete(@PathVariable UUID id) {
    exams.delete(id);
  }

  @PostMapping("/{id}/status")
  @Operation(operationId = "setExamStatus", summary = "Open for marks, publish or unpublish")
  ExamService.Exam status(@PathVariable UUID id, @RequestBody StatusRequest req) {
    return exams.setStatus(id, req.status());
  }

  @GetMapping("/{id}/papers")
  @Operation(operationId = "examPapers", summary = "The exam timetable")
  List<ExamService.Paper> papers(@PathVariable UUID id) {
    return exams.papers(id);
  }

  @PutMapping("/{id}/papers")
  @Operation(operationId = "saveExamPapers", summary = "Save the exam timetable")
  List<ExamService.Paper> savePapers(
      @PathVariable UUID id, @RequestBody List<ExamService.PaperInput> in) {
    return exams.savePapers(id, in);
  }

  // ------------------------------------------------------------------ marks

  @GetMapping("/{id}/progress")
  @Operation(operationId = "marksProgress", summary = "Marks progress per class and subject")
  List<MarksService.Task> progress(@PathVariable UUID id) {
    return marks.progress(id);
  }

  @GetMapping("/{id}/marks")
  @Operation(operationId = "marksSheet", summary = "Marks of a subject in a class")
  MarksService.Sheet sheet(
      @PathVariable UUID id, @RequestParam UUID sectionId, @RequestParam UUID subjectId) {
    return marks.sheet(id, sectionId, subjectId);
  }

  @PutMapping("/{id}/marks")
  @Operation(operationId = "saveMarks", summary = "Save marks of a subject in a class")
  MarksService.Sheet save(
      @PathVariable UUID id,
      @RequestParam UUID sectionId,
      @RequestParam UUID subjectId,
      @RequestBody List<MarksService.RowInput> rows) {
    return marks.save(id, sectionId, subjectId, rows);
  }

  @PostMapping("/{id}/marks/submit")
  @Operation(operationId = "submitMarks", summary = "Submit a class's marks for approval")
  MarksService.Sheet submit(
      @PathVariable UUID id, @RequestParam UUID sectionId, @RequestParam UUID subjectId) {
    return marks.submit(id, sectionId, subjectId);
  }

  @PostMapping("/{id}/marks/moderate")
  @Operation(operationId = "moderateMarks", summary = "Approve or send back a class's marks")
  MarksService.Sheet moderate(
      @PathVariable UUID id,
      @RequestParam UUID sectionId,
      @RequestParam UUID subjectId,
      @RequestBody ModerateRequest req) {
    return marks.moderate(id, sectionId, subjectId, req.approve(), req.note());
  }

  @GetMapping("/{id}/remarks")
  @Operation(operationId = "examRemarks", summary = "Class teacher's remarks")
  List<MarksService.Remark> remarks(@PathVariable UUID id, @RequestParam UUID sectionId) {
    return marks.remarks(id, sectionId);
  }

  @PutMapping("/{id}/remarks")
  @Operation(operationId = "saveExamRemarks", summary = "Save remarks")
  List<MarksService.Remark> saveRemarks(
      @PathVariable UUID id,
      @RequestParam UUID sectionId,
      @RequestBody List<MarksService.RemarkInput> rows) {
    return marks.saveRemarks(id, sectionId, rows);
  }

  // ------------------------------------------------------------------ outputs

  @GetMapping("/report-cards")
  @Operation(operationId = "reportCards", summary = "Report cards of a class")
  List<ReportService.ReportCard> reportCards(@RequestParam UUID sectionId) {
    return reports.forSection(sectionId);
  }

  @GetMapping("/{id}/hall-tickets")
  @Operation(operationId = "hallTickets", summary = "Hall tickets of a class")
  ReportService.HallTickets hallTickets(@PathVariable UUID id, @RequestParam UUID sectionId) {
    return reports.hallTickets(id, sectionId);
  }

  @GetMapping("/{id}/analysis")
  @Operation(operationId = "examAnalysis", summary = "Subject averages and toppers per class")
  List<ReportService.Analysis> analysis(@PathVariable UUID id) {
    return reports.analysis(id);
  }
}
