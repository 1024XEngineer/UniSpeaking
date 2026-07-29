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
 * 加载并校验单轮语言评价 Prompt 模板。
 *
 * <p>模板在组件构造时只读取一次，后续请求复用同一份已校验文本，不在运行期
 * 修改模板换行或其他已 Review 的语义。</p>
 */
@Component
public final class DialogueTurnEvaluationPromptTemplateLoader {

	static final String INPUT_PLACEHOLDER = "{{EVALUATION_INPUT_JSON}}";

	private static final String TEMPLATE_PATH =
			"prompts/evaluation/dialogue-turn-evaluation-v1.txt";

	private final String template;

	/**
	 * 从固定 classpath 路径加载正式模板。
	 */
	public DialogueTurnEvaluationPromptTemplateLoader() {
		this(new ClassPathResource(TEMPLATE_PATH));
	}

	/**
	 * 允许同包测试注入内存资源，不参与 Spring 正式组件的构造选择。
	 */
	DialogueTurnEvaluationPromptTemplateLoader(Resource templateResource) {
		this.template = loadTemplate(
				Objects.requireNonNull(
						templateResource,
						"templateResource must not be null"));
	}

	/**
	 * 返回已完成完整性校验的原始模板文本。
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
