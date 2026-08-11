package com.unispeaking.infrastructure.config;

import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class WebClientConfig {

	@Bean
	@Primary
	public HttpClient realtimeHttpClient(RealtimeProperties properties) {
		return HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
	}

	@Bean
	@Qualifier("qiniuRealtimeHttpClient")
	public HttpClient qiniuRealtimeHttpClient(QiniuRealtimeProperties properties) {
		return HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
	}
}
