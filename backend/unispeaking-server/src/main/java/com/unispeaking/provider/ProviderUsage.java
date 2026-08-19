package com.unispeaking.provider;

/** Usage returned by a provider, or a clearly-labelled local estimate when absent. */
public record ProviderUsage(
		long inputTokens,
		long outputTokens,
		long inputCharacters,
		long outputCharacters,
		double audioInputSeconds,
		double audioOutputSeconds,
		String source) {

	public ProviderUsage {
		inputTokens = nonNegative(inputTokens);
		outputTokens = nonNegative(outputTokens);
		inputCharacters = nonNegative(inputCharacters);
		outputCharacters = nonNegative(outputCharacters);
		audioInputSeconds = Math.max(0, audioInputSeconds);
		audioOutputSeconds = Math.max(0, audioOutputSeconds);
		source = source == null || source.isBlank() ? "NONE" : source.trim().toUpperCase();
	}

	public long totalTokens() {
		return inputTokens + outputTokens;
	}

	public static ProviderUsage estimatedText(String input, String output) {
		long inputCharacters = length(input);
		long outputCharacters = length(output);
		return new ProviderUsage(
				estimatedTokens(inputCharacters),
				estimatedTokens(outputCharacters),
				inputCharacters,
				outputCharacters,
				0,
				0,
				"ESTIMATED");
	}

	public static ProviderUsage tts(String input, byte[] output) {
		return new ProviderUsage(0, 0, length(input), 0, 0, wavSeconds(output), "ESTIMATED");
	}

	public static ProviderUsage ttsInput(String input) {
		return new ProviderUsage(0, 0, length(input), 0, 0, 0, "ESTIMATED");
	}

	public static ProviderUsage audioInput(byte[] input, String output) {
		return new ProviderUsage(0, estimatedTokens(length(output)), 0, length(output), wavSeconds(input), 0, "ESTIMATED");
	}

	public static ProviderUsage scoring(String reference, byte[] input) {
		return new ProviderUsage(0, 0, length(reference), 0, wavSeconds(input), 0, "ESTIMATED");
	}

	private static long estimatedTokens(long characters) {
		return characters == 0 ? 0 : Math.max(1, Math.round(characters / 4.0));
	}

	private static long length(String value) {
		return value == null ? 0 : value.codePointCount(0, value.length());
	}

	private static long nonNegative(long value) {
		return Math.max(0, value);
	}

	/** Reads PCM WAV duration without depending on an audio codec library. */
	static double wavSeconds(byte[] audio) {
		if (audio == null || audio.length < 44
				|| audio[0] != 'R' || audio[1] != 'I' || audio[2] != 'F' || audio[3] != 'F') {
			return 0;
		}
		long byteRate = littleEndianUnsignedInt(audio, 28);
		long dataBytes = 0;
		int offset = 12;
		while (offset + 8 <= audio.length) {
			long chunkSize = littleEndianUnsignedInt(audio, offset + 4);
			if (audio[offset] == 'd' && audio[offset + 1] == 'a'
					&& audio[offset + 2] == 't' && audio[offset + 3] == 'a') {
				dataBytes = Math.min(chunkSize, audio.length - offset - 8L);
				break;
			}
			long next = offset + 8L + chunkSize + (chunkSize & 1L);
			if (next <= offset || next > audio.length) break;
			offset = (int) next;
		}
		return byteRate <= 0 ? 0 : dataBytes / (double) byteRate;
	}

	private static long littleEndianUnsignedInt(byte[] value, int offset) {
		if (offset < 0 || offset + 4 > value.length) return 0;
		return (value[offset] & 0xffL)
				| ((value[offset + 1] & 0xffL) << 8)
				| ((value[offset + 2] & 0xffL) << 16)
				| ((value[offset + 3] & 0xffL) << 24);
	}
}
