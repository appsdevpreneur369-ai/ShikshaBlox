-- M07 Attendance & leave (small + medium depth): daily (or period-wise) class attendance taken by
-- the class teacher on a phone, absence / late alerts to guardians through the communication hub,
-- leave applications from the parent portal approved by the class teacher, corrections of past
-- days with approval and audit, monthly registers with attendance % for eligibility, absence
-- reason codes and a "needs attention" list for chronic absence.

ALTER TABLE role_assignment DROP CONSTRAINT role_assignment_role_check;
ALTER TABLE role_assignment ADD CONSTRAINT role_assignment_role_check
  CHECK (role IN ('PLATFORM_SUPER_ADMIN', 'SCHOOL_ADMIN', 'CONTENT_EDITOR', 'CONTENT_APPROVER', 'VIEWER',
                  'ADMISSIONS_COUNSELLOR', 'FRONT_OFFICE', 'TEACHER'));

CREATE TABLE attendance_settings (
  organization_id     uuid PRIMARY KEY REFERENCES organization (id) ON DELETE CASCADE,
  mode                text NOT NULL DEFAULT 'DAILY' CHECK (mode IN ('DAILY', 'PERIOD')),
  periods_per_day     integer NOT NULL DEFAULT 8 CHECK (periods_per_day BETWEEN 1 AND 12),
  alert_absent        boolean NOT NULL DEFAULT true,
  alert_late          boolean NOT NULL DEFAULT false,
  min_percent         integer NOT NULL DEFAULT 75 CHECK (min_percent BETWEEN 0 AND 100),
  concern_percent     integer NOT NULL DEFAULT 85 CHECK (concern_percent BETWEEN 0 AND 100),
  concern_streak      integer NOT NULL DEFAULT 3 CHECK (concern_streak BETWEEN 2 AND 30),
  reason_codes        text[] NOT NULL DEFAULT ARRAY['Sick', 'Family function', 'Travel', 'Not informed'],
  updated_at          timestamptz NOT NULL DEFAULT now()
);

-- Who takes attendance for a section (class teacher, and subject teachers in period mode).
CREATE TABLE attendance_teacher (
  organization_id  uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  class_section_id uuid NOT NULL REFERENCES class_section (id) ON DELETE CASCADE,
  user_id          uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
  PRIMARY KEY (class_section_id, user_id)
);
CREATE INDEX attendance_teacher_user_idx ON attendance_teacher (user_id);

-- One marked roll call: a section on a date (period 0 = the daily roll call).
CREATE TABLE attendance_session (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id  uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  class_section_id uuid NOT NULL REFERENCES class_section (id) ON DELETE CASCADE,
  date             date NOT NULL,
  period           integer NOT NULL DEFAULT 0 CHECK (period BETWEEN 0 AND 12),
  subject          text CHECK (subject IS NULL OR length(subject) <= 60),
  marked_by        uuid REFERENCES app_user (id) ON DELETE SET NULL,
  marked_at        timestamptz NOT NULL DEFAULT now(),
  updated_at       timestamptz NOT NULL DEFAULT now(),
  UNIQUE (class_section_id, date, period)
);
CREATE INDEX attendance_session_date_idx ON attendance_session (organization_id, date);

-- P present, A absent, L late, H half day, LV on approved leave.
CREATE TABLE attendance_mark (
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  session_id      uuid NOT NULL REFERENCES attendance_session (id) ON DELETE CASCADE,
  student_id      uuid NOT NULL REFERENCES student (id) ON DELETE CASCADE,
  status          text NOT NULL CHECK (status IN ('P', 'A', 'L', 'H', 'LV')),
  reason          text CHECK (reason IS NULL OR length(reason) <= 60),
  note            text CHECK (note IS NULL OR length(note) <= 300),
  PRIMARY KEY (session_id, student_id)
);
CREATE INDEX attendance_mark_student_idx ON attendance_mark (student_id);

-- Guardian alerts already sent (one per student, day and kind), linked to the hub broadcast.
CREATE TABLE attendance_alert (
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  student_id      uuid NOT NULL REFERENCES student (id) ON DELETE CASCADE,
  date            date NOT NULL,
  kind            text NOT NULL CHECK (kind IN ('ABSENT', 'LATE')),
  broadcast_id    uuid,
  skipped_reason  text,
  created_at      timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (student_id, date, kind)
);

CREATE TABLE leave_request (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  student_id      uuid NOT NULL REFERENCES student (id) ON DELETE CASCADE,
  guardian_id     uuid REFERENCES guardian (id) ON DELETE SET NULL,
  from_date       date NOT NULL,
  to_date         date NOT NULL,
  reason          text NOT NULL CHECK (length(reason) BETWEEN 1 AND 500),
  source          text NOT NULL DEFAULT 'PORTAL' CHECK (source IN ('PORTAL', 'STAFF')),
  status          text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
  decided_by      uuid REFERENCES app_user (id) ON DELETE SET NULL,
  decided_at      timestamptz,
  decision_note   text CHECK (decision_note IS NULL OR length(decision_note) <= 300),
  created_by      uuid REFERENCES app_user (id) ON DELETE SET NULL,
  created_at      timestamptz NOT NULL DEFAULT now(),
  CHECK (to_date >= from_date AND to_date - from_date <= 60)
);
CREATE INDEX leave_request_student_idx ON leave_request (student_id, from_date);
CREATE INDEX leave_request_status_idx ON leave_request (organization_id, status);

-- Changes to a past day's attendance by a teacher wait for approval.
CREATE TABLE attendance_correction (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  session_id      uuid NOT NULL REFERENCES attendance_session (id) ON DELETE CASCADE,
  student_id      uuid NOT NULL REFERENCES student (id) ON DELETE CASCADE,
  from_status     text CHECK (from_status IS NULL OR from_status IN ('P', 'A', 'L', 'H', 'LV')),
  to_status       text NOT NULL CHECK (to_status IN ('P', 'A', 'L', 'H', 'LV')),
  to_reason       text CHECK (to_reason IS NULL OR length(to_reason) <= 60),
  reason          text NOT NULL CHECK (length(reason) BETWEEN 1 AND 300),
  status          text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
  requested_by    uuid REFERENCES app_user (id) ON DELETE SET NULL,
  decided_by      uuid REFERENCES app_user (id) ON DELETE SET NULL,
  decided_at      timestamptz,
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX attendance_correction_status_idx ON attendance_correction (organization_id, status);
CREATE UNIQUE INDEX attendance_correction_pending_uq ON attendance_correction (session_id, student_id)
  WHERE status = 'PENDING';

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['attendance_settings', 'attendance_teacher', 'attendance_session', 'attendance_mark',
                           'attendance_alert', 'leave_request', 'attendance_correction'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format(
      'CREATE POLICY %I ON %I USING (organization_id = sb_current_org() OR sb_is_system())'
      ' WITH CHECK (organization_id = sb_current_org() OR sb_is_system())', t || '_tenant', t);
  END LOOP;
END
$$;

-- Attendance alerts are hub broadcasts addressed to specific contacts, kept out of the circulars list.
ALTER TABLE comms_broadcast ADD COLUMN kind text NOT NULL DEFAULT 'CIRCULAR' CHECK (kind IN ('CIRCULAR', 'ALERT'));
ALTER TABLE comms_broadcast ADD COLUMN audience_contacts uuid[] NOT NULL DEFAULT '{}';

-- ---------------------------------------------------------------------------------------------
-- Catalogue and plans: daily attendance, alerts, leave, corrections and registers on every ERP
-- plan; period-wise attendance, reason codes and chronic-absence alerts on Standard and Global.
-- ---------------------------------------------------------------------------------------------
INSERT INTO module_catalogue (code, name, description, features) VALUES
  ('ATTENDANCE', 'Attendance & leave', 'Daily or period-wise attendance on a phone, guardian alerts, leave, corrections, registers',
   ARRAY['daily', 'alerts', 'leave', 'corrections', 'registers', 'periods', 'reasons', 'concerns']);

UPDATE plan p
   SET entitlements = p.entitlements || jsonb_build_array(jsonb_build_object(
         'module', 'ATTENDANCE',
         'depth', CASE p.code WHEN 'LITE' THEN 'LITE' WHEN 'STANDARD' THEN 'STANDARD' ELSE 'ADVANCED' END,
         'features', CASE p.code WHEN 'LITE' THEN '["daily", "alerts", "leave", "corrections", "registers"]'::jsonb
                     ELSE '["daily", "alerts", "leave", "corrections", "registers", "periods", "reasons", "concerns"]'::jsonb END,
         'limits', '{}'::jsonb))
 WHERE p.code IN ('LITE', 'STANDARD', 'GLOBAL');

SELECT set_config('app.system', 'on', true);
INSERT INTO entitlement (organization_id, module_code, plan_code, depth, features, limits)
SELECT DISTINCT ON (en.organization_id) en.organization_id, 'ATTENDANCE', en.plan_code,
       CASE en.plan_code WHEN 'LITE' THEN 'LITE' WHEN 'STANDARD' THEN 'STANDARD' ELSE 'ADVANCED' END,
       CASE en.plan_code WHEN 'LITE' THEN ARRAY['daily', 'alerts', 'leave', 'corrections', 'registers']
            ELSE ARRAY['daily', 'alerts', 'leave', 'corrections', 'registers', 'periods', 'reasons', 'concerns'] END,
       '{}'::jsonb
  FROM entitlement en
 WHERE en.plan_code IN ('LITE', 'STANDARD', 'GLOBAL')
ON CONFLICT (organization_id, module_code) DO NOTHING;
SELECT set_config('app.system', '', true);
