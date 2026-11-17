# ADR 0006: Media pipeline and object storage

- Status: Accepted (M1b)
- Date: 2026-11-17

## Decision
- Any S3-compatible store through the AWS SDK v2 (path-style, checksums only when required): MinIO locally, Google Cloud Storage (HMAC interoperability) on staging, S3/R2 later. Two buckets: `private` (signed 10-minute links) and `public` (immutable, CDN-cacheable URLs).
- Uploads are identified by **file signature**, never by the client's content type. Allowed: JPG/PNG/WebP, PDF/DOCX/XLSX/PPTX, MP4. SVG is rejected (script risk). Caps come from configuration (10 MB images, 25 MB documents, 50 MB video). Plan storage is checked before storing.
- Every upload passes a `MalwareScanner` hook (no-op by default; ClamAV is the production plan).
- Images: thumb 320, medium 960, large 1920 px, never upscaled, re-encoded to **WebP** with `cwebp` (JPEG/PNG fallback), which strips EXIF/GPS. The **original is always private**, so camera metadata from school photos never becomes public. Decompression bombs are refused (> 50 MP).
- Storage usage (original + variants) is metered for the plan limit through the `UsageMeter` interface.
- Media has `contains_identifiable_students` and an optional consent record. Gallery publishing (M3) requires `ConsentService.mayPublish`: an active linked consent record or an active school-level declaration.

## Consequences
- Revoking consent makes media non-publishable immediately; M3 also unpublishes affected gallery items.
- Public variant keys contain random ids; identifiable photos that are not yet consented are not linked from any page.
