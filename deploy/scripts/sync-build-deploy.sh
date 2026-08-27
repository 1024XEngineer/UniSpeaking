#!/usr/bin/env bash

set -euo pipefail

readonly BASE_DIR="${UNISPEAKING_DIR:-/opt/unispeaking}"
readonly REPOSITORY_URL="${UNISPEAKING_REPOSITORY_URL:-https://github.com/1024XEngineer/UniSpeaking.git}"
readonly BRANCH="${UNISPEAKING_BRANCH:-main}"
readonly ENV_FILE="$BASE_DIR/deploy/env/.env"
readonly COMPOSE_FILE="$BASE_DIR/deploy/docker-compose.prod.yml"
readonly PROJECT_NAME="deploy"
readonly STATE_FILE="$BASE_DIR/.source-deploy-state"
readonly LOCK_FILE="/run/lock/unispeaking-source-deploy.lock"
readonly EXPECTED_VOLUME="deploy_postgres_data"
readonly DEFAULT_MONITORING_NETWORK="monitoring_default"
readonly DEFAULT_OTEL_AGENT="/opt/monitoring/opentelemetry-javaagent.jar"
readonly PUBLIC_HOST="unispeaking.qnsdk.com"

log() { printf '[%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }
fail() { log "部署失败：$*" >&2; exit 1; }

[[ "$(id -u)" -eq 0 ]] || fail "必须以 root 执行"
command -v git >/dev/null 2>&1 || fail "未找到 git"
command -v docker >/dev/null 2>&1 || fail "未找到 docker"
command -v curl >/dev/null 2>&1 || fail "未找到 curl"
command -v flock >/dev/null 2>&1 || fail "未找到 flock"
[[ -d "$BASE_DIR/.git" ]] || fail "不是 Git 工作区：$BASE_DIR"
[[ -f "$ENV_FILE" ]] || fail "生产环境文件不存在：$ENV_FILE"
[[ "$(stat -c '%a' "$ENV_FILE")" == 600 ]] || fail ".env 权限必须为 600"
[[ -f "$COMPOSE_FILE" ]] || fail "生产 Compose 不存在：$COMPOSE_FILE"

install -d -m 0755 "$(dirname "$LOCK_FILE")"
exec 9>"$LOCK_FILE"
flock -n 9 || { log "已有部署任务运行，跳过本次检查"; exit 0; }

git -C "$BASE_DIR" remote set-url origin "$REPOSITORY_URL"
git -C "$BASE_DIR" fetch --prune origin "$BRANCH"
target_sha="$(git -C "$BASE_DIR" rev-parse "origin/$BRANCH")"
current_sha="$(git -C "$BASE_DIR" rev-parse HEAD)"
env_sha="$(sha256sum "$ENV_FILE" | awk '{print $1}')"

if [[ -f "$STATE_FILE" ]] \
  && [[ "$(sed -n 's/^sha=//p' "$STATE_FILE" | head -n 1)" == "$target_sha" ]] \
  && [[ "$(sed -n 's/^env_sha=//p' "$STATE_FILE" | head -n 1)" == "$env_sha" ]]; then
  log "当前已部署 $target_sha，源码和环境均无变化"
  exit 0
fi

log "同步源码：$current_sha -> $target_sha"
git -C "$BASE_DIR" reset --hard "$target_sha"
# Keep server-owned runtime files while removing unexpected untracked files.
git -C "$BASE_DIR" clean -fd -e deploy/env/ -e runtime-logs/ -e .source-deploy-state
unexpected="$(git -C "$BASE_DIR" status --porcelain --untracked-files=all | grep -v '^?? deploy/env/' | grep -v '^?? runtime-logs/' || true)"
[[ -z "$unexpected" ]] || fail "同步后工作区仍有未预期改动：$unexpected"

monitoring_network="$(sed -n 's/^MONITORING_NETWORK_NAME=//p' "$ENV_FILE" | head -n 1 | tr -d "'\"")"
monitoring_network="${monitoring_network:-$DEFAULT_MONITORING_NETWORK}"
docker network inspect "$monitoring_network" >/dev/null 2>&1 || fail "监控网络不存在：$monitoring_network"

otel_agent_path="$(sed -n 's/^OTEL_JAVA_AGENT_HOST_PATH=//p' "$ENV_FILE" | head -n 1 | tr -d "'\"")"
otel_agent_path="${otel_agent_path:-$DEFAULT_OTEL_AGENT}"
[[ -f "$otel_agent_path" ]] || fail "OpenTelemetry Agent 不存在：$otel_agent_path"
docker volume inspect "$EXPECTED_VOLUME" >/dev/null 2>&1 \
  || fail "生产 Volume 不存在：$EXPECTED_VOLUME，拒绝创建新数据库 Volume"

compose=(docker compose --project-name "$PROJECT_NAME" --env-file "$ENV_FILE" --file "$COMPOSE_FILE")
"${compose[@]}" config --quiet || fail "Compose 配置校验失败"
"${compose[@]}" config --format json | grep -Fq 'deploy_postgres_data' \
  || fail "Compose 未使用 deploy_postgres_data"

log "构建应用镜像"
"${compose[@]}" build backend frontend admin || fail "应用镜像构建失败，未启动新版本"

log "启动生产服务"
"${compose[@]}" up -d --no-build postgres backend frontend admin nginx \
  || fail "Compose 启动失败"

ready=false
for _ in $(seq 1 60); do
  if "${compose[@]}" exec -T nginx wget -qO- http://backend:8080/actuator/health 2>/dev/null |
    grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'; then
    ready=true
    break
  fi
  sleep 2
done
$ready || fail "backend readiness 未在 120 秒内通过"

home_status="$(curl --fail --silent --show-error --connect-timeout 10 --max-time 20 \
  --resolve "$PUBLIC_HOST:443:127.0.0.1" -o /dev/null -w '%{http_code}' \
  "https://$PUBLIC_HOST/")" || fail "首页检查失败"
[[ "$home_status" == 200 ]] || fail "首页 HTTP 状态异常：$home_status"

admin_status="$(curl --fail --silent --show-error --connect-timeout 10 --max-time 20 \
  --resolve "$PUBLIC_HOST:443:127.0.0.1" -o /dev/null -w '%{http_code}' \
  "https://$PUBLIC_HOST/admin/")" || fail "Admin HTTP 状态异常"
[[ "$admin_status" == 200 ]] || fail "Admin HTTP 状态异常：$admin_status"

umask 077
tmp_state="$(mktemp "$BASE_DIR/.source-deploy-state.XXXXXX")"
trap 'rm -f "$tmp_state"' EXIT
printf 'sha=%s\nenv_sha=%s\ndeployed_at=%s\n' "$target_sha" "$env_sha" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$tmp_state"
mv -f "$tmp_state" "$STATE_FILE"
log "部署成功：$target_sha"
"${compose[@]}" ps
