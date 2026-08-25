package com.unispeaking.infrastructure.persistence.repository.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.unispeaking.domain.dto.evaluation.IeltsEvaluationResult;
import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.infrastructure.persistence.entity.evaluation.IeltsEvaluationEntity;
import com.unispeaking.infrastructure.persistence.entity.evaluation.IeltsPartEvaluationEntity;
import com.unispeaking.infrastructure.persistence.mapper.evaluation.IeltsEvaluationMapper;
import com.unispeaking.infrastructure.persistence.mapper.evaluation.IeltsPartEvaluationMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IeltsEvaluationRepositoryTest {

	@BeforeAll
	static void initializeMybatisMetadata() {
		TableInfoHelper.initTableInfo(
				new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
				IeltsEvaluationEntity.class);
		TableInfoHelper.initTableInfo(
				new MapperBuilderAssistant(new MybatisConfiguration(), "part-test"),
				IeltsPartEvaluationEntity.class);
	}

	@Test
	void insertsEvaluationIntoExistingIeltsTableShape() {
		IeltsEvaluationMapper mapper = mock(IeltsEvaluationMapper.class);
		IeltsPartEvaluationMapper partMapper = mock(
				IeltsPartEvaluationMapper.class);
		when(partMapper.selectById("ielts_part_ielts-session-1"))
				.thenReturn(null);
		when(partMapper.insert(any(IeltsPartEvaluationEntity.class)))
				.thenReturn(1);
		IeltsEvaluationRepository repository =
				new IeltsEvaluationRepository(mapper, partMapper);

		repository.save("ielts-mock-1", "ielts-session-1", result());

		ArgumentCaptor<IeltsPartEvaluationEntity> captor =
				ArgumentCaptor.forClass(IeltsPartEvaluationEntity.class);
		verify(partMapper).insert(captor.capture());
		IeltsPartEvaluationEntity saved = captor.getValue();
		assertEquals("ielts-session-1", saved.getSessionId());
		assertEquals("ielts-mock-1", saved.getIeltsId());
		assertEquals("PART_1", saved.getPart());
		assertEquals("COMPLETED", saved.getEvaluationStatus());
		assertEquals(new BigDecimal("7.0"), saved.getFluencyCoherenceScore());
		assertEquals("回答直接且衔接清楚。", saved.getFluencyCoherenceReason());
		assertEquals(List.of("表达连贯"), List.of(saved.getStrengths()));
		assertEquals(saved.getCreatedAt(), saved.getUpdatedAt());
	}

	@Test
	void updatesExistingRowWithoutChangingCreatedAt() {
		IeltsEvaluationMapper mapper = mock(IeltsEvaluationMapper.class);
		IeltsPartEvaluationMapper partMapper = mock(
				IeltsPartEvaluationMapper.class);
		IeltsPartEvaluationEntity existing = new IeltsPartEvaluationEntity();
		existing.setSessionId("ielts-session-1");
		existing.setPartEvaluationId("ielts_part_ielts-session-1");
		existing.setCreatedAt(OffsetDateTime.of(
				2026, 8, 4, 8, 0, 0, 0, ZoneOffset.UTC));
		when(partMapper.selectById("ielts_part_ielts-session-1"))
				.thenReturn(existing);
		when(partMapper.updateById(any(IeltsPartEvaluationEntity.class)))
				.thenReturn(1);

		new IeltsEvaluationRepository(mapper, partMapper)
				.save("ielts-mock-1", "ielts-session-1", result());

		ArgumentCaptor<IeltsPartEvaluationEntity> captor =
				ArgumentCaptor.forClass(IeltsPartEvaluationEntity.class);
		verify(partMapper).updateById(captor.capture());
		assertEquals(existing.getCreatedAt(), captor.getValue().getCreatedAt());
	}

	@Test
	void savesFinalEvaluationWithTheFinalMapperAndPreservesExistingCreationTime() {
		IeltsEvaluationMapper mapper = mock(IeltsEvaluationMapper.class);
		IeltsPartEvaluationMapper partMapper = mock(IeltsPartEvaluationMapper.class);
		IeltsEvaluationEntity existing = new IeltsEvaluationEntity();
		OffsetDateTime createdAt = OffsetDateTime.of(
				2026, 8, 4, 8, 0, 0, 0, ZoneOffset.UTC);
		existing.setCreatedAt(createdAt);
		when(mapper.selectById("ielts_mock_ielts-mock-1")).thenReturn(existing);
		when(mapper.updateById(any(IeltsEvaluationEntity.class))).thenReturn(1);

		new IeltsEvaluationRepository(mapper, partMapper)
				.save("ielts-mock-1", "ignored-for-final", finalResult());

		ArgumentCaptor<IeltsEvaluationEntity> captor =
				ArgumentCaptor.forClass(IeltsEvaluationEntity.class);
		verify(mapper).updateById(captor.capture());
		IeltsEvaluationEntity saved = captor.getValue();
		assertEquals("ielts_mock_ielts-mock-1", saved.getEvaluationId());
		assertEquals("ielts-mock-1", saved.getIeltsId());
		assertEquals("COMPLETED", saved.getEvaluationStatus());
		assertEquals(createdAt, saved.getCreatedAt());
		assertEquals(new BigDecimal("6.5"), saved.getOverallBandScore());
		assertEquals(List.of("表达连贯"), List.of(saved.getStrengths()));
	}

	@Test
	void convertsMapperFailuresAndUnexpectedAffectedCountsToPersistenceFailures() {
		IeltsEvaluationMapper mapper = mock(IeltsEvaluationMapper.class);
		IeltsPartEvaluationMapper partMapper = mock(IeltsPartEvaluationMapper.class);
		when(partMapper.selectById(any())).thenReturn(null);
		when(partMapper.insert(any(IeltsPartEvaluationEntity.class))).thenReturn(0);
		IeltsEvaluationRepository repository =
				new IeltsEvaluationRepository(mapper, partMapper);

		EvaluationException partFailure = assertThrows(
				EvaluationException.class,
				() -> repository.savePart("ielts-1", "session-1", result()));
		assertEquals(EvaluationErrorCode.PERSISTENCE_FAILED, partFailure.errorCode());

		when(mapper.selectById(any())).thenReturn(null);
		when(mapper.insert(any(IeltsEvaluationEntity.class))).thenThrow(
				new IllegalStateException("database unavailable"));
		EvaluationException finalFailure = assertThrows(
				EvaluationException.class,
				() -> repository.saveFinal("ielts-1", finalResult()));
		assertEquals(EvaluationErrorCode.PERSISTENCE_FAILED, finalFailure.errorCode());
	}

	@Test
	void readsPartFinalAndOrderedPartsAndTranslatesReadFailures() {
		IeltsEvaluationMapper mapper = mock(IeltsEvaluationMapper.class);
		IeltsPartEvaluationMapper partMapper = mock(IeltsPartEvaluationMapper.class);
		IeltsPartEvaluationEntity part = new IeltsPartEvaluationEntity();
		part.setSessionId("session-1");
		IeltsEvaluationEntity finalEvaluation = new IeltsEvaluationEntity();
		finalEvaluation.setIeltsId("ielts-1");
		when(partMapper.selectOne(any())).thenReturn(part);
		when(mapper.selectOne(any())).thenReturn(finalEvaluation);
		when(partMapper.selectList(any())).thenReturn(List.of(part));
		IeltsEvaluationRepository repository =
				new IeltsEvaluationRepository(mapper, partMapper);

		assertEquals("session-1", repository.findPart("session-1").orElseThrow().getSessionId());
		assertEquals("ielts-1", repository.findFinal("ielts-1").orElseThrow().getIeltsId());
		assertEquals(1, repository.findParts("ielts-1").size());

		when(partMapper.selectOne(any())).thenThrow(new IllegalStateException("read failed"));
		EvaluationException failure = assertThrows(
				EvaluationException.class,
				() -> repository.findPart("session-1"));
		assertEquals(EvaluationErrorCode.PERSISTENCE_FAILED, failure.errorCode());
	}

	@Test
	void createsPendingRowsBeforeBackgroundEvaluationStarts() {
		IeltsEvaluationMapper mapper = mock(IeltsEvaluationMapper.class);
		IeltsPartEvaluationMapper partMapper = mock(
				IeltsPartEvaluationMapper.class);
		when(partMapper.selectById("ielts_part_session-1")).thenReturn(null);
		when(partMapper.insert(any(IeltsPartEvaluationEntity.class))).thenReturn(1);
		when(mapper.selectById("ielts_mock_ielts-1")).thenReturn(null);
		when(mapper.insert(any(IeltsEvaluationEntity.class))).thenReturn(1);
		IeltsEvaluationRepository repository =
				new IeltsEvaluationRepository(mapper, partMapper);

		repository.ensurePartPending("ielts-1", "session-1", IeltsPart.PART_1);
		repository.ensureFinalPending("ielts-1");

		ArgumentCaptor<IeltsPartEvaluationEntity> part =
				ArgumentCaptor.forClass(IeltsPartEvaluationEntity.class);
		ArgumentCaptor<IeltsEvaluationEntity> complete =
				ArgumentCaptor.forClass(IeltsEvaluationEntity.class);
		verify(partMapper).insert(part.capture());
		verify(mapper).insert(complete.capture());
		assertEquals("PENDING", part.getValue().getEvaluationStatus());
		assertEquals("session-1", part.getValue().getSessionId());
		assertEquals("PENDING", complete.getValue().getEvaluationStatus());
		assertEquals("ielts-1", complete.getValue().getIeltsId());
	}

	@Test
	void resetsFailedPendingRowsAndLeavesCompletedRowsUntouched() {
		IeltsEvaluationMapper mapper = mock(IeltsEvaluationMapper.class);
		IeltsPartEvaluationMapper partMapper = mock(IeltsPartEvaluationMapper.class);
		IeltsPartEvaluationEntity failedPart = new IeltsPartEvaluationEntity();
		failedPart.setEvaluationStatus("FAILED");
		failedPart.setFailureReason("temporary failure");
		failedPart.setCompletedAt(OffsetDateTime.now());
		IeltsEvaluationEntity failedFinal = new IeltsEvaluationEntity();
		failedFinal.setEvaluationStatus("FAILED");
		failedFinal.setFailureReason("temporary failure");
		failedFinal.setCompletedAt(OffsetDateTime.now());
		when(partMapper.selectById("ielts_part_session-1")).thenReturn(failedPart);
		when(mapper.selectById("ielts_mock_ielts-1")).thenReturn(failedFinal);
		when(partMapper.update(isNull(), any())).thenReturn(1);
		when(mapper.update(isNull(), any())).thenReturn(1);
		IeltsEvaluationRepository repository =
				new IeltsEvaluationRepository(mapper, partMapper);

		assertEquals(
				"PENDING",
				repository.ensurePartPending("ielts-1", "session-1", IeltsPart.PART_1)
						.getEvaluationStatus());
		assertEquals(
				"PENDING",
				repository.ensureFinalPending("ielts-1").getEvaluationStatus());
		assertNull(failedPart.getFailureReason());
		assertNull(failedPart.getCompletedAt());
		assertNull(failedFinal.getFailureReason());
		assertNull(failedFinal.getCompletedAt());

		IeltsPartEvaluationEntity completedPart = new IeltsPartEvaluationEntity();
		completedPart.setEvaluationStatus("COMPLETED");
		IeltsEvaluationEntity completedFinal = new IeltsEvaluationEntity();
		completedFinal.setEvaluationStatus("COMPLETED");
		when(partMapper.selectById("ielts_part_session-2")).thenReturn(completedPart);
		when(mapper.selectById("ielts_mock_ielts-2")).thenReturn(completedFinal);
		assertEquals(
				completedPart,
				repository.ensurePartPending("ielts-2", "session-2", IeltsPart.PART_2));
		assertEquals(completedFinal, repository.ensureFinalPending("ielts-2"));
	}

	@Test
	void supportsAtomicLeaseAndClaimedTerminalTransitions() {
		IeltsEvaluationMapper mapper = mock(IeltsEvaluationMapper.class);
		IeltsPartEvaluationMapper partMapper = mock(IeltsPartEvaluationMapper.class);
		when(partMapper.update(isNull(), any()))
				.thenReturn(1, 0, 1, 0, 1, 0, 1, 1);
		when(mapper.update(isNull(), any()))
				.thenReturn(1, 0, 1, 0, 1, 0, 1, 1);
		IeltsEvaluationRepository repository =
				new IeltsEvaluationRepository(mapper, partMapper);

		assertTrue(repository.claimPart("session-1").isPresent());
		assertTrue(repository.claimPart("session-1").isEmpty());
		assertTrue(repository.renewPartLease("session-1", "lease-1"));
		assertFalse(repository.renewPartLease("session-1", "lease-1"));
		assertTrue(repository.completePartIfClaimed("session-1", "lease-1", result()));
		assertFalse(repository.completePartIfClaimed("session-1", "lease-1", result()));
		repository.markPartFailedIfClaimed("session-1", "lease-1", "failed");
		repository.markPartFailed("session-1", "failed");

		assertTrue(repository.claimFinal("ielts-1").isPresent());
		assertTrue(repository.claimFinal("ielts-1").isEmpty());
		assertTrue(repository.renewFinalLease("ielts-1", "lease-1"));
		assertFalse(repository.renewFinalLease("ielts-1", "lease-1"));
		assertTrue(repository.completeFinalIfClaimed("ielts-1", "lease-1", finalResult()));
		assertFalse(repository.completeFinalIfClaimed("ielts-1", "lease-1", finalResult()));
		repository.markFinalFailedIfClaimed("ielts-1", "lease-1", "failed");
		repository.markFinalFailed("ielts-1", "failed");
	}

	@Test
	void insertsNewFinalEvaluation() {
		IeltsEvaluationMapper mapper = mock(IeltsEvaluationMapper.class);
		IeltsPartEvaluationMapper partMapper = mock(IeltsPartEvaluationMapper.class);
		when(mapper.selectById("ielts_mock_ielts-1")).thenReturn(null);
		when(mapper.insert(any(IeltsEvaluationEntity.class))).thenReturn(1);

		new IeltsEvaluationRepository(mapper, partMapper)
				.saveFinal("ielts-1", finalResult());

		ArgumentCaptor<IeltsEvaluationEntity> captor =
				ArgumentCaptor.forClass(IeltsEvaluationEntity.class);
		verify(mapper).insert(captor.capture());
		assertEquals("ielts_mock_ielts-1", captor.getValue().getEvaluationId());
		assertEquals(captor.getValue().getCreatedAt(), captor.getValue().getUpdatedAt());
	}

	private IeltsEvaluationResult result() {
		return new IeltsEvaluationResult(
				IeltsPart.PART_1,
				"DIAGNOSTIC",
				new BigDecimal("6.5"),
				new BigDecimal("7.0"),
				new BigDecimal("6.0"),
				new BigDecimal("6.0"),
				new BigDecimal("6.5"),
				"本次为单 Part 诊断。",
				List.of("表达连贯"),
				List.of("丰富词汇"),
				List.of(),
				List.of(),
				"回答直接且衔接清楚。",
				"能够使用话题词汇，但改述有限。",
				"简单句准确，复杂结构控制有限。",
				"基于 3 轮原始语音，整体清晰可懂。");
	}

	private IeltsEvaluationResult finalResult() {
		return new IeltsEvaluationResult(
				null,
				"FINAL",
				new BigDecimal("6.5"),
				new BigDecimal("7.0"),
				new BigDecimal("6.0"),
				new BigDecimal("6.0"),
				new BigDecimal("6.5"),
				"完整模拟考试。",
				List.of("表达连贯"),
				List.of("丰富词汇"),
				List.of(),
				List.of("use a wider range"),
				"流利。",
				"词汇准确。",
				"语法稳定。",
				"发音清晰。");
	}
}
