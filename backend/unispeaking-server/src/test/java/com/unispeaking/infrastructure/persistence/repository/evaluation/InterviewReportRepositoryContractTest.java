package com.unispeaking.infrastructure.persistence.repository.evaluation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Additional contract tests kept separate from mapper-focused repository tests. */
class InterviewReportRepositoryContractTest {

	@BeforeAll
	static void initializeMybatisMetadata() {
		TableInfoHelper.initTableInfo(
				new MapperBuilderAssistant(
						new MybatisConfiguration(),
						"interview-report-repository-contract-test"),
				InterviewReportEntity.class);
	}

	@Test
	void findByIdHandlesBlankMissingAndNullablePersistedFields() {
		InterviewReportMapper mapper = mock(InterviewReportMapper.class);
		InterviewReportRepository repository = new MybatisInterviewReportRepository(mapper);
		assertTrue(repository.findById(null).isEmpty());
		assertTrue(repository.findById(" ").isEmpty());
		verify(mapper, never()).selectById(any());

		when(mapper.selectById("missing")).thenReturn(null);
		assertTrue(repository.findById("missing").isEmpty());
		InterviewReportEntity entity = entity();
		entity.setUserId(null);
		entity.setStatus(null);
		entity.setRetryCount(null);
		when(mapper.selectById("nullable")).thenReturn(entity);

		InterviewReportRecord record = repository.findById("nullable").orElseThrow();
		assertEquals(null, record.userId());
		assertEquals(null, record.status());
		assertEquals(0, record.retryCount());
	}

	@Test
	void readQueriesTranslateMapperFailuresAndMapSceneResults() {
		InterviewReportMapper mapper = mock(InterviewReportMapper.class);
		InterviewReportRepository repository = new MybatisInterviewReportRepository(mapper);
		when(mapper.selectList(any())).thenReturn(List.of(entity()));
		assertEquals(List.of("session-1"), repository.findBySceneId("scene-1")
				.stream().map(InterviewReportRecord::sessionId).toList());
		when(mapper.selectById("broken")).thenThrow(new IllegalStateException("read"));
		assertFailure(() -> repository.findById("broken"));
		when(mapper.selectList(any())).thenThrow(new IllegalStateException("read"));
		assertFailure(() -> repository.findBySceneId("scene-1"));
	}

	@Test
	void terminalWritesAreBestEffortButCasMethodsExposeTheirOutcome() {
		InterviewReportMapper mapper = mock(InterviewReportMapper.class);
		InterviewReportRepository repository = new MybatisInterviewReportRepository(mapper);
		when(mapper.update(any(), any())).thenReturn(0);

		assertDoesNotThrow(() -> repository.markCompleted(record()));
		assertDoesNotThrow(() -> repository.markFailed("session-1", "provider unavailable"));
		assertFalse(repository.retryFromFailed("session-1", 2));
		assertFalse(repository.casFailedToProcessing("session-1"));

		when(mapper.update(any(), any())).thenReturn(1);
		assertTrue(repository.retryFromFailed("session-1", 2));
		assertTrue(repository.casFailedToProcessing("session-1"));
	}

	@Test
	void writeFailuresAreTranslatedForEachStateTransition() {
		InterviewReportMapper mapper = mock(InterviewReportMapper.class);
		InterviewReportRepository repository = new MybatisInterviewReportRepository(mapper);
		when(mapper.update(any(), any())).thenThrow(new IllegalStateException("write"));

		assertFailure(() -> repository.markCompleted(record()));
		assertFailure(() -> repository.markFailed("session-1", "failed"));
		assertFailure(() -> repository.retryFromFailed("session-1", 0));
		assertFailure(() -> repository.casFailedToProcessing("session-1"));
	}

	private void assertFailure(org.junit.jupiter.api.function.Executable executable) {
		BusinessException failure = assertThrows(BusinessException.class, executable);
		assertEquals(InterviewErrorCode.INTERVIEW_REPORT_PERSISTENCE_FAILED, failure.code());
	}

	private InterviewReportEntity entity() {
		InterviewReportEntity entity = new InterviewReportEntity();
		entity.setSessionId("session-1");
		entity.setSceneId("scene-1");
		entity.setStatus(ReportStatus.PROCESSING.name());
		entity.setOverallScore(new BigDecimal("8.5"));
		entity.setSummary("summary");
		entity.setCreatedAt(OffsetDateTime.parse("2026-08-01T00:00:00Z"));
		entity.setUpdatedAt(OffsetDateTime.parse("2026-08-01T00:01:00Z"));
		return entity;
	}

	private InterviewReportRecord record() {
		OffsetDateTime now = OffsetDateTime.parse("2026-08-01T00:00:00Z");
		return new InterviewReportRecord("session-1", "scene-1", "11111111-1111-4111-8111-111111111111",
				ReportStatus.COMPLETED, new BigDecimal("8.5"), "summary",
				null, null, null, null, null, null, null, null, null, null, null, null,
				null, null, null, 2, null, now, now);
	}
}
