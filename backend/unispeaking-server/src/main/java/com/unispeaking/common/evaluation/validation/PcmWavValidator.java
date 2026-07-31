package com.unispeaking.common.evaluation.validation;

import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;

/**
 * 校验发音评分 Provider 所需的完整 PCM WAV 音频。
 *
 * <p>该组件只检查容器结构、编码参数和时长，不判断音频是否静音或失真。
 * 过短回答不需要音频，应由上层在调用本组件前完成分支判断。</p>
 */
public final class PcmWavValidator {

	private static final int RIFF_HEADER_SIZE = 12;
	private static final int CHUNK_HEADER_SIZE = 8;
	private static final int MIN_FMT_CHUNK_SIZE = 16;

	private static final int PCM_FORMAT = 1;
	private static final int REQUIRED_CHANNELS = 1;
	private static final long REQUIRED_SAMPLE_RATE = 16_000L;
	private static final int REQUIRED_BITS_PER_SAMPLE = 16;
	private static final int REQUIRED_BLOCK_ALIGN = 2;
	private static final long REQUIRED_BYTE_RATE = 32_000L;

	private static final int MAX_DURATION_SECONDS = 300;
	private static final long MAX_PCM_DATA_SIZE =
			REQUIRED_BYTE_RATE * MAX_DURATION_SECONDS;

	private PcmWavValidator() {
	}

	/**
	 * 校验音频是否为最长五分钟的 16 kHz、单声道、16-bit PCM WAV。
	 *
	 * @param audio 完整 WAV 文件字节
	 * @throws EvaluationException 音频缺失、容器不受支持或 WAV 内容无效
	 */
	public static void validate(byte[] audio) {
		if (audio == null || audio.length == 0) {
			throw new EvaluationException(EvaluationErrorCode.AUDIO_REQUIRED);
		}
		if (!hasChunkId(audio, 0, "RIFF")) {
			throw new EvaluationException(EvaluationErrorCode.AUDIO_UNSUPPORTED);
		}
		if (audio.length < RIFF_HEADER_SIZE) {
			throw invalidAudio();
		}
		if (!hasChunkId(audio, 8, "WAVE")) {
			throw new EvaluationException(EvaluationErrorCode.AUDIO_UNSUPPORTED);
		}

		long declaredFileSize = readUnsignedIntLittleEndian(audio, 4) + 8L;
		if (declaredFileSize != audio.length) {
			throw invalidAudio();
		}

		boolean fmtFound = false;
		boolean dataFound = false;
		long offset = RIFF_HEADER_SIZE;
		while (offset < audio.length) {
			if (audio.length - offset < CHUNK_HEADER_SIZE) {
				throw invalidAudio();
			}

			int chunkOffset = (int) offset;
			long chunkSize =
					readUnsignedIntLittleEndian(audio, chunkOffset + 4);
			long chunkDataStart = offset + CHUNK_HEADER_SIZE;

			/*
			 * chunk size 是无符号 32-bit。始终先提升为 long 并检查完整边界，
			 * 只有确认位于 byte[] 内部后才转换成 int，防止整数回绕和越界读取。
			 */
			long chunkDataEnd = chunkDataStart + chunkSize;
			long paddingSize = chunkSize & 1L;
			long nextChunkOffset = chunkDataEnd + paddingSize;
			if (chunkDataEnd > audio.length
					|| nextChunkOffset > audio.length) {
				throw invalidAudio();
			}

			if (hasChunkId(audio, chunkOffset, "fmt ")) {
				if (fmtFound) {
					throw invalidAudio();
				}
				validateFormatChunk(
						audio,
						(int) chunkDataStart,
						chunkSize);
				fmtFound = true;
			} else if (hasChunkId(audio, chunkOffset, "data")) {
				if (dataFound) {
					throw invalidAudio();
				}
				validateDataChunk(chunkSize);
				dataFound = true;
			}

			/*
			 * RIFF chunk 的数据按偶数字节对齐；奇数长度必须包含一个 padding
			 * 字节，该字节不计入 chunk size，但计入 RIFF 总长度。
			 */
			offset = nextChunkOffset;
		}

		if (!fmtFound || !dataFound || offset != audio.length) {
			throw invalidAudio();
		}
	}

	private static void validateFormatChunk(
			byte[] audio,
			int chunkDataStart,
			long chunkSize) {
		if (chunkSize < MIN_FMT_CHUNK_SIZE) {
			throw invalidAudio();
		}

		int format = readUnsignedShortLittleEndian(audio, chunkDataStart);
		int channels =
				readUnsignedShortLittleEndian(audio, chunkDataStart + 2);
		long sampleRate =
				readUnsignedIntLittleEndian(audio, chunkDataStart + 4);
		long byteRate =
				readUnsignedIntLittleEndian(audio, chunkDataStart + 8);
		int blockAlign =
				readUnsignedShortLittleEndian(audio, chunkDataStart + 12);
		int bitsPerSample =
				readUnsignedShortLittleEndian(audio, chunkDataStart + 14);

		if (format != PCM_FORMAT
				|| channels != REQUIRED_CHANNELS
				|| sampleRate != REQUIRED_SAMPLE_RATE
				|| byteRate != REQUIRED_BYTE_RATE
				|| blockAlign != REQUIRED_BLOCK_ALIGN
				|| bitsPerSample != REQUIRED_BITS_PER_SAMPLE) {
			throw invalidAudio();
		}
	}

	private static void validateDataChunk(long chunkSize) {
		if (chunkSize == 0
				|| chunkSize > MAX_PCM_DATA_SIZE
				|| chunkSize % REQUIRED_BLOCK_ALIGN != 0) {
			throw invalidAudio();
		}
	}

	private static boolean hasChunkId(
			byte[] audio,
			int offset,
			String expected) {
		if (offset < 0 || audio.length - offset < expected.length()) {
			return false;
		}
		for (int index = 0; index < expected.length(); index++) {
			if ((audio[offset + index] & 0xff) != expected.charAt(index)) {
				return false;
			}
		}
		return true;
	}

	private static int readUnsignedShortLittleEndian(byte[] bytes, int offset) {
		return (bytes[offset] & 0xff)
				| ((bytes[offset + 1] & 0xff) << 8);
	}

	private static long readUnsignedIntLittleEndian(byte[] bytes, int offset) {
		return (bytes[offset] & 0xffL)
				| ((bytes[offset + 1] & 0xffL) << 8)
				| ((bytes[offset + 2] & 0xffL) << 16)
				| ((bytes[offset + 3] & 0xffL) << 24);
	}

	private static EvaluationException invalidAudio() {
		return new EvaluationException(EvaluationErrorCode.AUDIO_INVALID);
	}
}
