# UniSpeaking

[![后端测试](https://github.com/1024XEngineer/UniSpeaking/actions/workflows/coverage.yml/badge.svg?branch=main)](https://github.com/1024XEngineer/UniSpeaking/actions/workflows/coverage.yml)
[![后端覆盖率](https://codecov.io/gh/1024XEngineer/UniSpeaking/branch/main/graph/badge.svg?flag=backend)](https://codecov.io/gh/1024XEngineer/UniSpeaking)

UniSpeaking 是一个面向英语口语训练的 AI 实时陪练系统。仓库包含 Spring Boot 后端、
React Web 客户端、React Native 移动端、PostgreSQL 数据模型以及 Docker/Nginx 部署配置。

## 已实现能力

- 账号与用户：邮箱注册、登录、JWT 鉴权、用户资料、偏好和学习目标。
- 自由对话：场景准备、Realtime 凭证、WebRTC SDP 交换、实时音频与字幕。
- 自定义场景：按用户输入生成单词、词组、句子和对话 Prompt，支持“学 → 读 → 说”、
  流程状态、逐轮评分、整场报告和复练。
- IELTS 口语：Part 1、Part 2、Part 3 专项训练与完整模考，支持题库检索、状态机、
  分阶段会话、四项能力评分、模考总评、学习资产、趋势统计和本地录音回放。
- 学习资产：训练记录、详细报告、推荐表达、能力趋势和训练音频。
- 个人中心：账户资料、偏好、学习洞察、目标进度和成就。

## 核心架构

场景运行时只定义五个稳定契约：

```text
SceneService<REQUEST, RESPONSE>       生成并准备场景
SceneFlowService<STAGE>               推进多阶段场景
SessionService                        管理会话生命周期和消息
EvaluationService<REPORT, DETAIL>     逐轮评分与报告
AiProvider                            提供厂商无关的 AI 能力
```

请求的主要依赖方向为：

```text
Controller / WebSocket
        │
        ▼
五个稳定契约及场景实现
        │
        ├── Component / State Machine
        ├── Domain DTO / PO / VO
        ├── Provider
        └── Repository
                 │
                 ▼
Infrastructure（AI、Realtime、数据库、存储和配置）
```

场景实现关系：

| 能力 | FreeChat | Custom | IELTS |
|---|---:|---:|---:|
| `SceneService` | ✓ | ✓ | ✓ |
| `SceneFlowService` | — | ✓ | ✓ |
| `SessionService` | ✓ | ✓ | ✓ |
| `EvaluationService` | — | ✓ | ✓ |
| `AiProvider` | 共享 | 共享 | 共享 |

`SessionService` 是稳定公共契约，但由各场景分别实现；项目中不设置通用
`SessionServiceImpl`。完整职责边界和新场景落位规范见 [CLAUDE.md](CLAUDE.md)。

## 仓库结构

```text
.
├── backend/unispeaking-server    Spring Boot 后端
├── frontend/web                  React + Vite Web 客户端
├── frontend/mobile               React Native + Expo 移动端
├── deploy                        Compose、Nginx 和环境变量示例
├── docs                          架构、API、CI 与部署文档
├── README.md                     项目说明与启动指南
└── CLAUDE.md                     后端架构和扩展规范
```

后端核心代码位于
`backend/unispeaking-server/src/main/java/com/unispeaking`：

```text
├── controller                    HTTP 协议入口
├── websocket                     WebSocket 协议入口
├── service
│   ├── scene                     SceneService、SceneFlowService
│   │   └── impl                  各场景的生成与流程实现
│   ├── session                   SessionService
│   │   └── impl                  各场景的会话实现
│   └── evaluation                EvaluationService
│       └── impl                  支持评分的场景实现
├── component                     状态机、协调器、录音等进程内组件
├── domain                        DTO、PO、VO
├── provider                      厂商无关能力接口与 Registry
├── infrastructure               AI、Realtime、持久化、存储和配置实现
└── common                        异常、响应、Prompt 和纯工具逻辑
```

## 技术栈

### 后端

- Java 21
- Spring Boot 4.0.7
- Spring Web MVC / WebSocket / Security
- JWT Resource Server
- PostgreSQL + Flyway
- MyBatis-Plus 3.5.17
- JUnit 5 / Mockito / Testcontainers

### 客户端

- Web：React 19、Vite 6、WebRTC、WebSocket
- Mobile：React Native 0.86、Expo SDK 57、TypeScript、Expo Router

### AI 与语音

- Qwen Realtime / LLM / ASR / TTS
- DeepSeek LLM
- Doubao ASR
- MiniMax / CosyVoice TTS
- 科大讯飞发音评分

业务代码只依赖 Provider 接口，模型与供应商选择由 Registry 和配置完成。

## 本地启动

### 1. 环境要求

- JDK 21
- Node.js 20+
- PostgreSQL
- npm

Docker 仅在容器部署或 Testcontainers 集成测试时需要。Redis 不是当前应用运行的必要依赖。

### 2. 创建环境配置

```bash
cp deploy/env/.env.example deploy/env/.env
```

本地直接启动后端时，至少配置：

```properties
DATABASE_URL=jdbc:postgresql://localhost:5432/unispeaking
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=your-local-password

JWT_SECRET=replace-with-at-least-32-random-bytes-in-base64
JWT_ISSUER=unispeaking
JWT_ACCESS_TOKEN_TTL=2h
```

可通过以下命令生成 JWT Secret：

```bash
openssl rand -base64 32
```

使用场景生成、Realtime、TTS、ASR 或评分能力时，还需在 `.env` 中配置对应厂商凭证。
完整变量及安全默认值见 [`deploy/env/.env.example`](deploy/env/.env.example)。

注意：

- 不要提交真实 `.env`。
- 不要把密钥放入任何 `VITE_` 变量；此类变量会进入浏览器构建产物。
- 后端默认读取 `../../deploy/env/.env`，也可使用 `UNISPEAKING_ENV_FILE` 指定绝对路径。

### 3. 初始化数据库

创建 PostgreSQL 数据库：

```bash
createdb -U postgres unispeaking
```

启动后端时，Flyway 自动执行：

[`V1__baseline.sql`](backend/unispeaking-server/src/main/resources/db/migration/V1__baseline.sql)

V1 是空库完整基线，包含业务表、索引、约束和 IELTS 题库种子数据；后续结构调整通过
V2 及更高版本迁移增量执行。已存在旧版 Flyway 历史的开发数据库不能直接套用该基线，
应先备份数据再重建。任何版本一旦进入共享环境，后续只能新增更高版本迁移，不能继续
改写已执行文件。

主要表按领域分为：

- 用户：`user`、`user_preference`、`user_ielts`。
- 场景：`scene`、`word`、`phrase`、`sentence`、`ielts`、`ielts_topic`、
  `ielts_question`。
- 会话：`practice_session`、`session_message`。
- 评分：`sentence_evaluation`、`turn_evaluation`、`session_evaluation`、
  `ielts_part_evaluation`、`ielts_evaluation`。
- 成就与反馈：`user_achievement_state`、`user_achievement_unlock`、`user_feedback`。

### 4. 启动后端

```bash
cd backend/unispeaking-server
./mvnw spring-boot:run
```

默认地址：`http://localhost:8080`。

### 5. 启动 Web 客户端

```bash
cd frontend/web
npm install
VITE_BACKEND_URL=http://localhost:8080 npm run dev
```

默认地址：`http://localhost:5173`。浏览器录音需要在 `localhost` 或 HTTPS 安全上下文中
运行，并授权麦克风。

### 6. 启动移动端

```bash
cd frontend/mobile
npm install
npm run web
```

原生开发客户端：

```bash
npm run ios
npm run android
```

移动端当前仍处于持续联调阶段，页面完成度和 Web 端不完全一致。开发前请阅读
[`frontend/mobile/HANDOFF.md`](frontend/mobile/HANDOFF.md)。

## 测试与质量检查

后端单元测试：

```bash
cd backend/unispeaking-server
./mvnw test
```

PostgreSQL/Redis Testcontainers 集成测试（需要可用的 Docker）：

```bash
./mvnw -Pci-integration -DskipUnitTests verify
```

生成合并覆盖率：

```bash
./mvnw -Pci-integration,coverage-aggregate clean verify
```

Web 构建和静态约束检查：

```bash
cd frontend/web
npm run build
npm run check:routes
npm run check:realtime-events
```

移动端检查：

```bash
cd frontend/mobile
npm run lint
npm run test:ci
```

## 文档

- [后端架构与扩展规范](CLAUDE.md)
- [完整 API 文档](docs/API接口文档.md)
- [五模块精简架构](<docs/UniSpeaking架构设计_精简版 (1).md>)
- [前后端接口契约](docs/frontend-backend-interface-contract.md)
- [CI 说明](docs/ci.md)
- [本地与通用部署](docs/deployment.md)
- [生产部署](docs/deployment-production.md)

## 开发原则

- 场景特有需求通过场景实现类和组件扩展，不修改五个稳定接口。
- 场景准备、鉴权、次数限制、Prompt 和内容落库归 `SceneService` 实现负责。
- `SessionService` 只管理已准备场景的会话，不重复生成场景，也不承担评分。
- 状态机、录音、生成器和协调器属于 `component`，不能包装成伪 Service。
- Controller 只做协议适配；同一场景的附属端点归并到同一个场景 Controller。
- PostgreSQL 是业务真相来源，持久化只能通过 Repository 访问。
- 接口或数据结构变化时，同步更新后端测试、前端调用和 API 文档。
