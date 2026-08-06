package com.unispeaking.domain.vo.scene;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.common.exception.interview.InterviewErrorCode;
import com.unispeaking.common.exception.interview.InterviewException;
import com.unispeaking.common.exception.ocr.OcrErrorCode;
import com.unispeaking.common.exception.ocr.OcrException;
import com.unispeaking.domain.dto.ocr.OcrImage;
import com.unispeaking.provider.OcrProvider;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InterviewJobDescriptionOcrRecognizerTest {

	private static final String SUPPLEMENTARY_CODE_POINT = "\uD83D\uDE80";

	@Test
	void validatesBatchAndCallsProviderOnceInInputOrder() {
		StubOcrProvider provider = new StubOcrProvider();
		provider.response = "\u2003First line\r\nSecond line\u2003";
		InterviewJobDescriptionOcrRecognizer recognizer =
				new InterviewJobDescriptionOcrRecognizer(provider);

		InterviewJobDescriptionOcrText result = recognizer.recognize(List.of(
				new OcrImage(new byte[] {3}),
				new OcrImage(new byte[] {1}),
				new OcrImage(new byte[] {2})));

		assertAll(
				() -> assertEquals("First line\nSecond line", result.text()),
				() -> assertEquals(List.of(3, 1, 2), provider.firstBytes),
				() -> assertEquals(1, provider.recognitionCalls),
				() -> assertFalse(result.toString().contains("First line")));
	}

	@Test
	void rejectsInvalidBatchBeforeCheckingOrCallingProvider() {
		StubOcrProvider provider = new StubOcrProvider();
		InterviewJobDescriptionOcrRecognizer recognizer =
				new InterviewJobDescriptionOcrRecognizer(provider);
		List<OcrImage> sixImages = java.util.stream.IntStream.range(0, 6)
				.mapToObj(index -> new OcrImage(new byte[] {1}))
				.toList();

		assertError(
				InterviewErrorCode.INPUT_INVALID,
				() -> recognizer.recognize(sixImages));
		assertAll(
				() -> assertEquals(0, provider.availabilityChecks),
				() -> assertEquals(0, provider.recognitionCalls));
	}

	@Test
	void mapsUnavailableProviderWithoutCallingRecognition() {
		StubOcrProvider provider = new StubOcrProvider();
		provider.available = false;
		InterviewJobDescriptionOcrRecognizer recognizer =
				new InterviewJobDescriptionOcrRecognizer(provider);

		assertError(
				InterviewErrorCode.SERVICE_UNAVAILABLE,
				() -> recognizer.recognize(oneImage()));
		assertEquals(0, provider.recognitionCalls);
	}

	@Test
	void mapsProviderFormatPixelAvailabilityAndProcessFailures() {
		assertProviderError(OcrErrorCode.FORMAT_UNSUPPORTED,
				InterviewErrorCode.MEDIA_TYPE_UNSUPPORTED);
		assertProviderError(OcrErrorCode.PIXEL_LIMIT_EXCEEDED,
				InterviewErrorCode.INPUT_INVALID);
		assertProviderError(OcrErrorCode.UNAVAILABLE,
				InterviewErrorCode.SERVICE_UNAVAILABLE);
		assertProviderError(OcrErrorCode.PROCESS_FAILED,
				InterviewErrorCode.DEPENDENCY_FAILED);
	}

	@Test
	void mapsUnexpectedProviderFailureToDependencyFailed() {
		StubOcrProvider provider = new StubOcrProvider();
		provider.failure = new IllegalStateException("provider diagnostic");

		assertError(
				InterviewErrorCode.DEPENDENCY_FAILED,
				() -> new InterviewJobDescriptionOcrRecognizer(provider).recognize(oneImage()));
	}

	@Test
	void rejectsNullAndUnicodeBlankRecognitionResults() {
		StubOcrProvider nullProvider = new StubOcrProvider();
		nullProvider.response = null;
		StubOcrProvider blankProvider = new StubOcrProvider();
		blankProvider.response = "\u2003\n\u2003";

		assertAll(
				() -> assertError(
						InterviewErrorCode.INPUT_INVALID,
						() -> new InterviewJobDescriptionOcrRecognizer(nullProvider)
								.recognize(oneImage())),
				() -> assertError(
						InterviewErrorCode.INPUT_INVALID,
						() -> new InterviewJobDescriptionOcrRecognizer(blankProvider)
								.recognize(oneImage())));
	}

	@Test
	void countsRecognizedTextByUnicodeCodePoint() {
		StubOcrProvider maximumProvider = new StubOcrProvider();
		maximumProvider.response = SUPPLEMENTARY_CODE_POINT.repeat(
				InterviewJobDescriptionOcrRecognizer.MAX_TEXT_CODE_POINTS);
		StubOcrProvider overProvider = new StubOcrProvider();
		overProvider.response = SUPPLEMENTARY_CODE_POINT.repeat(
				InterviewJobDescriptionOcrRecognizer.MAX_TEXT_CODE_POINTS + 1);

		InterviewJobDescriptionOcrText result =
				new InterviewJobDescriptionOcrRecognizer(maximumProvider).recognize(oneImage());

		assertAll(
				() -> assertEquals(
						InterviewJobDescriptionOcrRecognizer.MAX_TEXT_CODE_POINTS,
						result.text().codePointCount(0, result.text().length())),
				() -> assertError(
						InterviewErrorCode.INPUT_INVALID,
						() -> new InterviewJobDescriptionOcrRecognizer(overProvider)
								.recognize(oneImage())));
	}

	private static List<OcrImage> oneImage() {
		return List.of(new OcrImage(new byte[] {1}));
	}

	private static void assertProviderError(
			OcrErrorCode source,
			InterviewErrorCode expected) {
		StubOcrProvider provider = new StubOcrProvider();
		provider.failure = new OcrException(source);
		assertError(
				expected,
				() -> new InterviewJobDescriptionOcrRecognizer(provider).recognize(oneImage()));
	}

	private static void assertError(
			InterviewErrorCode expected,
			org.junit.jupiter.api.function.Executable action) {
		InterviewException exception = assertThrows(InterviewException.class, action);
		assertSame(expected, exception.errorCode());
	}

	private static final class StubOcrProvider implements OcrProvider {

		private boolean available = true;
		private String response = "recognized";
		private RuntimeException failure;
		private int availabilityChecks;
		private int recognitionCalls;
		private List<Integer> firstBytes = new ArrayList<>();

		@Override
		public String recognizeText(List<OcrImage> images) {
			recognitionCalls++;
			firstBytes = images.stream()
					.map(image -> Byte.toUnsignedInt(image.content()[0]))
					.toList();
			if (failure != null) {
				throw failure;
			}
			return response;
		}

		@Override
		public boolean available() {
			availabilityChecks++;
			return available;
		}
	}
}
