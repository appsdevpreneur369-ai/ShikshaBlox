-- M08 Timetable & substitutions (small + medium depth): the school's working days and bell
-- schedule, a weekly grid per section (subject, teacher, room) where a teacher or room can't be in
-- two places at once, weekly subject loads with an automatic generator, teacher timetables, daily
-- substitutions with suggested free teachers, and the class timetable in the parent portal.

CREATE TABLE timetable_settings (
  organization_id uuid PRIMARY KEY REFERENCES organization (id) ON DELETE CASCADE,
  days            integer[] NOT NULL DEFAULT ARRAY[1, 2, 3, 4, 5, 6],   -- ISO weekdays
  -- [{"label": "Period 1", "start": "08:30", "end": "09:10", "break": false}, ...]
  periods         jsonb NOT NULL DEFAULT '[
    {"label":"Period 1","start":"08:30","end":"09:10","break":false},
    {"label":"Period 2","start":"09:10","end":"09:50","break":false},
    {"label":"Period 3","start":"09:50","end":"10:30","break":false},
    {"label":"Short break","start":"10:30","end":"10:45","break":true},
    {"label":"Period 4","start":"10:45","end":"11:25","break":false},
    {"label":"Period 5","start":"11:25","end":"12:05","break":false},
    {"label":"Lunch","start":"12:05","end":"12:45","break":true},
    {"label":"Period 6","start":"12:45","end":"13:25","break":false},
    {"label":"Period 7","start":"13:25","end":"14:05","break":false},
    {"label":"Period 8","start":"14:05","end":"14:45","break":false}]',
  max_same_subject_per_day integer NOT NULL DEFAULT 2 CHECK (max_same_subject_per_day BETWEEN 1 AND 4),
  updated_at      timestamptz NOT NULL DEFAULT now()
);

-- How many periods a week each subject gets in a section, and who teaches it (generator input).
CREATE TABLE timetable_load (
  organization_id  uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  class_section_id uuid NOT NULL REFERENCES class_section (id) ON DELETE CASCADE,
  subject_id       uuid NOT NULL REFERENCES subject (id) ON DELETE CASCADE,
  periods_per_week integer NOT NULL CHECK (periods_per_week BETWEEN 0 AND 20),
  teacher_id       uuid REFERENCES app_user (id) ON DELETE SET NULL,
  PRIMARY KEY (class_section_id, subject_id)
);

CREATE TABLE timetable_slot (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id  uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  class_section_id uuid NOT NULL REFERENCES class_section (id) ON DELETE CASCADE,
  day              integer NOT NULL CHECK (day BETWEEN 1 AND 7),
  period           integer NOT NULL CHECK (period BETWEEN 0 AND 15),   -- index into settings.periods
  subject_id       uuid REFERENCES subject (id) ON DELETE SET NULL,
  label            text CHECK (label IS NULL OR length(label) <= 60),  -- e.g. "Library", "Games"
  teacher_id       uuid REFERENCES app_user (id) ON DELETE SET NULL,
  room             text CHECK (room IS NULL OR length(room) <= 30),
  UNIQUE (class_section_id, day, period),
  CHECK (subject_id IS NOT NULL OR label IS NOT NULL)
);
-- A teacher or a room can be in one place at a time.
CREATE UNIQUE INDEX timetable_teacher_uq ON timetable_slot (organization_id, teacher_id, day, period) WHERE teacher_id IS NOT NULL;
CREATE UNIQUE INDEX timetable_room_uq ON timetable_slot (organization_id, lower(room), day, period) WHERE room IS NOT NULL;

CREATE TABLE teacher_absence (
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  date            date NOT NULL,
  user_id         uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
  note            text CHECK (note IS NULL OR length(note) <= 120),
  created_by      uuid REFERENCES app_user (id) ON DELETE SET NULL,
  PRIMARY KEY (organization_id, date, user_id)
);

CREATE TABLE substitution (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id   uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  date              date NOT NULL,
  class_section_id  uuid NOT NULL REFERENCES class_section (id) ON DELETE CASCADE,
  period            integer NOT NULL,
  absent_teacher_id uuid REFERENCES app_user (id) ON DELETE SET NULL,
  substitute_id     uuid REFERENCES app_user (id) ON DELETE SET NULL,
  note              text CHECK (note IS NULL OR length(note) <= 120),
  created_by        uuid REFERENCES app_user (id) ON DELETE SET NULL,
  created_at        timestamptz NOT NULL DEFAULT now(),
  UNIQUE (class_section_id, date, period)
);
CREATE UNIQUE INDEX substitution_teacher_uq ON substitution (organization_id, substitute_id, date, period) WHERE substitute_id IS NOT NULL;

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['timetable_settings', 'timetable_load', 'timetable_slot', 'teacher_absence', 'substitution'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format(
      'CREATE POLICY %I ON %I USING (organization_id = sb_current_org() OR sb_is_system())'
      ' WITH CHECK (organization_id = sb_current_org() OR sb_is_system())', t || '_tenant', t);
  END LOOP;
END
$$;

-- ---------------------------------------------------------------------------------------------
-- Catalogue and plans: the grid with clash checks, teacher timetables and the parent view on
-- every ERP plan; the generator and substitutions on Standard and Global.
-- ---------------------------------------------------------------------------------------------
INSERT INTO module_catalogue (code, name, description, features) VALUES
  ('TIMETABLE', 'Timetable', 'Bell schedule, class and teacher timetables with clash checks, generator, substitutions',
   ARRAY['grid', 'clash_check', 'timetable_parent_view', 'generator', 'substitutions']);

UPDATE plan p
   SET entitlements = p.entitlements || jsonb_build_array(jsonb_build_object(
         'module', 'TIMETABLE',
         'depth', CASE p.code WHEN 'LITE' THEN 'LITE' WHEN 'STANDARD' THEN 'STANDARD' ELSE 'ADVANCED' END,
         'features', CASE p.code WHEN 'LITE' THEN '["grid", "clash_check", "timetable_parent_view"]'::jsonb
                     ELSE '["grid", "clash_check", "timetable_parent_view", "generator", "substitutions"]'::jsonb END,
         'limits', '{}'::jsonb))
 WHERE p.code IN ('LITE', 'STANDARD', 'GLOBAL');

SELECT set_config('app.system', 'on', true);
INSERT INTO entitlement (organization_id, module_code, plan_code, depth, features, limits)
SELECT DISTINCT ON (en.organization_id) en.organization_id, 'TIMETABLE', en.plan_code,
       CASE en.plan_code WHEN 'LITE' THEN 'LITE' WHEN 'STANDARD' THEN 'STANDARD' ELSE 'ADVANCED' END,
       CASE en.plan_code WHEN 'LITE' THEN ARRAY['grid', 'clash_check', 'timetable_parent_view']
            ELSE ARRAY['grid', 'clash_check', 'timetable_parent_view', 'generator', 'substitutions'] END,
       '{}'::jsonb
  FROM entitlement en
 WHERE en.plan_code IN ('LITE', 'STANDARD', 'GLOBAL')
ON CONFLICT (organization_id, module_code) DO NOTHING;
SELECT set_config('app.system', '', true);
