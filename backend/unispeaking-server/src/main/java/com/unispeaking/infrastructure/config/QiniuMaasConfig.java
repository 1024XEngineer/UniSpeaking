package com.unispeaking.infrastructure.config;

import com.unispeaking.infrastructure.ai.qiniu.QiniuMaasLlmClient;
import com.unispeaking.infrastructure.ai.qiniu.QiniuMaasLlmProvider;
import com.unispeaking.provider.LlmProvider;
import java.net.http.HttpClient;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class QiniuMaasConfig {

	@Bean
	QiniuMaasLlmClient qiniuMaasLlmClient(
			QiniuMaasProperties properties,
			ObjectMapper objectMapper) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.connectTimeout())
				.build();
		return new QiniuMaasLlmClient(httpClient, objectMapper, properties);
	}

	@Bean
	@Order(0)
	LlmProvider qiniuMaasPrimaryLlmProvider(
			QiniuMaasLlmClient client,
			QiniuMaasProperties properties) {
		return new QiniuMaasLlmProvider(client, properties.primaryModel());
	}

	@Bean
	@Order(1)
	LlmProvider qiniuMaasFallbackLlmProvider(
			QiniuMaasLlmClient client,
			QiniuMaasProperties properties) {
		return new QiniuMaasLlmProvider(client, properties.fallbackModel());
	}
}
