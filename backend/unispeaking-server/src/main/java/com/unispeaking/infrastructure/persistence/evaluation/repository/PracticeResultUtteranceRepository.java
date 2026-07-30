package com.unispeaking.infrastructure.persistence.evaluation.repository;

import com.unispeaking.infrastructure.persistence.evaluation.json.EvaluationJsonbCodec;
import com.unispeaking.infrastructure.persistence.evaluation.json.PronunciationDetailsJson;
import com.unispeaking.infrastructure.persistence.evaluation.mapper.PracticeResultUtteranceMapper;
import com.unispeaking.infrastructure.persistence.evaluation.utterance.PracticeResultUtterance;
import com.unispeaking.infrastructure.persistence.evaluation.utterance.PracticeResultUtterance.Phoneme;
import com.unispeaking.infrastructure.persistence.evaluation.utterance.PracticeResultUtterance.Word;
import com.unispeaking.infrastructure.persistence.evaluation.utterance.PracticeResultUtteranceRow;
import com.unispeaking.service.evaluation.internal.exception.EvaluationErrorCode;
import com.unispeaking.service.evaluation.internal.exception.EvaluationException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * 为评分处理器提供单轮气泡的覆盖保存和有序查询。
 *
 * <p>Provider 调用期间不应持有本仓储开启的事务；本类只执行单条 SQL
 * 或只读查询，并在边界处隐藏所有数据库和用户内容。</p>
 */
@Repository
@Profile("!test")
public final class PracticeResultUtteranceRepository {

	private final PracticeResultUtteranceMapper mapper;
	private final EvaluationJsonbCodec jsonbCodec;

	/**
	 * 创建气泡评分仓储。
	 */
	public PracticeResultUtteranceRepository(
			PracticeResultUtteranceMapper mapper,
			EvaluationJsonbCodec jsonbCodec) {
		this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
		this.jsonbCodec =
				Objects.requireNonNull(jsonbCodec, "jsonbCodec must not be null");
	}

	/**
	 * 插入或整体覆盖一个气泡的最新完整结果。
	 *
	 * <p>新 UUID 只用于首次插入；数据库冲突更新不会替换已存在记录的 ID。
	 * 返回行数不为 1 时按持久化失败处理。</p>
	 */
	public void upsert(PracticeResultUtterance utterance) {
		if (utterance == null) {
			throw invalidRequest();
		}

		int affectedRows;
		try {
			PracticeResultUtteranceRow row = toRow(utterance);
			affectedRows = mapper.upsert(row);
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
		if (affectedRows != 1) {
			throw persistenceFailure();
		}
	}

	/**
	 * 查询指定序号之前的历史气泡，并保持数据库中的升序。
	 */
	public List<PracticeResultUtterance> findBefore(
			UUID sessionId,
			int beforeUtteranceNo) {
		requireQuery(sessionId, beforeUtteranceNo);
		try {
			return mapRows(mapper.selectBefore(sessionId, beforeUtteranceNo));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	/**
	 * 查询一场练习的全部气泡，并保持数据库中的升序。
	 */
	public List<PracticeResultUtterance> findAll(UUID sessionId) {
		if (sessionId == null) {
			throw invalidRequest();
		}
		try {
			return mapRows(mapper.selectAll(sessionId));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	private PracticeResultUtteranceRow toRow(
			PracticeResultUtterance utterance) {
		PronunciationDetailsJson details = new PronunciationDetailsJson(
				utterance.words().stream()
						.map(this::toJsonWord)
						.toList());
		String json = jsonbCodec.encodePronunciationDetails(details);
		return new PracticeResultUtteranceRow(
				UUID.randomUUID(),
				utterance.sessionId(),
				utterance.utteranceNo(),
				utterance.transcript(),
				utterance.aiText(),
				utterance.overallScore(),
				utterance.rhythmScore(),
				utterance.toneScore(),
				utterance.integrityScore(),
				utterance.pronunciationScore(),
				utterance.fluencyScore(),
				utterance.feedbackSummary(),
				utterance.suggestedExpression(),
				json);
	}

	private List<PracticeResultUtterance> mapRows(
			List<PracticeResultUtteranceRow> rows) {
		List<PracticeResultUtteranceRow> requiredRows =
				Objects.requireNonNull(rows, "rows must not be null");
		return requiredRows.stream()
				.map(this::toUtterance)
				.toList();
	}

	private PracticeResultUtterance toUtterance(
			PracticeResultUtteranceRow row) {
		PracticeResultUtteranceRow requiredRow =
				Objects.requireNonNull(row, "row must not be null");
		PronunciationDetailsJson details =
				jsonbCodec.decodePronunciationDetails(
						requiredRow.pronunciationDetailsJson());
		List<Word> words = details.words().stream()
				.map(this::toWord)
				.toList();
		return new PracticeResultUtterance(
				requiredRow.sessionId(),
				requiredRow.utteranceNo(),
				requiredRow.transcript(),
				requiredRow.aiText(),
				requiredRow.overallScore(),
				requiredRow.rhythmScore(),
				requiredRow.toneScore(),
				requiredRow.integrityScore(),
				requiredRow.pronunciationScore(),
				requiredRow.fluencyScore(),
				requiredRow.feedbackSummary(),
				requiredRow.suggestedExpression(),
				words);
	}

	private PronunciationDetailsJson.Word toJsonWord(Word word) {
		return new PronunciationDetailsJson.Word(
				word.index(),
				word.text(),
				word.pronunciationScore(),
				word.phonemes().stream()
						.map(this::toJsonPhoneme)
						.toList());
	}

	private PronunciationDetailsJson.Phoneme toJsonPhoneme(
			Phoneme phoneme) {
		return new PronunciationDetailsJson.Phoneme(
				phoneme.index(),
				phoneme.expectedPhoneme(),
				phoneme.actualPhoneme(),
				phoneme.pronunciationScore(),
				phoneme.startPosition(),
				phoneme.endPosition());
	}

	private Word toWord(PronunciationDetailsJson.Word word) {
		return new Word(
				word.index(),
				word.text(),
				word.pronunciationScore(),
				word.phonemes().stream()
						.map(this::toPhoneme)
						.toList());
	}

	private Phoneme toPhoneme(PronunciationDetailsJson.Phoneme phoneme) {
		return new Phoneme(
				phoneme.index(),
				phoneme.expectedPhoneme(),
				phoneme.actualPhoneme(),
				phoneme.pronunciationScore(),
				phoneme.startPosition(),
				phoneme.endPosition());
	}

	private void requireQuery(UUID sessionId, int beforeUtteranceNo) {
		if (sessionId == null || beforeUtteranceNo < 1) {
			throw invalidRequest();
		}
	}

	private EvaluationException invalidRequest() {
		return new EvaluationException(EvaluationErrorCode.INVALID_REQUEST);
	}

	private EvaluationException persistenceFailure() {
		return new EvaluationException(EvaluationErrorCode.PERSISTENCE_FAILED);
	}
}
