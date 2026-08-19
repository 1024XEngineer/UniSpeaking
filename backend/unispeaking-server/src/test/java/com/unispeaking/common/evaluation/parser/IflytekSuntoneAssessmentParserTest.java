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

	private String envelope(String decodedResult) {
		String encoded = Base64.getEncoder().encodeToString(
				decodedResult.getBytes(StandardCharsets.UTF_8));
		return """
				{
				  "header": {
				    "code": 0,
				    "message": "success",
				    "status": 2
				  },
				  "payload": {
				    "result": {
				      "status": 2,
				      "text": "%s"
				    }
				  }
				}
				""".formatted(encoded);
	}
}
