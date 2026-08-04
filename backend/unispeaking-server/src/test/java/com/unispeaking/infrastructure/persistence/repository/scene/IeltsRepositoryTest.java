package com.unispeaking.infrastructure.persistence.repository.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.IeltsTopicType;
import com.unispeaking.infrastructure.persistence.codec.scene.IeltsJsonbCodec;
import com.unispeaking.infrastructure.persistence.entity.scene.IeltsQuestionEntity;
import com.unispeaking.infrastructure.persistence.entity.scene.IeltsTopicEntity;
import com.unispeaking.infrastructure.persistence.mapper.scene.IeltsQuestionMapper;
import com.unispeaking.infrastructure.persistence.mapper.scene.IeltsTopicMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class IeltsRepositoryTest {

	@BeforeAll
	static void initializeMybatisMetadata() {
		MapperBuilderAssistant assistant = new MapperBuilderAssistant(
				new MybatisConfiguration(),
				"ielts-repository-test");
		TableInfoHelper.initTableInfo(assistant, IeltsTopicEntity.class);
		TableInfoHelper.initTableInfo(assistant, IeltsQuestionEntity.class);
	}

	private final IeltsTopicMapper topicMapper = mock(IeltsTopicMapper.class);
	private final IeltsQuestionMapper questionMapper =
			mock(IeltsQuestionMapper.class);
	private final IeltsJsonbCodec codec = mock(IeltsJsonbCodec.class);
	private final IeltsRepository repository = new IeltsRepository(
			topicMapper,
			questionMapper,
			codec);

	@Test
	void findsAndMapsAvailableTopics() {
		IeltsTopicEntity entity = topicEntity("topic-home", "Home");
		when(topicMapper.selectList(any(Wrapper.class)))
				.thenReturn(List.of(entity));

		var topics = repository.findTopics(IeltsTopicType.PART_1_POOL);

		assertEquals(1, topics.size());
		assertEquals("topic-home", topics.getFirst().id());
		assertEquals(IeltsTopicType.PART_1_POOL,
				topics.getFirst().topicType());
	}

	@Test
	void disabledOrMissingTopicIsNotReturned() {
		IeltsTopicEntity disabled = topicEntity("topic-disabled", "Disabled");
		disabled.setStatus("DISABLED");
		when(topicMapper.selectById("topic-disabled")).thenReturn(disabled);
		when(topicMapper.selectById("topic-missing")).thenReturn(null);

		assertTrue(repository.findTopicById("topic-disabled").isEmpty());
		assertTrue(repository.findTopicById("topic-missing").isEmpty());
	}

	@Test
	void mapsQuestionJsonFieldsThroughCodec() {
		IeltsQuestionEntity entity = new IeltsQuestionEntity();
		entity.setId("question-1");
		entity.setTopicId("topic-home");
		entity.setPart("PART_2");
		entity.setSortNo(1);
		entity.setQuestionText("Describe your home.");
		entity.setCuePoints("[\"where it is\"]");
		entity.setRecommendedExpressions("[]");
		when(questionMapper.selectList(any(Wrapper.class)))
				.thenReturn(List.of(entity));
		when(codec.decodeCuePoints(entity.getCuePoints()))
				.thenReturn(List.of("where it is"));
		when(codec.decodeExpressions(entity.getRecommendedExpressions()))
				.thenReturn(List.of());

		var questions = repository.findQuestions(
				List.of("topic-home"),
				IeltsPart.PART_2);

		assertEquals(1, questions.size());
		assertEquals("where it is", questions.getFirst().cuePoints().getFirst());
		assertEquals(IeltsPart.PART_2, questions.getFirst().part());
	}

	@Test
	void emptyTopicIdsAvoidDatabaseQuery() {
		assertTrue(repository.findQuestions(List.of(), IeltsPart.PART_1).isEmpty());
		verify(questionMapper, never()).selectList(any(Wrapper.class));
	}

	private IeltsTopicEntity topicEntity(String id, String title) {
		IeltsTopicEntity entity = new IeltsTopicEntity();
		entity.setId(id);
		entity.setTitle(title);
		entity.setTopicType("PART_1_POOL");
		entity.setCategory("REQUIRED");
		entity.setSource("XDF");
		entity.setStatus("READY");
		return entity;
	}
}
