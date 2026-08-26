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
→ production Environment 审批后上传非敏感 release 配置包
→ SSH 只传递 release SHA
→ 服务器 root-owned 固定入口校验配置包、pull + up -d
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
DEPLOY_CONFIG_SIGNING_KEY
```

PR 和 Main CI 不读取生产 Secrets。前端公开构建变量只能放 Variables；数据库密码、JWT、供应商密钥、邮件密码、TURN shared secret 和管理员密码不得进入 `VITE_*`、Docker build args、镜像层或 Artifact。

服务器使用专用 GHCR 只读凭据，至少具备 `read:packages`，不能用于推送镜像或修改源码。

## 服务器 deploy 用户

以下命令只在服务器首次配置时由 root 执行。不要把 `deploy` 加入 `docker` 组；Actions 只需要通过受限 sudo 调用固定入口。

```bash
adduser --disabled-password --gecos "" deploy
install -d -o deploy -g deploy -m 700 /home/deploy/.ssh
install -o deploy -g deploy -m 600 /path/to/authorized_keys /home/deploy/.ssh/authorized_keys
```

`authorized_keys` 中只放本次 CD 使用的 ed25519 公钥，并加入 `restrict`（或至少 `no-agent-forwarding,no-port-forwarding,no-X11-forwarding`）选项。GitHub Environment Secret `DEPLOY_USER` 填 `deploy`，`DEPLOY_PORT` 填 SSH 端口；`DEPLOY_KNOWN_HOSTS` 使用管理员在可信网络中执行 `ssh-keyscan` 后人工核对的结果。

服务器 root 还需要登录 GHCR。使用仅有 `read:packages` 的短期或专用 Token，凭据只写入 root 的 Docker 配置：

```bash
printf '%s' '<GHCR_READ_TOKEN>' | docker login ghcr.io --username '<GHCR_USER>' --password-stdin
```

不要把该 Token 写入仓库、`.env`、Actions 日志或镜像构建参数。确认 `/root/.docker/config.json` 权限为 `600`。

## 服务器目录和文件

服务器使用独立 CD 目录：

```text
/opt/unispeaking-cd/
├── incoming/                         GitHub Actions 上传的临时配置包
├── releases/<commit SHA>/deploy/     对应 commit 的不可变配置
├── deploy/env/.env                   服务器生产配置，不由 Workflow 覆盖
├── releases/<commit SHA>/.images     对应发布的镜像地址
└── current -> releases/<commit SHA>  当前生效配置
```

`deploy-release.sh` 必须由 root 预先安装为 `/usr/local/sbin/unispeaking-deploy`，所有者为 `root:root`，权限为 `0755`。生产 Workflow 不上传或覆盖它，也不上传 `.env`、证书或其他 Secret。Workflow 会将当前 commit 中的以下非敏感文件打包上传到 `incoming/`，并使用 GitHub `production` Environment Secret `DEPLOY_CONFIG_SIGNING_KEY` 生成签名：

```text
deploy/docker-compose.prod.yml
deploy/nginx/nginx.prod.conf
deploy/coturn/turnserver.conf
```

在受信任的管理员工作站一次性生成 RSA 签名密钥。私钥完整内容保存为 GitHub `production` Environment Secret `DEPLOY_CONFIG_SIGNING_KEY`；公钥安装到服务器。两者都不得提交到仓库：

```bash
umask 077
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 \
  -out deploy-config-signing-private.pem
openssl pkey -in deploy-config-signing-private.pem -pubout \
  -out deploy-config-signing-public.pem
```

服务器必须预先安装与该 Secret 对应的 root-owned 公钥：

```text
/etc/unispeaking/deploy-config-signing-public.pem
```

固定入口会先将上传包复制到 root-owned 临时文件，再使用该公钥验签，然后校验归档路径清单并解包到 `releases/<commit SHA>/`。签名校验失败、归档内容超出白名单或镜像 `pull` 失败时，不会执行该配置。仅在镜像 `pull` 成功后才切换 `current`。生产 `.env` 仅保存服务器运行配置，权限必须为 600，不由 Workflow 覆盖；TLS 证书、监控 Agent 和数据库 Volume 也不进入配置包。`.images` 由固定入口原子更新，保存三个应用镜像地址，三个地址必须来自同一 SHA：

```dotenv
UNISPEAKING_BACKEND_IMAGE=ghcr.io/1024xengineer/unispeaking-backend:sha-<commit SHA>
UNISPEAKING_FRONTEND_IMAGE=ghcr.io/1024xengineer/unispeaking-frontend:sha-<commit SHA>
UNISPEAKING_ADMIN_IMAGE=ghcr.io/1024xengineer/unispeaking-admin:sha-<commit SHA>
```

生产 Compose 必须保持项目名 `deploy`，并使用现有 `deploy_postgres_data`。不得创建新数据库 Volume。monitoring 网络、OpenTelemetry Agent、证书和 Umami 不随应用镜像同步或删除。

deploy 用户只允许上传配置包到 `/opt/unispeaking-cd/incoming`，不允许直接运行 Docker、修改 release/current 配置或替换 root 脚本。服务器初始化时由 root 执行：

```bash
install -o root -g root -m 0755 deploy/scripts/deploy-release.sh /usr/local/sbin/unispeaking-deploy
install -o root -g root -m 0644 deploy-config-signing-public.pem /etc/unispeaking/deploy-config-signing-public.pem
install -d -o deploy -g deploy -m 0700 /opt/unispeaking-cd/incoming
install -d -o root -g root -m 0755 /opt/unispeaking-cd/releases
install -d -o root -g root -m 0755 /opt/unispeaking-cd/deploy/env
chmod 600 /opt/unispeaking-cd/deploy/env/.env
```

`/etc/sudoers.d/unispeaking-deploy` 只授予固定入口：

```sudoers
deploy ALL=(root) NOPASSWD: /usr/local/sbin/unispeaking-deploy *
```

保存后以 root 执行 `visudo -cf /etc/sudoers.d/unispeaking-deploy` 校验。首次切换前，人工将本目录的生产 Compose、Nginx、TURN 配置和 `.env` 安装到 `/opt/unispeaking-cd/deploy/`，并确认监控网络、Agent、证书及外部 Volume 存在。

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
docker exec deploy-postgres-1 pg_dump -U unispeaking -d unispeaking -Fc > "$BACKUP_PATH"
test -s "$BACKUP_PATH"
sha256sum "$BACKUP_PATH"
docker exec -i deploy-postgres-1 pg_restore --list < "$BACKUP_PATH" >/tmp/pre-cd.list
```

不要执行 `docker compose down -v`、`docker volume prune`、`docker volume rm deploy_postgres_data` 或 `docker compose up -d --build`。

## 部署和验收

固定脚本按以下顺序执行：

```text
校验 root、路径、权限、证书、监控网络、Agent、镜像 SHA、Compose project 和 Volume
→ config --quiet
→ pull
→ up -d
→ backend readiness
→ 首页和 Admin HTTP 检查
```

失败时返回非零。GHCR 拉取失败或 Compose 校验失败时，不停止当前容器。部署后检查 PostgreSQL、Flyway、Nginx、监控、日志、首页、Admin、登录、WebSocket、FreeChat、IELTS、Interview、Custom Scene、评分、报告和对象存储。

公网 `/backend/actuator/` 必须保持不可访问；TURN 保留为 profile，未完成 DNS、安全组和网络验收前不得启动。

## 回滚和数据库规则

应用回滚使用上一个已验收 SHA：由 root 执行 `/usr/local/sbin/unispeaking-deploy <已验收的40位SHA>`，脚本依次执行 `config --quiet`、`pull`、`up -d` 和健康检查。不要手工改成 `main` 标签。

应用镜像回滚不等于数据库回滚。已执行不可逆 migration、schema 不兼容或数据格式已变化时，不能简单回退镜像；必须使用兼容代码、前向修复 migration 或验证过的备份恢复流程。不得修改已执行的 Flyway migration。

每次发布记录 commit SHA、Actions run ID、镜像 digest、审批人、时间、Compose 版本、容器状态、readiness、HTTP 状态、Flyway 结果和失败日志摘要。
