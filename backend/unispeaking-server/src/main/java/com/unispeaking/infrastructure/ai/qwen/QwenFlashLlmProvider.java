package com.unispeaking.infrastructure.ai.qwen;

import com.unispeaking.provider.AiProviderRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class QwenFlashLlmProvider extends QwenLlmProvider {

	public QwenFlashLlmProvider(
			ObjectMapper objectMapper,
			@Value("${DASHSCOPE_API_KEY:}") String apiKey,
			@Value("${BAILIAN_WORKSPACE_ID:}") String workspaceId,
			@Value("${BAILIAN_REGION:cn-beijing}") String region,
			@Value("${QWEN_LLM_CONNECT_TIMEOUT_SECONDS:10}") int connectTimeoutSeconds,
			@Value("${QWEN_LLM_READ_TIMEOUT_SECONDS:60}") int readTimeoutSeconds,
			@Value("${QWEN_LLM_MAX_RESPONSE_BYTES:2097152}") int maxResponseBytes) {
		super(
				objectMapper,
				apiKey,
				workspaceId,
				region,
				AiProviderRegistry.QWEN_LLM_FLASH,
				connectTimeoutSeconds,
				readTimeoutSeconds,
				maxResponseBytes);
	}
}
