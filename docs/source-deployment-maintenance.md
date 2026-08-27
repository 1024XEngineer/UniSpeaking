# 源码部署维护手册

本文档面向维护 UniSpeaking 生产服务器的人员。当前生产部署不使用 GHCR/ACR 应用镜像，服务器固定使用 `/opt/unispeaking`，定时同步主仓库 `main`，在服务器本地构建应用镜像并启动 Compose 服务。

完整首次切换流程见 [`deployment-source.md`](deployment-source.md)。本文档说明日常维护时可修改的内容、必须保留的资源、故障处理和回滚方式。

## 部署链路

```text
main
  -> systemd timer
  -> /opt/unispeaking
  -> git fetch/reset/clean
  -> docker compose build backend frontend admin
  -> docker compose up -d --no-build
  -> readiness、首页、Admin 检查
```

GitHub Actions 只执行 CI，不访问生产服务器。生产服务器不拉取 GHCR/ACR 应用镜像。

## 文件职责

| 文件 | 作用 |
| --- | --- |
| `deploy/docker-compose.prod.yml` | 生产服务、构建上下文、挂载和网络 |
| `deploy/scripts/sync-build-deploy.sh` | 同步、清理、构建、启动和健康检查 |
| `deploy/systemd/unispeaking-source-deploy.service` | 单次部署任务 |
| `deploy/systemd/unispeaking-source-deploy.timer` | 定时调度，当前为开机 5 分钟后检查、之后每 10 分钟检查 |
| `deploy/env/.env.example` | 非敏感配置示例 |
| `deploy/env/.env` | 服务器私有生产配置，不提交 Git |
| `deploy/nginx/nginx.prod.conf` | 生产反向代理配置 |

所有脚本和 systemd 文件只在仓库中修改，再通过 PR 合并。不要只在服务器直接修改脚本，否则下次同步会被覆盖。

## 服务器保留资源

同步脚本执行 `git reset --hard` 和 `git clean` 时会保留：

- `/opt/unispeaking/deploy/env/`，包括生产 `.env` 和环境备份；
- `/opt/unispeaking/backups/`，包括数据库和代码备份；
- `/opt/unispeaking/runtime-logs/`；
- `/opt/unispeaking/.source-deploy-state`；
- Docker 外部 Volume `deploy_postgres_data`；
- 外部网络 `monitoring_default`；
- `/etc/unispeaking/certs/`；
- `/opt/monitoring/opentelemetry-javaagent.jar`。

新增服务器持久化目录时，必须先修改脚本保留规则和本文档，并通过 PR 评审。

## 自动部署行为

脚本比较远程 `main` SHA 和 `.env` SHA。两者都未变化时退出，不重建容器。任一项变化时构建 `backend`、`frontend`、`admin`，然后启动 `postgres`、`backend`、`frontend`、`admin`、`nginx`。

Docker 会复用未变化的缓存层。PostgreSQL、Nginx 和 TURN 不在应用构建列表中。脚本不会执行 `docker compose down`，也不会删除 Volume。

构建失败时不会启动新版本；健康检查失败时保留现场，先查看日志再处理。

## 配置变更

数据库密码、JWT、七牛云、阿里云、科大讯飞、邮件等运行时密钥只修改服务器文件：

```text
/opt/unispeaking/deploy/env/.env
```

修改前备份并保持权限：

```bash
cp -a /opt/unispeaking/deploy/env/.env \
  /opt/unispeaking/backups/env-before-change-$(date -u +%Y%m%dT%H%M%SZ)
chmod 600 /opt/unispeaking/deploy/env/.env
vi /opt/unispeaking/deploy/env/.env
systemctl start --no-block unispeaking-source-deploy.service
```

`VITE_*` 值会编译进前端静态文件，修改后必须等待前端重新构建。任何密钥不得进入 Git、`VITE_*`、Docker build args 或 Actions 日志。

## 定时器管理

```bash
systemctl status unispeaking-source-deploy.timer --no-pager
systemctl list-timers --all --no-pager | grep unispeaking
```

手动触发一次部署：

```bash
systemctl start --no-block unispeaking-source-deploy.service
```

暂停和恢复自动部署：

```bash
systemctl stop unispeaking-source-deploy.timer
systemctl enable --now unispeaking-source-deploy.timer
```

## GitHub 网络故障

单次 `fetch` 失败不会修改工作区、容器或数据库。可以配置 root 的 Git 连接参数后重试：

```bash
git config --global http.version HTTP/1.1
git config --global http.lowSpeedLimit 1000
git config --global http.lowSpeedTime 120
git fetch --prune origin main
```

确认 fetch 成功后再启动部署服务；fetch 失败时不要手工执行 `reset` 或 `clean`。

## 故障定位

```bash
journalctl -u unispeaking-source-deploy.service -n 200 --no-pager
systemctl status unispeaking-source-deploy.service --no-pager -l
cd /opt/unispeaking
docker compose --env-file deploy/env/.env --project-name deploy --file deploy/docker-compose.prod.yml ps
docker compose --env-file deploy/env/.env --project-name deploy --file deploy/docker-compose.prod.yml logs --tail=200 backend nginx
```

常见错误处理：

- `dubious ownership`：执行 `git config --global --add safe.directory /opt/unispeaking`；
- `fetch` TLS 超时：按上面的 HTTP/1.1 配置重试；
- Volume 不存在：立即停止，禁止让 Compose 创建新 Volume；
- Compose 校验失败：检查 `.env` 和最近的 Compose 改动，不要重启；
- readiness 失败：检查 backend 日志和 Flyway 状态，不要删除数据库容器。

## 数据库与回滚

正式部署前生成并验证 PostgreSQL Custom Format 备份，并记录 SHA256。部署后检查 Flyway 全部为 `success = t`，确认 `deploy_postgres_data` 仍存在。

禁止使用 `docker compose down -v`、`docker volume prune`、`docker volume rm deploy_postgres_data`，也不要手工删除 `/var/lib/docker/volumes/` 内容。

回滚只回滚源码和应用容器，不回滚数据库。先暂停 timer，再选择已验证的提交：

```bash
systemctl stop unispeaking-source-deploy.timer
cd /opt/unispeaking
git fetch --prune origin main
git log --oneline -20
git reset --hard <GOOD_SHA>
git clean -fd -e deploy/env/ -e backups/ -e runtime-logs/ -e .source-deploy-state
systemctl start --no-block unispeaking-source-deploy.service
```

确认业务恢复后：

```bash
systemctl enable --now unispeaking-source-deploy.timer
```

已执行的 Flyway migration 不通过镜像回滚撤销，必须使用兼容代码或新的前向修复 migration。

## 维护原则

1. 生产服务器只保留一个源码工作区：`/opt/unispeaking`。
2. `.env`、备份、证书、监控 Agent 和 Docker Volume 属于服务器状态，不提交 Git。
3. 先备份，再同步；先手动验收，再依赖定时器。
