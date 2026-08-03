package com.unispeaking.service.profile.image;

import com.unispeaking.common.exception.BusinessException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

@Component
public class AvatarImageProcessor {
	private static final int MAX_BYTES = 2 * 1024 * 1024;
	private static final int MIN_DIMENSION = 128;
	private static final int MAX_DIMENSION = 4096;

	public ProcessedAvatar process(byte[] input) {
		if (input == null || input.length == 0) {
			throw error("AVATAR_FILE_REQUIRED", "请选择头像文件");
		}
		if (input.length > MAX_BYTES) {
			throw error("AVATAR_FILE_TOO_LARGE", "头像不能超过 2 MiB");
		}
		if (!hasJpegHeader(input) && !hasPngHeader(input)) {
			throw error("AVATAR_TYPE_UNSUPPORTED", "仅支持 JPEG 和 PNG 头像");
		}
		try {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(input));
			if (image == null) {
				throw error("AVATAR_CONTENT_INVALID", "头像内容无法识别");
			}
			if (image.getWidth() < MIN_DIMENSION || image.getHeight() < MIN_DIMENSION
					|| image.getWidth() > MAX_DIMENSION || image.getHeight() > MAX_DIMENSION) {
				throw error("AVATAR_DIMENSION_INVALID", "头像尺寸必须在 128 到 4096 像素之间");
			}
			String format = image.getColorModel().hasAlpha() ? "png" : "jpg";
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			if (!ImageIO.write(image, format, output)) {
				throw error("AVATAR_TYPE_UNSUPPORTED", "仅支持 JPEG 和 PNG 头像");
			}
			return new ProcessedAvatar(
					output.toByteArray(),
					format.equals("png") ? "image/png" : "image/jpeg",
					format);
		}
		catch (IOException exception) {
			throw error("AVATAR_CONTENT_INVALID", "头像内容无法识别");
		}
	}

	private boolean hasJpegHeader(byte[] input) {
		return input.length >= 3
				&& (input[0] & 0xff) == 0xff
				&& (input[1] & 0xff) == 0xd8
				&& (input[2] & 0xff) == 0xff;
	}

	private boolean hasPngHeader(byte[] input) {
		byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
		if (input.length < signature.length) return false;
		for (int index = 0; index < signature.length; index++) {
			if (input[index] != signature[index]) return false;
		}
		return true;
	}

	private BusinessException error(String code, String message) {
		return new BusinessException(code, message);
	}

	public record ProcessedAvatar(byte[] content, String contentType, String extension) {
	}
}
