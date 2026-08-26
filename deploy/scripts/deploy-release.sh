#!/usr/bin/env bash

set -euo pipefail

# Installed once as a root-owned, fixed sudo entry point. The CD workflow may
# pass only a validated commit SHA; it cannot replace this script or Compose.
readonly ROOT_DIR="/opt/unispeaking-cd"
readonly DEPLOY_DIR="$ROOT_DIR/deploy"
readonly COMPOSE_FILE="$DEPLOY_DIR/docker-compose.prod.yml"
readonly ENV_FILE="$DEPLOY_DIR/env/.env"
readonly IMAGE_FILE="$DEPLOY_DIR/env/.images"
readonly PROJECT_NAME="deploy"
readonly EXPECTED_VOLUME="deploy_postgres_data"
readonly MONITORING_NETWORK_DEFAULT="monitoring_default"
readonly OTEL_AGENT_DEFAULT="/opt/monitoring/opentelemetry-javaagent.jar"
readonly PUBLIC_HOST="unispeaking.qnsdk.com"

fail() {
  echo "部署失败：$*" >&2
  exit 1
}

[[ $# -eq 1 ]] || fail "用法：$0 <40 位 commit SHA>"
release_sha="$1"
[[ "$release_sha" =~ ^[0-9a-f]{40}$ ]] || fail "commit SHA 格式错误"

[[ "$(id -u)" -eq 0 ]] || fail "必须通过 sudo 以 root 执行"
command -v docker >/dev/null 2>&1 || fail "未找到 docker"
command -v curl >/dev/null 2>&1 || fail "未找到 curl"
command -v awk >/dev/null 2>&1 || fail "未找到 awk"

[[ -f "$COMPOSE_FILE" ]] || fail "Compose 文件不存在：$COMPOSE_FILE"
[[ -f "$ENV_FILE" ]] || fail "生产环境文件不存在：$ENV_FILE"
[[ "$(stat -c '%a' "$ENV_FILE")" == 600 ]] || fail ".env 权限必须为 600"
[[ -f "$DEPLOY_DIR/nginx/nginx.prod.conf" ]] || fail "Nginx 配置文件不存在"
[[ -f "$DEPLOY_DIR/coturn/turnserver.conf" ]] || fail "TURN 配置文件不存在"
[[ -f /etc/unispeaking/certs/fullchain.pem ]] || fail "TLS fullchain.pem 不存在"
[[ -f /etc/unispeaking/certs/privkey.pem ]] || fail "TLS privkey.pem 不存在"

env_value() {
  local key="$1"
  awk -F= -v key="$key" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "$ENV_FILE" |
    sed -e 's/^"//' -e 's/"$//' -e "s/^'//" -e "s/'$//"
}

monitoring_network="$(env_value MONITORING_NETWORK_NAME)"
monitoring_network="${monitoring_network:-$MONITORING_NETWORK_DEFAULT}"
docker network inspect "$monitoring_network" >/dev/null 2>&1 \
  || fail "监控外部网络不存在：$monitoring_network"

otel_agent_path="$(env_value OTEL_JAVA_AGENT_HOST_PATH)"
otel_agent_path="${otel_agent_path:-$OTEL_AGENT_DEFAULT}"
[[ -f "$otel_agent_path" ]] || fail "OpenTelemetry Java Agent 不存在：$otel_agent_path"

docker volume inspect "$EXPECTED_VOLUME" >/dev/null 2>&1 \
  || fail "生产 Volume 不存在：$EXPECTED_VOLUME，拒绝创建新数据库 Volume"

image_tmp="$(mktemp "$ROOT_DIR/.images.XXXXXX")"
config_json="$(mktemp)"
config_images="$(mktemp)"
cleanup() { rm -f "$image_tmp" "$config_json" "$config_images"; }
trap cleanup EXIT
umask 077
cat > "$image_tmp" <<EOF
UNISPEAKING_BACKEND_IMAGE=ghcr.io/1024xengineer/unispeaking-backend:sha-$release_sha
UNISPEAKING_FRONTEND_IMAGE=ghcr.io/1024xengineer/unispeaking-frontend:sha-$release_sha
UNISPEAKING_ADMIN_IMAGE=ghcr.io/1024xengineer/unispeaking-admin:sha-$release_sha
EOF

get_image() {
  local key="$1"
  local value
  value="$(awk -F= -v key="$key" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "$image_tmp")"
  [[ "$value" =~ ^ghcr\.io/1024xengineer/unispeaking-(backend|frontend|admin):sha-[0-9a-f]{40}$ ]] \
    || fail "$key 不是允许的 GHCR SHA 镜像"
  printf '%s\n' "$value"
}

backend_image="$(get_image UNISPEAKING_BACKEND_IMAGE)"
frontend_image="$(get_image UNISPEAKING_FRONTEND_IMAGE)"
admin_image="$(get_image UNISPEAKING_ADMIN_IMAGE)"
[[ "${backend_image##*:sha-}" == "$release_sha" ]] || fail "backend 镜像 SHA 不匹配"
[[ "${frontend_image##*:sha-}" == "$release_sha" ]] || fail "frontend 镜像 SHA 不匹配"
[[ "${admin_image##*:sha-}" == "$release_sha" ]] || fail "admin 镜像 SHA 不匹配"

compose() {
  docker compose \
    --project-name "$PROJECT_NAME" \
    --env-file "$ENV_FILE" \
    --env-file "$image_tmp" \
    --file "$COMPOSE_FILE" \
    "$@"
}

compose config --quiet
compose config --format json > "$config_json"
grep -Eq '"external"[[:space:]]*:[[:space:]]*true' "$config_json" || fail "生产 Volume 未声明为 external"
grep -Eq '"name"[[:space:]]*:[[:space:]]*"deploy_postgres_data"' "$config_json" || fail "生产 Volume 名称不匹配"
grep -Fq '"build"' "$config_json" && fail "Compose 仍包含 build 配置"
compose config --images > "$config_images"
grep -Fxq "$backend_image" "$config_images" || fail "Compose 未解析到目标 backend 镜像"
grep -Fxq "$frontend_image" "$config_images" || fail "Compose 未解析到目标 frontend 镜像"
grep -Fxq "$admin_image" "$config_images" || fail "Compose 未解析到目标 admin 镜像"

echo "拉取发布镜像：sha-$release_sha"
compose pull

# Keep the previously active image list for a manual rollback, then switch the
# pinned release atomically only after all target images have been downloaded.
mkdir -p "$ROOT_DIR/releases"
if [[ -f "$IMAGE_FILE" ]]; then
  install -m 600 "$IMAGE_FILE" "$ROOT_DIR/releases/previous-$(date -u +%Y%m%dT%H%M%SZ).images"
fi
install -m 600 "$image_tmp" "$IMAGE_FILE"

echo "启动发布版本"
compose up -d

echo "等待 backend readiness"
for attempt in $(seq 1 60); do
  if compose exec -T nginx wget -qO- http://backend:8080/actuator/health 2>/dev/null |
    grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'; then
    break
  fi
  [[ "$attempt" -lt 60 ]] || fail "backend readiness 未在 120 秒内通过"
  sleep 2
done

home_status="$(curl --fail --silent --show-error --connect-timeout 10 --max-time 20 \
  --resolve "$PUBLIC_HOST:443:127.0.0.1" -o /dev/null -w '%{http_code}' \
  "https://$PUBLIC_HOST/")" || fail "首页检查失败"
[[ "$home_status" == 200 ]] || fail "首页 HTTP 状态异常：$home_status"

admin_status="$(curl --fail --silent --show-error --connect-timeout 10 --max-time 20 \
  --resolve "$PUBLIC_HOST:443:127.0.0.1" -o /dev/null -w '%{http_code}' \
  "https://$PUBLIC_HOST/admin/")" || fail "Admin 检查失败"
[[ "$admin_status" == 200 ]] || fail "Admin HTTP 状态异常：$admin_status"

echo "部署成功：sha-$release_sha"
compose ps --format json
