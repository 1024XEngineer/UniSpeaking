# UniSpeaking Frontend

前端项目位于 [`Unispeaking_fronted`](Unispeaking_fronted)，使用 React 19 和
Vite 6。

## 当前联调状态

已经连接后端的功能：

- 注册、登录、JWT 本地会话。
- 用户等级、AI 老师、音色和语速偏好。
- Qwen Realtime 自由对话、字幕、翻译和结束会话。
- 自定义场景生成及“学 → 读 → 说”流程。
- 单词、词组、句子展示和 TTS 示范。
- 句子音频采集与朗读评分。
- 自定义场景对话、状态机、逐轮评分、五维报告。
- 学习资产列表、详情和场景复练。

仍以页面和演示数据为主的功能：

- IELTS 训练及报告。
- 英文模拟面试。
- 个人主页统计与成就。
- 会员、额度和支付。

## 本地启动

要求 Node.js 20 或更高版本。

```bash
cd frontend/Unispeaking_fronted
npm install
VITE_BACKEND_URL=http://localhost:8080 npm run dev
```

默认地址：

```text
http://localhost:5173
```

`VITE_BACKEND_URL` 同时用于 REST 和认证 WebSocket。留空时请求会发送到前端源，
只有经过 Nginx 同源代理的生产部署才应使用 `/backend`。

浏览器麦克风需要 `localhost` 或 HTTPS，并需要用户授权。

## 构建和契约检查

```bash
npm run build
npm run check:routes
npm run check:realtime-events
```

## 目录

```text
Unispeaking_fronted
├── public
│   ├── brand
│   ├── examiner
│   └── teachers
├── scripts
│   ├── check-realtime-events.mjs
│   └── check-routes.mjs
├── src
│   ├── App.jsx
│   ├── IeltsModule.jsx
│   ├── InterviewModule.jsx
│   ├── apiClient.js
│   ├── realtimeClient.js
│   ├── router.js
│   └── styles.css
├── Dockerfile
├── nginx.conf
└── package.json
```

- `apiClient.js`：HTTP API、JWT Header 和统一响应解包。
- `realtimeClient.js`：WebRTC、DataChannel、WebSocket 和实时事件归一化。
- `router.js`：页面路径生成和解析。
- `App.jsx`：自由对话、自定义场景、学习流程和主要页面状态。
- `IeltsModule.jsx`、`InterviewModule.jsx`：尚在完善后端接口的独立模块。

## 主要路由

| 路由 | 页面 |
| --- | --- |
| `/conversation` | 自由对话入口 |
| `/conversation/{sessionId}` | 当前自由对话 |
| `/scenes` | 场景广场 |
| `/scenes/{sceneId}/word` | 单词学习 |
| `/scenes/{sceneId}/phrase` | 词组学习 |
| `/scenes/{sceneId}/sentence` | 句子朗读 |
| `/scenes/{sceneId}/session/{sessionId}` | 自定义场景对话 |
| `/scenes/{sceneId}/assets` | 场景学习资产 |
| `/assets` | 学习资产首页 |
| `/ielts` | IELTS 模块 |
| `/interview` | 英文面试模块 |
| `/profile` | 个人主页 |
| `/settings` | 用户设置 |

路由契约由 `npm run check:routes` 校验。页面切换必须使用 `router.js` 中的路径
生成器，不能重新退回只改 React state、不更新浏览器地址的方式。

## 鉴权与实时连接

- Access Token 保存在 `localStorage` 的 `unispeaking.accessToken`。
- HTTP 使用 `Authorization: Bearer <token>`。
- WebSocket 使用
  `/ws/session-messages?access_token=<token>`。
- WebRTC 音频和 Realtime DataChannel 由浏览器直接连接 Qwen。
- 暂停、恢复和打断属于前端与 Realtime 模型的交互。
- 后端 WebSocket 只接收需要保存的完整轮次消息和结束事件。

任何 `VITE_` 变量都会进入浏览器构建产物，禁止放入 API Key、数据库密码或 JWT
Secret。

完整接口以
[`docs/frontend-backend-interface-contract.md`](../docs/frontend-backend-interface-contract.md)
为准。
