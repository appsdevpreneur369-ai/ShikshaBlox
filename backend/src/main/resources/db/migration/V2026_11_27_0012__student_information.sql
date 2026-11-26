-- M04 Student information system (small + medium depth): classes and sections per academic
-- year, students with a lifecycle and yearly enrolments, guardians shared between siblings (with
-- pickup / emergency flags), documents, APAAR ID tracking with consent, custom fields, and the
-- school's current academic year (moved forward by year-end promotion).
-- Deliberately no Aadhaar numbers: they are not needed to run the school.

CREATE TABLE sis_settings (
  organization_id uuid PRIMARY KEY REFERENCES organization (id) ON DELETE CASCADE,
  current_year    text NOT NULL CHECK (current_year ~ '^\d{4}-\d{2}$'),
  next_admission_no integer NOT NULL DEFAULT 1 CHECK (next_admission_no > 0),
  admission_prefix text NOT NULL DEFAULT '' CHECK (length(admission_prefix) <= 10),
  updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE class_section (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  academic_year   text NOT NULL CHECK (academic_year ~ '^\d{4}-\d{2}$'),
  grade           text NOT NULL CHECK (length(grade) BETWEEN 1 AND 40),
  section         text NOT NULL DEFAULT 'A' CHECK (length(section) BETWEEN 1 AND 10),
  class_teacher   text CHECK (class_teacher IS NULL OR length(class_teacher) <= 120),
  grade_order     integer NOT NULL DEFAULT 0,
  UNIQUE (organization_id, academic_year, grade, section)
);

CREATE TABLE student (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id  uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  admission_no     text NOT NULL CHECK (length(admission_no) BETWEEN 1 AND 30),
  name             text NOT NULL CHECK (length(name) BETWEEN 1 AND 120),
  date_of_birth    date,
  gender           text CHECK (gender IS NULL OR gender IN ('FEMALE', 'MALE', 'OTHER')),
  blood_group      text CHECK (blood_group IS NULL OR blood_group IN ('A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-')),
  status           text NOT NULL DEFAULT 'ENROLLED' CHECK (status IN ('ENROLLED', 'WITHDRAWN', 'ALUMNI')),
  admission_date   date,
  leaving_date     date,
  leaving_reason   text CHECK (leaving_reason IS NULL OR length(leaving_reason) <= 300),
  previous_school  text CHECK (previous_school IS NULL OR length(previous_school) <= 200),
  address          text CHECK (address IS NULL OR length(address) <= 500),
  house            text CHECK (house IS NULL OR length(house) <= 40),
  medical_alerts   text CHECK (medical_alerts IS NULL OR length(medical_alerts) <= 1000),
  custody_note     text CHECK (custody_note IS NULL OR length(custody_note) <= 1000),
  apaar_id         text CHECK (apaar_id IS NULL OR apaar_id ~ '^[0-9]{12}$'),
  apaar_status     text NOT NULL DEFAULT 'NOT_STARTED'
                   CHECK (apaar_status IN ('NOT_STARTED', 'CONSENT_PENDING', 'CONSENT_GIVEN', 'CONSENT_REFUSED', 'GENERATED', 'EXCEPTION')),
  apaar_note       text CHECK (apaar_note IS NULL OR length(apaar_note) <= 300),
  custom           jsonb NOT NULL DEFAULT '{}',
  lead_id          uuid REFERENCES admission_lead (id) ON DELETE SET NULL,
  created_at       timestamptz NOT NULL DEFAULT now(),
  updated_at       timestamptz NOT NULL DEFAULT now(),
  UNIQUE (organization_id, admission_no)
);
CREATE INDEX student_name_idx ON student (organization_id, lower(name));
CREATE UNIQUE INDEX student_apaar_uq ON student (organization_id, apaar_id) WHERE apaar_id IS NOT NULL;

-- One row per student per academic year.
CREATE TABLE student_enrolment (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id  uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  student_id       uuid NOT NULL REFERENCES student (id) ON DELETE CASCADE,
  academic_year    text NOT NULL CHECK (academic_year ~ '^\d{4}-\d{2}$'),
  class_section_id uuid NOT NULL REFERENCES class_section (id) ON DELETE RESTRICT,
  roll_no          integer CHECK (roll_no IS NULL OR roll_no BETWEEN 1 AND 999),
  outcome          text CHECK (outcome IS NULL OR outcome IN ('PROMOTED', 'RETAINED', 'LEFT', 'GRADUATED')),
  created_at       timestamptz NOT NULL DEFAULT now(),
  UNIQUE (student_id, academic_year)
);
CREATE INDEX student_enrolment_section_idx ON student_enrolment (class_section_id);

CREATE TABLE guardian (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id  uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  name             text NOT NULL CHECK (length(name) BETWEEN 1 AND 120),
  phone            text CHECK (phone IS NULL OR length(phone) BETWEEN 6 AND 20),
  email            text CHECK (email IS NULL OR length(email) <= 200),
  occupation       text CHECK (occupation IS NULL OR length(occupation) <= 120),
  language         text NOT NULL DEFAULT 'en' CHECK (language ~ '^[a-z]{2}$'),
  created_at       timestamptz NOT NULL DEFAULT now(),
  updated_at       timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX guardian_phone_uq ON guardian (organization_id, right(regexp_replace(phone, '\D', '', 'g'), 10))
  WHERE phone IS NOT NULL;

CREATE TABLE student_guardian (
  organization_id  uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  student_id       uuid NOT NULL REFERENCES student (id) ON DELETE CASCADE,
  guardian_id      uuid NOT NULL REFERENCES guardian (id) ON DELETE CASCADE,
  relation         text NOT NULL DEFAULT 'GUARDIAN' CHECK (relation IN ('MOTHER', 'FATHER', 'GUARDIAN', 'GRANDPARENT', 'OTHER')),
  is_primary       boolean NOT NULL DEFAULT false,
  can_pickup       boolean NOT NULL DEFAULT true,
  emergency        boolean NOT NULL DEFAULT true,
  PRIMARY KEY (student_id, guardian_id)
);
CREATE INDEX student_guardian_guardian_idx ON student_guardian (guardian_id);

CREATE TABLE student_document (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id   uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  student_id        uuid NOT NULL REFERENCES student (id) ON DELETE CASCADE,
  title             text NOT NULL CHECK (length(title) BETWEEN 1 AND 120),
  original_filename text NOT NULL,
  content_type      text NOT NULL,
  size_bytes        bigint NOT NULL,
  storage_key       text NOT NULL,
  uploaded_by       uuid REFERENCES app_user (id) ON DELETE SET NULL,
  created_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX student_document_student_idx ON student_document (student_id);

-- School-defined extra fields (values in student.custom).
CREATE TABLE student_field (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  key             text NOT NULL CHECK (key ~ '^[a-z][a-z0-9_]{1,39}$'),
  label           text NOT NULL CHECK (length(label) BETWEEN 1 AND 80),
  type            text NOT NULL DEFAULT 'TEXT' CHECK (type IN ('TEXT', 'NUMBER', 'DATE', 'CHOICE', 'YES_NO')),
  options         text[] NOT NULL DEFAULT '{}',
  sort_order      integer NOT NULL DEFAULT 0,
  UNIQUE (organization_id, key)
);

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['sis_settings', 'class_section', 'student', 'student_enrolment', 'guardian',
                           'student_guardian', 'student_document', 'student_field'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format(
      'CREATE POLICY %I ON %I USING (organization_id = sb_current_org() OR sb_is_system())'
      ' WITH CHECK (organization_id = sb_current_org() OR sb_is_system())', t || '_tenant', t);
  END LOOP;
END
$$;

-- ---------------------------------------------------------------------------------------------
-- Catalogue and plans: SIS is the core of every ERP plan; custom fields 10 / 30 / unlimited.
-- ---------------------------------------------------------------------------------------------
INSERT INTO module_catalogue (code, name, description, features) VALUES
  ('SIS', 'Student information', 'Students, guardians, classes, enrolments, promotion, APAAR and UDISE+ readiness',
   ARRAY['profiles', 'guardians', 'documents', 'import', 'promotion', 'apaar', 'custom_fields']);

UPDATE plan p
   SET entitlements = p.entitlements || jsonb_build_array(jsonb_build_object(
         'module', 'SIS',
         'depth', CASE p.code WHEN 'LITE' THEN 'LITE' WHEN 'STANDARD' THEN 'STANDARD' ELSE 'ADVANCED' END,
         'features', '["profiles", "guardians", "documents", "import", "promotion", "apaar", "custom_fields"]'::jsonb,
         'limits', jsonb_build_object('custom_fields', CASE p.code WHEN 'LITE' THEN 10 WHEN 'STANDARD' THEN 30 ELSE -1 END)))
 WHERE p.code IN ('LITE', 'STANDARD', 'GLOBAL');

SELECT set_config('app.system', 'on', true);
INSERT INTO entitlement (organization_id, module_code, plan_code, depth, features, limits)
SELECT DISTINCT ON (en.organization_id) en.organization_id, 'SIS', en.plan_code,
       CASE en.plan_code WHEN 'LITE' THEN 'LITE' WHEN 'STANDARD' THEN 'STANDARD' ELSE 'ADVANCED' END,
       ARRAY['profiles', 'guardians', 'documents', 'import', 'promotion', 'apaar', 'custom_fields'],
       jsonb_build_object('custom_fields', CASE en.plan_code WHEN 'LITE' THEN 10 WHEN 'STANDARD' THEN 30 ELSE -1 END)
  FROM entitlement en
 WHERE en.plan_code IN ('LITE', 'STANDARD', 'GLOBAL')
ON CONFLICT (organization_id, module_code) DO NOTHING;
SELECT set_config('app.system', '', true);
