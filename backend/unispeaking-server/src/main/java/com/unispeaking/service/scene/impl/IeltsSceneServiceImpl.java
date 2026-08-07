package com.unispeaking.service.scene.impl;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.prompt.IeltsExaminerPromptBuilder;
import com.unispeaking.common.util.SceneIdGenerator;
import com.unispeaking.common.util.search.TitleRelevanceCalculator;
import com.unispeaking.domain.dto.scene.IeltsCategoryResponse;
import com.unispeaking.domain.dto.scene.IeltsGenerationRequest;
import com.unispeaking.domain.dto.scene.IeltsGenerationResponse;
import com.unispeaking.domain.dto.scene.IeltsDialogueSceneContext;
import com.unispeaking.domain.dto.scene.IeltsQuestionResponse;
import com.unispeaking.domain.dto.scene.IeltsSettingsResponse;
import com.unispeaking.domain.dto.scene.IeltsTopicSearchResponse;
import com.unispeaking.domain.dto.scene.IeltsTopicSummaryResponse;
import com.unispeaking.domain.dto.scene.IeltsTrainingResponse;
import com.unispeaking.domain.dto.scene.UpdateIeltsSettingsRequest;
import com.unispeaking.domain.po.scene.IeltsPracticeRecord;
import com.unispeaking.domain.po.scene.IeltsQuestion;
import com.unispeaking.domain.po.scene.IeltsTopic;
import com.unispeaking.domain.po.scene.IeltsUserSettings;
import com.unispeaking.domain.po.scene.IeltsTopicPracticeSummary;
import com.unispeaking.domain.vo.scene.IeltsContent;
import com.unispeaking.domain.vo.scene.IeltsContentQuestion;
import com.unispeaking.domain.vo.scene.IeltsExaminerVoice;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.IeltsMode;
import com.unispeaking.domain.vo.scene.IeltsStage;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.infrastructure.persistence.repository.scene.IeltsPracticeRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.IeltsRepository;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.scene.IeltsSceneFlowService;
import com.unispeaking.service.scene.IeltsSceneService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class IeltsSceneServiceImpl implements IeltsSceneService {

	private static final int DAILY_PRACTICE_LIMIT = 5;
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
	private final IeltsPracticeRepository practiceRepository;
	private final AuthService authService;
	private final IeltsExaminerPromptBuilder promptBuilder;
	private final IeltsSceneFlowService flowService;

	public IeltsSceneServiceImpl(
			IeltsRepository repository,
			TitleRelevanceCalculator relevanceCalculator,
			IeltsPracticeRepository practiceRepository,
			AuthService authService,
			IeltsExaminerPromptBuilder promptBuilder,
			IeltsSceneFlowService flowService) {
		this.repository = repository;
		this.relevanceCalculator = relevanceCalculator;
		this.practiceRepository = practiceRepository;
		this.authService = authService;
		this.promptBuilder = promptBuilder;
		this.flowService = flowService;
	}

	@Override
	public IeltsDialogueSceneContext prepareDialogue(
			String ieltsId,
			String requestedVoiceId) {
		IeltsPracticeRecord practice = requireOwnedPractice(ieltsId);
		IeltsExaminerVoice selectedVoice =
				IeltsExaminerVoice.fromVoiceId(requestedVoiceId);
		String preferredVoice = practiceRepository
				.getOrCreateSettings(practice.userId())
				.preferredVoice();
		if (!selectedVoice.voiceId().equals(preferredVoice)) {
			practiceRepository.updateSettings(
					practice.userId(),
					null,
					selectedVoice.voiceId());
		}
		IeltsPart activePart = switch (flowService.current(ieltsId)) {
			case PART1 -> IeltsPart.PART_1;
			case PART2 -> IeltsPart.PART_2;
			case PART3 -> IeltsPart.PART_3;
			case COMPLETED -> throw new BusinessException(
					"IELTS_FLOW_COMPLETED",
					"IELTS flow is already completed");
		};
		String topicId = switch (activePart) {
			case PART_1 -> practice.part1TopicId();
			case PART_2 -> practice.part2TopicId();
			case PART_3 -> practice.part3TopicId();
		};
		String topicTitle = topicId == null
				? "IELTS Speaking"
				: repository.findTopicById(topicId)
						.map(IeltsTopic::title)
						.orElseThrow(() -> new BusinessException(
								"IELTS_TOPIC_NOT_FOUND",
								"雅思话题不存在"));
		if (practice.mode() == IeltsMode.MOCK_TEST
				&& activePart == IeltsPart.PART_1) {
			topicTitle = "familiar everyday topics";
		}
		String prompt = promptBuilder.build(
				activePart,
				topicTitle,
				practice.content(),
				selectedVoice.examinerName());
		return new IeltsDialogueSceneContext(
				practice.userId().toString(),
				practice.ieltsId(),
				practice.content(),
				activePart,
				topicTitle,
				flowService.response(ieltsId),
				prompt,
				selectedVoice.voiceId());
	}

	@Override
	public IeltsStage completeDialogue(String ieltsId, String userId) {
		IeltsPracticeRecord practice = requirePracticeOwnedBy(ieltsId, userId);
		IeltsStage next = flowService.next(ieltsId);
		if (next == IeltsStage.COMPLETED) {
			practiceRepository.incrementCompletedCount(practice.userId());
		}
		return next;
	}

	@Override
	public IeltsPracticeRecord requireOwnedPractice(String ieltsId) {
		return requirePracticeOwnedBy(
				ieltsId,
				authService.requireUserId(null));
	}

	private IeltsPracticeRecord requirePracticeOwnedBy(
			String ieltsId,
			String userId) {
		IeltsPracticeRecord practice = practiceRepository.findPractice(ieltsId)
				.orElseThrow(() -> new BusinessException(
						"IELTS_PRACTICE_NOT_FOUND",
						"IELTS 练习不存在"));
		if (!practice.userId().toString().equals(userId)) {
			throw new BusinessException(
					"IELTS_PRACTICE_ACCESS_DENIED",
					"当前用户无权访问该 IELTS 练习");
		}
		return practice;
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
		Map<String, IeltsTopicPracticeSummary> practiceSummaries =
				practiceRepository.findTopicPracticeSummaries(
						UUID.fromString(authService.requireUserId(null)),
						part,
						pageTopics.stream().map(IeltsTopic::id).toList());
		return new IeltsTopicSearchResponse(
				categories,
				pageTopics.stream()
						.map(topic -> toSummary(
								topic,
								counts.getOrDefault(topic.id(), 0L),
								practiceSummaries.get(topic.id())))
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
		IeltsTopic topic = selectTopic(part, topicId);
		List<IeltsQuestion> questions = selectQuestions(topic, part);
		return new IeltsTrainingResponse(
				topic.id(),
				topic.title(),
				part,
				questions.stream().map(this::toQuestion).toList());
	}

	@Override
	public IeltsGenerationResponse generate(IeltsGenerationRequest request) {
		validate(request);
		UUID userId = UUID.fromString(authService.requireUserId(null));
		IeltsUserSettings settings = practiceRepository.getOrCreateSettings(userId);
		if (settings.todayCompletedCount() >= DAILY_PRACTICE_LIMIT) {
			throw new BusinessException(
					"IELTS_DAILY_LIMIT_REACHED",
					"今日已完成 5 次 IELTS 练习，请明天再试");
		}

		IeltsTopic topic;
		IeltsContent content;
		IeltsPart promptPart;
		String selectedTopicId;
		String topicSelectionMethod;
		String part1TopicId = null;
		String part2TopicId = null;
		String part3TopicId = null;
		String title;
		if (request.mode() == com.unispeaking.domain.vo.scene.IeltsMode.MOCK_TEST) {
			IeltsTopic partOneTopic = selectTopic(IeltsPart.PART_1, null);
			IeltsTopic partTwoThreeTopic = selectTopic(IeltsPart.PART_2, null);
			content = new IeltsContent(
					toContentQuestions(selectQuestions(partOneTopic, IeltsPart.PART_1)),
					toContentQuestions(selectQuestions(partTwoThreeTopic, IeltsPart.PART_2)),
					toContentQuestions(selectQuestions(partTwoThreeTopic, IeltsPart.PART_3)));
			topic = partOneTopic;
			promptPart = IeltsPart.PART_1;
			selectedTopicId = partTwoThreeTopic.id();
			topicSelectionMethod = "RANDOM";
			part1TopicId = partOneTopic.id();
			part2TopicId = partTwoThreeTopic.id();
			part3TopicId = partTwoThreeTopic.id();
			title = "IELTS Speaking Mock Test";
		}
		else {
			topic = selectTopic(request.part(), request.topicId());
			List<IeltsQuestion> questions = selectQuestions(topic, request.part());
			content = toContent(request.part(), questions);
			promptPart = request.part();
			selectedTopicId = topic.id();
			topicSelectionMethod = request.topicId() == null
					|| request.topicId().isBlank()
						? "RANDOM"
						: "USER_SELECTED";
			switch (request.part()) {
				case PART_1 -> part1TopicId = topic.id();
				case PART_2 -> part2TopicId = topic.id();
				case PART_3 -> part3TopicId = topic.id();
			}
			title = topic.title();
		}
		String ieltsId = SceneIdGenerator.generate(SceneType.IELTS_SCENE);
		IeltsPracticeRecord practice = new IeltsPracticeRecord(
				ieltsId,
				userId,
				request.mode(),
				request.part(),
				selectedTopicId,
				topicSelectionMethod,
				part1TopicId,
				part2TopicId,
				part3TopicId,
				content);
		practiceRepository.createPractice(practice);
		String voiceId = settings.preferredVoice();
		if (voiceId == null || voiceId.isBlank()) {
			voiceId = IeltsExaminerVoice.DANIEL.voiceId();
			practiceRepository.updateSettings(userId, null, voiceId);
		}

		return new IeltsGenerationResponse(
				practice.ieltsId(),
				practice.mode(),
				practice.selectedPart(),
				practice.selectedTopicId(),
				title,
				practice.content(),
				voiceId,
				promptBuilder.build(
						promptPart,
						topic.title(),
						practice.content(),
						IeltsExaminerVoice.fromVoiceId(voiceId)
								.examinerName()));
	}

	@Override
	public String buildDialoguePrompt(String ieltsId, IeltsPart part) {
		IeltsPracticeRecord practice = practiceRepository.findPractice(ieltsId)
				.orElseThrow(() -> new BusinessException(
						"IELTS_PRACTICE_NOT_FOUND",
						"IELTS 练习不存在"));
		UUID currentUserId = UUID.fromString(authService.requireUserId(null));
		if (!currentUserId.equals(practice.userId())) {
			throw new BusinessException(
					"IELTS_PRACTICE_ACCESS_DENIED",
					"当前用户无权访问该 IELTS 练习");
		}
		String topicId = switch (part) {
			case PART_1 -> practice.part1TopicId();
			case PART_2 -> practice.part2TopicId();
			case PART_3 -> practice.part3TopicId();
		};
		String topicTitle = topicId == null
				? "IELTS Speaking"
				: repository.findTopicById(topicId)
						.map(IeltsTopic::title)
						.orElse("IELTS Speaking");
		String voiceId = practiceRepository
				.getOrCreateSettings(practice.userId())
				.preferredVoice();
		if (voiceId == null || voiceId.isBlank()) {
			voiceId = IeltsExaminerVoice.DANIEL.voiceId();
		}
		return promptBuilder.build(
				part,
				topicTitle,
				practice.content(),
				IeltsExaminerVoice.fromVoiceId(voiceId).examinerName());
	}

	@Override
	public IeltsSettingsResponse getSettings() {
		UUID userId = UUID.fromString(authService.requireUserId(null));
		return toSettingsResponse(practiceRepository.getOrCreateSettings(userId));
	}

	@Override
	public IeltsSettingsResponse updateSettings(UpdateIeltsSettingsRequest request) {
		if (request == null
				|| (request.targetScore() == null
				&& (request.examinerId() == null || request.examinerId().isBlank()))) {
			throw new BusinessException(
					"IELTS_SETTINGS_EMPTY",
					"请至少填写目标分数或选择一位考官");
		}
		if (request.targetScore() != null
				&& request.targetScore().remainder(java.math.BigDecimal.valueOf(0.5))
						.compareTo(java.math.BigDecimal.ZERO) != 0) {
			throw new BusinessException(
					"IELTS_TARGET_SCORE_INVALID",
					"IELTS 目标分数必须以 0.5 分为步长");
		}
		String voiceId = request.examinerId() == null
				|| request.examinerId().isBlank()
				? null
				: IeltsExaminerVoice.fromExaminerId(request.examinerId()).voiceId();
		UUID userId = UUID.fromString(authService.requireUserId(null));
		return toSettingsResponse(practiceRepository.updateSettings(
				userId,
				request.targetScore(),
				voiceId));
	}

	private IeltsSettingsResponse toSettingsResponse(IeltsUserSettings settings) {
		String examinerId = settings.preferredVoice() == null
				|| settings.preferredVoice().isBlank()
				? null
				: IeltsExaminerVoice.fromVoiceId(settings.preferredVoice()).examinerId();
		return new IeltsSettingsResponse(
				settings.targetScore(),
				settings.todayCompletedCount(),
				examinerId,
				settings.preferredVoice(),
				null,
				settings.currentStreakDays(),
				settings.totalCheckInDays(),
				settings.lastCheckInDate());
	}

	private void validate(IeltsGenerationRequest request) {
		if (request == null || request.mode() == null
				|| (request.mode() == com.unispeaking.domain.vo.scene.IeltsMode.PART_PRACTICE
				&& request.part() == null)) {
			throw new BusinessException(
					"IELTS_GENERATION_REQUEST_INVALID",
					"IELTS 训练模式和 Part 不能为空");
		}
	}

	private List<IeltsContentQuestion> toContentQuestions(
			List<IeltsQuestion> questions) {
		return questions.stream()
				.map(question -> new IeltsContentQuestion(
						question.questionText(),
						question.cuePoints(),
						question.recommendedExpressions()))
				.toList();
	}

	private IeltsTopic selectTopic(IeltsPart part, String topicId) {
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

	private List<IeltsQuestion> selectQuestions(
			IeltsTopic topic,
			IeltsPart part) {
		List<IeltsQuestion> questions = new ArrayList<>(
				repository.findQuestions(topic.id(), part));
		if (questions.isEmpty()) {
			throw new BusinessException(
					"IELTS_QUESTIONS_NOT_FOUND",
					"当前话题没有可用问题");
		}
		if (part == IeltsPart.PART_1
				&& questions.size() > PART_ONE_QUESTION_COUNT) {
			Collections.shuffle(questions);
			return List.copyOf(questions.subList(0, PART_ONE_QUESTION_COUNT));
		}
		return List.copyOf(questions);
	}

	private IeltsContent toContent(
			IeltsPart part,
			List<IeltsQuestion> questions) {
		List<IeltsContentQuestion> selected = toContentQuestions(questions);
		return switch (part) {
			case PART_1 -> new IeltsContent(selected, List.of(), List.of());
			case PART_2 -> new IeltsContent(List.of(), selected, List.of());
			case PART_3 -> new IeltsContent(List.of(), List.of(), selected);
		};
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
			long questionCount,
			IeltsTopicPracticeSummary practice) {
		return new IeltsTopicSummaryResponse(
				topic.id(),
				topic.title(),
				topic.topicType(),
				topic.category(),
				categoryLabel(topic.category()),
				topic.source(),
				questionCount,
				practice == null ? 0 : practice.practiceCount(),
				practice == null ? 0 : practice.mockTestCount(),
				practice == null ? 0 : practice.randomPartPracticeCount(),
				practice == null ? 0 : practice.selectedPartPracticeCount(),
				practice == null ? null : practice.latestPracticeType(),
				practice == null ? null : practice.latestPerformanceScore(),
				practice == null ? null : practice.latestPerformanceSummary(),
				practice == null ? null : practice.lastPracticedAt());
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
