package com.unispeaking.service.prompt.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.service.evaluation.internal.exception.EvaluationErrorCode;
import com.unispeaking.service.evaluation.internal.exception.EvaluationException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

/**
 * 验证整场报告模板的 UTF-8 加载和唯一占位符检查。
 */
class ConversationReportEvaluationPromptTemplateLoaderTest {

	@Test
	void loadsReviewedTemplateWithoutChangingItsText() throws Exception {
		ClassPathResource resource = new ClassPathResource(
				"prompts/evaluation/conversation-report-evaluation-v1.txt");
		String expected;
		try (InputStream inputStream = resource.getInputStream()) {
			expected = new String(
					inputStream.readAllBytes(),
					StandardCharsets.UTF_8);
		}

		String actual =
				new ConversationReportEvaluationPromptTemplateLoader()
						.template();

		assertEquals(expected, actual);
		assertEquals(
				1,
				occurrenceCount(
						actual,
						ConversationReportEvaluationPromptTemplateLoader
								.INPUT_PLACEHOLDER));
		assertFalse(actual.contains("evaluationCriteria"));
		assertTrue(actual.contains("\"text_naturalness\""));
		assertTrue(actual.contains(
				"Every reason and every data_quality_notes item"));
		assertTrue(actual.contains("Chinese. Do not return English"));
	}

	@Test
	void rejectsMissingTemplateWithoutExposingResourcePath() {
		EvaluationException exception = assertThrows(
				EvaluationException.class,
				() -> new ConversationReportEvaluationPromptTemplateLoader(
						new ClassPathResource(
								"prompts/evaluation/missing-secret.txt")));

		assertEquals(
				EvaluationErrorCode.PROMPT_TEMPLATE_INVALID,
				exception.errorCode());
		assertFalse(exception.getMessage().contains("missing-secret"));
	}

	@Test
	void rejectsBlankTemplate() {
		assertTemplateInvalid(" \n\t ");
	}

	@Test
	void rejectsTemplateWithoutInputPlaceholder() {
		assertTemplateInvalid("report prompt without input slot");
	}

	@Test
	void rejectsTemplateWithRepeatedInputPlaceholder() {
		String placeholder =
				ConversationReportEvaluationPromptTemplateLoader
						.INPUT_PLACEHOLDER;

		assertTemplateInvalid(placeholder + "\n" + placeholder);
	}

	private static void assertTemplateInvalid(String template) {
		EvaluationException exception = assertThrows(
				EvaluationException.class,
				() -> new ConversationReportEvaluationPromptTemplateLoader(
						new ByteArrayResource(
								template.getBytes(StandardCharsets.UTF_8))));

		assertEquals(
				EvaluationErrorCode.PROMPT_TEMPLATE_INVALID,
				exception.errorCode());
		assertTrue(exception.getMessage().length() > 0);
		assertFalse(exception.getMessage().contains(template));
	}

	private static int occurrenceCount(String value, String target) {
		int count = 0;
		int searchFrom = 0;
		int index;
		while ((index = value.indexOf(target, searchFrom)) >= 0) {
			count++;
			searchFrom = index + target.length();
		}
		return count;
	}
}
