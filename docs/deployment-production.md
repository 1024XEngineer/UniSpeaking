# UniSpeaking 生产部署

生产环境采用服务器源码部署：服务器固定使用 `/opt/unispeaking`，定时同步主仓库 `main`，在本地构建应用镜像并通过 Docker Compose 重启应用服务。

完整的首次切换、定时任务安装、配置变更、数据库保护、健康检查和回滚步骤请阅读 [`deployment-source.md`](deployment-source.md)。

生产数据保护要求始终有效：保留 `/opt/unispeaking/deploy/env/.env`、现有 PostgreSQL Volume `deploy_postgres_data`、MinIO 数据、TLS 证书和监控资源；禁止 `docker compose down -v`、`docker volume prune` 或删除数据库 Volume。
