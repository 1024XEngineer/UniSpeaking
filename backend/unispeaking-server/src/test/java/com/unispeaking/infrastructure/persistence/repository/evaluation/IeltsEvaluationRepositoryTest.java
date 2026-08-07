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
}
