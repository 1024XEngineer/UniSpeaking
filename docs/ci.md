# UniSpeaking 持续集成与分支保护

本文档对应 RFC《UniSpeaking 最小可用 CI 方案》，说明第一阶段 CI 的运行方式、
报告位置和仓库设置。CI 只证明代码可以安全合并，不发布制品、不部署环境，也不把
Redis 引入生产运行时。

## 工作流

| 文件 | 职责 |
| --- | --- |
| `ci-pr.yml` | 在 PR 创建、重新打开、新提交和转为 Ready 时测试 GitHub merge commit |
| `ci-core.yml` | 按变更范围运行后端、前端、Docker、Compose、Nginx 和依赖检查 |
| `ci-refresh.yml` | `main` 更新后查找全部开放 PR，并行发起重检 |
| `ci-refresh-pr.yml` | 等待包含最新 `main` 的 merge SHA，跳过冲突或已变化的 PR |
| `ci-status.yml` | 在可信上下文校验当前 base、head、merge SHA 后发布 `CI / required` |
| `coverage.yml` | `main` 后端变更后生成 JaCoCo 聚合报告并通过 OIDC 上传到 Codecov |

同一 PR 的核心检查使用 `ci-pr-<PR 编号>` 并发组。出现新提交或 `main` 更新时，旧检查
会自动取消；不同 PR 可以并行执行。PR 工作流只使用只读权限，不接收仓库 Secrets。
状态写入任务不检出或执行 PR 代码，过期结果不会覆盖当前 merge SHA。

工作流文件本身发生变化时会强制运行全部检查。其他变更按路径执行：

- 后端：编译、186 项单元测试、打包、PostgreSQL/Redis 集成测试、70% 行覆盖率和镜像构建；
- Web 前端：Node.js 22、`npm ci`、路由与 Realtime 事件检查、生产构建和镜像构建；
- 移动端：Node.js 22、`npm ci`、TypeScript 检查和 Expo Web 静态导出；
- Compose、环境模板或 Nginx：配置解析或 `nginx -t`；
- Maven/npm 依赖文件：Dependency Review，High 和 Critical 阻止合并。

## 本地复现

后端单元测试、打包和覆盖率数据：

```bash
cd backend/unispeaking-server
./mvnw --batch-mode --no-transfer-progress clean verify
```

PostgreSQL 与 Redis 集成测试：

```bash
./mvnw --batch-mode --no-transfer-progress \
  -Pci-integration -DskipUnitTests verify
```

合并两类测试的 JaCoCo 数据并执行 70% 门禁：

```bash
./mvnw --batch-mode --no-transfer-progress \
  -Pcoverage-aggregate \
  -DskipUnitTests \
  -DskipIntegrationTests \
  verify
```

集成测试使用 Testcontainers，需要本机 Docker 可用；测试结束后容器会自动清理。

Web 前端：

```bash
cd frontend/web
npm ci
npm run check:routes
npm run check:realtime-events
npm run build
```

移动端：

```bash
cd frontend/mobile
npm ci
npx tsc --noEmit
EXPO_OFFLINE=1 npx expo export --platform web
```

## 报告

GitHub Actions 在对应运行的 Artifacts 中保留以下内容 30 天：

- Surefire 单元测试报告和必要日志；
- Failsafe 集成测试报告和必要日志；
- JaCoCo 单元、集成及合并覆盖率报告。

PR 不上传后端 JAR、前端 `dist` 或 Docker 镜像。用于在任务间合并覆盖率和校验 merge SHA
的临时数据只保留 1 天。

根目录 README 显示 `main` 分支的后端测试状态和 Codecov 覆盖率。覆盖率合并单元测试
及 PostgreSQL、Redis 集成测试所执行的后端 Java 代码；它不是数据库表或 SQL 语句的
覆盖率。前端尚未建立自动化测试覆盖率，因此 README 暂不显示前端覆盖率。
`coverage.yml` 上传的是 JaCoCo 聚合报告
`backend/unispeaking-server/target/site/jacoco-aggregate/jacoco.xml`，同时使用
`backend` flag 标记报告。上传通过 GitHub OIDC 鉴权，不需要配置 `CODECOV_TOKEN`。

首次启用前，仓库管理员需要在 Codecov 中安装 GitHub App 并激活
`1024XEngineer/UniSpeaking`。工作流合入 `main` 后会自动进行首次上传；也可以在
Actions 的 Coverage 工作流中手动运行。首次上传成功前，README 徽章可能显示
`unknown`。

## 首次启用与分支保护

新增工作流的首个 PR 合并前，默认分支尚不存在可供 Ruleset 选择的
`CI / required` 状态。首次启用按以下顺序完成：

1. 审核并合入工作流；
2. 创建一个验证 PR，确认 `CI / required` 首次成功；
3. 在仓库 Settings → Rules → Rulesets 中为 `main` 创建规则；
4. 要求通过 PR、至少一人批准，并把 `CI / required` 设为必需状态；
5. 禁止直接 Push、禁止绕过失败门禁，并要求分支无冲突后才能合并；
6. 分别验证新提交取消旧运行、`main` 更新重检、冲突跳过和条件任务。

Ruleset 只绑定稳定状态 `CI / required`，不绑定可能调整名称的内部 Job。配置规则需要
仓库管理员权限，且必须在默认分支存在工作流并产生首个成功状态后完成。

## 提交与 PR

CI 实现提交采用 Conventional Commit 结构，标题与正文均使用中文说明：

```text
<type>(<scope>): <中文摘要>

<说明做了什么、原因以及必要的验证或兼容性影响>
```

PR 正文应关联《UniSpeaking 最小可用 CI 方案》，简述“做了什么、怎么做、验证结果和
范围说明”，总长度不超过 1500 字符；不粘贴完整日志或重复 RFC 全文。
