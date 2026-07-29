package com.unispeaking.provider;

import com.unispeaking.exception.BusinessException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractAiProvider implements AiProvider {

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

	@Override
	public final String providerId() {
		return providerId;
	}

	@Override
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

	protected static byte[] unboxAudio(Byte[] audio, String operationName) {
		if (audio == null || audio.length == 0) {
			throw nonRetryableFailure(
					"INVALID_AUDIO",
					operationName + " WAV audio is required");
		}
		byte[] bytes = new byte[audio.length];
		for (int index = 0; index < audio.length; index++) {
			if (audio[index] == null) {
				throw nonRetryableFailure(
						"INVALID_AUDIO",
						operationName + " WAV audio contains a null byte");
			}
			bytes[index] = audio[index];
		}
		return bytes;
	}

	protected static Byte[] boxAudio(byte[] audio) {
		Byte[] boxed = new Byte[audio.length];
		for (int index = 0; index < audio.length; index++) {
			boxed[index] = audio[index];
		}
		return boxed;
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
