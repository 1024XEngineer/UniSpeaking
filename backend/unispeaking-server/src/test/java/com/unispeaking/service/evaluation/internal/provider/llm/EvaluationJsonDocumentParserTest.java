package com.unispeaking.service.evaluation.internal.provider.llm;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.service.evaluation.internal.exception.EvaluationErrorCode;
import com.unispeaking.service.evaluation.internal.exception.EvaluationException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 验证评价 JSON 的完整文档、围栏、重复字段和根节点约束。
 */
class EvaluationJsonDocumentParserTest {

	private final EvaluationJsonDocumentParser parser =
			new EvaluationJsonDocumentParser(new ObjectMapper());

	@Test
	void parsesPlainObjectAndExactLfOrCrlfJsonFences() {
		JsonNode plain = parser.parseObject(" \n {\"value\":\"plain\"} \t");
		JsonNode lf = parser.parseObject(
				"```json\n{\"value\":\"lf\"}\n```");
		JsonNode crlf = parser.parseObject(
				"```json\r\n{\"value\":\"crlf\"}\r\n```");

		assertAll(
				() -> assertEquals("plain", plain.path("value").asString()),
				() -> assertEquals("lf", lf.path("value").asString()),
				() -> assertEquals("crlf", crlf.path("value").asString()));
	}

	@Test
	void rejectsNonCanonicalUnclosedAndNestedFences() {
		assertAll(
				() -> assertInvalid("```\n{}\n```"),
				() -> assertInvalid("```JSON\n{}\n```"),
				() -> assertInvalid("```javascript\n{}\n```"),
				() -> assertInvalid("```json {}\n```"),
				() -> assertInvalid("```json \n{}\n```"),
				() -> assertInvalid("```json\n{}"),
				() -> assertInvalid("```json\n```json\n{}\n```\n```"),
				() -> assertInvalid("```json\n{}\n```\n```json\n{}\n```"));
	}

	@Test
	void rejectsProseAndAdditionalJsonOutsideTheDocument() {
		assertAll(
				() -> assertInvalid("Here is JSON: {\"value\":1}"),
				() -> assertInvalid("{\"value\":1} trailing prose"),
				() -> assertInvalid("{\"value\":1} {\"value\":2}"),
				() -> assertInvalid("prefix ```json\n{}\n```"),
				() -> assertInvalid("```json\n{}\n``` suffix"));
	}

	@Test
	void rejectsDuplicateFieldsBeforeTreeValuesCanBeOverwritten() {
		assertAll(
				() -> assertInvalid(
						"{\"feedbackSummary\":\"first\",\"feedbackSummary\":\"second\"}"),
				() -> assertInvalid(
						"{\"outer\":{\"value\":1,\"value\":2}}"));
	}

	@Test
	void distinguishesMissingContentFromMalformedOrNonObjectJson() {
		assertAll(
				() -> assertIncomplete(null),
				() -> assertIncomplete(" \t\r\n"),
				() -> assertIncomplete("```json\n   \n```"),
				() -> assertInvalid("{not-json"),
				() -> assertInvalid("[]"),
				() -> assertInvalid("\"text\""),
				() -> assertInvalid("1"),
				() -> assertInvalid("true"),
				() -> assertInvalid("null"));
	}

	private void assertInvalid(String content) {
		assertError(EvaluationErrorCode.PROVIDER_RESPONSE_INVALID, content);
	}

	private void assertIncomplete(String content) {
		assertError(EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE, content);
	}

	private void assertError(
			EvaluationErrorCode expectedCode,
			String content) {
		EvaluationException exception = assertThrows(
				EvaluationException.class,
				() -> parser.parseObject(content));
		assertEquals(expectedCode, exception.errorCode());
	}
}
