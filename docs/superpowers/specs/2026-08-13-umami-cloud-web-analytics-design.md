# UniSpeaking Umami Cloud Web 统计接入设计

日期：2026-08-13

## 1. 目标与范围

在最新 `origin/main` Web 前端上接入 Umami Cloud，为当前生产站点
`https://unispeaking.qnsdk.com` 提供访问与关键学习行为统计。

本次只修改 Web 前端、生产构建配置和必要的部署文档，不修改用户业务后端、数据库、
用户权限与权益功能，不自部署 Umami，不引入新的业务服务。

## 2. 当前状态

- 最新远端提交为 `d3962b7`，生产站点页面与该版本的 Web 构建一致。
- Umami Cloud 已存在名为 `unispeaking` 的 Website，Website ID 为
  `3ae2dee9-d585-43a9-93f3-fcafcd14b258`，当前无统计数据。
- 最新 Web 主线没有统计脚本或行为埋点。
- 旧本地调试分支存在可复用的事件语义，但其 `App.jsx` 已落后于主线，不能直接覆盖。

## 3. 接入架构

```text
unispeaking.qnsdk.com
        |
        +-- Umami Cloud script.js 自动统计页面访问
        |
        +-- analyticsClient 统一领域埋点
                    |
                    +-- window.umami.track(event, properties)
                                |
                                +-- Umami Cloud Events
```

Web 页面只调用本地 `analyticsClient`，不在业务组件中散落平台相关实现。以后替换统计平台时，
只需更换传输适配器。

## 4. 配置方式

生产构建增加：

- `VITE_UMAMI_ENABLED=true`
- `VITE_UMAMI_SCRIPT_URL=https://cloud.umami.is/script.js`
- `VITE_UMAMI_WEBSITE_ID=<Website ID>`
- `VITE_UMAMI_DOMAINS=unispeaking.qnsdk.com`

开发和测试环境默认关闭。构建时由 Vite 注入配置，不在业务代码中硬编码账号或密钥。
Website ID 是公开的采集标识，不属于访问密钥；Umami 登录凭据和 API Token 不进入前端。

## 5. 页面统计

关闭 Umami 自动页面统计，由前端在首屏和 SPA 路由切换时手动上报归一化路径。启用域名限制，
只接受 `unispeaking.qnsdk.com` 的流量，避免本地开发与非生产域名污染数据。

页面路径中的场景 ID、会话 ID、题目选择及查询参数在发送前转换为固定模板路径；不上传页面正文、
用户输入或动态标识。

## 6. 业务事件

### 6.1 模式定义

- `SCENE`：场景训练
- `FREE_CHAT`：自由聊天
- `INTERVIEW`：英语面试
- `IELTS`：雅思口语
- 学习资产是独立行为，不作为训练模式。

### 6.2 首期事件

| 事件 | 触发时机 | 关键属性 |
|---|---|---|
| `mode_selected` | 用户主动选择训练模式 | `mode`, `page_code`, `source` |
| `training_start_attempt` | 点击开始训练 | `mode`, `page_code` |
| `training_started` | 实时训练会话建立成功 | `mode`, `page_code` |
| `training_start_failed` | 训练启动失败 | `mode`, `reason` |
| `training_completed` | 正常完成训练 | `mode`, `effective_duration_seconds` |
| `training_abandoned` | 开始后中途退出 | `mode`, `reason`, `effective_duration_seconds` |
| `learning_asset_view` | 用户实际打开学习资产 | `asset_type`, `page_code` |

不向 Umami 上报 15 秒训练心跳。有效训练时长由前端活动计时器在完成或退出时汇总，后台如需
财务、权益或强一致统计，仍以业务数据库为准。

## 7. 数据安全

事件字段使用白名单。禁止上传：邮箱、手机号、密码、Token、JWT、Cookie、录音、音频 URL、
对话文本、字幕、Prompt、简历、职位材料和任何 AccessKey。

不调用 Umami Identify 上传真实用户 ID。首期只分析匿名访问与群体行为，避免将用户身份交给
第三方统计平台。

## 8. 故障处理

- Umami 脚本加载或事件发送失败时静默降级，不阻塞登录、导航和训练。
- 广告拦截器可能阻止采集，此类丢失不能影响产品功能。
- 客户端不重试训练完成事件，避免重复统计；Umami 不作为业务真相来源。
- 事件属性限制为短字符串、布尔值和数值，避免高基数与大载荷。

## 9. 部署顺序

1. 在最新主线 worktree 创建功能分支。
2. 先增加统计适配器测试，再实现 Umami 脚本配置和事件传输。
3. 将旧分支的领域埋点按最新主线代码逐点移植，避免覆盖七牛与面试改动。
4. 运行 Web 统计测试、现有契约检查与生产构建。
5. 本地预览验证脚本开关、页面访问、事件属性和降级行为。
6. 经用户确认后，仅部署 Web 前端容器。
7. 在 Umami Realtime、Pages、Events 和 Properties 中做生产验收。

## 10. 验收标准

- 生产构建产物包含 Umami 脚本、正确 Website ID 和生产域名限制。
- 访问 `unispeaking.qnsdk.com` 后，Realtime 在合理延迟内出现访客。
- SPA 路由切换只产生一次对应页面访问。
- 四种模式选择分别产生正确的 `mode_selected` 属性。
- 训练成功、失败、完成与中途退出事件符合实际操作。
- 完成和退出事件带有效训练时长，后台无 15 秒心跳噪声。
- 事件中不存在受禁止的敏感字段。
- Umami 不可用时主站登录、导航与训练仍正常。

## 11. 非本次范围

- UniSpeaking 管理后台读取 Umami API 并渲染自有看板。
- 后端运行日志、错误告警与 APM。
- Umami 自部署、数据库维护和自定义统计域名。
- 用户权限、权益和费用计量模块调整。
