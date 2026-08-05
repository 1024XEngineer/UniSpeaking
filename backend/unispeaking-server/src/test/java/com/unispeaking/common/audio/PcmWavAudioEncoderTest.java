package com.unispeaking.common.audio;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.common.exception.audio.AudioErrorCode;
import com.unispeaking.common.exception.audio.AudioException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PcmWavAudioEncoderTest {

	@Test
	void parsesUnknownPaddedChunksAndDataBeforeFormat() {
		byte[] wav = riff(
				chunk("JUNK", new byte[] {9, 8, 7}),
				dataChunk(samples((short) 100, (short) -200)),
				formatChunk(16_000, 1));

		PcmWavAudioEncoder.NormalizedPcm normalized =
				PcmWavAudioEncoder.normalize(List.of(wav));

		assertArrayEquals(
				new short[] {100, -200},
				readSamples(normalized.content()));
		assertEquals(2, normalized.sampleCount());
	}

	@Test
	void acceptsConsistentExtendedPcmFormatChunk() {
		byte[] extendedFormat = formatChunk(16_000, 1);
		extendedFormat = Arrays.copyOf(extendedFormat, extendedFormat.length + 2);
		writeUnsignedInt(extendedFormat, 4, 18);

		PcmWavAudioEncoder.NormalizedPcm normalized =
				PcmWavAudioEncoder.normalize(List.of(riff(
						extendedFormat,
						dataChunk(samples((short) 7)))));

		assertArrayEquals(new short[] {7}, readSamples(normalized.content()));
	}

	@Test
	void mixesStereoToMonoWithoutOverflow() {
		byte[] stereo = wav(
				16_000,
				2,
				(short) 1_000,
				(short) -1_000,
				Short.MAX_VALUE,
				Short.MAX_VALUE,
				Short.MIN_VALUE,
				Short.MIN_VALUE);

		PcmWavAudioEncoder.NormalizedPcm normalized =
				PcmWavAudioEncoder.normalize(List.of(stereo));

		assertArrayEquals(
				new short[] {0, Short.MAX_VALUE, Short.MIN_VALUE},
				readSamples(normalized.content()));
	}

	@Test
	void linearlyResamplesWithDeterministicTimeGridAndEndpointClamp() {
		byte[] upsampled = wav(
				8_000,
				1,
				(short) 0,
				(short) 1_000);
		byte[] downsampled = wav(
				48_000,
				1,
				(short) 0,
				(short) 300,
				(short) 600,
				(short) 900,
				(short) 1_200,
				(short) 1_500);

		assertAll(
				() -> assertArrayEquals(
						new short[] {0, 500, 1_000, 1_000},
						readSamples(PcmWavAudioEncoder.normalize(
								List.of(upsampled)).content())),
				() -> assertArrayEquals(
						new short[] {0, 900},
						readSamples(PcmWavAudioEncoder.normalize(
								List.of(downsampled)).content())));
	}

	@ParameterizedTest
	@ValueSource(ints = {
		8_000,
		11_025,
		12_000,
		16_000,
		22_050,
		24_000,
		32_000,
		44_100,
		48_000,
		88_200,
		96_000
	})
	void acceptsCommonSampleRates(int sampleRate) {
		short[] source = new short[sampleRate / 100];

		PcmWavAudioEncoder.NormalizedPcm normalized =
				PcmWavAudioEncoder.normalize(List.of(
						wav(sampleRate, 1, source)));

		assertEquals(160, normalized.sampleCount());
	}

	@Test
	void concatenatesSegmentsStrictlyInInputOrder() {
		PcmWavAudioEncoder.NormalizedPcm normalized =
				PcmWavAudioEncoder.normalize(List.of(
						wav(16_000, 1, (short) 1, (short) 2),
						wav(16_000, 1, (short) 3, (short) 4)));

		assertArrayEquals(
				new short[] {1, 2, 3, 4},
				readSamples(normalized.content()));
	}

	@Test
	void rejectsMissingAndEmptyInput() {
		assertAll(
				() -> assertError(null, AudioErrorCode.INPUT_REQUIRED),
				() -> assertError(List.of(), AudioErrorCode.INPUT_REQUIRED),
				() -> assertError(
						Arrays.asList((byte[]) null),
						AudioErrorCode.INPUT_REQUIRED),
				() -> assertError(
						List.of(new byte[0]),
						AudioErrorCode.INPUT_REQUIRED));
	}

	@Test
	void rejectsUnsupportedContainersAndPcmFormats() {
		byte[] compressedWav = riff(
				formatChunk(3, 16_000, 1, 32_000, 2, 16),
				dataChunk(samples((short) 1)));
		byte[] eightBitWav = riff(
				formatChunk(1, 16_000, 1, 16_000, 1, 8),
				dataChunk(new byte[] {1, 2}));

		assertAll(
				() -> assertError(
						List.of("ID3 MP3".getBytes(StandardCharsets.US_ASCII)),
						AudioErrorCode.FORMAT_UNSUPPORTED),
				() -> assertError(
						List.of(riffWithFormType("AVI ")),
						AudioErrorCode.FORMAT_UNSUPPORTED),
				() -> assertError(
						List.of(compressedWav),
						AudioErrorCode.FORMAT_UNSUPPORTED),
				() -> assertError(
						List.of(eightBitWav),
						AudioErrorCode.FORMAT_UNSUPPORTED),
				() -> assertError(
						List.of(wav(192_000, 1, (short) 1)),
						AudioErrorCode.FORMAT_UNSUPPORTED));
	}

	@Test
	void rejectsCorruptTruncatedAndIncompleteWavContainers() {
		byte[] oddPcm = riff(
				formatChunk(16_000, 1),
				dataChunk(new byte[] {1}));
		byte[] incompleteStereo = riff(
				formatChunk(16_000, 2),
				dataChunk(samples((short) 1)));
		byte[] badByteRate = riff(
				formatChunk(1, 16_000, 1, 1, 2, 16),
				dataChunk(samples((short) 1)));
		byte[] truncated = wav(16_000, 1, (short) 1);
		truncated = Arrays.copyOf(truncated, truncated.length - 1);
		byte[] oversizedChunk = riffFromRawChunks(
				rawChunkHeader("data", 0xffff_ffffL));
		byte[] malformedExtendedFormat = formatChunk(16_000, 1);
		malformedExtendedFormat = Arrays.copyOf(
				malformedExtendedFormat,
				malformedExtendedFormat.length + 2);
		writeUnsignedInt(malformedExtendedFormat, 4, 17);

		byte[] finalTruncated = truncated;
		byte[] finalMalformedExtendedFormat = malformedExtendedFormat;
		assertAll(
				() -> assertError(
						List.of("RIFF".getBytes(StandardCharsets.US_ASCII)),
						AudioErrorCode.CONTENT_INVALID),
				() -> assertError(
						List.of(oddPcm),
						AudioErrorCode.CONTENT_INVALID),
				() -> assertError(
						List.of(incompleteStereo),
						AudioErrorCode.CONTENT_INVALID),
				() -> assertError(
						List.of(badByteRate),
						AudioErrorCode.CONTENT_INVALID),
				() -> assertError(
						List.of(finalTruncated),
						AudioErrorCode.CONTENT_INVALID),
				() -> assertError(
						List.of(oversizedChunk),
						AudioErrorCode.CONTENT_INVALID),
				() -> assertError(
						List.of(riff(
								finalMalformedExtendedFormat,
								dataChunk(samples((short) 1)))),
						AudioErrorCode.CONTENT_INVALID),
				() -> assertError(
						List.of(riff(formatChunk(16_000, 1))),
						AudioErrorCode.CONTENT_INVALID),
				() -> assertError(
						List.of(riff(dataChunk(samples((short) 1)))),
						AudioErrorCode.CONTENT_INVALID),
				() -> assertError(
						List.of(riff(
								formatChunk(16_000, 1),
								formatChunk(16_000, 1),
								dataChunk(samples((short) 1)))),
						AudioErrorCode.CONTENT_INVALID),
				() -> assertError(
						List.of(riff(
								formatChunk(16_000, 1),
								dataChunk(samples((short) 1)),
								dataChunk(samples((short) 2)))),
						AudioErrorCode.CONTENT_INVALID));
	}

	@Test
	void encodesMp3WithExactNormalizedDurationAndDefensiveContent() {
		short[] samples = new short[1_600];
		for (int index = 0; index < samples.length; index++) {
			samples[index] = (short) (index * 17);
		}
		EncodedAudio encoded = new PcmWavAudioEncoder().encode(
				List.of(wav(16_000, 1, samples)));

		byte[] firstRead = encoded.content();
		assertAll(
				() -> assertEquals("audio/mpeg", encoded.mediaType()),
				() -> assertEquals(Duration.ofMillis(100), encoded.duration()),
				() -> assertTrue(firstRead.length > 0),
				() -> assertTrue(containsMp3FrameSync(firstRead)));
		firstRead[0] ^= 0xff;
		assertFalse(Arrays.equals(firstRead, encoded.content()));
	}

	@Test
	void encodedAudioDefensivelyCopiesConstructionInput() {
		byte[] content = new byte[] {1, 2, 3};
		EncodedAudio encoded = new EncodedAudio(
				content,
				"audio/mpeg",
				Duration.ofSeconds(1));

		content[0] = 9;

		assertArrayEquals(new byte[] {1, 2, 3}, encoded.content());
	}

	private static void assertError(
			List<byte[]> input,
			AudioErrorCode expected) {
		AudioException exception = assertThrows(
				AudioException.class,
				() -> PcmWavAudioEncoder.normalize(input));

		assertSame(expected, exception.errorCode());
		assertEquals(expected.code(), exception.code());
	}

	private static boolean containsMp3FrameSync(byte[] content) {
		for (int index = 0; index + 1 < content.length; index++) {
			if ((content[index] & 0xff) == 0xff
					&& (content[index + 1] & 0xe0) == 0xe0) {
				return true;
			}
		}
		return false;
	}

	private static short[] readSamples(byte[] pcm) {
		short[] samples = new short[pcm.length / Short.BYTES];
		for (int index = 0; index < samples.length; index++) {
			int offset = index * Short.BYTES;
			samples[index] = (short) ((pcm[offset] & 0xff)
					| ((pcm[offset + 1] & 0xff) << 8));
		}
		return samples;
	}

	private static byte[] wav(
			int sampleRate,
			int channels,
			short... interleavedSamples) {
		return riff(
				formatChunk(sampleRate, channels),
				dataChunk(samples(interleavedSamples)));
	}

	private static byte[] samples(short... samples) {
		byte[] pcm = new byte[samples.length * Short.BYTES];
		for (int index = 0; index < samples.length; index++) {
			pcm[index * 2] = (byte) samples[index];
			pcm[index * 2 + 1] = (byte) (samples[index] >>> 8);
		}
		return pcm;
	}

	private static byte[] formatChunk(int sampleRate, int channels) {
		return formatChunk(
				1,
				sampleRate,
				channels,
				sampleRate * channels * Short.BYTES,
				channels * Short.BYTES,
				16);
	}

	private static byte[] formatChunk(
			int format,
			int sampleRate,
			int channels,
			long byteRate,
			int blockAlign,
			int bitsPerSample) {
		byte[] payload = new byte[16];
		writeUnsignedShort(payload, 0, format);
		writeUnsignedShort(payload, 2, channels);
		writeUnsignedInt(payload, 4, sampleRate);
		writeUnsignedInt(payload, 8, byteRate);
		writeUnsignedShort(payload, 12, blockAlign);
		writeUnsignedShort(payload, 14, bitsPerSample);
		return chunk("fmt ", payload);
	}

	private static byte[] dataChunk(byte[] pcm) {
		return chunk("data", pcm);
	}

	private static byte[] chunk(String id, byte[] payload) {
		byte[] chunk = new byte[8 + payload.length + (payload.length & 1)];
		writeAscii(chunk, 0, id);
		writeUnsignedInt(chunk, 4, payload.length);
		System.arraycopy(payload, 0, chunk, 8, payload.length);
		return chunk;
	}

	private static byte[] riff(byte[]... chunks) {
		ByteArrayOutputStream rawChunks = new ByteArrayOutputStream();
		for (byte[] chunk : chunks) {
			rawChunks.writeBytes(chunk);
		}
		return riffFromRawChunks(rawChunks.toByteArray());
	}

	private static byte[] riffFromRawChunks(byte[] chunks) {
		byte[] wav = new byte[12 + chunks.length];
		writeAscii(wav, 0, "RIFF");
		writeUnsignedInt(wav, 4, wav.length - 8L);
		writeAscii(wav, 8, "WAVE");
		System.arraycopy(chunks, 0, wav, 12, chunks.length);
		return wav;
	}

	private static byte[] riffWithFormType(String formType) {
		byte[] wav = new byte[12];
		writeAscii(wav, 0, "RIFF");
		writeUnsignedInt(wav, 4, 4);
		writeAscii(wav, 8, formType);
		return wav;
	}

	private static byte[] rawChunkHeader(String id, long size) {
		byte[] header = new byte[8];
		writeAscii(header, 0, id);
		writeUnsignedInt(header, 4, size);
		return header;
	}

	private static void writeAscii(byte[] bytes, int offset, String value) {
		byte[] ascii = value.getBytes(StandardCharsets.US_ASCII);
		System.arraycopy(ascii, 0, bytes, offset, ascii.length);
	}

	private static void writeUnsignedShort(
			byte[] bytes,
			int offset,
			int value) {
		bytes[offset] = (byte) value;
		bytes[offset + 1] = (byte) (value >>> 8);
	}

	private static void writeUnsignedInt(
			byte[] bytes,
			int offset,
			long value) {
		bytes[offset] = (byte) value;
		bytes[offset + 1] = (byte) (value >>> 8);
		bytes[offset + 2] = (byte) (value >>> 16);
		bytes[offset + 3] = (byte) (value >>> 24);
	}
}
