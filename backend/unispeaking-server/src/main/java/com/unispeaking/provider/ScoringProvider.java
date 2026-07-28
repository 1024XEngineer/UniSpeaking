package com.unispeaking.provider;

import com.unispeaking.domain.dto.ai.PronunciationEvaluationRequest;
import com.unispeaking.domain.dto.ai.PronunciationEvaluationResponse;
import com.unispeaking.domain.vo.ai.AiCapability;
import com.unispeaking.domain.vo.realtime.ProviderType;
import java.util.Set;

public abstract class ScoringProvider extends AbstractAiProvider {

	protected ScoringProvider(ProviderType providerType, Set<String> supportedModels) {
		super(providerType, supportedModels);
	}

	@Override
	public final AiCapability capability() {
		return AiCapability.SCORING;
	}

	public abstract PronunciationEvaluationResponse evaluatePronunciation(
			PronunciationEvaluationRequest request);
}
