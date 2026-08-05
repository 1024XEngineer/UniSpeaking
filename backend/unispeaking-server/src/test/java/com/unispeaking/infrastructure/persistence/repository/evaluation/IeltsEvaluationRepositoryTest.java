package com.unispeaking.infrastructure.persistence.repository.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.unispeaking.domain.dto.evaluation.IeltsEvaluationResult;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.infrastructure.persistence.entity.evaluation.IeltsEvaluationEntity;
import com.unispeaking.infrastructure.persistence.mapper.evaluation.IeltsEvaluationMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class IeltsEvaluationRepositoryTest {

	@BeforeAll
	static void initializeMybatisMetadata() {
		TableInfoHelper.initTableInfo(
				new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
				IeltsEvaluationEntity.class);
	}

	@Test
	void insertsEvaluationIntoExistingIeltsTableShape() {
		IeltsEvaluationMapper mapper = mock(IeltsEvaluationMapper.class);
		when(mapper.selectById("ielts-session-1")).thenReturn(null);
		when(mapper.insert(any(IeltsEvaluationEntity.class))).thenReturn(1);
		IeltsEvaluationRepository repository =
				new IeltsEvaluationRepository(mapper, new ObjectMapper());

		repository.save("ielts-mock-1", "ielts-session-1", result());

		ArgumentCaptor<IeltsEvaluationEntity> captor =
				ArgumentCaptor.forClass(IeltsEvaluationEntity.class);
		verify(mapper).insert(captor.capture());
		IeltsEvaluationEntity saved = captor.getValue();
		assertEquals("ielts-session-1", saved.getSessionId());
		assertEquals("ielts-mock-1", saved.getIeltsId());
		assertEquals("PART_1", saved.getPart());
		assertEquals("DIAGNOSTIC", saved.getAssessmentType());
		assertEquals(new BigDecimal("6.5"), saved.getOverallBandScore());
		assertEquals(new BigDecimal("7.0"), saved.getFluencyCoherenceScore());
		assertEquals(List.of("表达连贯"), List.of(saved.getStrengths()));
		assertEquals(saved.getCreatedAt(), saved.getUpdatedAt());
	}

	@Test
	void updatesExistingRowWithoutChangingCreatedAt() {
		IeltsEvaluationMapper mapper = mock(IeltsEvaluationMapper.class);
		IeltsEvaluationEntity existing = new IeltsEvaluationEntity();
		existing.setSessionId("ielts-session-1");
		existing.setCreatedAt(OffsetDateTime.of(
				2026, 8, 4, 8, 0, 0, 0, ZoneOffset.UTC));
		when(mapper.selectById("ielts-session-1")).thenReturn(existing);
		when(mapper.updateById(any(IeltsEvaluationEntity.class))).thenReturn(1);

		new IeltsEvaluationRepository(mapper, new ObjectMapper())
				.save("ielts-mock-1", "ielts-session-1", result());

		ArgumentCaptor<IeltsEvaluationEntity> captor =
				ArgumentCaptor.forClass(IeltsEvaluationEntity.class);
		verify(mapper).updateById(captor.capture());
		assertEquals(existing.getCreatedAt(), captor.getValue().getCreatedAt());
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
				List.of("丰富词汇"));
	}
}
