package com.unispeaking.common.audio;

import java.time.Duration;
import java.util.Objects;

/**
 * 已编码音频的最小结果。
 *
 * @param content 编码后的完整音频内容
 * @param mediaType 音频媒体类型
 * @param duration 由标准化 PCM 样本数精确计算的时长
 */
public record EncodedAudio(
		byte[] content,
		String mediaType,
		Duration duration) {

	public EncodedAudio {
		Objects.requireNonNull(content, "encoded audio content is required");
		Objects.requireNonNull(mediaType, "encoded audio media type is required");
		Objects.requireNonNull(duration, "encoded audio duration is required");
		if (content.length == 0) {
			throw new IllegalArgumentException(
					"encoded audio content must not be empty");
		}
		if (mediaType.isBlank()) {
			throw new IllegalArgumentException(
					"encoded audio media type must not be blank");
		}
		if (duration.isZero() || duration.isNegative()) {
			throw new IllegalArgumentException(
					"encoded audio duration must be positive");
		}
		content = content.clone();
		mediaType = mediaType.trim();
	}

	@Override
	public byte[] content() {
		return content.clone();
	}
}
