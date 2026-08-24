package com.unispeaking.domain.vo.profile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class PreferredAiSpeechSpeedTest {
	@Test
	void exposesStablePreferenceOrder() {
		assertArrayEquals(new PreferredAiSpeechSpeed[] {
				PreferredAiSpeechSpeed.SLOWER,
				PreferredAiSpeechSpeed.MODERATE,
				PreferredAiSpeechSpeed.NATURAL,
				PreferredAiSpeechSpeed.FASTER }, PreferredAiSpeechSpeed.values());
	}
}
