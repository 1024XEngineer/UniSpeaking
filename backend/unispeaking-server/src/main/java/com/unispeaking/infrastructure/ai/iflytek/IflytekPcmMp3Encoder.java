package com.unispeaking.infrastructure.ai.iflytek;

import de.sciss.jump3r.lowlevel.LameEncoder;
import java.io.ByteArrayOutputStream;
import javax.sound.sampled.AudioFormat;

/**
 * 将评分模块约定的 16 kHz、16-bit、单声道 PCM 转为 Suntone 支持的 MP3。
 */
final class IflytekPcmMp3Encoder {

	private static final AudioFormat SOURCE_FORMAT =
			new AudioFormat(16_000, 16, 1, true, false);
	private static final int BIT_RATE_KBPS = 64;

	byte[] encode(byte[] pcm) {
		if (pcm == null || pcm.length == 0 || (pcm.length & 1) != 0) {
			throw new IllegalArgumentException(
					"PCM audio must contain complete 16-bit samples");
		}

		LameEncoder encoder = new LameEncoder(
				SOURCE_FORMAT,
				BIT_RATE_KBPS,
				LameEncoder.CHANNEL_MODE_MONO,
				LameEncoder.QUALITY_HIGH,
				false);
		try {
			byte[] encodedBuffer = new byte[encoder.getMP3BufferSize()];
			ByteArrayOutputStream output = new ByteArrayOutputStream(
					Math.max(1_024, pcm.length / 4));
			int pcmBufferSize = encoder.getPCMBufferSize();
			for (int offset = 0; offset < pcm.length;) {
				int length = Math.min(pcmBufferSize, pcm.length - offset);
				length -= length & 1;
				if (length == 0) {
					throw new IllegalArgumentException(
							"PCM audio ended with an incomplete sample");
				}
				int encoded = encoder.encodeBuffer(
						pcm,
						offset,
						length,
						encodedBuffer);
				if (encoded > 0) {
					output.write(encodedBuffer, 0, encoded);
				}
				offset += length;
			}
			int remaining = encoder.encodeFinish(encodedBuffer);
			if (remaining > 0) {
				output.write(encodedBuffer, 0, remaining);
			}
			byte[] mp3 = output.toByteArray();
			if (mp3.length == 0) {
				throw new IllegalStateException("MP3 encoder returned no audio");
			}
			return mp3;
		}
		finally {
			encoder.close();
		}
	}
}
