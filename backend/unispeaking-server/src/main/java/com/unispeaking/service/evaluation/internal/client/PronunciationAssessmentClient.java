package com.unispeaking.service.evaluation.internal.client;

import com.unispeaking.domain.vo.ai.AiCapability;
import com.unispeaking.exception.BusinessException;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.AiProviderRegistry.RoutedResult;
import com.unispeaking.service.evaluation.internal.exception.EvaluationErrorCode;
import com.unispeaking.service.evaluation.internal.exception.EvaluationException;
import com.unispeaking.service.evaluation.internal.model.PronunciationAssessmentResult;
import com.unispeaking.service.evaluation.internal.provider.iflytek.IflytekSuntoneAssessmentParser;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 连接 AI Provider 路由并在评分模块内解析完整发音评测响应。
 *
 * <p>本客户端不承担 WAV 结构校验；调用方必须先使用评分模块统一校验器。
 * Provider 只负责供应商通信，JSON、XML 和业务评分字段均在此边界内解析。</p>
 */
@Component
public final class PronunciationAssessmentClient {

	private static final String IFLYTEK_PROVIDER_ID = "iflytek";

	private final AiProviderRegistry aiProviderRegistry;
	private final EvaluationProviderFailureTranslator failureTranslator;
	private final IflytekSuntoneAssessmentParser assessmentParser;

	/**
	 * 创建使用项目 Jackson 配置和既有讯飞解析链的发音评分客户端。
	 */
	public PronunciationAssessmentClient(
			AiProviderRegistry aiProviderRegistry,
			ObjectMapper objectMapper,
			EvaluationProviderFailureTranslator failureTranslator) {
		this.aiProviderRegistry = Objects.requireNonNull(
				aiProviderRegistry,
				"aiProviderRegistry must not be null");
		this.failureTranslator = Objects.requireNonNull(
				failureTranslator,
				"failureTranslator must not be null");
		ObjectMapper requiredMapper = Objects.requireNonNull(
				objectMapper,
				"objectMapper must not be null");
		this.assessmentParser =
				new IflytekSuntoneAssessmentParser(requiredMapper);
	}

	/**
	 * 调用当前配置的发音评分路由并返回完整内部评分。
	 *
	 * @param referenceText 供应商评测使用的英文参考文本
	 * @param wavAudio 已通过统一结构校验的完整 PCM WAV
	 * @return 评分模块归一化后的完整发音评测结果
	 */
	public PronunciationAssessmentResult evaluate(
			String referenceText,
			byte[] wavAudio) {
		String requiredReferenceText = requireReferenceText(referenceText);
		Byte[] boxedAudio = boxAudio(wavAudio);

		RoutedResult<String> routedResult;
		try {
			routedResult = aiProviderRegistry.evaluatePronunciationRouted(
					requiredReferenceText,
					boxedAudio,
					null);
		}
		catch (BusinessException exception) {
			throw failureTranslator.translate(exception);
		}

		String rawResponse = requireSupportedResponse(routedResult);
		return assessmentParser.parse(rawResponse);
	}

	private String requireReferenceText(String referenceText) {
		if (referenceText == null || referenceText.isBlank()) {
			throw new EvaluationException(EvaluationErrorCode.INVALID_REQUEST);
		}
		return referenceText.trim();
	}

	private Byte[] boxAudio(byte[] wavAudio) {
		if (wavAudio == null || wavAudio.length == 0) {
			throw new EvaluationException(EvaluationErrorCode.AUDIO_REQUIRED);
		}
		Byte[] boxedAudio = new Byte[wavAudio.length];
		for (int index = 0; index < wavAudio.length; index++) {
			boxedAudio[index] = wavAudio[index];
		}
		return boxedAudio;
	}

	private String requireSupportedResponse(
			RoutedResult<String> routedResult) {
		if (routedResult == null
				|| routedResult.providerId() == null
				|| routedResult.providerId().isBlank()
				|| routedResult.response() == null
				|| routedResult.response().isBlank()) {
			throw new EvaluationException(
					EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE);
		}
		if (routedResult.capability() != AiCapability.SCORING) {
			throw new EvaluationException(
					EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);
		}
		if (!IFLYTEK_PROVIDER_ID.equals(routedResult.providerId())) {
			throw new EvaluationException(
					EvaluationErrorCode.PROVIDER_NOT_CONFIGURED);
		}
		return routedResult.response();
	}
}
