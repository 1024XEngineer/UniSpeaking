# Fork 与本机 CD 验证

本流程只验证镜像构建、GHCR 推送、本机拉取和隔离 Compose 运行，不连接服务器，不读取生产 Secrets，不使用 `deploy_postgres_data`，不执行生产 `cd.yml`。

## 1. Fork Actions

在个人 Fork 的 Actions 页面手动运行 `CD Verify (Fork)`，输入要验证的 40 位 commit SHA。Workflow 会将三个镜像推送到：

```text
ghcr.io/<个人账号>/unispeaking-backend:sha-<SHA>
ghcr.io/<个人账号>/unispeaking-frontend:sha-<SHA>
ghcr.io/<个人账号>/unispeaking-admin:sha-<SHA>
```

生产 `CD` Workflow 已限制为仅在 `1024XEngineer/UniSpeaking` 运行；Fork 中即使 Main CI 成功，也不会触发生产发布。Fork 只运行本文件新增的 `CD Verify (Fork)`。

## 2. 本机准备

在仓库根目录执行：

```bash
VERIFY_DIR="$PWD/.local-cd-verify"
mkdir -p "$VERIFY_DIR/deploy/env" "$VERIFY_DIR/deploy/nginx"
cp deploy/docker-compose.verify.yml "$VERIFY_DIR/deploy/"
cp deploy/nginx/nginx.verify.conf "$VERIFY_DIR/deploy/nginx/"
cp deploy/env/.env.verify.example "$VERIFY_DIR/deploy/env/.env.verify"
```

将 `.env.verify` 中的 `JWT_SECRET` 改为本机测试值；不要复制服务器 `.env`。

创建镜像版本文件：

```bash
cat > "$VERIFY_DIR/deploy/env/.images" <<'EOF'
UNISPEAKING_BACKEND_IMAGE=ghcr.io/<个人账号>/unispeaking-backend:sha-<SHA>
UNISPEAKING_FRONTEND_IMAGE=ghcr.io/<个人账号>/unispeaking-frontend:sha-<SHA>
UNISPEAKING_ADMIN_IMAGE=ghcr.io/<个人账号>/unispeaking-admin:sha-<SHA>
EOF
```

## 3. 执行本机验证

登录个人 GHCR 后运行：

```bash
chmod +x deploy/scripts/verify-local-release.sh
UNISPEAKING_VERIFY_DIR="$VERIFY_DIR" deploy/scripts/verify-local-release.sh
```

脚本会校验：

- 三个镜像是同一 SHA；
- Compose 不含 `build`；
- 不使用生产 Volume；
- 镜像可以拉取；
- backend readiness 为 UP；
- 首页和 Admin 可访问；
- 公网路径 `/backend/actuator/` 返回 404。

随后人工验证登录、WebSocket、FreeChat、IELTS、Interview、Custom Scene、评分、报告和对象存储等核心流程。

## 4. 清理验证环境

确认不再需要本机数据后执行：

```bash
docker compose \
  --env-file "$VERIFY_DIR/deploy/env/.env.verify" \
  --env-file "$VERIFY_DIR/deploy/env/.images" \
  --project-name unispeaking-cd-verify \
  --file "$VERIFY_DIR/deploy/docker-compose.verify.yml" \
  down -v
```

该命令只允许用于本机验证目录和 `verify_postgres_data`，不得在生产项目执行。
