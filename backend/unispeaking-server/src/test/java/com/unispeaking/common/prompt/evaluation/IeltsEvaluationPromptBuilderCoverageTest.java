package com.unispeaking.common.prompt.evaluation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.domain.vo.scene.IeltsPart;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedConstruction;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 补齐 IELTS 评价 Prompt builder 的入口、边界和失败路径覆盖。
 *
 * <p>测试故意只使用 classpath 中的正式模板；资源本身的内容完整性由
 * {@link IeltsEvaluationPromptBuilder} 的加载逻辑和现有模板测试共同验证。</p>
 */
class IeltsEvaluationPromptBuilderCoverageTest {

	private static final String OPEN = "<EVALUATION_INPUT>";
	private static final String CLOSE = "</EVALUATION_INPUT>";
	private static final String INPUT_PLACEHOLDER =
			"{{IELTS_EVALUATION_INPUT_JSON}}";
	private static final String RUBRIC_PLACEHOLDER =
			"{{IELTS_OFFICIAL_TEXT_RUBRIC}}";
	private static final String RUBRIC_START = "### Band 9";

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final IeltsEvaluationPromptBuilder builder =
			new IeltsEvaluationPromptBuilder(objectMapper);

	@Test
	void constructorRejectsNullMapper() {
		assertThrows(
				NullPointerException.class,
				() -> new IeltsEvaluationPromptBuilder(null));
	}

	@ParameterizedTest(name = "invalid part request: {0}")
	@MethodSource("invalidPartRequests")
	void buildPartRejectsMissingPartOrUnscorableTranscript(
			IeltsPart part,
			String transcript) {
		EvaluationException exception = assertThrows(
				EvaluationException.class,
				() -> builder.buildPart(part, transcript, null, null));

		assertEquals(EvaluationErrorCode.INVALID_REQUEST, exception.errorCode());
	}

	private static Stream<Arguments> invalidPartRequests() {
		return Stream.of(
				Arguments.of(null, "a valid answer"),
				Arguments.of(IeltsPart.PART_1, null),
				Arguments.of(IeltsPart.PART_2, ""),
				Arguments.of(IeltsPart.PART_3, " \t\n "));
	}

	@ParameterizedTest(name = "loads part template: {0}")
	@MethodSource("allParts")
	void buildPartLoadsTheMatchingTemplateAndPreservesAllInputFields(
			IeltsPart part) throws Exception {
		String transcript = "[history]\nExaminer: follow-up question\n"
				+ "Candidate: I answered at the boundary: <&>.";
		String cueCard = part == IeltsPart.PART_2
				? "Describe a useful invention.\nYou should say why."
				: null;
		String speechMetrics = "turns=2; fluency=0.0; max_duration_ms=2147483647";

		String prompt = builder.buildPart(
				part, transcript, cueCard, speechMetrics);
		JsonNode input = inputJson(prompt);

		assertAll(
				() -> assertEquals(part.name(), input.get("part").asString()),
				() -> assertEquals(transcript,
						input.get("transcript").asString()),
				() -> assertEquals(cueCard,
						nullableText(input.get("cue_card"))),
				() -> assertEquals(speechMetrics,
						input.get("speech_fluency_metrics").asString()),
				() -> assertTrue(prompt.contains(RUBRIC_START)),
				() -> assertFalse(prompt.contains(INPUT_PLACEHOLDER)),
				() -> assertFalse(prompt.contains(RUBRIC_PLACEHOLDER)),
				() -> assertTrue(prompt.contains(partEvidence(part))));
	}

	private static Stream<Arguments> allParts() {
		return Stream.of(IeltsPart.values()).map(Arguments::of);
	}

	@Test
	void buildPartConvertsBlankOptionalEvidenceToJsonNull() throws Exception {
		String prompt = builder.buildPart(
				IeltsPart.PART_1,
				"Candidate: I enjoy quiet mornings.",
				" \n\t ",
				"");
		JsonNode input = inputJson(prompt);

		assertAll(
				() -> assertTrue(input.get("cue_card").isNull()),
				() -> assertTrue(input.get("speech_fluency_metrics").isNull()));
	}

	@Test
	void buildPartRetainsEmptyHistorySectionsAndSpecialBoundaryCharacters()
			throws Exception {
		String transcript = "[PART_1]\n[history]\n\n[PART_2]\n"
				+ "Candidate: ampersand &, angle brackets < and >, emoji 😀";
		String prompt = builder.buildPart(
				IeltsPart.PART_2,
				transcript,
				"Describe an object.",
				"scorable_turns=0; empty_history=true");
		JsonNode input = inputJson(prompt);

		assertAll(
				() -> assertEquals(transcript,
						input.get("transcript").asString()),
				() -> assertTrue(prompt.contains("\\u0026")),
				() -> assertTrue(prompt.contains("\\u003C")),
				() -> assertTrue(prompt.contains("\\u003E")),
				() -> assertTrue(prompt.contains("empty_history=true")));
	}

	@ParameterizedTest(name = "invalid final transcript: {0}")
	@MethodSource("invalidFinalTranscripts")
	void buildFinalRejectsMissingOrBlankCompleteTranscript(String transcript) {
		EvaluationException exception = assertThrows(
				EvaluationException.class,
				() -> builder.buildFinal(transcript, null, "0.0"));

		assertEquals(EvaluationErrorCode.INVALID_REQUEST, exception.errorCode());
	}

	private static Stream<Arguments> invalidFinalTranscripts() {
		return Stream.of(
				Arguments.of((String) null),
				Arguments.of(""),
				Arguments.of("\t\n"));
	}

	@Test
	void buildFinalLoadsFinalStageWithNullHistoryMetricsAndBand() throws Exception {
		String transcript = "PART 1: I live in Shanghai.\n"
				+ "PART 2: I would like to describe a memorable trip.\n"
				+ "PART 3: Travel can broaden people's perspectives.";
		String prompt = builder.buildFinal(transcript, " \t", null);
		JsonNode input = inputJson(prompt);

		assertAll(
				() -> assertEquals(transcript,
						input.get("complete_test_transcript").asString()),
				() -> assertTrue(input.get("speech_fluency_metrics").isNull()),
				() -> assertNull(nullableText(input.get("pronunciation_band_from_audio"))),
				() -> assertTrue(prompt.contains("assessment_type")),
				() -> assertTrue(prompt.contains("average performance across all three Parts")),
				() -> assertTrue(prompt.contains(RUBRIC_START)),
				() -> assertFalse(prompt.contains(INPUT_PLACEHOLDER)),
				() -> assertFalse(prompt.contains(RUBRIC_PLACEHOLDER)));
	}

	@Test
	void wrapsJacksonSerializationFailureAsTemplateInvalid() {
		ObjectMapper failingMapper = spy(new ObjectMapper());
		JacksonException cause = new JacksonException("serialization failed") { };
		when(failingMapper.writeValueAsString(org.mockito.ArgumentMatchers.any()))
				.thenThrow(cause);
		IeltsEvaluationPromptBuilder failingBuilder =
				new IeltsEvaluationPromptBuilder(failingMapper);

		EvaluationException exception = assertThrows(
				EvaluationException.class,
				() -> failingBuilder.buildFinal("a valid complete transcript", null, "6.5"));

		assertAll(
				() -> assertEquals(
						EvaluationErrorCode.PROMPT_TEMPLATE_INVALID,
						exception.errorCode()),
				() -> assertEquals(cause, exception.getCause()));
	}

	@Test
	void rejectsPartTemplateMissingEitherRequiredPlaceholder() throws Exception {
		try (MockedConstruction<ClassPathResource> ignored = mockResources(
				"reviewed template without input slot",
				"reviewed rubric")) {
			EvaluationException exception = assertThrows(
				EvaluationException.class,
				() -> builder.buildPart(
						IeltsPart.PART_1, "Candidate: an answer.", null, null));

			assertEquals(
					EvaluationErrorCode.PROMPT_TEMPLATE_INVALID,
					exception.errorCode());
		}
	}

	@Test
	void rejectsRubricContainingAPlaceholder() throws Exception {
		try (MockedConstruction<ClassPathResource> ignored = mockResources(
				INPUT_PLACEHOLDER + "\n" + RUBRIC_PLACEHOLDER,
				"rubric containing " + INPUT_PLACEHOLDER)) {
			EvaluationException exception = assertThrows(
				EvaluationException.class,
				() -> builder.buildFinal("a complete transcript", null, "6.0"));

			assertEquals(
					EvaluationErrorCode.PROMPT_TEMPLATE_INVALID,
					exception.errorCode());
		}
	}

	@Test
	void rejectsBlankRubric() throws Exception {
		try (MockedConstruction<ClassPathResource> ignored = mockResources(
				INPUT_PLACEHOLDER + "\n" + RUBRIC_PLACEHOLDER,
				" \n\t ")) {
			EvaluationException exception = assertThrows(
				EvaluationException.class,
				() -> builder.buildFinal("a complete transcript", null, "6.0"));

			assertEquals(
					EvaluationErrorCode.PROMPT_TEMPLATE_INVALID,
					exception.errorCode());
		}
	}

	@Test
	void translatesTemplateReadFailureToTemplateInvalid() throws Exception {
		try (MockedConstruction<ClassPathResource> ignored =
				mockResourcesWithReadFailure()) {
			EvaluationException exception = assertThrows(
				EvaluationException.class,
				() -> builder.buildFinal("a complete transcript", null, "6.0"));

			assertAll(
					() -> assertEquals(
							EvaluationErrorCode.PROMPT_TEMPLATE_INVALID,
							exception.errorCode()),
					() -> assertTrue(exception.getCause() instanceof IOException));
		}
	}

	private static MockedConstruction<ClassPathResource> mockResources(
			String template,
			String rubric) throws Exception {
		return org.mockito.Mockito.mockConstruction(
				ClassPathResource.class,
				(mock, context) -> {
					String path = (String) context.arguments().get(0);
					when(mock.getContentAsString(StandardCharsets.UTF_8))
							.thenReturn(path.endsWith("official-text-rubric-v1.txt")
									? rubric
									: template);
				});
	}

	private static MockedConstruction<ClassPathResource>
			mockResourcesWithReadFailure() throws Exception {
		return org.mockito.Mockito.mockConstruction(
				ClassPathResource.class,
				(mock, context) -> when(mock.getContentAsString(StandardCharsets.UTF_8))
						.thenThrow(new IOException("resource unavailable")));
	}

	private JsonNode inputJson(String prompt) throws Exception {
		int start = prompt.indexOf(OPEN) + OPEN.length();
		int end = prompt.indexOf(CLOSE, start);
		assertTrue(start >= OPEN.length());
		assertTrue(end > start);
		return objectMapper.readTree(prompt.substring(start, end).strip());
	}

	private static String nullableText(JsonNode node) {
		return node == null || node.isNull() ? null : node.asString();
	}

	private static String partEvidence(IeltsPart part) {
		if (part == IeltsPart.PART_1) {
			return "does not need a formal introduction";
		}
		if (part == IeltsPart.PART_2) {
			return "Cue-card bullets are prompts, not a mandatory checklist";
		}
		return "Score language performance, not whether the opinion";
	}
}
