package com.shikshablox.platform.files;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.config.ConfigKey;
import com.shikshablox.platform.config.ConfigService;
import com.shikshablox.platform.consent.ConsentService;
import com.shikshablox.platform.entitlement.EntitlementService;
import com.shikshablox.platform.entitlement.UsageMeter;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.TenantContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Media library: validated uploads (type by file signature, size caps, plan storage limit,
 * malware-scan hook), image variants, public CDN URLs or signed private links, consent flags.
 */
@Service
public class MediaService implements UsageMeter {

  private static final Logger log = LoggerFactory.getLogger(MediaService.class);

  public enum Kind {
    IMAGE,
    DOCUMENT,
    VIDEO
  }

  public enum Visibility {
    PUBLIC,
    PRIVATE
  }

  public record VariantInfo(String key, String contentType, int width, int height) {}

  public record MediaAsset(
      UUID id,
      Kind kind,
      Visibility visibility,
      String originalFilename,
      String contentType,
      long sizeBytes,
      long storedBytes,
      String storageKey,
      Integer width,
      Integer height,
      Map<String, VariantInfo> variants,
      String altText,
      String caption,
      String folder,
      boolean containsIdentifiableStudents,
      UUID consentRecordId,
      Instant createdAt) {}

  public record Upload(
      String filename,
      byte[] content,
      Visibility visibility,
      String altText,
      String caption,
      String folder,
      boolean containsIdentifiableStudents) {}

  public record MetadataUpdate(
      String altText,
      String caption,
      String folder,
      Boolean containsIdentifiableStudents,
      UUID consentRecordId,
      boolean clearConsent) {}

  record Detected(Kind kind, String contentType, String extension) {}

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final ObjectStorage storage;
  private final ImageProcessor images;
  private final MalwareScanner scanner;
  private final EntitlementService entitlements;
  private final ConfigService config;
  private final ConsentService consents;
  private final AuditService audit;
  private final ObjectMapper json;

  MediaService(
      JdbcClient jdbc,
      TransactionTemplate tx,
      ObjectStorage storage,
      ImageProcessor images,
      MalwareScanner scanner,
      @org.springframework.context.annotation.Lazy EntitlementService entitlements,
      ConfigService config,
      ConsentService consents,
      AuditService audit,
      ObjectMapper json) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.storage = storage;
    this.images = images;
    this.scanner = scanner;
    this.entitlements = entitlements;
    this.config = config;
    this.consents = consents;
    this.audit = audit;
    this.json = json;
  }

  // --------------------------------------------------------------------------- usage meter

  @Override
  public String module() {
    return "WEBSITE";
  }

  @Override
  public String limitKey() {
    return "storage_bytes";
  }

  @Override
  public long usage(UUID organizationId) {
    return jdbc.sql(
            "select coalesce(sum(stored_bytes), 0) from media_asset where organization_id = ?")
        .param(organizationId)
        .query(Long.class)
        .single();
  }

  // --------------------------------------------------------------------------- upload

  public MediaAsset upload(Upload in) {
    UUID org = TenantContext.requireOrganizationId();
    String filename = cleanFilename(in.filename());
    Detected type = detect(in.content(), filename);
    long maxBytes = maxBytes(type.kind());
    if (in.content().length == 0) {
      throw ApiException.badRequest("empty_file", "The file is empty.");
    }
    if (in.content().length > maxBytes) {
      throw ApiException.badRequest(
          "file_too_large", "This file is larger than " + (maxBytes / (1024 * 1024)) + " MB.");
    }
    entitlements.checkCanAdd("WEBSITE", "storage_bytes", in.content().length, "more storage");
    if (scanner.scan(filename, in.content()) == MalwareScanner.Verdict.INFECTED) {
      log.warn("Upload rejected by malware scanner: {}", filename);
      throw ApiException.badRequest(
          "file_rejected", "This file was rejected by the virus scanner.");
    }

    UUID id = UUID.randomUUID();
    String prefix = "orgs/" + org + "/media/" + id + "/";
    String originalKey = prefix + "original." + type.extension();
    Visibility visibility = in.visibility() == null ? Visibility.PUBLIC : in.visibility();
    List<String[]> written = new ArrayList<>(); // [visibility, key] for compensation
    Map<String, VariantInfo> variants = new LinkedHashMap<>();
    Integer width = null;
    Integer height = null;
    long stored = in.content().length;

    try {
      if (type.kind() == Kind.IMAGE) {
        ImageProcessor.Decoded decoded;
        try {
          decoded = images.decode(in.content(), type.contentType());
        } catch (IOException e) {
          throw ApiException.badRequest("invalid_image", "This image could not be read.");
        }
        width = decoded.width();
        height = decoded.height();
        // Originals keep camera metadata (e.g. GPS), so they are always private.
        storage.put(Visibility.PRIVATE, originalKey, in.content(), type.contentType());
        written.add(new String[] {"PRIVATE", originalKey});
        for (ImageProcessor.Variant v : images.variants(decoded)) {
          String key = prefix + v.name() + "." + v.extension();
          storage.put(visibility, key, v.content(), v.contentType());
          written.add(new String[] {visibility.name(), key});
          variants.put(v.name(), new VariantInfo(key, v.contentType(), v.width(), v.height()));
          stored += v.content().length;
        }
      } else {
        storage.put(visibility, originalKey, in.content(), type.contentType());
        written.add(new String[] {visibility.name(), originalKey});
      }
    } catch (ApiException e) {
      cleanup(written);
      throw e;
    } catch (Exception e) {
      cleanup(written);
      log.error("Upload failed", e);
      throw new ApiException(
          HttpStatus.BAD_GATEWAY,
          "storage_unavailable",
          "The file could not be stored. Please try again.");
    }

    final Integer w = width;
    final Integer h = height;
    final long storedBytes = stored;
    try {
      return tx.execute(s -> {
        jdbc.sql(
                "insert into media_asset (id, organization_id, kind, visibility, original_filename,"
                    + " content_type, size_bytes, stored_bytes, sha256, storage_key, width, height,"
                    + " variants, alt_text, caption, folder, contains_identifiable_students, created_by)"
                    + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)")
            .params(
                id,
                org,
                type.kind().name(),
                visibility.name(),
                filename,
                type.contentType(),
                (long) in.content().length,
                storedBytes,
                sha256(in.content()),
                originalKey,
                w,
                h,
                toJson(variants),
                trim(in.altText(), 300),
                trim(in.caption(), 1000),
                cleanFolder(in.folder()),
                in.containsIdentifiableStudents(),
                TenantContext.userId().orElse(null))
            .update();
        MediaAsset asset = find(id).orElseThrow();
        audit.record(
            "media.uploaded",
            "media",
            id.toString(),
            null,
            Map.of("filename", filename, "kind", type.kind(), "bytes", in.content().length));
        return asset;
      });
    } catch (RuntimeException e) {
      cleanup(written);
      throw e;
    }
  }

  private void cleanup(List<String[]> written) {
    for (String[] w : written) {
      try {
        storage.delete(Visibility.valueOf(w[0]), w[1]);
      } catch (Exception ex) {
        log.warn("Could not remove orphaned object {}", w[1]);
      }
    }
  }

  private long maxBytes(Kind kind) {
    ConfigKey key =
        switch (kind) {
          case IMAGE -> ConfigKey.MEDIA_MAX_IMAGE_MB;
          case DOCUMENT -> ConfigKey.MEDIA_MAX_DOCUMENT_MB;
          case VIDEO -> ConfigKey.MEDIA_MAX_VIDEO_MB;
        };
    return config.getInt(key) * 1024L * 1024L;
  }

  /** Identifies the type from the file's signature; the client's content type is ignored. */
  static Detected detect(byte[] b, String filename) {
    String ext =
        filename.contains(".")
            ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
            : "";
    if (starts(b, 0xFF, 0xD8, 0xFF)) {
      return new Detected(Kind.IMAGE, "image/jpeg", "jpg");
    }
    if (starts(b, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
      return new Detected(Kind.IMAGE, "image/png", "png");
    }
    if (b.length > 12 && ascii(b, 0, 4).equals("RIFF") && ascii(b, 8, 4).equals("WEBP")) {
      return new Detected(Kind.IMAGE, "image/webp", "webp");
    }
    if (ascii(b, 0, 5).equals("%PDF-")) {
      return new Detected(Kind.DOCUMENT, "application/pdf", "pdf");
    }
    if (b.length > 12 && ascii(b, 4, 4).equals("ftyp") && ext.equals("mp4")) {
      return new Detected(Kind.VIDEO, "video/mp4", "mp4");
    }
    if (starts(b, 0x50, 0x4B, 0x03, 0x04)) {
      switch (ext) {
        case "docx":
          return new Detected(
              Kind.DOCUMENT,
              "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
              "docx");
        case "xlsx":
          return new Detected(
              Kind.DOCUMENT,
              "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
              "xlsx");
        case "pptx":
          return new Detected(
              Kind.DOCUMENT,
              "application/vnd.openxmlformats-officedocument.presentationml.presentation",
              "pptx");
        default:
          break;
      }
    }
    throw ApiException.badRequest(
        "unsupported_type",
        "Upload JPG, PNG or WebP images; PDF, DOCX, XLSX or PPTX documents; or MP4 video.");
  }

  private static boolean starts(byte[] b, int... sig) {
    if (b.length < sig.length) {
      return false;
    }
    for (int i = 0; i < sig.length; i++) {
      if ((b[i] & 0xFF) != sig[i]) {
        return false;
      }
    }
    return true;
  }

  private static String ascii(byte[] b, int off, int len) {
    return b.length < off + len ? "" : new String(b, off, len, StandardCharsets.US_ASCII);
  }

  // --------------------------------------------------------------------------- queries

  public Optional<MediaAsset> find(UUID id) {
    return jdbc.sql("select * from media_asset where id = ?").param(id).query(this::map).optional();
  }

  public MediaAsset require(UUID id) {
    return find(id).orElseThrow(() -> ApiException.notFound("File"));
  }

  public List<MediaAsset> list(Kind kind, String folder, String q, int limit, int offset) {
    StringBuilder sql = new StringBuilder("select * from media_asset where true");
    List<Object> params = new ArrayList<>();
    if (kind != null) {
      sql.append(" and kind = ?");
      params.add(kind.name());
    }
    if (folder != null && !folder.isBlank()) {
      sql.append(" and folder = ?");
      params.add(cleanFolder(folder));
    }
    if (q != null && !q.isBlank()) {
      sql.append(" and (original_filename ilike ? or alt_text ilike ? or caption ilike ?)");
      String like = "%" + q.replace("%", "").replace("_", "") + "%";
      params.add(like);
      params.add(like);
      params.add(like);
    }
    sql.append(" order by created_at desc limit ? offset ?");
    params.add(Math.max(1, Math.min(limit, 100)));
    params.add(Math.max(0, offset));
    return jdbc.sql(sql.toString()).params(params).query(this::map).list();
  }

  /** Whether the media may appear on the public website (consent rule). */
  public boolean publishable(MediaAsset a) {
    return a.visibility() == Visibility.PUBLIC
        && consents.mayPublish(a.containsIdentifiableStudents(), a.consentRecordId());
  }

  /** Public URL of an image variant (or the original for documents/videos). */
  public Optional<String> publicUrl(MediaAsset a, String variant) {
    if (a.visibility() != Visibility.PUBLIC) {
      return Optional.empty();
    }
    if (a.kind() == Kind.IMAGE) {
      VariantInfo v = a.variants().get(variant);
      if (v == null && !a.variants().isEmpty()) {
        v = a.variants().values().stream().reduce((x, y) -> y).orElseThrow(); // largest
      }
      return Optional.ofNullable(v).map(x -> storage.publicUrl(x.key()));
    }
    return Optional.of(storage.publicUrl(a.storageKey()));
  }

  /** A 10-minute download link for private files (and image originals). */
  public String signedDownloadUrl(UUID id) {
    MediaAsset a = require(id);
    if (a.visibility() == Visibility.PUBLIC && a.kind() != Kind.IMAGE) {
      return storage.publicUrl(a.storageKey());
    }
    return storage.signedUrl(a.storageKey(), Duration.ofMinutes(10), a.originalFilename());
  }

  // --------------------------------------------------------------------------- changes

  public MediaAsset update(UUID id, MetadataUpdate u) {
    return tx.execute(s -> {
      MediaAsset before = require(id);
      if (u.consentRecordId() != null && consents.find(u.consentRecordId()).isEmpty()) {
        throw ApiException.badRequest("invalid_consent", "That consent record does not exist.");
      }
      UUID consentId =
          u.clearConsent()
              ? null
              : (u.consentRecordId() != null ? u.consentRecordId() : before.consentRecordId());
      jdbc.sql(
              "update media_asset set alt_text = ?, caption = ?, folder = ?,"
                  + " contains_identifiable_students = ?, consent_record_id = ?, updated_at = now()"
                  + " where id = ?")
          .params(
              u.altText() != null ? trim(u.altText(), 300) : before.altText(),
              u.caption() != null ? trim(u.caption(), 1000) : before.caption(),
              u.folder() != null ? cleanFolder(u.folder()) : before.folder(),
              u.containsIdentifiableStudents() != null
                  ? u.containsIdentifiableStudents()
                  : before.containsIdentifiableStudents(),
              consentId,
              id)
          .update();
      MediaAsset after = require(id);
      audit.record("media.updated", "media", id.toString(), summary(before), summary(after));
      return after;
    });
  }

  public void delete(UUID id) {
    MediaAsset a =
        tx.execute(s -> {
          MediaAsset asset = require(id);
          jdbc.sql("delete from media_asset where id = ?").param(id).update();
          audit.record("media.deleted", "media", id.toString(), summary(asset), null);
          return asset;
        });
    List<String[]> keys = new ArrayList<>();
    keys.add(
        new String[] {a.kind() == Kind.IMAGE ? "PRIVATE" : a.visibility().name(), a.storageKey()});
    a.variants().values().forEach(v -> keys.add(new String[] {a.visibility().name(), v.key()}));
    cleanup(keys);
  }

  private static Map<String, Object> summary(MediaAsset a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("filename", a.originalFilename());
    m.put("altText", a.altText());
    m.put("caption", a.caption());
    m.put("folder", a.folder());
    m.put("identifiable", a.containsIdentifiableStudents());
    m.put("consentRecordId", a.consentRecordId());
    return m;
  }

  // --------------------------------------------------------------------------- helpers

  private MediaAsset map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
    Map<String, VariantInfo> variants;
    try {
      variants =
          json.readValue(
              rs.getString("variants"),
              new com.fasterxml.jackson.core.type.TypeReference<
                  LinkedHashMap<String, VariantInfo>>() {});
    } catch (Exception e) {
      throw new java.sql.SQLException("Invalid variants", e);
    }
    return new MediaAsset(
        rs.getObject("id", UUID.class),
        Kind.valueOf(rs.getString("kind")),
        Visibility.valueOf(rs.getString("visibility")),
        rs.getString("original_filename"),
        rs.getString("content_type"),
        rs.getLong("size_bytes"),
        rs.getLong("stored_bytes"),
        rs.getString("storage_key"),
        (Integer) rs.getObject("width"),
        (Integer) rs.getObject("height"),
        variants,
        rs.getString("alt_text"),
        rs.getString("caption"),
        rs.getString("folder"),
        rs.getBoolean("contains_identifiable_students"),
        rs.getObject("consent_record_id", UUID.class),
        rs.getTimestamp("created_at").toInstant());
  }

  static String cleanFilename(String raw) {
    String name = raw == null ? "file" : raw.replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>|:*?]", "").trim();
    if (name.isEmpty()) {
      name = "file";
    }
    return name.length() > 200 ? name.substring(name.length() - 200) : name;
  }

  static String cleanFolder(String raw) {
    if (raw == null) {
      return "";
    }
    String f = raw.trim().replaceAll("[^A-Za-z0-9 _-]", "");
    return f.length() > 60 ? f.substring(0, 60) : f;
  }

  private static String trim(String s, int max) {
    if (s == null) {
      return null;
    }
    String t = s.trim();
    return t.length() > max ? t.substring(0, max) : t;
  }

  private static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String toJson(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
