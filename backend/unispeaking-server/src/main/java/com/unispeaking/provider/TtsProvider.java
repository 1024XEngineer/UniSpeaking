package com.unispeaking.provider;

import com.unispeaking.domain.vo.provider.AiCapability;
import java.util.Set;

public abstract class TtsProvider extends AbstractAiProvider {

	protected TtsProvider(String providerId, Set<String> supportedModels) {
		super(providerId, supportedModels);
	}

	@Override
	public final AiCapability capability() {
		return AiCapability.TTS;
	}

	@Override
	public AiProviderResponse<byte[]> generateSpeechAudioMeasured(
			String text,
			String token,
			String voice) {
		return generateSpeechAudioMeasured(text, token);
	}
}
