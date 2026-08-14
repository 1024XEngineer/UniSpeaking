package com.unispeaking.service.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.unispeaking.component.session.RealtimeSessionCoordinator;
import com.unispeaking.component.statemachine.IeltsPart2StateMachine;
import com.unispeaking.component.statemachine.IeltsQuestionStateMachine;
import com.unispeaking.component.statemachine.ScenarioDialogueStateMachine;
import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.po.scene.IeltsPracticeRecord;
import com.unispeaking.domain.vo.scene.CustomStage;
import com.unispeaking.domain.vo.scene.IeltsContent;
import com.unispeaking.domain.vo.scene.IeltsMode;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.IeltsStage;
import com.unispeaking.infrastructure.persistence.repository.scene.IeltsPracticeRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.service.scene.CustomSceneFlowService;
import com.unispeaking.service.scene.FreeChatSceneService;
import com.unispeaking.service.scene.IeltsSceneFlowService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SceneFlowServiceTest {

	@Test
	void flowBaseClassProvidesTheSharedConcreteImplementation() {
		Set<String> methods = List.of(SceneFlowService.class.getDeclaredMethods())
				.stream()
				.filter(method -> !method.isSynthetic())
				.map(java.lang.reflect.Method::getName)
				.collect(Collectors.toSet());

		assertEquals(
				Set.of("start", "current", "next", "isCompleted", "clear"),
				methods);
		assertFalse(SceneFlowService.class.isInterface());
		assertTrue(SceneFlowService.class.isAssignableFrom(
				CustomSceneFlowService.class));
		assertTrue(SceneFlowService.class.isAssignableFrom(
				IeltsSceneFlowService.class));
		assertTrue(!SceneFlowService.class.isAssignableFrom(
				FreeChatSceneService.class));
		Set<String> customMethods = List.of(
				CustomSceneFlowService.class.getDeclaredMethods()).stream()
				.map(java.lang.reflect.Method::getName)
				.collect(Collectors.toSet());
		Set<String> ieltsMethods = List.of(
				IeltsSceneFlowService.class.getDeclaredMethods()).stream()
				.map(java.lang.reflect.Method::getName)
				.collect(Collectors.toSet());
		assertTrue(customMethods.containsAll(Set.of(
				"advanceDialogueState",
				"getDialogueState")));
		assertTrue(ieltsMethods.containsAll(Set.of(
				"advanceDialogueState",
				"getDialogueState",
				"advancePart2State",
				"getPart2State")));
	}

	@Test
	void customFlowFollowsLearningStagesAndExposesCurrentContent() {
		SceneRepository repository = mock(SceneRepository.class);
		SceneGenerationResponse scene = scene("custom_def456");
		when(repository.findGeneratedById(scene.sceneId()))
				.thenReturn(Optional.of(scene));
		CustomSceneFlowService service = new CustomSceneFlowService(
				repository,
				mock(ScenarioDialogueStateMachine.class),
				mock(RealtimeSessionCoordinator.class));

		assertEquals(CustomStage.WORD, service.start(scene.sceneId()));
		assertEquals("membership", service.content(scene.sceneId()).getFirst().englishText());
		assertEquals(CustomStage.PHRASE, service.next(scene.sceneId()));
		assertEquals(CustomStage.SENTENCE, service.next(scene.sceneId()));
		assertEquals(CustomStage.DIALOGUE, service.next(scene.sceneId()));
		assertEquals(CustomStage.COMPLETED, service.next(scene.sceneId()));
		assertTrue(service.isCompleted(scene.sceneId()));
	}

	@Test
	void partPracticeStartsAtSelectedPartAndCompletesInOneStep() {
		IeltsPracticeRepository repository = mock(IeltsPracticeRepository.class);
		IeltsPracticeRecord practice = practice(
				"ielts_practice",
				IeltsMode.PART_PRACTICE,
				IeltsPart.PART_2);
		when(repository.findPractice(practice.ieltsId()))
				.thenReturn(Optional.of(practice));
		IeltsSceneFlowService service = ieltsFlow(repository);

		assertEquals(IeltsStage.PART2, service.start(practice.ieltsId()));
		assertEquals(IeltsStage.COMPLETED, service.next(practice.ieltsId()));
		assertTrue(service.isCompleted(practice.ieltsId()));
	}

	@Test
	void mockExamFlowsThroughAllThreeParts() {
		IeltsPracticeRepository repository = mock(IeltsPracticeRepository.class);
		IeltsPracticeRecord practice = practice(
				"ielts_mock",
				IeltsMode.MOCK_TEST,
				null);
		when(repository.findPractice(practice.ieltsId()))
				.thenReturn(Optional.of(practice));
		IeltsSceneFlowService service = ieltsFlow(repository);

		assertEquals(IeltsStage.PART1, service.start(practice.ieltsId()));
		assertEquals(IeltsStage.PART2, service.next(practice.ieltsId()));
		assertEquals(IeltsStage.PART3, service.next(practice.ieltsId()));
		assertEquals(IeltsStage.COMPLETED, service.next(practice.ieltsId()));
	}

	private SceneGenerationResponse scene(String sceneId) {
		return new SceneGenerationResponse(
				sceneId,
				List.of(item("word_1", "membership")),
				List.of(item("phrase_1", "ask about")),
				List.of(item("sentence_1", "Could you help me?")),
				"dialogue prompt");
	}

	private IeltsSceneFlowService ieltsFlow(
			IeltsPracticeRepository repository) {
		return new IeltsSceneFlowService(
				repository,
				mock(IeltsQuestionStateMachine.class),
				mock(IeltsPart2StateMachine.class),
				mock(RealtimeSessionCoordinator.class));
	}

	private IeltsPracticeRecord practice(
			String id,
			IeltsMode mode,
			IeltsPart part) {
		return new IeltsPracticeRecord(
				id,
				UUID.randomUUID(),
				mode,
				part,
				null,
				new IeltsContent(List.of(), List.of(), List.of()));
	}

	private LearningContentItem item(String contentId, String englishText) {
		return new LearningContentItem(contentId, englishText, "中文", "");
	}
}
