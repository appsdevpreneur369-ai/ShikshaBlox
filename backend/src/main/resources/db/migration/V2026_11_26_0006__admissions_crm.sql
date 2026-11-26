-- M03 Admissions CRM (small + medium depth): leads with a stage pipeline, follow-ups and an
-- activity timeline, seat capacity per class, and the ERP plans (Lite / Standard / Global).

-- ---------------------------------------------------------------------------------------------
-- Role: admissions counsellor (works leads; no website or settings access)
-- ---------------------------------------------------------------------------------------------
ALTER TABLE role_assignment DROP CONSTRAINT role_assignment_role_check;
ALTER TABLE role_assignment ADD CONSTRAINT role_assignment_role_check
  CHECK (role IN ('PLATFORM_SUPER_ADMIN', 'SCHOOL_ADMIN', 'CONTENT_EDITOR', 'CONTENT_APPROVER', 'VIEWER',
                  'ADMISSIONS_COUNSELLOR'));

-- ---------------------------------------------------------------------------------------------
-- Leads
-- ---------------------------------------------------------------------------------------------
CREATE TABLE admission_lead (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id   uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  enquiry_id        uuid UNIQUE REFERENCES enquiry (id) ON DELETE SET NULL,
  student_name      text CHECK (student_name IS NULL OR length(student_name) BETWEEN 1 AND 120),
  guardian_name     text NOT NULL CHECK (length(guardian_name) BETWEEN 1 AND 120),
  phone             text NOT NULL CHECK (length(phone) BETWEEN 6 AND 20),
  email             text CHECK (email IS NULL OR length(email) <= 200),
  grade_sought      text CHECK (grade_sought IS NULL OR length(grade_sought) <= 60),
  academic_year     text NOT NULL CHECK (academic_year ~ '^\d{4}-\d{2}$'),
  source            text NOT NULL DEFAULT 'WEBSITE'
                    CHECK (source IN ('WEBSITE', 'WALK_IN', 'PHONE', 'WHATSAPP', 'REFERRAL', 'CAMPAIGN', 'OTHER')),
  stage             text NOT NULL DEFAULT 'NEW'
                    CHECK (stage IN ('NEW', 'CONTACTED', 'VISIT_SCHEDULED', 'VISITED', 'APPLIED', 'OFFERED',
                                     'ADMITTED', 'LOST')),
  lost_reason       text CHECK (lost_reason IS NULL OR length(lost_reason) <= 200),
  priority          text NOT NULL DEFAULT 'NORMAL' CHECK (priority IN ('LOW', 'NORMAL', 'HIGH')),
  assigned_to       uuid REFERENCES app_user (id) ON DELETE SET NULL,
  next_follow_up_at timestamptz,
  previous_school   text CHECK (previous_school IS NULL OR length(previous_school) <= 200),
  notes             text CHECK (notes IS NULL OR length(notes) <= 4000),
  created_by        uuid REFERENCES app_user (id) ON DELETE SET NULL,
  created_at        timestamptz NOT NULL DEFAULT now(),
  updated_at        timestamptz NOT NULL DEFAULT now(),
  CHECK (stage <> 'LOST' OR lost_reason IS NOT NULL)
);
CREATE INDEX admission_lead_stage_idx ON admission_lead (organization_id, academic_year, stage);
CREATE INDEX admission_lead_follow_up_idx ON admission_lead (organization_id, next_follow_up_at)
  WHERE stage NOT IN ('ADMITTED', 'LOST');
CREATE INDEX admission_lead_phone_idx ON admission_lead (organization_id, phone);

CREATE TABLE admission_activity (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  lead_id         uuid NOT NULL REFERENCES admission_lead (id) ON DELETE CASCADE,
  kind            text NOT NULL CHECK (kind IN ('CREATED', 'NOTE', 'CALL', 'VISIT', 'MESSAGE', 'STAGE', 'ASSIGNED',
                                                'FOLLOW_UP')),
  body            text CHECK (body IS NULL OR length(body) <= 2000),
  from_stage      text,
  to_stage        text,
  actor_id        uuid REFERENCES app_user (id) ON DELETE SET NULL,
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX admission_activity_lead_idx ON admission_activity (lead_id, created_at DESC);

-- Seats per class and academic year (medium schools). Admitted counts come from the leads.
CREATE TABLE admission_seat (
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  academic_year   text NOT NULL CHECK (academic_year ~ '^\d{4}-\d{2}$'),
  grade           text NOT NULL CHECK (length(grade) BETWEEN 1 AND 60),
  capacity        integer NOT NULL CHECK (capacity BETWEEN 0 AND 5000),
  sort_order      integer NOT NULL DEFAULT 0,
  updated_at      timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (organization_id, academic_year, grade)
);

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['admission_lead', 'admission_activity', 'admission_seat'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format(
      'CREATE POLICY %I ON %I USING (organization_id = sb_current_org() OR sb_is_system())'
      ' WITH CHECK (organization_id = sb_current_org() OR sb_is_system())', t || '_tenant', t);
  END LOOP;
END
$$;

-- ---------------------------------------------------------------------------------------------
-- Catalogue and plans
-- ---------------------------------------------------------------------------------------------
INSERT INTO module_catalogue (code, name, description, features) VALUES
  ('ADMISSIONS_CRM', 'Admissions CRM', 'Lead pipeline, follow-ups, walk-ins, seats and funnel analytics',
   ARRAY['pipeline', 'follow_ups', 'walk_ins', 'csv_export', 'counsellors', 'seat_matrix', 'funnel_analytics']);

INSERT INTO plan (code, name, sort_order, entitlements) VALUES
  ('LITE', 'Lite', 3, '[
     {"module": "WEBSITE", "depth": "STANDARD",
      "features": ["notices", "events", "gallery", "staff", "news", "faqs", "careers", "analytics",
                   "approval_workflow", "custom_domain", "remove_branding", "export_import"],
      "limits": {"custom_pages": -1, "storage_bytes": 10737418240, "languages": 2, "custom_domains": 1}},
     {"module": "DISCLOSURE", "depth": "STANDARD", "features": ["review_reminders"], "limits": {}},
     {"module": "ADMISSIONS_ENQUIRY", "depth": "STANDARD", "features": ["csv_export", "email_alerts"], "limits": {}},
     {"module": "ADMISSIONS_CRM", "depth": "LITE",
      "features": ["pipeline", "follow_ups", "walk_ins", "csv_export"], "limits": {}}
   ]'),
  ('STANDARD', 'Standard', 4, '[
     {"module": "WEBSITE", "depth": "STANDARD",
      "features": ["notices", "events", "gallery", "staff", "news", "faqs", "careers", "analytics",
                   "approval_workflow", "custom_domain", "remove_branding", "export_import"],
      "limits": {"custom_pages": -1, "storage_bytes": 26843545600, "languages": 2, "custom_domains": 2}},
     {"module": "DISCLOSURE", "depth": "STANDARD", "features": ["review_reminders"], "limits": {}},
     {"module": "ADMISSIONS_ENQUIRY", "depth": "STANDARD", "features": ["csv_export", "email_alerts"], "limits": {}},
     {"module": "ADMISSIONS_CRM", "depth": "STANDARD",
      "features": ["pipeline", "follow_ups", "walk_ins", "csv_export", "counsellors", "seat_matrix",
                   "funnel_analytics"], "limits": {}}
   ]'),
  ('GLOBAL', 'Global', 5, '[
     {"module": "WEBSITE", "depth": "ADVANCED",
      "features": ["notices", "events", "gallery", "staff", "news", "faqs", "careers", "analytics",
                   "approval_workflow", "custom_domain", "remove_branding", "export_import"],
      "limits": {"custom_pages": -1, "storage_bytes": 53687091200, "languages": 4, "custom_domains": 5}},
     {"module": "DISCLOSURE", "depth": "ADVANCED", "features": ["review_reminders"], "limits": {}},
     {"module": "ADMISSIONS_ENQUIRY", "depth": "ADVANCED", "features": ["csv_export", "email_alerts"], "limits": {}},
     {"module": "ADMISSIONS_CRM", "depth": "ADVANCED",
      "features": ["pipeline", "follow_ups", "walk_ins", "csv_export", "counsellors", "seat_matrix",
                   "funnel_analytics"], "limits": {}}
   ]');
