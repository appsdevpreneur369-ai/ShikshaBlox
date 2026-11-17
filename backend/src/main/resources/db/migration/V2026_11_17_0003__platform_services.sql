-- M1b: entitlements & plans, configuration hierarchy, audit log, media files, consent registry.

-- ---------------------------------------------------------------------------------------------
-- Entitlements (module catalogue, plans, per-tenant entitlements)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE module_catalogue (
  code        text PRIMARY KEY,
  name        text NOT NULL,
  description text NOT NULL,
  -- Features a module can expose (informational; plans grant a subset).
  features    text[] NOT NULL DEFAULT '{}'
);

-- A plan is a named bundle of module entitlements: [{module, depth, features[], limits{}}].
CREATE TABLE plan (
  code         text PRIMARY KEY,
  name         text NOT NULL,
  entitlements jsonb NOT NULL,
  sort_order   integer NOT NULL DEFAULT 0
);

-- Reference data: read-only for the runtime role (changed by migrations only).
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sb_app') THEN
    EXECUTE 'REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON module_catalogue, plan FROM sb_app';
  END IF;
END
$$;

CREATE TABLE entitlement (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  module_code     text NOT NULL REFERENCES module_catalogue (code),
  plan_code       text REFERENCES plan (code),
  depth           text NOT NULL CHECK (depth IN ('LITE', 'STANDARD', 'ADVANCED')),
  features        text[] NOT NULL DEFAULT '{}',
  limits          jsonb NOT NULL DEFAULT '{}', -- e.g. {"storage_bytes": 2147483648}; -1 = unlimited
  starts_at       timestamptz NOT NULL DEFAULT now(),
  expires_at      timestamptz,
  updated_at      timestamptz NOT NULL DEFAULT now(),
  UNIQUE (organization_id, module_code)
);

ALTER TABLE entitlement ENABLE ROW LEVEL SECURITY;
ALTER TABLE entitlement FORCE ROW LEVEL SECURITY;
CREATE POLICY entitlement_tenant_read ON entitlement FOR SELECT
  USING (organization_id = sb_current_org() OR sb_is_system());
-- Only the platform (system context) changes entitlements; schools cannot grant themselves more.
CREATE POLICY entitlement_system_write ON entitlement FOR ALL
  USING (sb_is_system()) WITH CHECK (sb_is_system());

INSERT INTO module_catalogue (code, name, description, features) VALUES
  ('WEBSITE', 'School Website & CMS', 'Public website, pages, content types, publishing',
   ARRAY['notices', 'events', 'gallery', 'staff', 'news', 'faqs', 'careers', 'approval_workflow',
         'custom_domain', 'remove_branding', 'analytics', 'export_import']),
  ('DISCLOSURE', 'Mandatory Public Disclosure', 'Board disclosure section with review reminders',
   ARRAY['review_reminders']),
  ('ADMISSIONS_ENQUIRY', 'Admissions enquiries', 'Website enquiry form and inbox (CRM in M03)',
   ARRAY['csv_export', 'email_alerts']);

INSERT INTO plan (code, name, sort_order, entitlements) VALUES
  ('WEB_FREE', 'Web Free', 1, '[
     {"module": "WEBSITE", "depth": "LITE",
      "features": ["notices", "events", "gallery", "staff", "news", "faqs", "careers", "analytics"],
      "limits": {"custom_pages": 5, "storage_bytes": 2147483648, "languages": 1, "custom_domains": 0}},
     {"module": "DISCLOSURE", "depth": "LITE", "features": ["review_reminders"], "limits": {}},
     {"module": "ADMISSIONS_ENQUIRY", "depth": "LITE", "features": ["csv_export", "email_alerts"], "limits": {}}
   ]'),
  ('WEB_PRO', 'Web Pro', 2, '[
     {"module": "WEBSITE", "depth": "STANDARD",
      "features": ["notices", "events", "gallery", "staff", "news", "faqs", "careers", "analytics",
                   "approval_workflow", "custom_domain", "remove_branding", "export_import"],
      "limits": {"custom_pages": -1, "storage_bytes": 10737418240, "languages": 2, "custom_domains": 1}},
     {"module": "DISCLOSURE", "depth": "STANDARD", "features": ["review_reminders"], "limits": {}},
     {"module": "ADMISSIONS_ENQUIRY", "depth": "STANDARD", "features": ["csv_export", "email_alerts"], "limits": {}}
   ]');

-- Schools created before entitlements existed start on Web Free. Entitlement writes need the
-- system context under FORCE RLS (transaction-local).
SELECT set_config('app.system', 'on', true);
INSERT INTO entitlement (organization_id, module_code, plan_code, depth, features, limits)
SELECT o.id, e ->> 'module', p.code, e ->> 'depth',
       ARRAY(SELECT jsonb_array_elements_text(e -> 'features')), e -> 'limits'
  FROM organization o
  CROSS JOIN plan p
  CROSS JOIN LATERAL jsonb_array_elements(p.entitlements) e
 WHERE p.code = 'WEB_FREE';
SELECT set_config('app.system', '', true);

-- ---------------------------------------------------------------------------------------------
-- Configuration hierarchy: PLATFORM -> PROFILE -> ORGANIZATION -> CAMPUS (most specific wins)
-- ---------------------------------------------------------------------------------------------
ALTER TABLE organization ADD COLUMN config_profile text NOT NULL DEFAULT 'default';

CREATE TABLE config_value (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  scope           text NOT NULL CHECK (scope IN ('PLATFORM', 'PROFILE', 'ORGANIZATION', 'CAMPUS')),
  scope_ref       text NOT NULL DEFAULT '', -- profile code or campus id; '' for platform/org
  organization_id uuid REFERENCES organization (id) ON DELETE CASCADE,
  key             text NOT NULL CHECK (key ~ '^[a-z][a-z0-9_.]{2,99}$'),
  value           jsonb NOT NULL,
  updated_by      uuid,
  updated_at      timestamptz NOT NULL DEFAULT now(),
  CHECK ((scope IN ('ORGANIZATION', 'CAMPUS')) = (organization_id IS NOT NULL)),
  UNIQUE NULLS NOT DISTINCT (scope, scope_ref, organization_id, key)
);

ALTER TABLE config_value ENABLE ROW LEVEL SECURITY;
ALTER TABLE config_value FORCE ROW LEVEL SECURITY;
-- Platform/profile defaults are readable by every tenant; tenant rows only by that tenant.
CREATE POLICY config_read ON config_value FOR SELECT
  USING (organization_id IS NULL OR organization_id = sb_current_org() OR sb_is_system());
CREATE POLICY config_tenant_write ON config_value FOR ALL
  USING ((organization_id IS NOT NULL AND organization_id = sb_current_org()) OR sb_is_system())
  WITH CHECK ((organization_id IS NOT NULL AND organization_id = sb_current_org()) OR sb_is_system());

-- ---------------------------------------------------------------------------------------------
-- Audit log (append-only)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE audit_log (
  id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  organization_id uuid, -- null for platform-level events (sign-in, platform admin actions)
  actor_user_id   uuid,
  action          text NOT NULL,
  entity_type     text NOT NULL,
  entity_id       text,
  before_state    jsonb,
  after_state     jsonb,
  ip              text,
  request_id      text,
  occurred_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_log_org_time_idx ON audit_log (organization_id, occurred_at DESC);
CREATE INDEX audit_log_entity_idx ON audit_log (organization_id, entity_type, entity_id);

ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log FORCE ROW LEVEL SECURITY;
CREATE POLICY audit_read ON audit_log FOR SELECT
  USING (organization_id = sb_current_org() OR sb_is_system());
CREATE POLICY audit_insert ON audit_log FOR INSERT
  WITH CHECK (
    sb_is_system()
    OR organization_id = sb_current_org()
    OR (organization_id IS NULL AND actor_user_id = sb_current_user())
  );

-- Immutable: the runtime role may only insert and read.
CREATE FUNCTION audit_log_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'audit_log is append-only';
END
$$;
CREATE TRIGGER audit_log_no_update BEFORE UPDATE OR DELETE ON audit_log
  FOR EACH ROW EXECUTE FUNCTION audit_log_immutable();
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sb_app') THEN
    EXECUTE 'REVOKE UPDATE, DELETE, TRUNCATE ON audit_log FROM sb_app';
  END IF;
END
$$;

-- ---------------------------------------------------------------------------------------------
-- Consent registry (minimal; the SIS Consent Ledger plugs in later via consent_record.source)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE consent_record (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  kind            text NOT NULL CHECK (kind IN ('SCHOOL_DECLARATION', 'GUARDIAN_CONSENT')),
  purpose         text NOT NULL DEFAULT 'website_media',
  -- A label, never student personal data, e.g. "Annual Day 2026 – consent forms on file".
  reference       text NOT NULL CHECK (length(reference) BETWEEN 3 AND 300),
  statement       text,
  declared_by     text NOT NULL,
  source          text NOT NULL DEFAULT 'WEBSITE', -- later: SIS_CONSENT_LEDGER
  valid_from      date NOT NULL DEFAULT current_date,
  valid_until     date,
  revoked_at      timestamptz,
  created_by      uuid,
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX consent_record_org_idx ON consent_record (organization_id, kind);

ALTER TABLE consent_record ENABLE ROW LEVEL SECURITY;
ALTER TABLE consent_record FORCE ROW LEVEL SECURITY;
CREATE POLICY consent_tenant ON consent_record
  USING (organization_id = sb_current_org() OR sb_is_system())
  WITH CHECK (organization_id = sb_current_org() OR sb_is_system());

-- ---------------------------------------------------------------------------------------------
-- Media files
-- ---------------------------------------------------------------------------------------------
CREATE TABLE media_asset (
  id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id       uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  kind                  text NOT NULL CHECK (kind IN ('IMAGE', 'DOCUMENT', 'VIDEO')),
  visibility            text NOT NULL DEFAULT 'PUBLIC' CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
  status                text NOT NULL DEFAULT 'READY'
                        CHECK (status IN ('PENDING_SCAN', 'READY', 'REJECTED')),
  original_filename     text NOT NULL,
  content_type          text NOT NULL,
  size_bytes            bigint NOT NULL,
  stored_bytes          bigint NOT NULL, -- original + variants (storage quota)
  sha256                text NOT NULL,
  storage_key           text NOT NULL,
  width                 integer,
  height                integer,
  variants              jsonb NOT NULL DEFAULT '{}', -- {"thumb": {"key","width","height","type"}}
  alt_text              text,
  caption               text,
  folder                text NOT NULL DEFAULT '',
  contains_identifiable_students boolean NOT NULL DEFAULT false,
  consent_record_id     uuid REFERENCES consent_record (id),
  created_by            uuid,
  created_at            timestamptz NOT NULL DEFAULT now(),
  updated_at            timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX media_asset_org_idx ON media_asset (organization_id, created_at DESC);

ALTER TABLE media_asset ENABLE ROW LEVEL SECURITY;
ALTER TABLE media_asset FORCE ROW LEVEL SECURITY;
CREATE POLICY media_tenant ON media_asset
  USING (organization_id = sb_current_org() OR sb_is_system())
  WITH CHECK (organization_id = sb_current_org() OR sb_is_system());
