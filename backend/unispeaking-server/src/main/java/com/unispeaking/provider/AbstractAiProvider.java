package com.unispeaking.provider;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.vo.provider.AiCapability;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractAiProvider implements AiProvider {

	public AiProviderResponse<String> executeLlmTaskMeasured(String prompt, String token) {
		String response = executeLlmTask(prompt, token);
		return new AiProviderResponse<>(response, null, ProviderUsage.estimatedText(prompt, response));
	}

	public AiProviderResponse<byte[]> generateSpeechAudioMeasured(String text, String token) {
		byte[] response = generateSpeechAudio(text, token);
		return new AiProviderResponse<>(response, null, ProviderUsage.tts(text, response));
	}

	public AiProviderResponse<byte[]> generateSpeechAudioMeasured(
			String text,
			String token,
			String voice) {
		byte[] response = generateSpeechAudio(text, token, voice);
		return new AiProviderResponse<>(response, null, ProviderUsage.tts(text, response));
	}

	public byte[] generateSpeechAudio(String text, String token, String voice) {
		return generateSpeechAudio(text, token);
	}

	public AiProviderResponse<String> convertAudioToTextMeasured(byte[] audio, String token) {
		String response = convertAudioToText(audio, token);
		return new AiProviderResponse<>(response, null, ProviderUsage.audioInput(audio, response));
	}

	public AiProviderResponse<String> evaluatePronunciationMeasured(
			String text, byte[] audio, String token) {
		String response = evaluatePronunciation(text, audio, token);
		return new AiProviderResponse<>(response, null, ProviderUsage.scoring(text, audio));
	}

	@Override
	public String exchangeRealtimeSdp(String offerSdp, String token) {
		throw unsupported("Realtime");
	}

	@Override
	public byte[] generateSpeechAudio(String text, String token) {
		throw unsupported("TTS");
	}

	@Override
	public String executeLlmTask(String prompt, String token) {
		throw unsupported("LLM");
	}

	@Override
	public String convertAudioToText(byte[] audio, String token) {
		throw unsupported("ASR");
	}

	@Override
	public String evaluatePronunciation(
			String text,
			byte[] audio,
			String token) {
		throw unsupported("pronunciation scoring");
	}

	private final String providerId;
	private final Set<String> supportedModels;

	protected AbstractAiProvider(String providerId, Set<String> supportedModels) {
		this.providerId = normalizeProviderId(providerId);
		if (this.providerId.isBlank()) {
			throw new IllegalArgumentException("AI provider ID is required");
		}
		this.supportedModels = supportedModels.stream()
				.map(AbstractAiProvider::normalizeModelId)
				.collect(Collectors.toUnmodifiableSet());
	}

	public final String providerId() {
		return providerId;
	}

	public final Set<String> supportedModels() {
		return supportedModels;
	}

	public final boolean supports(String modelId) {
		return supportedModels.contains(normalizeModelId(modelId));
	}

	protected final BusinessException capabilityNotConfigured(String modelId) {
		return retryableFailure(
				"AI_PROVIDER_CAPABILITY_NOT_CONFIGURED",
				providerId() + " provider is registered for " + modelId
						+ " but its " + capability() + " API is not configured");
	}

	protected static final BusinessException retryableFailure(String code, String message) {
		return new ClassifiedProviderException(code, message, true);
	}

	/**
	 * Marks a request or interruption failure that must not be sent to another
	 * provider. Provider/network/configuration failures use
	 * {@link #retryableFailure(String, String)} instead.
	 */
	protected static final BusinessException nonRetryableFailure(String code, String message) {
		return new ClassifiedProviderException(code, message, false);
	}

	public abstract AiCapability capability();

	protected static byte[] requireAudio(byte[] audio, String operationName) {
		if (audio == null || audio.length == 0) {
			throw nonRetryableFailure(
					"INVALID_AUDIO",
					operationName + " WAV audio is required");
		}
		return audio;
	}

	static Boolean retryable(BusinessException exception) {
		if (exception instanceof ClassifiedProviderException classified) {
			return classified.retryable();
		}
		return null;
	}

	static String normalizeModelId(String modelId) {
		return modelId == null ? "" : modelId.trim().toLowerCase(Locale.ROOT);
	}

	private static String normalizeProviderId(String providerId) {
		return providerId == null ? "" : providerId.trim().toLowerCase(Locale.ROOT);
	}

	private UnsupportedOperationException unsupported(String operation) {
		return new UnsupportedOperationException(
				getClass().getSimpleName() + " does not support " + operation);
	}

	private static final class ClassifiedProviderException extends BusinessException {

		private final boolean retryable;

		private ClassifiedProviderException(String code, String message, boolean retryable) {
			super(code, message);
			this.retryable = retryable;
		}

		private boolean retryable() {
			return retryable;
		}
	}
}
