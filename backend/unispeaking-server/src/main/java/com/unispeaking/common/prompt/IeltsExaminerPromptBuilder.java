package com.unispeaking.common.prompt;

import com.unispeaking.domain.vo.scene.IeltsContent;
import com.unispeaking.domain.vo.scene.IeltsContentQuestion;
import com.unispeaking.domain.vo.scene.IeltsPart;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public final class IeltsExaminerPromptBuilder {

	private static final String ROOT = "prompts/ielts/";
	private static final String BASE_TEMPLATE = "examiner-base.md";
	private static final Map<IeltsPart, String> PART_TEMPLATES = Map.of(
			IeltsPart.PART_1, "L1_Part_1.template.md",
			IeltsPart.PART_2, "L2_Part_2.template.md",
			IeltsPart.PART_3, "L3_Part_3.template.md");

	public String build(
			IeltsPart part,
			String topicTitle,
			IeltsContent content) {
		return build(part, topicTitle, content, "Daniel");
	}

	public String build(
			IeltsPart part,
			String topicTitle,
			IeltsContent content,
			String examinerName) {
		if (part == null || content == null) {
			throw new IllegalArgumentException("part and content must not be null");
		}
		List<IeltsContentQuestion> questions = content.questionsFor(part);
		if (questions.isEmpty()) {
			throw new IllegalArgumentException("active IELTS part must contain questions");
		}
		String partLayer = load(PART_TEMPLATES.get(part))
				.replace("{{topic_title}}", normalizedTitle(topicTitle))
				.replace("{{examiner_name}}", normalizedExaminerName(examinerName))
				.replace("{{part1_questions}}", part == IeltsPart.PART_1
						? formatQuestions(questions)
						: "")
				.replace("{{part2_cue_card}}", part == IeltsPart.PART_2
						? formatCueCard(questions.getFirst())
						: "")
				.replace("{{part3_questions}}", part == IeltsPart.PART_3
						? formatQuestions(questions)
						: "");
		return String.join("\n\n", load(BASE_TEMPLATE), partLayer);
	}

	private String formatQuestions(List<IeltsContentQuestion> questions) {
		StringBuilder result = new StringBuilder();
		for (int index = 0; index < questions.size(); index++) {
			IeltsContentQuestion question = questions.get(index);
			result.append(index + 1)
					.append(". ")
					.append(question.question().strip())
					.append('\n');
		}
		return result.toString().strip();
	}

	private String formatCueCard(IeltsContentQuestion question) {
		StringBuilder result = new StringBuilder("Topic:\n")
				.append(question.question().strip());
		if (!question.cuePoints().isEmpty()) {
			result.append("\n\nYou should say:\n");
			for (String cuePoint : question.cuePoints()) {
				result.append("- ").append(cuePoint.strip()).append('\n');
			}
		}
		return result.toString().strip();
	}

	private String normalizedTitle(String topicTitle) {
		return topicTitle == null || topicTitle.isBlank()
				? "this topic"
				: topicTitle.strip();
	}

	private String normalizedExaminerName(String examinerName) {
		return examinerName == null || examinerName.isBlank()
				? "Daniel"
				: examinerName.strip();
	}

	private String load(String fileName) {
		try {
			return new ClassPathResource(ROOT + fileName)
					.getContentAsString(StandardCharsets.UTF_8)
					.strip();
		}
		catch (IOException exception) {
			throw new IllegalStateException(
					"cannot load IELTS prompt template: " + fileName,
					exception);
		}
	}
}
