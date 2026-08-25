#!/usr/bin/env bash

set -euo pipefail

# Fixed server-side deployment entry point. The caller may select only the
# controlled CD root and public URL; it cannot provide arbitrary shell code.
CD_DIR=${UNISPEAKING_CD_DIR:-/opt/unispeaking-cd}
PUBLIC_BASE_URL=${UNISPEAKING_PUBLIC_BASE_URL:-https://unispeaking.qnsdk.com}
COMPOSE_PROJECT=deploy
COMPOSE_FILE="$CD_DIR/deploy/docker-compose.prod.yml"
RUNTIME_ENV="$CD_DIR/deploy/env/.env"
IMAGES_ENV="$CD_DIR/deploy/env/.images"
EXPECTED_VOLUME=deploy_postgres_data
EXPECTED_USER=root

die() {
  echo "部署检查失败：$*" >&2
  exit 1
}

command -v docker >/dev/null 2>&1 || die "未找到 docker"
command -v curl >/dev/null 2>&1 || die "未找到 curl"

[[ "$(id -un)" == "$EXPECTED_USER" ]] || die "必须通过受限 sudo 入口以 root 执行"
[[ -f "$COMPOSE_FILE" ]] || die "Compose 文件不存在"
[[ -f "$RUNTIME_ENV" ]] || die "生产环境文件不存在"
[[ -f "$IMAGES_ENV" ]] || die "镜像版本文件不存在"
[[ "$(stat -c '%a' "$RUNTIME_ENV")" == 600 ]] || die ".env 权限必须为 600"

for required_path in \
  "$CD_DIR/deploy/nginx/nginx.prod.conf" \
  "$CD_DIR/deploy/coturn/turnserver.conf"; do
  [[ -f "$required_path" ]] || die "部署配置不存在：$required_path"
done

get_image() {
  local name="$1"
  local value
  value=$(awk -F= -v key="$name" '$1 == key { print substr($0, index($0, "=") + 1); exit }' "$IMAGES_ENV")
  [[ "$value" =~ ^ghcr\.io/1024xengineer/unispeaking-(backend|frontend|admin):sha-[0-9a-f]{40}$ ]] ||
    die "$name 不是完整的 GHCR SHA 镜像"
  printf '%s\n' "$value"
}

backend_image=$(get_image UNISPEAKING_BACKEND_IMAGE)
frontend_image=$(get_image UNISPEAKING_FRONTEND_IMAGE)
admin_image=$(get_image UNISPEAKING_ADMIN_IMAGE)

release_sha=${backend_image##*:sha-}
[[ "${frontend_image##*:sha-}" == "$release_sha" ]] || die "三个应用镜像不是同一 commit"
[[ "${admin_image##*:sha-}" == "$release_sha" ]] || die "三个应用镜像不是同一 commit"

docker volume inspect "$EXPECTED_VOLUME" >/dev/null 2>&1 || die "生产 Volume 不存在：$EXPECTED_VOLUME"

compose=(
  docker compose
  --env-file "$RUNTIME_ENV"
  --env-file "$IMAGES_ENV"
  --project-name "$COMPOSE_PROJECT"
  --file "$COMPOSE_FILE"
)

config_volumes=$(mktemp)
config_images=$(mktemp)
config_json=$(mktemp)
trap 'rm -f "$config_volumes" "$config_images" "$config_json"' EXIT

"${compose[@]}" config --quiet
"${compose[@]}" config --format json >"$config_json"
grep -Fq '"external": true' "$config_json" || die "生产 Volume 未声明为 external"
grep -Fq '"name": "deploy_postgres_data"' "$config_json" || die "生产 Volume 名称不匹配"
grep -Fq '"build"' "$config_json" && die "Compose 仍包含 build 配置"
"${compose[@]}" config --volumes >"$config_volumes"
grep -Fxq postgres_data "$config_volumes" || die "Compose 未声明 postgres_data"
"${compose[@]}" config --images >"$config_images"
grep -Fxq "$backend_image" "$config_images" || die "Compose backend 镜像不匹配"
grep -Fxq "$frontend_image" "$config_images" || die "Compose frontend 镜像不匹配"
grep -Fxq "$admin_image" "$config_images" || die "Compose admin 镜像不匹配"

echo "拉取发布镜像：sha-$release_sha"
"${compose[@]}" pull

echo "启动发布版本"
"${compose[@]}" up -d

echo "等待 backend readiness"
for attempt in {1..30}; do
  if "${compose[@]}" exec -T nginx wget -qO- http://backend:8080/actuator/health 2>/dev/null |
    grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'; then
    break
  fi
  [[ "$attempt" -lt 30 ]] || die "backend readiness 未通过"
  sleep 2
done

curl --fail --silent --show-error --max-time 20 \
  -o /dev/null "$PUBLIC_BASE_URL/" || die "首页检查失败"
curl --fail --silent --show-error --max-time 20 \
  -o /dev/null "$PUBLIC_BASE_URL/admin/" || die "Admin 检查失败"

echo "部署成功：sha-$release_sha"
"${compose[@]}" ps --format json
