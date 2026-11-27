-- M31 Early years & pre-primary: a daily diary per child (arrival, meals, nap, toilet, mood,
-- activities), developmental milestones grouped by Foundational Stage domains with a status per
-- child, observation notes with an optional photo (private storage, shared only with that child's
-- family), learning-story reports, authorised pickup adults with a photo, and a daily parent feed.

CREATE TABLE ey_diary (
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  student_id      uuid NOT NULL REFERENCES student (id) ON DELETE CASCADE,
  date            date NOT NULL,
  arrival         time,
  meals           jsonb NOT NULL DEFAULT '{}',      -- {"Snack": "ALL", "Lunch": "SOME"}
  nap_from        time,
  nap_to          time,
  toilet          integer CHECK (toilet IS NULL OR toilet BETWEEN 0 AND 20),
  mood            text CHECK (mood IS NULL OR mood IN ('HAPPY', 'CALM', 'TIRED', 'UPSET')),
  activities      text CHECK (activities IS NULL OR length(activities) <= 500),
  note            text CHECK (note IS NULL OR length(note) <= 500),
  updated_by      uuid REFERENCES app_user (id) ON DELETE SET NULL,
  updated_at      timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (student_id, date)
);

CREATE TABLE ey_milestone (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  domain          text NOT NULL CHECK (length(domain) BETWEEN 1 AND 60),
  statement       text NOT NULL CHECK (length(statement) BETWEEN 1 AND 200),
  sort_order      integer NOT NULL DEFAULT 0,
  active          boolean NOT NULL DEFAULT true
);

CREATE TABLE ey_milestone_status (
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  student_id      uuid NOT NULL REFERENCES student (id) ON DELETE CASCADE,
  milestone_id    uuid NOT NULL REFERENCES ey_milestone (id) ON DELETE CASCADE,
  status          text NOT NULL CHECK (status IN ('NOT_YET', 'EMERGING', 'ACHIEVED')),
  updated_on      date NOT NULL,
  updated_by      uuid REFERENCES app_user (id) ON DELETE SET NULL,
  PRIMARY KEY (student_id, milestone_id)
);

CREATE TABLE ey_observation (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  student_id      uuid NOT NULL REFERENCES student (id) ON DELETE CASCADE,
  observed_on     date NOT NULL,
  domain          text CHECK (domain IS NULL OR length(domain) <= 60),
  note            text NOT NULL CHECK (length(note) BETWEEN 1 AND 2000),
  photo_key       text,
  photo_type      text,
  share_with_family boolean NOT NULL DEFAULT true,
  created_by      uuid REFERENCES app_user (id) ON DELETE SET NULL,
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ey_observation_student_idx ON ey_observation (student_id, observed_on DESC);

CREATE TABLE ey_pickup (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  student_id      uuid NOT NULL REFERENCES student (id) ON DELETE CASCADE,
  name            text NOT NULL CHECK (length(name) BETWEEN 1 AND 120),
  relation        text CHECK (relation IS NULL OR length(relation) <= 60),
  phone           text CHECK (phone IS NULL OR length(phone) <= 20),
  photo_key       text,
  photo_type      text,
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ey_pickup_student_idx ON ey_pickup (student_id);

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['ey_diary', 'ey_milestone', 'ey_milestone_status', 'ey_observation', 'ey_pickup'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format(
      'CREATE POLICY %I ON %I USING (organization_id = sb_current_org() OR sb_is_system())'
      ' WITH CHECK (organization_id = sb_current_org() OR sb_is_system())', t || '_tenant', t);
  END LOOP;
END
$$;

INSERT INTO module_catalogue (code, name, description, features) VALUES
  ('EARLY_YEARS', 'Early years', 'Daily diary, milestones, observations with photos, learning stories, pickup adults, parent feed',
   ARRAY['diary', 'milestones', 'ey_observations', 'parent_feed', 'photos', 'learning_stories', 'pickup']);

UPDATE plan p
   SET entitlements = p.entitlements || jsonb_build_array(jsonb_build_object(
         'module', 'EARLY_YEARS',
         'depth', CASE p.code WHEN 'LITE' THEN 'LITE' WHEN 'STANDARD' THEN 'STANDARD' ELSE 'ADVANCED' END,
         'features', CASE p.code WHEN 'LITE' THEN '["diary", "milestones", "ey_observations", "parent_feed"]'::jsonb
                     ELSE '["diary", "milestones", "ey_observations", "parent_feed", "photos", "learning_stories", "pickup"]'::jsonb END,
         'limits', '{}'::jsonb))
 WHERE p.code IN ('LITE', 'STANDARD', 'GLOBAL');

SELECT set_config('app.system', 'on', true);
INSERT INTO entitlement (organization_id, module_code, plan_code, depth, features, limits)
SELECT DISTINCT ON (en.organization_id) en.organization_id, 'EARLY_YEARS', en.plan_code,
       CASE en.plan_code WHEN 'LITE' THEN 'LITE' WHEN 'STANDARD' THEN 'STANDARD' ELSE 'ADVANCED' END,
       CASE en.plan_code WHEN 'LITE' THEN ARRAY['diary', 'milestones', 'ey_observations', 'parent_feed']
            ELSE ARRAY['diary', 'milestones', 'ey_observations', 'parent_feed', 'photos', 'learning_stories', 'pickup'] END,
       '{}'::jsonb
  FROM entitlement en
 WHERE en.plan_code IN ('LITE', 'STANDARD', 'GLOBAL')
ON CONFLICT (organization_id, module_code) DO NOTHING;
SELECT set_config('app.system', '', true);
