#!/usr/bin/env bash

set -euo pipefail

# Installed once as a root-owned, fixed sudo entry point. The CD workflow may
# pass only a validated commit SHA; it cannot replace this script or Compose.
readonly ROOT_DIR="/opt/unispeaking-cd"
readonly DEPLOY_DIR="$ROOT_DIR/deploy"
readonly ENV_FILE="$DEPLOY_DIR/env/.env"
readonly INCOMING_DIR="$ROOT_DIR/incoming"
readonly RELEASES_DIR="$ROOT_DIR/releases"
readonly CURRENT_LINK="$ROOT_DIR/current"
readonly CONFIG_SIGNING_PUBLIC_KEY="/etc/unispeaking/deploy-config-signing-public.pem"
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
command -v tar >/dev/null 2>&1 || fail "未找到 tar"
command -v openssl >/dev/null 2>&1 || fail "未找到 openssl"

[[ -f "$ENV_FILE" ]] || fail "生产环境文件不存在：$ENV_FILE"
[[ "$(stat -c '%a' "$ENV_FILE")" == 600 ]] || fail ".env 权限必须为 600"
[[ -f /etc/unispeaking/certs/fullchain.pem ]] || fail "TLS fullchain.pem 不存在"
[[ -f /etc/unispeaking/certs/privkey.pem ]] || fail "TLS privkey.pem 不存在"
[[ -f "$CONFIG_SIGNING_PUBLIC_KEY" ]] || fail "部署配置签名公钥不存在：$CONFIG_SIGNING_PUBLIC_KEY"
[[ -d "$INCOMING_DIR" ]] || fail "配置上传目录不存在：$INCOMING_DIR"
[[ -d "$RELEASES_DIR" ]] || fail "release 目录不存在：$RELEASES_DIR"

readonly ARCHIVE_PATH="$INCOMING_DIR/release-$release_sha.tar.gz"
readonly SIGNATURE_PATH="$ARCHIVE_PATH.sig"
readonly RELEASE_DIR="$RELEASES_DIR/$release_sha"
readonly COMPOSE_FILE="$RELEASE_DIR/deploy/docker-compose.prod.yml"
readonly IMAGE_FILE="$RELEASE_DIR/.images"

safe_archive_entry() {
  case "$1" in
    .release-sha|deploy/|deploy/nginx/|deploy/coturn/|deploy/docker-compose.prod.yml|deploy/nginx/nginx.prod.conf|deploy/coturn/turnserver.conf)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

if [[ ! -d "$RELEASE_DIR" ]]; then
  [[ -f "$ARCHIVE_PATH" ]] || fail "发布配置包不存在：$ARCHIVE_PATH"
  [[ -f "$SIGNATURE_PATH" ]] || fail "配置包签名不存在：$SIGNATURE_PATH"

  # Snapshot upload files into root-owned files before verification. The
  # deploy user owns incoming/, so verifying the original path would allow a
  # replacement between verification and extraction.
  archive_tmp="$(mktemp "$ROOT_DIR/.release-archive.XXXXXX.tar.gz")"
  signature_tmp="$(mktemp "$ROOT_DIR/.release-signature.XXXXXX")"
  cleanup_upload_tmp() { rm -f "$archive_tmp" "$signature_tmp"; }
  trap cleanup_upload_tmp EXIT
  install -o root -g root -m 600 "$ARCHIVE_PATH" "$archive_tmp"
  install -o root -g root -m 600 "$SIGNATURE_PATH" "$signature_tmp"
  rm -f "$ARCHIVE_PATH" "$SIGNATURE_PATH"
  openssl dgst -sha256 -verify "$CONFIG_SIGNING_PUBLIC_KEY" \
    -signature "$signature_tmp" "$archive_tmp" >/dev/null 2>&1 \
    || fail "配置包签名校验失败"

  while IFS= read -r entry; do
    safe_archive_entry "$entry" || fail "配置包包含未允许的路径：$entry"
  done < <(tar -tzf "$archive_tmp")

  release_tmp="$(mktemp -d "$RELEASES_DIR/.release-$release_sha.XXXXXX")"
  cleanup_release_tmp() { rm -rf "$release_tmp"; cleanup_upload_tmp; }
  trap cleanup_release_tmp EXIT
  tar -xzf "$archive_tmp" -C "$release_tmp" --no-same-owner --no-same-permissions
  [[ -f "$release_tmp/.release-sha" ]] || fail "配置包缺少 release SHA"
  [[ "$(cat "$release_tmp/.release-sha")" == "$release_sha" ]] || fail "配置包 release SHA 不匹配"
  [[ -f "$release_tmp/deploy/docker-compose.prod.yml" ]] || fail "配置包缺少 Compose 文件"
  [[ -f "$release_tmp/deploy/nginx/nginx.prod.conf" ]] || fail "配置包缺少 Nginx 配置"
  [[ -f "$release_tmp/deploy/coturn/turnserver.conf" ]] || fail "配置包缺少 TURN 配置"
  if find "$release_tmp/deploy" -type l -print -quit | grep -q .; then
    fail "配置包不允许包含符号链接"
  fi
  mv "$release_tmp" "$RELEASE_DIR"
  trap - EXIT
  cleanup_upload_tmp
else
  # A previously verified immutable release is authoritative. Discard any
  # duplicate upload for the same SHA instead of inspecting or executing it.
  rm -f "$ARCHIVE_PATH" "$SIGNATURE_PATH"
fi

[[ -f "$COMPOSE_FILE" ]] || fail "release Compose 文件不存在：$COMPOSE_FILE"
[[ -f "$RELEASE_DIR/deploy/nginx/nginx.prod.conf" ]] || fail "release Nginx 配置不存在"
[[ -f "$RELEASE_DIR/deploy/coturn/turnserver.conf" ]] || fail "release TURN 配置不存在"

# Compose resolves env_file relative to the Compose file. Link only the
# server-owned environment directory into each immutable release.
if [[ ! -e "$RELEASE_DIR/deploy/env" ]]; then
  ln -s "$DEPLOY_DIR/env" "$RELEASE_DIR/deploy/env"
fi

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

# Change the active configuration only after all target images are local. The
# previous release directory remains available for an explicit rollback.
previous_target=""
if [[ -L "$CURRENT_LINK" ]]; then
  previous_target="$(readlink "$CURRENT_LINK")"
fi
current_tmp="$ROOT_DIR/.current.$release_sha.$$"
ln -s "$RELEASE_DIR" "$current_tmp"
mv -Tf "$current_tmp" "$CURRENT_LINK"
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
