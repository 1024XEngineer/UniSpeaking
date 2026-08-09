package com.unispeaking.infrastructure.persistence.repository.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.unispeaking.domain.po.evaluation.InterviewReportRecord;
import com.unispeaking.domain.vo.evaluation.ReportStatus;
import com.unispeaking.infrastructure.persistence.entity.evaluation.InterviewReportEntity;
import com.unispeaking.infrastructure.persistence.mapper.evaluation.InterviewReportMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
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

	private InterviewReportEntity entity() {
		InterviewReportEntity entity = new InterviewReportEntity();
		entity.setSessionId("session-1");
		entity.setSceneId("interview_1");
		entity.setUserId(UUID.fromString("11111111-1111-4111-8111-111111111111"));
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
}
