
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
    SentenceEvaluationResponse evaluateSentenceReading(
        String sentenceId, byte[] audio
    );
    DialogueTurnEvaluationResult evaluateDialogueTurn(
        DialogueTurnEvaluationCommand command
    );
    DialogueReportResult generateDialogueReport(
        String sessionId, List<Message> dialogue
    );
    DialogueEvaluationResult getDialogueEvaluation(String sessionId);
}

整场报告仅包含 accuracy、fluency、grammar、vocabulary、naturalness 和
finalScore 六个分数。详细字段和公式以 `docs/EvaluationService设计.md` 为准。

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

EvaluationService.evaluateSentenceReading(
    sentenceId = "sentence UUID",
    audio = "用户 16 kHz 单声道 PCM WAV 跟读音频"
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
    overallScore = 86.0,
    passed = true,
    words = [...]
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
    sessionId = "session_5001",
    turnNo = 1,
    audio = "用户本轮 PCM WAV 音频",
    transcript = "I would like a cup of coffee."
)
→ DialogueTurnEvaluationResult {
    turnNo = 1,
    transcript = "I would like a cup of coffee.",
    overallScore = 84.0,
    rhythmScore = 82.0,
    toneScore = 80.0,
    integrityScore = 100.0,
    pronunciationScore = 86.0,
    fluencyScore = 83.0,
    feedbackSummary = "表达清楚，用词自然。",
    suggestedExpression = "I’d like a cup of coffee, please.",
    words = [...]
}


【结束会话】

SessionService.endSession(
    sessionId = "session_5001",
    stopTime = "2026-07-24T10:42:00Z"
)
→ void


【生成最终报告】

EvaluationService.generateDialogueReport(
    sessionId = "session_5001",
    dialogue = [
        { owner = 0, content = "What would you like to order?" },
        { owner = 1, content = "I would like a cup of coffee." }
    ]
)
→ DialogueReportResult {
    accuracyScore = 84.0,
    fluencyScore = 81.0,
    grammarScore = 86.0,
    vocabularyScore = 79.0,
    naturalnessScore = 83.0,
    finalScore = 83.0,
    summary = "用户能够完成咖啡店点单交流……",
    strengths = [...],
    improvements = [...]
}


【完成流程】

SceneFlowService.completeFlow(
    completed = true
)
→ void
