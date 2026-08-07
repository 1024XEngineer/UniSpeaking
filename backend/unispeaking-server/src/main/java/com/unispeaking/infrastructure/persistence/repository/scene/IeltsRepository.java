package com.unispeaking.infrastructure.persistence.repository.scene;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unispeaking.domain.po.scene.IeltsQuestion;
import com.unispeaking.domain.po.scene.IeltsTopic;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.IeltsTopicType;
import com.unispeaking.infrastructure.persistence.codec.scene.IeltsJsonbCodec;
import com.unispeaking.infrastructure.persistence.entity.scene.IeltsQuestionEntity;
import com.unispeaking.infrastructure.persistence.entity.scene.IeltsTopicEntity;
import com.unispeaking.infrastructure.persistence.mapper.scene.IeltsQuestionMapper;
import com.unispeaking.infrastructure.persistence.mapper.scene.IeltsTopicMapper;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class IeltsRepository {

	private final IeltsTopicMapper topicMapper;
	private final IeltsQuestionMapper questionMapper;
	private final IeltsJsonbCodec jsonbCodec;

	public IeltsRepository(
			IeltsTopicMapper topicMapper,
			IeltsQuestionMapper questionMapper,
			IeltsJsonbCodec jsonbCodec) {
		this.topicMapper = topicMapper;
		this.questionMapper = questionMapper;
		this.jsonbCodec = jsonbCodec;
	}

	public List<IeltsTopic> findTopics(IeltsTopicType topicType) {
		LambdaQueryWrapper<IeltsTopicEntity> query =
				new LambdaQueryWrapper<IeltsTopicEntity>()
						.eq(IeltsTopicEntity::getTopicType, topicType.name())
						.ne(IeltsTopicEntity::getStatus, "DISABLED")
						.orderByAsc(IeltsTopicEntity::getTitle);
		return topicMapper.selectList(query).stream()
				.map(this::toTopic)
				.toList();
	}

	public Optional<IeltsTopic> findTopicById(String topicId) {
		IeltsTopicEntity entity = topicMapper.selectById(topicId);
		if (entity == null || "DISABLED".equals(entity.getStatus())) {
			return Optional.empty();
		}
		return Optional.of(toTopic(entity));
	}

	public List<IeltsTopic> findTopicsByIds(Collection<String> topicIds) {
		if (topicIds == null || topicIds.isEmpty()) {
			return List.of();
		}
		return topicMapper.selectList(
				new LambdaQueryWrapper<IeltsTopicEntity>()
						.in(IeltsTopicEntity::getId, topicIds)
						.ne(IeltsTopicEntity::getStatus, "DISABLED"))
				.stream()
				.map(this::toTopic)
				.toList();
	}

	public List<IeltsQuestion> findQuestions(
			List<String> topicIds,
			IeltsPart part) {
		if (topicIds == null || topicIds.isEmpty()) {
			return List.of();
		}
		return questionMapper.selectList(
					new LambdaQueryWrapper<IeltsQuestionEntity>()
							.in(IeltsQuestionEntity::getTopicId, topicIds)
							.eq(IeltsQuestionEntity::getPart, part.name())
							.orderByAsc(IeltsQuestionEntity::getTopicId)
							.orderByAsc(IeltsQuestionEntity::getSortNo))
				.stream()
				.map(this::toQuestion)
				.toList();
	}

	public List<IeltsQuestion> findQuestions(
			String topicId,
			IeltsPart part) {
		return findQuestions(List.of(topicId), part);
	}

	private IeltsTopic toTopic(IeltsTopicEntity entity) {
		return new IeltsTopic(
				entity.getId(),
				entity.getTitle(),
				IeltsTopicType.valueOf(entity.getTopicType()),
				entity.getCategory(),
				entity.getSource(),
				entity.getStatus());
	}

	private IeltsQuestion toQuestion(IeltsQuestionEntity entity) {
		return new IeltsQuestion(
				entity.getId(),
				entity.getTopicId(),
				IeltsPart.valueOf(entity.getPart()),
				entity.getSortNo(),
				entity.getQuestionText(),
				jsonbCodec.decodeCuePoints(entity.getCuePoints()),
				jsonbCodec.decodeExpressions(
						entity.getRecommendedExpressions()));
	}
}
