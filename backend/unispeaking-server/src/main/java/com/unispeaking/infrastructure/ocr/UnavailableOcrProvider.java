package com.unispeaking.infrastructure.ocr;

import com.unispeaking.common.exception.ocr.OcrErrorCode;
import com.unispeaking.common.exception.ocr.OcrException;
import com.unispeaking.domain.dto.ocr.OcrImage;
import com.unispeaking.provider.OcrProvider;
import java.util.List;

public final class UnavailableOcrProvider implements OcrProvider {

	@Override
	public String recognizeText(List<OcrImage> images) {
		throw new OcrException(OcrErrorCode.UNAVAILABLE);
	}

	@Override
	public boolean available() {
		return false;
	}
}
