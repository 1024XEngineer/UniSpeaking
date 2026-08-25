# UniSpeaking 架构设计

> 申请最佳架构设计奖 PPT 文字稿（按当前代码实现校准）
>
> 说明：本稿中的方法名、入参、出参和幂等键均以当前后端实现为准。代码片段用于表达边界，不代表项目中存在一个同名的统一接口。

## 第 1 页｜封面

### UniSpeaking 架构设计

**面向多场景口语学习的统一、可复用、低耦合架构**

通过统一场景准备、场景流程、业务会话、评价处理与 AI 能力路由，支撑自由聊天、自定义场景、IELTS 口语和模拟面试持续演进。

---

## 第 2 页｜为什么需要统一架构

### 从“完成一个场景”到“支撑一类业务”

系统同时承载四类口语业务：

- 自由聊天：直接进入实时对话；
- 自定义场景：从用户描述生成词、词组、句子和角色扮演目标；
- IELTS：题库、Part 1/2/3 流程、考试规则和雅思评分；
- 模拟面试：JD/简历材料、主题推进、录音和五维报告。

如果每个业务各自实现，会出现：

1. 实时会话创建、消息保存、结束和资源回收重复实现；
2. 场景 Prompt、角色规则、题目推进和面试追问互相耦合；
3. LLM、Realtime、TTS、ASR、发音评分分别接入，供应商替换成本高；
4. 评分延迟、模型失败、并发提交、重复结束和任务恢复难以统一处理。

### 架构目标

稳定共性：会话生命周期、实时连接编排、消息证据、AI 路由、评价持久化。

隔离差异：场景内容、学习阶段、题目顺序、面试主题状态和业务评分规则。

---

## 第 3 页｜真实整体架构与模块职责

### 五个核心边界 + 异步协调器，外加面试专用状态机

```text
Controller
   |
   +-- Scene Service -------------------- 场景定义、内容、Prompt、归属校验
   +-- Scene Flow Service --------------- 场景级阶段状态机（Custom / IELTS）
   +-- Session Service ------------------ 业务会话编排
   |      +-- SessionLifecycleManager
   |      +-- RealtimeSessionCoordinator
   |      +-- SessionMessageDispatcher
   +-- Evaluation Service / Processor ---- 单轮、句子、会话、IELTS 评价
   +-- Async Coordinators ---------------- 自定义场景生成、IELTS 评价、面试报告
   +-- AiProviderRegistry ---------------- 按能力路由、故障转移、调用计量
```

### 边界定义

**Scene Service：练什么？**

生成或读取场景资产，准备业务 Prompt，并把用户、场景、目标和配置交给会话层。

**Scene Flow Service：业务阶段怎么推进？**

只存在于需要场景级阶段的业务。Custom 为 `WORD → PHRASE → SENTENCE → DIALOGUE → COMPLETED`；IELTS 为 `PART1 → PART2 → PART3 → COMPLETED`，专项练习只走选定 Part。

**Session Service：一次练习发生了什么？**

创建 `sessionId`、保存会话事实；对需要留存证据的场景保存最终转写和音频关联；编排 WebRTC SDP、绑定供应商会话、结束并回收资源。

**Evaluation：练得怎么样？**

调用发音评测和 LLM，生成单轮结果、句子朗读结果、对话报告、IELTS Part/Final 结果和面试报告。

**AiProviderRegistry：AI 能力如何获得？**

按 `REALTIME / LLM / SCORING / TTS / TRANSCRIPTION` 路由模型和供应商，执行可重试故障转移，并记录每次调用的用量与成本。

**关键修正：**项目没有一个统一的 `ConversationSession` 或一个承载全部阶段的通用 `LearningFlow` 接口；共性通过编排组件复用，差异通过具体 Service 和状态机实现。

---

## 第 4 页｜Scene：真实场景服务接口

### 场景服务不是泛型 `generate(R)`，而是按业务返回可运行的 Scene Context

#### 自由聊天

```java
FreeChatSceneResult generate(FreeChatSceneRequest request);
FreeChatSceneContext prepare(FreeChatSceneRequest request);
```

入参：`FreeChatSceneRequest(prompt)`。

出参：`FreeChatSceneResult(sceneId, dialoguePrompt)`；`prepare` 额外返回当前 `userId`。

实现要点：读取用户画像和 `FREE_CHAT` 场景配置，通过 `FiveLayerPromptBuilder` 组合 Prompt。自由聊天不生成词/词组/句子资产。

#### 自定义场景

```java
CustomSceneGenerationResponse generate(CustomSceneRequest request);
CustomDialogueSceneContext prepareDialogue(String sceneId);
```

入参：`sceneInput`、`userPreference`，以及可选的实时连接参数。

出参：场景 ID、标题、角色、学习目标、预计时长、`wordList / phraseList / sentenceList` 和 `scenePrompt`；对话准备阶段额外返回 `successFactorJson` 和 `learningGoal`。

当前入口由 `CustomSceneGenerationCoordinator.submit` 异步提交，返回 `taskId + sceneId + PROCESSING/COMPLETED/FAILED`；后台再调用 `generateForUser` 持久化场景。

#### IELTS

```java
IeltsGenerationResponse generate(IeltsGenerationRequest request);
IeltsDialogueSceneContext prepareDialogue(String ieltsId, String requestedVoiceId);
```

入参：`mode = PART_PRACTICE | MOCK_TEST`、`part`、可选 `topicId`；对话准备阶段传入练习 ID 和考官音色。

出参：`ieltsId`、模式、Part、题目内容、音色、当前流程阶段和 examiner Prompt。

#### 模拟面试

```java
InterviewMaterialDraft prepareMaterials(InterviewMaterialPreparationInput input);
InterviewSceneResult generate(InterviewSceneRequest request);
InterviewDialogueSceneContext prepareDialogue(String sceneId);
```

先对 JD/简历文本或图片做提取、脱敏和材料确认；再由 LLM 生成 `InterviewContext`，由 `InterviewPromptBuilder` 生成实时面试 Prompt，持久化 `interview_scene`。

### Scene 边界

Scene Service 负责场景资产、Prompt 和归属校验；不负责创建 `sessionId`、执行 SDP、保存会话消息，也不直接生成实时会话报告。

---

## 第 5 页｜Flow：真实流程与状态机

### 场景级 Flow 只服务 Custom 和 IELTS

实际公共实现是：

```java
public class SceneFlowService<S> {
    S start(String sceneId);
    S current(String sceneId);
    S next(String sceneId);
    boolean isCompleted(String sceneId);
    void clear(String sceneId);
}
```

它由 `ConcurrentHashMap<sceneId, stage>` 保存进程内阶段；没有独立 `flowId`，也不是持久化工作流引擎。

### CustomSceneFlowService

```text
WORD -> PHRASE -> SENTENCE -> DIALOGUE -> COMPLETED
```

- `furthestStages` 防止跳过尚未解锁阶段；
- `content(sceneId, stage)` 返回当前已解锁内容；
- 进入对话后，`ScenarioDialogueStateMachine` 以 `sessionId` 保存成功因素、有效轮次和结束原因；
- `advanceDialogueState(sceneId, sessionId, turnNo, transcript)` 通过语义目标提取推进对话子流程。

### IeltsSceneFlowService

```text
MOCK_TEST:     PART1 -> PART2 -> PART3 -> COMPLETED
PART_PRACTICE: 选定 Part -> COMPLETED
```

- `IeltsQuestionStateMachine` 按应用侧题目序列推进，拒绝模型自行加题或重复题目；
- `IeltsPart2StateMachine` 明确控制 `PREPARATION -> LONG_TURN -> FINISHED`；
- `startSessionState / advanceDialogueState / advancePart2State` 均要求 `sceneId + sessionId` 归属匹配。

### Interview 与 FreeChat 的真实情况

- FreeChat 没有场景级 Flow，创建会话后直接进入 `DIALOGUE`；
- Interview 不实现 `SceneFlowService`，由 `InterviewTopicStateMachine` 按 `sessionId` 推进主题、追问、必选主题和结束条件。

### 重要边界

这些状态机目前是进程内内存状态；代码注释明确说明进程重启后没有恢复对象。持久化的是场景、会话、消息和评价事实，不是完整的 Flow/Topic 状态快照。

---

## 第 6 页｜Session：真实会话生命周期

### 没有统一的 `ConversationSession` 接口，实际由共用生命周期组件编排

```java
StartSessionResponse startSession(StartSessionCommand command);
void addMessage(String userId, String sessionId, Message message);
void endSession(String userId, String sessionId, String stopTime);
void terminateSceneSession(
    String userId, String sessionId,
    SessionStatus terminalStatus, Instant endedAt);
```

`StartSessionCommand` 包含：`userId、sceneId、sceneType、stage、prompt`。

`StartSessionResponse` 返回：`sessionId、startTime`。

### 会话 ID 与持久化

`SessionIdGenerator` 生成：

```text
{sceneTypePrefix}_session_{UUID}
```

例如 `custom_session_<uuid>`、`ielts_session_<uuid>`。同一 `sessionId` 同时作为运行时会话键和 `practice_session.session_id` 主键。

消息通过 `SessionMessageDispatcher` 按场景分发到各 Session Service；非自由聊天消息落入 `session_message`，主键为 `(session_id, message_no)`，保存 owner、最终转写和可选音频地址。

### 实时连接与业务会话分离，但失败语义要如实表达

```java
RealtimeConnectionResult exchangeSdp(...);
StartSceneSessionResponse connect(...);
```

`RealtimeSessionCoordinator` 调用 `RealtimeSdpExchange`，拿到 `answerSdp、providerSessionId、providerType、modelId、traceId、credentialExpiresAt`，写回本地会话；`RealtimeSessionTerminator` 在结束或失败时 best-effort 停止供应商会话并记录实时用量。

本地会话状态是：

```text
CREATED -> CONNECTING -> WAITING_CLIENT -> ACTIVE
                         -> FAILED
ACTIVE <-> PAUSED / INTERRUPTED -> COMPLETED or FAILED
```

初始 SDP 建连失败时，当前实现会将本地会话置为 `FAILED` 并移除活动注册；因此不能宣称“连接失败后自动恢复原会话”。真正可复用的是本地会话事实、供应商标识和结束清理边界。

---

## 第 7 页｜Evaluation：真实评价链路

### 公共包装存在，但评价能力由 `EvaluationProcessor` 按业务执行

```java
DialogueTurnEvaluationResult evaluateTurn(
    DialogueTurnEvaluationCommand command);

R generateReport(String sceneId);
D getEvaluation(String sceneId);
```

单轮命令：`sessionId、turnNo、transcript、audio`；单轮结果包含综合分、节奏、语调、完整度、发音、流利度、反馈和逐词评分。

### 三种评价粒度

**句子朗读（Custom）**

```java
SentenceEvaluationResponse evaluateSentence(String sentenceId, byte[] audio);
```

调用发音评测，写入 `sentence_evaluation`，返回 `overallScore、passed、words`。每次朗读都是一次独立 attempt，`readingId = sentence_reading_<随机短 ID>`，这里是“允许重复练习”，不是幂等覆盖。

**对话单轮（Custom / IELTS）**

发音评测与 LLM 语言反馈并行执行；结果写入 `turn_evaluation`，主键 `(session_id, turn_no)`。供应商不可用时按策略落降级结果，保留转写和可解释状态。

**整场评价**

- Custom：结束接口内调用 `CustomEvaluationService.generateReport(sceneId)`；`session_evaluation.session_id` 主键提供缓存，已有结果直接返回；
- IELTS：`IeltsEvaluationCoordinator.submit(ieltsId, sessionId)` 异步提交，GET 轮询 `PROCESSING / COMPLETED / FAILED`；Part 结果按 `sessionId`，模拟考试 Final 按 `ieltsId`；
- Interview：结束时创建 `interview_report` 任务行，后台按轮次评分音频，再调用一次 LLM 生成五维面试报告。

### Evaluation 边界

Evaluation 读取会话消息、录音和场景资产，调用 AI 并持久化学习结果；它不负责 WebRTC 建连，也不负责推进 Custom/IELTS 的场景级阶段。

---

## 第 8 页｜AI Provider：真实能力契约与路由

### 最底层稳定契约

```java
public interface AiProvider {
    String exchangeRealtimeSdp(String offerSdp, String token);
    byte[] generateSpeechAudio(String text, String token);
    String executeLlmTask(String prompt, String token);
    String convertAudioToText(byte[] audio, String token);
    String evaluatePronunciation(
        String text, byte[] audio, String token);
}
```

实际实现由能力子类隔离：`RealtimeProvider、LlmProvider、ScoringProvider、TtsProvider、TranscriptionProvider`。它们分别对应 `REALTIME、LLM、SCORING、TTS、TRANSCRIPTION`，不是一个供应商实现所有能力。

### 业务层真正调用的是 AiProviderRegistry

```java
routeRealtime(...)
executeLlmTaskRouted(...)
generateSpeechAudioRouted(...)
convertAudioToTextRouted(...)
evaluatePronunciationRouted(...)
```

Registry 根据数据库/配置路由选择模型和供应商；显式模型可以直连指定模型，未指定模型则按能力路由。当前适配器包括 Qwen、Qiniu MaaS/RTI、DeepSeek、讯飞、阿里云、豆包、MiniMax 等。

### 调用上下文、故障转移与计量

```java
AiInvocationContext(
    UUID logicalRequestId,
    String userId,
    String sessionId,
    String businessScene,
    String routeKey)
```

`AiInvocationContexts.call` 将后台任务的用户、会话和业务场景传入 Provider 层。Registry 对可重试异常按路由尝试下一个模型；每次尝试记录 `invocation_id、logical_request_id、attempt_no、provider_request_id、usage、status、error_code、fallback_from_model_id` 到 `ai_model_invocations`。

### 边界说明

项目实际没有原稿中的 `LlmResult / RealtimeSession / AudioResult / AsrResult` 统一返回体系；供应商客户端以基础类型返回，计量和供应商请求号通过 `AiProviderResponse<T>` 和 Registry 内部封装承载。

---

## 第 9 页｜关键技术风险：已实现机制与真实边界

### 1. WebRTC 建连失败

**已实现：** `SessionLifecycleManager` 先创建本地会话；`RealtimeSessionCoordinator` 负责 `CONNECTING`、SDP 交换、供应商会话绑定和失败清理；`RealtimeSessionTerminator` best-effort 停止供应商连接并写实时用量。

**不能宣称：** 当前没有跨重启的实时连接恢复，也没有初始建连失败后的自动续接；失败会话进入 `FAILED`。

### 2. 第三方模型异常

**已实现：** `AiProviderRegistry.invokeMeasuredModels` 统一分类异常、按能力路由、只对可重试错误故障转移，并记录每个 attempt；业务层不依赖供应商 SDK 异常类型。

### 3. 评分延迟

**已实现：** 单轮发音和语言反馈并行；IELTS 整场评价、Custom 场景生成、Interview 报告使用后台 Executor、数据库状态和过期任务重派。

**真实差异：** Custom 对话最终报告仍在结束请求中生成；不是所有评价都异步。

### 4. 并发会话与归属

**已实现：** 活跃会话以 `sessionId` 隔离；需要场景绑定的操作校验 `userId + sceneId + sceneType + sessionId`，通用会话操作至少校验 `userId + sessionId`；数据库以 `practice_session` 和 `session_message` 保存事实。

### 5. 幂等与重复提交

幂等不是一个统一 `requestId` 参数，而是按业务事实选择主键和 CAS：

| 操作 | 实际幂等/去重键 | 机制 |
|---|---|---|
| 结束普通会话 | `sessionId` | `practice_session` 仅从非终态更新；同一终态重复结束视为成功 |
| 结束面试并创建报告 | `sessionId` | `interview_report.session_id` 主键；`createIfAbsent` 捕获 PK 冲突，只有创建者提交任务 |
| 面试提交轮次 | `sessionId + turnNo`，并比对已保存 transcript | 会话锁、owner=1 消息计数、内容一致性校验；重复轮次返回已处理状态 |
| Custom/IELTS 单轮评价 | `(sessionId, turnNo)` | `turn_evaluation` 复合主键 + `upsert` |
| Custom 整场报告 | `sessionId` | `session_evaluation.session_id` 主键，已有结果直接返回 |
| IELTS Part 评价 | `sessionId` | `ielts_part_evaluation` 唯一 session；行 ID 为 `ielts_part_<sessionId>` |
| IELTS Mock Final | `ieltsId` | `ielts_evaluation` 行 ID 为 `ielts_mock_<ieltsId>` |
| 自定义场景生成 | `taskId` 查询、`sceneId` 唯一 | 任务终态更新要求 `taskId + PROCESSING`；重复提交本身不会复用旧任务 |
| AI 调用审计 | `logicalRequestId + attemptNo` 分组；`invocationId` 为每次 attempt UUID | 用于追踪和计量，不是客户端请求幂等键 |

**必须如实说明：** 自由聊天/场景启动没有客户端幂等键；重复点击会生成新的 `sessionId`。句子朗读也故意保留每次 attempt，不做覆盖。

### 6. 进程重启与分布式部署

场景 Flow 和对话子状态目前在内存 Map 中；异步任务有数据库状态、过期重派和失败重试，但跨实例的单飞主要依赖数据库主键/CAS，不能把 JVM 内存集合描述为分布式锁。

---

## 第 10 页｜四种业务如何串起真实模块

### 自由聊天：Scene → Session → Provider

```text
FreeChatSceneService.prepare(FreeChatSceneRequest)
  -> FreeChatSessionService.startSession(StartFreeChatRequest)
  -> SessionLifecycleManager.startSession(StartSessionCommand)
  -> RealtimeSessionCoordinator.connect(... offerSdp, provider, model, voice)
  -> AiProviderRegistry.routeRealtime / RealtimeSdpExchange.exchangeSdp
  -> SessionMessageDispatcher.addMessage（自由聊天消息不落 session_message）
  -> FreeChatSessionService.endSession(sessionId)
```

自由聊天没有场景级 Flow 和最终评价报告。

### 自定义场景：异步 Scene → Flow → Session → Evaluation

```text
CustomSceneGenerationCoordinator.submit(CustomSceneRequest)
  -> taskId / sceneId / PROCESSING
  -> CustomSceneService.generateForUser(...)
  -> CustomSceneGenerator.generate（LLM 生成结构化内容）
  -> saveCustomScene（场景、词/词组/句子、Prompt）
  -> CustomSceneFlowService.start / next
  -> CustomSessionService.startSession(StartCustomSessionCommand)
  -> SessionLifecycleManager + RealtimeSessionCoordinator.connect
  -> ScenarioDialogueStateMachine.start / advance(sessionId, turnNo, transcript)
  -> CustomEvaluationService.evaluateTurn(DialogueTurnEvaluationCommand)
  -> CustomSessionService.endSession(EndCustomSessionCommand)
  -> CustomEvaluationService.generateReport(sceneId)
```

### IELTS：Scene → Part Flow → Session → 异步评价

```text
IeltsSceneService.generate(IeltsGenerationRequest)
  -> IeltsPracticeRecord（ieltsId、模式、题目、Part 内容）
  -> IeltsSceneFlowService.start(ieltsId)
  -> IeltsSessionService.startSession(StartIeltsSessionCommand)
  -> IeltsSceneFlowService.startSessionState（题目状态 / Part2 状态）
  -> RealtimeSessionCoordinator.connectIelts(...)
  -> IeltsEvaluationService.evaluateTurn(DialogueTurnEvaluationCommand)
  -> IeltsSceneFlowService.advanceDialogueState / advancePart2State
  -> IeltsSessionService.endSession(sessionId)
  -> 客户端调用 IeltsEvaluationCoordinator.submit(ieltsId, sessionId)
  -> GET evaluation 轮询 IeltsEvaluationTaskResponse
```

Mock Test 的 Final 评价以 `ieltsId` 汇总最多三个 Part；专项练习以该 Part 会话的 `sessionId` 为评价身份。

### 模拟面试：材料 Scene → Session → Topic State → Report Task

```text
InterviewSceneService.prepareMaterials(InterviewMaterialPreparationInput)
  -> 确认 InterviewMaterial
  -> InterviewSceneService.generate(InterviewSceneRequest)
  -> InterviewContext + scenePrompt + interview_scene
  -> InterviewSessionService.startSession(sceneId, StartCustomSceneDialogueRequest)
  -> SessionLifecycleManager + RealtimeSessionCoordinator.connect
  -> InterviewSessionService.submitTurn(sceneId, sessionId, turnNo, transcript, audio)
  -> AiProviderRegistry.executeLlmTaskRouted（主题识别）
  -> InterviewTopicStateMachine.advance(sessionId, turnNo, event)
  -> shouldEnd 后 InterviewSessionService.endInterview(sceneId, sessionId)
  -> interview_report(sessionId) PROCESSING
  -> InterviewReportCoordinator：逐段发音/流利评分 + 一次 LLM 五维报告
  -> GET report / POST report/retry
```

### 复用结论

四类业务共享 `SessionLifecycleManager、RealtimeSessionCoordinator、SessionMessageDispatcher、AiProviderRegistry` 和底层评价持久化约束；业务差异留在各自 Scene Service、状态机、Evaluation/Report Coordinator 中。

---

## 第 11 页｜架构设计结果

### 统一

统一的是稳定的运行事实和基础设施边界：`sessionId` 会话、实时连接编排、最终转写、评价记录、AI 能力路由。

### 可复用

自由聊天、自定义场景、IELTS、面试共用会话生命周期和 Provider 路由；新增业务只需增加场景准备、场景状态机或专用评价策略。

### 低耦合

业务服务依赖能力和 DTO，不直接依赖 Qwen、讯飞、七牛、阿里云等 SDK；模型路由、故障转移、调用计量在 Registry 和 Provider 层完成。

### 可验证

每个关键结果都有可追踪业务身份：

```text
sceneId -> sessionId -> messageNo / turnNo -> evaluation row
                         |
                         +-> providerSessionId / providerTraceId
                         +-> logicalRequestId -> AI attempt ledger
```

### 答辩结论

UniSpeaking 不是把四个场景强行塞进同一个大接口，而是把真正稳定的共性抽出来，把内容、流程、评价和失败策略留在业务边界内。这样既能复用会话与 AI 基础设施，也能让 Custom、IELTS 和 Interview 的差异被明确验证、独立演进。

---

## 附：原稿必须修改的地方

1. 删除不存在的 `Scene`、`LearningFlow`、`ConversationSession`、`Evaluation` 五方法统一接口；改为真实的具体 Service、Coordinator 和 StateMachine。
2. 删除 `flowId` 设计；当前 Flow 以 `sceneId` 为场景级键，对话子状态以 `sessionId` 为键。
3. 删除 `WORD → PHRASE → SENTENCE → DIALOGUE` 作为所有业务的通用流程；它只属于 Custom，IELTS 和 Interview 有自己的状态机，FreeChat 没有场景级 Flow。
4. 把 `Evaluation` 的四个泛化方法改成真实的 `DialogueTurnEvaluationCommand`、`SentenceEvaluationResponse`、Custom/IELTS 专用结果，以及 Interview 报告任务。
5. 把 `AiProvider` 页改成基础能力接口 + 能力子类 + `AiProviderRegistry` 路由；不要虚构统一的 `LlmResult`、`RealtimeSession` 等返回类型。
6. 把“所有关键操作携带 requestId/sessionId/evaluationId”改成第 9 页的业务事实键表；明确哪些操作幂等、哪些操作是重复练习或新建任务。
7. 把“WebRTC 失败后业务 Session 保留并恢复”改成当前真实语义：本地会话先建，初始 SDP 失败则置 `FAILED` 并清理；暂未实现自动恢复。
8. 把“评分全部异步”改成分层描述：单轮并行、IELTS/Interview/场景生成异步、Custom 最终报告同步。
9. 增加 Interview，并明确它不实现 `SceneFlowService`，而是使用 `InterviewTopicStateMachine` 和 `InterviewReportCoordinator`。
10. 增加“内存状态与持久化事实”的边界，避免将 JVM Map 描述成可跨重启、可跨实例恢复的流程引擎。
