package com.unispeaking.infrastructure.config;

import com.unispeaking.infrastructure.ocr.PaddleOcrProvider;
import com.unispeaking.infrastructure.ocr.UnavailableOcrProvider;
import com.unispeaking.provider.OcrProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class OcrConfig {

	@Bean
	OcrProvider ocrProvider(OcrProperties properties, ObjectMapper objectMapper) {
		if (!properties.configured()) {
			return new UnavailableOcrProvider();
		}
		return new PaddleOcrProvider(properties, objectMapper);
	}
}
