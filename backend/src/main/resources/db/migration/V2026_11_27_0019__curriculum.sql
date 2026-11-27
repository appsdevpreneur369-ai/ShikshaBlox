-- M09 Curriculum & lesson planning (small + medium depth): the syllabus per subject (units with
-- topics and the month each unit is planned for), topic coverage per section with a completion
-- meter and "behind plan" flags, lesson plans written by teachers and reviewed by the academic
-- coordinator, and classroom observations that teachers acknowledge with a reflection.

CREATE TABLE syllabus_unit (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  subject_id      uuid NOT NULL REFERENCES subject (id) ON DELETE CASCADE,
  title           text NOT NULL CHECK (length(title) BETWEEN 1 AND 120),
  planned_month   integer CHECK (planned_month IS NULL OR planned_month BETWEEN 1 AND 12),
  sort_order      integer NOT NULL DEFAULT 0
);
CREATE INDEX syllabus_unit_subject_idx ON syllabus_unit (subject_id, sort_order);

CREATE TABLE syllabus_topic (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  unit_id         uuid NOT NULL REFERENCES syllabus_unit (id) ON DELETE CASCADE,
  title           text NOT NULL CHECK (length(title) BETWEEN 1 AND 160),
  sort_order      integer NOT NULL DEFAULT 0
);
CREATE INDEX syllabus_topic_unit_idx ON syllabus_topic (unit_id, sort_order);

CREATE TABLE topic_coverage (
  organization_id  uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  topic_id         uuid NOT NULL REFERENCES syllabus_topic (id) ON DELETE CASCADE,
  class_section_id uuid NOT NULL REFERENCES class_section (id) ON DELETE CASCADE,
  covered_on       date NOT NULL,
  covered_by       uuid REFERENCES app_user (id) ON DELETE SET NULL,
  note             text CHECK (note IS NULL OR length(note) <= 200),
  PRIMARY KEY (topic_id, class_section_id)
);

CREATE TABLE lesson_plan (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id  uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  class_section_id uuid NOT NULL REFERENCES class_section (id) ON DELETE CASCADE,
  subject_id       uuid REFERENCES subject (id) ON DELETE SET NULL,
  topic_id         uuid REFERENCES syllabus_topic (id) ON DELETE SET NULL,
  planned_for      date NOT NULL,
  title            text NOT NULL CHECK (length(title) BETWEEN 1 AND 160),
  objectives       text CHECK (objectives IS NULL OR length(objectives) <= 2000),
  activities       text CHECK (activities IS NULL OR length(activities) <= 4000),
  resources        text CHECK (resources IS NULL OR length(resources) <= 1000),
  assessment       text CHECK (assessment IS NULL OR length(assessment) <= 1000),
  status           text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'SUBMITTED', 'REVIEWED')),
  review_note      text CHECK (review_note IS NULL OR length(review_note) <= 1000),
  reviewed_by      uuid REFERENCES app_user (id) ON DELETE SET NULL,
  reviewed_at      timestamptz,
  created_by       uuid REFERENCES app_user (id) ON DELETE SET NULL,
  created_at       timestamptz NOT NULL DEFAULT now(),
  updated_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX lesson_plan_section_idx ON lesson_plan (class_section_id, planned_for DESC);

CREATE TABLE classroom_observation (
  id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id    uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  teacher_id         uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
  class_section_id   uuid REFERENCES class_section (id) ON DELETE SET NULL,
  subject_id         uuid REFERENCES subject (id) ON DELETE SET NULL,
  observed_on        date NOT NULL,
  -- {"Planning": 3, "Engagement": 4, ...} on a 1-4 scale
  scores             jsonb NOT NULL DEFAULT '{}',
  strengths          text CHECK (strengths IS NULL OR length(strengths) <= 2000),
  suggestions        text CHECK (suggestions IS NULL OR length(suggestions) <= 2000),
  observer_id        uuid REFERENCES app_user (id) ON DELETE SET NULL,
  acknowledged_at    timestamptz,
  reflection         text CHECK (reflection IS NULL OR length(reflection) <= 2000),
  created_at         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX classroom_observation_teacher_idx ON classroom_observation (teacher_id, observed_on DESC);

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['syllabus_unit', 'syllabus_topic', 'topic_coverage', 'lesson_plan', 'classroom_observation'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format(
      'CREATE POLICY %I ON %I USING (organization_id = sb_current_org() OR sb_is_system())'
      ' WITH CHECK (organization_id = sb_current_org() OR sb_is_system())', t || '_tenant', t);
  END LOOP;
END
$$;

INSERT INTO module_catalogue (code, name, description, features) VALUES
  ('CURRICULUM', 'Curriculum & lesson plans', 'Syllabus, coverage meter, lesson plans with review, classroom observations',
   ARRAY['syllabus', 'coverage', 'lesson_plans', 'observations']);

UPDATE plan p
   SET entitlements = p.entitlements || jsonb_build_array(jsonb_build_object(
         'module', 'CURRICULUM',
         'depth', CASE p.code WHEN 'LITE' THEN 'LITE' WHEN 'STANDARD' THEN 'STANDARD' ELSE 'ADVANCED' END,
         'features', CASE p.code WHEN 'LITE' THEN '["syllabus", "coverage"]'::jsonb
                     ELSE '["syllabus", "coverage", "lesson_plans", "observations"]'::jsonb END,
         'limits', '{}'::jsonb))
 WHERE p.code IN ('LITE', 'STANDARD', 'GLOBAL');

SELECT set_config('app.system', 'on', true);
INSERT INTO entitlement (organization_id, module_code, plan_code, depth, features, limits)
SELECT DISTINCT ON (en.organization_id) en.organization_id, 'CURRICULUM', en.plan_code,
       CASE en.plan_code WHEN 'LITE' THEN 'LITE' WHEN 'STANDARD' THEN 'STANDARD' ELSE 'ADVANCED' END,
       CASE en.plan_code WHEN 'LITE' THEN ARRAY['syllabus', 'coverage']
            ELSE ARRAY['syllabus', 'coverage', 'lesson_plans', 'observations'] END,
       '{}'::jsonb
  FROM entitlement en
 WHERE en.plan_code IN ('LITE', 'STANDARD', 'GLOBAL')
ON CONFLICT (organization_id, module_code) DO NOTHING;
SELECT set_config('app.system', '', true);
