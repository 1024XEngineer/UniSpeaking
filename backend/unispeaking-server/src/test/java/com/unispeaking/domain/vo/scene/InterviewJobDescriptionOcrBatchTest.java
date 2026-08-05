package com.unispeaking.domain.vo.scene;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.common.exception.interview.InterviewErrorCode;
import com.unispeaking.common.exception.interview.InterviewException;
import com.unispeaking.domain.dto.ocr.OcrImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class InterviewJobDescriptionOcrBatchTest {

	@Test
	void acceptsFiveImagesAtExactTotalSizeAndKeepsAnImmutableSnapshot() {
		byte[] content = new byte[InterviewJobDescriptionOcrBatch.MAX_TOTAL_BYTES / 5];
		content[0] = 1;
		List<OcrImage> images = new ArrayList<>();
		for (int index = 0; index < InterviewJobDescriptionOcrBatch.MAX_IMAGE_COUNT; index++) {
			images.add(new OcrImage(content));
		}

		InterviewJobDescriptionOcrBatch batch = new InterviewJobDescriptionOcrBatch(images);
		content[0] = 9;
		images.clear();

		assertAll(
				() -> assertEquals(5, batch.images().size()),
				() -> assertEquals(
						InterviewJobDescriptionOcrBatch.MAX_TOTAL_BYTES,
						batch.totalBytes()),
				() -> assertEquals(1, batch.images().getFirst().content()[0]),
				() -> assertThrows(
						UnsupportedOperationException.class,
						() -> batch.images().clear()),
				() -> assertFalse(batch.toString().contains("[B@")));
	}

	@Test
	void rejectsMissingImagesAndImagesOverBatchLimits() {
		List<OcrImage> sixImages = java.util.stream.IntStream.range(0, 6)
				.mapToObj(index -> new OcrImage(new byte[] {1}))
				.toList();
		byte[] overLimit = new byte[InterviewJobDescriptionOcrBatch.MAX_TOTAL_BYTES + 1];

		assertAll(
				() -> assertError(null, InterviewErrorCode.INPUT_INVALID),
				() -> assertError(List.of(), InterviewErrorCode.INPUT_INVALID),
				() -> assertError(
						Arrays.asList(new OcrImage(new byte[] {1}), null),
						InterviewErrorCode.INPUT_INVALID),
				() -> assertError(
						List.of(new OcrImage(new byte[0])),
						InterviewErrorCode.INPUT_INVALID),
				() -> assertError(sixImages, InterviewErrorCode.INPUT_INVALID),
				() -> assertError(
						List.of(new OcrImage(overLimit)),
						InterviewErrorCode.PAYLOAD_TOO_LARGE));
	}

	private static void assertError(
			List<OcrImage> images,
			InterviewErrorCode expected) {
		InterviewException exception = assertThrows(
				InterviewException.class,
				() -> new InterviewJobDescriptionOcrBatch(images));
		assertSame(expected, exception.errorCode());
	}
}
