-- M13 Communication hub: contacts with a verifiable opt-in register, multilingual message
-- templates, broadcasts (circulars) with quiet hours, duplicate suppression and a monthly cap,
-- per-recipient delivery status, read receipts and acknowledgements via a personal link, and a
-- prepaid messaging wallet. WhatsApp / SMS go through a provider adapter ("test mode" until a
-- provider account is connected); email uses the platform mailer.

-- Contacts (guardians). Linked to student records once M04 exists; until then tags carry class.
CREATE TABLE comms_contact (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  name            text NOT NULL CHECK (length(name) BETWEEN 1 AND 120),
  phone           text CHECK (phone IS NULL OR length(phone) BETWEEN 6 AND 20),
  email           text CHECK (email IS NULL OR length(email) <= 200),
  language        text NOT NULL DEFAULT 'en' CHECK (language ~ '^[a-z]{2}$'),
  tags            text[] NOT NULL DEFAULT '{}',
  student_names   text CHECK (student_names IS NULL OR length(student_names) <= 300),
  whatsapp_opt_in boolean NOT NULL DEFAULT false,
  sms_opt_in      boolean NOT NULL DEFAULT false,
  email_opt_in    boolean NOT NULL DEFAULT false,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now(),
  CHECK (phone IS NOT NULL OR email IS NOT NULL)
);
CREATE INDEX comms_contact_tags_idx ON comms_contact USING gin (tags);
CREATE UNIQUE INDEX comms_contact_phone_uq ON comms_contact (organization_id, right(regexp_replace(phone, '\D', '', 'g'), 10))
  WHERE phone IS NOT NULL;

-- Every consent change, so opt-in is verifiable: who, which channel, when, how, by whom.
CREATE TABLE comms_consent_log (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  contact_id      uuid NOT NULL REFERENCES comms_contact (id) ON DELETE CASCADE,
  channel         text NOT NULL CHECK (channel IN ('WHATSAPP', 'SMS', 'EMAIL')),
  opted_in        boolean NOT NULL,
  source          text NOT NULL CHECK (source IN ('FORM', 'IMPORT', 'STAFF', 'LINK', 'REPLY')),
  note            text CHECK (note IS NULL OR length(note) <= 300),
  actor_id        uuid REFERENCES app_user (id) ON DELETE SET NULL,
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX comms_consent_log_contact_idx ON comms_consent_log (contact_id, created_at DESC);

-- Templates; one row per language variant of a named template.
CREATE TABLE comms_template (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  name            text NOT NULL CHECK (name ~ '^[a-z0-9_]{3,60}$'),
  language        text NOT NULL DEFAULT 'en' CHECK (language ~ '^[a-z]{2}$'),
  category        text NOT NULL DEFAULT 'UTILITY' CHECK (category IN ('UTILITY', 'MARKETING')),
  subject         text CHECK (subject IS NULL OR length(subject) <= 160),     -- email subject
  body            text NOT NULL CHECK (length(body) BETWEEN 1 AND 1000),
  whatsapp_status text NOT NULL DEFAULT 'DRAFT' CHECK (whatsapp_status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED')),
  sms_dlt_id      text CHECK (sms_dlt_id IS NULL OR sms_dlt_id ~ '^[0-9]{10,25}$'),
  updated_at      timestamptz NOT NULL DEFAULT now(),
  UNIQUE (organization_id, name, language)
);

-- Settings and wallet (one row per school).
CREATE TABLE comms_settings (
  organization_id     uuid PRIMARY KEY REFERENCES organization (id) ON DELETE CASCADE,
  quiet_start         time NOT NULL DEFAULT '21:00',
  quiet_end           time NOT NULL DEFAULT '07:00',
  monthly_cap         integer NOT NULL DEFAULT 5000 CHECK (monthly_cap BETWEEN 0 AND 1000000),
  provider            text NOT NULL DEFAULT 'TEST' CHECK (provider IN ('TEST', 'META', 'GUPSHUP')),
  whatsapp_rate_paise integer NOT NULL DEFAULT 0 CHECK (whatsapp_rate_paise BETWEEN 0 AND 10000),
  sms_rate_paise      integer NOT NULL DEFAULT 0 CHECK (sms_rate_paise BETWEEN 0 AND 10000),
  balance_paise       bigint NOT NULL DEFAULT 0,
  updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE comms_wallet_entry (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  amount_paise    bigint NOT NULL,              -- + top-up / refund, - usage
  reason          text NOT NULL CHECK (length(reason) BETWEEN 1 AND 200),
  broadcast_id    uuid,
  actor_id        uuid REFERENCES app_user (id) ON DELETE SET NULL,
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX comms_wallet_entry_idx ON comms_wallet_entry (organization_id, created_at DESC);

-- Broadcasts (circulars, alerts).
CREATE TABLE comms_broadcast (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  title           text NOT NULL CHECK (length(title) BETWEEN 1 AND 160),
  details         text CHECK (details IS NULL OR length(details) <= 8000),   -- shown on the circular page
  template_name   text NOT NULL,
  channels        text[] NOT NULL,                                          -- in fallback order
  audience_tags   text[] NOT NULL DEFAULT '{}',                             -- empty = all contacts
  ack_required    boolean NOT NULL DEFAULT false,
  consent_question text CHECK (consent_question IS NULL OR length(consent_question) <= 300),
  emergency       boolean NOT NULL DEFAULT false,                           -- ignores quiet hours
  status          text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'SCHEDULED', 'SENDING', 'SENT', 'CANCELLED')),
  send_at         timestamptz,
  sent_at         timestamptz,
  created_by      uuid REFERENCES app_user (id) ON DELETE SET NULL,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX comms_broadcast_status_idx ON comms_broadcast (status, send_at);

CREATE TABLE comms_delivery (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  broadcast_id    uuid NOT NULL REFERENCES comms_broadcast (id) ON DELETE CASCADE,
  contact_id      uuid REFERENCES comms_contact (id) ON DELETE SET NULL,
  contact_name    text NOT NULL,
  channel         text CHECK (channel IN ('WHATSAPP', 'SMS', 'EMAIL')),
  address         text,
  token           text NOT NULL UNIQUE,                                     -- personal circular link
  status          text NOT NULL CHECK (status IN ('QUEUED', 'SENT', 'DELIVERED', 'FAILED', 'SKIPPED')),
  skip_reason     text,
  provider_ref    text,
  test_mode       boolean NOT NULL DEFAULT false,
  cost_paise      integer NOT NULL DEFAULT 0,
  sent_at         timestamptz,
  read_at         timestamptz,
  ack_at          timestamptz,
  consent_answer  boolean,
  created_at      timestamptz NOT NULL DEFAULT now(),
  UNIQUE (broadcast_id, contact_id)
);
CREATE INDEX comms_delivery_broadcast_idx ON comms_delivery (broadcast_id, status);

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['comms_contact', 'comms_consent_log', 'comms_template', 'comms_settings',
                           'comms_wallet_entry', 'comms_broadcast', 'comms_delivery'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format(
      'CREATE POLICY %I ON %I USING (organization_id = sb_current_org() OR sb_is_system())'
      ' WITH CHECK (organization_id = sb_current_org() OR sb_is_system())', t || '_tenant', t);
  END LOOP;
END
$$;

-- ---------------------------------------------------------------------------------------------
-- Catalogue and plans: the hub is part of every ERP plan (messaging itself is a prepaid wallet).
-- ---------------------------------------------------------------------------------------------
INSERT INTO module_catalogue (code, name, description, features) VALUES
  ('COMMS', 'Communication hub', 'WhatsApp / SMS / email circulars with opt-in, templates, receipts and acknowledgements',
   ARRAY['broadcasts', 'templates', 'consent_register', 'acknowledgements', 'multilingual', 'scheduling']);

UPDATE plan p
   SET entitlements = p.entitlements || jsonb_build_array(jsonb_build_object(
         'module', 'COMMS',
         'depth', CASE p.code WHEN 'LITE' THEN 'LITE' WHEN 'STANDARD' THEN 'STANDARD' ELSE 'ADVANCED' END,
         'features', '["broadcasts", "templates", "consent_register", "acknowledgements", "multilingual", "scheduling"]'::jsonb,
         'limits', jsonb_build_object('contacts', CASE p.code WHEN 'LITE' THEN 1500 WHEN 'STANDARD' THEN 4000 ELSE -1 END)))
 WHERE p.code IN ('LITE', 'STANDARD', 'GLOBAL');

SELECT set_config('app.system', 'on', true);
INSERT INTO entitlement (organization_id, module_code, plan_code, depth, features, limits)
SELECT DISTINCT ON (en.organization_id) en.organization_id, 'COMMS', en.plan_code,
       CASE en.plan_code WHEN 'LITE' THEN 'LITE' WHEN 'STANDARD' THEN 'STANDARD' ELSE 'ADVANCED' END,
       ARRAY['broadcasts', 'templates', 'consent_register', 'acknowledgements', 'multilingual', 'scheduling'],
       jsonb_build_object('contacts', CASE en.plan_code WHEN 'LITE' THEN 1500 WHEN 'STANDARD' THEN 4000 ELSE -1 END)
  FROM entitlement en
 WHERE en.plan_code IN ('LITE', 'STANDARD', 'GLOBAL')
ON CONFLICT (organization_id, module_code) DO NOTHING;
SELECT set_config('app.system', '', true);
