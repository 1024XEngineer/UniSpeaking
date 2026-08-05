package com.unispeaking.common.prompt.evaluation;

import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.domain.vo.scene.IeltsPart;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public final class IeltsEvaluationPromptBuilder {

	private static final String ROOT = "prompts/evaluation/";
	private static final String PLACEHOLDER =
			"{{IELTS_EVALUATION_INPUT_JSON}}";
	private static final String RUBRIC_PLACEHOLDER =
			"{{IELTS_OFFICIAL_TEXT_RUBRIC}}";
	private static final String RUBRIC_FILE =
			"ielts-speaking-official-text-rubric-v1.txt";
	private static final Map<IeltsPart, String> PART_TEMPLATES = Map.of(
			IeltsPart.PART_1, "ielts-part1-evaluation-v1.txt",
			IeltsPart.PART_2, "ielts-part2-evaluation-v1.txt",
			IeltsPart.PART_3, "ielts-part3-evaluation-v1.txt");

	private final ObjectMapper objectMapper;

	public IeltsEvaluationPromptBuilder(ObjectMapper objectMapper) {
		this.objectMapper = Objects.requireNonNull(objectMapper);
	}

	public String buildPart(
			IeltsPart part,
			String transcript,
			String cueCard,
			String speechMetrics) {
		if (part == null || transcript == null || transcript.isBlank()) {
			throw new EvaluationException(EvaluationErrorCode.INVALID_REQUEST);
		}
		ObjectNode input = objectMapper.createObjectNode();
		input.put("part", part.name());
		input.put("transcript", transcript);
		putNullable(input, "cue_card", cueCard);
		putNullable(input, "speech_fluency_metrics", speechMetrics);
		return inject(load(PART_TEMPLATES.get(part)), input);
	}

	public String buildFinal(
			String completeTranscript,
			String speechMetrics,
			String pronunciationBand) {
		if (completeTranscript == null || completeTranscript.isBlank()) {
			throw new EvaluationException(EvaluationErrorCode.INVALID_REQUEST);
		}
		ObjectNode input = objectMapper.createObjectNode();
		input.put("complete_test_transcript", completeTranscript);
		putNullable(input, "speech_fluency_metrics", speechMetrics);
		input.put("pronunciation_band_from_audio", pronunciationBand);
		return inject(load("ielts-final-evaluation-v1.txt"), input);
	}

	private String inject(String template, ObjectNode input) {
		try {
			String json = objectMapper.writeValueAsString(input)
					.replace("&", "\\u0026")
					.replace("<", "\\u003C")
					.replace(">", "\\u003E");
			return template
					.replace(RUBRIC_PLACEHOLDER, loadRubric())
					.replace(PLACEHOLDER, json);
		}
		catch (JacksonException exception) {
			throw new EvaluationException(
					EvaluationErrorCode.PROMPT_TEMPLATE_INVALID,
					null,
					exception);
		}
	}

	private void putNullable(ObjectNode node, String name, String value) {
		if (value == null || value.isBlank()) node.putNull(name);
		else node.put(name, value);
	}

	private String load(String fileName) {
		try {
			String template = new ClassPathResource(ROOT + fileName)
					.getContentAsString(StandardCharsets.UTF_8)
					.strip();
			if (!template.contains(PLACEHOLDER)
					|| !template.contains(RUBRIC_PLACEHOLDER)) {
				throw new EvaluationException(
						EvaluationErrorCode.PROMPT_TEMPLATE_INVALID);
			}
			return template;
		}
		catch (IOException exception) {
			throw new EvaluationException(
					EvaluationErrorCode.PROMPT_TEMPLATE_INVALID,
					null,
					exception);
		}
	}

	private String loadRubric() {
		try {
			String rubric = new ClassPathResource(ROOT + RUBRIC_FILE)
					.getContentAsString(StandardCharsets.UTF_8)
					.strip();
			if (rubric.isBlank()
					|| rubric.contains(PLACEHOLDER)
					|| rubric.contains(RUBRIC_PLACEHOLDER)) {
				throw new EvaluationException(
						EvaluationErrorCode.PROMPT_TEMPLATE_INVALID);
			}
			return rubric;
		}
		catch (IOException exception) {
			throw new EvaluationException(
					EvaluationErrorCode.PROMPT_TEMPLATE_INVALID,
					null,
					exception);
		}
	}
}
