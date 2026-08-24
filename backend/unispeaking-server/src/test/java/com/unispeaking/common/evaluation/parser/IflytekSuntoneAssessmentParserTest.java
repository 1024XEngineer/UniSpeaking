package com.unispeaking.common.evaluation.parser;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.common.evaluation.model.EndingTone;
import com.unispeaking.common.evaluation.model.PronunciationAssessmentResult;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class IflytekSuntoneAssessmentParserTest {

	private final IflytekSuntoneAssessmentParser parser =
			new IflytekSuntoneAssessmentParser(
					new ObjectMapper());

	@Test
	void parsesSentenceScoresWordsAndPhonemes() {
		PronunciationAssessmentResult result =
				parser.parse(envelope("""
						{
						  "eof": 1,
						  "result": {
						    "overall": 91.5,
						    "rhythm": 84,
						    "integrity": 99,
						    "pronunciation": 88.4,
						    "fluency": 90,
						    "rear_tone": "rise",
						    "words": [
						      {
						        "charType": 0,
						        "word": "hello",
						        "readType": 0,
						        "scores": {
						          "overall": 90,
						          "pronunciation": 89,
						          "prominence": 1
						        },
						        "phonemes": [
						          {
						            "phone": "h",
						            "phoneme": "h",
						            "pronunciation": 93.2,
						            "span": {"start": 3, "end": 15}
						          }
						        ]
						      },
						      {
						        "charType": 1,
						        "word": ".",
						        "scores": {
						          "overall": 0,
						          "pronunciation": 0
						        },
						        "phonemes": []
						      }
						    ]
						  }
						}
						"""));

		assertAll(
				() -> assertEquals(
						new BigDecimal("91.50"),
						result.overallScore()),
				() -> assertEquals(
						new BigDecimal("84.00"),
						result.rhythmScore()),
				() -> assertNull(result.toneScore()),
				() -> assertSame(
						EndingTone.RISE,
						result.endingTone()),
				() -> assertEquals(1, result.words().size()),
				() -> assertEquals(
						"h",
						result.words().getFirst()
								.phonemes().getFirst()
								.expectedPhoneme()),
				() -> assertEquals(
						new BigDecimal("93.20"),
						result.words().getFirst()
								.phonemes().getFirst()
								.pronunciationScore()),
				() -> assertEquals(
						3,
						result.words().getFirst()
								.phonemes().getFirst()
								.startPosition()),
				() -> assertEquals(
						15,
						result.words().getFirst()
								.phonemes().getFirst()
								.endPosition()));
	}

	@Test
	void rejectsProviderFailureAndIncompletePhonemes() {
		EvaluationException providerFailure = assertThrows(
				EvaluationException.class,
				() -> parser.parse("""
						{"header":{"code":1001,"status":2}}
						"""));
		EvaluationException incomplete = assertThrows(
				EvaluationException.class,
				() -> parser.parse(envelope("""
						{
						  "result": {
						    "overall": 90,
						    "rhythm": 90,
						    "integrity": 90,
						    "pronunciation": 90,
						    "fluency": 90,
						    "words": [{
						      "word": "test",
						      "scores": {
						        "overall": 90,
						        "pronunciation": 90
						      }
						    }]
						  }
						}
						""")));

		assertSame(
				EvaluationErrorCode.PROVIDER_CALL_FAILED,
				providerFailure.errorCode());
		assertSame(
				EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE,
				incomplete.errorCode());
	}

	@Test
	void preservesUnmatchedPhonemeSpanSentinel() {
		PronunciationAssessmentResult result =
				parser.parse(envelope("""
						{
						  "result": {
						    "overall": 82,
						    "rhythm": 80,
						    "integrity": 85,
						    "pronunciation": 81,
						    "fluency": 83,
						    "words": [{
						      "word": "test",
						      "scores": {
						        "overall": 82,
						        "pronunciation": 81
						      },
						      "phonemes": [{
						        "phone": "t",
						        "phoneme": "t",
						        "pronunciation": 0,
						        "span": {"start": -1, "end": -1}
						      }]
						    }]
						  }
						}
						"""));

		assertAll(
				() -> assertEquals(
						-1,
						result.words().getFirst()
								.phonemes().getFirst()
								.startPosition()),
				() -> assertEquals(
						-1,
						result.words().getFirst()
								.phonemes().getFirst()
						.endPosition()));
	}

	@Test
	void mapsOptionalFieldsAndAllNonDefaultWordBranches() {
		PronunciationAssessmentResult result =
				parser.parse(envelope("""
						{
						  "result": {
						    "overall": 80,
						    "rhythm": 81,
						    "tone": 12.345,
						    "integrity": 82,
						    "pronunciation": 83,
						    "fluency": 84,
						    "rear_tone": " FALL ",
						    "words": [
						      {
						        "word": "first",
						        "readType": 1,
						        "scores": {"overall": 80, "pronunciation": 81, "prominence": 0},
						        "phonemes": [{
						          "phoneme": "f",
						          "phone": "  ",
						          "pronunciation": 82,
						          "span": {"start": 1, "end": 2}
						        }]
						      },
						      {
						        "word": "second",
						        "readType": 7,
						        "scores": {"overall": 83, "pronunciation": 84},
						        "phonemes": [{
						          "phoneme": "s",
						          "phone": 7,
						          "pronunciation": 85,
						          "span": {"start": 2, "end": 5}
						        }]
						      },
						      {"charType": 1}
						    ]
						  }
						}
						"""));

		assertAll(
				() -> assertEquals(new BigDecimal("12.35"), result.toneScore()),
				() -> assertSame(EndingTone.FALL, result.endingTone()),
				() -> assertSame(
						com.unispeaking.common.evaluation.model.WordReadStatus.INSERTION_BEFORE,
						result.words().get(0).readStatus()),
				() -> assertSame(
						com.unispeaking.common.evaluation.model.WordReadStatus.OMITTED,
						result.words().get(1).readStatus()),
				() -> assertEquals(Boolean.FALSE, result.words().get(0).isProminent()),
				() -> assertNull(result.words().get(1).isProminent()),
				() -> assertEquals("f", result.words().get(0).phonemes().getFirst().actualPhoneme()),
				() -> assertEquals("s", result.words().get(1).phonemes().getFirst().actualPhoneme()));
	}

	@Test
	void rejectsMalformedEnvelopeAndInvalidAssessmentFields() {
		assertCode(
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
				() -> parser.parse("not-json"));
		assertCode(
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
				() -> parser.parse("[]"));
		assertCode(
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
				() -> parser.parse("{"));
		assertCode(
				EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE,
				() -> parser.parse("{\"header\":{\"code\":0,\"status\":1}}"));
		assertCode(
				EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE,
				() -> parser.parse(encodedEnvelope("{\"result\":{\"status\":1}}")));
		assertCode(
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
				() -> parser.parse(encodedEnvelopeWithText("not-base64")));
		assertCode(
				EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE,
				() -> parser.parse(encodedEnvelope("{\"result\":{\"words\":[]}}")));
		assertCode(
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
				() -> parser.parse(encodedEnvelope("""
						{"result":{"overall":-1,"rhythm":80,"integrity":80,
						"pronunciation":80,"fluency":80,"words":[
						{"word":"x","scores":{"overall":80,"pronunciation":80},
						"phonemes":[{"phoneme":"x","pronunciation":80,
						"span":{"start":2,"end":2}}]}]}}
						""")));
		assertCode(
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
				() -> parser.parse(encodedEnvelope("""
						{"result":{"overall":80,"rhythm":80,"integrity":80,
						"pronunciation":80,"fluency":80,"words":[
						{"word":"x","scores":{"overall":80,"pronunciation":80,
						"prominence":2},"phonemes":[{"phoneme":"x",
						"pronunciation":80,"span":{"start":0,"end":1}}]}]}}
						""")));
	}

	@Test
	void rejectsMissingAndWronglyTypedRequiredValues() {
		assertCode(
				EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE,
				() -> parser.parse("{\"header\":{\"code\":0}}"));
		assertCode(
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
				() -> parser.parse("{\"header\":{\"code\":\"0\",\"status\":2}}"));
		assertCode(
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
				() -> parser.parse(encodedEnvelopeWithText("")));
		assertCode(
				EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE,
				() -> parser.parse(encodedEnvelope("{\"result\":{\"words\":{}}}")));
		assertCode(
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
				() -> parser.parse(encodedEnvelope("""
						{"result":{"overall":80,"rhythm":80,"integrity":80,
						"pronunciation":80,"fluency":80,"rear_tone":7,"words":[
						{"word":"x","scores":{"overall":80,"pronunciation":80},
						"phonemes":[{"phoneme":"x","pronunciation":80,
						"span":{"start":0,"end":1}}]}]}}
						""")));
	}

	private void assertCode(
				EvaluationErrorCode expected,
				org.junit.jupiter.api.function.Executable action) {
		EvaluationException exception = assertThrows(
				EvaluationException.class,
				action);
		assertSame(expected, exception.errorCode());
	}

	private String encodedEnvelopeWithText(String text) {
		return """
				{"header":{"code":0,"status":2},"payload":{"result":{"status":2,"text":"%s"}}}
				""".formatted(text);
	}

	private String encodedEnvelope(String decodedResult) {
		String encoded = Base64.getEncoder().encodeToString(
				decodedResult.getBytes(StandardCharsets.UTF_8));
		return encodedEnvelopeWithText(encoded);
	}

	private String envelope(String decodedResult) {
		return encodedEnvelope(decodedResult);
	}
}
