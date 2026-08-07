package com.unispeaking.provider;

/**
 * Stable, vendor-neutral AI capability contract.
 */
public interface AiProvider {

	String exchangeRealtimeSdp(String offerSdp, String token);

	byte[] generateSpeechAudio(String text, String token);

	String executeLlmTask(String prompt, String token);

	String convertAudioToText(byte[] audio, String token);

	String evaluatePronunciation(
			String text,
			byte[] audio,
			String token);
}
