package com.unispeaking.provider;

import com.unispeaking.domain.dto.ai.AudioTranscriptionRequest;
import com.unispeaking.domain.dto.ai.AudioTranscriptionResponse;
import com.unispeaking.domain.dto.ai.LlmTaskRequest;
import com.unispeaking.domain.dto.ai.LlmTaskResponse;
import com.unispeaking.domain.dto.ai.PronunciationEvaluationRequest;
import com.unispeaking.domain.dto.ai.PronunciationEvaluationResponse;
import com.unispeaking.domain.dto.ai.RealtimeSdpExchangeRequest;
import com.unispeaking.domain.dto.ai.RealtimeSdpExchangeResponse;
import com.unispeaking.domain.dto.ai.SpeechAudioRequest;
import com.unispeaking.domain.dto.ai.SpeechAudioResponse;
import com.unispeaking.domain.vo.ai.AiCapability;
import com.unispeaking.domain.vo.realtime.ProviderType;
import java.util.Set;

public interface AiProvider {

	ProviderType type();

	AiCapability capability();

	Set<String> supportedModels();

	RealtimeSdpExchangeResponse exchangeRealtimeSdp(RealtimeSdpExchangeRequest request);

	SpeechAudioResponse generateSpeechAudio(SpeechAudioRequest request);

	LlmTaskResponse executeLlmTask(LlmTaskRequest request);

	AudioTranscriptionResponse convertAudioToText(AudioTranscriptionRequest request);

	PronunciationEvaluationResponse evaluatePronunciation(
			PronunciationEvaluationRequest request);
}
