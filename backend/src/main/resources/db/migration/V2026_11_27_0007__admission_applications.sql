-- M03b: online application form on the school website, with document upload, a document
-- checklist the office verifies, and an application fee the office records (paid at school or by
-- bank transfer; no payment gateway yet).

-- ---------------------------------------------------------------------------------------------
-- Per-school form settings
-- ---------------------------------------------------------------------------------------------
CREATE TABLE admission_form_settings (
  organization_id  uuid PRIMARY KEY REFERENCES organization (id) ON DELETE CASCADE,
  is_open          boolean NOT NULL DEFAULT false,
  grades           text[] NOT NULL DEFAULT '{}',
  documents        jsonb NOT NULL DEFAULT '[
    {"code": "birth_certificate", "label": "Birth certificate", "required": true},
    {"code": "photo", "label": "Student photograph", "required": true},
    {"code": "address_proof", "label": "Address proof", "required": false},
    {"code": "report_card", "label": "Previous report card / TC", "required": false}
  ]',
  fee_amount       integer NOT NULL DEFAULT 0 CHECK (fee_amount BETWEEN 0 AND 100000),
  fee_instructions text CHECK (fee_instructions IS NULL OR length(fee_instructions) <= 1000),
  intro            text CHECK (intro IS NULL OR length(intro) <= 2000),
  updated_at       timestamptz NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------------------------
-- Applications (always linked to a lead, so the pipeline stays the single view of a family)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE admission_application (
  id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id       uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  lead_id               uuid NOT NULL REFERENCES admission_lead (id) ON DELETE CASCADE,
  application_no        text NOT NULL,
  academic_year         text NOT NULL CHECK (academic_year ~ '^\d{4}-\d{2}$'),
  grade                 text NOT NULL CHECK (length(grade) BETWEEN 1 AND 60),
  student_name          text NOT NULL CHECK (length(student_name) BETWEEN 1 AND 120),
  date_of_birth         date NOT NULL,
  gender                text CHECK (gender IS NULL OR gender IN ('FEMALE', 'MALE', 'OTHER')),
  previous_school       text CHECK (previous_school IS NULL OR length(previous_school) <= 200),
  guardian_name         text NOT NULL CHECK (length(guardian_name) BETWEEN 1 AND 120),
  relationship          text CHECK (relationship IS NULL OR length(relationship) <= 40),
  phone                 text NOT NULL CHECK (length(phone) BETWEEN 6 AND 20),
  email                 text CHECK (email IS NULL OR length(email) <= 200),
  address               text CHECK (address IS NULL OR length(address) <= 500),
  second_guardian_name  text CHECK (second_guardian_name IS NULL OR length(second_guardian_name) <= 120),
  second_guardian_phone text CHECK (second_guardian_phone IS NULL OR length(second_guardian_phone) <= 20),
  status                text NOT NULL DEFAULT 'SUBMITTED'
                        CHECK (status IN ('SUBMITTED', 'UNDER_REVIEW', 'DOCUMENTS_PENDING', 'VERIFIED',
                                          'REJECTED', 'WITHDRAWN')),
  review_note           text CHECK (review_note IS NULL OR length(review_note) <= 1000),
  fee_amount            integer NOT NULL DEFAULT 0 CHECK (fee_amount BETWEEN 0 AND 100000),
  fee_status            text NOT NULL DEFAULT 'NOT_REQUIRED'
                        CHECK (fee_status IN ('NOT_REQUIRED', 'DUE', 'PAID', 'WAIVED')),
  fee_reference         text CHECK (fee_reference IS NULL OR length(fee_reference) <= 120),
  fee_paid_at           timestamptz,
  consent_given         boolean NOT NULL CHECK (consent_given),
  created_at            timestamptz NOT NULL DEFAULT now(),
  updated_at            timestamptz NOT NULL DEFAULT now(),
  UNIQUE (organization_id, application_no)
);
CREATE INDEX admission_application_year_idx ON admission_application (organization_id, academic_year, status);
CREATE INDEX admission_application_lead_idx ON admission_application (lead_id);

-- Uploaded documents live in the private bucket; Studio gets 10-minute signed links.
CREATE TABLE admission_document (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id   uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  application_id    uuid NOT NULL REFERENCES admission_application (id) ON DELETE CASCADE,
  doc_code          text NOT NULL CHECK (doc_code ~ '^[a-z0-9_]{1,40}$'),
  label             text NOT NULL CHECK (length(label) BETWEEN 1 AND 80),
  original_filename text NOT NULL CHECK (length(original_filename) BETWEEN 1 AND 200),
  content_type      text NOT NULL,
  size_bytes        bigint NOT NULL CHECK (size_bytes > 0),
  sha256            text NOT NULL,
  storage_key       text NOT NULL,
  status            text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED')),
  note              text CHECK (note IS NULL OR length(note) <= 300),
  reviewed_by       uuid REFERENCES app_user (id) ON DELETE SET NULL,
  reviewed_at       timestamptz,
  created_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX admission_document_application_idx ON admission_document (application_id);

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['admission_form_settings', 'admission_application', 'admission_document'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format(
      'CREATE POLICY %I ON %I USING (organization_id = sb_current_org() OR sb_is_system())'
      ' WITH CHECK (organization_id = sb_current_org() OR sb_is_system())', t || '_tenant', t);
  END LOOP;
END
$$;

-- Timeline entries for applications.
ALTER TABLE admission_activity DROP CONSTRAINT admission_activity_kind_check;
ALTER TABLE admission_activity ADD CONSTRAINT admission_activity_kind_check
  CHECK (kind IN ('CREATED', 'NOTE', 'CALL', 'VISIT', 'MESSAGE', 'STAGE', 'ASSIGNED', 'FOLLOW_UP',
                  'APPLICATION'));

-- ---------------------------------------------------------------------------------------------
-- Entitlements: every CRM plan gets the online application form
-- ---------------------------------------------------------------------------------------------
UPDATE module_catalogue
   SET features = array_append(features, 'online_application')
 WHERE code = 'ADMISSIONS_CRM' AND NOT ('online_application' = ANY (features));

UPDATE plan p
   SET entitlements = (
     SELECT jsonb_agg(
              CASE WHEN e ->> 'module' = 'ADMISSIONS_CRM'
                   THEN jsonb_set(e, '{features}', (e -> 'features') || '["online_application"]')
                   ELSE e END
              ORDER BY ord)
       FROM jsonb_array_elements(p.entitlements) WITH ORDINALITY AS x(e, ord))
 WHERE p.code IN ('LITE', 'STANDARD', 'GLOBAL');

SELECT set_config('app.system', 'on', true);
UPDATE entitlement
   SET features = array_append(features, 'online_application'), updated_at = now()
 WHERE module_code = 'ADMISSIONS_CRM' AND NOT ('online_application' = ANY (features));
SELECT set_config('app.system', '', true);
