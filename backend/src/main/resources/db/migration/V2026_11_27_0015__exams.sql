-- M11 Exams & report cards (small + medium depth): subjects per class (from a board preset),
-- a grade scale and pass mark, exams with components (e.g. theory 80 + internal 20) and a weight
-- in the year's result, a paper timetable and hall tickets, marks entry by subject teachers with
-- absent / medical codes, co-scholastic grades and class-teacher remarks, submission and approval
-- of each class's marks before results are published, and report cards for staff and parents.

ALTER TABLE role_assignment DROP CONSTRAINT role_assignment_role_check;
ALTER TABLE role_assignment ADD CONSTRAINT role_assignment_role_check
  CHECK (role IN ('PLATFORM_SUPER_ADMIN', 'SCHOOL_ADMIN', 'CONTENT_EDITOR', 'CONTENT_APPROVER', 'VIEWER',
                  'ADMISSIONS_COUNSELLOR', 'FRONT_OFFICE', 'TEACHER', 'EXAM_CELL'));

CREATE TABLE exam_settings (
  organization_id uuid PRIMARY KEY REFERENCES organization (id) ON DELETE CASCADE,
  board           text NOT NULL DEFAULT 'CBSE' CHECK (board IN ('CBSE', 'ICSE', 'STATE', 'OTHER')),
  pass_percent    integer NOT NULL DEFAULT 33 CHECK (pass_percent BETWEEN 0 AND 100),
  -- [{"min": 91, "grade": "A1", "point": 10}, ...] highest first
  grade_bands     jsonb NOT NULL DEFAULT '[{"min":91,"grade":"A1","point":10},{"min":81,"grade":"A2","point":9},{"min":71,"grade":"B1","point":8},{"min":61,"grade":"B2","point":7},{"min":51,"grade":"C1","point":6},{"min":41,"grade":"C2","point":5},{"min":33,"grade":"D","point":4},{"min":0,"grade":"E","point":0}]',
  co_grades       text[] NOT NULL DEFAULT ARRAY['A', 'B', 'C'],
  principal_name  text CHECK (principal_name IS NULL OR length(principal_name) <= 120),
  show_rank       boolean NOT NULL DEFAULT false,
  updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE subject (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  academic_year   text NOT NULL CHECK (academic_year ~ '^\d{4}-\d{2}$'),
  grade           text NOT NULL CHECK (length(grade) BETWEEN 1 AND 40),
  name            text NOT NULL CHECK (length(name) BETWEEN 1 AND 80),
  kind            text NOT NULL DEFAULT 'SCHOLASTIC' CHECK (kind IN ('SCHOLASTIC', 'CO_SCHOLASTIC')),
  sort_order      integer NOT NULL DEFAULT 0,
  UNIQUE (organization_id, academic_year, grade, name)
);

-- Who enters marks for a subject in a section.
CREATE TABLE subject_teacher (
  organization_id  uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  class_section_id uuid NOT NULL REFERENCES class_section (id) ON DELETE CASCADE,
  subject_id       uuid NOT NULL REFERENCES subject (id) ON DELETE CASCADE,
  user_id          uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
  PRIMARY KEY (class_section_id, subject_id, user_id)
);
CREATE INDEX subject_teacher_user_idx ON subject_teacher (user_id);

CREATE TABLE exam (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  academic_year   text NOT NULL CHECK (academic_year ~ '^\d{4}-\d{2}$'),
  name            text NOT NULL CHECK (length(name) BETWEEN 1 AND 80),
  term            text CHECK (term IS NULL OR length(term) <= 40),
  grades          text[] NOT NULL,
  -- [{"key": "TH", "label": "Theory", "max": 80}, {"key": "IA", "label": "Internal assessment", "max": 20}]
  components      jsonb NOT NULL,
  weight          integer NOT NULL DEFAULT 0 CHECK (weight BETWEEN 0 AND 100),
  start_date      date,
  end_date        date,
  status          text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'MARKS_OPEN', 'PUBLISHED')),
  published_at    timestamptz,
  published_by    uuid REFERENCES app_user (id) ON DELETE SET NULL,
  sort_order      integer NOT NULL DEFAULT 0,
  created_at      timestamptz NOT NULL DEFAULT now(),
  UNIQUE (organization_id, academic_year, name)
);

-- The timetable: which subjects a grade sits, when.
CREATE TABLE exam_paper (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  exam_id         uuid NOT NULL REFERENCES exam (id) ON DELETE CASCADE,
  subject_id      uuid NOT NULL REFERENCES subject (id) ON DELETE CASCADE,
  date            date,
  start_time      time,
  duration_min    integer CHECK (duration_min IS NULL OR duration_min BETWEEN 10 AND 300),
  UNIQUE (exam_id, subject_id)
);

-- Marks: {"TH": 64.5, "IA": 18} for scholastic subjects, or a letter grade for co-scholastic ones.
CREATE TABLE exam_mark (
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  exam_id         uuid NOT NULL REFERENCES exam (id) ON DELETE CASCADE,
  subject_id      uuid NOT NULL REFERENCES subject (id) ON DELETE CASCADE,
  student_id      uuid NOT NULL REFERENCES student (id) ON DELETE CASCADE,
  marks           jsonb NOT NULL DEFAULT '{}',
  grade           text CHECK (grade IS NULL OR length(grade) <= 4),
  code            text CHECK (code IS NULL OR code IN ('AB', 'ML')),
  updated_by      uuid REFERENCES app_user (id) ON DELETE SET NULL,
  updated_at      timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (exam_id, subject_id, student_id)
);
CREATE INDEX exam_mark_student_idx ON exam_mark (student_id);

-- Marks of one subject in one section: entered -> submitted -> approved (moderation).
CREATE TABLE exam_entry (
  organization_id  uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  exam_id          uuid NOT NULL REFERENCES exam (id) ON DELETE CASCADE,
  subject_id       uuid NOT NULL REFERENCES subject (id) ON DELETE CASCADE,
  class_section_id uuid NOT NULL REFERENCES class_section (id) ON DELETE CASCADE,
  status           text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'SUBMITTED', 'APPROVED')),
  submitted_by     uuid REFERENCES app_user (id) ON DELETE SET NULL,
  submitted_at     timestamptz,
  approved_by      uuid REFERENCES app_user (id) ON DELETE SET NULL,
  approved_at      timestamptz,
  note             text CHECK (note IS NULL OR length(note) <= 300),
  PRIMARY KEY (exam_id, subject_id, class_section_id)
);

CREATE TABLE exam_remark (
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  exam_id         uuid NOT NULL REFERENCES exam (id) ON DELETE CASCADE,
  student_id      uuid NOT NULL REFERENCES student (id) ON DELETE CASCADE,
  remark          text NOT NULL CHECK (length(remark) BETWEEN 1 AND 500),
  updated_by      uuid REFERENCES app_user (id) ON DELETE SET NULL,
  updated_at      timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (exam_id, student_id)
);

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['exam_settings', 'subject', 'subject_teacher', 'exam', 'exam_paper', 'exam_mark',
                           'exam_entry', 'exam_remark'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format(
      'CREATE POLICY %I ON %I USING (organization_id = sb_current_org() OR sb_is_system())'
      ' WITH CHECK (organization_id = sb_current_org() OR sb_is_system())', t || '_tenant', t);
  END LOOP;
END
$$;

-- ---------------------------------------------------------------------------------------------
-- Catalogue and plans: marks, grading, co-scholastic, report cards and publishing on every ERP
-- plan; timetable / hall tickets, moderation and subject analysis on Standard and Global.
-- ---------------------------------------------------------------------------------------------
INSERT INTO module_catalogue (code, name, description, features) VALUES
  ('EXAMS', 'Exams & report cards', 'Subjects, exams with components and weights, marks entry, grading, report cards, hall tickets, moderation',
   ARRAY['marks', 'grading', 'coscholastic', 'report_cards', 'publish', 'timetable', 'hall_tickets', 'moderation', 'analysis']);

UPDATE plan p
   SET entitlements = p.entitlements || jsonb_build_array(jsonb_build_object(
         'module', 'EXAMS',
         'depth', CASE p.code WHEN 'LITE' THEN 'LITE' WHEN 'STANDARD' THEN 'STANDARD' ELSE 'ADVANCED' END,
         'features', CASE p.code WHEN 'LITE' THEN '["marks", "grading", "coscholastic", "report_cards", "publish"]'::jsonb
                     ELSE '["marks", "grading", "coscholastic", "report_cards", "publish", "timetable", "hall_tickets", "moderation", "analysis"]'::jsonb END,
         'limits', '{}'::jsonb))
 WHERE p.code IN ('LITE', 'STANDARD', 'GLOBAL');

SELECT set_config('app.system', 'on', true);
INSERT INTO entitlement (organization_id, module_code, plan_code, depth, features, limits)
SELECT DISTINCT ON (en.organization_id) en.organization_id, 'EXAMS', en.plan_code,
       CASE en.plan_code WHEN 'LITE' THEN 'LITE' WHEN 'STANDARD' THEN 'STANDARD' ELSE 'ADVANCED' END,
       CASE en.plan_code WHEN 'LITE' THEN ARRAY['marks', 'grading', 'coscholastic', 'report_cards', 'publish']
            ELSE ARRAY['marks', 'grading', 'coscholastic', 'report_cards', 'publish', 'timetable', 'hall_tickets', 'moderation', 'analysis'] END,
       '{}'::jsonb
  FROM entitlement en
 WHERE en.plan_code IN ('LITE', 'STANDARD', 'GLOBAL')
ON CONFLICT (organization_id, module_code) DO NOTHING;
SELECT set_config('app.system', '', true);
