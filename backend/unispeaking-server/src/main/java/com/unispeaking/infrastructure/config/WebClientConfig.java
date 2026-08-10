package com.unispeaking.infrastructure.config;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebClientConfig {

	@Bean
	public HttpClient realtimeHttpClient(RealtimeProperties properties) {
		return HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
	}
}
