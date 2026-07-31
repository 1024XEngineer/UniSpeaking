package com.unispeaking.infrastructure.persistence.repository.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.unispeaking.infrastructure.persistence.codec.evaluation.EvaluationJsonbCodec;
import com.unispeaking.infrastructure.persistence.entity.evaluation.CustomTurnEvaluation;
import com.unispeaking.infrastructure.persistence.entity.evaluation.TurnEvaluationEntity;
import com.unispeaking.infrastructure.persistence.mapper.evaluation.TurnEvaluationMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class TurnEvaluationRepositoryTest {

	@Test
	void insertsBySessionIdAndTurnNoWithoutSyntheticId() {
		TurnEvaluationMapper mapper = mock(TurnEvaluationMapper.class);
		when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
		when(mapper.insert(any(TurnEvaluationEntity.class))).thenReturn(1);
		TurnEvaluationRepository repository = repository(mapper);

		repository.upsert(evaluation());

		ArgumentCaptor<TurnEvaluationEntity> row =
				ArgumentCaptor.forClass(TurnEvaluationEntity.class);
		verify(mapper).insert(row.capture());
		assertEquals("custom_session_1", row.getValue().getSessionId());
		assertEquals(2, row.getValue().getTurnNo());
	}

	@Test
	void updatesByCompositeKeyInsteadOfUpdateById() {
		TurnEvaluationMapper mapper = mock(TurnEvaluationMapper.class);
		TurnEvaluationEntity existing = new TurnEvaluationEntity();
		existing.setSessionId("custom_session_1");
		existing.setTurnNo(2);
		existing.setCreatedAt(OffsetDateTime.parse("2026-07-31T10:00:00Z"));
		when(mapper.selectOne(any(Wrapper.class))).thenReturn(existing);
		when(mapper.update(
				any(TurnEvaluationEntity.class),
				any(Wrapper.class))).thenReturn(1);
		TurnEvaluationRepository repository = repository(mapper);

		repository.upsert(evaluation());

		verify(mapper).update(
				any(TurnEvaluationEntity.class),
				any(Wrapper.class));
		verify(mapper, never()).updateById(any(TurnEvaluationEntity.class));
	}

	private TurnEvaluationRepository repository(
			TurnEvaluationMapper mapper) {
		return new TurnEvaluationRepository(
				mapper,
				new EvaluationJsonbCodec(new ObjectMapper()));
	}

	private CustomTurnEvaluation evaluation() {
		return new CustomTurnEvaluation(
				"custom_scene1",
				"custom_session_1",
				2,
				"I would like some coffee.",
				new BigDecimal("84"),
				new BigDecimal("82"),
				new BigDecimal("80"),
				new BigDecimal("100"),
				new BigDecimal("86"),
				new BigDecimal("83"),
				"表达清楚。",
				"I'd like some coffee, please.",
				List.of());
	}
}
