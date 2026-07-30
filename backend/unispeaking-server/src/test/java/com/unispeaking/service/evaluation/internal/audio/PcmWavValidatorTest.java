package com.unispeaking.service.evaluation.internal.audio;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.service.evaluation.internal.exception.EvaluationErrorCode;
import com.unispeaking.service.evaluation.internal.exception.EvaluationException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * 验证 PCM WAV 的容器边界、编码要求、无符号长度处理和五分钟上限。
 */
class PcmWavValidatorTest {

	private static final int MAX_PCM_DATA_SIZE = 32_000 * 300;

	@Test
	void reportsMissingAudio() {
		assertAll(
				() -> assertError(null, EvaluationErrorCode.AUDIO_REQUIRED),
				() -> assertError(
						new byte[0],
						EvaluationErrorCode.AUDIO_REQUIRED));
	}

	@Test
	void rejectsUnsupportedContainers() {
		assertAll(
				() -> assertError(
						"ID3 audio".getBytes(StandardCharsets.US_ASCII),
						EvaluationErrorCode.AUDIO_UNSUPPORTED),
				() -> assertError(
						new byte[] {
							(byte) 0x1a,
							(byte) 0x45,
							(byte) 0xdf,
							(byte) 0xa3
						},
						EvaluationErrorCode.AUDIO_UNSUPPORTED),
				() -> assertError(
						riffWithFormType("AVI "),
						EvaluationErrorCode.AUDIO_UNSUPPORTED));
	}

	@Test
	void acceptsCanonicalPcmWavAndLegalUnknownChunks() {
		byte[] evenUnknownChunk = chunk("JUNK", new byte[] {1, 2});
		byte[] oddUnknownChunk = chunk("LIST", new byte[] {1, 2, 3});

		assertAll(
				() -> assertDoesNotThrow(
						() -> PcmWavValidator.validate(canonicalWav(2))),
				() -> assertDoesNotThrow(() -> PcmWavValidator.validate(
						riff(
								evenUnknownChunk,
								formatChunk(),
								oddUnknownChunk,
								dataChunk(2)))),
				() -> assertDoesNotThrow(() -> PcmWavValidator.validate(
						riff(dataChunk(2), formatChunk()))));
	}

	@Test
	void rejectsBrokenRiffAndChunkBoundariesWithoutOverflow() {
		byte[] sizeTooSmall = canonicalWav(2);
		writeUnsignedIntLittleEndian(
				sizeTooSmall,
				4,
				sizeTooSmall.length - 9L);
		byte[] sizeTooLarge = canonicalWav(2);
		writeUnsignedIntLittleEndian(
				sizeTooLarge,
				4,
				sizeTooLarge.length - 7L);
		byte[] trailingByte =
				Arrays.copyOf(canonicalWav(2), canonicalWav(2).length + 1);

		assertAll(
				() -> assertError(
						"RIFF".getBytes(StandardCharsets.US_ASCII),
						EvaluationErrorCode.AUDIO_INVALID),
				() -> assertError(
						sizeTooSmall,
						EvaluationErrorCode.AUDIO_INVALID),
				() -> assertError(
						sizeTooLarge,
						EvaluationErrorCode.AUDIO_INVALID),
				() -> assertError(
						trailingByte,
						EvaluationErrorCode.AUDIO_INVALID),
				() -> assertError(
						riffFromRawChunks(ascii("fmt ")),
						EvaluationErrorCode.AUDIO_INVALID),
				() -> assertError(
						riffFromRawChunks(
								rawChunkHeader("data", 0xffff_ffffL)),
						EvaluationErrorCode.AUDIO_INVALID),
				() -> assertError(
						riffFromRawChunks(
								concat(
										rawChunkHeader("data", 100),
										new byte[] {0, 0})),
						EvaluationErrorCode.AUDIO_INVALID),
				() -> assertError(
						riffFromRawChunks(
								concat(
										rawChunkHeader("JUNK", 1),
										new byte[] {1})),
						EvaluationErrorCode.AUDIO_INVALID));
	}

	@Test
	void requiresOneFormatChunkAndOneNonEmptyDataChunk() {
		assertAll(
				() -> assertError(
						riff(dataChunk(2)),
						EvaluationErrorCode.AUDIO_INVALID),
				() -> assertError(
						riff(formatChunk()),
						EvaluationErrorCode.AUDIO_INVALID),
				() -> assertError(
						riff(
								formatChunk(),
								formatChunk(),
								dataChunk(2)),
						EvaluationErrorCode.AUDIO_INVALID),
				() -> assertError(
						riff(
								formatChunk(),
								dataChunk(2),
								dataChunk(2)),
						EvaluationErrorCode.AUDIO_INVALID),
				() -> assertError(
						riff(formatChunk(), dataChunk(0)),
						EvaluationErrorCode.AUDIO_INVALID));
	}

	@Test
	void rejectsPcmParametersOutsideProviderRequirements() {
		assertAll(
				() -> assertInvalidFormat(3, 1, 16_000, 32_000, 2, 16),
				() -> assertInvalidFormat(1, 2, 16_000, 64_000, 4, 16),
				() -> assertInvalidFormat(1, 1, 44_100, 88_200, 2, 16),
				() -> assertInvalidFormat(1, 1, 16_000, 64_000, 2, 16),
				() -> assertInvalidFormat(1, 1, 16_000, 32_000, 4, 16),
				() -> assertInvalidFormat(1, 1, 16_000, 16_000, 1, 8));
	}

	@Test
	void rejectsDataThatDoesNotContainCompletePcmFrames() {
		assertError(
				riff(formatChunk(), dataChunk(1)),
				EvaluationErrorCode.AUDIO_INVALID);
	}

	@Test
	void enforcesFiveMinuteDurationAtExactFrameBoundary() {
		assertDoesNotThrow(
				() -> PcmWavValidator.validate(
						canonicalWav(MAX_PCM_DATA_SIZE)));
		assertError(
				canonicalWav(MAX_PCM_DATA_SIZE + 2),
				EvaluationErrorCode.AUDIO_INVALID);
	}

	private static void assertInvalidFormat(
			int format,
			int channels,
			long sampleRate,
			long byteRate,
			int blockAlign,
			int bitsPerSample) {
		assertError(
				riff(
						formatChunk(
								format,
								channels,
								sampleRate,
								byteRate,
								blockAlign,
								bitsPerSample),
						dataChunk(2)),
				EvaluationErrorCode.AUDIO_INVALID);
	}

	private static void assertError(
			byte[] audio,
			EvaluationErrorCode expectedErrorCode) {
		EvaluationException exception = assertThrows(
				EvaluationException.class,
				() -> PcmWavValidator.validate(audio));

		assertSame(expectedErrorCode, exception.errorCode());
	}

	private static byte[] canonicalWav(int dataSize) {
		byte[] wav = new byte[44 + dataSize];
		writeAscii(wav, 0, "RIFF");
		writeUnsignedIntLittleEndian(wav, 4, wav.length - 8L);
		writeAscii(wav, 8, "WAVE");
		writeAscii(wav, 12, "fmt ");
		writeUnsignedIntLittleEndian(wav, 16, 16);
		writeUnsignedShortLittleEndian(wav, 20, 1);
		writeUnsignedShortLittleEndian(wav, 22, 1);
		writeUnsignedIntLittleEndian(wav, 24, 16_000);
		writeUnsignedIntLittleEndian(wav, 28, 32_000);
		writeUnsignedShortLittleEndian(wav, 32, 2);
		writeUnsignedShortLittleEndian(wav, 34, 16);
		writeAscii(wav, 36, "data");
		writeUnsignedIntLittleEndian(wav, 40, dataSize);
		return wav;
	}

	private static byte[] riffWithFormType(String formType) {
		byte[] wav = new byte[12];
		writeAscii(wav, 0, "RIFF");
		writeUnsignedIntLittleEndian(wav, 4, 4);
		writeAscii(wav, 8, formType);
		return wav;
	}

	private static byte[] riff(byte[]... chunks) {
		ByteArrayOutputStream rawChunks = new ByteArrayOutputStream();
		for (byte[] chunk : chunks) {
			rawChunks.writeBytes(chunk);
		}
		return riffFromRawChunks(rawChunks.toByteArray());
	}

	private static byte[] riffFromRawChunks(byte[] rawChunks) {
		byte[] wav = new byte[12 + rawChunks.length];
		writeAscii(wav, 0, "RIFF");
		writeUnsignedIntLittleEndian(wav, 4, wav.length - 8L);
		writeAscii(wav, 8, "WAVE");
		System.arraycopy(rawChunks, 0, wav, 12, rawChunks.length);
		return wav;
	}

	private static byte[] formatChunk() {
		return formatChunk(1, 1, 16_000, 32_000, 2, 16);
	}

	private static byte[] formatChunk(
			int format,
			int channels,
			long sampleRate,
			long byteRate,
			int blockAlign,
			int bitsPerSample) {
		byte[] payload = new byte[16];
		writeUnsignedShortLittleEndian(payload, 0, format);
		writeUnsignedShortLittleEndian(payload, 2, channels);
		writeUnsignedIntLittleEndian(payload, 4, sampleRate);
		writeUnsignedIntLittleEndian(payload, 8, byteRate);
		writeUnsignedShortLittleEndian(payload, 12, blockAlign);
		writeUnsignedShortLittleEndian(payload, 14, bitsPerSample);
		return chunk("fmt ", payload);
	}

	private static byte[] dataChunk(int size) {
		return chunk("data", new byte[size]);
	}

	private static byte[] chunk(String id, byte[] payload) {
		int paddingSize = payload.length & 1;
		byte[] chunk = new byte[8 + payload.length + paddingSize];
		writeAscii(chunk, 0, id);
		writeUnsignedIntLittleEndian(chunk, 4, payload.length);
		System.arraycopy(payload, 0, chunk, 8, payload.length);
		return chunk;
	}

	private static byte[] rawChunkHeader(String id, long size) {
		byte[] header = new byte[8];
		writeAscii(header, 0, id);
		writeUnsignedIntLittleEndian(header, 4, size);
		return header;
	}

	private static byte[] ascii(String value) {
		return value.getBytes(StandardCharsets.US_ASCII);
	}

	private static byte[] concat(byte[] first, byte[] second) {
		byte[] combined = Arrays.copyOf(first, first.length + second.length);
		System.arraycopy(second, 0, combined, first.length, second.length);
		return combined;
	}

	private static void writeAscii(
			byte[] target,
			int offset,
			String value) {
		byte[] bytes = ascii(value);
		System.arraycopy(bytes, 0, target, offset, bytes.length);
	}

	private static void writeUnsignedShortLittleEndian(
			byte[] target,
			int offset,
			int value) {
		target[offset] = (byte) value;
		target[offset + 1] = (byte) (value >>> 8);
	}

	private static void writeUnsignedIntLittleEndian(
			byte[] target,
			int offset,
			long value) {
		target[offset] = (byte) value;
		target[offset + 1] = (byte) (value >>> 8);
		target[offset + 2] = (byte) (value >>> 16);
		target[offset + 3] = (byte) (value >>> 24);
	}
}
