package com.unispeaking.infrastructure.persistence.repository.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.InterviewErrorCode;
import com.unispeaking.domain.po.evaluation.InterviewReportRecord;
import com.unispeaking.domain.vo.evaluation.ReportStatus;
import com.unispeaking.infrastructure.persistence.entity.evaluation.InterviewReportEntity;
import com.unispeaking.infrastructure.persistence.mapper.evaluation.InterviewReportMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.lang.reflect.Method;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

class MybatisInterviewReportRepositoryTest {

	@BeforeAll
	static void initializeMybatisMetadata() {
		TableInfoHelper.initTableInfo(
				new MapperBuilderAssistant(
						new MybatisConfiguration(),
						"mybatis-interview-report-repository-test"),
				InterviewReportEntity.class);
	}

	@Test
	void createIfAbsentInsertsProcessingRowAndReturnsTrue() {
		InterviewReportMapper mapper = mock(InterviewReportMapper.class);
		InterviewReportRepository repository = new MybatisInterviewReportRepository(mapper);

		boolean created = repository.createIfAbsent(
				"session-1",
				"interview_1",
				"11111111-1111-4111-8111-111111111111");

		assertTrue(created);
		ArgumentCaptor<InterviewReportEntity> entity =
				ArgumentCaptor.forClass(InterviewReportEntity.class);
		verify(mapper).insert(entity.capture());
		assertEquals("session-1", entity.getValue().getSessionId());
		assertEquals("interview_1", entity.getValue().getSceneId());
		assertEquals(ReportStatus.PROCESSING.name(), entity.getValue().getStatus());
		assertEquals(0, entity.getValue().getRetryCount());
		assertEquals(
				UUID.fromString("11111111-1111-4111-8111-111111111111"),
				entity.getValue().getUserId());
	}

	@Test
	void createIfAbsentTreatsDuplicateKeyAsNotCreated() {
		InterviewReportMapper mapper = mock(InterviewReportMapper.class);
		when(mapper.insert(any(InterviewReportEntity.class)))
				.thenThrow(new DuplicateKeyException("duplicate pk"));
		InterviewReportRepository repository = new MybatisInterviewReportRepository(mapper);

		assertFalse(repository.createIfAbsent(
				"session-1",
				"interview_1",
				"11111111-1111-4111-8111-111111111111"));
	}

	@Test
	void translatesCreateFailuresAndInvalidUserIds() {
		InterviewReportMapper mapper = mock(InterviewReportMapper.class);
		InterviewReportRepository repository = new MybatisInterviewReportRepository(mapper);
		when(mapper.insert(any(InterviewReportEntity.class)))
				.thenThrow(new IllegalStateException("db down"));

		BusinessException failure = assertThrows(BusinessException.class,
				() -> repository.createIfAbsent("session-1", "interview-1", USER_ID));
		assertEquals(InterviewErrorCode.INTERVIEW_REPORT_PERSISTENCE_FAILED, failure.code());
		assertThrows(IllegalArgumentException.class,
				() -> repository.createIfAbsent("session-1", "interview-1", "not-a-uuid"));
	}

	@Test
	void findByIdConvertsEntityToRecord() {
		InterviewReportMapper mapper = mock(InterviewReportMapper.class);
		InterviewReportRepository repository = new MybatisInterviewReportRepository(mapper);
		when(mapper.selectById("session-1")).thenReturn(entity());

		InterviewReportRecord record = repository.findById("session-1").orElseThrow();

		assertEquals("session-1", record.sessionId());
		assertEquals(ReportStatus.COMPLETED, record.status());
		assertEquals(new BigDecimal("85.0"), record.overallScore());
		assertEquals(new BigDecimal("82.0"), record.fluencyScore());
		assertEquals("流利", record.fluencyEvaluation());
		assertTrue(repository.findById("missing").isEmpty());
	}

	@Test
	void mapsAllPersistedReportFieldsAndNullableDefaults() {
		InterviewReportMapper mapper = mock(InterviewReportMapper.class);
		InterviewReportRepository repository = new MybatisInterviewReportRepository(mapper);
		InterviewReportEntity source = entity();
		source.setUserId(null);
		source.setStatus(null);
		source.setRetryCount(null);
		source.setPronunciationIntelligibilityScore(new BigDecimal("80.0"));
		source.setPronunciationIntelligibilityEvaluation("清晰");
		source.setPronunciationIntelligibilityAdvice("保持语速");
		source.setLogicCoherenceScore(new BigDecimal("81.0"));
		source.setLogicCoherenceEvaluation("连贯");
		source.setLogicCoherenceAdvice("加强衔接");
		source.setGrammarControlScore(new BigDecimal("79.0"));
		source.setGrammarControlEvaluation("准确");
		source.setGrammarControlAdvice("检查时态");
		source.setVocabularyExpressionScore(new BigDecimal("83.0"));
		source.setVocabularyExpressionEvaluation("丰富");
		source.setVocabularyExpressionAdvice("扩展表达");
		when(mapper.selectById("session-1")).thenReturn(source);

		InterviewReportRecord mapped = repository.findById("session-1").orElseThrow();

		assertEquals(null, mapped.userId());
		assertEquals(null, mapped.status());
		assertEquals(0, mapped.retryCount());
		assertEquals(new BigDecimal("80.0"), mapped.pronunciationIntelligibilityScore());
		assertEquals("清晰", mapped.pronunciationIntelligibilityEvaluation());
		assertEquals("保持语速", mapped.pronunciationIntelligibilityAdvice());
		assertEquals(new BigDecimal("81.0"), mapped.logicCoherenceScore());
		assertEquals("连贯", mapped.logicCoherenceEvaluation());
		assertEquals("加强衔接", mapped.logicCoherenceAdvice());
		assertEquals(new BigDecimal("79.0"), mapped.grammarControlScore());
		assertEquals("准确", mapped.grammarControlEvaluation());
		assertEquals("检查时态", mapped.grammarControlAdvice());
		assertEquals(new BigDecimal("83.0"), mapped.vocabularyExpressionScore());
		assertEquals("丰富", mapped.vocabularyExpressionEvaluation());
		assertEquals("扩展表达", mapped.vocabularyExpressionAdvice());
	}

	@Test
	void retryFromFailedUsesUpdateResultAsCasSignal() {
		InterviewReportMapper mapper = mock(InterviewReportMapper.class);
		InterviewReportRepository repository = new MybatisInterviewReportRepository(mapper);
		when(mapper.update(any(), any())).thenReturn(1);

		assertTrue(repository.retryFromFailed("session-1", 0));

		when(mapper.update(any(), any())).thenReturn(0);
		assertFalse(repository.retryFromFailed("session-1", 1));
	}

	@Test
	void findStuckProcessingQueriesProcessingRowsBeforeCutoff() {
		InterviewReportMapper mapper = mock(InterviewReportMapper.class);
		InterviewReportRepository repository = new MybatisInterviewReportRepository(mapper);
		when(mapper.selectList(any())).thenReturn(List.of(entity()));

		List<InterviewReportRecord> stuck = repository.findStuckProcessingBefore(
				OffsetDateTime.now());

		assertEquals(1, stuck.size());
		assertEquals("session-1", stuck.getFirst().sessionId());
		verify(mapper).selectList(any());
	}

	@Test
	void findBySceneIdQueriesBySceneOrderedByCreatedAtDesc() {
		InterviewReportMapper mapper = mock(InterviewReportMapper.class);
		InterviewReportRepository repository = new MybatisInterviewReportRepository(mapper);
		when(mapper.selectList(any())).thenReturn(List.of(entity(), entity()));

		List<InterviewReportRecord> records = repository.findBySceneId("interview_1");

		assertEquals(2, records.size());
		assertEquals("session-1", records.getFirst().sessionId());
		assertEquals("interview_1", records.getFirst().sceneId());
		verify(mapper).selectList(any());
	}

	@Test
	void findBySceneIdReturnsEmptyForBlankSceneId() {
		InterviewReportMapper mapper = mock(InterviewReportMapper.class);
		InterviewReportRepository repository = new MybatisInterviewReportRepository(mapper);

		assertTrue(repository.findBySceneId(null).isEmpty());
		assertTrue(repository.findBySceneId("  ").isEmpty());
		verify(mapper, never()).selectList(any());
	}

	@Test
	void rejectsIncompleteIdentifiersBeforeAccessingTheMapper() {
		InterviewReportMapper mapper = mock(InterviewReportMapper.class);
		InterviewReportRepository repository = new MybatisInterviewReportRepository(mapper);

		BusinessException failure = assertThrows(BusinessException.class,
				() -> repository.createIfAbsent(" ", "scene-1", USER_ID));
		assertEquals(InterviewErrorCode.INTERVIEW_REQUEST_INVALID, failure.code());
		assertThrows(BusinessException.class,
				() -> repository.createIfAbsent("session-1", null, USER_ID));
		assertThrows(BusinessException.class,
				() -> repository.createIfAbsent("session-1", "scene-1", ""));
		verify(mapper, never()).insert(any(InterviewReportEntity.class));
	}

	@Test
	void writesCompletedFailedAndManualRetryTransitions() {
		InterviewReportMapper mapper = mock(InterviewReportMapper.class);
		when(mapper.update(any(), any())).thenReturn(1);
		InterviewReportRepository repository = new MybatisInterviewReportRepository(mapper);

		repository.markCompleted(record());
		repository.markFailed("session-1", "PROVIDER_RETRYABLE");
		assertTrue(repository.casFailedToProcessing("session-1"));
		verify(mapper, org.mockito.Mockito.times(3)).update(any(), any());
	}

	@Test
	void toleratesCompletedUpdateThatLostItsProcessingRace() {
		InterviewReportMapper mapper = mock(InterviewReportMapper.class);
		when(mapper.update(any(), any())).thenReturn(0);
		InterviewReportRepository repository = new MybatisInterviewReportRepository(mapper);

		repository.markCompleted(record());
		verify(mapper).update(any(), any());
	}

	@Test
	void translatesMapperReadAndWriteFailures() {
		InterviewReportMapper mapper = mock(InterviewReportMapper.class);
		InterviewReportRepository repository = new MybatisInterviewReportRepository(mapper);
		when(mapper.update(any(), any())).thenThrow(new IllegalStateException("db down"));

		BusinessException writeFailure = assertThrows(BusinessException.class,
				() -> repository.markFailed("session-1", "FAILED"));
		assertEquals(InterviewErrorCode.INTERVIEW_REPORT_PERSISTENCE_FAILED,
				writeFailure.code());

		when(mapper.selectList(any())).thenThrow(new IllegalStateException("read failed"));
		BusinessException readFailure = assertThrows(BusinessException.class,
				() -> repository.findStuckProcessingBefore(OffsetDateTime.now()));
		assertEquals(InterviewErrorCode.INTERVIEW_REPORT_PERSISTENCE_FAILED,
				readFailure.code());
	}

	@Test
	void translatesCreateAndFindBySceneWriteFailures() {
		InterviewReportMapper mapper = mock(InterviewReportMapper.class);
		InterviewReportRepository repository = new MybatisInterviewReportRepository(mapper);
		when(mapper.selectList(any())).thenThrow(new IllegalStateException("read failed"));

		BusinessException failure = assertThrows(BusinessException.class,
				() -> repository.findBySceneId("interview-1"));
		assertEquals(InterviewErrorCode.INTERVIEW_REPORT_PERSISTENCE_FAILED, failure.code());

		when(mapper.insert(any(InterviewReportEntity.class)))
				.thenThrow(new IllegalStateException("insert failed"));
		failure = assertThrows(BusinessException.class,
				() -> repository.createIfAbsent("session-1", "interview-1", USER_ID));
		assertEquals(InterviewErrorCode.INTERVIEW_REPORT_PERSISTENCE_FAILED, failure.code());
	}

	@Test
	void convertsACompleteRecordToPersistenceEntity() throws Exception {
		MybatisInterviewReportRepository repository =
				new MybatisInterviewReportRepository(mock(InterviewReportMapper.class));
		Method method = MybatisInterviewReportRepository.class
				.getDeclaredMethod("toEntity", InterviewReportRecord.class);
		method.setAccessible(true);
		InterviewReportRecord source = record();
		InterviewReportEntity entity = (InterviewReportEntity) method.invoke(repository, source);
		assertEquals("session-1", entity.getSessionId());
		assertEquals("interview_1", entity.getSceneId());
		assertEquals(UUID.fromString(USER_ID), entity.getUserId());
		assertEquals(ReportStatus.COMPLETED.name(), entity.getStatus());
		assertEquals(new BigDecimal("85.0"), entity.getOverallScore());
		assertEquals(new BigDecimal("82.0"), entity.getFluencyScore());
		assertEquals("流利", entity.getFluencyEvaluation());
		assertEquals("保持节奏", entity.getFluencyAdvice());
		assertEquals(new BigDecimal("80.0"), entity.getPronunciationIntelligibilityScore());
		assertEquals("清晰", entity.getPronunciationIntelligibilityEvaluation());
		assertEquals("继续练习", entity.getPronunciationIntelligibilityAdvice());
		assertEquals(new BigDecimal("81.0"), entity.getLogicCoherenceScore());
		assertEquals("连贯", entity.getLogicCoherenceEvaluation());
		assertEquals("组织结构", entity.getLogicCoherenceAdvice());
		assertEquals(new BigDecimal("79.0"), entity.getGrammarControlScore());
		assertEquals("准确", entity.getGrammarControlEvaluation());
		assertEquals("检查时态", entity.getGrammarControlAdvice());
		assertEquals(new BigDecimal("83.0"), entity.getVocabularyExpressionScore());
		assertEquals("丰富", entity.getVocabularyExpressionEvaluation());
		assertEquals("扩展表达", entity.getVocabularyExpressionAdvice());
		assertEquals(0, entity.getRetryCount());
		assertEquals(null, entity.getFailureReason());
		assertEquals(source.createdAt(), entity.getCreatedAt());
		assertEquals(source.updatedAt(), entity.getUpdatedAt());
	}

	private InterviewReportEntity entity() {
		InterviewReportEntity entity = new InterviewReportEntity();
		entity.setSessionId("session-1");
		entity.setSceneId("interview_1");
		entity.setUserId(UUID.fromString(USER_ID));
		entity.setStatus(ReportStatus.COMPLETED.name());
		entity.setOverallScore(new BigDecimal("85.0"));
		entity.setSummary("整体表现良好。");
		entity.setFluencyScore(new BigDecimal("82.0"));
		entity.setFluencyEvaluation("流利");
		entity.setFluencyAdvice("保持节奏");
		entity.setRetryCount(0);
		entity.setCreatedAt(OffsetDateTime.now());
		entity.setUpdatedAt(OffsetDateTime.now());
		return entity;
	}

	private InterviewReportRecord record() {
		OffsetDateTime now = OffsetDateTime.now();
		return new InterviewReportRecord(
				"session-1", "interview_1", USER_ID, ReportStatus.COMPLETED,
				new BigDecimal("85.0"), "整体表现良好。",
				new BigDecimal("82.0"), "流利", "保持节奏",
				new BigDecimal("80.0"), "清晰", "继续练习",
				new BigDecimal("81.0"), "连贯", "组织结构",
				new BigDecimal("79.0"), "准确", "检查时态",
				new BigDecimal("83.0"), "丰富", "扩展表达",
				0, null, now, now);
	}

	private static final String USER_ID = "11111111-1111-4111-8111-111111111111";
}
