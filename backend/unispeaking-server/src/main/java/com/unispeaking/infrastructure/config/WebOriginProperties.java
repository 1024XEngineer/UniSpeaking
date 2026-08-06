package com.unispeaking.infrastructure.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("web")
public class WebOriginProperties {

	private List<String> allowedOriginPatterns = new ArrayList<>(List.of(
			"http://localhost:*",
			"http://127.0.0.1:*",
			"https://unispeaking.cn",
			"https://www.unispeaking.cn"));

	public List<String> getAllowedOriginPatterns() {
		return allowedOriginPatterns;
	}

	public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
		this.allowedOriginPatterns = allowedOriginPatterns == null
				? new ArrayList<>()
				: new ArrayList<>(allowedOriginPatterns);
	}
}
