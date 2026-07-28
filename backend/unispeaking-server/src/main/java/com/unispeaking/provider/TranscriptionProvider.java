package com.unispeaking.provider;

import com.unispeaking.domain.dto.ai.AudioTranscriptionRequest;
import com.unispeaking.domain.dto.ai.AudioTranscriptionResponse;
import com.unispeaking.domain.vo.ai.AiCapability;
import com.unispeaking.domain.vo.realtime.ProviderType;
import java.util.Set;

public abstract class TranscriptionProvider extends AbstractAiProvider {

	protected TranscriptionProvider(ProviderType providerType, Set<String> supportedModels) {
		super(providerType, supportedModels);
	}

	@Override
	public final AiCapability capability() {
		return AiCapability.TRANSCRIPTION;
	}

	public abstract AudioTranscriptionResponse convertAudioToText(
			AudioTranscriptionRequest request);
}
