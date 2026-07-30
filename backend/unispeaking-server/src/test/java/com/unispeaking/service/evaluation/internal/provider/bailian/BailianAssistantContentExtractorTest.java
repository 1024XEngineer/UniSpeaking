package com.unispeaking.service.evaluation.internal.provider.bailian;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.service.evaluation.internal.exception.EvaluationErrorCode;
import com.unispeaking.service.evaluation.internal.exception.EvaluationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * 验证百炼响应 envelope 的唯一 choice、完成原因和内容安全边界。
 */
class BailianAssistantContentExtractorTest {

	private final BailianAssistantContentExtractor extractor =
			new BailianAssistantContentExtractor(new ObjectMapper());

	@Test
	void extractsContentFromCompleteBailianResponseFixture() {
		String content = extractor.extract(fixture("llm-success.json"));

		assertEquals(
				"""
				{"feedbackSummary":"表达清晰","suggestedExpression":"I would like a coffee."}""",
				content);
	}

	@Test
	void preservesCompleteJsonCodeFenceForLaterEvaluationParser() {
		String response = """
				{
				  "output": {
				    "choices": [{
				      "finish_reason": "stop",
				      "message": {
				        "role": "assistant",
				        "content": "  ```json\\n{\\\"feedbackSummary\\\":\\\"清晰\\\"}\\n```  "
				      }
				    }]
				  }
				}
				""";

		assertEquals(
				"""
				```json
				{"feedbackSummary":"清晰"}
				```""",
				extractor.extract(response));
	}

	@Test
	void rejectsEmptyMalformedAndNonObjectResponses() {
		assertAll(
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE,
						null),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE,
						" \t\n"),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
						"{not-json"),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
						"[]"),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
						"null"));
	}

	@Test
	void mapsProviderErrorEnvelopeWithoutExposingSensitiveContent() {
		EvaluationException fixtureException = assertError(
				EvaluationErrorCode.PROVIDER_CALL_FAILED,
				fixture("llm-error.json"));
		EvaluationException contradictoryException = assertError(
				EvaluationErrorCode.PROVIDER_CALL_FAILED,
				"""
					{
					  "code": "InvalidApiKey",
					  "message": "secret-provider-detail",
					  "output": {
					    "choices": [{
					      "finish_reason": "stop",
					      "message": {"role": "assistant", "content": "secret-content"}
					    }]
					  }
					}
					""");

		assertAll(
				() -> assertFalse(fixtureException.getMessage().contains("InvalidApiKey")),
				() -> assertFalse(fixtureException.getMessage().contains("fixture-secret")),
				() -> assertFalse(contradictoryException.getMessage().contains("secret-provider")),
				() -> assertFalse(contradictoryException.getMessage().contains("secret-content")));
	}

	@Test
	void requiresExactlyOneChoice() {
		assertAll(
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE,
						"{}"),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE,
						"""
							{"output": null}
							"""),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
						"""
							{"output": []}
							"""),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE,
						"""
							{"output": {}}
							"""),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE,
						"""
							{"output": {"choices": null}}
							"""),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE,
						"""
							{"output": {"choices": []}}
							"""),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
						"""
							{"output": {"choices": {}}}
							"""),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
						"""
							{
							  "output": {
							    "choices": [
							      {"finish_reason":"stop","message":{"content":"first"}},
							      {"finish_reason":"stop","message":{"content":"second"}}
							    ]
							  }
							}
							"""),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
						"""
							{"output": {"choices": ["not-an-object"]}}
							"""));
	}

	@Test
	void acceptsOnlyStopFinishReason() {
		assertAll(
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE,
						responseWithChoice("""
								"message": {"content": "{}"}""")),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE,
						responseWithChoice("""
								"finish_reason": null,
								"message": {"content": "{}"}""")),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
						responseWithChoice("""
								"finish_reason": 1,
								"message": {"content": "{}"}""")),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE,
						responseWithChoice("""
								"finish_reason": "length",
								"message": {"content": "{}"}""")),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE,
						responseWithChoice("""
								"finish_reason": "content_filter",
								"message": {"content": "{}"}""")),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE,
						responseWithChoice("""
								"finish_reason": "STOP",
								"message": {"content": "{}"}""")));
	}

	@Test
	void validatesMessageRoleWhenProviderSuppliesIt() {
		assertAll(
				() -> assertEquals(
						"{}",
						extractor.extract(responseWithChoice("""
								"finish_reason": "stop",
								"message": {"content": "{}"}"""))),
				() -> assertEquals(
						"{}",
						extractor.extract(responseWithChoice("""
								"finish_reason": "stop",
								"message": {"role": "assistant", "content": "{}"}"""))),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
						responseWithChoice("""
								"finish_reason": "stop",
								"message": {"role": "user", "content": "{}"}""")),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
						responseWithChoice("""
								"finish_reason": "stop",
								"message": {"role": 1, "content": "{}"}""")),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
						responseWithChoice("""
								"finish_reason": "stop",
								"message": []""")));
	}

	@Test
	void requiresNonBlankTextContent() {
		assertAll(
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE,
						responseWithChoice("""
								"finish_reason": "stop",
								"message": {}""")),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE,
						responseWithChoice("""
								"finish_reason": "stop",
								"message": {"content": null}""")),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE,
						responseWithChoice("""
								"finish_reason": "stop",
								"message": {"content": "   "}""")),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
						responseWithChoice("""
								"finish_reason": "stop",
								"message": {"content": {}}""")));
	}

	private String responseWithChoice(String choiceFields) {
		return """
				{
				  "output": {
				    "choices": [{
				%s
				    }]
				  }
				}
				""".formatted(choiceFields);
	}

	private EvaluationException assertError(
			EvaluationErrorCode expectedCode,
			String rawResponse) {
		EvaluationException exception = assertThrows(
				EvaluationException.class,
				() -> extractor.extract(rawResponse));
		assertEquals(expectedCode, exception.errorCode());
		return exception;
	}

	private String fixture(String fileName) {
		String resourcePath = "/evaluation/bailian/" + fileName;
		try (InputStream input =
				BailianAssistantContentExtractorTest.class.getResourceAsStream(resourcePath)) {
			if (input == null) {
				throw new IllegalStateException("Missing test fixture: " + resourcePath);
			}
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Unable to read test fixture: " + resourcePath, exception);
		}
	}
}
