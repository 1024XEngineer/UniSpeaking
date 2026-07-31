# UniSpeaking

UniSpeaking 是一个面向英语口语学习的 AI 实时陪练系统。当前仓库包含 React 前端、
Spring Boot 后端、PostgreSQL 数据模型以及 Nginx/Docker 部署配置。

当前已经打通的核心链路：

- 邮箱账号注册、登录、JWT 鉴权和用户偏好保存。
- 自由对话：WebRTC SDP 交换、Qwen Realtime 音频连接、字幕和翻译。
- 自定义场景生成：根据用户输入和偏好生成场景、单词、词组、句子及完整 Prompt。
- “学 → 读 → 说”流程：TTS 示范、句子朗读评分和场景对话。
- 自定义场景状态机、逐轮评分、五维会话报告和学习资产查询。
- 复练场景：复用已有学习内容，保存最新明细并保留历次会话总评。

IELTS、英文面试、个人主页统计和会员页面目前主要完成了前端界面，尚未形成完整后端
业务链路。

## 技术栈

### 后端

- Java 21
- Spring Boot 4.0.7
- Spring Web MVC / WebSocket / Security
- JWT
- MyBatis-Plus 3.5.17
- PostgreSQL
- JUnit 5 / Mockito

### 前端

- React 19
- Vite 6
- JavaScript + JSX
- WebRTC / WebSocket

### AI Provider

- Qwen Realtime：实时语音对话和 WebRTC SDP 交换
- Qwen LLM：场景、提示词和文本内容生成
- DeepSeek：LLM 后备路由
- Qwen ASR / Doubao ASR：语音识别路由
- Qwen3-TTS / CosyVoice / MiniMax：语音合成路由
- 科大讯飞：英语发音评分

## 仓库结构

```text
.
├── backend
│   └── unispeaking-server       Spring Boot 后端
├── frontend
│   └── Unispeaking_fronted      React + Vite 前端
├── deploy
│   ├── docker-compose.yml
│   ├── env
│   └── nginx
├── docs                        架构、接口和部署文档
└── CLAUDE.md                   后端架构与开发规范
```

后端主要分层：

```text
Controller / WebSocket
        |
        v
Service（业务模块与业务编排）
        |
        +--> Domain
        +--> Provider
        +--> Repository
                    ^
                    |
Infrastructure（AI、Realtime、数据库和配置实现）
```

详细目录职责、命名规则和禁止事项见
[后端架构与开发规范](CLAUDE.md)。

## 本地启动

### 1. 环境要求

- JDK 21
- Node.js 20 或更高版本
- PostgreSQL
- npm

Redis 和消息队列当前没有启用，不是本地启动的必要条件。

### 2. 创建运行配置

```bash
cp deploy/env/.env.example deploy/env/.env
```

至少配置数据库和 JWT：

```properties
DATABASE_URL=jdbc:postgresql://localhost:5432/unispeaking
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=your-postgres-password

JWT_SECRET=replace-with-at-least-32-random-bytes-in-base64
JWT_ISSUER=unispeaking
JWT_ACCESS_TOKEN_TTL=2h
```

可以用下面的命令生成 JWT Secret：

```bash
openssl rand -base64 32
```

需要运行实时对话、场景生成和 TTS 时配置：

```properties
DASHSCOPE_API_KEY=your-dashscope-api-key
BAILIAN_WORKSPACE_ID=your-bailian-workspace-id
BAILIAN_REGION=cn-beijing
BAILIAN_MODEL=qwen3.5-omni-flash-realtime
QWEN_TTS_VOICE=Aiden
```

Qwen Realtime 的 signaling URL 由后端根据 Workspace、Region 和 Model 自动生成，
不需要手工配置。

需要运行句子朗读和对话发音评分时配置：

```properties
XFYUN_APP_ID=your-xfyun-app-id
XFYUN_API_KEY=your-xfyun-api-key
XFYUN_API_SECRET=your-xfyun-api-secret
```

完整 Provider 路由、超时和大小限制见
[`deploy/env/.env.example`](deploy/env/.env.example)。不要提交真实 `.env`，也不要把
任何密钥放进 `VITE_` 变量。

### 3. 创建数据库

先创建名为 `unispeaking` 的 PostgreSQL 数据库：

```bash
createdb -U postgres unispeaking
```

也可以使用 `psql`：

```sql
CREATE DATABASE unispeaking;
```

后端启动时由 Flyway 执行
[`V1__baseline.sql`](backend/unispeaking-server/src/main/resources/db/migration/V1__baseline.sql)
创建或补齐当前表结构。新库直接执行 V1；已有数据库会先以版本 0 纳入管理，再执行
幂等迁移并保留现有数据。运行账号必须具有建表、建索引和管理 Flyway 历史表的权限。

当前主要数据表：

- `user`
- `user_preference`
- `scene`
- `word`
- `phrase`
- `sentence`
- `sentence_evaluation`
- `session_message`
- `turn_evaluation`
- `session_evaluation`

### 4. 启动后端

```bash
cd backend/unispeaking-server
./mvnw spring-boot:run
```

默认地址：

```text
http://localhost:8080
```

从该目录启动时，Spring 默认读取：

```text
../../deploy/env/.env
```

也可以指定其他配置文件：

```bash
UNISPEAKING_ENV_FILE=/absolute/path/to/runtime.env ./mvnw spring-boot:run
```

### 5. 启动前端

打开另一个终端：

```bash
cd frontend/Unispeaking_fronted
npm install
VITE_BACKEND_URL=http://localhost:8080 npm run dev
```

默认访问：

```text
http://localhost:5173
```

本地开发必须让 `VITE_BACKEND_URL` 指向后端；如果留空，请求会发送给 Vite 自己，
REST 和 WebSocket 都无法正常联调。

浏览器麦克风只能在 `localhost` 或 HTTPS 安全上下文中使用，并需要用户授权。

## Docker Compose

当前 Compose 包含：

- `backend`
- `frontend`
- `nginx`

启动：

```bash
cd deploy
docker compose --env-file env/.env up --build
```

访问：

```text
http://localhost
```

Nginx 将 `/backend/` 同时代理给后端 REST 和 WebSocket，前端生产构建使用
`VITE_BACKEND_URL=/backend`。

Compose 当前不创建 PostgreSQL 容器。`DATABASE_URL` 必须指向后端容器可以访问的
数据库地址；在 macOS Docker Desktop 中访问宿主机数据库时可使用
`host.docker.internal`，不能使用容器自身的 `localhost`。

## 认证与统一响应

注册、登录以外的 HTTP 接口都需要：

```http
Authorization: Bearer <access-token>
```

成功响应统一为：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {}
}
```

WebSocket 地址：

```text
/ws/session-messages?access_token=<access-token>
```

WebSocket 会校验 JWT 和会话所有权，不能只凭 `sessionId` 追加或结束其他用户的
会话。

## 主要接口

| 模块 | 接口 |
| --- | --- |
| 注册 | `POST /api/auth/register` |
| 登录 | `POST /api/auth/login` |
| 当前用户 | `GET /api/auth/me` |
| 获取用户偏好 | `GET /api/user-preferences` |
| 保存用户偏好 | `PUT /api/user-preferences` |
| 开始自由对话 | `POST /api/scene-sessions` |
| 结束自由对话 | `POST /api/scene-sessions/{sessionId}/end` |
| 生成自定义场景 | `POST /api/custom-scenes/generate` |
| 创建/推进场景流程 | `POST /api/custom-scenes/flows`、`POST /api/custom-scenes/flows/advance` |
| 开始场景对话 | `POST /api/custom-scenes/{sceneId}/sessions` |
| 单轮评分 | `POST /api/custom-scenes/{sceneId}/sessions/{sessionId}/turns/{turnNo}/evaluation` |
| 推进对话状态机 | `POST /api/custom-scenes/{sceneId}/sessions/{sessionId}/turns/{turnNo}/state` |
| 完成场景对话 | `POST /api/custom-scenes/{sceneId}/sessions/{sessionId}/complete` |
| 查询五维评分 | `GET /api/custom-scenes/{sceneId}/sessions/{sessionId}/evaluation` |
| 句子朗读评分 | `POST /api/custom-scenes/{sceneId}/sentences/{sentenceId}/evaluation` |
| TTS | `POST /api/custom-scenes/{sceneId}/speech` |
| 学习资产列表 | `GET /api/custom-scenes/assets` |
| 场景学习资产 | `GET /api/custom-scenes/{sceneId}/assets` |

完整请求字段、响应字段和 WebSocket 消息格式见
[前后端接口文档](docs/frontend-backend-interface-contract.md)。

## 实时会话流程

```text
Browser -- Offer SDP + scene data --> Spring Boot
Spring Boot -- permanent API key --> DashScope temporary token
Spring Boot -- temporary token + Offer SDP --> Qwen Realtime
Spring Boot <-- Answer SDP ----------------- Qwen Realtime
Browser <-- Answer SDP --------------------- Spring Boot
Browser <========= WebRTC audio/DataChannel =========> Qwen Realtime
Browser <========= authenticated WebSocket ==========> Spring Boot
```

- 永久 API Key 只保存在后端。
- 临时 Token 默认有效期为 300 秒。
- 浏览器不接收永久 API Key。
- 自由对话不保存长期消息历史。
- 自定义场景按完整轮次保存消息和评分，不保存流式 delta。

## 测试与检查

后端：

```bash
cd backend/unispeaking-server
./mvnw --batch-mode --no-transfer-progress clean verify
./mvnw --batch-mode --no-transfer-progress \
  -Pci-integration -DskipUnitTests verify
```

当前测试基线：186 项单元测试、7 项 PostgreSQL/Redis 容器集成测试通过，合并后的
JaCoCo 全局行覆盖率为 73.80%。

前端：

```bash
cd frontend/Unispeaking_fronted
npm run build
npm run check:routes
npm run check:realtime-events
```

## 文档

- [后端架构与开发规范](CLAUDE.md)
- [完整业务架构](docs/UniSpeaking架构设计（完整版）.md)
- [前后端接口文档](docs/frontend-backend-interface-contract.md)
- [部署与配置](docs/deployment.md)
- [持续集成与分支保护](docs/ci.md)
- [用户、场景与会话标识](docs/用户会话标识与用量归属流程.md)

## 当前边界

- PostgreSQL 是持久化真相来源；运行时代码只允许使用 MyBatis-Plus。
- Redis 仅用于 CI 容器烟测，生产运行时和消息队列尚未启用。
- 自由聊天内容不进入长期存储。
- 自定义场景、学习内容、逐轮评分和会话报告写入 PostgreSQL。
- IELTS、面试、个人统计和会员功能尚需继续开发后端接口。
