# UniSpeaking 后端当前架构

本文档描述当前 `backend/unispeaking-server` 按 `UniSpeaking架构设计（完整版）.md` 和自由聊天伪代码整理后的代码结构。

## 顶层分层

```text
com.unispeaking
├── controller        # HTTP 入口
├── websocket         # 会话消息 WebSocket 入口
├── service           # 业务服务，接口/抽象类与 impl 分离
├── domain            # 领域对象，已拆分 DTO / VO / PO
├── provider          # 模型和外部能力 Provider 抽象
├── repository        # Store / Repository 抽象
├── infrastructure    # 第三方调用、配置、内存实现
├── mapper            # HTTP DTO 到领域请求的转换
├── component         # 通用组件
├── common            # 通用日志等基础能力
└── exception         # 业务异常和全局异常处理
```

## Domain 分层

```text
domain
├── dto
│   ├── ai            # AiProvider 请求/响应
│   ├── command       # 业务命令，例如 StartCommand
│   ├── evaluation    # EvaluationService 请求/响应
│   ├── request       # HTTP 入参
│   ├── response      # HTTP 出参
│   ├── result        # Service 结果对象
│   ├── scene         # SceneService / SceneFlowService 请求/响应
│   └── session       # SessionService 请求/响应
├── po
│   ├── conversation  # ConversationMessage
│   ├── profile       # UserProfile
│   └── session       # AbstractSceneSession / FreeChatSceneSession / CustomSceneSession
└── vo
    ├── ai            # AiCallContext
    ├── conversation  # SpeakerType
    ├── evaluation    # AudioInput / FiveDimensionScore
    ├── prompt        # Prompt 构建上下文和值对象
    ├── realtime      # ProviderType / 凭证 / 连接结果
    ├── scene         # SceneType / SceneConfig / SceneFlowStage
    └── session       # SessionStatus
```

## 当前引入的 Service

### 场景会话统一启动链路

```text
FreeChatSessionController
└── 固定 FREE_CHAT
    └── SceneSessionCoordinator

CustomSceneController
├── 固定 CUSTOM_SCENE
├── SceneService.generateScene(...)
├── SceneFlowService.createFlow/advanceStage/getFlow/completeFlow(...)
└── SceneSessionCoordinator
    ├── SceneService.generateScene(...)
    ├── SceneFlowService.createFlow(...)
    └── SessionServiceSelector.startSession(...)
        ├── FreeChatSessionService
        └── CustomSceneSessionService
            ├── SessionStateStore
            ├── FreeChatConversationService
            └── SessionMemoryService
```

Controller 不承载业务逻辑，只做入参接收、薄转换和返回响应。当前所有场景启动统一由 `SceneSessionCoordinator` 按 `UniSpeaking架构设计（完整版）.md` 的 Service 边界组合调用。`SceneService` 只负责 `generateScene(...)`；`SceneFlowService` 只负责 Flow 创建、推进、查询、完成；`SessionService` 负责开始会话、追加完整消息、结束会话。

`SceneService.generateScene(...)` 内部完成场景生成前的业务准备：解析/校验用户身份，校验场景配置权限，读取用户 Profile，把用户偏好和场景输入注入 Prompt，最后返回完整 `scenePrompt`。Session 阶段只接收这个完整 prompt，不再拼接用户偏好或场景提示词。

```text
FreeChatSessionController
└── FREE_CHAT -> 生成 FreeChat Prompt，Flow 直接进入 DIALOGUE，不评分

CustomSceneController
└── CUSTOM_SCENE -> 生成学习内容和 CustomScene Prompt，Flow 从 WORD_LEARNING 开始，后续评分
```

### 会话暂存链路

```text
FreeChatSessionService extends SessionService
CustomSceneSessionService extends SessionService
    ├── AuthService
    ├── UsageQuotaService
    ├── SessionStateStore
    ├── RealtimeConnectionService
    │   ├── RealtimeCredentialService
    │   └── AiProviderRegistry -> QwenRealtimeProvider
    ├── FreeChatConversationService
    └── SessionMemoryService
```

`SessionService` 当前只保留 `UniSpeaking架构设计（完整版）.md` 中定义的三个业务方法：

```text
SessionService
├── startSession(StartSessionRequest) -> StartSessionResponse
├── addMessage(AddSessionMessageRequest) -> void
└── endSession(EndSessionRequest) -> EndSessionResponse
```

`FreeChatSessionService` 和 `CustomSceneSessionService` 负责各自场景的会话对象创建、会话暂存、完整消息保存和会话结束后的记忆更新。它们只保存 `SceneService` 已产出的完整 prompt。`startSession` 还负责把 `offerSdp/model/voice` 交给 `RealtimeConnectionService`，取得 Answer SDP 后返回前端。暂停、恢复、打断、DataChannel 绑定、绑定超时等过程事件不属于 SessionService 的公开方法。

### 会话消息 WebSocket

```text
SessionMessageWebSocketHandler
└── /ws/session-messages
    ├── session.message -> SessionService.addMessage(...)
    └── session.end     -> SessionService.endSession(...)
```

这个 WebSocket 不是过程事件总线，只承载业务会话消息。用户语音音频、用户完整文本、AI 完整文本都通过 `session.message` 发送给后端。

### Realtime 连接链路

```text
RealtimeConnectionService
├── RealtimeCredentialService
└── AiProviderRegistry
    └── RealtimeProvider
        └── QwenRealtimeProvider
```

当前可跑通的 Qwen Realtime 链路仍然使用 `RealtimeConnectionService`：先申请短期 Bearer 凭证，再使用短期凭证和 Offer SDP 与阿里云交换 Answer SDP。

### 架构预留服务

```text
SceneService
└── generateScene(SceneGenerationRequest)
    ├── 权限/身份校验
    ├── 用户 Profile 和偏好注入
    ├── 场景输入注入
    └── 产出完整 scenePrompt

SceneFlowService
├── createFlow(CreateSceneFlowRequest)
├── advanceStage(AdvanceSceneStageRequest)
├── getFlow(flowId)
└── completeFlow(CompleteSceneFlowRequest)

EvaluationService
├── evaluateSentence(SentenceEvaluationRequest)
├── evaluateDialogueTurn(DialogueTurnEvaluationRequest)
└── generateConversationReport(ConversationReportRequest)

AiProvider
└── AbstractAiProvider
    ├── RealtimeProvider
    │   └── QwenRealtimeProvider
    ├── LlmProvider
    │   ├── QwenLlmProvider
    │   └── DeepSeekLlmProvider
    ├── ScoringProvider
    │   └── IflytekScoringProvider
    ├── TtsProvider
    │   ├── AliyunTtsProvider
    │   └── MiniMaxTtsProvider
    └── TranscriptionProvider

AiProviderRegistry
├── getRealtimeProvider(modelId)
├── getLlmProvider(modelId)
├── getScoringProvider(modelId)
├── getTtsProvider(modelId)
├── getTranscriptionProvider(modelId)
└── 根据能力和 modelId 选择具体 Provider
```

Registry 当前登记 `qwen3.5-omni-flash-realtime`（Realtime 默认）、
`qwen3.5-omni-plus-realtime`、`qwen3.5-plus`（LLM 默认）、
`deepseek-chat`、`iflytek-pronunciation-evaluation`（评分默认）、
`aliyun-tts`（TTS 默认）和 `minimax-tts`。目前只有 Qwen Realtime SDP
交换接入真实厂商 API；其余 Provider 是可路由骨架，在厂商凭证和协议接入前返回明确的能力未配置错误。

## 当前可跑通能力

- 场景会话统一启动：HTTP `POST /api/scene-sessions`
- 自由会话：`SceneFlowStage=DIALOGUE`，`scoringEnabled=false`
- 自定义场景：生成 `wordList`、`phraseList`、`sentenceList` 和完整 `scenePrompt`，`SceneFlowStage=WORD_LEARNING`，`scoringEnabled=true`
- 雅思/面试：复用自定义场景会话骨架，Flow 从 `WORD_LEARNING` 开始，后续评分细节待接入
- 追加完整消息：WebSocket `/ws/session-messages`，`type=session.message`
- 结束会话：WebSocket `/ws/session-messages`，`type=session.end`；HTTP `POST /api/scene-sessions/{sessionId}/end` 保留
- 用户/AI 完整消息保存到自由会话 Conversation Store
- 会话结束后调用 SessionMemoryService 更新会话记忆
