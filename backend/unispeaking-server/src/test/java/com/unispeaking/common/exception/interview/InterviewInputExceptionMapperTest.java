package com.unispeaking.common.exception.interview;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.unispeaking.common.exception.document.DocumentErrorCode;
import com.unispeaking.common.exception.document.DocumentException;
import com.unispeaking.common.exception.ocr.OcrErrorCode;
import com.unispeaking.common.exception.ocr.OcrException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InterviewInputExceptionMapperTest {

	@Test
	void mapsEveryDocumentFailureToStableInterviewError() {
		Map<DocumentErrorCode, InterviewErrorCode> expected = Map.of(
				DocumentErrorCode.INPUT_REQUIRED, InterviewErrorCode.INPUT_INVALID,
				DocumentErrorCode.TOO_LARGE, InterviewErrorCode.PAYLOAD_TOO_LARGE,
				DocumentErrorCode.FORMAT_UNSUPPORTED,
				InterviewErrorCode.MEDIA_TYPE_UNSUPPORTED,
				DocumentErrorCode.CONTENT_INVALID, InterviewErrorCode.INPUT_INVALID,
				DocumentErrorCode.PDF_PAGE_LIMIT_EXCEEDED,
				InterviewErrorCode.INPUT_INVALID,
				DocumentErrorCode.TEXT_EMPTY, InterviewErrorCode.INPUT_INVALID,
				DocumentErrorCode.TEXT_TOO_LARGE, InterviewErrorCode.INPUT_INVALID);

		for (DocumentErrorCode source : DocumentErrorCode.values()) {
			DocumentException cause = new DocumentException(source);
			InterviewException mapped = InterviewInputExceptionMapper.fromDocument(cause);

			assertAll(
					() -> assertSame(expected.get(source), mapped.errorCode()),
					() -> assertSame(cause, mapped.getCause()));
		}
	}

	@Test
	void mapsEveryOcrFailureToStableInterviewError() {
		Map<OcrErrorCode, InterviewErrorCode> expected = Map.ofEntries(
				Map.entry(OcrErrorCode.INPUT_REQUIRED, InterviewErrorCode.INPUT_INVALID),
				Map.entry(OcrErrorCode.TOO_MANY_IMAGES, InterviewErrorCode.INPUT_INVALID),
				Map.entry(OcrErrorCode.TOTAL_SIZE_EXCEEDED,
						InterviewErrorCode.PAYLOAD_TOO_LARGE),
				Map.entry(OcrErrorCode.FORMAT_UNSUPPORTED,
						InterviewErrorCode.MEDIA_TYPE_UNSUPPORTED),
				Map.entry(OcrErrorCode.CONTENT_INVALID, InterviewErrorCode.INPUT_INVALID),
				Map.entry(OcrErrorCode.PIXEL_LIMIT_EXCEEDED,
						InterviewErrorCode.INPUT_INVALID),
				Map.entry(OcrErrorCode.UNAVAILABLE, InterviewErrorCode.SERVICE_UNAVAILABLE),
				Map.entry(OcrErrorCode.TIMEOUT, InterviewErrorCode.DEPENDENCY_FAILED),
				Map.entry(OcrErrorCode.PROCESS_FAILED, InterviewErrorCode.DEPENDENCY_FAILED),
				Map.entry(OcrErrorCode.RESPONSE_INVALID, InterviewErrorCode.DEPENDENCY_FAILED));

		for (OcrErrorCode source : OcrErrorCode.values()) {
			OcrException cause = new OcrException(source);
			InterviewException mapped = InterviewInputExceptionMapper.fromOcr(cause);

			assertAll(
					() -> assertSame(expected.get(source), mapped.errorCode()),
					() -> assertSame(cause, mapped.getCause()));
		}
	}
}
