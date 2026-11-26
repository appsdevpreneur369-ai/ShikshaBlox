-- M26 Surveys, PTM & Helpdesk. Until the parent portal (M12) exists, parents use public pages on
-- the school website: book a PTM slot, raise a helpdesk request (and follow it with the ticket
-- number + phone), answer a survey. Staff work everything in Studio. Records keep the parent's
-- phone so they can be linked to parent accounts later.

-- ---------------------------------------------------------------------------------------------
-- PTM: an event (day) with hosts (teachers / desks); parents book a slot with one host.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE ptm_event (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  title           text NOT NULL CHECK (length(title) BETWEEN 1 AND 160),
  event_date      date NOT NULL,
  start_time      time NOT NULL,
  end_time        time NOT NULL,
  slot_minutes    integer NOT NULL DEFAULT 10 CHECK (slot_minutes BETWEEN 5 AND 60),
  instructions    text CHECK (instructions IS NULL OR length(instructions) <= 1000),
  booking_open    boolean NOT NULL DEFAULT true,
  created_by      uuid REFERENCES app_user (id) ON DELETE SET NULL,
  created_at      timestamptz NOT NULL DEFAULT now(),
  CHECK (end_time > start_time)
);
CREATE INDEX ptm_event_date_idx ON ptm_event (organization_id, event_date);

CREATE TABLE ptm_host (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  event_id        uuid NOT NULL REFERENCES ptm_event (id) ON DELETE CASCADE,
  name            text NOT NULL CHECK (length(name) BETWEEN 1 AND 120),   -- e.g. "Mrs Rao"
  label           text CHECK (label IS NULL OR length(label) <= 120),      -- e.g. "Class 3A, Maths"
  room            text CHECK (room IS NULL OR length(room) <= 60),
  sort_order      integer NOT NULL DEFAULT 0
);
CREATE INDEX ptm_host_event_idx ON ptm_host (event_id, sort_order);

CREATE TABLE ptm_booking (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  host_id         uuid NOT NULL REFERENCES ptm_host (id) ON DELETE CASCADE,
  slot_start      time NOT NULL,
  parent_name     text NOT NULL CHECK (length(parent_name) BETWEEN 1 AND 120),
  phone           text NOT NULL CHECK (length(phone) BETWEEN 6 AND 20),
  student_name    text NOT NULL CHECK (length(student_name) BETWEEN 1 AND 120),
  student_class   text CHECK (student_class IS NULL OR length(student_class) <= 60),
  code            text NOT NULL,                       -- shown to the parent, used to cancel
  status          text NOT NULL DEFAULT 'BOOKED' CHECK (status IN ('BOOKED', 'CANCELLED', 'ATTENDED', 'NO_SHOW')),
  staff_note      text CHECK (staff_note IS NULL OR length(staff_note) <= 1000),
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ptm_booking_slot_uq ON ptm_booking (host_id, slot_start) WHERE status <> 'CANCELLED';
CREATE UNIQUE INDEX ptm_booking_code_uq ON ptm_booking (organization_id, code);

-- ---------------------------------------------------------------------------------------------
-- Helpdesk tickets with a conversation; replies marked public are visible to the parent.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE helpdesk_ticket (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id   uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  ticket_no         text NOT NULL,
  category          text NOT NULL DEFAULT 'OTHER'
                    CHECK (category IN ('FEES', 'TRANSPORT', 'ACADEMICS', 'ADMISSIONS', 'FACILITIES', 'CERTIFICATES',
                                        'COMPLAINT', 'OTHER')),
  subject           text NOT NULL CHECK (length(subject) BETWEEN 1 AND 160),
  requester_name    text NOT NULL CHECK (length(requester_name) BETWEEN 1 AND 120),
  phone             text NOT NULL CHECK (length(phone) BETWEEN 6 AND 20),
  email             text CHECK (email IS NULL OR length(email) <= 200),
  student_name      text CHECK (student_name IS NULL OR length(student_name) <= 120),
  student_class     text CHECK (student_class IS NULL OR length(student_class) <= 60),
  priority          text NOT NULL DEFAULT 'NORMAL' CHECK (priority IN ('LOW', 'NORMAL', 'HIGH')),
  status            text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'IN_PROGRESS', 'WAITING', 'RESOLVED', 'CLOSED')),
  assigned_to       uuid REFERENCES app_user (id) ON DELETE SET NULL,
  source            text NOT NULL DEFAULT 'WEBSITE' CHECK (source IN ('WEBSITE', 'PHONE', 'WALK_IN', 'EMAIL', 'OTHER')),
  due_at            timestamptz NOT NULL,
  first_response_at timestamptz,
  resolved_at       timestamptz,
  created_at        timestamptz NOT NULL DEFAULT now(),
  updated_at        timestamptz NOT NULL DEFAULT now(),
  UNIQUE (organization_id, ticket_no)
);
CREATE INDEX helpdesk_ticket_status_idx ON helpdesk_ticket (organization_id, status, due_at);

CREATE TABLE helpdesk_message (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  ticket_id       uuid NOT NULL REFERENCES helpdesk_ticket (id) ON DELETE CASCADE,
  author_kind     text NOT NULL CHECK (author_kind IN ('REQUESTER', 'STAFF', 'SYSTEM')),
  author_id       uuid REFERENCES app_user (id) ON DELETE SET NULL,
  body            text NOT NULL CHECK (length(body) BETWEEN 1 AND 4000),
  is_public       boolean NOT NULL DEFAULT true,
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX helpdesk_message_ticket_idx ON helpdesk_message (ticket_id, created_at);

-- ---------------------------------------------------------------------------------------------
-- Surveys: questions as validated JSON; responses as JSON answers keyed by question id.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE survey (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  slug            text NOT NULL CHECK (slug ~ '^[a-z0-9-]{3,60}$'),
  title           text NOT NULL CHECK (length(title) BETWEEN 1 AND 160),
  description     text CHECK (description IS NULL OR length(description) <= 2000),
  questions       jsonb NOT NULL DEFAULT '[]',
  is_open         boolean NOT NULL DEFAULT false,
  anonymous       boolean NOT NULL DEFAULT true,
  closes_on       date,
  created_by      uuid REFERENCES app_user (id) ON DELETE SET NULL,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now(),
  UNIQUE (organization_id, slug)
);

CREATE TABLE survey_response (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  survey_id       uuid NOT NULL REFERENCES survey (id) ON DELETE CASCADE,
  answers         jsonb NOT NULL,
  respondent_name text CHECK (respondent_name IS NULL OR length(respondent_name) <= 120),
  phone           text CHECK (phone IS NULL OR length(phone) <= 20),
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX survey_response_survey_idx ON survey_response (survey_id, created_at);

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['ptm_event', 'ptm_host', 'ptm_booking', 'helpdesk_ticket', 'helpdesk_message',
                           'survey', 'survey_response'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format(
      'CREATE POLICY %I ON %I USING (organization_id = sb_current_org() OR sb_is_system())'
      ' WITH CHECK (organization_id = sb_current_org() OR sb_is_system())', t || '_tenant', t);
  END LOOP;
END
$$;

-- ---------------------------------------------------------------------------------------------
-- Catalogue and plans: Lite = PTM + helpdesk; Standard / Global add surveys, SLA and assignment.
-- ---------------------------------------------------------------------------------------------
INSERT INTO module_catalogue (code, name, description, features) VALUES
  ('ENGAGEMENT', 'PTM, helpdesk & surveys', 'PTM slot booking, parent helpdesk with SLA, feedback surveys',
   ARRAY['ptm', 'helpdesk', 'surveys', 'sla', 'assignment']);

UPDATE plan p
   SET entitlements = p.entitlements || jsonb_build_array(jsonb_build_object(
         'module', 'ENGAGEMENT',
         'depth', CASE p.code WHEN 'LITE' THEN 'LITE' WHEN 'STANDARD' THEN 'STANDARD' ELSE 'ADVANCED' END,
         'features', CASE WHEN p.code = 'LITE' THEN '["ptm", "helpdesk"]'::jsonb
                          ELSE '["ptm", "helpdesk", "surveys", "sla", "assignment"]'::jsonb END,
         'limits', '{}'::jsonb))
 WHERE p.code IN ('LITE', 'STANDARD', 'GLOBAL');

SELECT set_config('app.system', 'on', true);
INSERT INTO entitlement (organization_id, module_code, plan_code, depth, features, limits)
SELECT DISTINCT ON (en.organization_id) en.organization_id, 'ENGAGEMENT', en.plan_code,
       CASE en.plan_code WHEN 'LITE' THEN 'LITE' WHEN 'STANDARD' THEN 'STANDARD' ELSE 'ADVANCED' END,
       CASE WHEN en.plan_code = 'LITE' THEN ARRAY['ptm', 'helpdesk']
            ELSE ARRAY['ptm', 'helpdesk', 'surveys', 'sla', 'assignment'] END,
       '{}'::jsonb
  FROM entitlement en
 WHERE en.plan_code IN ('LITE', 'STANDARD', 'GLOBAL')
ON CONFLICT (organization_id, module_code) DO NOTHING;
SELECT set_config('app.system', '', true);
