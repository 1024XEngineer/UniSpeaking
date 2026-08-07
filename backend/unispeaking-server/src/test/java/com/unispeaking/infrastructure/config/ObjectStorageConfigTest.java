package com.unispeaking.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.common.exception.BusinessException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ObjectStorageConfigTest {

	@Test
	void createsUnavailableProviderWithGenericStorageError() {
		var provider = new ObjectStorageConfig()
				.objectStorageProvider(new ObjectStorageProperties());

		assertFalse(provider.available());
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider.signGetUrl(
						"customs/recordings/audio.mp3",
						Duration.ofMinutes(5)));
		assertEquals("OBJECT_STORAGE_UNAVAILABLE", exception.code());
	}
}
