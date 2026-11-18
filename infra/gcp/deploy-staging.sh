#!/usr/bin/env bash
# Deploys the staging stack to Cloud Run (images built by infra/gcp/cloudbuild.yaml).
# Usage (Git Bash):  TAG=v1 bash infra/gcp/deploy-staging.sh
# One-time setup (already done for project-87cd...): Cloud SQL sb-staging-pg, GCS buckets,
# service accounts sb-api / sb-web, Secret Manager secrets, Artifact Registry "shikshablox".
set -euo pipefail

P=${PROJECT:-project-87cd233e-951b-43e4-886}
R=${REGION:-asia-south1}
TAG=${TAG:-v1}
N=$(gcloud projects describe "$P" --format='value(projectNumber)')
IMG="$R-docker.pkg.dev/$P/shikshablox"
API_SA="sb-api@$P.iam.gserviceaccount.com"
WEB_SA="sb-web@$P.iam.gserviceaccount.com"
SQL="$P:$R:sb-staging-pg"

# Cloud Run URLs are deterministic: https://<service>-<project number>.<region>.run.app
API_URL="https://shikshablox-api-$N.$R.run.app"
SITE_HOST="moriah-$N.$R.run.app"
ADMIN_URL="https://shikshablox-studio-$N.$R.run.app"
SITE_URL="https://$SITE_HOST"
JDBC="jdbc:postgresql:///shikshablox?cloudSqlInstance=$SQL&socketFactory=com.google.cloud.sql.postgres.SocketFactory&sslmode=disable"

echo "== API"
gcloud run deploy shikshablox-api --project "$P" --region "$R" --image "$IMG/api:$TAG" \
  --service-account "$API_SA" --allow-unauthenticated --port 8080 \
  --cpu 1 --memory 1Gi --min-instances 1 --max-instances 3 --no-cpu-throttling \
  --add-cloudsql-instances "$SQL" \
  --set-env-vars "^@^SPRING_PROFILES_ACTIVE=staging,docker@DB_URL=$JDBC@DB_APP_USER=sb_app@DB_OWNER_USER=sb_owner@DB_POOL_SIZE=8\
@STORAGE_PROVIDER=gcs@S3_PUBLIC_BUCKET=sb-stg-$N-public@S3_PRIVATE_BUCKET=sb-stg-$N-private\
@MEDIA_PUBLIC_BASE_URL=https://storage.googleapis.com/sb-stg-$N-public\
@SITE_REVALIDATE_URL=$SITE_URL/api/revalidate@ADMIN_BASE_URL=$ADMIN_URL\
@SEED_ENABLED=true@SEED_EXTRA_DOMAINS=moriah=$SITE_HOST@SB_BASE_DOMAIN=shikshablox.app" \
  --set-secrets "DB_APP_PASSWORD=db-app-password:latest,DB_OWNER_PASSWORD=db-owner-password:latest,JWT_SECRET=jwt-secret:latest,SB_DATA_KEY=sb-data-key:latest,SITE_REVALIDATE_SECRET=site-revalidate-secret:latest,SEED_PASSWORD=seed-password:latest" \
  --timeout 300 \
  --quiet

echo "== Site (Moriah)"
gcloud run deploy moriah --project "$P" --region "$R" --image "$IMG/site:$TAG" \
  --service-account "$WEB_SA" --allow-unauthenticated --port 3000 \
  --cpu 1 --memory 1Gi --min-instances 1 --max-instances 3 \
  --set-env-vars "API_INTERNAL_URL=$API_URL,SB_BASE_DOMAIN=shikshablox.app,SB_SITE_PROTOCOL=https" \
  --set-secrets "SITE_REVALIDATE_SECRET=site-revalidate-secret:latest" \
  --quiet

echo "== Studio"
gcloud run deploy shikshablox-studio --project "$P" --region "$R" --image "$IMG/admin:$TAG" \
  --service-account "$WEB_SA" --allow-unauthenticated --port 3000 \
  --cpu 1 --memory 1Gi --min-instances 1 --max-instances 3 \
  --set-env-vars "API_INTERNAL_URL=$API_URL,SB_BASE_DOMAIN=shikshablox.app,SB_SITE_URLS=moriah=$SITE_URL" \
  --quiet

echo
echo "Website: $SITE_URL"
echo "Studio:  $ADMIN_URL"
echo "API:     $API_URL"
