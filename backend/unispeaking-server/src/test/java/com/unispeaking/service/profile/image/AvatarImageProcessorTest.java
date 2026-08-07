package com.unispeaking.service.profile.image;

import com.unispeaking.component.profile.image.AvatarImageProcessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.common.exception.BusinessException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class AvatarImageProcessorTest {

	private final AvatarImageProcessor processor = new AvatarImageProcessor();

	@Test
	void preservesAlphaImagesAsPng() throws IOException {
		byte[] input = image("png", BufferedImage.TYPE_INT_ARGB, 256, 256);

		var result = processor.process(input);

		assertEquals("png", result.extension());
		assertEquals("image/png", result.contentType());
		assertTrue(result.content().length > 0);
	}

	@Test
	void convertsOpaqueImagesToJpeg() throws IOException {
		byte[] input = image("jpg", BufferedImage.TYPE_INT_RGB, 256, 256);

		var result = processor.process(input);

		assertEquals("jpg", result.extension());
		assertEquals("image/jpeg", result.contentType());
		assertTrue(result.content().length > 0);
	}

	@Test
	void requiresAvatarContent() {
		assertCode("AVATAR_FILE_REQUIRED", () -> processor.process(null));
		assertCode("AVATAR_FILE_REQUIRED", () -> processor.process(new byte[0]));
	}

	@Test
	void rejectsOversizedAvatar() {
		assertCode(
				"AVATAR_FILE_TOO_LARGE",
				() -> processor.process(new byte[2 * 1024 * 1024 + 1]));
	}

	@Test
	void rejectsUnsupportedHeader() {
		assertCode(
				"AVATAR_TYPE_UNSUPPORTED",
				() -> processor.process(new byte[] {1, 2, 3, 4}));
	}

	@Test
	void rejectsUnreadableImageWithSupportedHeader() {
		byte[] pngHeader = {
				(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
		};

		assertCode("AVATAR_CONTENT_INVALID", () -> processor.process(pngHeader));
	}

	@Test
	void rejectsImageOutsideAllowedDimensions() throws IOException {
		byte[] tooSmall = image("png", BufferedImage.TYPE_INT_ARGB, 127, 128);

		assertCode("AVATAR_DIMENSION_INVALID", () -> processor.process(tooSmall));
	}

	private byte[] image(String format, int type, int width, int height)
			throws IOException {
		BufferedImage image = new BufferedImage(width, height, type);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(Color.BLUE);
		graphics.fillRect(0, 0, width, height);
		graphics.dispose();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, format, output);
		return output.toByteArray();
	}

	private void assertCode(String code, Runnable action) {
		BusinessException exception = assertThrows(BusinessException.class, action::run);
		assertEquals(code, exception.code());
	}
}
