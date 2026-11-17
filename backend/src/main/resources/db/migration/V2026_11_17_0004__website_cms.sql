-- M2: website CMS core — site settings/theme, pages with per-language content and revisions,
-- menus, reusable blocks. Every table is tenant-owned and protected by RLS.

CREATE TABLE site (
  organization_id      uuid PRIMARY KEY REFERENCES organization (id) ON DELETE CASCADE,
  theme                text NOT NULL DEFAULT 'modern' CHECK (theme IN ('classic', 'modern', 'playful')),
  -- Design tokens: {"primary": "#1e3a8a", "accent": "#f59e0b", "fontHeading": "...", ...}
  brand                jsonb NOT NULL DEFAULT '{}',
  -- School profile used across the site: tagline, board, address, phone, email, social, ...
  profile              jsonb NOT NULL DEFAULT '{}',
  logo_media_id        uuid REFERENCES media_asset (id) ON DELETE SET NULL,
  languages            text[] NOT NULL DEFAULT ARRAY['en'],
  default_language     text NOT NULL DEFAULT 'en',
  onboarding_completed boolean NOT NULL DEFAULT false,
  created_at           timestamptz NOT NULL DEFAULT now(),
  updated_at           timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE page (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  -- Path without leading slash; '' is the home page.
  slug            text NOT NULL CHECK (slug ~ '^([a-z0-9]+(-[a-z0-9]+)*(/[a-z0-9]+(-[a-z0-9]+)*)*)?$'),
  kind            text NOT NULL CHECK (kind IN ('STANDARD', 'CUSTOM')),
  template_key    text,
  sort_order      integer NOT NULL DEFAULT 0,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now(),
  UNIQUE (organization_id, slug)
);
CREATE INDEX page_org_idx ON page (organization_id, sort_order);

-- One row per page and language: draft and published documents side by side.
CREATE TABLE page_content (
  page_id            uuid NOT NULL REFERENCES page (id) ON DELETE CASCADE,
  organization_id    uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  language           text NOT NULL CHECK (language ~ '^[a-z]{2}(-[A-Z]{2})?$'),
  title              text NOT NULL CHECK (length(title) BETWEEN 1 AND 160),
  seo                jsonb NOT NULL DEFAULT '{}',
  draft_document     jsonb NOT NULL,
  published_document jsonb,
  published_title    text,
  published_seo      jsonb,
  status             text NOT NULL DEFAULT 'DRAFT'
                     CHECK (status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'SCHEDULED', 'PUBLISHED', 'ARCHIVED')),
  published_at       timestamptz,
  updated_by         uuid,
  updated_at         timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (page_id, language)
);
CREATE INDEX page_content_org_idx ON page_content (organization_id);

CREATE TABLE page_revision (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  page_id         uuid NOT NULL REFERENCES page (id) ON DELETE CASCADE,
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  language        text NOT NULL,
  title           text NOT NULL,
  seo             jsonb NOT NULL DEFAULT '{}',
  document        jsonb NOT NULL,
  action          text NOT NULL, -- PUBLISHED, ROLLED_BACK, ...
  note            text,
  created_by      uuid,
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX page_revision_page_idx ON page_revision (page_id, language, created_at DESC);

CREATE TABLE menu (
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  location        text NOT NULL CHECK (location IN ('header', 'footer')),
  -- [{"id", "label": {"en": "About"}, "pageId"?, "href"?, "children": [...]}]
  items           jsonb NOT NULL DEFAULT '[]',
  updated_at      timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (organization_id, location)
);

CREATE TABLE reusable_block (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  name            text NOT NULL CHECK (length(name) BETWEEN 1 AND 120),
  block           jsonb NOT NULL,
  updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX reusable_block_org_idx ON reusable_block (organization_id);

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['site', 'page', 'page_content', 'page_revision', 'menu', 'reusable_block'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format(
      'CREATE POLICY %I ON %I USING (organization_id = sb_current_org() OR sb_is_system())'
      ' WITH CHECK (organization_id = sb_current_org() OR sb_is_system())', t || '_tenant', t);
  END LOOP;
END
$$;

-- Revisions are history: never rewritten.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sb_app') THEN
    EXECUTE 'REVOKE UPDATE ON page_revision FROM sb_app';
  END IF;
END
$$;
