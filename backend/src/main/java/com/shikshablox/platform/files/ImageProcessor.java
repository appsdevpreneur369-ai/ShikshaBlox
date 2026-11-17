package com.shikshablox.platform.files;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Produces resized variants (thumb 320, medium 960, large 1920; never upscaled). Variants are
 * re-encoded, which also strips EXIF metadata such as GPS location from school photos. WebP is
 * produced with the {@code cwebp} tool when installed (container image); otherwise JPEG/PNG.
 */
@Component
class ImageProcessor {

  private static final Logger log = LoggerFactory.getLogger(ImageProcessor.class);

  record Variant(
      String name, byte[] content, String contentType, String extension, int width, int height) {}

  record Decoded(BufferedImage image, int width, int height) {}

  static final Map<String, Integer> SIZES = new LinkedHashMap<>();

  static {
    SIZES.put("thumb", 320);
    SIZES.put("medium", 960);
    SIZES.put("large", 1920);
  }

  /** Images larger than this (pixels) are rejected to prevent decompression bombs. */
  static final long MAX_PIXELS = 50_000_000L;

  private final boolean webp;

  ImageProcessor() {
    this.webp = toolAvailable("cwebp");
    log.info(
        "Image variants will be encoded as {}", webp ? "WebP" : "JPEG/PNG (cwebp not installed)");
  }

  Decoded decode(byte[] content, String contentType) throws IOException {
    byte[] source = content;
    if ("image/webp".equals(contentType)) {
      source = runTool(List.of("dwebp", "%in", "-png", "-o", "%out"), content, ".webp", ".png");
    }
    try (var in = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
      var readers = ImageIO.getImageReaders(in);
      if (!readers.hasNext()) {
        throw new IOException("Unsupported image");
      }
      var reader = readers.next();
      try {
        reader.setInput(in, true, true);
        long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
        if (pixels > MAX_PIXELS) {
          throw new IOException("Image dimensions too large");
        }
        BufferedImage img = reader.read(0);
        return new Decoded(img, img.getWidth(), img.getHeight());
      } finally {
        reader.dispose();
      }
    }
  }

  List<Variant> variants(Decoded decoded) throws IOException {
    boolean alpha = decoded.image().getColorModel().hasAlpha();
    var out = new java.util.ArrayList<Variant>();
    int previousWidth = -1;
    for (var e : SIZES.entrySet()) {
      int target = Math.min(e.getValue(), decoded.width());
      if (target == previousWidth) {
        continue; // small originals: do not create identical variants
      }
      previousWidth = target;
      BufferedImage scaled = scale(decoded.image(), target, alpha);
      out.add(encode(e.getKey(), scaled, alpha));
    }
    return out;
  }

  private Variant encode(String name, BufferedImage img, boolean alpha) throws IOException {
    if (webp) {
      try {
        // Lossless PNG as the intermediate so WebP is the only lossy step.
        byte[] w =
            runTool(
                List.of("cwebp", "-quiet", "-q", "80", "-metadata", "none", "%in", "-o", "%out"),
                write(img, "png"),
                ".png",
                ".webp");
        return new Variant(name, w, "image/webp", "webp", img.getWidth(), img.getHeight());
      } catch (IOException ex) {
        log.warn("WebP encoding failed, falling back: {}", ex.getMessage());
      }
    }
    return alpha
        ? new Variant(name, write(img, "png"), "image/png", "png", img.getWidth(), img.getHeight())
        : new Variant(
            name, write(img, "jpg"), "image/jpeg", "jpg", img.getWidth(), img.getHeight());
  }

  private static BufferedImage scale(BufferedImage src, int width, boolean alpha) {
    int height = Math.max(1, (int) Math.round(src.getHeight() * (width / (double) src.getWidth())));
    BufferedImage dst =
        new BufferedImage(
            width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
    Graphics2D g = dst.createGraphics();
    try {
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      if (!alpha) {
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, width, height);
      }
      g.drawImage(src, 0, 0, width, height, null);
    } finally {
      g.dispose();
    }
    return dst;
  }

  private static byte[] write(BufferedImage img, String format) throws IOException {
    var bytes = new ByteArrayOutputStream();
    if ("jpg".equals(format)) {
      ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(0.82f);
      try (var out = ImageIO.createImageOutputStream(bytes)) {
        writer.setOutput(out);
        writer.write(null, new IIOImage(img, null, null), param);
      } finally {
        writer.dispose();
      }
    } else {
      ImageIO.write(img, format, bytes);
    }
    return bytes.toByteArray();
  }

  private static byte[] runTool(List<String> template, byte[] input, String inExt, String outExt)
      throws IOException {
    Path in = Files.createTempFile("sb-img-", inExt);
    Path out = Files.createTempFile("sb-img-", outExt);
    try {
      Files.write(in, input);
      List<String> cmd =
          template.stream()
              .map(a -> a.equals("%in") ? in.toString() : a.equals("%out") ? out.toString() : a)
              .toList();
      Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
      p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
      if (!p.waitFor(30, TimeUnit.SECONDS) || p.exitValue() != 0) {
        p.destroyForcibly();
        throw new IOException(template.get(0) + " failed");
      }
      return Files.readAllBytes(out);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted", e);
    } finally {
      Files.deleteIfExists(in);
      Files.deleteIfExists(out);
    }
  }

  private static boolean toolAvailable(String tool) {
    try {
      Process p = new ProcessBuilder(tool, "-version").redirectErrorStream(true).start();
      p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
      return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
    } catch (Exception e) {
      return false;
    }
  }
}
