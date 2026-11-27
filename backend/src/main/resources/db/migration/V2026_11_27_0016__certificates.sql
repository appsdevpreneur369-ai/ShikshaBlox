-- M32 Certificates & documents (small + medium depth): transfer certificates (TC), bonafide,
-- character and study certificates and the school's own templates with merge fields; a number
-- series per template; an issue register with cancellation and duplicates; optional principal
-- approval; no-dues clearances before a TC; a public QR verification page; parent requests from
-- the portal. Each issued certificate keeps a snapshot of its text and fields.

CREATE TABLE certificate_settings (
  organization_id   uuid PRIMARY KEY REFERENCES organization (id) ON DELETE CASCADE,
  signatory_name    text CHECK (signatory_name IS NULL OR length(signatory_name) <= 120),
  signatory_title   text NOT NULL DEFAULT 'Principal' CHECK (length(signatory_title) BETWEEN 1 AND 60),
  school_address    text CHECK (school_address IS NULL OR length(school_address) <= 300),
  affiliation_no    text CHECK (affiliation_no IS NULL OR length(affiliation_no) <= 40),
  school_code       text CHECK (school_code IS NULL OR length(school_code) <= 40),
  require_approval  boolean NOT NULL DEFAULT false,
  no_dues_items     text[] NOT NULL DEFAULT ARRAY['School fees', 'Library books', 'Laboratory', 'Sports equipment'],
  updated_at        timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE certificate_template (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id   uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  kind              text NOT NULL CHECK (kind IN ('TC', 'BONAFIDE', 'CHARACTER', 'STUDY', 'CUSTOM')),
  name              text NOT NULL CHECK (length(name) BETWEEN 1 AND 80),
  title             text NOT NULL CHECK (length(title) BETWEEN 1 AND 120),
  body              text NOT NULL CHECK (length(body) BETWEEN 1 AND 4000),
  prefix            text NOT NULL DEFAULT '' CHECK (length(prefix) <= 20),
  next_number       integer NOT NULL DEFAULT 1 CHECK (next_number > 0),
  parent_requestable boolean NOT NULL DEFAULT false,
  active            boolean NOT NULL DEFAULT true,
  sort_order        integer NOT NULL DEFAULT 0,
  UNIQUE (organization_id, name)
);

CREATE TABLE certificate (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id   uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  template_id       uuid NOT NULL REFERENCES certificate_template (id) ON DELETE RESTRICT,
  student_id        uuid NOT NULL REFERENCES student (id) ON DELETE CASCADE,  -- SIS refuses while any is ISSUED
  kind              text NOT NULL,
  title             text NOT NULL,
  number            text,
  verify_code       text UNIQUE,
  status            text NOT NULL CHECK (status IN ('REQUESTED', 'PENDING_APPROVAL', 'ISSUED', 'CANCELLED', 'REJECTED')),
  purpose           text CHECK (purpose IS NULL OR length(purpose) <= 200),
  fields            jsonb NOT NULL DEFAULT '{}',
  body              text,
  duplicate_of      uuid REFERENCES certificate (id) ON DELETE SET NULL,
  requested_by_guardian uuid REFERENCES guardian (id) ON DELETE SET NULL,
  created_by        uuid REFERENCES app_user (id) ON DELETE SET NULL,
  approved_by       uuid REFERENCES app_user (id) ON DELETE SET NULL,
  issued_by         uuid REFERENCES app_user (id) ON DELETE SET NULL,
  issued_at         timestamptz,
  status_note       text CHECK (status_note IS NULL OR length(status_note) <= 300),
  created_at        timestamptz NOT NULL DEFAULT now(),
  updated_at        timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX certificate_number_uq ON certificate (organization_id, number) WHERE number IS NOT NULL;
CREATE INDEX certificate_student_idx ON certificate (student_id);
CREATE INDEX certificate_status_idx ON certificate (organization_id, status, created_at DESC);

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['certificate_settings', 'certificate_template', 'certificate'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format(
      'CREATE POLICY %I ON %I USING (organization_id = sb_current_org() OR sb_is_system())'
      ' WITH CHECK (organization_id = sb_current_org() OR sb_is_system())', t || '_tenant', t);
  END LOOP;
END
$$;

-- ---------------------------------------------------------------------------------------------
-- Catalogue and plans: TC / bonafide / character / study certificates, numbering, register and QR
-- verification on every ERP plan; own templates, approval and parent requests on Standard/Global.
-- ---------------------------------------------------------------------------------------------
INSERT INTO module_catalogue (code, name, description, features) VALUES
  ('CERTIFICATES', 'Certificates', 'Transfer, bonafide, character and study certificates with numbering, register and QR verification',
   ARRAY['standard_certificates', 'issue_register', 'qr_verification', 'custom_templates', 'approval', 'parent_requests']);

UPDATE plan p
   SET entitlements = p.entitlements || jsonb_build_array(jsonb_build_object(
         'module', 'CERTIFICATES',
         'depth', CASE p.code WHEN 'LITE' THEN 'LITE' WHEN 'STANDARD' THEN 'STANDARD' ELSE 'ADVANCED' END,
         'features', CASE p.code WHEN 'LITE' THEN '["standard_certificates", "issue_register", "qr_verification"]'::jsonb
                     ELSE '["standard_certificates", "issue_register", "qr_verification", "custom_templates", "approval", "parent_requests"]'::jsonb END,
         'limits', '{}'::jsonb))
 WHERE p.code IN ('LITE', 'STANDARD', 'GLOBAL');

SELECT set_config('app.system', 'on', true);
INSERT INTO entitlement (organization_id, module_code, plan_code, depth, features, limits)
SELECT DISTINCT ON (en.organization_id) en.organization_id, 'CERTIFICATES', en.plan_code,
       CASE en.plan_code WHEN 'LITE' THEN 'LITE' WHEN 'STANDARD' THEN 'STANDARD' ELSE 'ADVANCED' END,
       CASE en.plan_code WHEN 'LITE' THEN ARRAY['standard_certificates', 'issue_register', 'qr_verification']
            ELSE ARRAY['standard_certificates', 'issue_register', 'qr_verification', 'custom_templates', 'approval', 'parent_requests'] END,
       '{}'::jsonb
  FROM entitlement en
 WHERE en.plan_code IN ('LITE', 'STANDARD', 'GLOBAL')
ON CONFLICT (organization_id, module_code) DO NOTHING;
SELECT set_config('app.system', '', true);
