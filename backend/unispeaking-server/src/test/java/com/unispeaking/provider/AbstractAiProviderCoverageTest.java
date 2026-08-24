package com.unispeaking.provider;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.domain.vo.provider.AiCapability;
import com.unispeaking.common.exception.BusinessException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AbstractAiProviderCoverageTest {
	@Test
	void defaultMeasuredOperationsAndUnsupportedCapabilitiesAreCovered() {
		StubProvider provider = new StubProvider();
		assertEquals("answer", provider.executeLlmTaskMeasured("hello", null).response());
		assertEquals("ESTIMATED", provider.executeLlmTaskMeasured("hello", null).usage().source());
		assertArrayEquals(new byte[] {1, 2}, provider.generateSpeechAudioMeasured("hello", null).response());
		assertArrayEquals(new byte[] {1, 2}, provider.generateSpeechAudioMeasured("hello", null, "voice").response());
		assertEquals("text", provider.convertAudioToTextMeasured(new byte[] {1}, null).response());
		assertEquals("score", provider.evaluatePronunciationMeasured("hello", new byte[] {1}, null).response());
		BareProvider bare = new BareProvider();
		assertThrows(UnsupportedOperationException.class, () -> bare.exchangeRealtimeSdp("offer", null));
		assertThrows(UnsupportedOperationException.class, () -> bare.generateSpeechAudio("x", null));
		assertThrows(UnsupportedOperationException.class, () -> bare.executeLlmTask("x", null));
		assertThrows(UnsupportedOperationException.class, () -> bare.convertAudioToText(new byte[] {1}, null));
		assertThrows(UnsupportedOperationException.class, () -> bare.evaluatePronunciation("x", new byte[] {1}, null));
		assertThrows(BusinessException.class, () -> provider.requireAudioForTest(null));
		assertEquals("stub", provider.providerId());
		assertEquals(Set.of("model"), provider.supportedModels());
		assertEquals(true, provider.supports(" MODEL "));
		assertEquals(false, provider.supports("other"));
	}

	private static final class StubProvider extends AbstractAiProvider {
		StubProvider() { super(" STUB ", Set.of(" MODEL ")); }
		@Override public AiCapability capability() { return AiCapability.LLM; }
		@Override public String executeLlmTask(String prompt, String token) { return "answer"; }
		@Override public byte[] generateSpeechAudio(String text, String token) { return new byte[] {1, 2}; }
		@Override public String convertAudioToText(byte[] audio, String token) { return "text"; }
		@Override public String evaluatePronunciation(String text, byte[] audio, String token) { return "score"; }
		void requireAudioForTest(byte[] audio) { requireAudio(audio, "stub"); }
	}

	private static final class BareProvider extends AbstractAiProvider {
		BareProvider() { super("bare", Set.of("model")); }
		@Override public AiCapability capability() { return AiCapability.LLM; }
	}
}
