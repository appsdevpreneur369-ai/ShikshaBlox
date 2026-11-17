#!/bin/sh
# Creates the schema-owner role (used by Flyway) and the non-owner runtime role (RLS applies).
# Runs once, on first start of an empty data volume.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-SQL
  CREATE ROLE ${DB_OWNER_USER} LOGIN PASSWORD '${DB_OWNER_PASSWORD}';
  CREATE ROLE ${DB_APP_USER} LOGIN PASSWORD '${DB_APP_PASSWORD}' NOSUPERUSER NOBYPASSRLS;
  ALTER DATABASE ${POSTGRES_DB} OWNER TO ${DB_OWNER_USER};
  ALTER SCHEMA public OWNER TO ${DB_OWNER_USER};
  GRANT CONNECT ON DATABASE ${POSTGRES_DB} TO ${DB_APP_USER};
SQL
