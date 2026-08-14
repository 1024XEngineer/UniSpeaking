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
import com.unispeaking.domain.po.scene.IeltsPracticeRecord;
import com.unispeaking.domain.po.scene.IeltsQuestion;
import com.unispeaking.domain.po.scene.IeltsTopic;
import com.unispeaking.domain.po.scene.IeltsUserSettings;
import com.unispeaking.domain.vo.scene.IeltsMode;
import com.unispeaking.domain.vo.scene.IeltsContent;
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

	private IeltsPracticeRecord mockPractice(String ieltsId) {
		return new IeltsPracticeRecord(
				ieltsId,
				userId,
				IeltsMode.MOCK_TEST,
				null,
				null,
				new IeltsContent(List.of(), List.of(), List.of()));
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
