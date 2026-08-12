# UniSpeaking Frontends

仓库包含两个相互独立的前端项目：

- [`web`](web)：React 19 + Vite 6 Web 应用，已经接入主要后端业务链路。
- [`mobile`](mobile)：React Native + Expo Router 移动端，当前以高保真交互原型为主。

两端共享产品流程和视觉语言，但不共享组件源码。

## Web 前端

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
- 个人主页每周学习目标、真实进度、训练类型占比、五维能力趋势、薄弱项识别与推荐训练。
- 个人主页关于产品、用户协议、隐私政策与 AI 服务说明。

仍以页面和演示数据为主的功能：

- IELTS 训练及报告。
- 英文模拟面试。
- 会员、额度和支付。

## 本地启动

要求 Node.js 20 或更高版本。

```bash
cd frontend/web
npm install
VITE_BACKEND_URL=http://localhost:8080 VITE_FEEDBACK_URL= npm run dev
```

默认地址：

```text
http://localhost:5173
```

`VITE_BACKEND_URL` 同时用于 REST 和认证 WebSocket。留空时请求会发送到前端源，
只有经过 Nginx 同源代理的生产部署才应使用 `/backend`。

反馈入口默认打开 `https://wj.qq.com/s2/27565116/i1wq/`。如需替换问卷，可通过
`VITE_FEEDBACK_URL` 配置其他有效的 HTTP(S) 地址；入口会在新窗口打开问卷。反馈数据
不经过 UniSpeaking 后端，也不提供处理进度查询。

浏览器麦克风需要 `localhost` 或 HTTPS，并需要用户授权。

## 构建和契约检查

```bash
npm run build
npm run check:routes
npm run check:realtime-events
```

## 目录

```text
web
├── public
│   ├── brand
│   ├── examiner
│   └── teachers
├── scripts
│   ├── check-realtime-events.mjs
│   └── check-routes.mjs
├── src
│   ├── common                 # 公共样式和跨模块基础能力
│   ├── component              # 按业务域拆分的页面与可复用 UI
│   ├── controller              # 应用入口、路由和页面编排
│   ├── domain/content          # 业务演示数据和领域内容
│   ├── infrastructure          # HTTP、浏览器音频等外部能力
│   └── websocket               # 实时会话与消息归一化
├── Dockerfile
├── nginx.conf
└── package.json
```

- `infrastructure/http/apiClient.js`：HTTP API、JWT Header 和统一响应解包。
- `websocket/realtimeClient.js`：WebRTC、DataChannel、WebSocket 和实时事件归一化。
- `controller/router.js`：页面路径生成和解析。
- `controller/App.jsx`：自由对话、自定义场景、学习流程和主要页面状态。

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
| `/profile` | 个人主页 |
| `/profile/insights` | 学习目标与洞察 |
| `/about` | 关于产品 |
| `/about/user-agreement` | 用户协议草案 |
| `/about/privacy-policy` | 隐私政策草案 |
| `/about/ai-service` | AI 服务说明草案 |
| `/settings` | 用户设置 |

路由契约由 `npm run check:routes` 校验。页面切换必须使用 `controller/router.js` 中的路径
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

## 移动端

要求 Node.js 20 或更高版本。浏览器预览：

```bash
cd frontend/mobile
npm install
npm run web
```

Android 开发客户端：

```bash
npx expo prebuild --platform android
npm run android
```

移动端使用 TypeScript、Expo SDK 57 和 Expo Router，应用包名为
`com.unispeaking.mobile`。当前只有自由对话、场景广场和学习资产完成前端定稿；
IELTS、英文面试和个人主页仍需继续开发与定稿，仓库中的相关页面只是阶段性原型。
移动端后端数据接入范围也与 Web 端不同，继续开发前请先阅读
[`mobile/HANDOFF.md`](mobile/HANDOFF.md)。
