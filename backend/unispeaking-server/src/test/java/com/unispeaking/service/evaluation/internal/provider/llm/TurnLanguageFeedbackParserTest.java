package com.unispeaking.service.evaluation.internal.provider.llm;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.service.evaluation.internal.exception.EvaluationErrorCode;
import com.unispeaking.service.evaluation.internal.exception.EvaluationException;
import com.unispeaking.service.evaluation.internal.model.TurnLanguageFeedback;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * 验证单轮语言反馈的严格字段白名单、文本类型和非空要求。
 */
class TurnLanguageFeedbackParserTest {

	private final TurnLanguageFeedbackParser parser = new TurnLanguageFeedbackParser(
			new EvaluationJsonDocumentParser(new ObjectMapper()));

	@Test
	void parsesEitherFieldOrderAndTrimsOnlyOuterTextWhitespace() {
		TurnLanguageFeedback feedback = parser.parse("""
				{
				  "suggestedExpression": "  I would like a coffee.  ",
				  "feedbackSummary": "  第一行\\n第二行  "
				}
				""");

		assertAll(
				() -> assertEquals("第一行\n第二行", feedback.feedbackSummary()),
				() -> assertEquals(
						"I would like a coffee.",
						feedback.suggestedExpression()));
	}

	@Test
	void acceptsOneCanonicalJsonFenceThroughSharedDocumentParser() {
		TurnLanguageFeedback feedback = parser.parse("""
				```json
				{
				  "feedbackSummary": "表达清晰",
				  "suggestedExpression": "Could I have some water?"
				}
				```
				""");

		assertAll(
				() -> assertEquals("表达清晰", feedback.feedbackSummary()),
				() -> assertEquals(
						"Could I have some water?",
						feedback.suggestedExpression()));
	}

	@Test
	void rejectsMissingNullAndBlankRequiredFieldsAsIncomplete() {
		assertAll(
				() -> assertIncomplete("""
						{"suggestedExpression":"Try this."}
						"""),
				() -> assertIncomplete("""
						{"feedbackSummary":"清晰"}
						"""),
				() -> assertIncomplete("""
						{"feedbackSummary":null,"suggestedExpression":"Try this."}
						"""),
				() -> assertIncomplete("""
						{"feedbackSummary":"清晰","suggestedExpression":null}
						"""),
				() -> assertIncomplete("""
						{"feedbackSummary":"  ","suggestedExpression":"Try this."}
						"""),
				() -> assertIncomplete("""
						{"feedbackSummary":"清晰","suggestedExpression":"\\n\\t"}
						"""));
	}

	@Test
	void rejectsNonTextFieldValuesAsInvalid() {
		assertAll(
				() -> assertInvalid("""
						{"feedbackSummary":1,"suggestedExpression":"Try this."}
						"""),
				() -> assertInvalid("""
						{"feedbackSummary":true,"suggestedExpression":"Try this."}
						"""),
				() -> assertInvalid("""
						{"feedbackSummary":{},"suggestedExpression":"Try this."}
						"""),
				() -> assertInvalid("""
						{"feedbackSummary":[],"suggestedExpression":"Try this."}
						"""),
				() -> assertInvalid("""
						{"feedbackSummary":"清晰","suggestedExpression":1}
						"""),
				() -> assertInvalid("""
						{"feedbackSummary":"清晰","suggestedExpression":[]}
						"""));
	}

	@Test
	void rejectsUnknownAliasesAndExtraFields() {
		assertAll(
				() -> assertInvalid("""
						{
						  "feedbackSummary":"清晰",
						  "suggestedExpression":"Try this.",
						  "score":90
						}
						"""),
				() -> assertInvalid("""
						{
						  "FeedbackSummary":"清晰",
						  "suggestedExpression":"Try this."
						}
						"""),
				() -> assertInvalid("""
						{
						  "feedback_summary":"清晰",
						  "suggestedExpression":"Try this."
						}
						"""));
	}

	@Test
	void rejectsEnglishOnlyFeedbackSummary() {
		assertInvalid("""
				{
				  "feedbackSummary":"The expression is clear.",
				  "suggestedExpression":"The expression is clear."
				}
				""");
	}

	@Test
	void rejectsDuplicateContractFields() {
		assertAll(
				() -> assertInvalid("""
						{
						  "feedbackSummary":"first",
						  "feedbackSummary":"second",
						  "suggestedExpression":"Try this."
						}
						"""),
				() -> assertInvalid("""
						{
						  "feedbackSummary":"清晰",
						  "suggestedExpression":"first",
						  "suggestedExpression":"second"
						}
						"""));
	}

	@Test
	void doesNotExposeAssistantContentInErrors() {
		String sensitiveContent = """
				{
				  "feedbackSummary":"private-feedback-value",
				  "suggestedExpression":"private-suggestion-value",
				  "unexpected":"private-extra-value"
				}
				""";

		EvaluationException exception = assertThrows(
				EvaluationException.class,
				() -> parser.parse(sensitiveContent));

		assertAll(
				() -> assertEquals(
						EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
						exception.errorCode()),
				() -> assertFalse(exception.getMessage().contains("private-feedback")),
				() -> assertFalse(exception.getMessage().contains("private-suggestion")),
				() -> assertFalse(exception.getMessage().contains("private-extra")),
				() -> assertNull(exception.getCause()));
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
				() -> parser.parse(content));
		assertEquals(expectedCode, exception.errorCode());
	}
}
