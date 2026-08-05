# Interview foundation 架构

## 1. 目的与范围

英文模拟面试让已登录用户用简历、目标岗位说明和训练时长准备一个面试场景，完成实时
英语问答，并获得只针对本次口语表现的报告。

本文是 Interview foundation 的维护入口，只记录公共能力边界、接口语义、关键决策、
失败补偿和核心流程。Interview 与 IELTS 都是现有 Scene 的特化，不是顶层后端模块。

本阶段范围：

- 准备 `INTERVIEW_SCENE` 的材料快照、问题计划、流程定义和 Prompt。
- 复用公共 Scene Flow、Session、Evaluation 和 Provider 能力完成训练闭环。
- 明确幂等、授权、失败状态、可重试边界和敏感材料最小化规则。

本阶段不包含招聘决策、岗位匹配、录用建议、长期保存原始简历、计费权益、消息队列或
新的 AI/存储供应商。本文不承诺具体厂商，也不以空接口预留未确认能力。

## 2. 领域原语与职责边界

| 原语 | 核心职责 | 输入 | 输出 | 不负责 |
|---|---|---|---|---|
| Interview Scene | 把用户材料转成一次可训练的面试场景 | 当前用户、简历、岗位说明、时长 | 岗位快照、问题计划、流程定义、Prompt | 会话连接、逐轮消息、评分、招聘判断 |
| Scene Flow | 记录并校验场景当前步骤 | Scene、期望步骤、动作标识 | 当前步骤、下一步骤、是否完成 | 材料解析、问题生成、实时传输、评分 |
| Session | 记录一次用户训练事实及完整对话 | 当前用户、就绪 Scene、完整消息、结束原因 | Session ID、生命周期状态、对话记录 | 面试规则、问题选择、评分算法 |
| Evaluation | 从已授权训练事实生成口语反馈 | Session、完整对话、可选音频、评分上下文 | 单轮结果、整场报告或明确的处理中/失败状态 | 推进流程、改变会话状态、岗位胜任判断 |

变化方向测试：面试材料和问题策略会独立于 IELTS 题库演进，分别留在各自的 Scene
特化；会话授权、生命周期、消息完整性和口语评分语义需要跨 Scene 一致，保留为公共
原语。前端页面可以独立演进，但不据此拆出 `service/interview`。

## 3. 代码组织约束

```text
service/scene/InterviewSceneService.java
service/scene/impl/InterviewSceneServiceImpl.java
service/scene/SceneFlowService.java
service/session/SessionService.java
service/evaluation/EvaluationService.java
domain/dto/scene/...
domain/dto/session/...
domain/dto/evaluation/...
infrastructure/persistence/{entity,mapper,repository}/...
src/main/resources/db/migration/V{version}__{description}.sql
```

Interview 的 HTTP 入口可以使用独立业务路由和 `InterviewSceneController`，但内部仍按
上述边界编排。供应商 Adapter 继续位于 `infrastructure`，数据库访问继续通过公共
Repository；不得出现 Interview 私有 Mapper、Session、Evaluation 或 Provider 包。

兼容基线：复用现有 `SceneType.INTERVIEW_SCENE`、`interview_` ID 前缀、公共 Session
状态机和已有评分持久化；后续实现只为缺失的 Interview Scene 事实新增 Flyway 迁移，
不复制公共表，也不改写已发布迁移。

## 4. 接口契约

以下是稳定的业务语义，不固定尚未实现的 Java DTO 形状。

### 4.1 `InterviewSceneService`

| 操作 | 输入 | 输出 | 前置与后置条件 | 幂等与错误 |
|---|---|---|---|---|
| 准备面试场景 | 当前用户、PDF/DOC/DOCX 简历、岗位说明、10/15/20 分钟、幂等键 | `sceneId`、准备状态、材料摘要、问题数 | 用户已认证；文件不超过 10 MB；岗位说明 30–5000 字；成功后 Scene 类型为 `INTERVIEW_SCENE` 且归属当前用户 | 同一用户和幂等键返回同一结果；格式无效为 400，材料不可解析为 422，供应商暂时失败为 503 |
| 查询准备结果 | 当前用户、`sceneId` | 准备中、已就绪或失败及安全错误码 | 只返回当前用户的 Scene；已就绪结果包含可启动 Session 所需的稳定快照 | 不存在为 404，不归属为 403；不返回原始 Prompt 或简历全文 |

该 Service 可以编排材料提取、LLM Provider 和 Scene Repository，但不得创建 Session、
推进对话步骤或调用 Evaluation。

### 4.2 `SceneFlowService`

| 操作 | 输入 | 输出 | 契约 |
|---|---|---|---|
| 创建流程 | `sceneId`、Scene 特化给出的流程定义 | 初始步骤和流程版本 | Scene 已就绪；同一 Scene/版本重复调用不产生第二条流程 |
| 推进流程 | `sceneId`、期望当前步骤、动作标识 | 新的当前步骤、完成标志 | 比较并推进；动作标识去重；过期步骤返回 409 并附当前状态 |
| 完成流程 | `sceneId`、结束原因 | 完成状态 | 已完成时重复调用仍成功；不能倒退到历史步骤 |

Interview 的问题计划决定“下一题是什么”，`SceneFlowService` 只保证顺序、并发安全和
可恢复状态，不理解简历或岗位语义。

### 4.3 公共 `SessionService`

| 操作 | 输入 | 输出 | 契约 |
|---|---|---|---|
| 开始 Scene Session | 当前用户、已就绪 `sceneId`、模型/声音等会话选项、幂等键 | `sessionId`、`sceneType`、连接信息、状态 | 校验 Scene 归属并绑定 `INTERVIEW_SCENE`；公共状态机负责 `CREATED` 到 `ACTIVE` |
| 追加完整消息 | 当前用户、`sessionId`、轮次/消息标识、角色、完整文本 | 确认后的消息序号 | 只保存 final 消息，不保存流式 delta；同一消息标识重复提交不重复写入 |
| 结束 Session | 当前用户、`sessionId`、结束原因 | 最终状态和结束时间 | 先持久化结束事实，再触发可重试的报告生成；重复结束不改写首次有效结束时间 |
| 查询 Session | 当前用户、`sessionId` | 状态、去敏对话和报告状态 | 每次读取都校验归属；不得返回临时凭证、内部 Prompt 或原始供应商错误 |

公共 Session 是实时训练用例的编排者：可以协调 `SceneFlowService`，并在结束事实落库
后把报告任务交给 `EvaluationService`；它不决定面试下一题、不解析材料、不计算评分。
Controller 只做协议适配，不串联这三个 Service。暂停、恢复和打断的实时协议仍由客户
端 DataChannel 处理；只有影响业务事实的状态才持久化。

### 4.4 公共 `EvaluationService`

| 操作 | 输入 | 输出 | 契约 |
|---|---|---|---|
| 记录单轮评价 | 已授权 `sessionId`、轮次、完整文本、可选音频 | 单轮口语结果 | `(sessionId, turnNo, evaluatorVersion)` 幂等；不改变 Flow 或 Session 状态 |
| 生成整场报告 | 已完成 `sessionId`、完整对话、Interview 评分上下文 | 报告或处理中状态 | 可重复执行并覆盖同版本未完成结果；缺失维度明确标记，不以 0 分代替 |
| 查询评价 | 当前用户、`sessionId` | 完整、部分、处理中或失败 | 校验 Session 归属；报告只评价英语表达，不推断岗位匹配或录用概率 |

评分上下文由 Interview Scene 提供，算法与供应商实现归公共 Evaluation/Provider。

## 5. 关键 ADR

### ADR-001：Interview 与 IELTS 作为 Scene 特化

- 背景：两者有不同材料和流程规则，但都需要 Scene、Session、Evaluation 和 Provider。
- 决策：特化放在 `service/scene` 和 `domain/dto/scene`，不建立顶层 Interview/IELTS 包。
- 原因：业务差异留在变化更快的 Scene 策略，跨场景事实只维护一份。
- 代价：公共契约必须容纳不同流程，不能把自定义场景的固定学习阶段写死为全局规则。
- 替代：独立 Interview 模块会复制 Session/评分并形成双向依赖，因此不采用。

### ADR-002：结束事实与报告生成解耦

- 背景：外部评分可能超时，不能因此丢失已经完成的训练。
- 决策：Session 结束先持久化；报告按 Session 和评分版本幂等生成，可返回处理中、部分或
  失败状态。
- 原因：用户可以离开页面，重试不会重复会话或报告。
- 代价：读取方必须展示非终态，并需要后台重试或后续补偿入口。
- 替代：结束请求内强制同步生成报告会扩大超时和重复提交风险，因此不采用。

### ADR-003：原始面试材料最小化和短期保留

- 背景：简历与岗位说明包含个人和商业敏感信息。
- 决策：原始文件仅在受控临时存储中保留到提取完成或短 TTL 到期；业务库只保存训练所需
  的最小快照和对象 Key，不保存长期签名 URL，不在日志中记录全文。派生快照仍按敏感
  数据处理，不跨用户复用，并受明确保留期和删除策略约束。
- 原因：降低泄漏面，同时保留可重复训练所需的稳定业务上下文。
- 代价：失败排查依赖脱敏摘要、错误码和关联 ID，不能依赖原文日志。
- 替代：长期保存原始简历便于重跑但超出 foundation 的必要范围，因此不采用。

### ADR-004：数据库演进只认 Flyway 版本迁移

- 背景：项目已配置 `classpath:db/migration` 且关闭 Spring SQL init，多份 DDL 来源会
  造成执行顺序和环境状态不一致。
- 决策：版本化 DDL 只放 `src/main/resources/db/migration`；运维脚本不能替代或重复
  Flyway 迁移。
- 原因：所有环境共享可验证、可追踪的迁移历史。
- 代价：已发布迁移不可直接修改，修正必须新增后续版本。
- 替代：同时维护 `schema.sql` 或手工部署 DDL 容易漂移，因此不采用。

## 6. 失败补偿与隐私

| 失败点 | 已持久化事实 | 补偿与重试 | 用户可见状态 |
|---|---|---|---|
| 文件校验/提取失败 | 最多只有准备请求与脱敏错误码 | 删除临时对象；同一幂等键修正输入后需新请求 | 材料无效或无法解析 |
| 问题计划/Prompt 生成失败 | Scene 保持准备中或失败，不可启动 | 按退避策略重试；达到上限后标记失败并清理临时材料 | 准备失败，可重试 |
| 流程创建失败 | 已就绪 Scene 仍存在 | 对 Scene/流程版本幂等重放 | 暂不可开始 |
| 实时连接失败 | Session 已创建但未激活 | 将 Session 记为 `FAILED` 或按策略重连；临时凭证自然过期 | 连接失败，可重新开始 |
| 消息重复/乱序 | 已确认的完整消息 | 按消息标识去重；缺口保持待补，不猜测文本 | 对话同步中 |
| 结束时评分失败 | Session 已完成、结束时间不可回退 | 按 Session/评分版本重试；保留已有单轮结果 | 报告处理中、部分或失败 |
| 临时材料删除失败 | 仅保存对象 Key 和清理状态 | 有界重试并告警；下载链接保持短 TTL | 不暴露基础设施细节 |

所有入口从认证上下文取得 `userId`，对 `sceneId`、`sessionId`、报告和对象 Key 逐次校验
归属。日志只记录关联 ID、状态、耗时、文件类型/大小和脱敏错误码；禁止记录简历正文、
岗位说明全文、Prompt、JWT、临时凭证、完整 SDP、音频 Base64 或供应商原始响应。材料
摘要和问题计划也按敏感数据授权与清理，不得用于其他用户或模型训练。

## 7. 核心场景伪代码

### 7.1 准备面试 Scene

```text
准备面试场景(当前用户, 简历, 岗位说明, 时长, 幂等键):
    校验用户、文件类型与大小、岗位说明长度、允许时长
    如果存在同用户同幂等键的请求:
        返回已有准备结果

    临时对象 = 安全保存简历(短期保留)
    尝试:
        材料摘要 = 提取训练所需最小信息(临时对象, 岗位说明)
        问题计划 = 生成面试问题计划(材料摘要, 时长)
        场景 = 保存 Interview Scene(当前用户, 材料摘要, 问题计划)
        创建流程(场景, 问题计划的流程定义)
        标记场景已就绪
        安排删除临时对象
        返回场景标识和已就绪状态
    失败:
        标记准备失败并保存安全错误码
        安排删除临时对象
        返回可重试或需修正材料的状态
```

### 7.2 公共 Session 完成面试并交付报告

```text
公共 Session 完成面试(当前用户, 场景标识, 会话标识, 结束原因):
    校验场景和会话都归属当前用户且相互绑定
    幂等完成 Scene Flow
    幂等结束公共 Session 并固定结束时间

    尝试:
        完整对话 = 读取已确认的 final 消息
        评分上下文 = 读取 Interview Scene 的评分快照
        报告 = 幂等生成口语报告(会话标识, 完整对话, 评分上下文)
        返回已完成报告
    暂时失败:
        保留已完成 Session 和已有单轮结果
        安排按会话和评分版本重试
        返回报告处理中或部分结果
    永久失败:
        保存安全错误码，不覆盖已有结果
        返回报告失败并允许用户查看完整对话
```

## 8. 实现验收约束

- 任何 Interview 生产代码都落在现有 Scene、Session、Evaluation、Provider 或
  Infrastructure 边界内，没有顶层 `service/interview`、`domain/dto/interview`。
- 并发创建、推进、消息追加、结束和报告生成都有业务幂等键及冲突测试。
- 认证、资源归属、文件限制、短 TTL、日志脱敏和删除补偿有自动化测试。
- 新 DDL 只通过新的 Flyway 版本迁移交付；不修改已发布迁移，不提交 `schema.sql` 副本。
- 单元、容器集成和覆盖率门禁遵循 `CLAUDE.md`；本文档本身不替代实现测试。
