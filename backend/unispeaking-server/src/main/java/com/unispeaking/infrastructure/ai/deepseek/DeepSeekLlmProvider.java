package com.unispeaking.infrastructure.ai.deepseek;

import com.unispeaking.domain.dto.ai.LlmTaskRequest;
import com.unispeaking.domain.dto.ai.LlmTaskResponse;
import com.unispeaking.domain.vo.realtime.ProviderType;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.LlmProvider;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DeepSeekLlmProvider extends LlmProvider {

	public DeepSeekLlmProvider() {
		super(ProviderType.DEEPSEEK, Set.of(AiProviderRegistry.DEEPSEEK_CHAT));
	}

	@Override
	public LlmTaskResponse executeLlmTask(LlmTaskRequest request) {
		throw capabilityNotConfigured(AiProviderRegistry.DEEPSEEK_CHAT);
	}
}
