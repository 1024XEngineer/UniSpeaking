package com.unispeaking.domain.dto.ocr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class OcrImageTest {

	@Test
	void defensivelyCopiesImageContent() {
		byte[] source = new byte[] {1, 2, 3};

		OcrImage image = new OcrImage(source);
		source[0] = 9;
		byte[] returned = image.content();
		returned[1] = 8;

		assertArrayEquals(new byte[] {1, 2, 3}, image.content());
	}
}
