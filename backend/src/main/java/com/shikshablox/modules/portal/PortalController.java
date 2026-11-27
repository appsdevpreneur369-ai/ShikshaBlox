package com.shikshablox.modules.portal;

import com.shikshablox.modules.attendance.AttendanceService;
import com.shikshablox.modules.attendance.LeaveService;
import com.shikshablox.modules.certificates.CertificateService;
import com.shikshablox.modules.earlyyears.EarlyYearsService;
import com.shikshablox.modules.exams.ReportService;
import com.shikshablox.modules.homework.HomeworkService;
import com.shikshablox.modules.timetable.TimetableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Parent portal (M12). Public endpoints are called by the school website, which keeps the session
 * token in an httpOnly cookie and passes it as {@code X-Parent-Session}.
 */
@RestController
@Tag(name = "Parent portal")
class PortalController {

  private static final String P = "/api/public/v1/portal";
  private static final String H = "X-Parent-Session";

  record SetupRequest(String code, String phone, String pin) {}

  record LoginRequest(String phone, String pin) {}

  record CertificateRequest(UUID studentId, UUID templateId, String purpose) {}

  record PreferencesRequest(boolean whatsapp, boolean sms, boolean email, String language) {}

  private final PortalService portal;

  PortalController(PortalService portal) {
    this.portal = portal;
  }

  // ------------------------------------------------------------------ staff

  @GetMapping("/api/admin/v1/portal/students/{studentId}/access")
  @PreAuthorize("hasAuthority('students:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "portalAccess", summary = "Portal access of a student's guardians")
  List<PortalService.Access> access(@PathVariable UUID studentId) {
    return portal.access(studentId);
  }

  @PostMapping("/api/admin/v1/portal/guardians/{guardianId}/invite")
  @PreAuthorize("hasAuthority('students:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(
      operationId = "invitePortalParent",
      summary = "New invite link (also resets a forgotten PIN)")
  PortalService.Invite invite(@PathVariable UUID guardianId) {
    return portal.invite(guardianId);
  }

  @PostMapping("/api/admin/v1/portal/guardians/{guardianId}/disable")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('students:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(
      operationId = "disablePortalParent",
      summary = "Remove portal access and sign the parent out")
  void disable(@PathVariable UUID guardianId) {
    portal.disable(guardianId);
  }

  // ------------------------------------------------------------------ public (school website)

  @PostMapping(P + "/setup")
  @Operation(operationId = "portalSetup", summary = "Accept an invitation: confirm phone, set PIN")
  PortalService.Session setup(
      @RequestParam String host,
      @RequestBody SetupRequest req,
      @RequestHeader(value = "X-Client-IP", required = false) String ip) {
    return portal.setup(host, req.code(), req.phone(), req.pin(), ip);
  }

  @PostMapping(P + "/login")
  @Operation(operationId = "portalLogin", summary = "Sign in with phone and PIN")
  PortalService.Session login(
      @RequestParam String host,
      @RequestBody LoginRequest req,
      @RequestHeader(value = "X-Client-IP", required = false) String ip) {
    return portal.login(host, req.phone(), req.pin(), ip);
  }

  @PostMapping(P + "/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "portalLogout", summary = "Sign out")
  void logout(@RequestParam String host, @RequestHeader(value = H, required = false) String token) {
    portal.logout(host, token);
  }

  @GetMapping(P + "/me")
  @Operation(operationId = "portalMe", summary = "The parent and their children")
  PortalService.Me me(
      @RequestParam String host, @RequestHeader(value = H, required = false) String token) {
    return portal.me(host, token);
  }

  @GetMapping(P + "/circulars")
  @Operation(operationId = "portalCirculars", summary = "Circulars sent to the parent")
  List<PortalService.CircularItem> circulars(
      @RequestParam String host, @RequestHeader(value = H, required = false) String token) {
    return portal.circulars(host, token);
  }

  @GetMapping(P + "/requests")
  @Operation(operationId = "portalRequests", summary = "The parent's helpdesk requests")
  List<PortalService.RequestItem> requests(
      @RequestParam String host, @RequestHeader(value = H, required = false) String token) {
    return portal.requests(host, token);
  }

  @GetMapping(P + "/ptm")
  @Operation(operationId = "portalPtm", summary = "The parent's upcoming PTM bookings")
  List<PortalService.PtmItem> ptm(
      @RequestParam String host, @RequestHeader(value = H, required = false) String token) {
    return portal.ptm(host, token);
  }

  @GetMapping(P + "/attendance")
  @Operation(operationId = "portalAttendance", summary = "The children's attendance")
  List<AttendanceService.ChildAttendance> attendance(
      @RequestParam String host, @RequestHeader(value = H, required = false) String token) {
    return portal.attendance(host, token);
  }

  @GetMapping(P + "/report-cards")
  @Operation(operationId = "portalReportCards", summary = "The children's published report cards")
  List<ReportService.ReportCard> reportCards(
      @RequestParam String host, @RequestHeader(value = H, required = false) String token) {
    return portal.reportCards(host, token);
  }

  @GetMapping(P + "/certificates")
  @Operation(
      operationId = "portalCertificates",
      summary = "Certificate types and the children's certificates")
  PortalService.CertificatesView certificates(
      @RequestParam String host, @RequestHeader(value = H, required = false) String token) {
    return portal.certificates(host, token);
  }

  @PostMapping(P + "/certificates")
  @Operation(operationId = "portalRequestCertificate", summary = "Ask the school for a certificate")
  CertificateService.Certificate requestCertificate(
      @RequestParam String host,
      @RequestHeader(value = H, required = false) String token,
      @RequestBody CertificateRequest req) {
    return portal.requestCertificate(host, token, req.studentId(), req.templateId(), req.purpose());
  }

  @GetMapping(P + "/certificates/{id}")
  @Operation(operationId = "portalCertificate", summary = "An issued certificate")
  CertificateService.PrintView certificate(
      @RequestParam String host,
      @RequestHeader(value = H, required = false) String token,
      @PathVariable UUID id) {
    return portal.certificate(host, token, id);
  }

  @GetMapping(P + "/homework")
  @Operation(operationId = "portalHomework", summary = "The children's homework")
  List<HomeworkService.ChildHomework> homework(
      @RequestParam String host, @RequestHeader(value = H, required = false) String token) {
    return portal.homework(host, token);
  }

  @GetMapping(P + "/homework/files/{fileId}")
  @Operation(
      operationId = "portalHomeworkFile",
      summary = "Download link for a homework attachment")
  java.util.Map<String, String> homeworkFile(
      @RequestParam String host,
      @RequestHeader(value = H, required = false) String token,
      @PathVariable UUID fileId) {
    return java.util.Map.of("url", portal.homeworkFile(host, token, fileId));
  }

  @PostMapping(
      value = P + "/homework/{id}/submit",
      consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(operationId = "portalSubmitHomework", summary = "Hand in a child's work")
  HomeworkService.Submission submitHomework(
      @RequestParam String host,
      @RequestHeader(value = H, required = false) String token,
      @PathVariable UUID id,
      @RequestParam UUID studentId,
      @RequestParam(required = false) String text,
      @org.springframework.web.bind.annotation.RequestPart(value = "file", required = false)
          org.springframework.web.multipart.MultipartFile file) {
    byte[] content = null;
    if (file != null && !file.isEmpty()) {
      try {
        content = file.getBytes();
      } catch (java.io.IOException e) {
        throw com.shikshablox.platform.shared.web.ApiException.badRequest(
            "upload_failed", "The file could not be read.");
      }
    }
    return portal.submitHomework(
        host,
        token,
        id,
        studentId,
        text,
        file == null ? null : file.getOriginalFilename(),
        content);
  }

  @GetMapping(P + "/timetable")
  @Operation(operationId = "portalTimetable", summary = "The children's class timetables")
  List<TimetableService.ChildTimetable> timetable(
      @RequestParam String host, @RequestHeader(value = H, required = false) String token) {
    return portal.timetables(host, token);
  }

  @GetMapping(P + "/early-years")
  @Operation(operationId = "portalEarlyYears", summary = "Pre-primary daily feed")
  List<EarlyYearsService.ChildFeed> earlyYears(
      @RequestParam String host, @RequestHeader(value = H, required = false) String token) {
    return portal.earlyYears(host, token);
  }

  @GetMapping(P + "/leave")
  @Operation(operationId = "portalLeave", summary = "The children's leave requests")
  List<LeaveService.Leave> leave(
      @RequestParam String host, @RequestHeader(value = H, required = false) String token) {
    return portal.leave(host, token);
  }

  @PostMapping(P + "/leave")
  @Operation(operationId = "portalApplyLeave", summary = "Apply for leave for a child")
  LeaveService.Leave applyLeave(
      @RequestParam String host,
      @RequestHeader(value = H, required = false) String token,
      @RequestBody LeaveService.LeaveInput req) {
    return portal.applyLeave(host, token, req);
  }

  @PostMapping(P + "/leave/{id}/cancel")
  @Operation(operationId = "portalCancelLeave", summary = "Withdraw a leave request")
  LeaveService.Leave cancelLeave(
      @RequestParam String host,
      @RequestHeader(value = H, required = false) String token,
      @PathVariable UUID id) {
    return portal.cancelLeave(host, token, id);
  }

  @GetMapping(P + "/preferences")
  @Operation(operationId = "portalPreferences", summary = "The parent's message preferences")
  PortalService.Preferences preferences(
      @RequestParam String host, @RequestHeader(value = H, required = false) String token) {
    return portal.preferences(host, token);
  }

  @PutMapping(P + "/preferences")
  @Operation(
      operationId = "savePortalPreferences",
      summary = "Change message preferences (consent)")
  PortalService.Preferences savePreferences(
      @RequestParam String host,
      @RequestHeader(value = H, required = false) String token,
      @RequestBody PreferencesRequest req) {
    return portal.setPreferences(
        host, token, req.whatsapp(), req.sms(), req.email(), req.language());
  }
}
