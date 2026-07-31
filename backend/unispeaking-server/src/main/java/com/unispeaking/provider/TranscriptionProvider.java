package com.unispeaking.provider;

import com.unispeaking.domain.vo.provider.AiCapability;
import java.util.Set;

public abstract class TranscriptionProvider extends AbstractAiProvider {

	protected TranscriptionProvider(String providerId, Set<String> supportedModels) {
		super(providerId, supportedModels);
	}

	@Override
	public final AiCapability capability() {
		return AiCapability.TRANSCRIPTION;
	}
}
