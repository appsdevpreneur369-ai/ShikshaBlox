package com.shikshablox.modules.portal;

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
