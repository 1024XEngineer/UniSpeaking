package com.unispeaking.provider;

import com.unispeaking.domain.dto.ai.SpeechAudioRequest;
import com.unispeaking.domain.dto.ai.SpeechAudioResponse;
import com.unispeaking.domain.vo.ai.AiCapability;
import com.unispeaking.domain.vo.realtime.ProviderType;
import java.util.Set;

public abstract class TtsProvider extends AbstractAiProvider {

	protected TtsProvider(ProviderType providerType, Set<String> supportedModels) {
		super(providerType, supportedModels);
	}

	@Override
	public final AiCapability capability() {
		return AiCapability.TTS;
	}

	public abstract SpeechAudioResponse generateSpeechAudio(SpeechAudioRequest request);
}
