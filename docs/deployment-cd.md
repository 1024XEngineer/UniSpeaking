# CD 部署说明

原先“GitHub Actions 构建并推送 GHCR/ACR 镜像”的 CD 方案已停用。当前生产部署统一采用服务器源码部署，避免生产服务器跨境拉取大型 GHCR 镜像。

请使用 [`deployment-source.md`](deployment-source.md) 中的流程：服务器同步 `main`、本地构建 `backend`/`frontend`/`admin`，然后执行 Compose 启动和健康检查。GitHub Actions 仅执行 CI，不包含生产部署密钥和服务器操作。
