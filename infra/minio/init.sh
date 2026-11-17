#!/bin/sh
# Creates the private and public buckets on any S3-compatible store (MinIO locally).
# Only the public bucket allows anonymous reads. Retries until the store is reachable.
set -eu

export AWS_ACCESS_KEY_ID="$S3_ACCESS_KEY" AWS_SECRET_ACCESS_KEY="$S3_SECRET_KEY" AWS_DEFAULT_REGION=us-east-1
s3() { aws --endpoint-url "$S3_ENDPOINT" s3api "$@"; }

i=0
until s3 list-buckets >/dev/null 2>&1; do
  i=$((i + 1))
  [ "$i" -gt 60 ] && { echo "object storage not reachable" >&2; exit 1; }
  sleep 2
done

for b in "$S3_PRIVATE_BUCKET" "$S3_PUBLIC_BUCKET"; do
  s3 head-bucket --bucket "$b" >/dev/null 2>&1 || s3 create-bucket --bucket "$b" >/dev/null
done

s3 put-bucket-policy --bucket "$S3_PUBLIC_BUCKET" --policy "{
  \"Version\": \"2012-10-17\",
  \"Statement\": [{
    \"Effect\": \"Allow\", \"Principal\": {\"AWS\": [\"*\"]},
    \"Action\": [\"s3:GetObject\"], \"Resource\": [\"arn:aws:s3:::$S3_PUBLIC_BUCKET/*\"]
  }]
}"
echo "Buckets ready: $S3_PRIVATE_BUCKET (private), $S3_PUBLIC_BUCKET (public-read)"
