package com.unispeaking.infrastructure.persistence.repository.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.po.scene.IeltsPracticeRecord;
import com.unispeaking.domain.po.scene.IeltsTopicPracticeSummary;
import com.unispeaking.domain.po.scene.IeltsUserSettings;
import com.unispeaking.domain.vo.scene.IeltsContent;
import com.unispeaking.domain.vo.scene.IeltsContentQuestion;
import com.unispeaking.domain.vo.scene.IeltsMode;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.RecommendedExpression;
import com.unispeaking.infrastructure.persistence.entity.scene.IeltsPracticeEntity;
import com.unispeaking.infrastructure.persistence.entity.scene.UserIeltsEntity;
import com.unispeaking.infrastructure.persistence.entity.evaluation.IeltsPartEvaluationEntity;
import com.unispeaking.infrastructure.persistence.mapper.scene.IeltsPracticeMapper;
import com.unispeaking.infrastructure.persistence.mapper.scene.UserIeltsMapper;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.util.UUID;
import java.time.LocalDate;
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
		TableInfoHelper.initTableInfo(assistant, IeltsPartEvaluationEntity.class);
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

	@Test
	void returnsExistingSettingsAndCreatesMissingSettings() {
		UUID userId = UUID.randomUUID();
		UserIeltsEntity existing = settingsEntity(userId, 2);
		existing.setTargetScore(new BigDecimal("7.0"));
		existing.setPreferredVoice("Daniel");
		when(userIeltsMapper.selectById(userId)).thenReturn(existing);

		IeltsUserSettings found = repository.getOrCreateSettings(userId);

		assertEquals(new BigDecimal("7.0"), found.targetScore());
		assertEquals(2, found.todayCompletedCount());
		verify(userIeltsMapper, org.mockito.Mockito.never()).insert(any(UserIeltsEntity.class));

		UUID missingId = UUID.randomUUID();
		when(userIeltsMapper.selectById(missingId)).thenReturn(null);
		when(userIeltsMapper.insert(any(UserIeltsEntity.class))).thenReturn(1);
		IeltsUserSettings created = repository.getOrCreateSettings(missingId);
		assertEquals(missingId, created.userId());
		assertEquals(0, created.todayCompletedCount());
		verify(userIeltsMapper).insert(any(UserIeltsEntity.class));
	}

	@Test
	void recoversConcurrentSettingsCreationAndWrapsFailures() {
		UUID concurrentId = UUID.randomUUID();
		when(userIeltsMapper.selectById(concurrentId))
				.thenReturn(null, settingsEntity(concurrentId, 1));
		when(userIeltsMapper.insert(any(UserIeltsEntity.class))).thenThrow(new RuntimeException("duplicate"));
		assertEquals(1, repository.getOrCreateSettings(concurrentId).todayCompletedCount());

		UUID failedId = UUID.randomUUID();
		when(userIeltsMapper.selectById(failedId)).thenReturn(null);
		BusinessException failure = assertThrows(BusinessException.class,
				() -> repository.getOrCreateSettings(failedId));
		assertEquals("IELTS_PERSISTENCE_FAILED", failure.code());
	}

	@Test
	void findPracticeHandlesBlankMissingAndStoredRecords() {
		assertFalse(repository.findPractice(" ").isPresent());
		when(practiceMapper.selectById("missing")).thenReturn(null);
		assertFalse(repository.findPractice("missing").isPresent());

		UUID userId = UUID.randomUUID();
		IeltsPracticeEntity entity = new IeltsPracticeEntity();
		entity.setIeltsId("stored");
		entity.setUserId(userId);
		entity.setMode("PART_PRACTICE");
		entity.setSelectedPart("PART_2");
		entity.setSelectedTopicId("topic");
		entity.setTopicSelectionMethod("RANDOM");
		entity.setPart2TopicId("topic");
		entity.setContent("{\"part1\":[],\"part2\":[],\"part3\":[]}");
		when(practiceMapper.selectById("stored")).thenReturn(entity);

		var found = repository.findPractice("stored").orElseThrow();
		assertEquals(IeltsPart.PART_2, found.selectedPart());
		assertEquals("RANDOM", found.topicSelectionMethod());
		assertEquals("topic", found.part2TopicId());
	}

	@Test
	void createAndUpdateWrapPersistenceFailures() {
		when(practiceMapper.insert(any(IeltsPracticeEntity.class))).thenReturn(0);
		BusinessException createFailure = assertThrows(BusinessException.class,
				() -> repository.createPractice(new IeltsPracticeRecord("failure", UUID.randomUUID(),
						IeltsMode.PART_PRACTICE, IeltsPart.PART_1, "topic", content())));
		assertEquals("IELTS_PERSISTENCE_FAILED", createFailure.code());

		UUID userId = UUID.randomUUID();
		when(userIeltsMapper.selectById(userId)).thenReturn(settingsEntity(userId, 0));
		when(userIeltsMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);
		BusinessException updateFailure = assertThrows(BusinessException.class,
				() -> repository.updateSettings(userId, new BigDecimal("6.5"), "Daniel"));
		assertEquals("IELTS_PERSISTENCE_FAILED", updateFailure.code());
	}

	@Test
	void incrementRejectsLimitsMissingSettingsAndRepeatedConflicts() {
		UUID limitedId = UUID.randomUUID();
		when(userIeltsMapper.selectById(limitedId)).thenReturn(settingsEntity(limitedId, 5));
		BusinessException limit = assertThrows(BusinessException.class,
				() -> repository.incrementCompletedCount(limitedId));
		assertEquals("IELTS_DAILY_LIMIT_REACHED", limit.code());

		UUID missingId = UUID.randomUUID();
		when(userIeltsMapper.selectById(missingId)).thenReturn(null);
		BusinessException missing = assertThrows(BusinessException.class,
				() -> repository.incrementCompletedCount(missingId));
		assertEquals("IELTS_PERSISTENCE_FAILED", missing.code());

		UUID contestedId = UUID.randomUUID();
		when(userIeltsMapper.selectById(contestedId)).thenReturn(settingsEntity(contestedId, 0));
		when(userIeltsMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);
		BusinessException contested = assertThrows(BusinessException.class,
				() -> repository.incrementCompletedCount(contestedId));
		assertEquals("IELTS_PERSISTENCE_FAILED", contested.code());
	}

	@Test
	void createsAndUpdatesSettingsWithAllOptionalFields() {
		UUID userId = UUID.randomUUID();
		when(userIeltsMapper.selectById(userId)).thenReturn(
				settingsEntity(userId, 1), settingsEntity(userId, 1));
		when(userIeltsMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

		IeltsUserSettings result = repository.updateSettings(
				userId, new BigDecimal("7.5"), "Harvey");

		assertEquals(userId, result.userId());
		verify(userIeltsMapper).update(isNull(), any(Wrapper.class));
	}

	@Test
	void findPracticeWrapsMalformedStoredJsonAsPersistenceFailure() {
		IeltsPracticeEntity entity = new IeltsPracticeEntity();
		entity.setIeltsId("broken");
		entity.setUserId(UUID.randomUUID());
		entity.setMode("PART_PRACTICE");
		entity.setSelectedPart("PART_1");
		entity.setTopicSelectionMethod("USER_SELECTED");
		entity.setContent("not-json");
		when(practiceMapper.selectById("broken")).thenReturn(entity);

		BusinessException failure = assertThrows(BusinessException.class,
				() -> repository.findPractice("broken"));

		assertEquals("IELTS_PERSISTENCE_FAILED", failure.code());
	}

	@Test
	void topicPracticeSummariesSkipEmptyTopicsAndAggregateLatestEvaluation() {
		UUID userId = UUID.randomUUID();
		assertEquals(Map.of(), repository.findTopicPracticeSummaries(
				userId, IeltsPart.PART_1, List.of()));

		IeltsPracticeEntity latest = practiceEntity("latest", "topic-1", "PART_PRACTICE", "RANDOM");
		IeltsPracticeEntity selected = practiceEntity("selected", "topic-1", "PART_PRACTICE", "USER_SELECTED");
		IeltsPracticeEntity mock = practiceEntity("mock", "topic-1", "MOCK_TEST", "RANDOM");
		when(practiceMapper.selectList(any())).thenReturn(List.of(latest, selected, mock));
		IeltsPartEvaluationEntity evaluation = new IeltsPartEvaluationEntity();
		evaluation.setIeltsId("latest");
		evaluation.setFluencyCoherenceScore(new BigDecimal("6.0"));
		evaluation.setLexicalResourceScore(new BigDecimal("7.0"));
		evaluation.setSummary("latest summary");
		evaluation.setCompletedAt(java.time.OffsetDateTime.parse("2026-08-01T00:00:00Z"));
		IeltsPartEvaluationEntity selectedEvaluation = new IeltsPartEvaluationEntity();
		selectedEvaluation.setIeltsId("selected");
		IeltsPartEvaluationEntity mockEvaluation = new IeltsPartEvaluationEntity();
		mockEvaluation.setIeltsId("mock");
		when(partEvaluationMapper.selectList(any())).thenReturn(List.of(
				evaluation, selectedEvaluation, mockEvaluation));

		Map<String, IeltsTopicPracticeSummary> summaries = repository.findTopicPracticeSummaries(
				userId, IeltsPart.PART_1, List.of("topic-1"));

		IeltsTopicPracticeSummary summary = summaries.get("topic-1");
		assertEquals(3, summary.practiceCount());
		assertEquals(1, summary.mockTestCount());
		assertEquals(1, summary.randomPartPracticeCount());
		assertEquals(1, summary.selectedPartPracticeCount());
		assertEquals(new BigDecimal("6.5"), summary.latestPerformanceScore());
		assertEquals("RANDOM_PART_PRACTICE", summary.latestPracticeType());
	}

	@Test
	void topicPracticeSummariesSupportPartTwoAndPartThreeAndIgnoreUnscoredPractices() {
		UUID userId = UUID.randomUUID();
		IeltsPracticeEntity partTwo = practiceEntity("part-two", "topic-2", "PART_PRACTICE", "USER_SELECTED");
		partTwo.setPart1TopicId(null);
		partTwo.setPart2TopicId("topic-2");
		IeltsPracticeEntity partThree = practiceEntity("part-three", "topic-3", "MOCK_TEST", "RANDOM");
		partThree.setPart1TopicId(null);
		partThree.setPart2TopicId(null);
		partThree.setPart3TopicId("topic-3");
		when(practiceMapper.selectList(any())).thenReturn(List.of(partTwo), List.of(partThree), List.of());
		when(partEvaluationMapper.selectList(any())).thenReturn(
				List.of(evaluation("part-two", "summary-2")),
				List.of(evaluation("part-three", "summary-3")));

		Map<String, IeltsTopicPracticeSummary> partTwoResult = repository.findTopicPracticeSummaries(
				userId, IeltsPart.PART_2, List.of("topic-2"));
		assertEquals("SELECTED_PART_PRACTICE", partTwoResult.get("topic-2").latestPracticeType());

		Map<String, IeltsTopicPracticeSummary> partThreeResult = repository.findTopicPracticeSummaries(
				userId, IeltsPart.PART_3, List.of("topic-3"));
		assertEquals("MOCK_TEST", partThreeResult.get("topic-3").latestPracticeType());

		when(practiceMapper.selectList(any())).thenReturn(List.of());
		assertEquals(Map.of(), repository.findTopicPracticeSummaries(
				userId, IeltsPart.PART_3, List.of("topic-3")));
	}

	@Test
	void topicPracticeSummariesReturnNullAverageWhenAllScoresAreMissing() {
		UUID userId = UUID.randomUUID();
		when(practiceMapper.selectList(any())).thenReturn(List.of(
				practiceEntity("no-score", "topic", "PART_PRACTICE", "USER_SELECTED")));
		IeltsPartEvaluationEntity evaluation = evaluation("no-score", "no score");
		when(partEvaluationMapper.selectList(any())).thenReturn(List.of(evaluation));

		var summary = repository.findTopicPracticeSummaries(
				userId, IeltsPart.PART_1, List.of("topic")).get("topic");

		assertEquals(null, summary.latestPerformanceScore());
		assertEquals("no score", summary.latestPerformanceSummary());
	}

	@Test
	void updateSettingsAllowsPartialUpdatesAndWrapsMapperExceptions() {
		UUID userId = UUID.randomUUID();
		UserIeltsEntity current = settingsEntity(userId, 1);
		current.setPreferredVoice("Daniel");
		when(userIeltsMapper.selectById(userId)).thenReturn(current);
		when(userIeltsMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
		when(userIeltsMapper.selectById(userId)).thenReturn(current);

		var unchanged = repository.updateSettings(userId, null, " ");
		assertEquals("Daniel", unchanged.preferredVoice());

		when(userIeltsMapper.update(isNull(), any(Wrapper.class)))
				.thenThrow(new IllegalStateException("db down"));
		BusinessException failure = assertThrows(BusinessException.class,
				() -> repository.updateSettings(userId, new BigDecimal("7.0"), "Harvey"));
		assertEquals("IELTS_PERSISTENCE_FAILED", failure.code());
	}

	@Test
	void incrementCompletedCountHandlesNullCountersAndConsecutiveCheckIn() {
		UUID userId = UUID.randomUUID();
		UserIeltsEntity current = settingsEntity(userId, 0);
		current.setTodayCompletedCount(null);
		current.setCurrentStreakDays(null);
		current.setTotalCheckInDays(null);
		current.setLastCheckInDate(LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).minusDays(1));
		when(userIeltsMapper.selectById(userId)).thenReturn(current);
		when(userIeltsMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

		repository.incrementCompletedCount(userId);

		verify(userIeltsMapper).update(isNull(), any(Wrapper.class));
	}

	@Test
	void incrementWrapsMapperFailureAndResetWrapsMapperFailure() {
		UUID userId = UUID.randomUUID();
		when(userIeltsMapper.selectById(userId)).thenThrow(new IllegalStateException("db down"));
		BusinessException incrementFailure = assertThrows(BusinessException.class,
				() -> repository.incrementCompletedCount(userId));
		assertEquals("IELTS_PERSISTENCE_FAILED", incrementFailure.code());

		when(userIeltsMapper.update(isNull(), any(Wrapper.class)))
				.thenThrow(new IllegalStateException("db down"));
		BusinessException resetFailure = assertThrows(BusinessException.class,
				() -> repository.resetCompletedCounts());
		assertEquals("IELTS_PERSISTENCE_FAILED", resetFailure.code());
	}

	@Test
	void createPracticeMapsNullSelectedPartAndWrapsSerializationFailure() {
		when(practiceMapper.insert(any(IeltsPracticeEntity.class))).thenReturn(1);
		repository.createPractice(new IeltsPracticeRecord(
				"mock", UUID.randomUUID(), IeltsMode.MOCK_TEST, null, null,
				"RANDOM", null, "topic", "topic", content()));
		ArgumentCaptor<IeltsPracticeEntity> entity = ArgumentCaptor.forClass(IeltsPracticeEntity.class);
		verify(practiceMapper).insert(entity.capture());
		assertEquals(null, entity.getValue().getSelectedPart());

		ObjectMapper brokenMapper = mock(ObjectMapper.class);
		when(brokenMapper.writeValueAsString(any())).thenThrow(
				new tools.jackson.core.JacksonException("cannot serialize") { });
		IeltsPracticeRepository brokenRepository = new IeltsPracticeRepository(
				practiceMapper, userIeltsMapper, partEvaluationMapper, brokenMapper);
		BusinessException failure = assertThrows(BusinessException.class,
				() -> brokenRepository.createPractice(new IeltsPracticeRecord(
						"broken", UUID.randomUUID(), IeltsMode.PART_PRACTICE, IeltsPart.PART_1,
						"topic", "USER_SELECTED", "topic", null, null, content())));
		assertEquals("IELTS_CONTENT_INVALID", failure.code());
	}

	@Test
	void readsRecordsWithNullSelectedPartAndWrapsInvalidEnum() throws Exception {
		IeltsPracticeEntity entity = new IeltsPracticeEntity();
		entity.setIeltsId("mock-record");
		entity.setUserId(UUID.randomUUID());
		entity.setMode("MOCK_TEST");
		entity.setSelectedPart(null);
		entity.setTopicSelectionMethod("RANDOM");
		entity.setContent(new ObjectMapper().writeValueAsString(content()));
		when(practiceMapper.selectById("mock-record")).thenReturn(entity);
		assertTrue(repository.findPractice("mock-record").orElseThrow().selectedPart() == null);

		entity.setMode("INVALID");
		BusinessException failure = assertThrows(BusinessException.class,
				() -> repository.findPractice("mock-record"));
		assertEquals("IELTS_PERSISTENCE_FAILED", failure.code());
	}

	@Test
	void handlesSameDayCompletionAndRetriesOptimisticConflicts() {
		UUID userId = UUID.randomUUID();
		UserIeltsEntity current = settingsEntity(userId, 1);
		current.setLastCheckInDate(LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")));
		current.setCurrentStreakDays(3);
		current.setTotalCheckInDays(7);
		when(userIeltsMapper.selectById(userId)).thenReturn(current);
		when(userIeltsMapper.update(isNull(), any(Wrapper.class))).thenReturn(0, 1);
		repository.incrementCompletedCount(userId);
		verify(userIeltsMapper, org.mockito.Mockito.times(2)).update(isNull(), any(Wrapper.class));
	}

	@Test
	void convertsNullCountersInSettingsToZero() {
		UUID userId = UUID.randomUUID();
		UserIeltsEntity entity = new UserIeltsEntity();
		entity.setUserId(userId);
		when(userIeltsMapper.selectById(userId)).thenReturn(entity);
		IeltsUserSettings settings = repository.getOrCreateSettings(userId);
		assertEquals(0, settings.todayCompletedCount());
		assertEquals(0, settings.currentStreakDays());
		assertEquals(0, settings.totalCheckInDays());
	}

	private UserIeltsEntity settingsEntity(UUID userId, int completed) {
		UserIeltsEntity entity = new UserIeltsEntity();
		entity.setUserId(userId);
		entity.setTodayCompletedCount(completed);
		return entity;
	}

	private IeltsContent content() {
		return new IeltsContent(List.of(), List.of(), List.of());
	}

	private IeltsPracticeEntity practiceEntity(
			String ieltsId,
			String topicId,
			String mode,
			String selectionMethod) {
		IeltsPracticeEntity entity = new IeltsPracticeEntity();
		entity.setIeltsId(ieltsId);
		entity.setUserId(UUID.randomUUID());
		entity.setMode(mode);
		entity.setTopicSelectionMethod(selectionMethod);
		entity.setPart1TopicId(topicId);
		entity.setCreatedAt(java.time.OffsetDateTime.parse("2026-08-01T00:00:00Z"));
		return entity;
	}

	private IeltsPartEvaluationEntity evaluation(String ieltsId, String summary) {
		IeltsPartEvaluationEntity evaluation = new IeltsPartEvaluationEntity();
		evaluation.setIeltsId(ieltsId);
		evaluation.setEvaluationStatus("COMPLETED");
		evaluation.setSummary(summary);
		evaluation.setCompletedAt(java.time.OffsetDateTime.parse("2026-08-02T00:00:00Z"));
		return evaluation;
	}
}
