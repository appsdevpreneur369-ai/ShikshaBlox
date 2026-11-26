-- M02 compliance (small + medium depth): a calendar of recurring obligations (affiliation,
-- safety certificates, statutory returns, committee meetings…) with owners, reminders and a
-- completion log, and an evidence vault of certificates with expiry dates. The website's public
-- disclosure section (M01) already exists; this is the back-office side.

CREATE TABLE compliance_item (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  title           text NOT NULL CHECK (length(title) BETWEEN 1 AND 160),
  category        text NOT NULL DEFAULT 'OTHER'
                  CHECK (category IN ('AFFILIATION', 'SAFETY', 'HEALTH', 'STATUTORY', 'FINANCE', 'STAFF',
                                      'TRANSPORT', 'DISCLOSURE', 'OTHER')),
  authority       text CHECK (authority IS NULL OR length(authority) <= 120),
  description     text CHECK (description IS NULL OR length(description) <= 2000),
  recurrence      text NOT NULL DEFAULT 'YEARLY'
                  CHECK (recurrence IN ('ONCE', 'MONTHLY', 'QUARTERLY', 'HALF_YEARLY', 'YEARLY')),
  due_date        date,                -- next due date; null once a one-time item is done
  remind_days     integer NOT NULL DEFAULT 30 CHECK (remind_days BETWEEN 0 AND 180),
  owner_id        uuid REFERENCES app_user (id) ON DELETE SET NULL,
  active          boolean NOT NULL DEFAULT true,
  created_by      uuid REFERENCES app_user (id) ON DELETE SET NULL,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX compliance_item_due_idx ON compliance_item (organization_id, due_date) WHERE active;

-- Evidence vault: certificates and proofs, optionally tied to an item, with an expiry date.
CREATE TABLE compliance_document (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id   uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  item_id           uuid REFERENCES compliance_item (id) ON DELETE SET NULL,
  title             text NOT NULL CHECK (length(title) BETWEEN 1 AND 160),
  category          text NOT NULL DEFAULT 'OTHER',
  reference_no      text CHECK (reference_no IS NULL OR length(reference_no) <= 120),
  issued_on         date,
  expires_on        date,
  original_filename text NOT NULL CHECK (length(original_filename) BETWEEN 1 AND 200),
  content_type      text NOT NULL,
  size_bytes        bigint NOT NULL CHECK (size_bytes > 0),
  sha256            text NOT NULL,
  storage_key       text NOT NULL,
  uploaded_by       uuid REFERENCES app_user (id) ON DELETE SET NULL,
  created_at        timestamptz NOT NULL DEFAULT now(),
  CHECK (expires_on IS NULL OR issued_on IS NULL OR expires_on >= issued_on)
);
CREATE INDEX compliance_document_expiry_idx ON compliance_document (organization_id, expires_on);
CREATE INDEX compliance_document_item_idx ON compliance_document (item_id);

-- Each time an item is completed for a cycle.
CREATE TABLE compliance_completion (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  item_id         uuid NOT NULL REFERENCES compliance_item (id) ON DELETE CASCADE,
  due_date        date,                -- the due date this completion closed
  completed_on    date NOT NULL,
  note            text CHECK (note IS NULL OR length(note) <= 1000),
  document_id     uuid REFERENCES compliance_document (id) ON DELETE SET NULL,
  completed_by    uuid REFERENCES app_user (id) ON DELETE SET NULL,
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX compliance_completion_item_idx ON compliance_completion (item_id, completed_on DESC);

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['compliance_item', 'compliance_document', 'compliance_completion'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format(
      'CREATE POLICY %I ON %I USING (organization_id = sb_current_org() OR sb_is_system())'
      ' WITH CHECK (organization_id = sb_current_org() OR sb_is_system())', t || '_tenant', t);
  END LOOP;
END
$$;

-- ---------------------------------------------------------------------------------------------
-- Catalogue and plans: Lite gets the calendar, vault and reminders; Standard / Global add owners
-- and the inspection export pack.
-- ---------------------------------------------------------------------------------------------
INSERT INTO module_catalogue (code, name, description, features) VALUES
  ('COMPLIANCE', 'Compliance calendar', 'Recurring obligations, reminders, evidence vault and export packs',
   ARRAY['calendar', 'evidence_vault', 'reminders', 'owners', 'export_pack']);

UPDATE plan p
   SET entitlements = p.entitlements || CASE p.code
     WHEN 'LITE' THEN '[{"module": "COMPLIANCE", "depth": "LITE",
                         "features": ["calendar", "evidence_vault", "reminders"], "limits": {}}]'::jsonb
     WHEN 'STANDARD' THEN '[{"module": "COMPLIANCE", "depth": "STANDARD",
                         "features": ["calendar", "evidence_vault", "reminders", "owners", "export_pack"],
                         "limits": {}}]'::jsonb
     ELSE '[{"module": "COMPLIANCE", "depth": "ADVANCED",
             "features": ["calendar", "evidence_vault", "reminders", "owners", "export_pack"],
             "limits": {}}]'::jsonb
   END
 WHERE p.code IN ('LITE', 'STANDARD', 'GLOBAL');

-- Schools already on those plans get the module now.
SELECT set_config('app.system', 'on', true);
INSERT INTO entitlement (organization_id, module_code, plan_code, depth, features, limits)
SELECT DISTINCT ON (en.organization_id) en.organization_id, 'COMPLIANCE', en.plan_code, e ->> 'depth',
       ARRAY(SELECT jsonb_array_elements_text(e -> 'features')), e -> 'limits'
  FROM entitlement en
  JOIN plan p ON p.code = en.plan_code
  CROSS JOIN LATERAL jsonb_array_elements(p.entitlements) e
 WHERE en.plan_code IN ('LITE', 'STANDARD', 'GLOBAL') AND e ->> 'module' = 'COMPLIANCE'
ON CONFLICT (organization_id, module_code) DO NOTHING;
SELECT set_config('app.system', '', true);
