-- Mirrors infra/postgres/init: schema owner (migrations) + non-owner runtime role (RLS applies).
CREATE ROLE sb_owner LOGIN PASSWORD 'sb_owner_test';
CREATE ROLE sb_app LOGIN PASSWORD 'sb_app_test' NOSUPERUSER NOBYPASSRLS;
ALTER DATABASE test OWNER TO sb_owner;
ALTER SCHEMA public OWNER TO sb_owner;
GRANT CONNECT ON DATABASE test TO sb_app;
