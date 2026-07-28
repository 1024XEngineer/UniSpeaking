public interface SceneFlowService {
    // 为已经生成完成的场景创建流程。
    SceneFlowResponse createFlow(
        CreateSceneFlowRequest request
    );
    // 完成当前阶段并进入下一阶段。
    SceneFlowResponse advanceStage(
        AdvanceSceneStageRequest request
    );

    SceneFlowResponse getFlow(
        String flowId
    );

    void completeFlow(
        CompleteSceneFlowRequest request
    );
}
enum SceneFlowStage {
    WORD_LEARNING,
    PHRASE_LEARNING,
    SENTENCE_LEARNING,
    DIALOGUE,
    COMPLETED
}
CreateSceneFlowRequest {
    String userId;
    String sceneId;
}
SceneFlowResponse {
    String flowId;
    String sceneId;
    SceneFlowStage currentStage;
    Boolean completed;
}
AdvanceSceneStageRequest {
    String userId;
    String flowId;
}
CompleteSceneFlowRequest {
    String userId;
    String flowId;
}