package com.unispeaking.provider;

import com.unispeaking.domain.dto.ocr.OcrImage;
import java.util.List;

public interface OcrProvider {

	String recognizeText(List<OcrImage> images);

	boolean available();
}
