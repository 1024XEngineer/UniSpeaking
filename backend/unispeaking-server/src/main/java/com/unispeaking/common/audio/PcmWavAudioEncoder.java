package com.unispeaking.common.audio;

import com.unispeaking.common.exception.audio.AudioErrorCode;
import com.unispeaking.common.exception.audio.AudioException;
import java.time.Duration;
import java.util.List;

/**
 * 将一组 PCM WAV 按输入顺序标准化并编码为 MP3。
 */
public final class PcmWavAudioEncoder {

	private static final int OUTPUT_SAMPLE_RATE = 16_000;
	private static final String OUTPUT_MEDIA_TYPE = "audio/mpeg";
	private static final long NANOS_PER_OUTPUT_SAMPLE = 62_500L;

	private final PcmMp3Encoder mp3Encoder = new PcmMp3Encoder();

	/**
	 * 标准化并顺序拼接所有输入；时长按最终 16 kHz PCM 样本数计算。
	 */
	public EncodedAudio encode(List<byte[]> wavSegments) {
		NormalizedPcm normalized = normalize(wavSegments);
		try {
			byte[] mp3 = mp3Encoder.encode(normalized.content());
			Duration duration = Duration.ofNanos(
					normalized.sampleCount() * NANOS_PER_OUTPUT_SAMPLE);
			return new EncodedAudio(mp3, OUTPUT_MEDIA_TYPE, duration);
		}
		catch (RuntimeException exception) {
			throw new AudioException(AudioErrorCode.ENCODING_FAILED, exception);
		}
	}

	static NormalizedPcm normalize(List<byte[]> wavSegments) {
		if (wavSegments == null || wavSegments.isEmpty()) {
			throw new AudioException(AudioErrorCode.INPUT_REQUIRED);
		}

		short[][] normalizedSegments = new short[wavSegments.size()][];
		long totalSamples = 0;
		for (int index = 0; index < wavSegments.size(); index++) {
			PcmWavData wav = PcmWavParser.parse(wavSegments.get(index));
			short[] mono = toMono(wav);
			short[] resampled = resample(mono, wav.sampleRate());
			normalizedSegments[index] = resampled;
			totalSamples += resampled.length;
			if (totalSamples > Integer.MAX_VALUE / Short.BYTES) {
				throw new AudioException(AudioErrorCode.CONTENT_INVALID);
			}
		}

		byte[] pcm = new byte[(int) totalSamples * Short.BYTES];
		int outputOffset = 0;
		for (short[] segment : normalizedSegments) {
			for (short sample : segment) {
				pcm[outputOffset++] = (byte) sample;
				pcm[outputOffset++] = (byte) (sample >>> 8);
			}
		}
		return new NormalizedPcm(pcm, totalSamples);
	}

	private static short[] toMono(PcmWavData wav) {
		byte[] pcm = wav.pcm();
		int frameSize = wav.channels() * Short.BYTES;
		int frameCount = pcm.length / frameSize;
		short[] mono = new short[frameCount];
		for (int frame = 0; frame < frameCount; frame++) {
			int offset = frame * frameSize;
			short left = readSample(pcm, offset);
			if (wav.channels() == 1) {
				mono[frame] = left;
			}
			else {
				short right = readSample(pcm, offset + Short.BYTES);
				mono[frame] = (short) (((int) left + right) / 2);
			}
		}
		return mono;
	}

	private static short[] resample(short[] source, int sourceSampleRate) {
		if (sourceSampleRate == OUTPUT_SAMPLE_RATE) {
			return source;
		}
		long targetSampleCount = Math.max(
				1L,
				((long) source.length * OUTPUT_SAMPLE_RATE
						+ sourceSampleRate / 2L) / sourceSampleRate);
		if (targetSampleCount > Integer.MAX_VALUE) {
			throw new AudioException(AudioErrorCode.CONTENT_INVALID);
		}
		short[] target = new short[(int) targetSampleCount];
		for (int index = 0; index < target.length; index++) {
			long sourcePosition = (long) index * sourceSampleRate;
			int lowerIndex = (int) Math.min(
					sourcePosition / OUTPUT_SAMPLE_RATE,
					source.length - 1L);
			int upperIndex = Math.min(lowerIndex + 1, source.length - 1);
			long fraction = sourcePosition % OUTPUT_SAMPLE_RATE;
			long weighted = (long) source[lowerIndex]
					* (OUTPUT_SAMPLE_RATE - fraction)
					+ (long) source[upperIndex] * fraction;
			target[index] = (short) divideRounded(
					weighted,
					OUTPUT_SAMPLE_RATE);
		}
		return target;
	}

	private static long divideRounded(long value, long divisor) {
		return value >= 0
				? (value + divisor / 2) / divisor
				: (value - divisor / 2) / divisor;
	}

	private static short readSample(byte[] pcm, int offset) {
		return (short) ((pcm[offset] & 0xff)
				| ((pcm[offset + 1] & 0xff) << 8));
	}

	record NormalizedPcm(byte[] content, long sampleCount) {
	}
}
