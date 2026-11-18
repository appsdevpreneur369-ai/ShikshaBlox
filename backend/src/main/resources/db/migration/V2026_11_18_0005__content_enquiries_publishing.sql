-- M3: content types (notices, news, events, albums, staff, FAQs, careers, disclosures) stored as
-- validated JSON per item. M4: admissions enquiries and disclosure review tracking.
-- M5: review workflow and scheduled publishing for pages.

CREATE TABLE content_item (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  type            text NOT NULL CHECK (type IN ('notices', 'news', 'events', 'albums', 'staff', 'faqs',
                                                'careers', 'disclosures')),
  -- URL segment for types with detail pages (news, events, albums).
  slug            text CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$' AND length(slug) <= 80),
  status          text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED')),
  -- Type-specific fields, validated and sanitised by ContentService.
  data            jsonb NOT NULL,
  -- Main date (notice date, news date, event start, album date, apply-by) for ordering/filtering.
  item_date       timestamptz,
  sort_order      integer NOT NULL DEFAULT 0,
  published_at    timestamptz,
  created_by      uuid,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX content_item_feed_idx ON content_item (organization_id, type, status, item_date DESC);
CREATE UNIQUE INDEX content_item_slug_idx ON content_item (organization_id, type, slug) WHERE slug IS NOT NULL;

CREATE TABLE enquiry (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  name            text NOT NULL CHECK (length(name) BETWEEN 1 AND 120),
  phone           text NOT NULL CHECK (length(phone) BETWEEN 6 AND 20),
  email           text CHECK (email IS NULL OR length(email) <= 200),
  grade_sought    text CHECK (grade_sought IS NULL OR length(grade_sought) <= 60),
  message         text NOT NULL CHECK (length(message) BETWEEN 1 AND 2000),
  source_page     text CHECK (source_page IS NULL OR length(source_page) <= 200),
  status          text NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW', 'CONTACTED', 'ENROLLED', 'CLOSED', 'SPAM')),
  notes           text CHECK (notes IS NULL OR length(notes) <= 4000),
  consent_given   boolean NOT NULL DEFAULT false,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX enquiry_org_idx ON enquiry (organization_id, created_at DESC);

ALTER TABLE site ADD COLUMN disclosure_reviewed_at timestamptz;
ALTER TABLE site ADD COLUMN disclosure_reminder_sent_at timestamptz;

ALTER TABLE page_content ADD COLUMN scheduled_at timestamptz;
ALTER TABLE page_content ADD COLUMN review_note text CHECK (review_note IS NULL OR length(review_note) <= 500);
ALTER TABLE page_content ADD COLUMN submitted_by uuid;
CREATE INDEX page_content_scheduled_idx ON page_content (scheduled_at) WHERE status = 'SCHEDULED';

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['content_item', 'enquiry'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format(
      'CREATE POLICY %I ON %I USING (organization_id = sb_current_org() OR sb_is_system())'
      ' WITH CHECK (organization_id = sb_current_org() OR sb_is_system())', t || '_tenant', t);
  END LOOP;
END
$$;
