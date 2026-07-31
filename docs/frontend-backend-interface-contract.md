# UniSpeaking 前后端接口文档

本文档用于前端和后端对齐接口。当前后端已实现登录注册、JWT 鉴权、用户偏好、字幕翻译、自由会话开始、WebSocket 追加完整消息和结束；其他未实现接口会单独标记。

## 个人主页与账户安全接口

以下接口均从 JWT 获取当前用户，不接收客户端传入的 `userId`。

### 获取个人主页

```text
GET /api/profile/overview?month=2026-07
Authorization: Bearer <accessToken>
```

`month` 可省略，格式为 `yyyy-MM`，省略时按 `Asia/Shanghai` 返回当前月；未来月份不允许查询。日历的打卡依据是该用户场景下已经持久化的五维评分报告，同一天多份报告只返回一个日期。

```json
{
  "account": {
    "userId": "11111111-1111-4111-8111-111111111111",
    "email": "name@example.com",
    "nickname": "Sunny",
    "displayName": "Sunny",
    "avatarUrl": "https://signed-oss-url.example/avatar.jpg",
    "avatarUrlExpiresAt": "2026-07-31T08:00:00Z"
  },
  "calendar": {
    "month": "2026-07",
    "checkedDates": ["2026-07-02", "2026-07-15"],
    "checkedInToday": true
  }
}
```

`avatarUrl` 是短期签名地址，未上传头像或对象存储暂不可用时为 `null`。

### 修改用户名（昵称）

```text
PATCH /api/profile
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{
  "nickname": "Sunny"
}
```

这里只修改展示昵称，不修改登录邮箱。昵称去除首尾空白后长度必须为 1～80。

### 上传头像

```text
POST /api/profile/avatar
Authorization: Bearer <accessToken>
Content-Type: multipart/form-data
```

表单字段名为 `avatar`。仅接受 JPEG/PNG，文件不超过 2 MiB，宽高均为 128～4096 像素。后端会解码并重新编码后写入阿里云 OSS，响应包含一小时有效的签名 URL。

### 修改密码

```text
PUT /api/auth/password
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{
  "currentPassword": "old-password",
  "newPassword": "new-password"
}
```

新密码长度为 6～72，且不能与当前密码相同。成功响应中的 `reauthenticationRequired` 为 `true`；服务端同时递增 `auth_version`，使该用户所有现有 JWT（包括当前请求所用 JWT）失效，前端必须清除 Token 并跳转登录页。

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

## 3. Level、老师和长期用户资料 HTTP 接口

状态：已实现。新手引导的“下一步”和设置页的“保存设置”统一调用用户偏好接口。

### 3.2 获取用户偏好

```text
GET /api/user-preferences
Authorization: Bearer <accessToken>
```

响应：

```json
{
  "success": true,
  "data": {
    "userId": "11111111-1111-4111-8111-111111111111",
    "preferredVoice": "Katerina",
    "preferredAiSpeechSpeed": "NATURAL",
    "cefrLevel": "C",
    "memoryText": "兴趣与背景：喜欢科技和旅行，从事软件产品相关工作。个人信息：昵称 Sunny；不希望讨论具体客户和项目。"
  }
}
```

### 3.3 更新用户偏好

```text
PUT /api/user-preferences
Authorization: Bearer <accessToken>
```

请求：

```json
{
  "preferredVoice": "Katerina",
  "preferredAiSpeechSpeed": "MODERATE",
  "cefrLevel": "B",
  "memoryText": "兴趣与背景：喜欢科技、电影和旅行，熟悉会议和演示场景。个人信息：昵称 Sunny；不希望讨论具体公司、客户或项目。"
}
```

所有字段都可省略；省略表示保留原值，`memoryText` 传空字符串表示清空长期资料。`memoryText` 最长 4000 个字符，只保存用户主动提供的长期背景、称谓和话题边界，不保存逐轮对话或会话历史摘要。响应同 `GET /api/user-preferences`。

字段值：

| 字段 | 可选值 |
| --- | --- |
| `preferredVoice` | `Katerina`, `Aiden`, `Raymond`, `Tina`, `Harvey`, `Dolce` |
| `preferredAiSpeechSpeed` | `SLOWER`, `MODERATE`, `NATURAL`, `FASTER` |
| `cefrLevel` | `A`, `B`, `C`, `D` |

## 4. 字幕翻译 HTTP 接口

状态：已实现。前端只在用户点击某句字幕的“翻译”按钮时调用，结果缓存在当前页面的字幕行中。

```text
POST /api/translations
Authorization: Bearer <accessToken>
```

请求：

```json
{
  "text": "How are you today?"
}
```

响应数据：

```json
{
  "sourceText": "How are you today?",
  "translatedText": "你今天好吗？",
  "targetLanguage": "zh-CN"
}
```

后端通过 `AiProviderRegistry` 选择 `qwen3.5-plus`，调用百炼 Chat Completions 完成翻译。待翻译文本不能为空且最长 4000 个字符。

## 5. 场景会话统一接口

状态：已实现统一启动、WebSocket 追加完整消息和结束。

### 5.1 启动场景会话

```text
POST /api/scene-sessions
```

请求：

```json
{
  "offerSdp": "浏览器生成的 WebRTC Offer SDP",
  "provider": "QWEN",
  "model": "qwen3.5-omni-flash-realtime",
  "voice": "Katerina",
  "translationEnabled": true
}
```

该接口是自由聊天入口。客户端不传 `userId`、`sceneType`、`prompt`、
`topic` 或 `userPreference`。后端从 JWT 获取用户 ID，从数据库读取用户
Profile 和偏好，固定以 `FREE_CHAT` 调用 `SceneService` 和
`FiveLayerPromptService` 生成完整五层提示词。

响应：

```json
{
  "sceneId": "freechat_801cca605b3049cebe9c54a55655754a",
  "sceneName": "Free Chat",
  "sceneType": "FREE_CHAT",
  "wordList": [],
  "phraseList": [],
  "sentenceList": [],
  "currentStage": "DIALOGUE",
  "scoringEnabled": false,
  "sessionId": "freechat_session_801cca605b3049cebe9c54a55655754a",
  "providerSessionId": null,
  "answerSdp": "Qwen 返回的 WebRTC Answer SDP",
  "credentialExpiresAt": "2026-07-21T08:20:07Z",
  "voiceId": "Katerina",
  "status": "WAITING_CLIENT",
  "startTime": "2026-07-21T08:15:07Z",
  "systemPrompt": "完整系统提示词"
}
```

自由会话会直接进入 `DIALOGUE` 阶段且不评分；自定义场景会返回三组学习材料并从 `WORD_LEARNING` 开始，后续可接单词、词组、句子学习和评分。

`systemPrompt` 是后端在 `SceneService.generateScene(...)` 中完成权限校验、
用户 Profile 注入和用户偏好注入后的完整五层提示词。前端必须将它放入
DataChannel `session.update.session.instructions`，不得使用客户端默认提示词替代。
`SessionService.startSession(prompt)` 只创建业务会话并记录开始时间。
`RealtimeSessionConnector` 使用 `offerSdp/model/voice` 调用
`RealtimeConnectionService`，内部申请短期凭证并交换 Answer SDP。`systemPrompt`
就是 `SceneService` 生成的 `scenePrompt`，启动响应只保留这一个提示词字段。

### 5.2 追加完整消息

```text
WS /ws/session-messages?access_token=<JWT>
```

浏览器 WebSocket 握手必须携带登录接口签发的 JWT。Docker 部署时实际地址为
`/backend/ws/session-messages?access_token=<JWT>`。Spring Security 在协议升级前
验证 JWT，握手拦截器将已验证的 `userId` 写入 WebSocket 会话；后端随后对每个
`session.message` 和 `session.end` 校验 `sessionId` 是否属于该用户。

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

后端只接受 `content` 为非空最终文本的消息。自由聊天内容不做持久化，也不写入
Redis 或数据库；流式 transcription/audio delta 同样不会保存。自定义场景的
完整消息才会写入 `session_message`。

响应：

```json
{
  "type": "session.message.accepted",
  "sessionId": "freechat_session_801cca605b3049cebe9c54a55655754a",
  "success": true,
  "code": "OK",
  "message": "success",
  "data": null
}
```

### 5.3 结束自由会话

```text
WS /ws/session-messages?access_token=<JWT>
```

请求：

```json
{
  "type": "session.end",
  "sessionId": "scene_801cca60-5b30-49ce-be9c-54a55655754a",
  "stopTime": "2026-07-21T08:25:07Z"
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
  "data": null
}
```

HTTP `POST /api/scene-sessions/{sessionId}/end` 目前保留，方便调试和兜底。

后端行为：

1. 会话状态变为 `COMPLETED`。
2. 结算用量。
3. 保留本次完整消息；不更新用户长期 `memory_text`。

## 6. 自定义场景接口

状态：自定义场景使用独立 `CustomSceneController`，与
`FreeChatSessionController` 分开。独立学习 WebSocket、TTS、发音评分和最终报告仍待接入。

### 6.1 生成自定义场景

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
    "sceneId": "custom_801cca605b3049cebe9c54a55655754a",
    "wordList": [],
    "phraseList": [],
    "sentenceList": [],
    "scenePrompt": "L1 基础职责\n\nL2 老师角色\n\nL3 难度与语速\n\nL4 用户长期偏好\n\nL5 当前场景"
  }
}
```

### 6.2 启动场景训练

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

### 6.3 场景学习 Flow

```text
POST /api/custom-scenes/flows
POST /api/custom-scenes/flows/advance
GET  /api/custom-scenes/flows/{sceneId}/content?stage=WORD_LEARNING
POST /api/custom-scenes/flows/complete
```

创建 Flow 只传已经生成并保存的 `sceneId`：

```json
{
  "sceneId": "custom_801cca605b3049cebe9c54a55655754a"
}
```

创建响应：

```json
{
  "sceneId": "custom_801cca605b3049cebe9c54a55655754a",
  "stage": "WORD_LEARNING",
  "completed": false
}
```

获取当前阶段内容：

```json
[
  {
    "contentId": "word_1",
    "englishText": "membership",
    "chineseText": "核心话题词",
    "phonetic": ""
  }
]
```

推进阶段时传当前阶段，用于防止前端重复点击或乱序请求：

```json
{
  "sceneId": "custom_801cca605b3049cebe9c54a55655754a",
  "stage": "WORD_LEARNING"
}
```

`FREE_CHAT` 创建 Flow 后直接返回 `DIALOGUE`；其他场景从
`WORD_LEARNING` 开始。`SceneFlowService` 根据 `sceneId` 读取已保存的
场景内容，不再接收 `userId`、Prompt 或三组学习材料。

## 7. 个人中心 HTTP 接口

状态：建议新增。

### 7.1 学习概览

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

### 7.2 会员和额度

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

### 7.3 学习资产列表

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

### 7.4 学习资产详情

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

### 7.5 删除学习资产

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

## 8. 评分接口

状态：建议新增。

评分可在会话结束后由后端异步生成，也可前端主动查询。

### 8.1 查询会话评分

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

### 8.2 评分状态

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

## 9. 前端页面和接口对应关系

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

## 10. 当前后端已实现清单

| 能力 | 状态 | 文件 |
| --- | --- | --- |
| 启动自由会话 | 已实现，固定 `FREE_CHAT` | `FreeChatSessionController` -> `SceneSessionCoordinator` |
| 追加完整消息 | 已实现，走 WebSocket | `SessionMessageWebSocketHandler`, `SessionService.addMessage` |
| 结束自由会话 | 已实现，WebSocket 为主，HTTP 保留 | `SessionMessageWebSocketHandler`, `FreeChatSessionController`, `SessionService.endSession` |
| 申请 Qwen 临时 token | 保留 Provider 能力，当前不属于 SessionService | `RealtimeCredentialServiceImpl` |
| Offer SDP 换 Answer SDP | 已通过模型 Registry 选择 Realtime Provider | `AiProviderRegistry` -> `QwenRealtimeProvider` |
| 登录注册与 JWT 鉴权 | 已实现 | `AuthController`, `AuthService`, `JwtTokenService` |
| 用户偏好与长期用户资料 | 已实现，MyBatis-Plus 持久化 | `UserPreferenceController`, `ProfileService` |
| 字幕按需翻译 | 已实现，使用 `qwen3.5-plus` | `TranslationController`, `TranslationService`, `QwenLlmProvider` |
| 自定义场景 | 已实现独立入口 | `CustomSceneController` |
| 学习资产 | 未实现 | 建议新增 |
| 评分 | 未实现 | 建议新增 |

## 11. 联调时看日志

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
