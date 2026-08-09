package com.unispeaking.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.unispeaking.common.exception.document.DocumentErrorCode;
import com.unispeaking.common.exception.ocr.OcrErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class OcrDocumentGlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void mapsAllOcrErrorCodesExplicitly() {
		assertMapping(OcrErrorCode.INPUT_REQUIRED, HttpStatus.BAD_REQUEST);
		assertMapping(OcrErrorCode.TOO_MANY_IMAGES, HttpStatus.BAD_REQUEST);
		assertMapping(OcrErrorCode.TOTAL_SIZE_EXCEEDED, HttpStatus.BAD_REQUEST);
		assertMapping(OcrErrorCode.PIXEL_LIMIT_EXCEEDED, HttpStatus.BAD_REQUEST);
		assertMapping(OcrErrorCode.FORMAT_UNSUPPORTED, HttpStatus.UNPROCESSABLE_ENTITY);
		assertMapping(OcrErrorCode.CONTENT_INVALID, HttpStatus.UNPROCESSABLE_ENTITY);
		assertMapping(OcrErrorCode.UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
		assertMapping(OcrErrorCode.TIMEOUT, HttpStatus.GATEWAY_TIMEOUT);
		assertMapping(OcrErrorCode.PROCESS_FAILED, HttpStatus.BAD_GATEWAY);
		assertMapping(OcrErrorCode.RESPONSE_INVALID, HttpStatus.BAD_GATEWAY);
	}

	@Test
	void mapsAllDocumentErrorCodesExplicitly() {
		assertMapping(DocumentErrorCode.INPUT_REQUIRED, HttpStatus.BAD_REQUEST);
		assertMapping(DocumentErrorCode.TOO_LARGE, HttpStatus.BAD_REQUEST);
		assertMapping(DocumentErrorCode.PDF_PAGE_LIMIT_EXCEEDED, HttpStatus.BAD_REQUEST);
		assertMapping(DocumentErrorCode.TEXT_EMPTY, HttpStatus.BAD_REQUEST);
		assertMapping(DocumentErrorCode.TEXT_TOO_LARGE, HttpStatus.BAD_REQUEST);
		assertMapping(DocumentErrorCode.FORMAT_UNSUPPORTED, HttpStatus.UNPROCESSABLE_ENTITY);
		assertMapping(DocumentErrorCode.CONTENT_INVALID, HttpStatus.UNPROCESSABLE_ENTITY);
	}

	@Test
	void mapsInterviewMaterialLlmResponseInvalidToBadRequest() {
		assertMapping(
				InterviewErrorCode.INTERVIEW_MATERIAL_LLM_RESPONSE_INVALID,
				HttpStatus.BAD_REQUEST);
	}

	private void assertMapping(OcrErrorCode code, HttpStatus expected) {
		assertMapping(code.code(), expected);
	}

	private void assertMapping(DocumentErrorCode code, HttpStatus expected) {
		assertMapping(code.code(), expected);
	}

	private void assertMapping(String code, HttpStatus expected) {
		var response = handler.handleBusinessException(
				new BusinessException(code, "test message"));
		assertEquals(expected, response.getStatusCode(), code);
		assertEquals(code, response.getBody().code());
	}
}
