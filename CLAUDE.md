# UniSpeaking 后端架构与开发规范

本文件是 UniSpeaking 仓库的后端开发约束，适用于
`backend/unispeaking-server`。业务设计参考 `docs` 中的架构文档；当实现方式
存在歧义时，以本文件的分层、依赖和持久化规则为准。

## 1. 技术基线

- 开发语言：Java 21。
- 应用框架：Spring Boot 4。
- Web：Spring Web MVC、Spring WebSocket。
- 鉴权：Spring Security、JWT Resource Server。
- 数据库：PostgreSQL。
- ORM：MyBatis-Plus 3.5.x。
- 测试：JUnit 5、Mockito，集成测试可使用 H2。
- 构建工具：Maven Wrapper，统一使用 `./mvnw`。
- Redis、消息队列、OSS/CDN 当前不是默认启用组件；引入前必须先确认业务需求、
  数据归属、失败策略和运维配置。

禁止在业务代码中引入第二套 ORM、数据库访问框架或重复的 HTTP/JSON 技术栈。

## 2. 总体分层

依赖方向必须保持为：

```text
Controller / WebSocket
        |
        v
Service（业务模块与业务编排）
        |
        +--> Domain
        +--> Provider（能力接口）
        +--> Repository（持久化门面）
                    ^
                    |
Infrastructure（供应商、数据库、网络和框架实现）
```

上层可以依赖下层抽象，下层不得反向依赖 Controller。不同 Service 模块之间应避免
双向依赖；出现循环依赖时，优先重新划分业务职责，而不是使用延迟注入绕过问题。

## 3. 当前目录职责

后端源码根目录为：

```text
src/main/java/com/unispeaking
├── common
├── component
├── controller
├── domain
│   ├── dto
│   ├── po
│   └── vo
├── infrastructure
│   ├── ai
│   ├── config
│   ├── evaluation
│   ├── persistence
│   ├── realtime
│   └── serialization
├── provider
├── service
│   ├── asset
│   ├── auth
│   ├── evaluation
│   ├── profile
│   ├── scene
│   └── session
└── websocket
```

### 3.1 `controller`

职责：

- 定义 HTTP 路由、请求方法、状态码和参数校验。
- 从 `AuthService` 获取当前登录用户。
- 将请求转换为 Service 入参，并包装统一响应。

允许：

- `@Valid`、`@RequestBody`、`@PathVariable`、`MultipartFile` 等协议适配。
- 调用一个主要业务 Service；必要时调用认证服务获取用户身份。

禁止：

- 编写业务规则、状态机、评分、提示词拼装或事务逻辑。
- 直接调用 Mapper、Repository、厂商 SDK 或 HTTP Client。
- 信任客户端传入的 `userId`。
- 为同一个业务流程创建重复、占位 Controller。

### 3.2 `service`

`service` 表示业务模块和业务用例编排，不是所有类的默认归宿。

当前模块：

- `auth`：注册、登录、JWT 和当前用户身份。
- `profile`：用户资料与偏好。
- `scene`：场景生成、场景流程和学习阶段推进。
- `session`：会话建立、消息追加、结束和会话授权。
- `evaluation`：句子、单轮和整场对话评分编排。
- `asset`：学习资产查询、复练资产与历史结果。

每个稳定业务能力采用：

```text
service/{module}/{Name}Service.java
service/{module}/impl/{Name}ServiceImpl.java
```

Service 可以：

- 编排领域对象、Repository 和 Provider。
- 定义事务边界、权限检查、幂等规则和业务异常。
- 串联多个步骤完成一个完整业务用例。

Service 不可以：

- 只包装一行工具方法或一次 Mapper 调用。
- 充当静态工具、JSON Parser、Prompt Builder、ID Generator 或 HTTP Client。
- 使用供应商名称承担业务职责，例如 `QwenSceneService`。
- 因“以后可能用到”而新增空接口或空实现。
- 直接写 SQL 或绕过 Repository 调用 Mapper。

新增 Service 前必须回答：

1. 它是否代表用户可感知、可独立描述的业务能力？
2. 它是否需要编排多个步骤、权限、状态或事务？
3. 它是否有稳定的输入输出契约？
4. 现有 Service 是否已经拥有该职责？

任一答案不成立时，优先使用模块内部组件、工具类、Provider 或 Repository。

### 3.3 `domain`

- `domain/dto`：跨 Controller、Service 或 WebSocket 边界传递的数据。
- `domain/po`：业务持久化对象或聚合数据，不等同于数据库 Entity。
- `domain/vo`：不可变值对象、结果对象和枚举。

Domain 可以包含业务数据约束和无副作用的领域行为，但不得依赖 Spring Controller、
MyBatis、数据库 Entity、厂商 SDK 或网络 Client。

DTO 按模块分包。已经不存在的 Request、Response、Command、Result 必须删除，不保留
占位类。

### 3.4 `provider`

Provider 是外部 AI 能力的业务抽象，例如：

- 实时模型 SDP 交换。
- LLM 文本生成。
- TTS。
- 语音或对话评分。

`provider` 中放接口、能力枚举和 Registry。供应商实现放在
`infrastructure/ai/{vendor}`，例如 Qwen、DeepSeek、MiniMax、讯飞。

规则：

- 业务代码依赖 Provider 接口，不依赖具体厂商类。
- Registry 根据能力和模型标识选择 Provider。
- 不修改稳定 Provider 方法来迁就单个供应商；供应商差异在实现内部适配。
- API Key、Workspace ID 和厂商 URL 只能由配置注入，不能出现在 DTO 或日志中。

### 3.5 `infrastructure`

- `ai`：外部 AI 厂商 Adapter、协议、HTTP 请求和响应解析。
- `evaluation`：评分供应商 Client 与协议实现。
- `persistence`：数据库 Entity、Mapper、Repository、Codec、TypeHandler。
- `realtime`：临时凭证、SDP 交换等实时连接内部操作。
- `config`：Spring Security、CORS、WebClient、Properties 和 Bean 配置。
- `serialization`：通用序列化配置。

基础设施代码实现技术细节，不拥有业务流程。`realtime` 不是 Service；Prompt 生成器
也不是 Service。

### 3.6 `common`

放置无状态、可复用且不依赖业务流程的代码：

- `exception`：统一异常及处理器。
- `response`：统一 API 响应。
- `util`：明确用途的通用工具。
- `prompt`：Prompt 模板加载与 Builder。
- `evaluation`：纯评分计算、Parser、Validator、Policy 和值模型。
- `logging`：脱敏后的流程日志支持。

禁止创建含义模糊的 `CommonService`、`Helper` 或万能 `Utils`。能够归属某个模块的
工具应放在该模块附近；只有真正跨模块复用的纯逻辑才进入 `common`。

### 3.7 `component`

用于应用进程内的运行时组件，例如会话上下文、状态持有器、超时协调器。它不是
数据库真相来源，应用重启后可丢失的数据才允许放在这里。

### 3.8 `websocket`

职责：

- WebSocket 握手、帧解析和 ACK。
- 校验 JWT 身份及会话归属。
- 将完整消息或结束事件交给 `SessionService`。

禁止未经认证连接，禁止仅凭客户端提供的 `sessionId` 修改会话。前端与 Realtime
模型 DataChannel 直接完成的暂停、恢复和打断，不应伪造成后端业务接口。

## 4. 新代码归类决策

新增类前按以下顺序判断：

1. HTTP/WebSocket 协议入口：Controller 或 WebSocket Handler。
2. 用户可感知的业务用例和流程：已有 Service；只有独立业务模块才新增 Service。
3. 外部能力的稳定契约：Provider；厂商实现放 Infrastructure。
4. 数据库读写：Repository；MyBatis-Plus Mapper 仅由 Repository 使用。
5. 纯转换或算法：`Builder`、`Parser`、`Validator`、`Calculator`、`Generator`。
6. 进程内临时状态：Component。
7. 配置：`@ConfigurationProperties` 或 Configuration。
8. 仅用于传输数据：DTO、VO 或 PO。

只有存在多实现、外部边界或需要稳定替换点时才创建接口。不要为每一个工具类机械地
创建接口和 `Impl`。

## 5. 命名规范

- Controller：`{Business}Controller`。
- Service：`{Business}Service`、`{Business}ServiceImpl`。
- Provider：`{Capability}Provider` 或 `{Vendor}{Capability}Provider`。
- Registry：`{Capability}ProviderRegistry`。
- 数据库实体：`{TableMeaning}Entity`。
- Mapper：`{EntityMeaning}Mapper`。
- Repository：`{Aggregate}Repository`。
- 配置：`{Feature}Properties`、`{Feature}Config`。
- 传输对象：`Request`、`Response`、`Command`、`Result`。
- 纯逻辑：`Builder`、`Parser`、`Validator`、`Calculator`、`Generator`、`Factory`。

Java 包名一律小写，按业务模块命名。避免 `Manager`、`Handler`、`Processor`、
`Helper` 等无法表达边界的名称；协议处理器除外。

ID 前缀必须表达业务类型：

- 自由会话场景：`freechat_...`
- 自定义场景：`custom_...`
- 面试场景：`interview_...`
- 雅思场景：`ielts_...`
- 会话 ID 按对应业务约定生成，不使用固定 `scene_` 前缀冒充所有类型。

## 6. 持久化规范

### 6.1 唯一位置

所有运行时数据库代码只能位于：

```text
infrastructure/persistence
├── entity/{module}
├── mapper/{module}
├── repository/{module}
├── codec/{module}
└── typehandler
```

项目中不得再出现第二套 `mapper`、`repository`、`persistence/evaluation` 或模块内
私有数据库目录。Controller 和 Service 不得直接引用 Entity 或 Mapper。

### 6.2 MyBatis-Plus 强制规则

- Mapper 必须继承 `BaseMapper<Entity>`。
- 条件查询使用 `LambdaQueryWrapper`、`LambdaUpdateWrapper` 等 MyBatis-Plus API。
- Repository 封装 Mapper，并向 Service 返回 Domain 对象。
- JSONB 使用统一 Codec/TypeHandler，不在业务代码中手工拼 JSON。
- 多表原子写入在 Service 事务边界或专用聚合 Repository 中完成。

运行时代码严格禁止：

- `@Select`、`@Insert`、`@Update`、`@Delete` SQL 注解。
- Mapper XML 中的 SQL。
- `JdbcTemplate`、原生 JDBC、JPA/EntityManager。
- 字符串拼接 SQL。
- Wrapper 的 `.last()`、`.apply()`、`.inSql()`、`.notInSql()`、`.setSql()`。
- Service 调用 Mapper 或 `selectById/updateById` 处理复合主键。

唯一允许的 SQL 是建表、索引和迁移 DDL，位置为：

- `src/main/resources/db/schema.sql`
- `deploy/postgres/*.sql`

### 6.3 主键与关联

- 数据库真实复合主键必须由全部字段共同定位。
- MyBatis-Plus 不原生支持复合 `@TableId`；对应 Entity 不伪造单字段主键。
- 复合主键查询、更新和删除必须通过 Lambda Wrapper 显式包含全部主键字段。
- 不使用外键时，仍须保证关联字段类型完全一致并建立必要索引。
- Service/Repository 负责应用级归属校验和删除顺序。
- 不允许为绕过 ORM 警告增加不存在的合成 ID。

## 7. Redis 规范

当前后端没有启用 Redis 依赖。只有缓存、幂等、限流、短期会话状态或分布式锁等明确
需求才能引入，Redis 不得替代 PostgreSQL 作为业务真相来源。自由聊天当前不做消息
持久化，不得擅自恢复旧的 Redis 对话存储。

引入后统一放置：

```text
infrastructure/cache/redis/{module}
infrastructure/config/RedisProperties.java
```

规则：

- Key：`unispeaking:{module}:{businessId}:{purpose}`。
- 每个 Key 必须定义 TTL、序列化格式和版本。
- 不存 API Key、JWT、明文密码和无界音频。
- 缓存失效不能导致核心数据丢失。
- 地址、端口、密码均由环境变量注入，仓库只保留空值或安全默认值。

示例环境变量：

```text
REDIS_HOST=
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_DATABASE=0
```

## 8. 消息队列规范

当前项目没有启用消息队列。异步清理、评分后处理等需求引入 MQ 前，必须确定 RabbitMQ
或 Kafka、消息语义、重试、死信和幂等方案。

建议位置：

```text
infrastructure/messaging/{rabbitmq|kafka}/{module}
```

要求：

- Producer/Consumer 与业务 Service 分离。
- 事件 DTO 有明确版本，包含事件 ID、业务 ID、发生时间和类型。
- Consumer 必须幂等，并定义最大重试和死信处理。
- 不假设数据库事务与 MQ 发布天然原子；需要强一致时采用 Outbox。
- 消息不得携带凭证、完整 SDP 或大段音频二进制。

## 9. OSS 与 CDN 规范

对象存储和 CDN 属于基础设施：

```text
infrastructure/storage
├── ObjectStorageProvider.java
└── qiniu
infrastructure/cdn
infrastructure/config/ObjectStorageProperties.java
infrastructure/config/CdnProperties.java
```

- 业务只保存对象 Key 和必要元数据，不保存长期签名 URL。
- 下载 URL 按需生成并设置短 TTL。
- 上传必须校验文件类型、大小、用户归属和对象路径。
- CDN 刷新、预热和签名属于 `infrastructure/cdn`，不进入 Controller。
- 只有“媒体资产管理”成为独立业务用例时才新增 Service；SDK 封装本身不是 Service。
- Access Key、Secret、Bucket、域名全部通过环境变量配置。

## 10. HTTP 接口统一规范

### 10.1 路由与方法

- API 前缀统一为 `/api`。
- 路由使用复数资源名和 kebab-case。
- `GET` 查询，`POST` 创建或执行动作，`PUT` 整体更新，`PATCH` 局部更新，
  `DELETE` 删除。
- 路由中体现资源归属，例如
  `/api/custom-scenes/{sceneId}/sessions/{sessionId}`。
- 不使用 `/doSomething`、`/handleXxx` 作为对外业务路由。

### 10.2 统一响应

所有 HTTP JSON 响应使用：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {}
}
```

- 字段使用 camelCase。
- 时间使用带时区的 ISO-8601。
- 成功码稳定为 `OK`；错误码使用大写下划线且具有业务语义。
- `400` 参数错误，`401` 未认证，`403` 无权限，`404` 资源不存在，
  `409` 状态冲突，`422` 可识别但无法完成的业务请求，`500/502/503` 服务异常。
- 不向客户端返回堆栈、SQL、密钥或厂商原始错误体。

### 10.3 身份与授权

- 注册、登录以外的接口默认必须携带 JWT。
- `userId` 从认证上下文获取，不能由请求体决定。
- 所有 `sceneId`、`sessionId`、资产 ID 操作都要校验当前用户归属。
- WebSocket 握手必须认证，消息处理前再次验证会话归属。

### 10.4 音频与实时接口

- 小音频评分使用 `multipart/form-data`，明确采样率、声道和支持格式。
- 大音频进入 OSS，不通过 JSON Base64 长期传输。
- 开始 Realtime 会话通过 HTTP 交换 Offer/Answer SDP。
- 对话完整文本通过认证 WebSocket 逐轮追加；流式 delta 不落库。
- 暂停、恢复和打断由前端 DataChannel 与 Realtime 模型交互，不新增后端占位接口。

## 11. 后续业务模块接口规范

### 11.1 个人主页

模块位置：

```text
service/profile
domain/dto/profile
controller/ProfileController.java
```

建议路由：

- `GET /api/profile/overview`
- `GET /api/profile/history`
- `GET /api/profile/statistics`
- `GET /api/user-preferences`
- `PUT /api/user-preferences`

个人主页聚合展示可以查询多个 Repository，但写操作必须回到对应业务模块，不能由一个
“万能 ProfileService”修改所有表。

### 11.2 雅思

模块位置：

```text
service/ielts
domain/dto/ielts
controller/IeltsController.java
```

建议路由：

- `POST /api/ielts/scenes`
- `POST /api/ielts/scenes/{sceneId}/sessions`
- `GET /api/ielts/sessions/{sessionId}/evaluation`
- `GET /api/ielts/history`

雅思评分由 `EvaluationService`/Provider 提供能力，IELTS Service 负责任务、流程和
评分标准编排，不复制厂商评分实现。

### 11.3 面试

模块位置：

```text
service/interview
domain/dto/interview
controller/InterviewController.java
```

建议路由：

- `POST /api/interviews`
- `POST /api/interviews/{interviewId}/sessions`
- `GET /api/interview-sessions/{sessionId}`
- `GET /api/interview-sessions/{sessionId}/evaluation`

岗位、面试轮次和问题属于 Interview 模块；Realtime、TTS、LLM、评分继续复用
Provider，不创建 `InterviewTtsService` 或 `InterviewLlmService`。

## 12. 配置与密钥

`application.yaml` 只保存结构、安全默认值和环境变量占位符。开发密钥放在
`deploy/env/.env`，仓库中的 `.env.example` 只能保留占位值。

PostgreSQL：

```text
DATABASE_URL=jdbc:postgresql://localhost:5432/unispeaking
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=
```

JWT：

```text
JWT_ISSUER=unispeaking
JWT_SECRET=
JWT_ACCESS_TOKEN_TTL=2h
```

供应商配置使用各自前缀，例如 `DASHSCOPE_`、`BAILIAN_`、`IFLYTEK_`。

禁止：

- 提交 `.env`、真实密码、Token、Access Key。
- 在 Java 类中写生产 URL、Key 或 Workspace ID。
- 在日志中打印 Bearer Token、完整临时凭证、密码、完整 SDP、音频 Base64。

## 13. 日志与异常

- 业务异常统一使用 `BusinessException` 及稳定错误码。
- 外部供应商错误在 Infrastructure 转换为项目异常，不向上泄漏厂商响应细节。
- 日志只记录流程节点、业务 ID、状态、耗时和脱敏摘要。
- 不记录用户密码、JWT、API Key、临时 Token、完整用户音频。
- 会话开始、绑定、停止和评分完成可使用结构化日志；高频流式事件默认不记录。

## 14. 测试规范

测试目录镜像主代码包结构：

```text
src/test/java/com/unispeaking/{same-package}
```

- Service 测试业务分支、授权、状态迁移和事务边界。
- Repository 测试复合主键条件、实体映射和 JSONB 转换。
- Controller 测试参数、认证、状态码和统一响应。
- Provider 使用 Mock HTTP/固定夹具，不默认访问真实厂商。
- 真实供应商手工测试不放在默认 `src/test`，避免依赖密钥、网络和本机文件。
- 未引用夹具、旧包路径测试和已删除功能测试必须同步删除。
- Bug 修复必须添加能复现问题的回归测试。
- 每次修改生产代码时，必须同步新增或更新对应测试文件，覆盖新增行为、受影响分支和
  兼容性边界；不得以“改动较小”或“后续补测”为由只提交实现代码。
- 后端自动化测试的全局行覆盖率不得低于 80%。启动类、纯 DTO、简单属性绑定类和明确
  生成代码可以按 CI 约定排除，Service、Controller、Repository、Provider、鉴权、
  会话、评分和状态机等核心业务代码不得通过排除规则规避覆盖率要求。
- 当前 CI 的强制门槛暂时保持为 70%，用于降低建设初期的协作阻力。这是过渡性自动
  门禁，不代表开发质量标准降低；测试基线稳定达到 80% 后，再单独调整 CI 阈值。

提交前至少执行：

```bash
cd backend/unispeaking-server
./mvnw --batch-mode --no-transfer-progress clean verify
./mvnw --batch-mode --no-transfer-progress \
  -Pci-integration -DskipUnitTests verify
./mvnw --batch-mode --no-transfer-progress \
  -Pcoverage-aggregate \
  -DskipUnitTests \
  -DskipIntegrationTests \
  verify
```

修改前端接口契约时，还须执行前端构建和路由/实时协议检查。

## 15. 开发前审查清单

写代码前确认：

1. 需求属于哪个现有业务模块？
2. 是否可以扩展现有 Service，而不是新增 Service？
3. 新类究竟是业务编排、Provider、Repository、Component 还是纯工具？
4. 是否引入了重复 DTO、Repository、Mapper 或厂商 Adapter？
5. 当前用户身份是否来自 JWT，资源归属是否校验？
6. 是否需要 PostgreSQL 事务、缓存 TTL、消息幂等或外部调用超时？
7. API 路由、DTO 和统一响应是否与现有规范一致？
8. 是否有对应测试，旧代码和旧测试是否同步删除？

## 16. 完成标准

一项后端改动只有同时满足以下条件才算完成：

- 目录、命名和依赖方向符合本文件。
- Controller 无业务逻辑，Service 无原始 SQL，Repository 不泄漏 Entity。
- 持久化仅使用 MyBatis-Plus，复合主键使用完整条件。
- 认证、授权、输入校验、超时和错误转换完整。
- 配置无真实密钥，日志无敏感数据。
- 所有生产代码改动均同步补齐测试，默认测试不依赖外网或真实账号。
- 全局行覆盖率达到 80% 的开发质量标准；CI 在过渡期仍按 70% 自动门禁执行。
- 单元测试、容器集成测试和覆盖率检查全部通过。
