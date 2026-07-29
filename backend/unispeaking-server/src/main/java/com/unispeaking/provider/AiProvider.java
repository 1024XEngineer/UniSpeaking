package com.unispeaking.provider;

import com.unispeaking.domain.vo.ai.AiCapability;
import java.util.Set;

/**
 * Common contract exposed by every pluggable AI provider adapter.
 *
 * <p>The Registry invokes only the operation matching {@link #capability()}.
 * Default methods make unsupported operations explicit without forcing an
 * adapter to implement capabilities it does not provide.</p>
 */
public interface AiProvider {

	String providerId();

	AiCapability capability();

	Set<String> supportedModels();

	/**
	 * Completes the WebRTC Offer SDP / Answer SDP exchange.
	 *
	 * @param offerSdp browser-generated Offer SDP
	 * @param token short-lived provider credential
	 * @return provider Answer SDP without modification
	 */
	default String exchangeRealtimeSdp(String offerSdp, String token) {
		throw unsupported("Realtime");
	}

	/**
	 * Converts text to the provider's configured WAV audio.
	 *
	 * @param text text to synthesize
	 * @param token reserved for interface compatibility; TTS uses server credentials
	 * @return WAV audio bytes
	 */
	default Byte[] generateSpeechAudio(String text, String token) {
		throw unsupported("TTS");
	}

	/**
	 * Executes a text-generation task.
	 *
	 * @param prompt prompt sent to the model
	 * @param token reserved for interface compatibility; LLM uses server credentials
	 * @return only the model message content
	 */
	default String executeLlmTask(String prompt, String token) {
		throw unsupported("LLM");
	}

	/**
	 * Converts WAV audio to transcription text.
	 *
	 * @param audio complete WAV file bytes
	 * @param token reserved for interface compatibility; ASR uses server credentials
	 * @return only the transcription text
	 */
	default String convertAudioToText(Byte[] audio, String token) {
		throw unsupported("ASR");
	}

	/**
	 * Sends WAV audio for pronunciation evaluation.
	 *
	 * @param text reference text
	 * @param audio complete WAV file bytes
	 * @param token reserved for interface compatibility; scoring uses server credentials
	 * @return the complete final provider response without score parsing
	 */
	default String evaluatePronunciation(String text, Byte[] audio, String token) {
		throw unsupported("pronunciation scoring");
	}

	private UnsupportedOperationException unsupported(String operation) {
		return new UnsupportedOperationException(
				providerId() + " provider does not support " + operation);
	}
}
