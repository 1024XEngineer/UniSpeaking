#!/usr/bin/env bash

set -euo pipefail

VERIFY_DIR=${UNISPEAKING_VERIFY_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/.local-cd-verify}
PROJECT_NAME=${UNISPEAKING_VERIFY_PROJECT:-unispeaking-cd-verify}
VERIFY_HTTP_PORT=${VERIFY_HTTP_PORT:-18080}
COMPOSE_FILE="$VERIFY_DIR/deploy/docker-compose.verify.yml"
RUNTIME_ENV="$VERIFY_DIR/deploy/env/.env.verify"
IMAGES_ENV="$VERIFY_DIR/deploy/env/.images"

die() {
  echo "本机验证失败：$*" >&2
  exit 1
}

command -v docker >/dev/null 2>&1 || die "未找到 docker"
command -v curl >/dev/null 2>&1 || die "未找到 curl"
[[ -f "$COMPOSE_FILE" ]] || die "验证 Compose 不存在：$COMPOSE_FILE"
[[ -f "$RUNTIME_ENV" ]] || die "验证环境文件不存在：$RUNTIME_ENV"
[[ -f "$IMAGES_ENV" ]] || die "镜像版本文件不存在：$IMAGES_ENV"

get_image() {
  local name="$1"
  local value
  value=$(awk -F= -v key="$name" '$1 == key { print substr($0, index($0, "=") + 1); exit }' "$IMAGES_ENV")
  [[ "$value" =~ ^ghcr\.io/[^/]+/unispeaking-(backend|frontend|admin):sha-[0-9a-f]{40}$ ]] ||
    die "$name 不是完整的 GHCR SHA 镜像"
  printf '%s\n' "$value"
}

backend_image=$(get_image UNISPEAKING_BACKEND_IMAGE)
frontend_image=$(get_image UNISPEAKING_FRONTEND_IMAGE)
admin_image=$(get_image UNISPEAKING_ADMIN_IMAGE)
release_sha=${backend_image##*:sha-}
[[ "${frontend_image##*:sha-}" == "$release_sha" ]] || die "三个镜像不是同一 commit"
[[ "${admin_image##*:sha-}" == "$release_sha" ]] || die "三个镜像不是同一 commit"

compose=(
  docker compose
  --env-file "$RUNTIME_ENV"
  --env-file "$IMAGES_ENV"
  --project-name "$PROJECT_NAME"
  --file "$COMPOSE_FILE"
)

"${compose[@]}" config --quiet
config_json=$(mktemp)
trap 'rm -f "$config_json"' EXIT
"${compose[@]}" config --format json > "$config_json"
grep -Fq 'verify_postgres_data' "$config_json" || die "未使用本机验证 Volume"
grep -Fq 'build' "$config_json" && die "验证 Compose 仍包含 build"
grep -Fq 'deploy_postgres_data' "$config_json" && die "错误使用生产 Volume"

"${compose[@]}" pull
"${compose[@]}" up -d

for attempt in {1..30}; do
  if "${compose[@]}" exec -T nginx wget -qO- http://backend:8080/actuator/health 2>/dev/null |
    grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'; then
    break
  fi
  [[ "$attempt" -lt 30 ]] || die "backend readiness 未通过"
  sleep 2
done

base_url="http://127.0.0.1:${VERIFY_HTTP_PORT}"
curl --fail --silent --show-error --max-time 20 -o /dev/null "$base_url/" || die "首页检查失败"
curl --fail --silent --show-error --max-time 20 -o /dev/null "$base_url/admin/" || die "Admin 检查失败"
[[ "$(curl --silent --output /dev/null --write-out '%{http_code}' "$base_url/backend/actuator/health")" == 404 ]] || die "Actuator 未被 Nginx 拦截"

echo "本机验证通过：sha-$release_sha"
"${compose[@]}" ps
