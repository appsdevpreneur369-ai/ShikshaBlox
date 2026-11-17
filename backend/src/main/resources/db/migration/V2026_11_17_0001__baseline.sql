-- Baseline: extensions, app-role privileges and the Spring Modulith event publication registry
-- (transactional outbox). Domain tables arrive per module from M1 onwards.
--
-- Naming: V<yyyy_mm_dd_nnnn>__<module>_<what>.sql keeps versions globally unique across modules.

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;

-- The runtime role (sb_app by default) gets DML only, never DDL, and never owns tables, so RLS
-- policies always apply to it. The role itself is created by infra (docker init script / IaC).
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sb_app') THEN
    EXECUTE 'GRANT USAGE ON SCHEMA public TO sb_app';
    EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO sb_app';
    EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO sb_app';
  END IF;
END
$$;

CREATE TABLE event_publication (
  id               UUID PRIMARY KEY,
  listener_id      TEXT NOT NULL,
  event_type       TEXT NOT NULL,
  serialized_event TEXT NOT NULL,
  publication_date TIMESTAMPTZ NOT NULL,
  completion_date  TIMESTAMPTZ
);
CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);
