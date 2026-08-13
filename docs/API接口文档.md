# UniSpeaking 接口文档

本文档以当前后端代码为准，覆盖 REST API、WebSocket 协议、核心数据结构和公共 Service 接口。删除或未实现的业务不在文档中。

## 1. 通用约定

### 1.1 基础地址与鉴权

- REST 基础路径：`/api`
- WebSocket 路径：`/ws/session-messages`
- 除 `POST /api/auth/register`、`POST /api/auth/login` 和预检 `OPTIONS` 外，全部接口都需要 JWT。
- REST 使用请求头：`Authorization: Bearer <accessToken>`。
- WebSocket 可使用相同请求头；浏览器无法设置握手头时可使用 `?access_token=<accessToken>`。
- 服务端从 JWT 识别当前用户，不接受客户端用 `userId` 越权指定其他用户。

### 1.2 JSON 响应包络

除音频二进制接口外，响应结构统一如下：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {}
}
```

失败响应：

```json
{
  "success": false,
  "code": "VALIDATION_ERROR",
  "message": "字段错误说明",
  "data": null
}
```

### 1.3 常用状态码

| HTTP 状态 | 典型错误码 | 含义 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR`、普通业务错误 | 参数或业务状态不合法 |
| 401 | `AUTHENTICATION_REQUIRED`、`INVALID_ACCESS_TOKEN`、`INVALID_CREDENTIALS` | 未登录、令牌或凭据无效 |
| 403 | `SESSION_ACCESS_DENIED`、`ADMIN_ACCESS_DENIED` | 无权访问资源 |
| 404 | `ACHIEVEMENT_UNLOCK_NOT_FOUND`、`IELTS_RECORDING_NOT_FOUND` | 资源不存在或不属于当前用户 |
| 409 | `USERNAME_ALREADY_EXISTS`、`PASSWORD_UPDATE_CONFLICT`、`PROFILE_UPDATE_CONFLICT` | 数据冲突 |
| 413 | `PAYLOAD_TOO_LARGE` | 上传内容过大 |
| 415 | `MEDIA_TYPE_UNSUPPORTED` | Content-Type 不支持 |
| 422 | `AVATAR_DIMENSION_INVALID`、`AVATAR_CONTENT_INVALID` | 图片内容不可处理 |
| 500 | `ACHIEVEMENT_PERSISTENCE_FAILED`、`PROFILE_GOALS_PERSISTENCE_FAILED`、`IELTS_RECORDING_PERSISTENCE_FAILED` | 服务端持久化失败 |
| 502 | `OBJECT_STORAGE_FAILED`、`OCR_PROCESS_FAILED` | 上游服务失败 |
| 503 | `OBJECT_STORAGE_UNAVAILABLE`、`OCR_UNAVAILABLE` | 上游服务不可用 |
| 504 | `OCR_TIMEOUT` | 上游处理超时 |

### 1.4 常用枚举

| 类型 | 可选值 |
| --- | --- |
| `SceneType` | `FREE_CHAT`、`CUSTOM_SCENE`、`IELTS_SCENE` |
| `ProviderType` | `QWEN`、`OPENAI`、`DEEPSEEK`、`IFLYTEK`、`ALIYUN`、`MINIMAX`、`DOUBAO` |
| `SessionStatus` | `CREATED`、`CONNECTING`、`WAITING_CLIENT`、`ACTIVE`、`PAUSED`、`INTERRUPTED`、`COMPLETED`、`FAILED` |
| `IeltsMode` | `PART_PRACTICE`、`MOCK_TEST` |
| `IeltsPart` | `PART_1`、`PART_2`、`PART_3` |
| `IeltsPart2Event` | `PREPARATION_COMPLETE`、`ANSWER_COMPLETE`、`LONG_TURN_TIME_LIMIT` |
| `SceneFlowStage` | `WORD_LEARNING`、`PHRASE_LEARNING`、`SENTENCE_LEARNING`、`DIALOGUE`、`IELTS_PART_1`、`IELTS_PART_2`、`IELTS_PART_3`、`COMPLETED` |
| `PreferredVoice` | `Katerina`、`Aiden`、`Raymond`、`Tina`、`Harvey`、`Dolce` |
| `PreferredAiSpeechSpeed` | `SLOWER`、`MODERATE`、`NATURAL`、`FASTER` |
| `CefrLevel` | `A`、`B`、`C`、`D` |

## 2. 认证接口

基础路径：`/api/auth`

| 方法 | 路径 | 鉴权 | 请求 | 响应 `data` |
| --- | --- | --- | --- | --- |
| POST | `/register` | 否 | `RegisterRequest` | `AuthResponse` |
| POST | `/login` | 否 | `LoginRequest` | `AuthResponse` |
| GET | `/me` | 是 | 无 | `UserAccountResponse` |
| PUT | `/password` | 是 | `ChangePasswordRequest` | `ChangePasswordResponse` |

### 2.1 请求结构

`RegisterRequest`

| 字段 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| `username` | string | 是 | 邮箱，最长 254 |
| `password` | string | 是 | 6～72 位 |
| `nickname` | string | 否 | 最长 32 |

`LoginRequest`：`username` 为邮箱，`password` 为 6～72 位字符串。

`ChangePasswordRequest`：`currentPassword`、`newPassword` 均为 6～72 位字符串。

### 2.2 响应结构

`AuthResponse`：`tokenType`、`accessToken`、`expiresAt`、`user`。

`UserAccountResponse`：`id`、`username`、`nickname`、`role`、`status`、`lastLoginAt`、`createdAt`。

`ChangePasswordResponse`：`reauthenticationRequired`，当前密码更新成功后用于指示客户端重新登录。

## 3. 用户资料与偏好

### 3.1 Profile

基础路径：`/api/profile`

| 方法 | 路径 | 请求 | 响应 `data` |
| --- | --- | --- | --- |
| GET | `/insights` | 无 | `ProfileInsightsResponse` |
| PUT | `/insights/goals` | `UpdateWeeklyLearningGoalsRequest` | 更新后的 `ProfileInsightsResponse` |
| GET | `/overview?month=YYYY-MM` | `month` 可选 | `ProfileOverviewResponse` |
| PATCH | `/` | `UpdateProfileRequest` | `UpdateProfileResponse` |
| POST | `/avatar` | multipart：`avatar` | `AvatarResponse` |

`UpdateWeeklyLearningGoalsRequest`

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `durationTargetMinutes` | integer | 1～1260 |
| `trainingCountTarget` | integer | 1～70 |

`UpdateProfileRequest`：`nickname` 必填，最长 32。

`ProfileOverviewResponse`

- `account`：`userId`、`email`、`nickname`、`displayName`、`avatarUrl`、`avatarUrlExpiresAt`。
- `statistics`：`weeklyPracticeSeconds`、`trainingRecordCount`、`consecutiveLearningDays`、`lastSevenDays[{date, practiceSeconds}]`。
- `calendar`：`month`、`checkedDates`、`checkedInToday`。

`ProfileInsightsResponse`

- `weeklyGoals`：周起止时间、时长/次数目标、已完成值、剩余值、进度及是否达标。
- `trainingTypeDistribution[]`：`type`、`durationSeconds`、`percentage`。
- `abilityTrends[]`：`sessionId`、`completedAt`、`trainingType`、`scores`。
- `scores`：`accuracy`、`fluency`、`grammar`、`vocabulary`、`naturalness`。
- `weaknessAnalysis`：`sampleCount`、`minimumSampleCount`、`reliable`。
- `weaknesses[]`：`dimension`、`rank`、`averageScore`、`recentChange`、`basis`。
- `recommendations[]`：`dimension`、`trainingType`、`reason`。

### 3.2 User Preference

基础路径：`/api/user-preferences`

| 方法 | 路径 | 请求 | 响应 `data` |
| --- | --- | --- | --- |
| GET | `/` | 无 | `UserPreferenceResponse` |
| PUT | `/` | `UpdateUserPreferenceRequest` | 更新后的 `UserPreferenceResponse` |

`UpdateUserPreferenceRequest`：`preferredVoice`、`preferredAiSpeechSpeed`、`cefrLevel`、`memoryText`（最长 4000）均可选。

`UserPreferenceResponse`：`userId`、`preferredVoice`、`preferredAiSpeechSpeed`、`cefrLevel`、`memoryText`。

## 4. 成就接口

| 方法 | 路径 | 请求 | 响应 `data` |
| --- | --- | --- | --- |
| GET | `/api/achievements` | 无 | `AchievementOverviewResponse` |
| POST | `/api/achievement-unlocks` | 无 | `AchievementSyncResponse` |
| PATCH | `/api/achievement-unlocks/{achievementId}` | `AchievementAcknowledgeRequest` | `AchievementAcknowledgeResponse` |

`AchievementAcknowledgeRequest`：`acknowledged`（boolean）。

`AchievementOverviewResponse.series[]` 包含 `seriesId`、`category`、`title`、`unit`、`currentValue`、当前/下一等级信息、`completed` 和 `milestones`。每个里程碑包含 `achievementId`、`level`、`title`、`description`、`threshold`、`unlocked`、`unlockedAt`。

`AchievementSyncResponse` 包含 `initialized`、`overview`、`newlyUnlocked[]`、`pendingNotifications[]`。

## 5. 自由聊天

基础路径：`/api/scene-sessions`

| 方法 | 路径 | 请求 | 响应 `data` |
| --- | --- | --- | --- |
| POST | `/` | `StartFreeChatRequest` | `StartSceneSessionResponse` |
| POST | `/{sessionId}/end` | 无 | `null` |
| POST | `/{sessionId}/translations` | `TranslateTextRequest` | `TranslateTextResponse` |

`StartFreeChatRequest`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `offerSdp` | string | 是 | 客户端 WebRTC SDP |
| `provider` | `ProviderType` | 否 | AI 供应商提示；省略时由后端路由选择 |
| `model` | string | 否 | 模型标识；省略时默认七牛 Plus，失败回退百炼 Flash |
| `voice` | string | 否 | 音色标识；当前七牛 RTC 阶段统一使用 Tina |
| `translationEnabled` | boolean | 否 | 是否启用翻译 |

`TranslateTextRequest`：`text` 必填，最长 4000。响应字段为 `sourceText`、`translatedText`、`targetLanguage`。

Realtime 默认路由为七牛 RTI `qwen3.5-omni-plus-realtime`，可回退错误切换至百炼
`qwen3.5-omni-flash-realtime`。七牛长期 API Key 与 Session 短期媒体 token 均不会返回
客户端；响应中的 `providerSessionId` 是可持久化的七牛 RTI Session ID。结束接口会同步
完成本地业务会话，并尽最大努力调用供应商 Stop 释放并发额度。

## 6. 自定义场景

基础路径：`/api/custom-scenes`

### 6.1 场景和流程

| 方法 | 路径 | 请求 | 响应 `data` |
| --- | --- | --- | --- |
| POST | `/generate` | `CustomSceneRequest` | `CustomSceneGenerationResponse` |
| POST | `/flows` | `{ "sceneId": string }` | `SceneFlowResponse` |
| POST | `/flows/advance` | `AdvanceSceneStageRequest` | `SceneFlowResponse` |
| POST | `/flows/complete` | `{ "sceneId": string, "completed": boolean }` | `null` |
| GET | `/flows/{sceneId}/content?stage=...` | `stage` 可选；当前内容以服务端实际阶段为准 | `LearningContentItem[]` |

`CustomSceneRequest`

| 字段 | 类型 | 必填 | 约束/说明 |
| --- | --- | --- | --- |
| `userId` | string | 否 | 不作为授权依据 |
| `userPreference` | string | 否 | 最长 1000 |
| `sceneInput` | string | 是 | 也兼容别名 `prompt`，最长 500 |
| `offerSdp` | string | 否 | 可选实时连接信息 |
| `provider` | `ProviderType` | 否 | AI 供应商提示；省略时由后端路由选择 |
| `model` | string | 否 | 模型；省略时使用默认 Realtime 路由 |
| `voice` | string | 否 | 音色；当前七牛 RTC 阶段统一使用 Tina |
| `translationEnabled` | boolean | 否 | 是否启用翻译 |

`CustomSceneGenerationResponse`：`sceneId`、`title`、`background`、`aiRole`、`userRole`、`learningGoal`、`estimatedMinutes`、`wordList`、`phraseList`、`sentenceList`、`scenePrompt`。

`LearningContentItem`：`contentId`、`englishText`、`chineseText`、`phonetic`。

`SceneFlowResponse`：`sceneId`、`stage`、`completed`。

### 6.2 对话会话

| 方法 | 路径 | 请求 | 响应 `data` |
| --- | --- | --- | --- |
| POST | `/{sceneId}/sessions` | `StartCustomSceneDialogueRequest` | `StartSceneSessionResponse` |
| POST | `/{sceneId}/sessions/{sessionId}/turns/{turnNo}/evaluation` | multipart：`transcript`，可选 `audio` | `DialogueTurnEvaluationResult` |
| POST | `/{sceneId}/sessions/{sessionId}/turns/{turnNo}/state` | `{ "transcript": string }` | `ScenarioDialogueStateResponse` |
| GET | `/{sceneId}/sessions/{sessionId}/state` | 无 | `ScenarioDialogueStateResponse` |
| POST | `/{sceneId}/sessions/{sessionId}/complete` | 可选 `{ "stopTime": string }` | `CompleteCustomSceneDialogueResponse` |
| GET | `/{sceneId}/sessions/{sessionId}/evaluation` | 无 | `DialogueReportResult` |

`StartCustomSceneDialogueRequest` 与自由聊天启动字段一致，其中 `offerSdp` 必填。

`ScenarioDialogueStateResponse`：`sceneId`、`sessionId`、`stage`、`effectiveUserTurns`、`maximumUserTurns`、`outcomes[]`、`completed`、`completionReason`、`controlInstruction`、`warning`。

`DialogueTurnEvaluationResult`：`turnNo`、`transcript`、`overallScore`、`rhythmScore`、`toneScore`、`integrityScore`、`pronunciationScore`、`fluencyScore`、`feedbackSummary`、`suggestedExpression`、`words[]`。

`DialogueReportResult`：`accuracyScore`、`fluencyScore`、`grammarScore`、`vocabularyScore`、`naturalnessScore`、`finalScore`、`summary`、`strengths[]`、`improvements[]`。

### 6.3 学习资产和辅助接口

| 方法 | 路径 | 请求 | 响应 |
| --- | --- | --- | --- |
| GET | `/assets` | 无 | `ApiResponse<LearningAssetSummary[]>` |
| GET | `/{sceneId}/assets` | 无 | `ApiResponse<LearningAssetDetail>` |
| POST | `/{sceneId}/sentences/{sentenceId}/evaluation` | multipart：必填 `audio` | `ApiResponse<SentenceEvaluationResponse>` |
| POST | `/{sceneId}/speech` | `TtsRequest` | 原始 `audio/wav` |
| POST | `/{sceneId}/translations` | `TranslateTextRequest` | `ApiResponse<TranslateTextResponse>` |

`LearningAssetSummary`：`sceneId`、`title`、`background`、单词/短语/句子数量、最近会话和分数、最近练习时间、练习次数、创建时间。

`LearningAssetDetail`：场景基本信息、三类学习内容、`latestSessionId`、`dialogueEvaluation`、`latestReport`、`reportHistory`。

`TtsRequest`：`text` 必填且最长 500，`model` 可选。

`SentenceEvaluationResponse`：`overallScore`、`passed`、`words[]`；单词项包含 `word`、`wordScore` 和音素明细。

## 7. IELTS 场景

基础路径：`/api/ielts`

### 7.1 设置、题库和生成

| 方法 | 路径 | 请求 | 响应 `data` |
| --- | --- | --- | --- |
| GET | `/settings` | 无 | `IeltsSettingsResponse` |
| PUT | `/settings` | `UpdateIeltsSettingsRequest` | 更新后的 `IeltsSettingsResponse` |
| GET | `/topics` | 查询参数见下方 | `IeltsTopicSearchResponse` |
| GET | `/training` | `part` 必填，`topicId` 可选 | `IeltsTrainingResponse` |
| POST | `/generate` | `IeltsGenerationRequest` | `IeltsGenerationResponse` |
| POST | `/flows` | `{ "sceneId": string }` | `SceneFlowResponse` |

`UpdateIeltsSettingsRequest`：`targetScore`（0～9）、`examinerId`。

`IeltsSettingsResponse`：`targetScore`、`todayCompletedCount`、`examinerId`、`preferredVoice`、`latestEstimatedScore`、`currentStreakDays`、`totalCheckInDays`、`lastCheckInDate`。

`GET /topics` 查询参数：

| 参数 | 类型 | 必填 | 默认/约束 |
| --- | --- | --- | --- |
| `part` | `IeltsPart` | 是 | — |
| `category` | string | 否 | 分类代码 |
| `keyword` | string | 否 | 题目关键词 |
| `page` | integer | 否 | 默认 1，最小 1 |
| `pageSize` | integer | 否 | 默认 10，1～50 |

`IeltsTopicSearchResponse`：`categories[]`、`topics[]`、`page`、`pageSize`、`total`、`totalPages`。

`topics[]`：`id`、`title`、`topicType`、`category`、`categoryLabel`、`source`、`questionCount`、各类练习次数、`latestPracticeType`、`latestPerformanceScore`、`latestPerformanceSummary`、`lastPracticedAt`。

`IeltsTrainingResponse`：`topicId`、`title`、`part`、`questions[]`。问题项包含 `id`、`part`、`sortNo`、`questionText`、`cuePoints[]`、`recommendedExpressions[]`。

`IeltsGenerationRequest`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `mode` | `IeltsMode` | 是 | 专项训练或完整模考 |
| `part` | `IeltsPart` | 专项训练时是 | 模考可为空 |
| `topicId` | string | 否 | 不传时随机选题 |

`IeltsGenerationResponse`：`ieltsId`、`mode`、`selectedPart`、`selectedTopicId`、`title`、`content`、`voiceId`、`scenePrompt`。

`content` 固定为：

```json
{
  "part1": [
    {
      "question": "...",
      "cue_points": [],
      "recommended_expressions": [
        {
          "type": "...",
          "expression": "...",
          "translation": "...",
          "usageNote": "..."
        }
      ]
    }
  ],
  "part2": [],
  "part3": []
}
```

### 7.2 IELTS 会话与状态机

| 方法 | 路径 | 请求 | 响应 `data` |
| --- | --- | --- | --- |
| POST | `/{ieltsId}/sessions` | `StartIeltsDialogueRequest` | `StartIeltsSessionResponse` |
| POST | `/{ieltsId}/sessions/{sessionId}/turns/{turnNo}/evaluation` | multipart：`transcript`，可选 `audio` | `DialogueTurnEvaluationResult` |
| POST | `/{ieltsId}/sessions/{sessionId}/turns/{turnNo}/state?timedOut=false` | 无 JSON body | `IeltsDialogueStateResponse` |
| GET | `/{ieltsId}/sessions/{sessionId}/state` | 无 | `IeltsDialogueStateResponse` |
| POST | `/{ieltsId}/sessions/{sessionId}/part2/state` | `IeltsPart2StateRequest` | `IeltsPart2StateResponse` |
| GET | `/{ieltsId}/sessions/{sessionId}/part2/state` | 无 | `IeltsPart2StateResponse` |

`StartIeltsDialogueRequest`：`offerSdp` 必填、`provider`、`model`、`voiceId` 必填、`translationEnabled`。

`StartIeltsSessionResponse`：`sceneId`、`sceneName`、`sceneType`、`content`、`currentStage`、`scoringEnabled`、`sessionId`、`providerSessionId`、`answerSdp`、`credentialExpiresAt`、`voiceId`、`status`、`startTime`、`systemPrompt`。

`IeltsDialogueStateResponse`：`sceneId`、`sessionId`、`part`、`openingCompleted`、`answeredQuestions`、`totalQuestions`、`completed`、`controlInstruction`。

`IeltsPart2StateRequest`：`event`，取值为 `PREPARATION_COMPLETE`、`ANSWER_COMPLETE` 或 `LONG_TURN_TIME_LIMIT`。

`IeltsPart2StateResponse`：`sceneId`、`sessionId`、`phase`、`completed`、`controlInstruction`。

以上状态接口由 `IeltsSceneFlowService` 处理。`IeltsSessionService` 只负责启动会话、
保存消息和结束会话，不负责题目推进或 Part 2 阶段转换。

### 7.3 IELTS 评分、历史和录音

| 方法 | 路径 | 请求 | 响应 |
| --- | --- | --- | --- |
| POST | `/{ieltsId}/sessions/{sessionId}/evaluation` | 无 | `ApiResponse<IeltsEvaluationResult>` |
| GET | `/evaluations` | 无 | `ApiResponse<IeltsEvaluationHistoryItem[]>` |
| GET | `/recordings/{sessionId}/{fileName}` | 无 | 原始 `audio/wav` |

录音接口已经归入 `IELTSSceneController`，原 URL 保持不变。它只加载当前用户拥有的会话录音，响应使用 `Cache-Control: private, no-store`；不存在或无权访问时对外返回 `IELTS_RECORDING_NOT_FOUND`。

`IeltsEvaluationResult`

- `part`：专项训练的 Part；完整模考可为空。
- `assessmentType`：评分类型。
- `overallBandScore`：仅完整模考使用的综合分。
- 四项能力：`fluencyCoherenceScore`、`lexicalResourceScore`、`grammaticalRangeAccuracyScore`、`pronunciationScore`。
- `summary`、`strengths[]`、`improvements[]`、`recommendedExpressions[]`。
- `partEvaluations[]`：各 Part 的四项能力、总结、优势、改进、推荐表达和四项具体评分理由。
- 四项理由：`fluencyCoherenceReason`、`lexicalResourceReason`、`grammaticalRangeAccuracyReason`、`pronunciationReason`。

`IeltsEvaluationHistoryItem` 在上述评分字段之外还包含 `sessionId`、`ieltsId`、`mode`、`topicSelectionMethod`、`topicTitles`、`recordingUrls[]`、`startedAt`、`endedAt`，用于学习资产、趋势图和音频回放。

## 8. 会话通用响应

`StartSceneSessionResponse`

- 场景：`sceneId`、`sceneName`、`sceneType`。
- 学习内容：`wordList`、`phraseList`、`sentenceList`、`currentStage`、`scoringEnabled`。
- 会话：`sessionId`、`providerSessionId`、`answerSdp`、`credentialExpiresAt`、`voiceId`、`status`、`startTime`、`systemPrompt`。

时间字段均使用 ISO-8601 字符串；分数字段使用 JSON number，允许半分。

## 9. WebSocket 会话消息

连接：`ws(s)://<host>/ws/session-messages?access_token=<JWT>`。

客户端帧：

```json
{
  "type": "message",
  "sessionId": "...",
  "message": {
    "owner": 1,
    "content": "transcript",
    "audio": null
  },
  "stopTime": null
}
```

- 追加消息支持 `type`：`message`、`session.message`、`addMessage`。
- 结束会话支持 `type`：`end`、`session.end`、`endSession`，并通过 `stopTime` 传结束时间。
- `audio` 是 JSON 中的 Base64 字节数组；大音频应使用专用 multipart 接口。

服务端确认帧：

```json
{
  "type": "session.message.accepted",
  "sessionId": "...",
  "success": true,
  "code": "OK",
  "message": "success",
  "data": null
}
```

失败时 `type` 以 `.failed` 结尾，`code` 为 `SESSION_SOCKET_ERROR`。

## 10. 公共 Service 接口契约

这些是后端内部稳定接口，不是 HTTP 路由。具体场景实现可以添加自身业务方法，但不得修改公共接口来迁就单一场景。

### 10.1 场景准备职责与 SceneFlowService

```java
public interface SceneFlowService<S> {
    S start(String sceneId);
    S current(String sceneId);
    S next(String sceneId);
    boolean isCompleted(String sceneId);
}
```

`SceneService` 公共基类已删除：场景准备是**职责**（权限、次数、题目/内容、Prompt 和场景持久化），由各场景专用接口自身声明 `generate`（如 `CustomSceneService.generate`）。`SceneFlowService` 只负责阶段状态，不启动会话、不评分。

### 10.2 会话生命周期形状

```java
public interface CustomSessionService {
    StartSceneSessionResponse startSession(StartCustomSessionCommand command);
    void addMessage(String sessionId, Message message);
    CompleteCustomSceneDialogueResponse endSession(EndCustomSessionCommand command);
}
// FreeChatSessionService / IeltsSessionService 声明同形 startSession/addMessage/endSession
```

`SessionService` 公共基类已删除：接受 WS 实时帧的场景会话接口必须各自声明 `startSession/addMessage/endSession` 生命周期形状（`addMessage` 由 `SessionMessageDispatcher` 消费）。会话只管理已准备场景的会话生命周期和消息，不调用认证服务生成场景，也不重复场景模块的准备逻辑。会话详情和按场景查询是内部协作能力，由 `SessionLifecycleManager` 提供，不属于场景会话接口。

### 10.3 Evaluation

```java
public interface EvaluationService<R, D> {
    DialogueTurnEvaluationResult evaluateTurn(DialogueTurnEvaluationCommand command);
    R generateReport(String sceneId);
    D getEvaluation(String sceneId);
}
```

评分接口提供稳定的单轮、报告生成和结果查询契约。IELTS、自定义场景各自实现并可增加场景专属方法。

### 10.4 其他公共接口

- `AuthService`：注册、登录、当前用户、改密和用户身份校验。
- `AchievementService`：成就概览、同步和通知确认。
- `LearningAssetService`：资产列表、资产详情和会话报告查询。
- `ProfileAccountService`：昵称与头像。
- `ProfileOverviewService`：个人页汇总。
- `ProfileInsightsService`：目标、趋势、薄弱项和建议。
- `ProfileService`：用户长期偏好。

## 11. 调用顺序

自定义场景：

1. `POST /api/custom-scenes/generate`
2. `POST /api/custom-scenes/flows`
3. 按当前阶段读取内容并调用 `/flows/advance`
4. 到 `DIALOGUE` 后调用 `POST /{sceneId}/sessions`
5. 通过 WebSocket 保存消息，通过 turn 接口评分并推进状态
6. 调用 complete，再查询 evaluation 或 learning assets

IELTS 专项训练/模考：

1. 可先读取 `/settings`、`/topics`、`/training`
2. `POST /api/ielts/generate`
3. `POST /api/ielts/flows`
4. `POST /api/ielts/{ieltsId}/sessions`
5. 根据 Part 状态机提交轮次、音频和状态事件
6. 每阶段后台评分；最后调用 evaluation 获取汇总
7. `/evaluations` 用于学习资产列表、报告、趋势和录音链接

Interview（英文面试，第 4 场景，逐步实现中）：

1. `POST /api/interview-scenes/prepare-materials` — 解析 JD/简历 → 脱敏 → LLM-1 整理 → `{material}` 草稿
   - multipart：`resumeText`/`resumeFile`（PDF/DOCX 文本，`.doc` 拒绝）、`jobDescriptionText`/`jobDescriptionImage`（单张图片 OCR）；JD 文本与图片二选一。
   - 失败码：`DOCUMENT_FORMAT_UNSUPPORTED`→422（`.doc`/图片简历）、`OCR_UNAVAILABLE`→503、`OCR_TIMEOUT`→504、`OCR_PROCESS_FAILED/RESPONSE_INVALID`→502。
2. `POST /api/interview-scenes` — 生成面试场景（body：`{material, difficulty}` → `{sceneId, scenePrompt}`）
   - `material` 需含非空 `responsibilities`/`qualificationRequirements`；`difficulty` ∈ EASY/STANDARD/HARD。
   - 失败码：`INTERVIEW_MATERIAL_INVALID`→400、`INTERVIEW_REQUEST_INVALID`→400、`INTERVIEW_SCENE_ACCESS_DENIED`→403、`INTERVIEW_SCENE_NOT_FOUND`→404、`INTERVIEW_SCENE_PERSISTENCE_FAILED`→500、`INTERVIEW_CONTEXT_LLM_RESPONSE_INVALID`→400。
3. `POST /api/interview-scenes/{sceneId}/sessions` — 启动实时会话（body 复用 `StartCustomSceneDialogueRequest`：offerSdp/provider/model/voice/translationEnabled）
   - 首面/复练统一入口（body 无 material/difficulty，结构上禁改材料/难度）；复练计入门槛（当日 COMPLETED 5 次，独立计数）。
   - 失败码：`INTERVIEW_SCENE_NOT_FOUND`→404、`INTERVIEW_SCENE_ACCESS_DENIED`→403、`INTERVIEW_DAILY_LIMIT_REACHED`→429。
4. `POST /api/interview-scenes/{sceneId}/sessions/{sessionId}/turns/{turnNo}` — 逐轮提交（multipart：`transcript` 必填 + `audio` 可空）
   - 幂等粒度 `(sessionId, turnNo)`：重复请求返回已记录状态；同轮内容不一致 → 409 `INTERVIEW_TURN_CONTENT_MISMATCH`；WS 消息在途 → 409 `INTERVIEW_TURN_MESSAGE_PENDING`（可重试）；轮次空洞/非正 → 400 `INTERVIEW_TURN_OUT_OF_ORDER`；会话已结束 → 409 `INTERVIEW_SESSION_ENDED`。
   - 返回 `{state: {shouldEnd, completedTopicCount, coveredTopicCount, currentTopic, controlInstruction}, reportStatus}`；`shouldEnd=true` 时前端停录音关连接，`reportStatus=PROCESSING`；`controlInstruction` 为下一轮实时指令（开场/推进/收尾，结束时为空）。
5. `POST /api/interview-scenes/{sceneId}/sessions/{sessionId}/end` — 用户主动结束（幂等结束编排，自动/手动只允许一次 end + 一次报告任务）→ `{sessionId, reportStatus}`。
6. `GET /api/interview-scenes/{sceneId}/sessions/{sessionId}/report` — 轮询报告：`{sessionId, sceneId, status: PROCESSING/COMPLETED/FAILED, report, failureReason}`（PROCESSING 时 report 为空）。
7. `POST /api/interview-scenes/{sceneId}/sessions/{sessionId}/report/retry` — FAILED→PROCESSING 重试（CAS 幂等；瞬时失败自动重试 1 次后留手动）。
8. `POST /api/interview-scenes/{sceneId}/sessions/{sessionId}/ai-audio` — AI「实际播放的」音频上报（`RecordingStore` 落盘，TTL 清扫）。
9. `GET /api/interview-scenes/{sceneId}/sessions/{sessionId}/recording` — 总音频回放（`audio/wav`，`Cache-Control: private, no-store`，归属校验；V1 为按轮序拼接的用户录音段）。
10. `GET /api/interview-scenes/{sceneId}/sessions/{sessionId}/recordings/{fileName:.+}` — 分段录音读取（内部/调试，私有缓存）。
11. `DELETE /api/interview-scenes/{sceneId}` — 后端删除：软删 `interview_scene` + 物理清该 scene 音频；practice_session/session_message/interview_report 保留（审计 + 学习日历），下游访问经软删过滤 404/403。
12. 失败码补充：`INTERVIEW_REPORT_NOT_FOUND`→404、`INTERVIEW_RECORDING_NOT_FOUND`→404、`INTERVIEW_REPORT_PERSISTENCE_FAILED`→500、`INTERVIEW_AUDIO_INVALID`→400、`INTERVIEW_SESSION_ENDED`→409。
13. `GET /api/interview-scenes/assets` — 面试学习资产列表：`List<InterviewAssetItem>`（`sceneId/jobTitle/difficulty/latestSessionId/latestReportStatus/latestOverallScore/latestPracticedAt/practiceCount/createdAt`），复练入口。
14. `GET /api/interview-scenes/ocr/availability` — OCR 可用性探测：`{available: boolean}`（后端检查启用开关、Python runner 和预下载模型目录；Web 面试页启动时调用此接口，据此启用或禁用 JD 图片上传）。
