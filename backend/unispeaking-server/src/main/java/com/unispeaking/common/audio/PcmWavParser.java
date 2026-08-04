package com.unispeaking.common.audio;

import com.unispeaking.common.exception.audio.AudioErrorCode;
import com.unispeaking.common.exception.audio.AudioException;
import java.util.Arrays;
import java.util.Set;

/**
 * 解析首版支持的 16-bit PCM WAV 容器。
 */
final class PcmWavParser {

	private static final int RIFF_HEADER_SIZE = 12;
	private static final int CHUNK_HEADER_SIZE = 8;
	private static final int MIN_FORMAT_SIZE = 16;
	private static final int EXTENDED_FORMAT_HEADER_SIZE = 18;
	private static final int PCM_FORMAT = 1;
	private static final int BITS_PER_SAMPLE = 16;
	private static final Set<Long> SUPPORTED_SAMPLE_RATES = Set.of(
			8_000L,
			11_025L,
			12_000L,
			16_000L,
			22_050L,
			24_000L,
			32_000L,
			44_100L,
			48_000L,
			88_200L,
			96_000L);

	private PcmWavParser() {
	}

	static PcmWavData parse(byte[] wav) {
		if (wav == null || wav.length == 0) {
			throw new AudioException(AudioErrorCode.INPUT_REQUIRED);
		}
		if (!hasId(wav, 0, "RIFF")) {
			throw new AudioException(AudioErrorCode.FORMAT_UNSUPPORTED);
		}
		if (wav.length < RIFF_HEADER_SIZE) {
			throw invalidAudio();
		}
		if (!hasId(wav, 8, "WAVE")) {
			throw new AudioException(AudioErrorCode.FORMAT_UNSUPPORTED);
		}
		long declaredSize = readUnsignedInt(wav, 4) + 8L;
		if (declaredSize != wav.length) {
			throw invalidAudio();
		}

		Format format = null;
		int dataStart = -1;
		int dataSize = -1;
		long offset = RIFF_HEADER_SIZE;
		while (offset < wav.length) {
			if (wav.length - offset < CHUNK_HEADER_SIZE) {
				throw invalidAudio();
			}
			int chunkOffset = (int) offset;
			long chunkSize = readUnsignedInt(wav, chunkOffset + 4);
			long chunkDataStart = offset + CHUNK_HEADER_SIZE;
			long chunkDataEnd = chunkDataStart + chunkSize;
			long nextChunk = chunkDataEnd + (chunkSize & 1L);
			if (chunkDataEnd > wav.length || nextChunk > wav.length) {
				throw invalidAudio();
			}

			if (hasId(wav, chunkOffset, "fmt ")) {
				if (format != null) {
					throw invalidAudio();
				}
				format = parseFormat(wav, (int) chunkDataStart, chunkSize);
			}
			else if (hasId(wav, chunkOffset, "data")) {
				if (dataStart >= 0 || chunkSize > Integer.MAX_VALUE) {
					throw invalidAudio();
				}
				dataStart = (int) chunkDataStart;
				dataSize = (int) chunkSize;
			}
			offset = nextChunk;
		}

		if (offset != wav.length || format == null || dataStart < 0) {
			throw invalidAudio();
		}
		int frameSize = format.channels() * Short.BYTES;
		if (dataSize == 0
				|| (dataSize & 1) != 0
				|| dataSize % frameSize != 0) {
			throw invalidAudio();
		}
		return new PcmWavData(
				format.sampleRate(),
				format.channels(),
				Arrays.copyOfRange(wav, dataStart, dataStart + dataSize));
	}

	private static Format parseFormat(
			byte[] wav,
			int offset,
			long chunkSize) {
		if (chunkSize < MIN_FORMAT_SIZE) {
			throw invalidAudio();
		}
		if (chunkSize != MIN_FORMAT_SIZE) {
			if (chunkSize < EXTENDED_FORMAT_HEADER_SIZE) {
				throw invalidAudio();
			}
			int extensionSize = readUnsignedShort(
					wav,
					offset + MIN_FORMAT_SIZE);
			if (chunkSize != EXTENDED_FORMAT_HEADER_SIZE + extensionSize) {
				throw invalidAudio();
			}
		}
		int audioFormat = readUnsignedShort(wav, offset);
		int channels = readUnsignedShort(wav, offset + 2);
		long sampleRate = readUnsignedInt(wav, offset + 4);
		long byteRate = readUnsignedInt(wav, offset + 8);
		int blockAlign = readUnsignedShort(wav, offset + 12);
		int bitsPerSample = readUnsignedShort(wav, offset + 14);
		if (audioFormat != PCM_FORMAT
				|| (channels != 1 && channels != 2)
				|| !SUPPORTED_SAMPLE_RATES.contains(sampleRate)
				|| bitsPerSample != BITS_PER_SAMPLE) {
			throw new AudioException(AudioErrorCode.FORMAT_UNSUPPORTED);
		}
		int expectedBlockAlign = channels * Short.BYTES;
		long expectedByteRate = sampleRate * expectedBlockAlign;
		if (blockAlign != expectedBlockAlign || byteRate != expectedByteRate) {
			throw invalidAudio();
		}
		return new Format((int) sampleRate, channels);
	}

	private static boolean hasId(byte[] bytes, int offset, String expected) {
		if (offset < 0 || bytes.length - offset < expected.length()) {
			return false;
		}
		for (int index = 0; index < expected.length(); index++) {
			if ((bytes[offset + index] & 0xff) != expected.charAt(index)) {
				return false;
			}
		}
		return true;
	}

	private static int readUnsignedShort(byte[] bytes, int offset) {
		return (bytes[offset] & 0xff)
				| ((bytes[offset + 1] & 0xff) << 8);
	}

	private static long readUnsignedInt(byte[] bytes, int offset) {
		return (bytes[offset] & 0xffL)
				| ((bytes[offset + 1] & 0xffL) << 8)
				| ((bytes[offset + 2] & 0xffL) << 16)
				| ((bytes[offset + 3] & 0xffL) << 24);
	}

	private static AudioException invalidAudio() {
		return new AudioException(AudioErrorCode.CONTENT_INVALID);
	}

	private record Format(int sampleRate, int channels) {
	}
}
