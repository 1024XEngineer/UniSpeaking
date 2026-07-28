public interface EvaluationService {

    /**
     * 对学习阶段的句子跟读录音进行评分。
     * 页面可以直接展示该总分。
     */
    SentenceEvaluationResponse evaluateSentence(
        SentenceEvaluationRequest request
    );

    /**
     * 对场景对话中的单轮用户回答进行评分。
     * 该结果内部保存，不直接展示给用户。
     */
    DialogueTurnEvaluationResponse evaluateDialogueTurn(
        DialogueTurnEvaluationRequest request
    );

    /**
     * 根据整场对话的各轮评分生成最终五维报告。
     */
    ConversationReportResponse generateConversationReport(
        ConversationReportRequest request
    );
}
AudioInput {
    byte[] audioData;
    String audioFormat;
}
SentenceEvaluationRequest {
    String userId;
    String contentId;
    AudioInput audio;
}
SentenceEvaluationResponse {
    String contentId;
    Integer totalScore;
}
DialogueTurnEvaluationRequest {
    String userId;
    String localSessionId;
    String turnId;
    AudioInput audio;
    String userText;
}
DialogueTurnEvaluationResponse {
    String turnId;
    Integer totalScore;
    Integer fluency;
    Integer pronunciation;
    Integer rhythm;
    Integer tone;
    Integer grammar;
    Integer vocabulary;
    Integer relevance;
}
ConversationReportRequest {
    String userId;
    String localSessionId;
}
ConversationReportResponse {
    String reportId;
    String localSessionId;
    Integer totalScore;
    FiveDimensionScore dimensionScore;
    String report;
}
FiveDimensionScore {
    Integer pronunciation;
    Integer fluency;
    Integer grammar;
    Integer vocabulary;
    Integer communication;
}