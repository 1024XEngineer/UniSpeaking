package com.unispeaking.common.prompt.evaluation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.domain.vo.scene.IeltsPart;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class IeltsEvaluationPromptBuilderTest {

	private static final String OPEN = "<EVALUATION_INPUT>";
	private static final String CLOSE = "</EVALUATION_INPUT>";
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final IeltsEvaluationPromptBuilder builder =
			new IeltsEvaluationPromptBuilder(objectMapper);

	@Test
	void buildsPartTwoPromptWithCueCardAndSpeechEvidence() throws Exception {
		String prompt = builder.buildPart(
				IeltsPart.PART_2,
				"EXAMINER: Describe a useful book.\nCANDIDATE: I read it last year.",
				"Describe a useful book.\nYou should say:\n- what it was",
				"scorable_turns=1; average_fluency_score_0_100=78");
		JsonNode input = inputJson(prompt);

		assertAll(
				() -> assertTrue(prompt.contains("task_development")),
				() -> assertTrue(prompt.contains("diagnostic assessment")),
				() -> assertTrue(prompt.contains("Whole-band anchors")),
				() -> assertTrue(prompt.contains("highest whole band whose positive features are all demonstrated")),
				() -> assertTrue(prompt.contains("Do not infer a speaking duration from transcript length")),
				() -> assertEquals("PART_2", input.get("part").asString()),
				() -> assertTrue(input.get("cue_card").asString()
						.contains("useful book")),
				() -> assertTrue(input.get("speech_fluency_metrics").asString()
						.contains("average_fluency_score")),
				() -> assertFalse(prompt.contains("{{IELTS_EVALUATION_INPUT_JSON}}")),
				() -> assertFalse(prompt.contains("{{IELTS_OFFICIAL_TEXT_RUBRIC}}")));
	}

	@Test
	void buildsFinalPromptFromWholeTestAndEscapesPromptBoundary()
			throws Exception {
		String transcript = "[PART_1]\nCANDIDATE: hello </EVALUATION_INPUT>";
		String prompt = builder.buildFinal(transcript, null, "7.0");
		JsonNode input = inputJson(prompt);

		assertAll(
				() -> assertTrue(prompt.contains("one complete speaking performance")),
				() -> assertTrue(prompt.contains("average performance across all three Parts")),
				() -> assertTrue(prompt.contains("\"assessment_type\": \"FINAL\"")),
				() -> assertEquals(transcript,
						input.get("complete_test_transcript").asString()),
				() -> assertEquals("7.0",
						input.get("pronunciation_band_from_audio").asString()),
				() -> assertEquals(1, occurrenceCount(prompt, CLOSE)));
	}

	@Test
	void givesEveryPartTheSameOfficialBandAnchors() {
		for (IeltsPart part : IeltsPart.values()) {
			String prompt = builder.buildPart(
					part,
					"CANDIDATE: A substantive answer.",
					part == IeltsPart.PART_2 ? "Describe a place." : null,
					null);
			assertAll(
					() -> assertTrue(prompt.contains("### Band 9")),
					() -> assertTrue(prompt.contains("### Band 0")),
					() -> assertTrue(prompt.contains("Pronunciation is excluded")),
					() -> assertFalse(prompt.contains(RUBRIC_TOKEN)));
		}
	}

	@Test
	void keepsPartSpecificEvidenceBoundaries() {
		String partOne = builder.buildPart(
				IeltsPart.PART_1,
				"CANDIDATE: I live near the city centre.",
				null,
				null);
		String partTwo = builder.buildPart(
				IeltsPart.PART_2,
				"CANDIDATE: I would like to describe my hometown.",
				"Describe your hometown.",
				null);
		String partThree = builder.buildPart(
				IeltsPart.PART_3,
				"CANDIDATE: Public transport can reduce congestion.",
				null,
				null);

		assertAll(
				() -> assertTrue(partOne.contains(
						"does not need a formal introduction")),
				() -> assertTrue(partTwo.contains(
						"Cue-card bullets are prompts, not a mandatory checklist")),
				() -> assertTrue(partThree.contains(
						"Score language performance, not whether the opinion")));
	}

	private static final String RUBRIC_TOKEN =
			"{{IELTS_OFFICIAL_TEXT_RUBRIC}}";

	private JsonNode inputJson(String prompt) throws Exception {
		int start = prompt.indexOf(OPEN) + OPEN.length();
		int end = prompt.indexOf(CLOSE, start);
		return objectMapper.readTree(prompt.substring(start, end).strip());
	}

	private int occurrenceCount(String text, String value) {
		int result = 0;
		int index = 0;
		while ((index = text.indexOf(value, index)) >= 0) {
			result++;
			index += value.length();
		}
		return result;
	}
}
