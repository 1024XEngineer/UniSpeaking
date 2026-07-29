package com.unispeaking.service.evaluation.internal.provider.llm;

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
 * 验证 OpenAI-compatible 完整响应的错误边界和 content 提取规则。
 */
class OpenAiCompatibleAssistantContentExtractorTest {

	private final OpenAiCompatibleAssistantContentExtractor extractor =
			new OpenAiCompatibleAssistantContentExtractor(new ObjectMapper());

	@Test
	void extractsContentFromCompleteResponseFixture() {
		String content = extractor.extract(
				fixture("openai-compatible-success.json"));

		assertEquals(
				"""
				{"feedbackSummary":"表达清晰","suggestedExpression":"I would like a coffee."}""",
				content);
	}

	@Test
	void preservesEnvelopeMetadataAndContentCodeFence() {
		String response = """
				{
				  "id": "request-id",
				  "model": "model-name",
				  "choices": [{
				    "finish_reason": "stop",
				    "message": {
				      "role": "assistant",
				      "content": "  ```json\\n{\\\"feedbackSummary\\\":\\\"清晰\\\"}\\n```  "
				    }
				  }],
				  "usage": {"total_tokens": 20}
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
	void rejectsEmptyMalformedNonObjectAndBareContentResponses() {
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
						"null"),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE,
						"""
							{"feedbackSummary":"裸 content 不允许"}
							"""),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
						"""
							{"choices":[]} trailing-text
							"""),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
						"""
							{"choices":[],"choices":[]}
							"""));
	}

	@Test
	void mapsExplicitProviderErrorsWithoutExposingSensitiveContent() {
		EvaluationException errorNodeException = assertError(
				EvaluationErrorCode.PROVIDER_CALL_FAILED,
				"""
					{
					  "error": {
					    "code": "invalid_api_key",
					    "message": "secret-provider-detail"
					  }
					}
					""");
		EvaluationException textCodeException = assertError(
				EvaluationErrorCode.PROVIDER_CALL_FAILED,
				"""
					{
					  "code": "rate_limit_exceeded",
					  "message": "secret-rate-limit-detail"
					}
					""");
		EvaluationException numericCodeException = assertError(
				EvaluationErrorCode.PROVIDER_CALL_FAILED,
				"""
					{"code": 429, "message": "secret-numeric-code-detail"}
					""");

		assertAll(
				() -> assertFalse(
						errorNodeException.getMessage().contains("secret-provider")),
				() -> assertFalse(
						textCodeException.getMessage().contains("secret-rate-limit")),
				() -> assertFalse(
						numericCodeException.getMessage().contains("secret-numeric")));
	}

	@Test
	void ignoresNullErrorAndBlankCode() {
		assertAll(
				() -> assertEquals(
						"{}",
						extractor.extract(responseWithRootPrefix(
								"""
									"error": null,"""))),
				() -> assertEquals(
						"{}",
						extractor.extract(responseWithRootPrefix(
								"""
									"code": "   ","""))));
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
							{"choices": null}
							"""),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE,
						"""
							{"choices": []}
							"""),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
						"""
							{"choices": {}}
							"""),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
						"""
							{
							  "choices": [
							    {"finish_reason":"stop","message":{"content":"first"}},
							    {"finish_reason":"stop","message":{"content":"second"}}
							  ]
							}
							"""),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
						"""
							{"choices": ["not-an-object"]}
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
	void validatesOptionalAssistantRoleAndMessageShape() {
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
				() -> assertEquals(
						"{}",
						extractor.extract(responseWithChoice("""
								"finish_reason": "stop",
								"message": {"role": null, "content": "{}"}"""))),
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
						EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE,
						responseWithChoice("""
								"finish_reason": "stop"
								""")),
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

	private String responseWithRootPrefix(String rootPrefix) {
		return """
				{
				  %s
				  "choices": [{
				    "finish_reason": "stop",
				    "message": {"role": "assistant", "content": "{}"}
				  }]
				}
				""".formatted(rootPrefix);
	}

	private String responseWithChoice(String choiceFields) {
		return """
				{
				  "choices": [{
				%s
				  }]
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
		assertEquals(
				expectedCode.defaultMessage(),
				exception.getMessage());
		return exception;
	}

	private String fixture(String fileName) {
		String resourcePath = "/evaluation/llm/" + fileName;
		try (InputStream input =
				OpenAiCompatibleAssistantContentExtractorTest.class
						.getResourceAsStream(resourcePath)) {
			if (input == null) {
				throw new IllegalStateException(
						"Missing test fixture: " + resourcePath);
			}
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			throw new IllegalStateException(
					"Failed to read test fixture: " + resourcePath,
					exception);
		}
	}
}
