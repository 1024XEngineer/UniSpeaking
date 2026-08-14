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

	/**
	 * Default overload keeps existing providers and callers in plain-text mode.
	 * A provider may override this only when it has a native format parameter.
	 */
	public String executeLlmTask(
			String prompt,
			String token,
			LlmResponseFormat responseFormat) {
		return executeLlmTask(prompt, token);
	}
}
