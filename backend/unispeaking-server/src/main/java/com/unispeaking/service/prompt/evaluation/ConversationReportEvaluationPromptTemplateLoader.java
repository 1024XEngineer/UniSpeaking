package com.unispeaking.service.prompt.evaluation;

import com.unispeaking.service.evaluation.internal.exception.EvaluationErrorCode;
import com.unispeaking.service.evaluation.internal.exception.EvaluationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * 单次加载并校验整场语言评价 Prompt 模板。
 */
@Component
public final class ConversationReportEvaluationPromptTemplateLoader {

	static final String INPUT_PLACEHOLDER = "{{EVALUATION_INPUT_JSON}}";

	private static final String TEMPLATE_PATH =
			"prompts/evaluation/conversation-report-evaluation-v1.txt";

	private final String template;

	/**
	 * 从固定 classpath 路径加载已 Review 的正式模板。
	 */
	public ConversationReportEvaluationPromptTemplateLoader() {
		this(new ClassPathResource(TEMPLATE_PATH));
	}

	/**
	 * 允许同包测试注入内存资源，不参与 Spring 正式组件的构造选择。
	 */
	ConversationReportEvaluationPromptTemplateLoader(
			Resource templateResource) {
		this.template = loadTemplate(
				Objects.requireNonNull(
						templateResource,
						"templateResource must not be null"));
	}

	/**
	 * 返回保留原始换行、且已完成占位符检查的模板。
	 */
	String template() {
		return template;
	}

	private static String loadTemplate(Resource templateResource) {
		String loadedTemplate;
		try (InputStream inputStream = templateResource.getInputStream()) {
			loadedTemplate = new String(
					inputStream.readAllBytes(),
					StandardCharsets.UTF_8);
		}
		catch (IOException exception) {
			throw new EvaluationException(
					EvaluationErrorCode.PROMPT_TEMPLATE_INVALID,
					null,
					exception);
		}

		if (loadedTemplate.isBlank()
				|| placeholderCount(loadedTemplate) != 1) {
			throw new EvaluationException(
					EvaluationErrorCode.PROMPT_TEMPLATE_INVALID);
		}
		return loadedTemplate;
	}

	private static int placeholderCount(String template) {
		int count = 0;
		int searchFrom = 0;
		int index;
		while ((index = template.indexOf(
				INPUT_PLACEHOLDER,
				searchFrom)) >= 0) {
			count++;
			searchFrom = index + INPUT_PLACEHOLDER.length();
		}
		return count;
	}
}
