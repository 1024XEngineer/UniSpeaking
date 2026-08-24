package com.unispeaking.service.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.component.session.RealtimeSessionCoordinator;
import com.unispeaking.component.statemachine.IeltsPart2StateMachine;
import com.unispeaking.component.statemachine.IeltsQuestionStateMachine;
import com.unispeaking.domain.dto.session.IeltsDialogueStateResponse;
import com.unispeaking.domain.dto.session.IeltsPart2StateResponse;
import com.unispeaking.domain.po.scene.IeltsPracticeRecord;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.vo.scene.IeltsContent;
import com.unispeaking.domain.vo.scene.IeltsMode;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.IeltsPart2Event;
import com.unispeaking.domain.vo.scene.IeltsStage;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.infrastructure.persistence.repository.scene.IeltsPracticeRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IeltsSceneFlowServiceTest {

	private final IeltsPracticeRepository practices = mock(IeltsPracticeRepository.class);
	private final IeltsQuestionStateMachine questions = mock(IeltsQuestionStateMachine.class);
	private final IeltsPart2StateMachine partTwo = mock(IeltsPart2StateMachine.class);
	private final RealtimeSessionCoordinator sessions = mock(RealtimeSessionCoordinator.class);
	private final IeltsSceneFlowService service = new IeltsSceneFlowService(
			practices, questions, partTwo, sessions);
	private final UUID userId = UUID.randomUUID();

	@Test
	void startRejectsMissingPracticeAndStartsPartPracticeAtItsSelectedPart() {
		when(practices.findPractice("missing")).thenReturn(Optional.empty());
		assertCode("IELTS_PRACTICE_NOT_FOUND", () -> service.start("missing"));

		IeltsPracticeRecord partPractice = practice(
				"part-practice", IeltsMode.PART_PRACTICE, IeltsPart.PART_3);
		when(practices.findPractice("part-practice"))
				.thenReturn(Optional.of(partPractice));
		assertEquals(IeltsStage.PART3, service.start("part-practice"));
	}

	@Test
	void mockPracticeProgressesThroughAllPartsAndExposesLegacyResponse() {
		IeltsPracticeRecord practice = practice("mock", IeltsMode.MOCK_TEST, null);
		when(practices.findPractice("mock")).thenReturn(Optional.of(practice));

		assertEquals(IeltsStage.PART1, service.start("mock"));
		assertEquals(IeltsStage.PART2, service.next("mock"));
		assertEquals(com.unispeaking.domain.vo.scene.SceneFlowStage.IELTS_PART_2,
				service.response("mock").stage());
		assertEquals(IeltsStage.PART3, service.next("mock"));
		assertEquals(IeltsStage.COMPLETED, service.next("mock"));
		assertEquals(IeltsStage.COMPLETED, service.next("mock"));
	}

	@Test
	void sessionStateRequiresMatchingIeltsBindingBeforeDelegation() {
		IeltsPracticeRecord practice = practice("scene", IeltsMode.PART_PRACTICE, IeltsPart.PART_1);
		when(practices.findPractice("scene")).thenReturn(Optional.of(practice));
		AbstractSceneSession wrong = mock(AbstractSceneSession.class);
		when(wrong.getSceneType()).thenReturn(SceneType.CUSTOM_SCENE);
		when(sessions.requireOwnedSession(userId.toString(), "session")).thenReturn(wrong);
		assertCode("IELTS_SESSION_MISMATCH", () -> service.getDialogueState("scene", "session"));

		AbstractSceneSession matching = mock(AbstractSceneSession.class);
		when(matching.getSceneType()).thenReturn(SceneType.IELTS_SCENE);
		when(matching.getSceneId()).thenReturn("scene");
		when(sessions.requireOwnedSession(userId.toString(), "session")).thenReturn(matching);
		IeltsDialogueStateResponse dialogue = mock(IeltsDialogueStateResponse.class);
		IeltsPart2StateResponse part2 = mock(IeltsPart2StateResponse.class);
		when(questions.get("scene", "session")).thenReturn(dialogue);
		when(partTwo.get("scene", "session")).thenReturn(part2);

		assertEquals(dialogue, service.getDialogueState("scene", "session"));
		assertEquals(part2, service.getPart2State("scene", "session"));
	}

	@Test
	void startsAndAdvancesTheCorrectStateMachineForEachPart() {
		IeltsPracticeRecord practice = practice("scene", IeltsMode.MOCK_TEST, null);
		when(practices.findPractice("scene")).thenReturn(Optional.of(practice));
		AbstractSceneSession matching = mock(AbstractSceneSession.class);
		when(matching.getSceneType()).thenReturn(SceneType.IELTS_SCENE);
		when(matching.getSceneId()).thenReturn("scene");
		when(sessions.requireOwnedSession(userId.toString(), "session")).thenReturn(matching);
		IeltsDialogueStateResponse dialogue = mock(IeltsDialogueStateResponse.class);
		IeltsPart2StateResponse part2 = mock(IeltsPart2StateResponse.class);
		when(questions.advance("scene", "session", 2, false)).thenReturn(dialogue);
		when(partTwo.advance("scene", "session", IeltsPart2Event.LONG_TURN_TIME_LIMIT)).thenReturn(part2);

		service.startSessionState("scene", "session", IeltsPart.PART_1);
		service.startSessionState("scene", "session", IeltsPart.PART_2);
		assertEquals(dialogue, service.advanceDialogueState("scene", "session", 2, false));
		assertEquals(part2, service.advancePart2State("scene", "session", IeltsPart2Event.LONG_TURN_TIME_LIMIT));
		verify(questions).start("scene", "session", IeltsPart.PART_1, practice.content().part1());
		verify(partTwo).start("scene", "session");
	}

	@Test
	void clearSessionStateRemovesBothMachineCaches() {
		service.clearSessionState("session");
		verify(questions).remove("session");
		verify(partTwo).remove("session");
	}

	private void assertCode(String expected, org.junit.jupiter.api.function.Executable action) {
		assertEquals(expected, assertThrows(BusinessException.class, action).code());
	}

	private IeltsPracticeRecord practice(String id, IeltsMode mode, IeltsPart part) {
		return new IeltsPracticeRecord(id, userId, mode, part, "topic",
				new IeltsContent(List.of(), List.of(), List.of()));
	}
}
