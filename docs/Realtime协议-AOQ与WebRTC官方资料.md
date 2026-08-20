# Realtime 协议、AOQ 与 WebRTC 官方资料整理

> 调研时间：2026-08-20
>
> 本笔记只引用阿里云百炼官方文档，用于评估移动端从 WebRTC 迁移 AOQ，以及是否让 Realtime 模型承担场景语义状态分析。它不修改现有业务代码。

## 官方结论摘要

- 百炼 Realtime API 同时提供 AOQ（AI over QUIC）、WebRTC、WebSocket 三种传输协议。协议负责媒体/数据传输，不能替代业务状态机。
- 官方选型表将 AOQ 定位为 AI 多模态实时交互、弱网、混合数据传输，尤其面向 Android、iOS、HarmonyOS 原生应用；WebRTC 定位为浏览器端和传统音视频通话场景。
- 官方 Realtime 模型矩阵明确列出 `qwen3.5-omni-flash-realtime` 对 AOQ、WebRTC、WebSocket 均支持。因此从产品能力上，Flash 模型不是只能用 WebRTC。
- WebRTC 只支持服务端 VAD（`server_vad` 或 `semantic_vad`），不支持手动模式；官方对 Qwen3.5 Omni-Realtime 推荐 `semantic_vad`。AOQ 示例也使用服务端 VAD。
- Qwen3.5 Omni-Realtime 官方说明支持 Function Calling 和语义打断，但这不等同于“模型会可靠地维护应用场景状态机”。结构化的完成判定仍需定义工具/事件契约、幂等、超时和兜底。
- AOQ 官方接入方式是原生 Client SDK（Android AAR、iOS framework、HarmonyOS har），需要业务 AppServer 获取 Token；它不是可直接在浏览器中替换 WebRTC 的 JavaScript 协议。

## 1. Realtime 的协议定位

官方概述将三种协议定义为同一个 Realtime API 的不同传输方案：

| 维度 | AOQ | WebRTC | WebSocket |
| --- | --- | --- | --- |
| 典型场景 | AI 多模态实时交互、弱网、混合数据 | 浏览器端互动、传统音视频通话 | 服务端集成、快速原型 |
| 浏览器兼容性 | 不支持 | 原生支持 | 原生支持 |
| 弱网对抗 | 极致 | 良好 | 差 |
| 数据类型 | 音视频 + 文本 | 音视频 + 文本 | 文本/音频/图像 |
| 回声消除/降噪 | 内置 | 内置 | 无，需客户端处理 |
| 原生端平台 | Android / iOS / HarmonyOS | 浏览器、移动端 | 支持 WebSocket 的平台 |

官方建议：AOQ 更适合对延迟、弱网和多模态传输有极致要求的 AI 交互，尤其是移动端原生应用；WebRTC 更适合浏览器原生支持或已有 WebRTC 基础设施的场景。

来源：[Realtime API 概述](https://help.aliyun.com/zh/model-studio/realtime-api-overview)

### 当前模型的官方支持矩阵

概述页面的“模型/应用支持力度”表格将以下实时全模态模型标记为 AOQ、WebRTC、WebSocket 均支持：

- `qwen3.5-omni-plus-realtime`
- `qwen3.5-omni-flash-realtime`

因此如果当前代码实际使用的是 `qwen3.5-omni-flash-realtime`（用户所说的“Qwen-omni-flash3.0”需要再以控制台/代码中的精确模型名核对），AOQ 在官方矩阵中具备接入资格。官方 AOQ 示例页目前以 `qwen3.5-omni-plus-realtime` 为示例，迁移 Flash 时仍应按实际地域、SDK 版本和模型白名单做一次小规模验证。

来源：[Realtime API 概述](https://help.aliyun.com/zh/model-studio/realtime-api-overview)

## 2. AOQ 的接入方式和实时能力

AOQ（AI over QUIC）不是在客户端自行实现 QUIC，而是通过阿里云 AOQ Client SDK 建立音频、视频和数据轨道：

1. 原生 App 向业务 AppServer 请求本次通话 Token、会话参数等连接凭证。
2. 客户端创建 `AoqClientEngine`，启动音频采集/播放（视频可选）。
3. 调用 `connect` 建立 AOQ 连接；官方示例要求先关闭媒体发送。
4. 连接后通过数据消息发送 `session.update`，等待服务端 `session.updated`，再打开媒体轨道。
5. 音频/视频通过媒体轨道传输，不需要像 WebSocket 那样发送 `input_audio_buffer.append`；字幕和模型文本通过下行数据消息接收。

官方 AOQ 示例的会话配置包括：

```json
{
  "type": "session.update",
  "session": {
    "modalities": ["text", "audio"],
    "turn_detection": {
      "type": "semantic_vad",
      "threshold": 0.5,
      "silence_duration_ms": 800
    }
  }
}
```

官方说明 `turn_detection` 可使用 `server_vad` 或 `semantic_vad`；使用 Qwen3.5 Omni-Realtime 时推荐 `semantic_vad`。当模型产生新一轮对话时，AOQ SDK 与百炼模型支持打断上一轮；SDK 还提供本地播放器打断接口 `interruptAudioPlayer`。

来源：[通过 AOQ 使用 qwen3.5-omni-plus-realtime 实现实时通话](https://help.aliyun.com/zh/model-studio/best-practice-aoq-omni-realtime)

### AOQ 对本项目的直接影响

- AOQ 可能改善原生 OnePlus 设备上的建连速度、弱网稳定性、回声消除和降噪，但不会自动修复客户端错误的事件编排、VAD 参数或状态机时序。
- AOQ 的音频轨道是 SDK 托管的实时媒体流。现有 React Native/JS WebRTC 音频录制、远端 WAV 拼接、播放器打断逻辑不能假设可以原样复用；需要原生模块桥接或采用官方 SDK 的轨道/回调。
- Token、workspace、地域和 SDK 二进制（Android AAR、Opus 插件）是新增运行时依赖。AOQ 官方 Android 示例要求 `minSdk 21`、麦克风/音频设置权限，并要求通过业务 AppServer 获取 Token。

来源：[AOQ 原生 SDK 接入示例](https://help.aliyun.com/zh/model-studio/best-practice-aoq-omni-realtime)

## 3. WebRTC 的官方行为边界

WebRTC 接入由 HTTP SDP 交换和 RTP 媒体通道组成：客户端 POST Offer SDP，服务端返回 Answer SDP，之后音频/视频通过 RTP 传输，文本事件通过 DataChannel 接收。官方文档强调 WebRTC 仅支持服务端 VAD（`server_vad` 或 `semantic_vad`），不支持 `manual` 模式。

官方 WebRTC 示例使用 `getUserMedia` 保持本地音频轨道，远端音频由 `ontrack` 播放；模型文本事件在 DataChannel 中接收。WebRTC 的音频不需要 `input_audio_buffer.append`。

来源：[通过 WebRTC 使用 qwen3.5-omni-plus-realtime 实现实时通话](https://help.aliyun.com/zh/model-studio/best-practice-webrtc-omni-realtime)

## 4. “让 Realtime 模型做状态语义分析”的评估

### 模型具备的能力

Qwen3.5 Omni-Realtime 是端到端实时多模态模型，可以同时理解流式音频和图像并输出文本、音频。官方模型说明还列出：

- 支持 Function Calling，可根据需要调用外部工具；
- 支持语义打断，识别对话意图，减少附和声和无意义背景音导致的打断；
- 支持文本输入、system instructions 和 `function_call_output`（具体输入方式取决于协议）。

来源：[Qwen-Omni-Realtime 模型文档](https://help.aliyun.com/zh/model-studio/realtime)

### 不应直接推导出的能力

官方文档没有承诺模型会自动输出本项目所需的、可靠且严格有序的场景状态事件，也没有把“状态机完成判定”定义为 Realtime API 的内建功能。Function Calling 只提供了可调用工具的机制；业务仍需定义：

- 工具名称和 JSON 参数 schema（例如 `advance_scene_state`）；
- 允许触发工具的阶段、必填字段和版本；
- 重复调用、乱序、模型漏调或误调时的幂等与恢复；
- 模型回复和工具调用的竞态处理；
- Realtime 断线、超时、上下文裁剪后的服务端兜底。

官方 Function Calling 的 Realtime 工作流页面明确写的是通过 DashScope SDK 或 WebSocket 原生协议传入工具定义，并接收 `response.function_call_arguments.done`，执行工具后再发送 `function_call_output` 和 `response.create`。该页面没有明确承诺 AOQ 或 WebRTC 对这套 Realtime Function Calling 流程提供同等封装。因此在切换 AOQ 前，不能把“模型支持 Function Calling”直接等同于“AOQ 客户端已经支持场景工具事件”；需要用实际 SDK/API 版本做 POC 验证。

因此可以让 Realtime 模型**提出结构化的状态观察结果**，但不建议让它成为唯一可信状态源，也不应让工具调用阻塞正常语音回复。推荐分层：Realtime 负责低延迟对话和可选的结构化观察事件；后端状态机负责权威校验、顺序应用和最终完成确认。

### 延迟上的现实限制

官方性能说明明确指出，端到端 Realtime 模型在单模型内串行完成语音识别、语义理解和语音生成，不能像 ASR + LLM + TTS 拼接方案那样独立并行优化。文档给出的特定 WebSocket 参数参考值为：Flash 总响应约 5.1 秒、Plus 约 5.8 秒；实际值随输入长度、网络和 VAD 参数变化。

这意味着把“场景状态分析”塞进同一次 Realtime 生成并不保证更快：它可能减少一次独立 LLM 请求，但也可能增加模型本轮推理复杂度，或让工具调用/结构化输出与语音回复互相竞争。是否更快必须用真实设备对比“用户停止说话 → 首个音频包”以及“状态完成信号到达”两个指标。

来源：[Qwen-Omni-Realtime 模型文档中的响应延迟与优化](https://help.aliyun.com/zh/model-studio/realtime)

## 5. 迁移建议（不改变 Web 端现有链路）

建议按风险由低到高推进：

1. **先修正现有 WebRTC 链路**：确认移动端使用准确的 `qwen3.5-omni-flash-realtime` 模型名、`semantic_vad`、`interrupt_response` 和持续上行音频；把状态机/WAV 从主对话链路中移出，先用真实设备建立基线。
2. **增加模型观察事件的实验分支**：通过 Function Calling 或明确的文本/JSON约定让 Realtime 提交“候选状态事件”，但事件进入后端队列，由后端状态机校验，不得直接结束会话。
3. **独立评估 AOQ**：仅在 Android 原生层做最小 Demo，验证建连、语音识别、语义 VAD、打断、字幕、音频播放和 Token 生命周期；不要同时重写场景状态机和评分逻辑。
4. **比较指标后再决定迁移**：至少记录首包延迟、VAD 误切句率、短词识别率、打断成功率、弱网恢复、回声污染、状态完成延迟和崩溃率。

AOQ 值得作为 OnePlus 原生端的候选方案，官方定位也比 WebRTC 更贴近移动端 AI 实时交互；但它不是解决“异步状态机落后”问题的单独开关。状态权威、对话主链、评分旁路三者仍需保持职责分离。

## 官方链接索引

- [Realtime API 概述（协议对比与模型支持矩阵）](https://help.aliyun.com/zh/model-studio/realtime-api-overview)
- [Qwen-Omni-Realtime 模型文档（能力、VAD、工具调用、延迟）](https://help.aliyun.com/zh/model-studio/realtime)
- [AOQ + qwen3.5-omni-plus-realtime 原生 SDK 示例](https://help.aliyun.com/zh/model-studio/best-practice-aoq-omni-realtime)
- [WebRTC + qwen3.5-omni-plus-realtime 浏览器示例](https://help.aliyun.com/zh/model-studio/best-practice-webrtc-omni-realtime)
- [Qwen-Omni-Realtime Function Calling（官方流程以 DashScope SDK/WebSocket 为例）](https://help.aliyun.com/zh/model-studio/qwen-function-calling#rt02realtime01)
