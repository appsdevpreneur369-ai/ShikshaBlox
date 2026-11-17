-- M1a: tenancy (Organization -> Campus, site domains) and identity (users, roles, tokens, OTP).
--
-- Row-Level Security model (ADR 0002):
--   * The runtime role sb_app is not an owner and has NOBYPASSRLS, and every table below uses
--     ENABLE + FORCE ROW LEVEL SECURITY.
--   * The application sets three session settings on every connection checkout:
--       app.org_id   tenant of the current request ('' = none)
--       app.user_id  authenticated user ('' = none)
--       app.system   'on' only inside an explicit, logged system context (login, onboarding, jobs)
--   * No tenant context => no tenant rows.

-- ---------------------------------------------------------------------------------------------
-- Context helpers
-- ---------------------------------------------------------------------------------------------
CREATE FUNCTION sb_current_org() RETURNS uuid
  LANGUAGE sql STABLE AS $$ SELECT nullif(current_setting('app.org_id', true), '')::uuid $$;

CREATE FUNCTION sb_current_user() RETURNS uuid
  LANGUAGE sql STABLE AS $$ SELECT nullif(current_setting('app.user_id', true), '')::uuid $$;

CREATE FUNCTION sb_is_system() RETURNS boolean
  LANGUAGE sql STABLE AS $$ SELECT coalesce(current_setting('app.system', true), '') = 'on' $$;

-- ---------------------------------------------------------------------------------------------
-- Tenancy
-- ---------------------------------------------------------------------------------------------
CREATE TABLE organization (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  slug        citext NOT NULL UNIQUE
              CHECK (slug ~ '^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$'),
  name        text NOT NULL CHECK (length(name) BETWEEN 2 AND 200),
  status      text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE campus (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  name            text NOT NULL,
  is_default      boolean NOT NULL DEFAULT false,
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX campus_org_idx ON campus (organization_id);
CREATE UNIQUE INDEX campus_one_default_idx ON campus (organization_id) WHERE is_default;

-- Hosts that serve a school's public site besides its platform subdomain (custom domains).
CREATE TABLE site_domain (
  id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id    uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  host               citext NOT NULL UNIQUE,
  status             text NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING', 'VERIFIED', 'FAILED', 'DISABLED')),
  verification_token text NOT NULL DEFAULT encode(gen_random_bytes(16), 'hex'),
  is_primary         boolean NOT NULL DEFAULT false,
  verified_at        timestamptz,
  created_at         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX site_domain_org_idx ON site_domain (organization_id);

ALTER TABLE organization ENABLE ROW LEVEL SECURITY;
ALTER TABLE organization FORCE ROW LEVEL SECURITY;
CREATE POLICY organization_tenant ON organization
  USING (id = sb_current_org() OR sb_is_system())
  WITH CHECK (id = sb_current_org() OR sb_is_system());

ALTER TABLE campus ENABLE ROW LEVEL SECURITY;
ALTER TABLE campus FORCE ROW LEVEL SECURITY;
CREATE POLICY campus_tenant ON campus
  USING (organization_id = sb_current_org() OR sb_is_system())
  WITH CHECK (organization_id = sb_current_org() OR sb_is_system());

ALTER TABLE site_domain ENABLE ROW LEVEL SECURITY;
ALTER TABLE site_domain FORCE ROW LEVEL SECURITY;
CREATE POLICY site_domain_tenant ON site_domain
  USING (organization_id = sb_current_org() OR sb_is_system())
  WITH CHECK (organization_id = sb_current_org() OR sb_is_system());

-- Host -> tenant lookups for the public site run before any tenant is known. These narrow
-- SECURITY DEFINER functions return only an organization id for an active tenant; they are the
-- only path around RLS for anonymous traffic. FORCE RLS also binds the owner, so the owner role
-- (which runs migrations and these functions, never application requests) gets read-only policies.
DO $$
BEGIN
  EXECUTE format('CREATE POLICY organization_owner_read ON organization FOR SELECT TO %I USING (true)', current_user);
  EXECUTE format('CREATE POLICY site_domain_owner_read ON site_domain FOR SELECT TO %I USING (true)', current_user);
END
$$;

CREATE FUNCTION sb_resolve_slug(p_slug text) RETURNS uuid
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS $$
  SELECT o.id FROM organization o WHERE o.slug = p_slug AND o.status = 'ACTIVE'
$$;

CREATE FUNCTION sb_resolve_domain(p_host text) RETURNS uuid
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS $$
  SELECT d.organization_id
    FROM site_domain d JOIN organization o ON o.id = d.organization_id
   WHERE d.host = p_host AND d.status = 'VERIFIED' AND o.status = 'ACTIVE'
$$;

REVOKE ALL ON FUNCTION sb_resolve_slug(text) FROM PUBLIC;
REVOKE ALL ON FUNCTION sb_resolve_domain(text) FROM PUBLIC;
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sb_app') THEN
    EXECUTE 'GRANT EXECUTE ON FUNCTION sb_resolve_slug(text) TO sb_app';
    EXECUTE 'GRANT EXECUTE ON FUNCTION sb_resolve_domain(text) TO sb_app';
  END IF;
END
$$;

-- ---------------------------------------------------------------------------------------------
-- Identity
-- ---------------------------------------------------------------------------------------------
-- A person has one account and may belong to several schools through role assignments.
CREATE TABLE app_user (
  id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email              citext NOT NULL UNIQUE CHECK (length(email) <= 254 AND email LIKE '%_@_%'),
  display_name       text NOT NULL CHECK (length(display_name) BETWEEN 1 AND 120),
  password_hash      text,
  status             text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
  mfa_secret_enc     text,
  mfa_enabled        boolean NOT NULL DEFAULT false,
  mfa_last_step      bigint, -- last accepted TOTP time step (replay protection)
  failed_login_count integer NOT NULL DEFAULT 0,
  locked_until       timestamptz,
  last_login_at      timestamptz,
  created_at         timestamptz NOT NULL DEFAULT now(),
  updated_at         timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE role_assignment (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
  organization_id uuid REFERENCES organization (id) ON DELETE CASCADE,
  role            text NOT NULL CHECK (role IN ('PLATFORM_SUPER_ADMIN', 'SCHOOL_ADMIN',
                                                'CONTENT_EDITOR', 'CONTENT_APPROVER', 'VIEWER')),
  created_at      timestamptz NOT NULL DEFAULT now(),
  -- Platform roles have no organization; school roles always have one.
  CHECK ((role = 'PLATFORM_SUPER_ADMIN') = (organization_id IS NULL)),
  UNIQUE NULLS NOT DISTINCT (user_id, organization_id, role)
);
CREATE INDEX role_assignment_org_idx ON role_assignment (organization_id);
CREATE INDEX role_assignment_user_idx ON role_assignment (user_id);

CREATE TABLE refresh_token (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
  family_id       uuid NOT NULL,
  token_hash      bytea NOT NULL UNIQUE,
  organization_id uuid REFERENCES organization (id) ON DELETE CASCADE,
  mfa             boolean NOT NULL DEFAULT false,
  expires_at      timestamptz NOT NULL,
  used_at         timestamptz,
  revoked_at      timestamptz,
  created_at      timestamptz NOT NULL DEFAULT now(),
  created_ip      text,
  user_agent      text
);
CREATE INDEX refresh_token_family_idx ON refresh_token (family_id);
CREATE INDEX refresh_token_user_idx ON refresh_token (user_id);

CREATE TABLE login_otp (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
  code_hash   bytea NOT NULL,
  expires_at  timestamptz NOT NULL,
  attempts    integer NOT NULL DEFAULT 0,
  consumed_at timestamptz,
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX login_otp_user_idx ON login_otp (user_id, created_at DESC);

-- Users are visible to themselves, to members of the current school (to list colleagues) and
-- to the system context (authentication).
ALTER TABLE app_user ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_user FORCE ROW LEVEL SECURITY;
CREATE POLICY app_user_visible ON app_user
  USING (
    sb_is_system()
    OR id = sb_current_user()
    OR EXISTS (SELECT 1 FROM role_assignment ra
                WHERE ra.user_id = app_user.id AND ra.organization_id = sb_current_org())
  )
  WITH CHECK (sb_is_system() OR id = sb_current_user());

ALTER TABLE role_assignment ENABLE ROW LEVEL SECURITY;
ALTER TABLE role_assignment FORCE ROW LEVEL SECURITY;
CREATE POLICY role_assignment_read ON role_assignment FOR SELECT
  USING (sb_is_system() OR organization_id = sb_current_org() OR user_id = sb_current_user());
CREATE POLICY role_assignment_insert ON role_assignment FOR INSERT
  WITH CHECK (sb_is_system() OR (organization_id IS NOT NULL AND organization_id = sb_current_org()));
CREATE POLICY role_assignment_update ON role_assignment FOR UPDATE
  USING (sb_is_system() OR (organization_id IS NOT NULL AND organization_id = sb_current_org()))
  WITH CHECK (sb_is_system() OR (organization_id IS NOT NULL AND organization_id = sb_current_org()));
CREATE POLICY role_assignment_delete ON role_assignment FOR DELETE
  USING (sb_is_system() OR (organization_id IS NOT NULL AND organization_id = sb_current_org()));

ALTER TABLE refresh_token ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_token FORCE ROW LEVEL SECURITY;
CREATE POLICY refresh_token_owner ON refresh_token
  USING (sb_is_system() OR user_id = sb_current_user())
  WITH CHECK (sb_is_system() OR user_id = sb_current_user());

ALTER TABLE login_otp ENABLE ROW LEVEL SECURITY;
ALTER TABLE login_otp FORCE ROW LEVEL SECURITY;
CREATE POLICY login_otp_system ON login_otp
  USING (sb_is_system()) WITH CHECK (sb_is_system());
