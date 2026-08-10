# UniSpeaking 后端架构与场景扩展规范

本文件是 `backend/unispeaking-server` 的强制开发约束，也是后续新增场景时的落位指南。
它描述当前代码的真实结构；若历史文档、示例代码与当前实现冲突，以本文件和编译通过的
稳定接口为准。

## 1. 技术基线

- Java 21，Spring Boot 4.0.7。
- Spring Web MVC、Spring WebSocket、Spring Security、JWT Resource Server。
- PostgreSQL、Flyway、MyBatis-Plus 3.5.17。
- Maven Wrapper：所有后端命令统一使用 `./mvnw`。
- JUnit 5、Mockito、Testcontainers。
- PostgreSQL 是业务真相来源；进程内状态、缓存、对象存储都不能替代数据库。

禁止在业务代码中引入第二套 ORM、第二套运行时 DDL 或重复的 HTTP/JSON 技术栈。

## 2. 场景运行时契约与职责

场景运行时保留的稳定面为：**两治理契约（`SceneFlowService` / `EvaluationService`）+ 一个 AI 能力族（`AiProvider` 根 + 5 能力接口 + `AiProviderRegistry`）**，外加两个**文档化职责**（场景准备、会话生命周期）。判据：**治理抽象仅当"声明一条逐字节相同、业务同义、有产品驱动预期第二实现、且不被泛型仪式主导的共享形状"时保留；无共享形状可被第二实现继承的抽象为空；行为共享永远在 Component。** `SceneService`/`SessionService` 基类已删除（零多态消费者、零共享签名、或仅 WS 传输约定）。

### 2.1 场景准备职责（Scene Preparation）

`SceneService` 基类已删除；每个场景的生成方法 `generate` 是场景专用接口的**自身主声明**（如 `CustomSceneService.generate`）。场景准备仍须满足以下职责（由各场景专用接口与 Impl 落实）：

- 校验登录用户、资源归属和业务权限。
- 校验每日次数、配额或前置条件。
- 选择或生成场景内容。
- 组装 Prompt、音色和场景上下文。
- 持久化场景及其内容。
- 返回后续流程所需的 `sceneId` 和场景结果。

禁止：

- 在 `generate` 内启动 Session。
- 把场景准备工作推给会话层。

归属校验是 Impl 私有或薄 `OwnershipPolicy` 组件（折叠错误 + 身份来源），不进入场景专用接口。

### 2.2 `SceneFlowService`

位置：`service/scene/SceneFlowService.java`

```java
public interface SceneFlowService<S> {
    S start(String sceneId);
    S current(String sceneId);
    S next(String sceneId);
    boolean isCompleted(String sceneId);
}
```

职责：管理有阶段场景的全部流程状态。FreeChat 无阶段，不实现此接口。

有阶段的场景通过专用接口继承它，例如 `CustomSceneFlowService extends
SceneFlowService<CustomStage>`；Impl 不直接实现公共接口。

它既负责场景级阶段（例如 IELTS 的 Part 1/2/3），也负责场景专属的会话内子流程
（例如题目推进、Part 2 准备/作答、自定义对话目标）。这些子流程方法只声明在对应的
场景专用 Flow 接口中，不得放入 Session 接口。Flow 不负责生成内容、创建会话、保存
消息或评分。真实流程状态必须可以从数据库恢复；进程内状态机只负责运行时判断和转换。

### 2.3 会话生命周期（由 Component 承载）

`SessionService` 基类已删除。会话生命周期由 `component/session/SessionLifecycleManager` 承载，`SessionMessageDispatcher` 按 `SceneType` 将 WS 帧路由到各场景会话接口。接受 WS 实时帧的场景会话接口（`FreeChatSessionService`/`CustomSessionService`/`IeltsSessionService`）必须各自声明 `startSession/addMessage/endSession` 生命周期形状（`addMessage` 由 `SessionMessageDispatcher` 消费）。

场景会话 Impl 的职责：

- 基于已经准备好的 `sceneId` 启动会话。
- 创建 Realtime 会话、维护会话生命周期。
- 接收、验证并持久化消息。
- 结束会话和释放临时资源。

边界：

- 不调用 `AuthService` 重新完成场景权限或次数校验；这些已由场景生成阶段完成。
- 仍必须校验当前请求者是否拥有目标 `sceneId/sessionId`，防止越权访问。
- 不生成场景、不拼 Prompt、不选择题目、不推进业务阶段、不生成评分。
- 不得创建通用 `SessionServiceImpl`。

会话查询与生命周期实现属于 `SessionLifecycleManager`，不进入场景会话接口。

### 2.4 `EvaluationService`

位置：`service/evaluation/EvaluationService.java`

```java
public interface EvaluationService<R, D> {
    DialogueTurnEvaluationResult evaluateTurn(DialogueTurnEvaluationCommand command);
    R generateReport(String sceneId);
    D getEvaluation(String sceneId);
}
```

职责：逐轮评分、场景报告生成和评分结果查询。它可读取 Session 消息和语音证据，但不能
管理会话生命周期或推进 Scene Flow。

FreeChat 当前不评分，因此不实现。Custom 与 IELTS 分别实现；不得创建通用
`EvaluationServiceImpl`。

支持评分的场景必须声明专用 Evaluation 接口继承公共契约，额外的历史、详情或专项评分
方法放在专用接口中。

### 2.5 `AiProvider`

位置：`provider/AiProvider.java`

```java
public interface AiProvider {
    String exchangeRealtimeSdp(String offerSdp, String token);
    byte[] generateSpeechAudio(String text, String token);
    String executeLlmTask(String prompt, String token);
    String convertAudioToText(byte[] audio, String token);
    String evaluatePronunciation(String text, byte[] audio, String token);
}
```

职责：定义供应商无关的 AI 能力。业务代码只依赖 Provider 接口或 Registry；Qwen、
Doubao、DeepSeek、MiniMax、讯飞等供应商差异全部留在 `infrastructure`。

## 3. 当前实现矩阵

> "场景准备"与"会话"列是**职责**（由场景专用接口承载），不是可注入的公共契约类型；`SceneService`/`SessionService` 基类已删除。"Flow/Evaluation"是保留的治理契约。

| 场景 | 场景准备 | Flow | 会话 | Evaluation |
|---|---|---|---|---|
| FreeChat | `FreeChatSceneService → Impl` | 无 | `FreeChatSessionService → Impl` | 无 |
| Custom | `CustomSceneService → Impl` | `CustomSceneFlowService → Impl` | `CustomSessionService → Impl` | `CustomEvaluationService → Impl` |
| IELTS | `IeltsSceneService → Impl` | `IeltsSceneFlowService → Impl` | `IeltsSessionService → Impl` | `IeltsEvaluationService → Impl` |

所有实现类必须位于对应模块的 `impl` 包并以 `Impl` 结尾。以下类不允许存在：

```text
SceneServiceImpl
SceneFlowServiceImpl
SessionServiceImpl
EvaluationServiceImpl
```

这些通用实现会把场景职责重新耦合到一起，与当前架构冲突。

## 4. 依赖方向与职责边界

```text
Controller / WebSocket
        │
        ▼
场景专用 Service 接口与实现
        │
        ├── Component / Domain
        ├── Provider
        └── Repository
                 ▲
                 │
Infrastructure（外部厂商、数据库、存储和框架实现）
```

规则：

- Controller 不得依赖 Mapper、Repository 或厂商实现。
- Service 不得依赖 Controller、数据库 Entity 或 Mapper。
- Component 不拥有跨重启的业务真相。
- Repository 是 Service 访问数据库的唯一入口。
- Infrastructure 实现技术细节，不拥有业务流程。
- 不同场景实现不得相互调用来复用业务；应下沉真正通用的纯组件或 Provider。
- 遇到循环依赖应重新划分职责，不能用 `@Lazy` 掩盖。

## 5. 目录规范

源码根目录：

```text
src/main/java/com/unispeaking
├── controller
├── websocket
├── service
│   ├── scene
│   │   ├── SceneFlowService.java
│   │   ├── {Scene}SceneService.java
│   │   ├── {Scene}SceneFlowService.java
│   │   └── impl
│   ├── session
│   │   ├── {Scene}SessionService.java
│   │   └── impl
│   ├── evaluation
│   │   ├── EvaluationService.java
│   │   ├── {Scene}EvaluationService.java
│   │   └── impl
│   ├── auth
│   ├── profile
│   ├── asset
│   └── achievement
├── component
│   ├── scene
│   ├── session
│   ├── evaluation
│   ├── statemachine
│   ├── recording
│   └── ...
├── domain
│   ├── dto/{scene,session,evaluation,...}
│   ├── po/{scene,session,evaluation,...}
│   └── vo/{scene,session,evaluation,...}
├── provider
├── infrastructure
│   ├── ai/{vendor}
│   ├── evaluation
│   ├── realtime
│   ├── storage
│   ├── persistence
│   └── config
└── common
    └── persistence/{codec,typehandler}
```

### 5.1 `controller`

只负责 HTTP 协议：路由、参数校验、当前用户识别、状态码和统一响应包装。

禁止：

- 拼 Prompt、执行状态机、计算评分或控制事务。
- 信任客户端传入的 `userId`。
- 直接访问数据库或厂商 SDK。
- 为同一场景的一个附属资源新建单用途 Controller。

同一场景的接口应归并到对应场景 Controller。例如 IELTS 录音读取属于
`IELTSSceneController` 的 `/api/ielts/recordings/...`，不单独创建
`IeltsRecordingController`。

Controller 注入场景专用接口，不直接依赖 Impl。专用接口负责暴露场景特有的查询、搜索等
方法；有阶段/评分的场景按治理契约（`SceneFlowService`/`EvaluationService`）声明专用接口。
场景接口遵循**接口最小化**：只暴露被 Controller、其他 Service 或 WebSocket Dispatcher
消费的方法；归属校验与内部读不进接口（下沉 Impl 私有或 `OwnershipPolicy`）。

### 5.2 `service`

`scene`、`session`、`evaluation` 包的根目录放治理契约（`SceneFlowService`/`EvaluationService`）和场景专用接口，具体场景实现全部放 `impl`。结构为"治理契约（可选）→ 场景专用接口 → 场景 Impl"；`SceneService`/`SessionService` 基类已删除，场景准备方法（`generate`）与会话生命周期形状（`startSession/addMessage/endSession`）由场景专用接口自身声明。

其他横向业务（如 auth、profile、asset、achievement）仍采用：

```text
service/{module}/{Business}Service.java
service/{module}/impl/{Business}ServiceImpl.java
```

不要为了目录对称创建空接口、空实现或只有一行转发的 Service。

### 5.3 `component`

Component 用于场景生成器、状态机、运行时协调器、纯计算器和录音存储等内部能力。

典型归类：

- 场景内容生成辅助：`component/scene`。
- Session 生命周期协调、消息分发、临时 Registry：`component/session`。
- 评分处理器：`component/evaluation`。
- 状态转换：`component/statemachine`。
- 本地录音保存和读取：`component/recording`。

录音不是独立业务 Service；它是 Session/场景使用的基础组件。若未来替换为对象存储，
存储技术实现进入 `infrastructure/storage`，业务归属仍不改变。

### 5.4 `domain`

- `dto`：跨 Controller、Service、WebSocket 边界传输的数据。
- `po`：业务持久化记录或聚合数据，不等同于数据库 Entity。
- `vo`：值对象、枚举和不可变结果。

场景特化数据按职责分包，而不是按场景新建顶层包：

```text
domain/dto/scene/IeltsSceneRequest.java
domain/dto/session/StartSessionCommand.java
domain/dto/evaluation/IeltsEvaluationReport.java
domain/vo/scene/IeltsStage.java
```

禁止创建 `domain/ielts`、`domain/custom` 等与现有维度平行的第二套结构。

Domain 不得依赖 Spring MVC、Mapper、数据库 Entity 或厂商 SDK。

### 5.5 `provider` 与 `infrastructure`

`provider` 放稳定能力接口、能力枚举和 Registry；供应商实现放：

```text
infrastructure/ai/qwen
infrastructure/ai/doubao
infrastructure/ai/deepseek
infrastructure/ai/minimax
infrastructure/ai/iflytek
```

API Key、Workspace ID、模型名和 URL 通过配置注入，不能进入 DTO、源码或日志。新增供应商
应实现现有能力接口并注册到 Registry，不修改场景 Service 来硬编码厂商选择。

### 5.6 `common`

仅放真正跨模块复用的无状态纯逻辑：异常、统一响应、Prompt 模板加载、Parser、Validator、
Calculator、Policy 和通用工具。

禁止 `CommonService`、万能 `Utils`、含业务状态的 Helper。能明确归属场景或模块的逻辑应
放到相应 Component。

## 6. 新增场景的标准落位

以下以新场景 `Debate` 为例。只创建业务实际需要的文件。

### 6.1 必需文件

```text
controller/DebateSceneController.java

service/scene/DebateSceneService.java
  // 场景专用接口，不继承已删除的 SceneService 基类；
  // generate 为该接口自身主声明
service/scene/impl/DebateSceneServiceImpl.java
  implements DebateSceneService

service/session/DebateSessionService.java
  // 声明 startSession/addMessage/endSession 会话生命周期形状
service/session/impl/DebateSessionServiceImpl.java
  implements DebateSessionService

domain/dto/scene/DebateSceneRequest.java
domain/dto/scene/DebateSceneResult.java
domain/dto/scene/DebateDialogueSceneContext.java
```

### 6.2 有多阶段流程时增加

```text
service/scene/DebateSceneFlowService.java
  extends SceneFlowService<DebateStage>
service/scene/impl/DebateSceneFlowServiceImpl.java
  implements DebateSceneFlowService

domain/vo/scene/DebateStage.java
component/statemachine/DebateStateMachine.java
```

无阶段场景不得为了矩阵完整而实现 `SceneFlowService`。

### 6.3 支持评分时增加

```text
service/evaluation/DebateEvaluationService.java
  extends EvaluationService<DebateEvaluationReport, DebateEvaluationDetail>
service/evaluation/impl/DebateEvaluationServiceImpl.java
  implements DebateEvaluationService

domain/dto/evaluation/DebateEvaluationReport.java
domain/dto/evaluation/DebateEvaluationDetail.java
```

评分 Prompt、解析器和规则只有在跨场景复用时才放 `common/evaluation`；场景专用逻辑放
`component/evaluation` 或具体实现内部。

### 6.4 需要持久化时增加

```text
infrastructure/persistence/entity/scene/DebateEntity.java
infrastructure/persistence/mapper/scene/DebateMapper.java
infrastructure/persistence/repository/scene/DebateRepository.java
common/persistence/codec/scene/DebateJsonbCodec.java   // 仅需要 JSONB 时
```

同时：

1. 在 `SceneType` 增加明确类型和 ID 前缀。
2. 新增 Flyway 迁移及必要索引/约束。
3. 更新数据库归属校验和删除顺序。
4. 增加 Repository 集成测试。

### 6.5 不允许的落位

```text
service/debate/...                       // 不新增平行场景模块
domain/dto/debate/...                    // DTO 按职责分包
service/*/impl/SceneServiceImpl.java     // 不恢复通用实现
service/*/impl/SessionServiceImpl.java   // 不恢复通用会话实现
service/scene/impl/DebateSceneServiceImpl.java
  implements SceneFlowService           // Impl 不越过场景专用接口直接实现治理契约
controller/DebateRecordingController.java // 附属接口并入场景 Controller
```

## 7. 场景调用顺序

### 7.1 FreeChat

```text
Controller
  → FreeChatSceneServiceImpl.generate
  → FreeChatSessionServiceImpl.startSession
  → Realtime 对话
  → addMessage / endSession
```

Scene 先完成认证、Prompt 和场景落库，Session 只接管会话。

### 7.2 Custom

```text
CustomSceneServiceImpl.generate
  → CustomSceneFlowServiceImpl（WORD/PHRASE/SENTENCE/DIALOGUE）
  → CustomSessionServiceImpl
  → CustomEvaluationServiceImpl
```

### 7.3 IELTS

```text
IeltsSceneServiceImpl.generate
  → IeltsSceneFlowServiceImpl（按专项或模考推进）
  → IeltsSessionServiceImpl（每个 Part 独立 Session）
  → IeltsEvaluationServiceImpl（Part 评分与模考聚合）
```

完整模考的多个 Session 通过同一 `ieltsId/sceneId` 关联；Part 评分和整场总评不得混为同一
条记录。

## 8. Controller 与 API 规范

- 路径以 `/api` 开头，资源名使用复数或稳定业务名。
- 请求使用 DTO 和 Bean Validation，不接收 Map 代替稳定契约。
- 用户身份从安全上下文获取；任何用户资源都必须执行归属校验。
- 使用统一 `ApiResponse` 和全局异常处理，不在 Controller 内吞异常。
- 二进制、音频等响应明确 `Content-Type`、缓存和权限策略。
- 同一资源的查询、生成、录音、报告等端点尽量集中到同一个场景 Controller。
- Controller 不返回基础设施 Entity 或厂商原始响应。
- 改动接口时同步更新 `docs/API接口文档.md`、前端调用和 Controller 测试。

## 9. Session 与实时通信规范

- `sceneId` 表示已准备场景，`sessionId` 表示一次会话，二者不得混用。
- WebSocket 握手和消息处理必须验证 JWT 与 Session 归属。
- Session 消息写入统一通过 `SessionService.addMessage` 或其内部组件。
- Realtime 临时凭证、SDP 和厂商事件属于 `infrastructure/realtime` 或 Provider。
- 前端与模型 DataChannel 直接完成的暂停、恢复和打断不伪造成新的业务 Service。
- 进程内 `ActiveSessionRegistry` 可用于活跃连接，但不能作为历史会话的唯一来源。
- 录音元数据/链接保存在 Session 消息中；录音文件读取仍需校验 Session 所有权。

## 10. 状态机规范

- 只有存在明确状态、事件、转换和终止条件时才创建状态机。
- 状态枚举放 `domain/vo/scene`，执行器放 `component/statemachine`。
- 状态机由对应的 `{Scene}SceneFlowServiceImpl` 持有；Session 只能通知 Flow 初始化或
  清理 session 绑定状态，不能直接推进或查询业务状态机。
- 状态转换不得只依赖前端按钮；后端保存可恢复状态。
- 状态机不得直接调用 Controller 或厂商 SDK。
- 定时、静默、最大回答时长等规则应有单元测试，覆盖最后一题、提前结束和超时边界。

## 11. 持久化规范

### 11.1 唯一位置

```text
infrastructure/persistence
├── entity/{module}
├── mapper/{module}
└── repository/{module}

common/persistence
├── codec/{module}
└── typehandler
```

Controller 和 Service 不得直接引用 Mapper 或 Entity；Repository 向上返回 Domain 数据。

### 11.2 MyBatis-Plus

- Mapper 继承 `BaseMapper<Entity>`。
- 查询与更新使用 Lambda Wrapper。
- JSONB 使用统一 Codec/TypeHandler。
- 多表原子写入由 Service 事务或聚合 Repository 完成。
- 复合主键通过包含全部字段的 Wrapper 定位，不伪造单字段 `@TableId`。

运行时代码禁止：

- Mapper SQL 注解和 XML SQL。
- `JdbcTemplate`、原生 JDBC、JPA/EntityManager。
- 字符串拼接 SQL。
- Wrapper 的 `.last()`、`.apply()`、`.inSql()`、`.notInSql()`、`.setSql()`。

### 11.3 Flyway

运行时 DDL 只允许位于：

```text
src/main/resources/db/migration/V{version}__{description}.sql
```

当前 `V1__baseline.sql` 是新库基线。它一旦被共享环境执行，后续只新增 V2、V3 等迁移，
不能改写旧版本。禁止同时维护 `schema.sql` 或手工执行等价 DDL。

每次结构变化必须包含必要索引、约束、注释、兼容策略和对应集成测试。

## 12. Prompt、评分与 Provider 规范

- Prompt 模板和 Builder 位于 `common/prompt`，不要在 Controller 中拼长字符串。
- Part/阶段特有 Prompt 只在进入对应阶段时注入。
- LLM 输出必须经过 Parser、Validator 和缺省策略，不能直接信任 JSON。
- 文本评分和语音评分必须保留证据边界，发音不能从 ASR 文本推测。
- `turn_evaluation` 是逐轮反馈数据；整场报告是否使用它必须由 Evaluation 实现显式决定。
- IELTS Part 评分不产生单 Part 总分；完整模考才聚合 Overall Band Score。
- 异步评分不得阻塞下一个 Part，但在最终报告页必须等待所有必需评分完成或返回明确状态。
- Provider 异常统一翻译为业务异常，不把厂商响应和密钥暴露给客户端。

## 13. 命名规范

- Java 包名全小写。
- 接口：`{Capability}Service`。
- 实现：`{Scene}{Capability}ServiceImpl`，且位于 `impl`。
- Controller：`{Scene}SceneController` 或稳定资源名。
- Component：按真实职责命名，如 `StateMachine`、`Coordinator`、`Store`、`Calculator`。
- Repository / Mapper / Entity：`{Aggregate}Repository`、`{Aggregate}Mapper`、
  `{Aggregate}Entity`。
- DTO：`Request`、`Response`、`Command`、`Result`、`Detail`、`Report`。
- Java 类型中的缩写按 CamelCase：`IeltsSceneServiceImpl`；不要继续扩散全大写类名前缀。

场景 ID 前缀必须可判定场景类型：

```text
freechat_...
custom_...
ielts_...
```

新增场景必须定义唯一前缀并更新 `SceneType` 的解析规则。

## 14. 配置、安全与日志

- 配置使用 `@ConfigurationProperties`，凭证只从环境变量注入。
- 日志不得记录 JWT、API Key、密码、完整 SDP、原始音频或敏感个人资料。
- 记录业务 ID、场景类型、阶段、耗时、状态和脱敏错误码，便于排障。
- 文件上传校验大小、类型、所有权和路径；禁止路径穿越。
- 本地录音目录必须可配置，生产环境应评估对象存储、生命周期和访问签名。
- Redis/MQ/OSS 只有明确需求和失败策略后才能引入，不能先建空抽象。

## 15. 测试规范

新增或修改场景至少覆盖：

1. `SceneService.generate` 的权限、配额、内容和落库。
2. 有 Flow 时的 start/current/next/completed 和非法转换。
3. Session 启动、消息、结束、查询及越权。
4. 有 Evaluation 时的逐轮评分、报告生成、查询和外部失败。
5. Controller 路由、参数校验、响应结构和身份来源。
6. Repository 的 PostgreSQL 集成测试、索引和复合键行为。
7. 前端调用契约、关键页面构建和 Realtime 事件约束。

常用命令：

```bash
cd backend/unispeaking-server
./mvnw test
./mvnw -Pci-integration -DskipUnitTests verify

cd ../../frontend/web
npm run build
npm run check:routes
npm run check:realtime-events
```

测试不得依赖执行顺序、真实生产凭证或不可控公网服务。

## 16. 新场景提交检查清单

提交前逐项确认：

- [ ] 未修改两治理契约（`SceneFlowService`/`EvaluationService`）的方法签名。
- [ ] 已建立“场景专用接口 → Impl”落位（有阶段/评分场景按治理契约声明专用接口）。
- [ ] 场景实现位于 `service/*/impl` 且以 `Impl` 结尾。
- [ ] 未创建通用 Flow/Evaluation 实现，也未伪造已删除的 Scene/Session 基类。
- [ ] 场景准备已完成认证、配额、Prompt、内容和落库，未启动 Session。
- [ ] 会话层未重复准备场景，也未承担评分或 Flow。
- [ ] 状态机、录音、生成器和协调器已放入 Component。
- [ ] 场景附属端点合并到对应场景 Controller。
- [ ] Controller 未访问 Mapper、Repository、Entity 或厂商实现。
- [ ] DTO 按 scene/session/evaluation 职责分包。
- [ ] 数据库变更使用新 Flyway 版本（生产 baseline=8，新迁移强制 V9+）并包含索引与测试。
- [ ] 新场景有唯一 ID 前缀和 `SceneType` 映射。
- [ ] 已覆盖权限、越权、边界状态和外部失败。
- [ ] 已同步 API 文档和前端调用。
- [ ] 未提交密钥、真实 `.env`、录音或用户数据。

## 17. 禁止事项汇总

- 修改治理契约（`SceneFlowService`/`EvaluationService`）来迁就某个场景。
- Impl 跳过场景专用接口而直接实现治理契约。
- 恢复通用 Flow/Evaluation 实现，或伪造已被删除的 `SceneService`/`SessionService` 基类。
- 在场景 Service 中启动 Session，或在 Session 中生成场景。
- 把录音、状态机、Parser、Prompt Builder 包装成独立 Service。
- 为单个附属端点创建孤立 Controller。
- Controller 直接访问持久化或供应商实现。
- Service 直接访问 Mapper、Entity 或拼 SQL。
- 只依赖前端状态，导致后端流程无法恢复。
- 复制一套平行目录来隔离新场景。
- 修改已执行 Flyway 版本；新迁移不得使用 V3-V8 编号（生产 baseline=8 双轨分叉）。
- 将密钥、JWT、完整 SDP、原始音频写入日志或前端变量。
