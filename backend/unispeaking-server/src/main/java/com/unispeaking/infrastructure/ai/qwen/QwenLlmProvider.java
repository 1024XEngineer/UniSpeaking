package com.unispeaking.infrastructure.ai.qwen;

import com.unispeaking.domain.dto.ai.LlmTaskRequest;
import com.unispeaking.domain.dto.ai.LlmTaskResponse;
import com.unispeaking.domain.vo.realtime.ProviderType;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.LlmProvider;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class QwenLlmProvider extends LlmProvider {

	public QwenLlmProvider() {
		super(ProviderType.QWEN, Set.of(AiProviderRegistry.QWEN_LLM_PLUS));
	}

	@Override
	public LlmTaskResponse executeLlmTask(LlmTaskRequest request) {
		throw capabilityNotConfigured(AiProviderRegistry.QWEN_LLM_PLUS);
	}
}
