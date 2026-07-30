package com.unispeaking.service.tts;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.service.tts.impl.TtsServiceImpl;
import org.junit.jupiter.api.Test;

class TtsServiceImplTest {

	@Test
	void routesTextThroughDefaultTtsProvider() {
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		when(registry.generateSpeechAudio("reservation", null))
				.thenReturn(new Byte[] {82, 73, 70, 70});
		TtsService service = new TtsServiceImpl(registry);

		byte[] audio = service.synthesize("reservation", null);

		assertArrayEquals(new byte[] {82, 73, 70, 70}, audio);
		verify(registry).generateSpeechAudio("reservation", null);
	}
}
