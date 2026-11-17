package com.shikshablox.platform.files;

import com.shikshablox.platform.files.MediaService.Kind;
import com.shikshablox.platform.files.MediaService.MediaAsset;
import com.shikshablox.platform.files.MediaService.Visibility;
import com.shikshablox.platform.shared.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/v1/media")
@Tag(name = "Media library")
@SecurityRequirement(name = "bearer")
class MediaController {

  record VariantView(String url, int width, int height) {}

  record MediaView(
      UUID id,
      String kind,
      String visibility,
      String filename,
      String contentType,
      long sizeBytes,
      Integer width,
      Integer height,
      String url,
      Map<String, VariantView> variants,
      String altText,
      String caption,
      String folder,
      boolean containsIdentifiableStudents,
      UUID consentRecordId,
      boolean publishable,
      boolean needsAltText,
      Instant createdAt) {}

  record UpdateMediaRequest(
      @Size(max = 300) String altText,
      @Size(max = 1000) String caption,
      @Size(max = 60) String folder,
      Boolean containsIdentifiableStudents,
      UUID consentRecordId,
      Boolean clearConsent) {}

  record DownloadLink(String url) {}

  private final MediaService media;

  MediaController(MediaService media) {
    this.media = media;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('content:edit')")
  @Operation(operationId = "uploadMedia", summary = "Upload an image, document or MP4 video")
  MediaView upload(
      @RequestPart("file") MultipartFile file,
      @RequestParam(defaultValue = "PUBLIC") Visibility visibility,
      @RequestParam(required = false) String altText,
      @RequestParam(required = false) String caption,
      @RequestParam(required = false) String folder,
      @RequestParam(defaultValue = "false") boolean containsIdentifiableStudents) {
    byte[] content;
    try {
      content = file.getBytes();
    } catch (IOException e) {
      throw ApiException.badRequest("upload_failed", "The upload did not complete.");
    }
    return view(
        media.upload(
            new MediaService.Upload(
                file.getOriginalFilename(),
                content,
                visibility,
                altText,
                caption,
                folder,
                containsIdentifiableStudents)));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('content:view')")
  @Operation(operationId = "listMedia", summary = "Browse the media library")
  List<MediaView> list(
      @RequestParam(required = false) Kind kind,
      @RequestParam(required = false) String folder,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "40") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    return media.list(kind, folder, q, limit, offset).stream().map(this::view).toList();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('content:view')")
  @Operation(operationId = "getMedia", summary = "One media item")
  MediaView get(@PathVariable UUID id) {
    return view(media.require(id));
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('content:edit')")
  @Operation(
      operationId = "updateMedia",
      summary = "Edit alt text, caption, folder or consent flags")
  MediaView update(@PathVariable UUID id, @Valid @RequestBody UpdateMediaRequest req) {
    return view(
        media.update(
            id,
            new MediaService.MetadataUpdate(
                req.altText(),
                req.caption(),
                req.folder(),
                req.containsIdentifiableStudents(),
                req.consentRecordId(),
                Boolean.TRUE.equals(req.clearConsent()))));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('content:edit')")
  @Operation(operationId = "deleteMedia", summary = "Delete a media item and its files")
  void delete(@PathVariable UUID id) {
    media.delete(id);
  }

  @GetMapping("/{id}/download")
  @PreAuthorize("hasAuthority('content:view')")
  @Operation(operationId = "mediaDownloadLink", summary = "A short-lived download link")
  DownloadLink download(@PathVariable UUID id) {
    return new DownloadLink(media.signedDownloadUrl(id));
  }

  private MediaView view(MediaAsset a) {
    Map<String, VariantView> variants = new LinkedHashMap<>();
    a.variants()
        .forEach((name, v) ->
            media
                .publicUrl(a, name)
                .ifPresent(url -> variants.put(name, new VariantView(url, v.width(), v.height()))));
    return new MediaView(
        a.id(),
        a.kind().name(),
        a.visibility().name(),
        a.originalFilename(),
        a.contentType(),
        a.sizeBytes(),
        a.width(),
        a.height(),
        media.publicUrl(a, "large").orElse(null),
        variants,
        a.altText(),
        a.caption(),
        a.folder(),
        a.containsIdentifiableStudents(),
        a.consentRecordId(),
        media.publishable(a),
        a.kind() == Kind.IMAGE && (a.altText() == null || a.altText().isBlank()),
        a.createdAt());
  }
}
