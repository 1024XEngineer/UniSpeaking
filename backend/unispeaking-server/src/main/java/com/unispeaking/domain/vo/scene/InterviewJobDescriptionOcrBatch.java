package com.unispeaking.domain.vo.scene;

import com.unispeaking.common.exception.interview.InterviewErrorCode;
import com.unispeaking.common.exception.interview.InterviewException;
import com.unispeaking.domain.dto.ocr.OcrImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Validated transient JD screenshot batch. It has no persistence representation.
 */
public final class InterviewJobDescriptionOcrBatch {

	static final int MAX_IMAGE_COUNT = 5;
	static final int MAX_TOTAL_BYTES = 10 * 1024 * 1024;

	private final List<OcrImage> images;
	private final int totalBytes;

	public InterviewJobDescriptionOcrBatch(List<OcrImage> images) {
		if (images == null || images.isEmpty()) {
			throw new InterviewException(InterviewErrorCode.INPUT_INVALID);
		}
		if (images.size() > MAX_IMAGE_COUNT) {
			throw new InterviewException(InterviewErrorCode.INPUT_INVALID);
		}
		long totalBytes = 0;
		List<OcrImage> copied = new ArrayList<>(images.size());
		for (OcrImage image : images) {
			if (image == null) {
				throw new InterviewException(InterviewErrorCode.INPUT_INVALID);
			}
			byte[] content = image.content();
			if (content == null || content.length == 0) {
				throw new InterviewException(InterviewErrorCode.INPUT_INVALID);
			}
			totalBytes += content.length;
			if (totalBytes > MAX_TOTAL_BYTES) {
				throw new InterviewException(InterviewErrorCode.PAYLOAD_TOO_LARGE);
			}
			copied.add(new OcrImage(content));
		}
		this.images = List.copyOf(copied);
		this.totalBytes = (int) totalBytes;
	}

	public List<OcrImage> images() {
		return images;
	}

	public int totalBytes() {
		return totalBytes;
	}

	@Override
	public String toString() {
		return "InterviewJobDescriptionOcrBatch[imageCount=" + images.size()
				+ ", totalBytes=" + totalBytes() + "]";
	}
}
