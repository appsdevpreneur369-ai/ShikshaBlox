package com.shikshablox.modules.curriculum;

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

/** Studio API for curriculum, coverage, lesson plans and observations (M09). */
@RestController
@RequestMapping("/api/admin/v1/curriculum")
@PreAuthorize("hasAnyAuthority('curriculum:manage', 'homework:post')")
@SecurityRequirement(name = "bearer")
@Tag(name = "Curriculum")
class CurriculumController {

  record TopicsRequest(List<String> titles) {}

  record CoverRequest(UUID sectionId, UUID topicId, boolean covered, LocalDate date) {}

  record NoteRequest(String note) {}

  private final CurriculumService curriculum;

  CurriculumController(CurriculumService curriculum) {
    this.curriculum = curriculum;
  }

  @GetMapping("/overview")
  @Operation(
      operationId = "curriculumOverview",
      summary = "My classes, subjects, observation areas")
  CurriculumService.Overview overview() {
    return curriculum.overview();
  }

  @GetMapping("/coverage")
  @Operation(
      operationId = "curriculumCoverage",
      summary = "Syllabus of a subject, with coverage in a class")
  CurriculumService.Coverage coverage(
      @RequestParam UUID subjectId, @RequestParam(required = false) UUID sectionId) {
    return curriculum.coverage(subjectId, sectionId);
  }

  @GetMapping("/coverage/overview")
  @Operation(
      operationId = "coverageOverview",
      summary = "Completion of every subject in every class")
  List<CurriculumService.CoverageCell> coverageOverview() {
    return curriculum.coverageOverview();
  }

  @PutMapping("/coverage")
  @Operation(operationId = "markTopicCovered", summary = "Mark a topic covered (or not) in a class")
  CurriculumService.Coverage cover(@RequestBody CoverRequest req) {
    return curriculum.markCovered(req.sectionId(), req.topicId(), req.covered(), req.date());
  }

  @PostMapping("/units")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(operationId = "addSyllabusUnit", summary = "Add a unit to a subject's syllabus")
  CurriculumService.Coverage addUnit(@RequestBody CurriculumService.UnitInput in) {
    return curriculum.addUnit(in);
  }

  @PutMapping("/units/{id}")
  @Operation(operationId = "updateSyllabusUnit", summary = "Rename a unit or change its month")
  CurriculumService.Coverage updateUnit(
      @PathVariable UUID id, @RequestBody CurriculumService.UnitInput in) {
    return curriculum.updateUnit(id, in);
  }

  @DeleteMapping("/units/{id}")
  @Operation(operationId = "deleteSyllabusUnit", summary = "Delete a unit and its topics")
  CurriculumService.Coverage deleteUnit(@PathVariable UUID id) {
    return curriculum.deleteUnit(id);
  }

  @PostMapping("/units/{id}/topics")
  @Operation(operationId = "addSyllabusTopics", summary = "Add topics (one per line)")
  CurriculumService.Coverage addTopics(@PathVariable UUID id, @RequestBody TopicsRequest req) {
    return curriculum.addTopics(id, req.titles());
  }

  @DeleteMapping("/topics/{id}")
  @Operation(operationId = "deleteSyllabusTopic", summary = "Delete a topic")
  CurriculumService.Coverage deleteTopic(@PathVariable UUID id) {
    return curriculum.deleteTopic(id);
  }

  @GetMapping("/plans")
  @Operation(
      operationId = "lessonPlans",
      summary = "Lesson plans (mine, or submitted ones for the coordinator)")
  List<CurriculumService.LessonPlan> plans(@RequestParam(required = false) String status) {
    return curriculum.plans(status);
  }

  @PostMapping("/plans")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(operationId = "createLessonPlan", summary = "Write a lesson plan")
  CurriculumService.LessonPlan createPlan(@RequestBody CurriculumService.LessonPlanInput in) {
    return curriculum.savePlan(null, in);
  }

  @PutMapping("/plans/{id}")
  @Operation(operationId = "updateLessonPlan", summary = "Change or submit a lesson plan")
  CurriculumService.LessonPlan updatePlan(
      @PathVariable UUID id, @RequestBody CurriculumService.LessonPlanInput in) {
    return curriculum.savePlan(id, in);
  }

  @PostMapping("/plans/{id}/review")
  @Operation(operationId = "reviewLessonPlan", summary = "Mark a plan reviewed, with a note")
  CurriculumService.LessonPlan review(@PathVariable UUID id, @RequestBody NoteRequest req) {
    return curriculum.review(id, req.note());
  }

  @DeleteMapping("/plans/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "deleteLessonPlan", summary = "Delete a lesson plan")
  void deletePlan(@PathVariable UUID id) {
    curriculum.deletePlan(id);
  }

  @GetMapping("/observations")
  @Operation(
      operationId = "observations",
      summary = "Classroom observations (own, or all for the coordinator)")
  List<CurriculumService.Observation> observations() {
    return curriculum.observations();
  }

  @PostMapping("/observations")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(operationId = "recordObservation", summary = "Record a classroom observation")
  CurriculumService.Observation observe(@RequestBody CurriculumService.ObservationInput in) {
    return curriculum.observe(in);
  }

  @DeleteMapping("/observations/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "deleteObservation", summary = "Delete an observation")
  void deleteObservation(@PathVariable UUID id) {
    curriculum.deleteObservation(id);
  }

  @PostMapping("/observations/{id}/acknowledge")
  @Operation(
      operationId = "acknowledgeObservation",
      summary = "Acknowledge an observation, with a reflection")
  CurriculumService.Observation acknowledge(@PathVariable UUID id, @RequestBody NoteRequest req) {
    return curriculum.acknowledge(id, req.note());
  }
}
