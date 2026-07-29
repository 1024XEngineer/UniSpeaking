
public interface SceneService {

    /**
     * 根据场景类型和用户输入生成场景内容。
     */
    SceneGenerationResponse generateScene(
        SceneGenerationRequest request
    );
}
enum SceneType {
    FREE_CHAT,
    CUSTOM_SCENE,
    INTERVIEW_SCENE,
    IELTS_SCENE
}
LearningContentItem {
    String contentId;
    String englishText;
    String chineseText;
    String phonetic;
}
SceneGenerationRequest {
    String userId;
    String userPreference;
    SceneType sceneType;
    String sceneInput;
}
SceneGenerationResponse {
    String sceneId;
    List<LearningContentItem> wordList;
    List<LearningContentItem> phraseList;
    List<LearningContentItem> sentenceList;
    String scenePrompt;
}

public interface SceneFlowService {
    // 为已经生成完成的场景创建流程。
    SceneFlowResponse createFlow(
        String sceneId
    );
    // 完成当前阶段并进入下一阶段。
    SceneFlowResponse advanceStage(SceneFlowStage stage);

    void completeFlow(Boolean completed);

    // 获取当前阶段的学习内容。
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

package com.unispeaking.session;

public interface SessionService {

    /**
     * 开始一次业务会话，生成 sessionId 并记录开始时间。
     */
    StartSessionResponse startSession(String prompt);

    /**
     * 向当前会话中追加一条用户或 AI 的完整消息。
     *
     * 只保存最终完整文本，不保存流式 delta。
     * 消息可以先追加到内存，再异步写入数据库。
     */
    void addMessage(Message message);

    /**
     * 结束当前业务会话，记录结束时间。
     */
    void endSession(String sessionId, String stopTime);
}
StartSessionResponse {
    String sessionId;
    String startTime;
}
Message {
    Integer owner;    // 1：用户，0：模型
    String content;   // 用户或模型的完整文本
    byte[] audio;     // 可选音频，模型消息通常为空
}

自由聊天的 `addMessage(Message)` 将用户和 AI 的最终文本按顺序暂存到
Redis List；音频和流式 delta 不进入 Redis，消息通过 TTL 自动过期。

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
    String content;
    AudioInput audio;
}
SentenceEvaluationResponse {
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
    String userId;
    String localSessionId;
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

package com.unispeaking.provider;

public abstract class AiProvider {
    ProviderType type();
    AiCapability capability();
    Set<String> supportedModels();
}
public abstract class RealtimeProvider extends AiProvider {
    RealtimeSdpExchangeResponse exchangeRealtimeSdp(
        RealtimeSdpExchangeRequest request
    );
}
public abstract class LlmProvider extends AiProvider {
    LlmTaskResponse executeLlmTask(LlmTaskRequest request);
}
public abstract class ScoringProvider extends AiProvider {
    PronunciationEvaluationResponse evaluatePronunciation(
        PronunciationEvaluationRequest request
    );
}
public abstract class TtsProvider extends AiProvider {
    SpeechAudioResponse generateSpeechAudio(SpeechAudioRequest request);
}

public class QwenRealtimeProvider extends RealtimeProvider {}
public class QwenLlmProvider extends LlmProvider {}
public class DeepSeekLlmProvider extends LlmProvider {}
public class IflytekScoringProvider extends ScoringProvider {}
public class AliyunTtsProvider extends TtsProvider {}
public class MiniMaxTtsProvider extends TtsProvider {}

public class AiProviderRegistry {
    RealtimeProvider getRealtimeProvider(String modelId);
    LlmProvider getLlmProvider(String modelId);
    ScoringProvider getScoringProvider(String modelId);
    TtsProvider getTtsProvider(String modelId);
}

AiCallContext {
    String userId;
    String businessId;
    <!-- 
    Realtime：businessId = localSessionId
    场景生成：businessId = sceneId
    文本评分：businessId = evaluationId
    音素评分：businessId = practiceId 
    -->
}
AudioInput {
    byte[] audioData;
    String audioFormat;
}
RealtimeSdpExchangeRequest {
    AiCallContext context;
    String offerSdp;
    String apiKey;
}
RealtimeSdpExchangeResponse {
    String answerSdp;
}
SpeechAudioRequest {
    AiCallContext context;
    String text;
    String apiKey;
}
SpeechAudioResponse {
    byte[] audioData;
    String audioFormat;
    String contentType;
}
LlmTaskRequest {
    AiCallContext context;
    String prompt;
    String apiKey;
}
LlmTaskResponse {
    JsonNode data;
}
AudioTranscriptionRequest {
    AiCallContext context;
    AudioInput audio;
    String apiKey;
}
AudioTranscriptionResponse {
    String text;
}
PronunciationEvaluationRequest {
    AiCallContext context;
    AudioInput audio;
    String referenceText;
}
PronunciationEvaluationResponse {
    Integer totalScore;
    Integer fluency；
    Integer pronunciation;
    Integer rhythm;
    Integer tone;

}


【场景生成】

SceneService.generateScene(
    userId = "user_1001",
    userPreference = "英语基础一般，喜欢慢速对话",
    sceneType = CUSTOM_SCENE,
    sceneInput = "咖啡店点单"
)
    └── AiProviderRegistry.getLlmProvider("qwen3.5-plus").executeLlmTask(
            context.userId = "user_1001",
            context.businessId = "custom_2001",
            prompt = "生成咖啡店点单的单词、词组、句子和对话Prompt"
        )
        → LlmTaskResponse {
            data = { wordList, phraseList, sentenceList, scenePrompt }
        }

→ SceneGenerationResponse {
    sceneId = "custom_2001",
    wordList,
    phraseList,
    sentenceList,
    scenePrompt = "你是一名咖啡店店员……"
}


【创建场景流程】

SceneFlowService.createFlow(
    sceneId = "custom_2001"
)
→ SceneFlowResponse {
    sceneId = "custom_2001",
    stage = WORD_LEARNING,
    completed = false
}


【单词学习】

AiProviderRegistry.getTtsProvider("aliyun-tts").generateSpeechAudio(
    context.userId = "user_1001",
    context.businessId = "custom_2001",
    text = "coffee"
)
→ SpeechAudioResponse {
    audioData = "单词音频",
    audioFormat = "mp3"
}

SceneFlowService.advanceStage(
    stage = WORD_LEARNING
)
→ SceneFlowResponse {
    sceneId = "custom_2001",
    stage = PHRASE_LEARNING,
    completed = false
}


【词组学习】

AiProviderRegistry.getTtsProvider("aliyun-tts").generateSpeechAudio(
    context.userId = "user_1001",
    context.businessId = "custom_2001",
    text = "a cup of coffee"
)
→ SpeechAudioResponse {
    audioData = "词组音频",
    audioFormat = "mp3"
}

SceneFlowService.advanceStage(
    stage = PHRASE_LEARNING
)
→ SceneFlowResponse {
    sceneId = "custom_2001",
    stage = SENTENCE_LEARNING,
    completed = false
}


【句子学习】

AiProviderRegistry.getTtsProvider("aliyun-tts").generateSpeechAudio(
    context.userId = "user_1001",
    context.businessId = "custom_2001",
    text = "Could I have a cup of coffee?"
)
→ SpeechAudioResponse {
    audioData = "标准句子音频",
    audioFormat = "mp3"
}

EvaluationService.evaluateSentence(
    userId = "user_1001",
    content = "Could I have a cup of coffee?",
    audio.audioData = "用户跟读音频",
    audio.audioFormat = "webm"
)
    └── AiProviderRegistry.getScoringProvider(
            "iflytek-pronunciation-evaluation"
        ).evaluatePronunciation(
            context.businessId = "practice_4001",
            audio.audioData = "用户跟读音频",
            referenceText = "Could I have a cup of coffee?"
        )
        → PronunciationEvaluationResponse {
            totalScore = 86
        }

→ SentenceEvaluationResponse {
    contentId = "sentence_001",
    totalScore = 86
}

SceneFlowService.advanceStage(
    stage = SENTENCE_LEARNING
)
→ SceneFlowResponse {
    sceneId = "custom_2001",
    stage = DIALOGUE,
    completed = false
}


【开始场景会话】

SessionService.startSession(
    "你是一名咖啡店店员……"
)
→ StartSessionResponse {
    sessionId = "session_5001",
    startTime = "2026-07-24 10:30:00"
}

AiProviderRegistry.getRealtimeProvider(
    "qwen3.5-omni-flash-realtime"
).exchangeRealtimeSdp(
    context.userId = "user_1001",
    context.businessId = "session_5001",
    offerSdp = "客户端Offer SDP",
    apiKey = "Realtime临时API Key"
)
→ RealtimeSdpExchangeResponse {
    answerSdp = "模型Answer SDP",
}


【保存对话内容】

SessionService.addMessage(
    owner = 0,
    content = "What would you like to order?",
    audio = null
)
→ void

SessionService.addMessage(
    owner = 1,
    content = "I would like a cup of coffee.",
    audio = "用户本轮音频"
)
→ void


【单轮对话评分】

EvaluationService.evaluateDialogueTurn(
    userId = "user_1001",
    localSessionId = "session_5001",
    turnId = "turn_001",
    audio.audioData = "用户本轮音频",
    userText = "I would like a cup of coffee."
)
    ├── AiProviderRegistry.getScoringProvider(
            "iflytek-pronunciation-evaluation"
        ).evaluatePronunciation(
            context.businessId = "evaluation_7001",
            audio.audioData = "用户本轮音频",
            referenceText = "I would like a cup of coffee."
        )
    └── AiProviderRegistry.getLlmProvider("qwen3.5-plus").executeLlmTask(
            context.businessId = "evaluation_7001",
            prompt = "根据AI问题和用户回答评估语法、词汇和相关性"
        )

→ DialogueTurnEvaluationResponse {
    userId = "user_1001",
    localSessionId = "session_5001",
    turnId = "turn_001",
    totalScore = 84,
    fluency = 83,
    pronunciation = 86,
    grammar = 88,
    vocabulary = 82,
    relevance = 90
}


【结束会话】

SessionService.endSession(
    sessionId = "session_5001",
    stopTime = "2026-07-24T10:42:00Z"
)
→ void


【生成最终报告】

EvaluationService.generateConversationReport(
    userId = "user_1001",
    localSessionId = "session_5001"
)
    ├── 获取每轮评分 {
            turn_001 = { totalScore = 84, fluency = 83, pronunciation = 86,
                         grammar = 88, vocabulary = 82, relevance = 90 },
            turn_002 = { totalScore = 80, fluency = 78, pronunciation = 82,
                         grammar = 84, vocabulary = 76, relevance = 85 }
        }

    └── AiProviderRegistry.getLlmProvider("qwen3.5-plus").executeLlmTask(
            context.userId = "user_1001",
            context.businessId = "session_5001",
            prompt = "根据以上每轮对话评分生成总分、五维评分和改进报告"
        )
        → LlmTaskResponse {
            data = {
                totalScore = 82,
                pronunciation = 84,
                fluency = 81,
                grammar = 86,
                vocabulary = 79,
                communication = 87,
                report = "用户能够完成咖啡店点单交流……"
            }
        }

→ ConversationReportResponse {
    reportId = "report_8001",
    localSessionId = "session_5001",
    totalScore = 82,
    dimensionScore = {
        pronunciation = 84,
        fluency = 81,
        grammar = 86,
        vocabulary = 79,
        communication = 87
    },
    report = "用户能够完成咖啡店点单交流……"
}


【完成流程】

SceneFlowService.completeFlow(
    completed = true
)
→ void
