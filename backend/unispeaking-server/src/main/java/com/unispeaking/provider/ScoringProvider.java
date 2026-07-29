package com.unispeaking.provider;

import com.unispeaking.domain.vo.ai.AiCapability;
import java.util.Set;

public abstract class ScoringProvider extends AbstractAiProvider {

	protected ScoringProvider(String providerId, Set<String> supportedModels) {
		super(providerId, supportedModels);
	}

	@Override
	public final AiCapability capability() {
		return AiCapability.SCORING;
	}
}
