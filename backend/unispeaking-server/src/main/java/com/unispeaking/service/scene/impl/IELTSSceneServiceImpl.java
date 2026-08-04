package com.unispeaking.service.scene.impl;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.util.search.TitleRelevanceCalculator;
import com.unispeaking.domain.dto.scene.IeltsCategoryResponse;
import com.unispeaking.domain.dto.scene.IeltsQuestionResponse;
import com.unispeaking.domain.dto.scene.IeltsTopicSearchResponse;
import com.unispeaking.domain.dto.scene.IeltsTopicSummaryResponse;
import com.unispeaking.domain.dto.scene.IeltsTrainingResponse;
import com.unispeaking.domain.po.scene.IeltsQuestion;
import com.unispeaking.domain.po.scene.IeltsTopic;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.infrastructure.persistence.repository.scene.IeltsRepository;
import com.unispeaking.service.scene.IELTSSceneService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class IELTSSceneServiceImpl implements IELTSSceneService {

	private static final int PART_ONE_QUESTION_COUNT = 4;
	private static final double MINIMUM_RELEVANCE = 0.08;
	private static final Map<String, String> CATEGORY_LABELS = Map.of(
			"REQUIRED", "必考题",
			"PERSON", "人物",
			"OBJECT", "事物",
			"EVENT", "事件",
			"PLACE", "地点");

	private final IeltsRepository repository;
	private final TitleRelevanceCalculator relevanceCalculator;

	public IELTSSceneServiceImpl(
			IeltsRepository repository,
			TitleRelevanceCalculator relevanceCalculator) {
		this.repository = repository;
		this.relevanceCalculator = relevanceCalculator;
	}

	@Override
	public IeltsTopicSearchResponse searchTopics(
			IeltsPart part,
			String category,
			String keyword,
			int page,
			int pageSize) {
		if (page < 1 || pageSize < 1 || pageSize > 50) {
			throw new BusinessException(
					"IELTS_PAGINATION_INVALID",
					"分页参数不合法");
		}
		String normalizedCategory = normalizeCategory(category);
		String normalizedKeyword = keyword == null ? "" : keyword.trim();
		List<IeltsTopic> allTopics = repository.findTopics(part.topicType());
		List<IeltsCategoryResponse> categories = categories(allTopics);

		List<IeltsTopic> topics = allTopics.stream()
				.filter(topic -> normalizedCategory == null
						|| normalizedCategory.equals(topic.category()))
				.toList();
		if (!normalizedKeyword.isEmpty()) {
			topics = topics.stream()
					.map(topic -> new ScoredTopic(
							topic,
							relevanceCalculator.isKeywordMatch(
									topic.title(),
									normalizedKeyword),
							relevanceCalculator.score(
									topic.title(),
									normalizedKeyword)))
					.filter(item -> item.keywordMatch()
							|| item.score() >= MINIMUM_RELEVANCE)
					.sorted(Comparator
							.comparing(ScoredTopic::keywordMatch)
							.reversed()
							.thenComparing(Comparator
									.comparingDouble(ScoredTopic::score)
									.reversed())
							.thenComparing(item -> item.topic().title()))
					.map(ScoredTopic::topic)
					.toList();
		}

		long total = topics.size();
		int totalPages = (int) Math.ceil((double) total / pageSize);
		long requestedFrom = (long) (page - 1) * pageSize;
		int fromIndex = (int) Math.min(requestedFrom, topics.size());
		int toIndex = Math.min(fromIndex + pageSize, topics.size());
		List<IeltsTopic> pageTopics = topics.subList(fromIndex, toIndex);
		Map<String, Long> counts = questionCounts(pageTopics, part);
		return new IeltsTopicSearchResponse(
				categories,
				pageTopics.stream()
						.map(topic -> toSummary(
								topic,
								counts.getOrDefault(topic.id(), 0L)))
						.toList(),
				page,
				pageSize,
				total,
				totalPages);
	}

	@Override
	public IeltsTrainingResponse prepareTraining(
			IeltsPart part,
			String topicId) {
		IeltsTopic topic = findTopic(part, topicId);
		List<IeltsQuestion> questions = new ArrayList<>(
				repository.findQuestions(topic.id(), part));
		if (questions.isEmpty()) {
			throw new BusinessException(
					"IELTS_QUESTIONS_NOT_FOUND",
					"当前话题没有可用问题");
		}
		if (part == IeltsPart.PART_1) {
			Collections.shuffle(questions);
			questions = questions.subList(
					0,
					Math.min(PART_ONE_QUESTION_COUNT, questions.size()));
		}
		return new IeltsTrainingResponse(
				topic.id(),
				topic.title(),
				part,
				questions.stream().map(this::toQuestion).toList());
	}

	private IeltsTopic findTopic(IeltsPart part, String topicId) {
		if (topicId != null && !topicId.isBlank()) {
			IeltsTopic topic = repository.findTopicById(topicId)
					.orElseThrow(() -> new BusinessException(
							"IELTS_TOPIC_NOT_FOUND",
							"雅思话题不存在"));
			if (topic.topicType() != part.topicType()) {
				throw new BusinessException(
						"IELTS_PART_MISMATCH",
						"话题与训练 Part 不匹配");
			}
			return topic;
		}
		List<IeltsTopic> candidates = repository.findTopics(part.topicType());
		if (candidates.isEmpty()) {
			throw new BusinessException(
					"IELTS_TOPIC_NOT_FOUND",
					"当前 Part 没有可用话题");
		}
		return candidates.get(ThreadLocalRandom.current().nextInt(
				candidates.size()));
	}

	private Map<String, Long> questionCounts(
			List<IeltsTopic> topics,
			IeltsPart part) {
		return repository.findQuestions(
					topics.stream().map(IeltsTopic::id).toList(),
					part)
				.stream()
				.collect(Collectors.groupingBy(
						IeltsQuestion::topicId,
						Collectors.counting()));
	}

	private List<IeltsCategoryResponse> categories(List<IeltsTopic> topics) {
		Map<String, String> values = topics.stream()
				.map(IeltsTopic::category)
				.distinct()
				.sorted(Comparator.comparing(this::categoryLabel))
				.collect(Collectors.toMap(
						Function.identity(),
						this::categoryLabel,
						(left, right) -> left,
						LinkedHashMap::new));
		return values.entrySet().stream()
				.map(entry -> new IeltsCategoryResponse(
						entry.getKey(),
						entry.getValue()))
				.toList();
	}

	private IeltsTopicSummaryResponse toSummary(
			IeltsTopic topic,
			long questionCount) {
		return new IeltsTopicSummaryResponse(
				topic.id(),
				topic.title(),
				topic.topicType(),
				topic.category(),
				categoryLabel(topic.category()),
				topic.source(),
				questionCount);
	}

	private IeltsQuestionResponse toQuestion(IeltsQuestion question) {
		return new IeltsQuestionResponse(
				question.id(),
				question.part(),
				question.sortNo(),
				question.questionText(),
				question.cuePoints(),
				question.recommendedExpressions());
	}

	private String normalizeCategory(String category) {
		return category == null || category.isBlank() || "ALL".equals(category)
				? null
				: category.trim().toUpperCase();
	}

	private String categoryLabel(String category) {
		return CATEGORY_LABELS.getOrDefault(category, category);
	}

	private record ScoredTopic(
			IeltsTopic topic,
			boolean keywordMatch,
			double score) {
	}
}
