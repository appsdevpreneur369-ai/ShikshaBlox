-- M43 Front office & reception: walk-in and call register (with hand-off to the admissions CRM),
-- appointments with staff, postal / courier inward and outward, and lost & found with photos.

ALTER TABLE role_assignment DROP CONSTRAINT role_assignment_role_check;
ALTER TABLE role_assignment ADD CONSTRAINT role_assignment_role_check
  CHECK (role IN ('PLATFORM_SUPER_ADMIN', 'SCHOOL_ADMIN', 'CONTENT_EDITOR', 'CONTENT_APPROVER', 'VIEWER',
                  'ADMISSIONS_COUNSELLOR', 'FRONT_OFFICE'));

-- Walk-ins and phone calls.
CREATE TABLE front_desk_entry (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  kind            text NOT NULL CHECK (kind IN ('WALK_IN', 'CALL_IN', 'CALL_OUT')),
  name            text NOT NULL CHECK (length(name) BETWEEN 1 AND 120),
  phone           text CHECK (phone IS NULL OR length(phone) <= 20),
  purpose         text NOT NULL DEFAULT 'OTHER'
                  CHECK (purpose IN ('ADMISSION', 'FEES', 'MEET_STAFF', 'DOCUMENTS', 'COMPLAINT', 'DELIVERY',
                                     'OTHER')),
  meet_with       text CHECK (meet_with IS NULL OR length(meet_with) <= 120),
  people_count    integer NOT NULL DEFAULT 1 CHECK (people_count BETWEEN 1 AND 50),
  notes           text CHECK (notes IS NULL OR length(notes) <= 1000),
  in_at           timestamptz NOT NULL DEFAULT now(),
  out_at          timestamptz,
  lead_id         uuid REFERENCES admission_lead (id) ON DELETE SET NULL,
  created_by      uuid REFERENCES app_user (id) ON DELETE SET NULL,
  created_at      timestamptz NOT NULL DEFAULT now(),
  CHECK (out_at IS NULL OR out_at >= in_at)
);
CREATE INDEX front_desk_entry_day_idx ON front_desk_entry (organization_id, in_at DESC);

-- Appointments with a staff member (or a named person / office).
CREATE TABLE front_desk_appointment (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  visitor_name    text NOT NULL CHECK (length(visitor_name) BETWEEN 1 AND 120),
  phone           text CHECK (phone IS NULL OR length(phone) <= 20),
  staff_id        uuid REFERENCES app_user (id) ON DELETE SET NULL,
  meet_with       text CHECK (meet_with IS NULL OR length(meet_with) <= 120),
  purpose         text CHECK (purpose IS NULL OR length(purpose) <= 300),
  starts_at       timestamptz NOT NULL,
  minutes         integer NOT NULL DEFAULT 15 CHECK (minutes BETWEEN 5 AND 240),
  status          text NOT NULL DEFAULT 'SCHEDULED'
                  CHECK (status IN ('SCHEDULED', 'ARRIVED', 'DONE', 'CANCELLED', 'NO_SHOW')),
  notes           text CHECK (notes IS NULL OR length(notes) <= 1000),
  created_by      uuid REFERENCES app_user (id) ON DELETE SET NULL,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now(),
  CHECK (staff_id IS NOT NULL OR meet_with IS NOT NULL)
);
CREATE INDEX front_desk_appointment_day_idx ON front_desk_appointment (organization_id, starts_at);

-- Post and couriers.
CREATE TABLE front_desk_post (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  direction       text NOT NULL CHECK (direction IN ('INWARD', 'OUTWARD')),
  counterparty    text NOT NULL CHECK (length(counterparty) BETWEEN 1 AND 160), -- sender (inward) / recipient (outward)
  addressee       text CHECK (addressee IS NULL OR length(addressee) <= 120),   -- school person it is for / from
  carrier         text CHECK (carrier IS NULL OR length(carrier) <= 80),
  tracking_no     text CHECK (tracking_no IS NULL OR length(tracking_no) <= 80),
  description     text CHECK (description IS NULL OR length(description) <= 300),
  logged_on       date NOT NULL,
  status          text NOT NULL CHECK (status IN ('RECEIVED', 'HANDED_OVER', 'READY', 'DISPATCHED')),
  handed_to       text CHECK (handed_to IS NULL OR length(handed_to) <= 120),
  closed_at       timestamptz,
  created_by      uuid REFERENCES app_user (id) ON DELETE SET NULL,
  created_at      timestamptz NOT NULL DEFAULT now(),
  CHECK ((direction = 'INWARD' AND status IN ('RECEIVED', 'HANDED_OVER'))
      OR (direction = 'OUTWARD' AND status IN ('READY', 'DISPATCHED')))
);
CREATE INDEX front_desk_post_idx ON front_desk_post (organization_id, logged_on DESC);

-- Lost & found (photo in the private bucket).
CREATE TABLE front_desk_lost_item (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  description     text NOT NULL CHECK (length(description) BETWEEN 1 AND 300),
  found_at        text CHECK (found_at IS NULL OR length(found_at) <= 120),
  found_on        date NOT NULL,
  photo_key       text,
  status          text NOT NULL DEFAULT 'FOUND' CHECK (status IN ('FOUND', 'RETURNED', 'DISPOSED')),
  returned_to     text CHECK (returned_to IS NULL OR length(returned_to) <= 160),
  closed_at       timestamptz,
  created_by      uuid REFERENCES app_user (id) ON DELETE SET NULL,
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX front_desk_lost_item_idx ON front_desk_lost_item (organization_id, status, found_on DESC);

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['front_desk_entry', 'front_desk_appointment', 'front_desk_post', 'front_desk_lost_item'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format(
      'CREATE POLICY %I ON %I USING (organization_id = sb_current_org() OR sb_is_system())'
      ' WITH CHECK (organization_id = sb_current_org() OR sb_is_system())', t || '_tenant', t);
  END LOOP;
END
$$;

-- ---------------------------------------------------------------------------------------------
-- Catalogue and plans: included in Standard / Global (optional add-on for Lite schools).
-- ---------------------------------------------------------------------------------------------
INSERT INTO module_catalogue (code, name, description, features) VALUES
  ('FRONT_OFFICE', 'Front office', 'Walk-in and call register, appointments, post and couriers, lost & found',
   ARRAY['register', 'appointments', 'post', 'lost_found']);

UPDATE plan p
   SET entitlements = p.entitlements || jsonb_build_array(jsonb_build_object(
         'module', 'FRONT_OFFICE',
         'depth', CASE WHEN p.code = 'GLOBAL' THEN 'ADVANCED' ELSE 'STANDARD' END,
         'features', '["register", "appointments", "post", "lost_found"]'::jsonb,
         'limits', '{}'::jsonb))
 WHERE p.code IN ('STANDARD', 'GLOBAL');

SELECT set_config('app.system', 'on', true);
INSERT INTO entitlement (organization_id, module_code, plan_code, depth, features, limits)
SELECT DISTINCT ON (en.organization_id) en.organization_id, 'FRONT_OFFICE', en.plan_code,
       CASE WHEN en.plan_code = 'GLOBAL' THEN 'ADVANCED' ELSE 'STANDARD' END,
       ARRAY['register', 'appointments', 'post', 'lost_found'], '{}'::jsonb
  FROM entitlement en
 WHERE en.plan_code IN ('STANDARD', 'GLOBAL')
ON CONFLICT (organization_id, module_code) DO NOTHING;
SELECT set_config('app.system', '', true);
