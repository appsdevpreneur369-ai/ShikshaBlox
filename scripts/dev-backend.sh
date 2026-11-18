#!/usr/bin/env bash
# Runs the API on the host for local development/demo (infra via docker compose; see README).
set -euo pipefail
cd "$(dirname "$0")/../backend"
export MAVEN_OPTS="${MAVEN_OPTS:--Xmx768m -XX:+UseSerialGC}"
export SEED_ENABLED="${SEED_ENABLED:-true}"
export SEED_PASSWORD="${SEED_PASSWORD:-Shiksha@Dev2026}"
export SITE_REVALIDATE_URL="${SITE_REVALIDATE_URL:-http://localhost:3000/api/revalidate}"
export SITE_REVALIDATE_SECRET="${SITE_REVALIDATE_SECRET:-dev-revalidate-secret-0123456789}"
export MEDIA_PUBLIC_BASE_URL="${MEDIA_PUBLIC_BASE_URL:-http://media.shikshablox.localhost/sb-public}"
export S3_SIGNING_ENDPOINT="${S3_SIGNING_ENDPOINT:-http://media.shikshablox.localhost}"
export SPRING_MAIL_HOST="${SPRING_MAIL_HOST:-localhost}"
export SPRING_MAIL_PORT="${SPRING_MAIL_PORT:-1025}"
exec ./mvnw -q spring-boot:run -Dspotless.check.skip=true -Dspring-boot.run.jvmArguments="-Xmx768m"
