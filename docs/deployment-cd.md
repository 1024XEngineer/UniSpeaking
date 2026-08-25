# UniSpeaking CD 部署说明

本流程将应用部署从“服务器拉源码并构建”切换为“GitHub Actions 构建并推送私有 GHCR，服务器只拉取并运行镜像”。服务器不保存源码，不读取 GitHub 仓库，也不执行应用构建。

## 发布链路

```text
PR CI
→ 合并 main
→ Main CI / required 成功
→ CD 校验当前 main HEAD
→ 构建 backend、frontend、admin
→ 推送 GHCR
→ production Environment 审批
→ 同步非敏感部署配置
→ 服务器固定脚本 pull + up -d
→ readiness、HTTP 和业务验收
```

生产镜像使用以下地址：

```text
ghcr.io/1024xengineer/unispeaking-backend
ghcr.io/1024xengineer/unispeaking-frontend
ghcr.io/1024xengineer/unispeaking-admin
```

每个镜像至少推送 `sha-<完整 commit SHA>` 和 `main` 两个标签。生产只能使用同一 commit 的 SHA 标签；`main` 只能作为快捷标签，不能作为回滚依据。

## GitHub 配置

仓库需要启用 Packages 写权限，并创建 `production` Environment，设置 required reviewers。Environment Secrets：

```text
DEPLOY_HOST
DEPLOY_PORT
DEPLOY_USER
DEPLOY_SSH_PRIVATE_KEY
DEPLOY_KNOWN_HOSTS
```

PR 和 Main CI 不读取生产 Secrets。前端公开构建变量只能放 Variables；数据库密码、JWT、供应商密钥、邮件密码、TURN shared secret 和管理员密码不得进入 `VITE_*`、Docker build args、镜像层或 Artifact。

服务器使用专用 GHCR 只读凭据，至少具备 `read:packages`，不能用于推送镜像或修改源码。

## 服务器目录和文件

服务器使用独立 CD 目录：

```text
/opt/unispeaking-cd/
├── deploy/
│   ├── docker-compose.prod.yml
│   ├── nginx/nginx.prod.conf
│   ├── coturn/turnserver.conf
│   └── env/
│       ├── .env
│       └── .images
└── deploy-release.sh
```

`.env` 仅保存生产运行配置，权限必须为 600，不由 Workflow 覆盖。`.images` 仅保存三个应用镜像地址，三个地址必须来自同一 SHA：

```dotenv
UNISPEAKING_BACKEND_IMAGE=ghcr.io/1024xengineer/unispeaking-backend:sha-<commit SHA>
UNISPEAKING_FRONTEND_IMAGE=ghcr.io/1024xengineer/unispeaking-frontend:sha-<commit SHA>
UNISPEAKING_ADMIN_IMAGE=ghcr.io/1024xengineer/unispeaking-admin:sha-<commit SHA>
```

生产 Compose 必须保持项目名 `deploy`，并使用现有 `deploy_postgres_data`。不得创建新数据库 Volume。monitoring 网络、OpenTelemetry Agent、证书和 Umami 不随应用镜像同步或删除。

## 首次部署前检查

首次切换必须在维护窗口执行，并完成：

1. 保存当前容器、镜像 ID、Compose 来源和业务健康基线；
2. 确认 `deploy_postgres_data` 存在且挂载正确；
3. 生成 PostgreSQL Custom Format 备份；
4. 记录备份 SHA256，并用 `pg_restore --list` 验证；
5. 将备份复制到独立磁盘、对象存储或其他服务器；
6. 确认三个 GHCR SHA 镜像可拉取；
7. `docker compose config --quiet` 成功，且没有 `build`、新 Volume 或缺少 monitoring 网络。

备份示例：

```bash
BACKUP_PATH="/opt/backups/unispeaking/pre-cd/pre-cd-$(date -u +%Y%m%dT%H%M%SZ).dump"
docker exec -T deploy-postgres-1 pg_dump -U unispeaking -d unispeaking -Fc > "$BACKUP_PATH"
test -s "$BACKUP_PATH"
sha256sum "$BACKUP_PATH"
docker exec -i deploy-postgres-1 pg_restore --list < "$BACKUP_PATH" >/tmp/pre-cd.list
```

不要执行 `docker compose down -v`、`docker volume prune`、`docker volume rm deploy_postgres_data` 或 `docker compose up -d --build`。

## 部署和验收

固定脚本按以下顺序执行：

```text
校验用户、路径、权限、镜像 SHA、Compose project 和 Volume
→ config --quiet
→ pull
→ up -d
→ backend readiness
→ 首页和 Admin HTTP 检查
```

失败时返回非零。GHCR 拉取失败或 Compose 校验失败时，不停止当前容器。部署后检查 PostgreSQL、Flyway、Nginx、监控、日志、首页、Admin、登录、WebSocket、FreeChat、IELTS、Interview、Custom Scene、评分、报告和对象存储。

公网 `/backend/actuator/` 必须保持不可访问；TURN 保留为 profile，未完成 DNS、安全组和网络验收前不得启动。

## 回滚和数据库规则

应用回滚使用上一个已验收 SHA：修改 `.images`，依次执行 `config --quiet`、`pull`、`up -d` 和健康检查。

应用镜像回滚不等于数据库回滚。已执行不可逆 migration、schema 不兼容或数据格式已变化时，不能简单回退镜像；必须使用兼容代码、前向修复 migration 或验证过的备份恢复流程。不得修改已执行的 Flyway migration。

每次发布记录 commit SHA、Actions run ID、镜像 digest、审批人、时间、Compose 版本、容器状态、readiness、HTTP 状态、Flyway 结果和失败日志摘要。
