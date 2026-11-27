package com.shikshablox.modules.homework;

import com.shikshablox.platform.shared.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import java.util.Map;
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

/** Studio API for homework (M10). */
@RestController
@RequestMapping("/api/admin/v1/homework")
@PreAuthorize("hasAuthority('homework:post')")
@SecurityRequirement(name = "bearer")
@Tag(name = "Homework")
class HomeworkController {

  private final HomeworkService homework;

  HomeworkController(HomeworkService homework) {
    this.homework = homework;
  }

  @GetMapping("/overview")
  @Operation(operationId = "homeworkOverview", summary = "My classes and their subjects")
  HomeworkService.Overview overview() {
    return homework.overview();
  }

  @GetMapping
  @Operation(operationId = "listHomework", summary = "Recent and upcoming homework of my classes")
  List<HomeworkService.Homework> list(@RequestParam(required = false) UUID sectionId) {
    return homework.list(sectionId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(operationId = "postHomework", summary = "Post homework to one or more classes")
  List<HomeworkService.Homework> create(@RequestBody HomeworkService.HomeworkInput in) {
    return homework.create(in);
  }

  @PutMapping("/{id}")
  @Operation(operationId = "updateHomework", summary = "Change homework")
  HomeworkService.Homework update(
      @PathVariable UUID id, @RequestBody HomeworkService.HomeworkInput in) {
    return homework.update(id, in);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "deleteHomework", summary = "Delete homework")
  void delete(@PathVariable UUID id) {
    homework.delete(id);
  }

  @PostMapping(value = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(operationId = "addHomeworkFile", summary = "Attach a file (up to 10 MB)")
  HomeworkService.Homework addFile(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
    return homework.addFile(id, file.getOriginalFilename(), bytes(file));
  }

  @DeleteMapping("/files/{fileId}")
  @Operation(operationId = "removeHomeworkFile", summary = "Remove an attachment")
  HomeworkService.Homework removeFile(@PathVariable UUID fileId) {
    return homework.removeFile(fileId);
  }

  @GetMapping("/files/{fileId}/url")
  @Operation(operationId = "homeworkFileUrl", summary = "Download link for an attachment")
  Map<String, String> fileUrl(@PathVariable UUID fileId) {
    return Map.of("url", homework.fileUrl(fileId));
  }

  @GetMapping("/{id}/submissions")
  @Operation(operationId = "homeworkSubmissions", summary = "Who handed in what")
  List<HomeworkService.Submission> submissions(@PathVariable UUID id) {
    return homework.submissions(id);
  }

  @PostMapping("/submissions/{submissionId}/grade")
  @Operation(operationId = "gradeSubmission", summary = "Mark, give feedback or return for redo")
  HomeworkService.Submission grade(
      @PathVariable UUID submissionId, @RequestBody HomeworkService.GradeInput in) {
    return homework.grade(submissionId, in);
  }

  @GetMapping("/submissions/{submissionId}/file")
  @Operation(operationId = "submissionFileUrl", summary = "Download link for a handed-in file")
  Map<String, String> submissionFile(@PathVariable UUID submissionId) {
    return Map.of("url", homework.submissionFileUrl(submissionId));
  }

  static byte[] bytes(MultipartFile f) {
    try {
      return f.getBytes();
    } catch (IOException e) {
      throw ApiException.badRequest("upload_failed", "The file could not be read.");
    }
  }
}
