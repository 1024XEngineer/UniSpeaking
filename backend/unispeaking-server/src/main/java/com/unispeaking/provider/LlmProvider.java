package com.unispeaking.provider;

import com.unispeaking.domain.vo.provider.AiCapability;
import java.util.Set;

public abstract class LlmProvider extends AbstractAiProvider {

	protected LlmProvider(String providerId, Set<String> supportedModels) {
		super(providerId, supportedModels);
	}

	@Override
	public final AiCapability capability() {
		return AiCapability.LLM;
	}
}
