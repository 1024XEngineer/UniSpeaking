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
import com.unispeaking.domain.vo.realtime.ProviderType;
import com.unispeaking.exception.BusinessException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractAiProvider implements AiProvider {

	private final ProviderType providerType;
	private final Set<String> supportedModels;

	protected AbstractAiProvider(ProviderType providerType, Set<String> supportedModels) {
		this.providerType = providerType;
		this.supportedModels = supportedModels.stream()
				.map(AbstractAiProvider::normalizeModelId)
				.collect(Collectors.toUnmodifiableSet());
	}

	@Override
	public final ProviderType type() {
		return providerType;
	}

	@Override
	public final Set<String> supportedModels() {
		return supportedModels;
	}

	public final boolean supports(String modelId) {
		return supportedModels.contains(normalizeModelId(modelId));
	}

	@Override
	public RealtimeSdpExchangeResponse exchangeRealtimeSdp(RealtimeSdpExchangeRequest request) {
		throw unsupportedCapability("realtime SDP exchange");
	}

	@Override
	public SpeechAudioResponse generateSpeechAudio(SpeechAudioRequest request) {
		throw unsupportedCapability("speech synthesis");
	}

	@Override
	public LlmTaskResponse executeLlmTask(LlmTaskRequest request) {
		throw unsupportedCapability("LLM task execution");
	}

	@Override
	public AudioTranscriptionResponse convertAudioToText(AudioTranscriptionRequest request) {
		throw unsupportedCapability("audio transcription");
	}

	@Override
	public PronunciationEvaluationResponse evaluatePronunciation(
			PronunciationEvaluationRequest request) {
		throw unsupportedCapability("pronunciation evaluation");
	}

	protected final BusinessException capabilityNotConfigured(String modelId) {
		return new BusinessException(
				"AI_PROVIDER_CAPABILITY_NOT_CONFIGURED",
				type() + " provider is registered for " + modelId
						+ " but its " + capability() + " API is not configured");
	}

	private BusinessException unsupportedCapability(String operation) {
		return new BusinessException(
				"AI_PROVIDER_CAPABILITY_NOT_SUPPORTED",
				getClass().getSimpleName() + " does not support " + operation);
	}

	static String normalizeModelId(String modelId) {
		return modelId == null ? "" : modelId.trim().toLowerCase(Locale.ROOT);
	}
}
