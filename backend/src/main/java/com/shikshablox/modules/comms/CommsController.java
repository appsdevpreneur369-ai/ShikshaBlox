package com.shikshablox.modules.comms;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Communication hub (M13): Studio API, the public circular page, platform wallet top-ups. */
@RestController
@Tag(name = "Communication hub")
class CommsController {

  private static final String A = "/api/admin/v1/comms";
  private static final String P = "/api/public/v1/comms";

  record ImportRequest(String csv, String consentNote) {}

  record SettingsRequest(LocalTime quietStart, LocalTime quietEnd, Integer monthlyCap) {}

  record AckRequest(Boolean consent) {}

  record TopUpRequest(long amountPaise, String reason) {}

  private final CommsService comms;
  private final BroadcastService broadcasts;

  CommsController(CommsService comms, BroadcastService broadcasts) {
    this.comms = comms;
    this.broadcasts = broadcasts;
  }

  // ------------------------------------------------------------------ contacts

  @GetMapping(A + "/contacts")
  @PreAuthorize("hasAuthority('comms:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "listCommsContacts", summary = "Contacts, filtered by tag or search")
  List<CommsService.Contact> contacts(
      @RequestParam(required = false) String tag, @RequestParam(required = false) String q) {
    return comms.contacts(tag, q);
  }

  @GetMapping(A + "/tags")
  @PreAuthorize("hasAuthority('comms:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "listCommsTags", summary = "Tags with contact counts")
  List<CommsService.TagCount> tags() {
    return comms.tags();
  }

  @PostMapping(A + "/contacts")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('comms:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "createCommsContact", summary = "Add a contact with their opt-ins")
  CommsService.Contact create(@RequestBody CommsService.ContactInput in) {
    return comms.create(in, "STAFF");
  }

  @GetMapping(A + "/contacts/{id}")
  @PreAuthorize("hasAuthority('comms:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "getCommsContact", summary = "Contact with consent history")
  CommsService.ContactDetail contact(@PathVariable UUID id) {
    return comms.contact(id);
  }

  @PatchMapping(A + "/contacts/{id}")
  @PreAuthorize("hasAuthority('comms:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "updateCommsContact", summary = "Edit a contact or their opt-ins")
  CommsService.Contact update(@PathVariable UUID id, @RequestBody CommsService.ContactInput in) {
    return comms.update(id, in);
  }

  @DeleteMapping(A + "/contacts/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('comms:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "deleteCommsContact", summary = "Delete a contact")
  void delete(@PathVariable UUID id) {
    comms.delete(id);
  }

  @PostMapping(A + "/contacts/import")
  @PreAuthorize("hasAuthority('comms:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "importCommsContacts", summary = "Import contacts from CSV text")
  CommsService.ImportResult importCsv(@RequestBody ImportRequest req) {
    return comms.importCsv(req.csv(), req.consentNote());
  }

  // ------------------------------------------------------------------ templates

  @GetMapping(A + "/templates")
  @PreAuthorize("hasAuthority('comms:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "listCommsTemplates", summary = "Templates and their language variants")
  List<CommsService.Template> templates() {
    return comms.templates();
  }

  @PostMapping(A + "/templates")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('comms:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "createCommsTemplate", summary = "Add a template or language variant")
  CommsService.Template createTemplate(@RequestBody CommsService.TemplateInput in) {
    return comms.saveTemplate(null, in);
  }

  @PutMapping(A + "/templates/{id}")
  @PreAuthorize("hasAuthority('comms:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "updateCommsTemplate", summary = "Edit a template variant")
  CommsService.Template updateTemplate(
      @PathVariable UUID id, @RequestBody CommsService.TemplateInput in) {
    return comms.saveTemplate(id, in);
  }

  @DeleteMapping(A + "/templates/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('comms:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "deleteCommsTemplate", summary = "Delete a template variant")
  void deleteTemplate(@PathVariable UUID id) {
    comms.deleteTemplate(id);
  }

  // ------------------------------------------------------------------ broadcasts

  @GetMapping(A + "/broadcasts")
  @PreAuthorize("hasAuthority('comms:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "listBroadcasts", summary = "Broadcasts with delivery statistics")
  List<BroadcastService.Broadcast> broadcasts() {
    return broadcasts.list();
  }

  @PostMapping(A + "/broadcasts")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('comms:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "createBroadcast", summary = "Draft a broadcast")
  BroadcastService.Broadcast createBroadcast(@RequestBody BroadcastService.BroadcastInput in) {
    return broadcasts.save(null, in);
  }

  @GetMapping(A + "/broadcasts/{id}")
  @PreAuthorize("hasAuthority('comms:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "getBroadcast", summary = "A broadcast with statistics")
  BroadcastService.Broadcast broadcast(@PathVariable UUID id) {
    return broadcasts.get(id);
  }

  @PatchMapping(A + "/broadcasts/{id}")
  @PreAuthorize("hasAuthority('comms:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "updateBroadcast", summary = "Edit a draft")
  BroadcastService.Broadcast updateBroadcast(
      @PathVariable UUID id, @RequestBody BroadcastService.BroadcastInput in) {
    return broadcasts.save(id, in);
  }

  @DeleteMapping(A + "/broadcasts/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('comms:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(
      operationId = "deleteBroadcast",
      summary = "Delete a broadcast and its delivery records (not while sending)")
  void deleteBroadcast(@PathVariable UUID id) {
    broadcasts.delete(id);
  }

  @GetMapping(A + "/broadcasts/{id}/preview")
  @PreAuthorize("hasAuthority('comms:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(
      operationId = "previewBroadcast",
      summary = "Recipients by channel, skips, cost, send time")
  BroadcastService.Preview preview(@PathVariable UUID id) {
    return broadcasts.preview(id);
  }

  @PostMapping(A + "/broadcasts/{id}/send")
  @PreAuthorize("hasAuthority('comms:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(
      operationId = "sendBroadcast",
      summary = "Send now / at the scheduled time / after quiet hours")
  BroadcastService.Broadcast send(@PathVariable UUID id) {
    return broadcasts.send(id);
  }

  @PostMapping(A + "/broadcasts/{id}/cancel")
  @PreAuthorize("hasAuthority('comms:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "cancelBroadcast", summary = "Cancel a scheduled broadcast")
  BroadcastService.Broadcast cancel(@PathVariable UUID id) {
    return broadcasts.cancel(id);
  }

  @GetMapping(A + "/broadcasts/{id}/deliveries")
  @PreAuthorize("hasAuthority('comms:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(
      operationId = "broadcastDeliveries",
      summary = "Per-recipient status, read and acknowledgement")
  List<BroadcastService.Delivery> deliveries(
      @PathVariable UUID id, @RequestParam(required = false) String filter) {
    return broadcasts.deliveries(id, filter);
  }

  // ------------------------------------------------------------------ settings

  @GetMapping(A + "/settings")
  @PreAuthorize("hasAuthority('comms:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "commsSettings", summary = "Quiet hours, cap, provider, wallet")
  CommsService.Settings settings() {
    return comms.settings();
  }

  @PutMapping(A + "/settings")
  @PreAuthorize("hasAuthority('comms:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "saveCommsSettings", summary = "Quiet hours and monthly cap")
  CommsService.Settings saveSettings(@RequestBody SettingsRequest req) {
    return comms.saveSettings(req.quietStart(), req.quietEnd(), req.monthlyCap());
  }

  @GetMapping(A + "/wallet")
  @PreAuthorize("hasAuthority('comms:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(operationId = "commsWallet", summary = "Messaging wallet entries")
  List<CommsService.WalletEntry> wallet() {
    return comms.wallet();
  }

  @PostMapping("/api/platform/v1/organizations/{organizationId}/comms-wallet")
  @PreAuthorize("hasAuthority('platform:manage')")
  @SecurityRequirement(name = "bearer")
  @Operation(
      operationId = "topUpCommsWallet",
      summary = "Credit or adjust a school's messaging wallet")
  CommsService.Settings topUp(@PathVariable UUID organizationId, @RequestBody TopUpRequest req) {
    return comms.topUp(organizationId, req.amountPaise(), req.reason());
  }

  // ------------------------------------------------------------------ public circular page

  @GetMapping(P + "/circulars/{token}")
  @Operation(operationId = "publicCircular", summary = "A circular from a personal message link")
  BroadcastService.Circular circular(@RequestParam String host, @PathVariable String token) {
    return broadcasts.circular(host, token);
  }

  @PostMapping(P + "/circulars/{token}/ack")
  @Operation(
      operationId = "acknowledgeCircular",
      summary = "Acknowledge (and answer the consent question)")
  BroadcastService.Circular ack(
      @RequestParam String host, @PathVariable String token, @RequestBody AckRequest req) {
    return broadcasts.acknowledge(host, token, req.consent());
  }

  @PostMapping(P + "/circulars/{token}/stop")
  @Operation(operationId = "stopMessages", summary = "Stop all messages to this contact")
  BroadcastService.Circular stop(@RequestParam String host, @PathVariable String token) {
    return broadcasts.stop(host, token);
  }
}
