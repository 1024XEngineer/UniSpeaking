public interface SceneFlowService {
    // 为已经生成完成的场景创建流程。
    SceneFlowResponse createFlow(
        String sceneId
    );
    // 完成当前阶段并进入下一阶段。
    SceneFlowResponse advanceStage(SceneFlowStage stage);

    void completeFlow(Boolean completed);

    List<LearningContentItem> getByCurrentStage(SceneFlowStage stage);
}
enum SceneFlowStage {
    WORD_LEARNING,
    PHRASE_LEARNING,
    SENTENCE_LEARNING,
    DIALOGUE,
    COMPLETED
}
SceneFlowResponse {
    String sceneId;
    SceneFlowStage stage;
    Boolean completed;
}

自由聊天创建 Flow 时直接进入 `DIALOGUE`。自定义场景、面试和 IELTS
场景从 `WORD_LEARNING` 开始，并通过 `getByCurrentStage(...)` 读取
SceneService 已保存的单词、短语或句子。每个 `SceneFlowService` 实例只
维护一个场景流程；外层 `SceneFlowServiceSelector` 使用 `sceneId` 管理并发实例，
不会把路由字段扩散到 Service 契约。创建流程时直接解析 `sceneId` 前缀：
`freechat_` 从 `DIALOGUE` 开始，其余已注册场景前缀从
`WORD_LEARNING` 开始；不再通过学习内容是否为空推断场景类型。
