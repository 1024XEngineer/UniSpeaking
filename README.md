# UniSpeaking

[![后端测试](https://github.com/1024XEngineer/UniSpeaking/actions/workflows/coverage.yml/badge.svg?branch=main)](https://github.com/1024XEngineer/UniSpeaking/actions/workflows/coverage.yml)
[![后端覆盖率](https://codecov.io/gh/1024XEngineer/UniSpeaking/branch/main/graph/badge.svg?flag=backend)](https://codecov.io/gh/1024XEngineer/UniSpeaking)
[![移动端覆盖率](https://codecov.io/gh/1024XEngineer/UniSpeaking/branch/main/graph/badge.svg?flag=mobile)](https://codecov.io/gh/1024XEngineer/UniSpeaking)

后端与移动端 Codecov 使用独立的 `backend`、`mobile` flag，分别统计后端 JaCoCo 合并测试和移动端 Jest 行覆盖率；README 徽章显示 main 分支最新报告，移动端目标为保持在 80% 以上。

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

场景运行时采用直接实现类，不再为每个 Service 同时维护“接口 + Impl”。只有存在稳定、
确定返回类型和可复用逻辑时才保留具体公共父类：

```text
Custom/Ielts/FreeChat/InterviewSceneService    直接生成并准备场景
SceneFlowService<STAGE>                        具体父类，提供阶段流转实现
Custom/Ielts/...SessionService                 直接管理各场景会话
EvaluationService<REPORT, DETAIL>              具体父类，提供公共评价实现
AiProvider                                     厂商无关的 AI 能力契约
```

`CustomSceneFlowService`、`IeltsSceneFlowService` 继承 `SceneFlowService`，并显式
`@Override` 公共流转方法；`CustomEvaluationService`、`IeltsEvaluationService` 以同样方式
继承 `EvaluationService`。父类不是接口或抽象类，可以直接复用其完整实现。

请求的主要调用方向为：

```text
Controller / WebSocket
        │
        ▼
具体 Service
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

| 能力 | FreeChat | Custom | IELTS | Interview |
|---|---:|---:|---:|
| 场景 Service | ✓ | ✓ | ✓ | ✓ |
| `SceneFlowService` 具体父类 | — | ✓ | ✓ | — |
| 会话 Service | ✓ | ✓ | ✓ | ✓ |
| `EvaluationService` 具体父类 | — | ✓ | ✓ | — |
| `AiProvider` | 共享 | 共享 | 共享 | 共享 |

各场景的会话输入和返回值不同，因此会话目录使用独立具体类，不设置无实际复用价值的
`SessionService` 父类或 `SessionServiceImpl`。完整职责边界和新场景落位规范见
[CLAUDE.md](CLAUDE.md)。

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
│   ├── auth                      认证用例和持久化端口
│   ├── scene                     场景具体类、SceneFlowService 具体父类
│   ├── session                   各场景会话具体类
│   └── evaluation                评价具体类、EvaluationService 具体父类
├── component                     状态机、协调器、录音等进程内组件
├── domain
│   └── dto/auth                  认证输入输出模型
├── provider                      厂商无关能力接口与 Registry
├── infrastructure
│   ├── ai/aliyun/captcha         阿里云 CAPTCHA SDK 调用和适配器
│   ├── security/captcha          开发及 Turnstile 人机验证适配器
│   ├── persistence/repository/auth  认证存储实现
│   └── config                    认证 Bean 与适配器装配
└── common
    ├── security                  人机验证稳定端口
    ├── email                     验证邮件稳定端口
    └── exception                 公共异常
```

原 `com.unispeaking.auth` 聚合包已拆除。认证链路遵循端口与适配器的依赖方向：

```text
Controller
    -> service/auth
        -> domain/dto/auth + common 端口
                               ^
                               |
                 Infrastructure 适配器
```

Service 不依赖阿里云 SDK、JDBC、内存存储或 SMTP 的具体实现；Infrastructure 负责实现
公共端口和 Service 持久化端口，并通过配置类完成装配。

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

- 七牛 RTI Realtime（默认）
- 七牛 MaaS LLM（Qwen 3.5 Plus 主模型，阿里百炼 Qwen 3.5 Plus 后备）
- Qwen Realtime（后备）/ ASR / TTS
- Qwen / DeepSeek 官方直连 LLM（显式回滚用）
- Doubao ASR
- MiniMax / CosyVoice TTS
- 科大讯飞发音评分

业务代码只依赖 Provider 接口，模型与供应商选择由 Registry 和配置完成。
Realtime 默认使用七牛 RTI 的 `qwen3.5-omni-plus-realtime`、`default_assistant`、
`Tina` 和 `platform_rtc`；七牛出现可回退错误时切换到百炼 Flash。七牛长期 API Key
只保存在后端，创建 Session 返回的短期媒体 token 仅用于服务端 SDP 协商。
LLM 默认通过七牛 MaaS 调用 `qwen/qwen3.5-plus`，可重试错误时回退到阿里百炼
`qwen3.5-plus`；七牛 MaaS DeepSeek 与 DeepSeek 官方直连 LLM 仅作为显式回滚能力。
`QINIU_MAAS_API_KEY` 只能配置在后端环境中。

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

### 2.1 本地启用 JD 图片 OCR

Web 的“上传图片”入口以服务端探测结果为准，不需要手动设置浏览器端的开关。后端本地运行时，先准备 Python 3.11、PaddleOCR 依赖和模型：

```bash
./scripts/prepare-local-ocr.sh
```

然后从 `backend/unispeaking-server` 启动后端，并把 OCR 路径指向仓库内的本地目录：

```bash
cd backend/unispeaking-server
OCR_ENABLED=true \
OCR_PYTHON_EXECUTABLE="$PWD/../../.local/ocr/venv/bin/python" \
OCR_RUNNER_PATH="$PWD/src/main/resources/ocr/paddle_ocr_runner.py" \
OCR_MODEL_DIRECTORY="$PWD/../../.local/ocr/models" \
MAVEN_REPO_URL=https://maven.aliyun.com/repository/public \
./mvnw --settings docker/maven/settings.xml spring-boot:run
```

启动后用浏览器访问 Web，在模拟面试页面选择“上传图片”。也可以用登录后的 JWT 进行接口实测：

```bash
OCR_ACCESS_TOKEN='登录后 localStorage 中的 unispeaking.accessToken' \
./scripts/check-local-ocr.sh /absolute/path/to/jd.png
```

脚本先验证 `/api/interview-scenes/ocr/availability`，再提交图片到 `/prepare-materials`；这样可以区分“服务端未装好 OCR”和“图片上传/材料整理链路失败”。

注意：

- 不要提交真实 `.env`。
- 不要把密钥放入任何 `VITE_` 变量；此类变量会进入浏览器构建产物。
- 后端默认读取 `../../deploy/env/.env`，也可使用 `UNISPEAKING_ENV_FILE` 指定绝对路径。

### 3. 初始化数据库

创建 PostgreSQL 数据库：

```bash
createdb -U postgres unispeaking
```

启动后端时，Flyway 自动执行合并后的单一迁移：

[`V1__baseline.sql`](backend/unispeaking-server/src/main/resources/db/migration/V1__baseline.sql)

V1 包含完整业务结构、IELTS 题库种子数据、动态 AI Provider、路由、价格和调用账单。
它用于初始化空数据库；已经记录过旧迁移历史的数据库需要先备份，再将结构和 Flyway
历史归一化到当前 V1。

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
DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/unispeaking \
DATABASE_USERNAME=postgres \
DATABASE_PASSWORD='your-local-password' \
AUTH_COOKIE_SECURE=false \
UNISPEAKING_ADMIN_SECURE_COOKIE=false \
WEB_ALLOWED_ORIGIN_PATTERNS='http://localhost:*,http://127.0.0.1:*,http://100.100.57.60:*' \
AUTH_CAPTCHA_PROVIDER=development \
AUTH_CAPTCHA_DEVELOPMENT_TOKEN=local-human-verified \
./mvnw spring-boot:run
```

默认地址：`http://localhost:8080`。

这组参数仅用于本机联调：允许本机和局域网 Web/Expo 来源，并使用本地人机验证令牌，
不会连接生产数据库或阿里云验证码。不要修改 `deploy/env/.env` 中的生产配置。

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

真机与电脑必须连接同一局域网。先查看电脑局域网 IP（例如 `100.100.57.60`），再启动 Expo：

```bash
EXPO_PUBLIC_BACKEND_URL=http://100.100.57.60:8080 \
npx expo start --dev-client --host lan --clear --port 8081
```

如果只在 Android 模拟器中运行，可将地址改为 `http://10.0.2.2:8080`；iOS 模拟器使用
`http://127.0.0.1:8080`。

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

- `scene`、`session`、`evaluation` 下的 Service 使用直接实现类，不新增配套 `Impl`。
- 有公共具体逻辑时继承具体父类，子类对公开父类方法显式使用 `@Override`。
- 场景准备、鉴权、次数限制、Prompt 和内容落库归对应场景 Service 负责。
- 会话 Service 只管理已准备场景的会话，不重复生成场景，也不承担评分。
- 状态机、录音、生成器和协调器属于 `component`，不能包装成伪 Service。
- Controller 只做协议适配；同一场景的附属端点归并到同一个场景 Controller。
- 业务 Service 依赖稳定端口，外部 SDK、数据库和远程调用只能由 Infrastructure 适配。
- PostgreSQL 是业务真相来源，持久化只能通过 Repository 访问。
- 接口或数据结构变化时，同步更新后端测试、前端调用和 API 文档。
