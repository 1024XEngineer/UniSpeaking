# UniSpeaking 源码部署（生产）

生产服务器使用单一工作区 `/opt/unispeaking`。服务器定时检查主仓库 `main`，发现新提交后同步源码，在服务器本地构建三个应用镜像，并使用 Docker Compose 启动应用服务。GitHub Actions 只负责 CI，不访问生产服务器，也不推送或拉取应用镜像。

本流程不会迁移或重建数据库。PostgreSQL 使用现有外部 Volume `deploy_postgres_data`，MinIO、证书、监控 Agent 等宿主机资源保持原路径。严禁执行 `docker compose down -v`、`docker volume prune` 或删除 `deploy_postgres_data`。

## 文件职责

- `deploy/docker-compose.prod.yml`：生产 Compose。`backend`、`frontend`、`admin` 从仓库源码本地构建；PostgreSQL、Nginx 和可选 TURN 仍使用基础镜像。
- `deploy/scripts/sync-build-deploy.sh`：root 执行的同步、构建、启动和健康检查入口。脚本使用 `flock` 防止并发部署。
- `deploy/systemd/unispeaking-source-deploy.service`：一次部署任务。
- `deploy/systemd/unispeaking-source-deploy.timer`：开机 5 分钟后检查，此后每 10 分钟检查一次。
- `deploy/env/.env`：服务器私有运行时配置，不提交 Git。更新密钥或第三方配置时只修改服务器上的这个文件，然后手动运行部署服务。

## 首次切换前检查

以 root 登录服务器，先确认当前业务正常并完成数据库备份：

```bash
cd /opt/unispeaking
mkdir -p /opt/backups/unispeaking/pre-source-deploy
umask 077
docker exec -i deploy-postgres-1 \
  sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' \
  > "/opt/backups/unispeaking/pre-source-deploy/pre-source-$(date -u +%Y%m%dT%H%M%SZ).dump"
ls -lh /opt/backups/unispeaking/pre-source-deploy/
sha256sum /opt/backups/unispeaking/pre-source-deploy/*.dump
```

确认外部资源仍存在：

```bash
docker volume inspect deploy_postgres_data
docker network inspect monitoring_default >/dev/null
test -f /opt/monitoring/opentelemetry-javaagent.jar
test -f /etc/unispeaking/certs/fullchain.pem
test -f /etc/unispeaking/certs/privkey.pem
stat -c '%A %U:%G %s bytes %n' /opt/unispeaking/deploy/env/.env
test "$(stat -c '%a' /opt/unispeaking/deploy/env/.env)" = 600
```

首次切换前不要删除 `/opt/unispeaking-cd`。新流程验收成功后再单独归档旧目录。

## 同步工作区

以下操作会让跟踪文件严格匹配远程 `main`，并删除工作区内未跟踪的临时文件；服务器私有 `.env` 和 `runtime-logs/` 会保留：

```bash
cd /opt/unispeaking
git remote set-url origin https://github.com/1024XEngineer/UniSpeaking.git
git fetch --prune origin main
git reset --hard origin/main
git clean -fd -e deploy/env/ -e runtime-logs/ -e .source-deploy-state
git status --short
```

`git status --short` 只能显示服务器保留的 `deploy/env/` 或 `runtime-logs/` 内容；若出现其他修改，应停止并人工确认。

## 安装定时部署

仓库同步到包含本方案的提交后，以 root 执行：

```bash
cd /opt/unispeaking
chmod 0755 deploy/scripts/sync-build-deploy.sh
install -o root -g root -m 0644 deploy/systemd/unispeaking-source-deploy.service /etc/systemd/system/unispeaking-source-deploy.service
install -o root -g root -m 0644 deploy/systemd/unispeaking-source-deploy.timer /etc/systemd/system/unispeaking-source-deploy.timer
systemctl daemon-reload
```

先手动执行一次，确认成功后再启用定时器：

```bash
systemctl start unispeaking-source-deploy.service
journalctl -u unispeaking-source-deploy.service -n 200 --no-pager
cd /opt/unispeaking
docker compose --env-file deploy/env/.env --project-name deploy --file deploy/docker-compose.prod.yml ps
curl -fsS https://unispeaking.qnsdk.com/ >/dev/null
curl -fsS https://unispeaking.qnsdk.com/admin/ >/dev/null
```

确认无误后启用并检查定时器：

```bash
systemctl enable --now unispeaking-source-deploy.timer
systemctl list-timers unispeaking-source-deploy.timer --no-pager
```

## 每次自动部署做什么

服务执行 `sync-build-deploy.sh` 时依次执行：

1. 以 root、固定目录和固定仓库检查运行条件。
2. 获取 `origin/main`，比较当前提交和目标提交；无新提交时退出。
3. 将跟踪文件重置到目标提交，清理未跟踪临时文件，同时保留服务器 `.env` 和运行日志。
4. 校验 `.env` 权限、监控网络、OpenTelemetry Agent、`deploy_postgres_data` 和 Compose 配置。
5. 只构建 `backend`、`frontend`、`admin`，不构建 PostgreSQL 或 Nginx。
6. 执行 `docker compose up -d --no-build postgres backend frontend admin nginx`，不会删除 Volume 或执行 `down`。
7. 等待后端 readiness、首页和 Admin 返回成功。
8. 成功后把提交 SHA 写入 `/opt/unispeaking/.source-deploy-state`。

构建失败时脚本在启动新版本前退出，已有容器不会被主动停止。启动后健康检查失败时保留现场，先查看日志，再决定是否回滚。

## 配置和密钥变更

第三方密钥、数据库密码、七牛云、阿里云、科大讯飞等运行时配置只在服务器 `/opt/unispeaking/deploy/env/.env` 中维护，权限必须为 `600`，不得提交仓库或写入 Actions 日志。

修改后执行：

```bash
chmod 600 /opt/unispeaking/deploy/env/.env
systemctl start unispeaking-source-deploy.service
journalctl -u unispeaking-source-deploy.service -n 200 --no-pager
```

前端 `VITE_*` 配置会在镜像构建时写入静态资源；修改这类值后必须重新构建 `frontend`。脚本执行完整构建和健康检查。

## 数据库和迁移

应用更新可能触发 Flyway 新迁移。启动后检查迁移结果：

```bash
docker compose --env-file deploy/env/.env --project-name deploy --file deploy/docker-compose.prod.yml exec -T postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c 'SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;'
```

迁移失败时不要删除数据库容器或 Volume。保存后端日志和迁移错误，修复代码或配置后再运行服务。

## 回滚

回滚只回滚源码和应用容器，不回滚数据库数据。先暂停定时器：

```bash
systemctl stop unispeaking-source-deploy.timer
cd /opt/unispeaking
git fetch --prune origin main
git log --oneline -10
```

选择已验证的历史提交 `<GOOD_SHA>` 后执行：

```bash
git reset --hard <GOOD_SHA>
git clean -fd -e deploy/env/ -e runtime-logs/ -e .source-deploy-state
systemctl start unispeaking-source-deploy.service
journalctl -u unispeaking-source-deploy.service -n 200 --no-pager
```

验收完成后恢复定时器：

```bash
systemctl start unispeaking-source-deploy.timer
```

任何回滚都不得使用 `docker compose down -v`。

## 监控和验收

```bash
journalctl -u unispeaking-source-deploy.service -n 200 --no-pager
docker compose --env-file deploy/env/.env --project-name deploy --file deploy/docker-compose.prod.yml logs --tail=200 backend nginx
docker compose --env-file deploy/env/.env --project-name deploy --file deploy/docker-compose.prod.yml ps
```

确认首页、Admin、登录、WebSocket、ASR/TTS、FreeChat、IELTS、Interview、对象存储和后台管理功能；确认 PostgreSQL 健康且数据记录仍在。
