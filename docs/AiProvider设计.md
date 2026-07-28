package com.unispeaking.provider;

public interface AiProvider {
    ProviderType type();
    AiCapability capability();
    Set<String> supportedModels();
    RealtimeSdpExchangeResponse exchangeRealtimeSdp(
        RealtimeSdpExchangeRequest request
    );
    SpeechAudioResponse generateSpeechAudio(SpeechAudioRequest request);
    LlmTaskResponse executeLlmTask(LlmTaskRequest request);
    AudioTranscriptionResponse convertAudioToText(
        AudioTranscriptionRequest request
    );
    PronunciationEvaluationResponse evaluatePronunciation(
        PronunciationEvaluationRequest request
    );
}

public abstract class AbstractAiProvider implements AiProvider {
    // 保存 providerType、supportedModels，并提供不支持能力的统一错误。
}
public abstract class RealtimeProvider extends AbstractAiProvider {
    RealtimeSdpExchangeResponse exchangeRealtimeSdp(
        RealtimeSdpExchangeRequest request
    );
}
public abstract class LlmProvider extends AbstractAiProvider {
    LlmTaskResponse executeLlmTask(LlmTaskRequest request);
}
public abstract class ScoringProvider extends AbstractAiProvider {
    PronunciationEvaluationResponse evaluatePronunciation(
        PronunciationEvaluationRequest request
    );
}
public abstract class TtsProvider extends AbstractAiProvider {
    SpeechAudioResponse generateSpeechAudio(SpeechAudioRequest request);
}
public abstract class TranscriptionProvider extends AbstractAiProvider {
    AudioTranscriptionResponse convertAudioToText(
        AudioTranscriptionRequest request
    );
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
    String model;
    String offerSdp;
    String apiKey;
}
RealtimeSdpExchangeResponse {
    String answerSdp;
    String aiCallId;
}
SpeechAudioRequest {
    AiCallContext context;
    String text;
}
SpeechAudioResponse {
    byte[] audioData;
    String audioFormat;
    String contentType;
}
LlmTaskRequest {
    AiCallContext context;
    String prompt;
}
LlmTaskResponse {
    JsonNode data;
}
AudioTranscriptionRequest {
    AiCallContext context;
    AudioInput audio;
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
