-- M12 Parent portal: guardians get access through a personal invite link from the school, set a
-- PIN, and sign in on the school website with phone + PIN. Sessions are opaque random tokens
-- (stored hashed) so they can be revoked. Preference changes from the portal are consent events.

CREATE TABLE parent_account (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id   uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  guardian_id       uuid NOT NULL UNIQUE REFERENCES guardian (id) ON DELETE CASCADE,
  status            text NOT NULL DEFAULT 'INVITED' CHECK (status IN ('INVITED', 'ACTIVE', 'DISABLED')),
  pin_hash          text,
  invite_hash       text UNIQUE,
  invite_expires_at timestamptz,
  failed_attempts   integer NOT NULL DEFAULT 0,
  locked_until      timestamptz,
  last_login_at     timestamptz,
  created_at        timestamptz NOT NULL DEFAULT now(),
  updated_at        timestamptz NOT NULL DEFAULT now(),
  CHECK (status <> 'ACTIVE' OR pin_hash IS NOT NULL)
);

CREATE TABLE parent_session (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  account_id      uuid NOT NULL REFERENCES parent_account (id) ON DELETE CASCADE,
  token_hash      text NOT NULL UNIQUE,
  expires_at      timestamptz NOT NULL,
  created_at      timestamptz NOT NULL DEFAULT now(),
  last_seen_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX parent_session_account_idx ON parent_session (account_id);

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['parent_account', 'parent_session'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format(
      'CREATE POLICY %I ON %I USING (organization_id = sb_current_org() OR sb_is_system())'
      ' WITH CHECK (organization_id = sb_current_org() OR sb_is_system())', t || '_tenant', t);
  END LOOP;
END
$$;

-- Parents can change their own message preferences in the portal.
ALTER TABLE comms_consent_log DROP CONSTRAINT comms_consent_log_source_check;
ALTER TABLE comms_consent_log ADD CONSTRAINT comms_consent_log_source_check
  CHECK (source IN ('FORM', 'IMPORT', 'STAFF', 'LINK', 'REPLY', 'PORTAL'));

-- ---------------------------------------------------------------------------------------------
-- Catalogue and plans: the parent portal is part of every ERP plan.
-- ---------------------------------------------------------------------------------------------
INSERT INTO module_catalogue (code, name, description, features) VALUES
  ('PORTAL', 'Parent portal', 'Parent sign-in on the school website: children, circulars, PTM, requests, preferences',
   ARRAY['children', 'circulars', 'ptm', 'requests', 'preferences']);

UPDATE plan p
   SET entitlements = p.entitlements || jsonb_build_array(jsonb_build_object(
         'module', 'PORTAL',
         'depth', CASE p.code WHEN 'LITE' THEN 'LITE' WHEN 'STANDARD' THEN 'STANDARD' ELSE 'ADVANCED' END,
         'features', '["children", "circulars", "ptm", "requests", "preferences"]'::jsonb,
         'limits', '{}'::jsonb))
 WHERE p.code IN ('LITE', 'STANDARD', 'GLOBAL');

SELECT set_config('app.system', 'on', true);
INSERT INTO entitlement (organization_id, module_code, plan_code, depth, features, limits)
SELECT DISTINCT ON (en.organization_id) en.organization_id, 'PORTAL', en.plan_code,
       CASE en.plan_code WHEN 'LITE' THEN 'LITE' WHEN 'STANDARD' THEN 'STANDARD' ELSE 'ADVANCED' END,
       ARRAY['children', 'circulars', 'ptm', 'requests', 'preferences'], '{}'::jsonb
  FROM entitlement en
 WHERE en.plan_code IN ('LITE', 'STANDARD', 'GLOBAL')
ON CONFLICT (organization_id, module_code) DO NOTHING;
SELECT set_config('app.system', '', true);
