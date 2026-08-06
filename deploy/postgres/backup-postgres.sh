#!/usr/bin/env bash

set -euo pipefail

BASE_DIR="${UNISPEAKING_DIR:-/opt/unispeaking}"
COMPOSE_FILE="$BASE_DIR/deploy/docker-compose.prod.yml"
ENV_FILE="$BASE_DIR/deploy/env/.env"
BACKUP_DIR="${UNISPEAKING_BACKUP_DIR:-$BASE_DIR/backups/postgres}"
RETENTION_DAYS="${UNISPEAKING_BACKUP_RETENTION_DAYS:-14}"

mkdir -p "$BACKUP_DIR"

timestamp=$(date -u +%Y%m%dT%H%M%SZ)
backup_file="$BACKUP_DIR/unispeaking-$timestamp.dump"

docker compose \
  --env-file "$ENV_FILE" \
  -f "$COMPOSE_FILE" \
  exec -T postgres \
  pg_dump \
    -U unispeaking \
    -d unispeaking \
    --format=custom \
    --no-owner \
  > "$backup_file"

sha256sum "$backup_file" > "$backup_file.sha256"

find "$BACKUP_DIR" \
  -type f \
  -name 'unispeaking-*.dump' \
  -mtime "+$RETENTION_DAYS" \
  -delete

find "$BACKUP_DIR" \
  -type f \
  -name 'unispeaking-*.dump.sha256' \
  -mtime "+$RETENTION_DAYS" \
  -delete

printf 'PostgreSQL backup created: %s\n' "$backup_file"
