package com.unispeaking.infrastructure.persistence.repository.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.unispeaking.domain.po.scene.IeltsPracticeRecord;
import com.unispeaking.domain.vo.scene.IeltsContent;
import com.unispeaking.domain.vo.scene.IeltsContentQuestion;
import com.unispeaking.domain.vo.scene.IeltsMode;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.RecommendedExpression;
import com.unispeaking.infrastructure.persistence.entity.scene.IeltsPracticeEntity;
import com.unispeaking.infrastructure.persistence.entity.scene.UserIeltsEntity;
import com.unispeaking.infrastructure.persistence.mapper.scene.IeltsPracticeMapper;
import com.unispeaking.infrastructure.persistence.mapper.scene.UserIeltsMapper;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class IeltsPracticeRepositoryTest {

	@BeforeAll
	static void initializeMybatisMetadata() {
		MapperBuilderAssistant assistant = new MapperBuilderAssistant(
				new MybatisConfiguration(),
				"ielts-practice-repository-test");
		TableInfoHelper.initTableInfo(assistant, IeltsPracticeEntity.class);
		TableInfoHelper.initTableInfo(assistant, UserIeltsEntity.class);
	}

	private final IeltsPracticeMapper practiceMapper =
			mock(IeltsPracticeMapper.class);
	private final UserIeltsMapper userIeltsMapper =
			mock(UserIeltsMapper.class);
	private final com.unispeaking.infrastructure.persistence.mapper.evaluation.IeltsPartEvaluationMapper partEvaluationMapper =
			mock(com.unispeaking.infrastructure.persistence.mapper.evaluation.IeltsPartEvaluationMapper.class);
	private final IeltsPracticeRepository repository =
			new IeltsPracticeRepository(
					practiceMapper,
					userIeltsMapper,
					partEvaluationMapper,
					new ObjectMapper());

	@Test
	void persistsStableContentJsonContract() {
		when(practiceMapper.insert(any(IeltsPracticeEntity.class))).thenReturn(1);
		UUID userId = UUID.fromString(
				"33333333-3333-4333-8333-333333333333");

		repository.createPractice(new IeltsPracticeRecord(
				"ielts_session_1",
				userId,
				IeltsMode.PART_PRACTICE,
				IeltsPart.PART_1,
				"topic-weekends",
				new IeltsContent(
						List.of(new IeltsContentQuestion(
								"What do you do at weekends?",
								List.of(),
								List.of(new RecommendedExpression(
										"phrase",
										"I tend to...",
										"我通常……",
										"")))),
						List.of(),
						List.of())));

		ArgumentCaptor<IeltsPracticeEntity> entity =
				ArgumentCaptor.forClass(IeltsPracticeEntity.class);
		verify(practiceMapper).insert(entity.capture());
		assertEquals("PART_PRACTICE", entity.getValue().getMode());
		assertTrue(entity.getValue().getContent().contains("\"part1\""));
		assertTrue(entity.getValue().getContent()
				.contains("\"recommended_expressions\""));
		assertTrue(entity.getValue().getContent().contains("\"part2\":[]"));
		assertTrue(entity.getValue().getContent().contains("\"part3\":[]"));
	}

	@Test
	void incrementsCompletedCountWithAnOptimisticUpdate() {
		UUID userId = UUID.fromString(
				"33333333-3333-4333-8333-333333333333");
		UserIeltsEntity current = new UserIeltsEntity();
		current.setUserId(userId);
		current.setTodayCompletedCount(4);
		when(userIeltsMapper.selectById(userId)).thenReturn(current);
		when(userIeltsMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

		repository.incrementCompletedCount(userId);

		verify(userIeltsMapper).update(isNull(), any(Wrapper.class));
	}

	@Test
	void resetsNonZeroCompletedCounts() {
		when(userIeltsMapper.update(isNull(), any(Wrapper.class))).thenReturn(3);

		assertEquals(3, repository.resetCompletedCounts());

		verify(userIeltsMapper).update(isNull(), any(Wrapper.class));
	}
}
