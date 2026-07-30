package com.unispeaking.infrastructure.persistence.evaluation.repository;

import com.unispeaking.infrastructure.persistence.evaluation.asset.AssetSentenceEvaluationTarget;
import com.unispeaking.infrastructure.persistence.evaluation.json.EvaluationJsonbCodec;
import com.unispeaking.infrastructure.persistence.evaluation.json.ReadingDetailsJson;
import com.unispeaking.infrastructure.persistence.evaluation.mapper.AssetSentenceEvaluationMapper;
import com.unispeaking.service.evaluation.internal.exception.EvaluationErrorCode;
import com.unispeaking.service.evaluation.internal.exception.EvaluationException;
import com.unispeaking.service.evaluation.internal.model.PronunciationAssessmentResult;
import com.unispeaking.service.evaluation.internal.model.PronunciationPhonemeResult;
import com.unispeaking.service.evaluation.internal.model.PronunciationWordResult;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * 为跟读评分提供参考句查询和 reading_details 整体覆盖。
 *
 * <p>只保存数据库设计要求的句级分数，因此供应商的 toneScore 不进入
 * reading_details；句末语调通过 endingTone 单独保存。仅在非测试环境注册，
 * 与评分 Mapper 的 Profile 保持一致，兼容测试环境整体关闭数据库连接。</p>
 */
@Repository
@Profile("!test")
public final class AssetSentenceEvaluationRepository {

	private final AssetSentenceEvaluationMapper mapper;
	private final EvaluationJsonbCodec jsonbCodec;

	/**
	 * 创建仅依赖评分模块 Mapper 与 JSONB 编解码器的仓储。
	 */
	public AssetSentenceEvaluationRepository(
			AssetSentenceEvaluationMapper mapper,
			EvaluationJsonbCodec jsonbCodec) {
		this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
		this.jsonbCodec =
				Objects.requireNonNull(jsonbCodec, "jsonbCodec must not be null");
	}

	/**
	 * 查询可用于跟读评分的句子；不存在或所属场景已软删除时返回空。
	 */
	public Optional<AssetSentenceEvaluationTarget> findEvaluationTarget(
			UUID sentenceId) {
		try {
			return Optional.ofNullable(
					mapper.selectEvaluationTarget(sentenceId));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	/**
	 * 将完整且成功的发音评分转换为 JSONB，并整体覆盖旧跟读结果。
	 *
	 * <p>零行表示目标句子已不存在；超过一行违反句子主键预期，统一按持久化
	 * 异常处理。任何失败都不暴露 SQL、连接信息或评分明细。</p>
	 */
	public void replaceReadingDetails(
			UUID sentenceId,
			PronunciationAssessmentResult assessment) {
		int affectedRows;
		try {
			ReadingDetailsJson details = toReadingDetails(assessment);
			String json = jsonbCodec.encodeReadingDetails(details);
			affectedRows = mapper.updateReadingDetails(sentenceId, json);
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}

		if (affectedRows == 0) {
			throw new EvaluationException(
					EvaluationErrorCode.SENTENCE_NOT_FOUND);
		}
		if (affectedRows != 1) {
			throw persistenceFailure();
		}
	}

	private ReadingDetailsJson toReadingDetails(
			PronunciationAssessmentResult assessment) {
		PronunciationAssessmentResult requiredAssessment =
				Objects.requireNonNull(assessment, "assessment must not be null");
		List<ReadingDetailsJson.Word> words = requiredAssessment.words()
				.stream()
				.map(this::toReadingWord)
				.toList();
		return new ReadingDetailsJson(
				requiredAssessment.overallScore(),
				requiredAssessment.pronunciationScore(),
				requiredAssessment.fluencyScore(),
				requiredAssessment.integrityScore(),
				requiredAssessment.rhythmScore(),
				requiredAssessment.endingTone(),
				words);
	}

	private ReadingDetailsJson.Word toReadingWord(
			PronunciationWordResult word) {
		List<ReadingDetailsJson.Phoneme> phonemes = word.phonemes()
				.stream()
				.map(this::toReadingPhoneme)
				.toList();
		return new ReadingDetailsJson.Word(
				word.index(),
				word.word(),
				word.readStatus(),
				word.overallScore(),
				word.pronunciationScore(),
				word.isProminent(),
				phonemes);
	}

	private ReadingDetailsJson.Phoneme toReadingPhoneme(
			PronunciationPhonemeResult phoneme) {
		return new ReadingDetailsJson.Phoneme(
				phoneme.index(),
				phoneme.expectedPhoneme(),
				phoneme.actualPhoneme(),
				phoneme.pronunciationScore(),
				phoneme.startPosition(),
				phoneme.endPosition());
	}

	private EvaluationException persistenceFailure() {
		return new EvaluationException(EvaluationErrorCode.PERSISTENCE_FAILED);
	}
}
