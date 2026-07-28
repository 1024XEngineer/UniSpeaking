package com.unispeaking.provider;

import com.unispeaking.domain.dto.ai.LlmTaskRequest;
import com.unispeaking.domain.dto.ai.LlmTaskResponse;
import com.unispeaking.domain.vo.ai.AiCapability;
import com.unispeaking.domain.vo.realtime.ProviderType;
import java.util.Set;

public abstract class LlmProvider extends AbstractAiProvider {

	protected LlmProvider(ProviderType providerType, Set<String> supportedModels) {
		super(providerType, supportedModels);
	}

	@Override
	public final AiCapability capability() {
		return AiCapability.LLM;
	}

	public abstract LlmTaskResponse executeLlmTask(LlmTaskRequest request);
}
