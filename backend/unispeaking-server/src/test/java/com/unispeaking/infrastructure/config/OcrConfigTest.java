package com.unispeaking.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.common.exception.ocr.OcrErrorCode;
import com.unispeaking.common.exception.ocr.OcrException;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OcrConfigTest {

	@Test
	void createsUnavailableProviderWhenOcrIsDisabled() {
		OcrProperties properties = new OcrProperties();
		properties.setEnabled(false);

		var provider = new OcrConfig().ocrProvider(properties, new ObjectMapper());

		assertFalse(provider.available());
		OcrException exception = assertThrows(
				OcrException.class,
				() -> provider.recognizeText(List.of()));
		assertSame(OcrErrorCode.UNAVAILABLE, exception.errorCode());
		assertEquals(OcrErrorCode.UNAVAILABLE.code(), exception.code());
	}
}
