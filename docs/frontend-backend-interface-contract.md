# UniSpeaking 前后端接口文档

本文档用于前端和后端对齐接口。当前后端已实现自由会话的开始、WebSocket 追加完整消息和结束；登录注册、用户偏好、场景、评分、个人中心等接口属于建议新增契约。

## 1. 基础约定

### 1.1 Base URL

本地开发：

```text
HTTP: http://127.0.0.1:8080
WebSocket: ws://127.0.0.1:8080
```

Vite 前端开发代理：

```text
/api -> http://127.0.0.1:8080
/ws  -> ws://127.0.0.1:8080
```

### 1.2 统一 HTTP 响应

后端 HTTP 接口统一返回：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {}
}
```

失败示例：

```json
{
  "success": false,
  "code": "BUSINESS_ERROR",
  "message": "错误说明",
  "data": null
}
```

### 1.3 会话 ID

| 字段 | 来源 | 说明 |
| --- | --- | --- |
| `sessionId` | Java 后端生成 | 本地业务会话 ID，前后端业务状态以它为准 |

SessionService 不处理暂停、恢复、打断、DataChannel 绑定等过程事件，只记录业务会话的开始、完整消息和结束。完整消息通过 WebSocket 传输。

## 2. 登录注册 HTTP 接口

状态：建议新增。

### 2.1 注册

```text
POST /api/auth/register
```

请求：

```json
{
  "email": "name@example.com",
  "password": "password123"
}
```

响应：

```json
{
  "userId": "user_123",
  "email": "name@example.com",
  "emailVerified": false,
  "nextStep": "VERIFY_EMAIL"
}
```

### 2.2 登录

```text
POST /api/auth/login
```

请求：

```json
{
  "email": "name@example.com",
  "password": "password123"
}
```

响应：

```json
{
  "accessToken": "jwt_or_session_token",
  "user": {
    "userId": "user_123",
    "email": "name@example.com",
    "displayName": "Yufan"
  }
}
```

### 2.3 当前用户

```text
GET /api/auth/me
Authorization: Bearer <accessToken>
```

响应：

```json
{
  "userId": "user_123",
  "email": "name@example.com",
  "displayName": "Yufan",
  "emailVerified": true
}
```

## 3. Level 和老师选择 HTTP 接口

状态：建议新增。

### 3.1 保存新手引导偏好

```text
PUT /api/users/me/onboarding
Authorization: Bearer <accessToken>
```

请求：

```json
{
  "level": "starter",
  "teacherId": "james"
}
```

字段：

| 字段 | 可选值 |
| --- | --- |
| `level` | `starter`, `basic`, `independent`, `fluent` |
| `teacherId` | `clara`, `james`, `leo`, `david`, `emily`, `arthur` |

响应：

```json
{
  "userId": "user_123",
  "level": "starter",
  "teacherId": "james",
  "onboardingCompleted": true
}
```

### 3.2 获取用户偏好

```text
GET /api/users/me/profile
Authorization: Bearer <accessToken>
```

响应：

```json
{
  "userId": "user_123",
  "displayName": "Yufan",
  "level": "starter",
  "teacherId": "james",
  "voiceId": "Katerina",
  "conversationSpeed": "自然"
}
```

### 3.3 更新用户偏好

```text
PATCH /api/users/me/profile
Authorization: Bearer <accessToken>
```

请求：

```json
{
  "level": "basic",
  "teacherId": "clara",
  "conversationSpeed": "适中",
  "voiceId": "Katerina"
}
```

响应同 `GET /api/users/me/profile`。

## 4. 场景会话统一接口

状态：已实现统一启动、WebSocket 追加完整消息和结束。

### 4.1 启动场景会话

```text
POST /api/scene-sessions
```

请求：

```json
{
  "sceneType": "FREE_CHAT",
  "prompt": "自由对话。老师：James。语速：自然。水平：starter。",
  "userPreference": "I prefer gentle correction and short replies.",
  "offerSdp": "浏览器生成的 WebRTC Offer SDP",
  "provider": "QWEN",
  "model": "qwen3.5-omni-flash-realtime",
  "voice": "Katerina",
  "translationEnabled": true
}
```

`sceneType` 可选：

```text
FREE_CHAT, CUSTOM_SCENE, IELTS_SCENE, INTERVIEW_SCENE
```

响应：

```json
{
  "sceneId": "scene_801cca60-5b30-49ce-be9c-54a55655754a",
  "sceneName": "Free Chat",
  "sceneType": "FREE_CHAT",
  "wordList": [],
  "phraseList": [],
  "sentenceList": [],
  "flowId": "flow_9524a0d2-617d-42a4-b824-f7e86cc5158c",
  "currentStage": "DIALOGUE",
  "scoringEnabled": false,
  "sessionId": "scene_801cca60-5b30-49ce-be9c-54a55655754a",
  "localSessionId": "scene_801cca60-5b30-49ce-be9c-54a55655754a",
  "providerSessionId": null,
  "answerSdp": "Qwen 返回的 WebRTC Answer SDP",
  "credentialExpiresAt": "2026-07-21T08:20:07Z",
  "voiceId": "Katerina",
  "status": "WAITING_CLIENT",
  "startTime": "2026-07-21T08:15:07Z",
  "scenePrompt": "完整系统提示词",
  "systemPrompt": "完整系统提示词"
}
```

自由会话会直接进入 `DIALOGUE` 阶段且不评分；自定义场景会返回三组学习材料并从 `WORD_LEARNING` 开始，后续可接单词、词组、句子学习和评分。

`scenePrompt` 是后端在 `SceneService.generateScene(...)` 中完成权限校验、用户 Profile 注入、用户偏好注入和场景输入注入后的完整提示词，Session 阶段直接使用它。
`SessionService.startSession(...)` 使用 `offerSdp/model/voice` 调用
`RealtimeConnectionService`，内部申请短期凭证并交换 Answer SDP。`systemPrompt`
与 `scenePrompt` 内容相同，用于兼容实时前端已有字段。

### 4.2 追加完整消息

```text
WS /ws/session-messages
```

请求：

```json
{
  "type": "session.message",
  "sessionId": "scene_801cca60-5b30-49ce-be9c-54a55655754a",
  "message": {
    "owner": 1,
    "content": "Hello, I want to practice small talk.",
    "audio": null
  }
}
```

字段：

| 字段 | 说明 |
| --- | --- |
| `owner` | `1` 表示用户，`0` 表示模型 |
| `content` | 用户或模型的一条完整文本，不传流式 delta |
| `audio` | 用户说话音频，使用 base64 字符串；模型消息通常为空 |

响应：

```json
{
  "type": "session.message.accepted",
  "sessionId": "scene_801cca60-5b30-49ce-be9c-54a55655754a",
  "success": true,
  "code": "OK",
  "message": "success",
  "data": null
}
```

### 4.3 结束自由会话

```text
WS /ws/session-messages
```

请求：

```json
{
  "type": "session.end",
  "sessionId": "scene_801cca60-5b30-49ce-be9c-54a55655754a"
}
```

响应：

```json
{
  "type": "session.end.accepted",
  "sessionId": "scene_801cca60-5b30-49ce-be9c-54a55655754a",
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {
    "sessionId": "scene_801cca60-5b30-49ce-be9c-54a55655754a",
    "stopTime": "2026-07-21T08:25:07Z"
  }
}
```

HTTP `POST /api/scene-sessions/{sessionId}/end` 目前保留，方便调试和兜底。

后端行为：

1. 会话状态变为 `COMPLETED`。
2. 结算用量。
3. 更新 session memory。

## 5. 自定义场景接口

状态：自定义场景使用独立 `CustomSceneController`，与
`FreeChatSessionController` 分开。独立学习 WebSocket、TTS、发音评分和最终报告仍待接入。

### 5.1 生成自定义场景

```text
POST /api/custom-scenes/generate
Authorization: Bearer <accessToken>
```

请求：

```json
{
  "userId": "user_123",
  "sceneInput": "第一次去健身房，咨询设施、开放时间和会员体验",
  "userPreference": "英语基础一般，喜欢慢速对话",
  "offerSdp": "浏览器生成的 WebRTC Offer SDP",
  "provider": "QWEN",
  "model": "qwen3.5-omni-flash-realtime",
  "voice": "Katerina",
  "translationEnabled": true
}
```

响应：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {
    "sceneId": "scene_801cca60-5b30-49ce-be9c-54a55655754a",
    "sceneName": "第一次去健身房，咨询设施、开放时间...",
    "sceneType": "CUSTOM_SCENE",
    "wordList": [],
    "phraseList": [],
    "sentenceList": [],
    "scenePrompt": "完整的自定义场景提示词"
  }
}
```

### 5.2 启动场景训练

自定义场景使用独立启动接口：

```text
POST /api/custom-scenes/start
```

请求：

```json
{
  "userId": "user_123",
  "sceneInput": "第一次去健身房，咨询设施、开放时间和会员体验",
  "userPreference": "英语基础一般，喜欢慢速对话"
}
```

为兼容已有前端，`sceneInput` 也可以使用字段名 `prompt`。

### 5.3 场景学习 Flow

```text
POST /api/custom-scenes/flows
POST /api/custom-scenes/flows/advance
GET  /api/custom-scenes/flows/{flowId}
POST /api/custom-scenes/flows/complete
```

响应会包含学习材料：

```json
{
  "sceneId": "scene_801cca60-5b30-49ce-be9c-54a55655754a",
  "sceneName": "第一次去健身房，咨询设施、开放时间和会员...",
  "sceneType": "CUSTOM_SCENE",
  "wordList": [
    {
      "contentId": "word_1",
      "englishText": "membership",
      "chineseText": "核心话题词",
      "phonetic": ""
    }
  ],
  "phraseList": [
    {
      "contentId": "phrase_1",
      "englishText": "Could you tell me more about this situation?",
      "chineseText": "你能多介绍一下这个场景吗？",
      "phonetic": ""
    }
  ],
  "sentenceList": [
    {
      "contentId": "sentence_1",
      "englishText": "Hi, I would like to practice a conversation about this situation.",
      "chineseText": "你好，我想练习关于这个场景的对话。",
      "phonetic": ""
    }
  ],
  "flowId": "flow_9524a0d2-617d-42a4-b824-f7e86cc5158c",
  "currentStage": "WORD_LEARNING",
  "scoringEnabled": true,
  "sessionId": "scene_801cca60-5b30-49ce-be9c-54a55655754a",
  "startTime": "2026-07-21T08:15:07Z",
  "scenePrompt": "完整系统提示词"
}
```

## 6. 个人中心 HTTP 接口

状态：建议新增。

### 6.1 学习概览

```text
GET /api/users/me/overview
Authorization: Bearer <accessToken>
```

响应：

```json
{
  "weeklyMinutes": 183,
  "assetCount": 12,
  "streakDays": 7,
  "calendar": [
    {
      "date": "2026-07-21",
      "practiced": true,
      "minutes": 31
    }
  ],
  "milestones": [
    {
      "id": "first_free_chat",
      "title": "开口先锋",
      "unlocked": true
    }
  ]
}
```

### 6.2 会员和额度

```text
GET /api/users/me/subscription
Authorization: Bearer <accessToken>
```

响应：

```json
{
  "plan": "free",
  "freeChatQuota": {
    "usedMinutesToday": 3,
    "limitMinutesToday": 5
  },
  "sceneQuota": {
    "usedToday": 0,
    "limitToday": 1
  }
}
```

### 6.3 学习资产列表

```text
GET /api/learning-assets
Authorization: Bearer <accessToken>
```

查询参数：

```text
category=普通场景|IELTS|英文面试
page=1
pageSize=20
```

响应：

```json
{
  "items": [
    {
      "assetId": "asset_123",
      "title": "咖啡店点单",
      "category": "普通场景",
      "date": "2026-07-21",
      "status": "COMPLETED",
      "score": 84,
      "itemCount": 4
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1
}
```

### 6.4 学习资产详情

```text
GET /api/learning-assets/{assetId}
Authorization: Bearer <accessToken>
```

响应：

```json
{
  "assetId": "asset_123",
  "title": "咖啡店点单",
  "category": "普通场景",
  "status": "COMPLETED",
  "learningItems": [
    {
      "type": "短语",
      "en": "with oat milk",
      "zh": "换成燕麦奶"
    }
  ],
  "conversation": [
    {
      "role": "USER",
      "text": "Could you recommend something less sweet?",
      "createdAt": "2026-07-21T08:15:40Z"
    }
  ],
  "scoreReport": {
    "totalScore": 84
  }
}
```

### 6.5 删除学习资产

```text
DELETE /api/learning-assets/{assetId}
Authorization: Bearer <accessToken>
```

响应：

```json
{
  "deleted": true
}
```

## 7. 评分接口

状态：建议新增。

评分可在会话结束后由后端异步生成，也可前端主动查询。

### 7.1 查询会话评分

```text
GET /api/scene-sessions/{localSessionId}/score
Authorization: Bearer <accessToken>
```

响应：

```json
{
  "localSessionId": "scene_xxx",
  "status": "READY",
  "totalScore": 84,
  "dimensions": [
    {
      "name": "pronunciation",
      "label": "发音清晰度",
      "score": 84
    },
    {
      "name": "fluency",
      "label": "流利度",
      "score": 78
    },
    {
      "name": "completeness",
      "label": "表达完整度",
      "score": 91
    },
    {
      "name": "interaction",
      "label": "互动回应",
      "score": 86
    },
    {
      "name": "naturalness",
      "label": "自然度",
      "score": 80
    }
  ],
  "feedback": [
    {
      "type": "GRAMMAR",
      "title": "表达问题",
      "original": "I feel like to try something different.",
      "suggestion": "I feel like trying something different.",
      "explanation": "feel like 后面应接动名词。"
    }
  ]
}
```

### 7.2 评分状态

如果评分异步生成：

```text
GET /api/scene-sessions/{localSessionId}/score/status
Authorization: Bearer <accessToken>
```

响应：

```json
{
  "localSessionId": "scene_xxx",
  "status": "PENDING"
}
```

可选值：

```text
PENDING, READY, FAILED
```

## 8. 前端页面和接口对应关系

| 前端页面 | HTTP 接口 | 实时协议 |
| --- | --- | --- |
| 登录 | `POST /api/auth/login` | 无 |
| 注册 | `POST /api/auth/register` | 无 |
| Level 选择 | `PUT /api/users/me/onboarding` | 无 |
| 老师选择 | `PUT /api/users/me/onboarding`, `PATCH /api/users/me/profile` | 无 |
| 自由会话主页 | `POST /api/scene-sessions` | `WS /ws/session-messages` |
| 自由会话结束 | `POST /api/scene-sessions/{sessionId}/end` 可选 | `session.end` |
| 字幕/完整消息 | 无 | `session.message` |
| 自定义场景生成 | `POST /api/custom-scenes/generate` | 无 |
| 自定义场景启动 | `POST /api/custom-scenes/start` | 无 |
| 场景训练 | `POST /api/scene-sessions` | 无 |
| 学习资产 | `GET /api/learning-assets` | 无 |
| 个人中心 | `GET /api/users/me/overview`, `GET /api/users/me/profile` | 无 |
| 会员额度 | `GET /api/users/me/subscription` | 无 |
| 评分结果 | `GET /api/scene-sessions/{sessionId}/score` | 无 |

## 9. 当前后端已实现清单

| 能力 | 状态 | 文件 |
| --- | --- | --- |
| 启动自由会话 | 已实现，固定 `FREE_CHAT` | `FreeChatSessionController` -> `SceneSessionCoordinator` |
| 追加完整消息 | 已实现，走 WebSocket | `SessionMessageWebSocketHandler`, `SessionService.addMessage` |
| 结束自由会话 | 已实现，WebSocket 为主，HTTP 保留 | `SessionMessageWebSocketHandler`, `FreeChatSessionController`, `SessionService.endSession` |
| 申请 Qwen 临时 token | 保留 Provider 能力，当前不属于 SessionService | `RealtimeCredentialServiceImpl` |
| Offer SDP 换 Answer SDP | 已通过模型 Registry 选择 Realtime Provider | `AiProviderRegistry` -> `QwenRealtimeProvider` |
| 登录注册 | 未实现 | 建议新增 |
| 用户偏好 | 仓储和服务雏形存在，HTTP 未实现 | 建议新增 Controller |
| 自定义场景 | 已实现独立入口 | `CustomSceneController` |
| 学习资产 | 未实现 | 建议新增 |
| 评分 | 未实现 | 建议新增 |

## 10. 联调时看日志

日志文件：

```text
backend/unispeaking-server/logs/realtime-flow.log
```

命令：

```bash
tail -f backend/unispeaking-server/logs/realtime-flow.log
```

关键日志：

```text
flow.1.start.request
flow.2.token.request
flow.2.token.response
flow.3.sdp.request
flow.3.sdp.response
flow.4.start.response
flow.event.websocket
flow.5.datachannel.session
flow.6.bind
flow.event.transcript
flow.state.pause
flow.state.resume
flow.state.interrupt
flow.state.stop
```
