package com.unispeaking.infrastructure.persistence.repository.scene;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.po.scene.IeltsPracticeRecord;
import com.unispeaking.domain.po.scene.IeltsUserSettings;
import com.unispeaking.domain.po.scene.IeltsTopicPracticeSummary;
import com.unispeaking.domain.vo.scene.IeltsContent;
import com.unispeaking.domain.vo.scene.IeltsMode;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.infrastructure.persistence.entity.scene.IeltsPracticeEntity;
import com.unispeaking.infrastructure.persistence.entity.scene.UserIeltsEntity;
import com.unispeaking.infrastructure.persistence.mapper.scene.IeltsPracticeMapper;
import com.unispeaking.infrastructure.persistence.mapper.scene.UserIeltsMapper;
import com.unispeaking.infrastructure.persistence.entity.evaluation.IeltsPartEvaluationEntity;
import com.unispeaking.infrastructure.persistence.mapper.evaluation.IeltsPartEvaluationMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class IeltsPracticeRepository {
	private static final ZoneId CHECK_IN_ZONE = ZoneId.of("Asia/Shanghai");

	private final IeltsPracticeMapper practiceMapper;
	private final UserIeltsMapper userIeltsMapper;
	private final IeltsPartEvaluationMapper partEvaluationMapper;
	private final ObjectMapper objectMapper;

	public IeltsPracticeRepository(
			IeltsPracticeMapper practiceMapper,
			UserIeltsMapper userIeltsMapper,
			IeltsPartEvaluationMapper partEvaluationMapper,
			ObjectMapper objectMapper) {
		this.practiceMapper = practiceMapper;
		this.userIeltsMapper = userIeltsMapper;
		this.partEvaluationMapper = partEvaluationMapper;
		this.objectMapper = objectMapper;
	}

	public Map<String, IeltsTopicPracticeSummary> findTopicPracticeSummaries(
			UUID userId,
			IeltsPart part,
			List<String> topicIds) {
		if (topicIds == null || topicIds.isEmpty()) return Map.of();
		LambdaQueryWrapper<IeltsPracticeEntity> query =
				new LambdaQueryWrapper<IeltsPracticeEntity>()
						.eq(IeltsPracticeEntity::getUserId, userId)
						.orderByDesc(IeltsPracticeEntity::getCreatedAt);
		switch (part) {
			case PART_1 -> query.in(IeltsPracticeEntity::getPart1TopicId, topicIds);
			case PART_2 -> query.in(IeltsPracticeEntity::getPart2TopicId, topicIds);
			case PART_3 -> query.in(IeltsPracticeEntity::getPart3TopicId, topicIds);
		}
		List<IeltsPracticeEntity> practices = practiceMapper.selectList(query);
		if (practices.isEmpty()) return Map.of();
		List<String> practiceIds = practices.stream()
				.map(IeltsPracticeEntity::getIeltsId)
				.toList();
		Map<String, IeltsPartEvaluationEntity> evaluations = partEvaluationMapper
				.selectList(new LambdaQueryWrapper<IeltsPartEvaluationEntity>()
						.eq(IeltsPartEvaluationEntity::getPart, part.name())
						.eq(IeltsPartEvaluationEntity::getEvaluationStatus, "COMPLETED")
						.in(IeltsPartEvaluationEntity::getIeltsId, practiceIds)
						.orderByDesc(IeltsPartEvaluationEntity::getCompletedAt))
				.stream()
				.collect(Collectors.toMap(
						IeltsPartEvaluationEntity::getIeltsId,
						Function.identity(),
						(left, right) -> left,
						LinkedHashMap::new));
		Map<String, List<IeltsPracticeEntity>> byTopic = practices.stream()
				.filter(item -> evaluations.containsKey(item.getIeltsId()))
				.collect(Collectors.groupingBy(
						item -> topicId(item, part),
						LinkedHashMap::new,
						Collectors.toList()));
		Map<String, IeltsTopicPracticeSummary> result = new LinkedHashMap<>();
		byTopic.forEach((topicId, items) -> {
			IeltsPracticeEntity latest = items.getFirst();
			IeltsPartEvaluationEntity latestEvaluation = evaluations.get(
					latest.getIeltsId());
			int mockCount = (int) items.stream()
					.filter(item -> "MOCK_TEST".equals(item.getMode()))
					.count();
			int randomCount = (int) items.stream()
					.filter(item -> "PART_PRACTICE".equals(item.getMode()))
					.filter(item -> "RANDOM".equals(item.getTopicSelectionMethod()))
					.count();
			int selectedCount = items.size() - mockCount - randomCount;
			String latestType = "MOCK_TEST".equals(latest.getMode())
					? "MOCK_TEST"
					: "RANDOM".equals(latest.getTopicSelectionMethod())
							? "RANDOM_PART_PRACTICE"
							: "SELECTED_PART_PRACTICE";
			result.put(topicId, new IeltsTopicPracticeSummary(
					topicId,
					items.size(),
					mockCount,
					randomCount,
					selectedCount,
					latestType,
					averageScore(latestEvaluation),
					latestEvaluation.getSummary(),
					latestEvaluation.getCompletedAt()));
		});
		return result;
	}

	private String topicId(IeltsPracticeEntity practice, IeltsPart part) {
		return switch (part) {
			case PART_1 -> practice.getPart1TopicId();
			case PART_2 -> practice.getPart2TopicId();
			case PART_3 -> practice.getPart3TopicId();
		};
	}

	private BigDecimal averageScore(IeltsPartEvaluationEntity evaluation) {
		List<BigDecimal> values = java.util.stream.Stream.of(
				evaluation.getFluencyCoherenceScore(),
				evaluation.getLexicalResourceScore(),
				evaluation.getGrammaticalRangeAccuracyScore(),
				evaluation.getPronunciationScore())
				.filter(java.util.Objects::nonNull)
				.toList();
		if (values.isEmpty()) return null;
		return values.stream()
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.divide(BigDecimal.valueOf(values.size()), 1, RoundingMode.HALF_UP);
	}

	public IeltsUserSettings getOrCreateSettings(UUID userId) {
		UserIeltsEntity existing = userIeltsMapper.selectById(userId);
		if (existing != null) {
			return toSettings(existing);
		}
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		UserIeltsEntity created = new UserIeltsEntity();
		created.setUserId(userId);
		created.setTodayCompletedCount(0);
		created.setCurrentStreakDays(0);
		created.setTotalCheckInDays(0);
		created.setCreatedAt(now);
		created.setUpdatedAt(now);
		try {
			userIeltsMapper.insert(created);
			return toSettings(created);
		}
		catch (RuntimeException exception) {
			UserIeltsEntity concurrent = userIeltsMapper.selectById(userId);
			if (concurrent != null) {
				return toSettings(concurrent);
			}
			throw persistenceFailure(exception);
		}
	}

	public IeltsUserSettings updateSettings(
			UUID userId,
			java.math.BigDecimal targetScore,
			String preferredVoice) {
		getOrCreateSettings(userId);
		LambdaUpdateWrapper<UserIeltsEntity> update =
				new LambdaUpdateWrapper<UserIeltsEntity>()
						.eq(UserIeltsEntity::getUserId, userId)
						.set(UserIeltsEntity::getUpdatedAt,
								OffsetDateTime.now(ZoneOffset.UTC));
		if (targetScore != null) {
			update.set(UserIeltsEntity::getTargetScore, targetScore);
		}
		if (preferredVoice != null && !preferredVoice.isBlank()) {
			update.set(UserIeltsEntity::getPreferredVoice, preferredVoice);
		}
		try {
			if (userIeltsMapper.update(null, update) != 1) {
				throw persistenceFailure(null);
			}
			return toSettings(userIeltsMapper.selectById(userId));
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure(exception);
		}
	}

	public void createPractice(IeltsPracticeRecord record) {
		IeltsPracticeEntity entity = new IeltsPracticeEntity();
		entity.setIeltsId(record.ieltsId());
		entity.setUserId(record.userId());
		entity.setMode(record.mode().name());
		entity.setSelectedPart(record.selectedPart() == null
				? null
				: record.selectedPart().name());
		entity.setSelectedTopicId(record.selectedTopicId());
		entity.setTopicSelectionMethod(record.topicSelectionMethod());
		entity.setPart1TopicId(record.part1TopicId());
		entity.setPart2TopicId(record.part2TopicId());
		entity.setPart3TopicId(record.part3TopicId());
		try {
			entity.setContent(objectMapper.writeValueAsString(record.content()));
		}
		catch (JacksonException exception) {
			throw new BusinessException(
					"IELTS_CONTENT_INVALID",
					"IELTS 训练内容无法序列化");
		}
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		entity.setCreatedAt(now);
		entity.setUpdatedAt(now);
		try {
			if (practiceMapper.insert(entity) != 1) {
				throw persistenceFailure(null);
			}
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure(exception);
		}
	}

	public Optional<IeltsPracticeRecord> findPractice(String ieltsId) {
		if (ieltsId == null || ieltsId.isBlank()) {
			return Optional.empty();
		}
		try {
			IeltsPracticeEntity entity = practiceMapper.selectById(ieltsId);
			if (entity == null) {
				return Optional.empty();
			}
			return Optional.of(new IeltsPracticeRecord(
					entity.getIeltsId(),
					entity.getUserId(),
					IeltsMode.valueOf(entity.getMode()),
					entity.getSelectedPart() == null
							? null
							: IeltsPart.valueOf(entity.getSelectedPart()),
					entity.getSelectedTopicId(),
					entity.getTopicSelectionMethod(),
					entity.getPart1TopicId(),
					entity.getPart2TopicId(),
					entity.getPart3TopicId(),
					objectMapper.readValue(
							entity.getContent(),
							IeltsContent.class)));
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure(exception);
		}
	}

	public void incrementCompletedCount(UUID userId) {
		try {
			for (int attempt = 0; attempt < 5; attempt++) {
				UserIeltsEntity current = userIeltsMapper.selectById(userId);
				if (current == null) {
					throw persistenceFailure(null);
				}
				int completed = current.getTodayCompletedCount() == null
						? 0
						: current.getTodayCompletedCount();
				if (completed >= 5) {
					throw dailyLimitReached();
				}
				LocalDate today = LocalDate.now(CHECK_IN_ZONE);
				LocalDate lastCheckInDate = current.getLastCheckInDate();
				boolean firstCompletionToday = !today.equals(lastCheckInDate);
				int currentStreak = current.getCurrentStreakDays() == null
						? 0
						: current.getCurrentStreakDays();
				int totalCheckIns = current.getTotalCheckInDays() == null
						? 0
						: current.getTotalCheckInDays();
				LambdaUpdateWrapper<UserIeltsEntity> update =
						new LambdaUpdateWrapper<UserIeltsEntity>()
								.eq(UserIeltsEntity::getUserId, userId)
								.eq(
										UserIeltsEntity::getTodayCompletedCount,
										completed)
								.set(
										UserIeltsEntity::getTodayCompletedCount,
										completed + 1)
								.set(
										UserIeltsEntity::getUpdatedAt,
										OffsetDateTime.now(ZoneOffset.UTC));
				if (lastCheckInDate == null) {
					update.isNull(UserIeltsEntity::getLastCheckInDate);
				}
				else {
					update.eq(UserIeltsEntity::getLastCheckInDate, lastCheckInDate);
				}
				if (firstCompletionToday) {
					int nextStreak = today.minusDays(1).equals(lastCheckInDate)
							? currentStreak + 1
							: 1;
					update.set(UserIeltsEntity::getCurrentStreakDays, nextStreak)
							.set(UserIeltsEntity::getTotalCheckInDays, totalCheckIns + 1)
							.set(UserIeltsEntity::getLastCheckInDate, today);
				}
				int updated = userIeltsMapper.update(
						null,
						update);
				if (updated == 1) {
					return;
				}
			}
			throw persistenceFailure(null);
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure(exception);
		}
	}

	public int resetCompletedCounts() {
		try {
			return userIeltsMapper.update(
					null,
					new LambdaUpdateWrapper<UserIeltsEntity>()
							.gt(UserIeltsEntity::getTodayCompletedCount, 0)
							.set(UserIeltsEntity::getTodayCompletedCount, 0));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure(exception);
		}
	}

	private BusinessException dailyLimitReached() {
		return new BusinessException(
				"IELTS_DAILY_LIMIT_REACHED",
				"今日已完成 5 次 IELTS 练习，请明天再试");
	}

	private IeltsUserSettings toSettings(UserIeltsEntity entity) {
		return new IeltsUserSettings(
				entity.getUserId(),
				entity.getTargetScore(),
				entity.getTodayCompletedCount() == null
						? 0
						: entity.getTodayCompletedCount(),
				entity.getPreferredVoice(),
				entity.getCurrentStreakDays() == null
						? 0
						: entity.getCurrentStreakDays(),
				entity.getTotalCheckInDays() == null
						? 0
						: entity.getTotalCheckInDays(),
				entity.getLastCheckInDate());
	}

	private BusinessException persistenceFailure(Throwable cause) {
		return new BusinessException(
				"IELTS_PERSISTENCE_FAILED",
				"IELTS 练习记录保存失败");
	}
}
