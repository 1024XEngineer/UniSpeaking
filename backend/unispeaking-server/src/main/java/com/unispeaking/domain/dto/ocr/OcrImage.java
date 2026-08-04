package com.unispeaking.domain.dto.ocr;

import java.util.Arrays;

/**
 * 图片 OCR 的供应商无关输入。
 */
public record OcrImage(byte[] content) {

	public OcrImage {
		content = content == null ? null : Arrays.copyOf(content, content.length);
	}

	@Override
	public byte[] content() {
		return content == null ? null : Arrays.copyOf(content, content.length);
	}
}
