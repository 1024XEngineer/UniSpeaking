package com.unispeaking.infrastructure.persistence.evaluation.repository;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.infrastructure.persistence.evaluation.asset.AssetSentenceEvaluationTarget;
import com.unispeaking.infrastructure.persistence.evaluation.json.EvaluationJsonbCodec;
import com.unispeaking.infrastructure.persistence.evaluation.json.ReadingDetailsJson;
import com.unispeaking.infrastructure.persistence.evaluation.mapper.AssetSentenceEvaluationMapper;
import com.unispeaking.service.evaluation.internal.exception.EvaluationErrorCode;
import com.unispeaking.service.evaluation.internal.exception.EvaluationException;
import com.unispeaking.service.evaluation.internal.model.EndingTone;
import com.unispeaking.service.evaluation.internal.model.PronunciationAssessmentResult;
import com.unispeaking.service.evaluation.internal.model.PronunciationPhonemeResult;
import com.unispeaking.service.evaluation.internal.model.PronunciationWordResult;
import com.unispeaking.service.evaluation.internal.model.WordReadStatus;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 验证参考句 SQL 契约、JSONB 富结果映射和数据库失败边界。
 */
class AssetSentenceEvaluationRepositoryTest {

	private final StubAssetSentenceEvaluationMapper mapper =
			new StubAssetSentenceEvaluationMapper();
	private final EvaluationJsonbCodec codec =
			new EvaluationJsonbCodec(new ObjectMapper());
	private final AssetSentenceEvaluationRepository repository =
			new AssetSentenceEvaluationRepository(mapper, codec);

	@Test
	void mapperSqlJoinsOwnershipChainAndCastsReadingJsonb()
			throws Exception {
		Method selectMethod = AssetSentenceEvaluationMapper.class.getMethod(
				"selectEvaluationTarget",
				UUID.class);
		Method updateMethod = AssetSentenceEvaluationMapper.class.getMethod(
				"updateReadingDetails",
				UUID.class,
				String.class);
		String selectSql = normalizeSql(
				String.join(" ", selectMethod.getAnnotation(Select.class).value()));
		String updateSql = normalizeSql(
				String.join(" ", updateMethod.getAnnotation(Update.class).value()));

		assertAll(
				() -> assertTrue(selectSql.contains("from public.asset_sentences sentence")),
				() -> assertTrue(selectSql.contains(
						"inner join public.learning_assets asset")),
				() -> assertTrue(selectSql.contains(
						"asset.id = sentence.learning_asset_id")),
				() -> assertTrue(selectSql.contains(
						"inner join public.custom_scenes scene")),
				() -> assertTrue(selectSql.contains("scene.id = asset.scene_id")),
				() -> assertTrue(selectSql.contains(
						"scene.deleted_at is null")),
				() -> assertTrue(updateSql.contains(
						"reading_details = cast(#{readingdetailsjson} as jsonb)")),
				() -> assertTrue(updateSql.contains("where id = #{sentenceid}")));
	}

	@Test
	void returnsOptionalEvaluationTargetAndEmptyForMissingSentence() {
		UUID sentenceId = UUID.randomUUID();
		AssetSentenceEvaluationTarget target =
				new AssetSentenceEvaluationTarget(
						sentenceId,
						"Could I have some water?",
						UUID.randomUUID());
		mapper.selectedTarget = target;

		Optional<AssetSentenceEvaluationTarget> found =
				repository.findEvaluationTarget(sentenceId);
		mapper.selectedTarget = null;
		Optional<AssetSentenceEvaluationTarget> missing =
				repository.findEvaluationTarget(sentenceId);

		assertAll(
				() -> assertEquals(Optional.of(target), found),
				() -> assertTrue(missing.isEmpty()));
	}

	@Test
	void mapsCompletePronunciationResultAndOmitsToneScore()
			throws Exception {
		UUID sentenceId = UUID.randomUUID();
		mapper.updateCount = 1;

		repository.replaceReadingDetails(sentenceId, assessment());

		String json = mapper.updatedJson;
		ReadingDetailsJson details = codec.decodeReadingDetails(json);
		JsonNode root = new ObjectMapper().readTree(json);
		ReadingDetailsJson.Word word = details.words().get(0);
		ReadingDetailsJson.Phoneme phoneme = word.phonemes().get(0);

		assertAll(
				() -> assertEquals(sentenceId, mapper.updatedSentenceId),
				() -> assertEquals(score("91"), details.overallScore()),
				() -> assertEquals(score("87"), details.rhythmScore()),
				() -> assertEquals(EndingTone.FALL, details.endingTone()),
				() -> assertFalse(root.has("tone_score")),
				() -> assertEquals(WordReadStatus.NORMAL, word.readStatus()),
				() -> assertNull(word.isProminent()),
				() -> assertEquals("g", phoneme.expectedPhoneme()),
				() -> assertEquals("k", phoneme.actualPhoneme()),
				() -> assertEquals(score("82"), phoneme.pronunciationScore()));
	}

	@Test
	void mapsZeroAndUnexpectedUpdateCountsToStableErrors() {
		UUID sentenceId = UUID.randomUUID();
		mapper.updateCount = 0;

		EvaluationException missing = assertThrows(
				EvaluationException.class,
				() -> repository.replaceReadingDetails(
						sentenceId,
						assessment()));
		mapper.updateCount = 2;
		EvaluationException unexpected = assertThrows(
				EvaluationException.class,
				() -> repository.replaceReadingDetails(
						sentenceId,
						assessment()));

		assertAll(
				() -> assertEquals(
						EvaluationErrorCode.SENTENCE_NOT_FOUND,
						missing.errorCode()),
				() -> assertNull(missing.getCause()),
				() -> assertEquals(
						EvaluationErrorCode.PERSISTENCE_FAILED,
						unexpected.errorCode()),
				() -> assertNull(unexpected.getCause()));
	}

	@Test
	void hidesMapperAndMappingFailuresWithoutPreservingCause() {
		UUID querySentenceId = UUID.randomUUID();
		mapper.selectFailure = new IllegalStateException(
				"jdbc:postgresql://secret-host/private");

		EvaluationException queryFailure = assertThrows(
				EvaluationException.class,
				() -> repository.findEvaluationTarget(querySentenceId));
		mapper.updateFailure = new IllegalStateException(
				"SQL contains private reading details");
		EvaluationException updateFailure = assertThrows(
				EvaluationException.class,
				() -> repository.replaceReadingDetails(
						UUID.randomUUID(),
						assessment()));
		EvaluationException mappingFailure = assertThrows(
				EvaluationException.class,
				() -> repository.replaceReadingDetails(
						UUID.randomUUID(),
						null));

		assertAll(
				() -> assertPersistenceFailure(queryFailure),
				() -> assertPersistenceFailure(updateFailure),
				() -> assertPersistenceFailure(mappingFailure));
	}

	@Test
	void requiresCompleteEvaluationTargetFields() {
		UUID sentenceId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		assertAll(
				() -> assertThrows(
						NullPointerException.class,
						() -> new AssetSentenceEvaluationTarget(
								null,
								"Reference sentence.",
								userId)),
				() -> assertThrows(
						IllegalArgumentException.class,
						() -> new AssetSentenceEvaluationTarget(
								sentenceId,
								" ",
								userId)),
				() -> assertThrows(
						NullPointerException.class,
						() -> new AssetSentenceEvaluationTarget(
								sentenceId,
								"Reference sentence.",
								null)));
	}

	private void assertPersistenceFailure(EvaluationException exception) {
		assertAll(
				() -> assertEquals(
						EvaluationErrorCode.PERSISTENCE_FAILED,
						exception.errorCode()),
				() -> assertFalse(exception.getMessage().contains("secret-host")),
				() -> assertFalse(exception.getMessage().contains("private")),
				() -> assertNull(exception.getCause()));
	}

	private PronunciationAssessmentResult assessment() {
		PronunciationPhonemeResult phoneme =
				new PronunciationPhonemeResult(
						0,
						"g",
						"k",
						score("82"));
		PronunciationWordResult word =
				new PronunciationWordResult(
						0,
						"good",
						WordReadStatus.NORMAL,
						score("85"),
						score("84"),
						null,
						List.of(phoneme));
		return new PronunciationAssessmentResult(
				score("91"),
				score("87"),
				score("73"),
				score("89"),
				score("88"),
				score("90"),
				EndingTone.FALL,
				List.of(word));
	}

	private String normalizeSql(String sql) {
		return sql.replaceAll("\\s+", " ")
				.trim()
				.toLowerCase();
	}

	private static BigDecimal score(String value) {
		return new BigDecimal(value);
	}

	/**
	 * 不依赖 JVM 动态代理的可控 Mapper stub，专门记录仓储调用参数和返回行数。
	 */
	private static final class StubAssetSentenceEvaluationMapper
			implements AssetSentenceEvaluationMapper {

		private AssetSentenceEvaluationTarget selectedTarget;
		private RuntimeException selectFailure;
		private RuntimeException updateFailure;
		private int updateCount;
		private UUID updatedSentenceId;
		private String updatedJson;

		@Override
		public AssetSentenceEvaluationTarget selectEvaluationTarget(
				UUID sentenceId) {
			if (selectFailure != null) {
				throw selectFailure;
			}
			return selectedTarget;
		}

		@Override
		public int updateReadingDetails(
				UUID sentenceId,
				String readingDetailsJson) {
			if (updateFailure != null) {
				throw updateFailure;
			}
			this.updatedSentenceId = sentenceId;
			this.updatedJson = readingDetailsJson;
			return updateCount;
		}
	}
}
