package com.unispeaking.service.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.prompt.IeltsExaminerPromptBuilder;
import com.unispeaking.common.util.search.TitleRelevanceCalculator;
import com.unispeaking.domain.dto.scene.IeltsGenerationRequest;
import com.unispeaking.domain.dto.scene.UpdateIeltsSettingsRequest;
import com.unispeaking.domain.po.scene.IeltsPracticeRecord;
import com.unispeaking.domain.po.scene.IeltsQuestion;
import com.unispeaking.domain.po.scene.IeltsTopic;
import com.unispeaking.domain.po.scene.IeltsUserSettings;
import com.unispeaking.domain.vo.scene.IeltsMode;
import com.unispeaking.domain.vo.scene.IeltsContent;
import com.unispeaking.domain.vo.scene.IeltsContentQuestion;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.IeltsStage;
import com.unispeaking.domain.vo.scene.IeltsTopicType;
import com.unispeaking.infrastructure.persistence.repository.scene.IeltsPracticeRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.IeltsRepository;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.scene.IeltsSceneService;
import com.unispeaking.service.scene.IeltsSceneFlowService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;
import java.util.stream.IntStream;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IeltsSceneServiceTest {

	private final IeltsRepository repository = mock(IeltsRepository.class);
	private final IeltsPracticeRepository practiceRepository =
			mock(IeltsPracticeRepository.class);
	private final AuthService authService = mock(AuthService.class);
	private final IeltsSceneFlowService flowService =
			mock(IeltsSceneFlowService.class);
	private final IeltsSceneService service = new IeltsSceneService(
			repository,
			new TitleRelevanceCalculator(),
			practiceRepository,
			authService,
			new IeltsExaminerPromptBuilder(),
			flowService);
	private final UUID userId = UUID.randomUUID();

	@BeforeEach
	void allowGeneration() {
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(practiceRepository.getOrCreateSettings(userId)).thenReturn(
				new IeltsUserSettings(userId, null, 0, "Harvey"));
	}

	@Test
	void partOneSelectsFourQuestionsAndPersistsGeneratedContent() {
		IeltsTopic topic = topic("weekends", IeltsTopicType.PART_1_POOL);
		when(repository.findTopicById(topic.id())).thenReturn(Optional.of(topic));
		when(repository.findQuestions(topic.id(), IeltsPart.PART_1))
				.thenReturn(questions(topic.id(), IeltsPart.PART_1, 8));

		var result = service.generate(request(IeltsPart.PART_1, topic.id()));

		assertTrue(result.ieltsId().startsWith("ielts_"));
		assertEquals(4, result.content().part1().size());
		assertTrue(result.content().part2().isEmpty());
		assertTrue(result.scenePrompt().contains("Active IELTS Layer: Part 1"));
		assertTrue(result.scenePrompt().contains("My name is Daniel"));
		assertEquals("Harvey", result.voiceId());
		ArgumentCaptor<IeltsPracticeRecord> record =
				ArgumentCaptor.forClass(IeltsPracticeRecord.class);
		verify(practiceRepository).createPractice(record.capture());
		assertEquals(result.content(), record.getValue().content());
	}

	@Test
	void partTwoKeepsItsCompleteQuestionSet() {
		IeltsTopic topic = topic("museum", IeltsTopicType.PART_2_3_BUNDLE);
		when(repository.findTopicById(topic.id())).thenReturn(Optional.of(topic));
		when(repository.findQuestions(topic.id(), IeltsPart.PART_2))
				.thenReturn(questions(topic.id(), IeltsPart.PART_2, 3));

		var result = service.generate(request(IeltsPart.PART_2, topic.id()));

		assertEquals(3, result.content().part2().size());
		assertTrue(result.content().part1().isEmpty());
		assertTrue(result.content().part3().isEmpty());
	}

	@Test
	void missingTopicIdChoosesFromTheRequestedPartPool() {
		IeltsTopic topic = topic("random", IeltsTopicType.PART_1_POOL);
		when(repository.findTopics(IeltsTopicType.PART_1_POOL))
				.thenReturn(List.of(topic));
		when(repository.findQuestions(topic.id(), IeltsPart.PART_1))
				.thenReturn(questions(topic.id(), IeltsPart.PART_1, 2));

		var result = service.generate(request(IeltsPart.PART_1, null));

		assertEquals(topic.id(), result.selectedTopicId());
	}

	@Test
	void mockTestGeneratesAllThreePartsInOneScene() {
		IeltsTopic partOne = topic("part-one", IeltsTopicType.PART_1_POOL);
		IeltsTopic partTwoThree = topic("part-two-three", IeltsTopicType.PART_2_3_BUNDLE);
		when(repository.findTopics(IeltsTopicType.PART_1_POOL))
				.thenReturn(List.of(partOne));
		when(repository.findTopics(IeltsTopicType.PART_2_3_BUNDLE))
				.thenReturn(List.of(partTwoThree));
		when(repository.findQuestions(partOne.id(), IeltsPart.PART_1))
				.thenReturn(questions(partOne.id(), IeltsPart.PART_1, 6));
		when(repository.findQuestions(partTwoThree.id(), IeltsPart.PART_2))
				.thenReturn(questions(partTwoThree.id(), IeltsPart.PART_2, 1));
		when(repository.findQuestions(partTwoThree.id(), IeltsPart.PART_3))
				.thenReturn(questions(partTwoThree.id(), IeltsPart.PART_3, 3));

		var result = service.generate(new IeltsGenerationRequest(
				IeltsMode.MOCK_TEST,
				null,
				null));

		assertEquals(4, result.content().part1().size());
		assertEquals(1, result.content().part2().size());
		assertEquals(3, result.content().part3().size());
		assertEquals(null, result.selectedPart());
		assertEquals(partTwoThree.id(), result.selectedTopicId());
	}

	@Test
	void topicFromAnotherPartIsRejected() {
		IeltsTopic topic = topic("part-one", IeltsTopicType.PART_1_POOL);
		when(repository.findTopicById(topic.id())).thenReturn(Optional.of(topic));

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.generate(request(IeltsPart.PART_2, topic.id())));

		assertEquals("IELTS_PART_MISMATCH", exception.code());
	}

	@Test
	void generationIsRejectedAtDailyLimit() {
		when(practiceRepository.getOrCreateSettings(userId)).thenReturn(
				new IeltsUserSettings(userId, null, 5, null));

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.generate(request(IeltsPart.PART_1, "topic")));

		assertEquals("IELTS_DAILY_LIMIT_REACHED", exception.code());
	}

	@Test
	void topicSearchReturnsPartSpecificPagedResults() {
		IeltsTopic topic = topic("weekends", IeltsTopicType.PART_1_POOL);
		when(repository.findTopics(IeltsTopicType.PART_1_POOL))
				.thenReturn(List.of(topic));
		when(repository.findQuestions(
				List.of(topic.id()),
				IeltsPart.PART_1))
				.thenReturn(questions(topic.id(), IeltsPart.PART_1, 8));

		var result = service.searchTopics(
				IeltsPart.PART_1,
				"ALL",
				"weekends",
				1,
				10);

		assertEquals(1, result.total());
		assertEquals(topic.id(), result.topics().getFirst().id());
		assertEquals(8, result.topics().getFirst().questionCount());
	}

	@Test
	void trainingPreviewUsesTheSamePartOneSelectionRule() {
		IeltsTopic topic = topic("weekends", IeltsTopicType.PART_1_POOL);
		when(repository.findTopicById(topic.id())).thenReturn(Optional.of(topic));
		when(repository.findQuestions(topic.id(), IeltsPart.PART_1))
				.thenReturn(questions(topic.id(), IeltsPart.PART_1, 8));

		var result = service.prepareTraining(IeltsPart.PART_1, topic.id());

		assertEquals(topic.id(), result.topicId());
		assertEquals(4, result.questions().size());
	}

	@Test
	void completedFlowOnlyAdvancesTheSceneAndLeavesCountingToEvaluation() {
		IeltsPracticeRecord practice = mockPractice("ielts_complete");
		when(practiceRepository.findPractice(practice.ieltsId()))
				.thenReturn(Optional.of(practice));
		when(flowService.next(practice.ieltsId()))
				.thenReturn(IeltsStage.COMPLETED);

		service.completeDialogue(practice.ieltsId(), userId.toString());

		verify(practiceRepository, never()).incrementCompletedCount(userId);
	}

	@Test
	void intermediateMockPartDoesNotConsumeTheDailyPractice() {
		IeltsPracticeRecord practice = mockPractice("ielts_continue");
		when(practiceRepository.findPractice(practice.ieltsId()))
				.thenReturn(Optional.of(practice));
		when(flowService.next(practice.ieltsId()))
				.thenReturn(IeltsStage.PART2);

		service.completeDialogue(practice.ieltsId(), userId.toString());

		verify(practiceRepository, never()).incrementCompletedCount(userId);
	}

	@Test
	void dialogueRejectsMissingAndForeignPractices() {
		when(practiceRepository.findPractice("missing")).thenReturn(Optional.empty());
		BusinessException missing = assertThrows(BusinessException.class,
				() -> service.completeDialogue("missing", userId.toString()));
		assertEquals("IELTS_PRACTICE_NOT_FOUND", missing.code());

		when(practiceRepository.findPractice("foreign"))
				.thenReturn(Optional.of(new IeltsPracticeRecord("foreign", UUID.randomUUID(),
						IeltsMode.PART_PRACTICE, IeltsPart.PART_1, "topic", emptyContent())));
		BusinessException denied = assertThrows(BusinessException.class,
				() -> service.completeDialogue("foreign", userId.toString()));
		assertEquals("IELTS_PRACTICE_ACCESS_DENIED", denied.code());
	}

	@Test
	void generationRejectsInvalidRequestsMissingTopicsAndEmptyQuestions() {
		BusinessException invalid = assertThrows(BusinessException.class, () -> service.generate(null));
		assertEquals("IELTS_GENERATION_REQUEST_INVALID", invalid.code());
		BusinessException missingTopic = assertThrows(BusinessException.class,
				() -> service.generate(request(IeltsPart.PART_1, "missing")));
		assertEquals("IELTS_TOPIC_NOT_FOUND", missingTopic.code());

		IeltsTopic topic = topic("empty", IeltsTopicType.PART_1_POOL);
		when(repository.findTopicById(topic.id())).thenReturn(Optional.of(topic));
		when(repository.findQuestions(topic.id(), IeltsPart.PART_1)).thenReturn(List.of());
		BusinessException missingQuestions = assertThrows(BusinessException.class,
				() -> service.generate(request(IeltsPart.PART_1, topic.id())));
		assertEquals("IELTS_QUESTIONS_NOT_FOUND", missingQuestions.code());
	}

	@Test
	void generationUsesDefaultVoiceAndMarksExplicitTopicSelection() {
		IeltsTopic topic = topic("weekends", IeltsTopicType.PART_1_POOL);
		when(practiceRepository.getOrCreateSettings(userId)).thenReturn(new IeltsUserSettings(userId, null, 0, null));
		when(repository.findTopicById(topic.id())).thenReturn(Optional.of(topic));
		when(repository.findQuestions(topic.id(), IeltsPart.PART_1)).thenReturn(questions(topic.id(), IeltsPart.PART_1, 1));

		var result = service.generate(request(IeltsPart.PART_1, topic.id()));

		assertEquals("Harvey", result.voiceId());
		ArgumentCaptor<IeltsPracticeRecord> record = ArgumentCaptor.forClass(IeltsPracticeRecord.class);
		verify(practiceRepository).createPractice(record.capture());
		assertEquals("USER_SELECTED", record.getValue().topicSelectionMethod());
		verify(practiceRepository).updateSettings(userId, null, "Harvey");
	}

	@Test
	void settingsRejectInvalidValuesAndMapsAnExaminer() {
		BusinessException empty = assertThrows(BusinessException.class, () -> service.updateSettings(null));
		assertEquals("IELTS_SETTINGS_EMPTY", empty.code());
		BusinessException invalidScore = assertThrows(BusinessException.class,
				() -> service.updateSettings(new UpdateIeltsSettingsRequest(new BigDecimal("6.3"), null)));
		assertEquals("IELTS_TARGET_SCORE_INVALID", invalidScore.code());

		when(practiceRepository.updateSettings(userId, new BigDecimal("7.5"), "Harvey"))
				.thenReturn(new IeltsUserSettings(userId, new BigDecimal("7.5"), 2, "Harvey"));
		var settings = service.updateSettings(new UpdateIeltsSettingsRequest(new BigDecimal("7.5"), "daniel"));
		assertEquals("daniel", settings.examinerId());
		assertEquals(2, settings.todayCompletedCount());
	}

	@Test
	void searchRejectsInvalidPagingAndBuildsCategories() {
		BusinessException invalid = assertThrows(BusinessException.class,
				() -> service.searchTopics(IeltsPart.PART_1, "ALL", null, 0, 10));
		assertEquals("IELTS_PAGINATION_INVALID", invalid.code());
		IeltsTopic person = new IeltsTopic("person", "A person", IeltsTopicType.PART_1_POOL, "PERSON", "seed", "ACTIVE");
		IeltsTopic required = topic("required", IeltsTopicType.PART_1_POOL);
		when(repository.findTopics(IeltsTopicType.PART_1_POOL)).thenReturn(List.of(person, required));
		when(repository.findQuestions(List.of("person", "required"), IeltsPart.PART_1)).thenReturn(List.of());
		when(practiceRepository.findTopicPracticeSummaries(userId, IeltsPart.PART_1, List.of("person", "required"))).thenReturn(java.util.Map.of());

		var result = service.searchTopics(IeltsPart.PART_1, "ALL", null, 1, 10);

		assertEquals(List.of("PERSON", "REQUIRED"), result.categories().stream().map(item -> item.code()).toList());
		assertEquals(2, result.total());
	}

	@Test
	void prepareDialogueRejectsCompletedFlowsAndMissingTopics() {
		IeltsPracticeRecord practice = new IeltsPracticeRecord("completed", userId,
				IeltsMode.PART_PRACTICE, IeltsPart.PART_1, "topic", emptyContent());
		when(practiceRepository.findPractice("completed")).thenReturn(Optional.of(practice));
		when(flowService.current("completed")).thenReturn(IeltsStage.COMPLETED);
		BusinessException completed = assertThrows(BusinessException.class,
				() -> service.prepareDialogue("completed", "Harvey"));
		assertEquals("IELTS_FLOW_COMPLETED", completed.code());

		when(flowService.current("completed")).thenReturn(IeltsStage.PART1);
		when(repository.findTopicById("topic")).thenReturn(Optional.empty());
		BusinessException missingTopic = assertThrows(BusinessException.class,
				() -> service.prepareDialogue("completed", "Harvey"));
		assertEquals("IELTS_TOPIC_NOT_FOUND", missingTopic.code());
	}

	@Test
	void buildDialoguePromptChecksOwnershipAndUsesDefaultVoice() {
		when(practiceRepository.findPractice("missing")).thenReturn(Optional.empty());
		BusinessException missing = assertThrows(BusinessException.class,
				() -> service.buildDialoguePrompt("missing", IeltsPart.PART_1));
		assertEquals("IELTS_PRACTICE_NOT_FOUND", missing.code());

		IeltsPracticeRecord foreign = new IeltsPracticeRecord("foreign", UUID.randomUUID(),
				IeltsMode.PART_PRACTICE, IeltsPart.PART_1, "topic", emptyContent());
		when(practiceRepository.findPractice("foreign")).thenReturn(Optional.of(foreign));
		BusinessException denied = assertThrows(BusinessException.class,
				() -> service.buildDialoguePrompt("foreign", IeltsPart.PART_1));
		assertEquals("IELTS_PRACTICE_ACCESS_DENIED", denied.code());

		IeltsPracticeRecord owned = new IeltsPracticeRecord("owned", userId,
				IeltsMode.PART_PRACTICE, IeltsPart.PART_1, null,
				new IeltsContent(List.of(new IeltsContentQuestion(
						"What do you enjoy?", List.of(), List.of())), List.of(), List.of()));
		when(practiceRepository.findPractice("owned")).thenReturn(Optional.of(owned));
		when(practiceRepository.getOrCreateSettings(userId)).thenReturn(new IeltsUserSettings(userId, null, 0, null));
		assertTrue(service.buildDialoguePrompt("owned", IeltsPart.PART_1).contains("My name is Daniel"));
	}

	@Test
	void getSettingsAndSearchRespectCurrentUserAndFilteredCategory() {
		when(practiceRepository.getOrCreateSettings(userId)).thenReturn(
				new IeltsUserSettings(userId, new BigDecimal("6.5"), 3, "Mione"));
		var settings = service.getSettings();
		assertEquals("margaret", settings.examinerId());
		assertEquals(3, settings.todayCompletedCount());

		IeltsTopic required = topic("required", IeltsTopicType.PART_1_POOL);
		IeltsTopic person = new IeltsTopic("person", "A person", IeltsTopicType.PART_1_POOL,
				"PERSON", "seed", "ACTIVE");
		when(repository.findTopics(IeltsTopicType.PART_1_POOL)).thenReturn(List.of(required, person));
		when(repository.findQuestions(List.of("person"), IeltsPart.PART_1)).thenReturn(List.of());
		when(practiceRepository.findTopicPracticeSummaries(userId, IeltsPart.PART_1, List.of("person")))
				.thenReturn(java.util.Map.of());

		var response = service.searchTopics(IeltsPart.PART_1, " person ", null, 2, 1);
		assertEquals(0, response.topics().size());
		assertEquals(1, response.total());
		assertEquals(1, response.totalPages());
	}

	@Test
	void prepareDialogueMapsEveryActivePartAndSynchronizesChangedVoice() {
		IeltsPracticeRecord practice = new IeltsPracticeRecord(
				"dialogue", userId, IeltsMode.PART_PRACTICE, IeltsPart.PART_1,
				"part-1-topic", "USER_SELECTED", "part-1-topic", "part-2-topic",
				"part-3-topic", dialogueContent());
		when(practiceRepository.findPractice("dialogue")).thenReturn(Optional.of(practice));
		when(practiceRepository.getOrCreateSettings(userId))
				.thenReturn(new IeltsUserSettings(userId, null, 0, "Harvey"));
		when(flowService.response("dialogue")).thenReturn(null);

		for (var part : List.of(IeltsStage.PART1, IeltsStage.PART2, IeltsStage.PART3)) {
			when(flowService.current("dialogue")).thenReturn(part);
			String topicId = switch (part) {
				case PART1 -> "part-1-topic";
				case PART2 -> "part-2-topic";
				case PART3 -> "part-3-topic";
				case COMPLETED -> throw new AssertionError();
			};
			when(repository.findTopicById(topicId)).thenReturn(Optional.of(
					topic(topicId, part == IeltsStage.PART1
							? IeltsTopicType.PART_1_POOL : IeltsTopicType.PART_2_3_BUNDLE)));

				var context = service.prepareDialogue("dialogue", "Harvey");

			assertEquals(part == IeltsStage.PART1 ? IeltsPart.PART_1
					: part == IeltsStage.PART2 ? IeltsPart.PART_2 : IeltsPart.PART_3,
				context.activePart());
			assertEquals("Harvey", context.voiceId());
			if (part == IeltsStage.PART1 && practice.mode() == IeltsMode.PART_PRACTICE) {
				assertTrue(context.topicTitle().startsWith("Topic part-1-topic"));
			}
		}

		verify(practiceRepository, org.mockito.Mockito.never())
				.updateSettings(userId, null, "Daniel");
	}

	@Test
	void prepareDialogueUsesMockPartOneTopicFallbackAndDefaultTopicTitle() {
		IeltsPracticeRecord mockPractice = new IeltsPracticeRecord(
				"mock-dialogue", userId, IeltsMode.MOCK_TEST, null, null,
				"RANDOM", null, null, null, dialogueContent());
		when(practiceRepository.findPractice("mock-dialogue")).thenReturn(Optional.of(mockPractice));
		when(practiceRepository.getOrCreateSettings(userId))
				.thenReturn(new IeltsUserSettings(userId, null, 0, "Daniel"));
		when(flowService.current("mock-dialogue")).thenReturn(IeltsStage.PART1);
		when(flowService.response("mock-dialogue")).thenReturn(null);

			var context = service.prepareDialogue("mock-dialogue", "Harvey");

		assertEquals(IeltsPart.PART_1, context.activePart());
		assertEquals("familiar everyday topics", context.topicTitle());
		verify(repository, never()).findTopicById(null);
	}

	@Test
	void prepareDialogueUsesDefaultTitleForNullTopicAndRequiresOwnedPractice() {
		IeltsPracticeRecord practice = new IeltsPracticeRecord(
				"untitled", userId, IeltsMode.PART_PRACTICE, IeltsPart.PART_2,
				null, "RANDOM", null, null, null, dialogueContent());
		when(practiceRepository.findPractice("untitled")).thenReturn(Optional.of(practice));
		when(practiceRepository.getOrCreateSettings(userId))
				.thenReturn(new IeltsUserSettings(userId, null, 0, "Daniel"));
		when(flowService.current("untitled")).thenReturn(IeltsStage.PART2);
		when(flowService.response("untitled")).thenReturn(null);

			var context = service.prepareDialogue("untitled", "Harvey");

		assertEquals("IELTS Speaking", context.topicTitle());
		assertEquals(userId.toString(), context.userId());

		when(authService.requireUserId(null)).thenReturn(UUID.randomUUID().toString());
		BusinessException denied = assertThrows(BusinessException.class,
			() -> service.prepareDialogue("untitled", "Harvey"));
		assertEquals("IELTS_PRACTICE_ACCESS_DENIED", denied.code());
	}

	@Test
	void searchSupportsPartTwoAndThreeAndFiltersLowRelevanceTopics() {
		IeltsTopic partTwo = topic("part-two", IeltsTopicType.PART_2_3_BUNDLE);
		IeltsTopic unrelated = new IeltsTopic(
				"unrelated", "Completely unrelated", IeltsTopicType.PART_2_3_BUNDLE,
				"OBJECT", "import", "ACTIVE");
		when(repository.findTopics(IeltsTopicType.PART_2_3_BUNDLE))
				.thenReturn(List.of(partTwo, unrelated));
		when(repository.findQuestions(List.of("part-two"), IeltsPart.PART_2))
				.thenReturn(List.of());
		when(practiceRepository.findTopicPracticeSummaries(
				userId, IeltsPart.PART_2, List.of("part-two")))
				.thenReturn(java.util.Map.of());

		var result = service.searchTopics(IeltsPart.PART_2, "ALL", "part-two", 1, 50);

		assertEquals(1, result.total());
		assertEquals("part-two", result.topics().getFirst().id());
	}

	@Test
	void trainingMapsPartTwoAndPartThreeQuestionsAndRejectsEmptyPools() {
		IeltsTopic topic = topic("bundle", IeltsTopicType.PART_2_3_BUNDLE);
		for (IeltsPart part : List.of(IeltsPart.PART_2, IeltsPart.PART_3)) {
			when(repository.findTopicById(topic.id())).thenReturn(Optional.of(topic));
			when(repository.findQuestions(topic.id(), part)).thenReturn(
					questions(topic.id(), part, 2));
			var response = service.prepareTraining(part, topic.id());
			assertEquals(part, response.part());
			assertEquals(2, response.questions().size());
		}

		when(repository.findQuestions(topic.id(), IeltsPart.PART_3)).thenReturn(List.of());
		BusinessException exception = assertThrows(BusinessException.class,
				() -> service.prepareTraining(IeltsPart.PART_3, topic.id()));
		assertEquals("IELTS_QUESTIONS_NOT_FOUND", exception.code());
	}

	@Test
	void generationSupportsPartThreeAndRejectsMissingRandomCandidates() {
		IeltsTopic topic = topic("part-three", IeltsTopicType.PART_2_3_BUNDLE);
		when(repository.findTopicById(topic.id())).thenReturn(Optional.of(topic));
		when(repository.findQuestions(topic.id(), IeltsPart.PART_3))
				.thenReturn(questions(topic.id(), IeltsPart.PART_3, 1));

		var result = service.generate(request(IeltsPart.PART_3, topic.id()));
		assertEquals(IeltsPart.PART_3, result.selectedPart());
		assertEquals(topic.id(), result.selectedTopicId());
		verify(practiceRepository).createPractice(org.mockito.ArgumentMatchers.argThat(
				practice -> practice.part3TopicId().equals(topic.id())));

		when(repository.findTopics(IeltsTopicType.PART_2_3_BUNDLE)).thenReturn(List.of());
		BusinessException missing = assertThrows(BusinessException.class,
				() -> service.generate(request(IeltsPart.PART_3, null)));
		assertEquals("IELTS_TOPIC_NOT_FOUND", missing.code());
	}

	@Test
	void settingsMapBlankVoiceToNullAndUnknownExaminerIsRejected() {
		when(practiceRepository.updateSettings(userId, new BigDecimal("7.0"), null))
				.thenReturn(new IeltsUserSettings(userId, new BigDecimal("7.0"), 0, ""));
		var response = service.updateSettings(
				new UpdateIeltsSettingsRequest(new BigDecimal("7.0"), " "));
		assertEquals(null, response.examinerId());

		assertThrows(BusinessException.class,
				() -> service.updateSettings(new UpdateIeltsSettingsRequest(null, "unknown")));
	}

	private IeltsPracticeRecord mockPractice(String ieltsId) {
		return new IeltsPracticeRecord(
				ieltsId,
				userId,
				IeltsMode.MOCK_TEST,
				null,
				null,
				new IeltsContent(List.of(), List.of(), List.of()));
	}

	private IeltsContent emptyContent() {
		return new IeltsContent(List.of(), List.of(), List.of());
	}

	private IeltsContent dialogueContent() {
		return new IeltsContent(
				List.of(new IeltsContentQuestion("Question 1", List.of(), List.of())),
				List.of(new IeltsContentQuestion("Cue card", List.of("point"), List.of())),
				List.of(new IeltsContentQuestion("Question 3", List.of(), List.of())));
	}

	private IeltsGenerationRequest request(IeltsPart part, String topicId) {
		return new IeltsGenerationRequest(
				IeltsMode.PART_PRACTICE,
				part,
				topicId);
	}

	private IeltsTopic topic(String id, IeltsTopicType type) {
		return new IeltsTopic(
				id,
				"Topic " + id,
				type,
				"REQUIRED",
				"import",
				"ACTIVE");
	}

	private List<IeltsQuestion> questions(
			String topicId,
			IeltsPart part,
			int count) {
		return IntStream.rangeClosed(1, count)
				.mapToObj(index -> new IeltsQuestion(
						topicId + "-" + part + "-" + index,
						topicId,
						part,
						index,
						"Question " + index,
						List.of(),
						List.of()))
				.toList();
	}
}
