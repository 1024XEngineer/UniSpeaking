package com.unispeaking.infrastructure.ai.qiniu;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.provider.LlmProvider;
import java.util.Objects;
import java.util.Set;

public final class QiniuMaasLlmProvider extends LlmProvider {

	public static final String PROVIDER_ID = "qiniu-maas";

	private final QiniuMaasLlmClient client;
	private final String model;

	public QiniuMaasLlmProvider(QiniuMaasLlmClient client, String model) {
		super(PROVIDER_ID, Set.of(requiredModel(model)));
		this.client = Objects.requireNonNull(client, "Qiniu MaaS LLM client is required");
		this.model = requiredModel(model);
	}

	@Override
	public String executeLlmTask(String prompt, String token) {
		try {
			return client.execute(model, prompt);
		}
		catch (QiniuMaasLlmClient.ProviderFailure exception) {
			throw exception.retryable()
					? retryableFailure(exception.code(), exception.getMessage())
					: nonRetryableFailure(exception.code(), exception.getMessage());
		}
		catch (BusinessException exception) {
			throw exception;
		}
	}

	private static String requiredModel(String value) {
		String model = value == null ? "" : value.trim();
		if (model.isBlank()) {
			throw new IllegalArgumentException("Qiniu MaaS LLM model is required");
		}
		return model;
	}
}
