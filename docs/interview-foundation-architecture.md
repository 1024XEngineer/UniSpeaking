# Interview foundation 架构

## 1. 目的与范围

英文模拟面试是现有 Scene 业务的特化场景。用户围绕目标岗位完成固定五个主问题及有限
追问，在面试结束后获得完整录音和五维英语口语报告。系统不评价岗位匹配度、胜任度或
录用概率。

本阶段实现 Issue #33 定义、Issue #38 交付的后端闭环：

- 岗位名称、可选 JD 和可选简历输入；简历支持文本、文本型 PDF 和 DOCX。
- 本地 PaddleOCR 识别最多五张 JD 截图，创建时只接收用户确认后的最终 JD 文本。
- BASIC、STANDARD、CHALLENGE 三档难度，所有难度固定五个主问题。
- `ASR -> LLM -> TTS` 的分段式逐题问答，不使用 Realtime API。
- `submissionId` 幂等、十分钟进程内恢复、FULL/PARTIAL 五维报告和完整 MP3 录音。
- 历史、录音播放、FULL 趋势、快速复练和物理删除。

本阶段不实现前端 API 接入、时长选择、自动结束、用户回答字幕、DOC/图片/扫描版简历、
重启恢复、多实例共享、Redis、MQ、Outbox、SSE、业务 WebSocket、向量检索或第二套 Scene
Flow。原始简历、JD 截图、用户转写和分题回答音频不长期保存。

## 2. 模块与职责

代码继续位于现有模块：

```text
controller/InterviewController.java
service/scene/InterviewSceneService.java
service/scene/impl/InterviewSceneServiceImpl.java
domain/dto/scene/...
domain/vo/scene/...
domain/po/session/InterviewSession.java
component/session/ActiveSessionRegistry.java
infrastructure/persistence/{entity,mapper,repository}/scene/...
```

不得新增 `service/interview`、`domain/dto/interview`、Interview 私有 Provider、第二套
Session Service 或 `interview_session` 表。

职责边界：

- `InterviewController` 只做 HTTP 协议适配、认证入口和 DTO 转换，只依赖
  `InterviewSceneService`。
- `InterviewSceneService` 拥有材料准备、创建、逐题回答、追问裁决、恢复、结束、报告、
  完整录音和学习资产的完整用例编排。
- `SceneFlowService` 只管理外层 `DIALOGUE -> COMPLETED` 流程。题目和追问切换不推进
  Flow，成功或最终失败时释放 Flow。
- `SessionService` 只注册 Scene 特化创建的 Session、创建 `practice_session`、校验归属并
  写入 `COMPLETED`/`FAILED` 终态。不得编排 Interview 的 ASR、LLM、TTS、追问或报告。
- `EvaluationService.evaluateSpeech` 只提供无持久化副作用的语音评分，不查询 Scene 或
  Session，不保存转写和逐轮评分。
- ASR、LLM、TTS、评分、OCR、对象存储继续通过现有 Provider 或通用组件调用。

## 3. 运行态与持久化边界

`InterviewSession` 继承 `AbstractSceneSession`，由 `ActiveSessionRegistry` 管理。它只存在于
当前后端进程，组合：

- `InterviewQuestionPlan`：五个主问题和每题追问上限。
- `InterviewProgress`：当前主问题、追问次数、实际问题和结束原因。
- `InterviewTurn`：AI 问题音频、用户回答临时音频、临时转写和临时语音评分。
- 最近活动时间、当前 submission、结束请求和不足数据确认状态。

不建立 `interview_session`、`interview_turn` 或任务表。数据库继续使用三张 Interview 表和
`practice_session`：

- `interview` 保存身份、岗位摘要、难度和完成录音元数据；`completed_at IS NOT NULL` 才是
  可见资产。
- `interview_question` 只保存实际提出的 AI 问题。
- `interview_report` 保存 FULL/PARTIAL 的完整五维报告。
- `practice_session` 表达公共训练事实和最终状态，由公共 Session 能力唯一创建。

原始材料、用户转写、分题回答音频和逐轮评分仅在进程内或受控临时存储中存在，最终化或
失败后清理。

## 4. 材料与问题计划

输入约束：

- `jobTitle` 必填；最终 JD 文本不超过 5,000 字符。
- `resumeText` 与 `resumeFile` 互斥且都可不提供。
- 简历文件仅允许文本型 PDF 和 DOCX，单文件不超过 10 MB，PDF 不超过 10 页，最终提取
  文本不超过 20,000 字符。
- 不支持 DOC、Markdown 文件、图片简历、扫描件和扫描版 PDF。
- JD OCR 最多接收五张图片，合计不超过 10 MB；只返回识别文字，不创建 Interview，也不
  保存截图。

`TargetRoleSummary` 固定包含 `overview`、`responsibilities`、`requiredSkills`、
`qualificationRequirements` 四个字段。简历只临时影响问题计划，不进入岗位摘要。

所有难度固定五个主问题：

- BASIC：每个主问题最多一个澄清型追问。
- STANDARD：每个主问题最多一个原因、行动或结果型追问。
- CHALLENGE：每个主问题最多两个权衡、反思或复杂场景型追问。

LLM 每轮只可建议 `ASK_FOLLOW_UP` 或 `MOVE_TO_NEXT_MAIN`，最终由
`InterviewProgress` 强制执行追问上限。用户英语等级只影响措辞复杂度，不改变问题难度、
评分量尺或数据库结构。

## 5. 创建流程

```text
分配 interviewId 和 sessionId
-> 构造 InterviewSession
-> 校验并解析材料
-> 生成 role_summary
-> 生成五题计划
-> 生成第一题 TTS
-> 创建 interview
-> SceneFlowService.createFlow(interviewId)，初始阶段为 DIALOGUE
-> SessionService.registerSceneSession(session)
-> 返回第一题文本和 Base64 音频
```

`registerSceneSession` 是 `practice_session` 的唯一创建入口，并以一个可补偿的注册命令
完成 registry 注册和数据库创建：任一步骤失败都必须移除已注册运行态并删除已创建的
`practice_session`。创建失败时再按逆序释放 Flow、删除未完成 Interview 和清理临时材料。
创建流程不拆分 prepare/start，不创建 Realtime 连接。

## 6. 回答协议与幂等

```text
POST /api/interviews/{interviewId}/answers
Content-Type: multipart/form-data

questionNo
submissionId
audio
```

音频必须为 16 kHz、单声道、16-bit PCM WAV，单轮最长 210 秒。210 秒是适配默认 Qwen
ASR 7 MiB 输入上限的技术安全边界，不是面试结束原因，也不提供面试总时长选择。公共
`PcmWavValidator` 的默认五分钟能力保持兼容，Interview 通过参数化上限使用 210 秒。

回答接口在复制临时 WAV、原子预占 submission 并成功提交 executor 后返回
`202 Accepted`。状态机为：

```text
ACCEPTED -> PROCESSING -> COMPLETED
                         -> FAILED_RETRYABLE
                         -> FAILED_TERMINAL
```

幂等规则：

- 相同 `submissionId`、题号和音频摘要在 PROCESSING/COMPLETED 时返回已有状态，不重复
  推进问题。
- FAILED_RETRYABLE 可使用同一 ID 和摘要重试；优先复用原临时文件。
- FAILED_TERMINAL 返回原错误；当前题仍可继续时允许使用新的 submission ID 重新录制。
- 同一 ID 对应不同内容、当前题使用其他未完成 ID、旧题迟到提交均返回 409。
- 所有预占失败和 executor 拒绝都删除本次新临时文件并回滚未生效的 reservation。

处理顺序固定为：

```text
WAV 校验 -> ASR -> 有效英文词统计 -> (有效词 >= 4 时 evaluateSpeech，否则记录不可评分)
-> LLM 决定追问或下一主问题 -> InterviewProgress 裁决 -> 下一题 TTS
```

只有全部步骤成功才推进问题。API 和日志不得返回或记录用户转写、逐轮评分或 Provider 原始
响应。AI 问题文本和短音频可通过运行态 JSON 返回，音频字段使用 Base64 和明确 MIME。

## 7. 中断恢复

状态查询、心跳和业务请求都更新 `lastSeen`。非处理态会话失联后进入 `INTERRUPTED`；后端
每分钟扫描一次，十分钟内恢复时返回 ACTIVE，不重建 Flow，也不改变问题进度。

正在处理回答、等待已接收回答结束或最终化的 Session 不参与基于 lastSeen 的空闲清理，
但每个 submission 和 finalizer 都必须带有硬截止时间（从接收/开始最终化起不超过十分钟）。
超过截止时间时，watchdog 将卡死任务标记为 FAILED_RETRYABLE 或 FAILED_TERMINAL，删除其
临时数据并解除占用；随后由普通十分钟清理处理已无活动的 Session。这样 Provider 阻塞、
executor 丢失回调和 finalizer 停滞都不会永久占用运行态。

超过会话恢复窗口后：

- 运行态和 `practice_session` 标记 FAILED。
- 调用 `completeFlow(interviewId, false)`。
- 删除未完成 Interview 和临时数据。
- 移除运行态，不进入历史、训练时长或趋势。

不支持后端进程重启后的未完成面试恢复。

## 8. 结束与最低数据

单个回答至少四个有效英文词才参与评分；所有可评分回答合计至少二十个有效英文词才允许
形成资产。有效词统计复用 `EnglishWordCounter`。二十词资产门槛与公共三十秒训练时长
门槛互不替代。

```text
POST /api/interviews/{interviewId}/end
{ "confirmInsufficientData": false }
```

结束请求不调用会过早完成 `practice_session` 的通用 `endSession`。它只阻止新的回答预占，
并等待已经 ACCEPTED/PROCESSING 的回答结束；锁内只观察和切换状态，不阻塞 processor。

- 有 pending submission 时返回 `202 WAITING_FOR_SUBMISSIONS`。
- pending 完成后若不足二十词，运行态设置 `confirmationRequired=true`，恢复 ACTIVE 和新
  回答接收；`state` 返回实际词数和最低词数。
- 数据已确定不足且 `confirmInsufficientData=false` 时返回
  `409 INTERVIEW_DATA_CONFIRMATION_REQUIRED`，保持 ACTIVE 并更新 `lastSeen`。
- 确认结束后，运行态和 `practice_session` 标记 FAILED，释放 Flow，删除未完成 Interview
  和临时数据，不生成报告或录音。
- 数据足够的用户提前结束生成 PARTIAL；五题计划自然完成生成 FULL。

## 9. 五维报告

FULL 和 PARTIAL 都是字段完整、同量尺的报告。五个维度为：

- 流利度。
- 逻辑与连贯性。
- 语法控制。
- 发音可理解度。
- 词汇与面试表达。

流利度和发音复用无副作用语音评分结果，逻辑、语法和词汇由一次结构化 LLM 调用生成。
五维均为 0-100 分且等权，总分保留一位小数。不得复用包含其他维度或不等权规则的
`ConversationScoreCalculator`。

报告使用中文，包含每个维度的评价和行动建议；不逐字引用用户回答，不输出姓名、公司、
项目等身份实体，不评价岗位匹配、胜任度或录用概率。LLM 结构非法时只修复一次，再次
失败则最终化失败。

## 10. 完整录音与完成闸门

完整录音按实际问答顺序包含：

```text
AI 问题音频 -> 用户回答 -> 下一 AI 问题音频 -> 下一用户回答 -> ...
```

所有分段统一为 16 kHz 单声道 PCM，拼接后编码为 64 kbps MP3，通过现有
`ObjectStorageProvider` 上传确定性对象 Key。数据库不保存公开或长期签名 URL。

Interview 只有在完整报告和完整录音都成功后才算完成。成功顺序：

```text
确定 FULL/PARTIAL
-> 生成完整报告
-> 拼接并编码完整录音
-> 上传录音对象
-> 单个数据库事务：保存实际问题、报告、录音元数据、completed_at、practice_session COMPLETED
-> 事务提交后更新运行态
-> SceneFlowService.advanceStage(interviewId, DIALOGUE)
-> SceneFlowService.completeFlow(interviewId, true)
-> 清理临时数据和运行态
```

最终化按 `interviewId` 幂等。已提交资产不会因后续 Flow 清理失败降级：

- `advanceStage` 和 `completeFlow(true)` 都必须幂等。
- 提交后 Flow 操作失败时保留运行态中的 `flowCleanupPending`，以有界退避重试；重试任务
  只做 Flow 收敛，不修改已提交资产和 `practice_session`。
- 重试耗尽只记录脱敏告警，资产仍保持可见，后续状态查询不得把它降级为 FAILED。

报告、录音、对象上传或最终数据库事务任一最终化步骤失败时统一执行：

- 运行态和 `practice_session` 标记 FAILED。
- 不向 Interview 表写不存在的 FAILED 状态。
- 删除未完成 Interview、问题和报告。
- 回滚或删除已写入但未提交的数据库事实。
- 对已上传对象执行补偿删除；对象不存在视为幂等成功。
- `completeFlow(interviewId, false)` 并清理临时数据。
- 不产生可见学习资产。

## 11. 学习资产、趋势与删除

FULL 和 PARTIAL 都进入历史并可查看岗位摘要、实际 AI 问题、完整五维报告和完整录音。
只有 FULL 进入最近成绩和能力趋势，趋势按难度分组并动态计算。录音播放接口每次校验用户
归属并生成短期签名 URL。

快速复练创建新的 Interview，带入岗位名称、难度和岗位摘要；只读取用户指定来源的上一场
相同岗位 Interview 的实际问题作为避免重复提示。不查询全历史，不做向量或语义相似度
检索，不持久化 `sourceInterviewId`，也不承诺绝对不重复。

删除顺序：

```text
删除录音对象
-> 数据库事务删除 interview_report
-> 删除 interview_question
-> 删除 interview
```

对象不存在视为幂等成功。对象已删但数据库事务失败时，数据库 Interview 记录就是重试
依据；第二次请求继续数据库删除。删除不得删除或回写 `practice_session`，已产生的训练
时长不回退。

## 12. HTTP 与隐私约束

公开路由：

```text
POST   /api/interviews/job-description/ocr
POST   /api/interviews
POST   /api/interviews/{id}/answers
GET    /api/interviews/{id}/state
POST   /api/interviews/{id}/heartbeat
POST   /api/interviews/{id}/end
GET    /api/interviews
GET    /api/interviews/{id}
GET    /api/interviews/{id}/recording
GET    /api/interviews/trends?difficulty=...
POST   /api/interviews/{sourceId}/repractice
DELETE /api/interviews/{id}
```

主要状态映射：202 表示已接收异步工作；404 表示资源不存在或不归属；409 表示题号、提交
或确认冲突；422 表示可识别但无效的材料、音频或业务状态；502 表示同步外部依赖失败；
503 表示 executor 或服务暂不可用。异步失败通过 `state` 返回稳定业务错误码、可重试标志，
不改变已经结束的 HTTP 响应。

日志只记录关联 ID、题号、状态、耗时、文件类型和大小、脱敏错误码。禁止记录 JWT、简历
正文、JD 全文、Prompt、转写、音频 Base64、长期签名 URL、供应商请求或原始错误体。

## 13. 验收约束

- Controller 只依赖 `InterviewSceneService`。
- 五题计划、追问上限、submission 幂等和题号推进均有单元与并发测试。
- 210 秒音频边界测试不改变公共 300 秒行为。
- 状态查询、恢复、结束确认和十分钟清理使用 fake clock/executor/latch 测试。
- FULL/PARTIAL 五维完整性、等权总分和趋势口径有自动化测试。
- 报告或录音任一失败都不能形成可见资产。
- 对象删除成功但数据库失败后的第二次重试可以完成删除。
- 原始材料、用户转写和分题音频不入库、不进入公开 API 和日志。
- 所有 Java 测试放在 `backend/unispeaking-server/src/test/java` 对应包目录。
