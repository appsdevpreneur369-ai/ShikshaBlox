-- M10 Homework & assignments (small + medium depth): teachers post homework to one or more
-- sections with attachments and a due date, parents get an alert and see it in the portal;
-- with submissions (Standard) parents hand in work for their child (text and a file), teachers
-- mark with points or a rubric, give feedback or return it for redoing; a late policy applies.

CREATE TABLE homework (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id   uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  class_section_id  uuid NOT NULL REFERENCES class_section (id) ON DELETE CASCADE,
  subject_id        uuid REFERENCES subject (id) ON DELETE SET NULL,
  subject_name      text CHECK (subject_name IS NULL OR length(subject_name) <= 80),
  title             text NOT NULL CHECK (length(title) BETWEEN 1 AND 160),
  details           text CHECK (details IS NULL OR length(details) <= 4000),
  assigned_on       date NOT NULL,
  due_on            date NOT NULL,
  allow_submissions boolean NOT NULL DEFAULT false,
  max_points        numeric(6, 1) CHECK (max_points IS NULL OR (max_points > 0 AND max_points <= 1000)),
  -- [{"criterion": "Neatness", "points": 5}, ...]
  rubric            jsonb NOT NULL DEFAULT '[]',
  late_policy       text NOT NULL DEFAULT 'ACCEPT' CHECK (late_policy IN ('ACCEPT', 'CLOSE')),
  notify_parents    boolean NOT NULL DEFAULT true,
  created_by        uuid REFERENCES app_user (id) ON DELETE SET NULL,
  created_at        timestamptz NOT NULL DEFAULT now(),
  updated_at        timestamptz NOT NULL DEFAULT now(),
  CHECK (due_on >= assigned_on)
);
CREATE INDEX homework_section_idx ON homework (class_section_id, due_on DESC);

CREATE TABLE homework_file (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id   uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  homework_id       uuid NOT NULL REFERENCES homework (id) ON DELETE CASCADE,
  original_filename text NOT NULL,
  content_type      text NOT NULL,
  size_bytes        bigint NOT NULL,
  storage_key       text NOT NULL,
  created_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX homework_file_idx ON homework_file (homework_id);

CREATE TABLE homework_submission (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id   uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  homework_id       uuid NOT NULL REFERENCES homework (id) ON DELETE CASCADE,
  student_id        uuid NOT NULL REFERENCES student (id) ON DELETE CASCADE,
  guardian_id       uuid REFERENCES guardian (id) ON DELETE SET NULL,
  text              text CHECK (text IS NULL OR length(text) <= 4000),
  file_name         text,
  content_type      text,
  size_bytes        bigint,
  storage_key       text,
  submitted_at      timestamptz NOT NULL DEFAULT now(),
  late              boolean NOT NULL DEFAULT false,
  status            text NOT NULL DEFAULT 'SUBMITTED' CHECK (status IN ('SUBMITTED', 'RETURNED', 'GRADED')),
  points            numeric(6, 1),
  rubric_scores     jsonb NOT NULL DEFAULT '{}',
  feedback          text CHECK (feedback IS NULL OR length(feedback) <= 2000),
  graded_by         uuid REFERENCES app_user (id) ON DELETE SET NULL,
  graded_at         timestamptz,
  UNIQUE (homework_id, student_id)
);

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['homework', 'homework_file', 'homework_submission'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format(
      'CREATE POLICY %I ON %I USING (organization_id = sb_current_org() OR sb_is_system())'
      ' WITH CHECK (organization_id = sb_current_org() OR sb_is_system())', t || '_tenant', t);
  END LOOP;
END
$$;

-- ---------------------------------------------------------------------------------------------
-- Catalogue and plans: posting with attachments and parent alerts on every ERP plan; online
-- submissions, rubrics and feedback on Standard and Global. Attachments count towards storage.
-- ---------------------------------------------------------------------------------------------
INSERT INTO module_catalogue (code, name, description, features) VALUES
  ('HOMEWORK', 'Homework', 'Homework with attachments and parent alerts; submissions, rubrics and feedback',
   ARRAY['homework_post', 'attachments', 'homework_alerts', 'submissions', 'rubrics', 'feedback']);

UPDATE plan p
   SET entitlements = p.entitlements || jsonb_build_array(jsonb_build_object(
         'module', 'HOMEWORK',
         'depth', CASE p.code WHEN 'LITE' THEN 'LITE' WHEN 'STANDARD' THEN 'STANDARD' ELSE 'ADVANCED' END,
         'features', CASE p.code WHEN 'LITE' THEN '["homework_post", "attachments", "homework_alerts"]'::jsonb
                     ELSE '["homework_post", "attachments", "homework_alerts", "submissions", "rubrics", "feedback"]'::jsonb END,
         'limits', '{}'::jsonb))
 WHERE p.code IN ('LITE', 'STANDARD', 'GLOBAL');

SELECT set_config('app.system', 'on', true);
INSERT INTO entitlement (organization_id, module_code, plan_code, depth, features, limits)
SELECT DISTINCT ON (en.organization_id) en.organization_id, 'HOMEWORK', en.plan_code,
       CASE en.plan_code WHEN 'LITE' THEN 'LITE' WHEN 'STANDARD' THEN 'STANDARD' ELSE 'ADVANCED' END,
       CASE en.plan_code WHEN 'LITE' THEN ARRAY['homework_post', 'attachments', 'homework_alerts']
            ELSE ARRAY['homework_post', 'attachments', 'homework_alerts', 'submissions', 'rubrics', 'feedback'] END,
       '{}'::jsonb
  FROM entitlement en
 WHERE en.plan_code IN ('LITE', 'STANDARD', 'GLOBAL')
ON CONFLICT (organization_id, module_code) DO NOTHING;
SELECT set_config('app.system', '', true);
