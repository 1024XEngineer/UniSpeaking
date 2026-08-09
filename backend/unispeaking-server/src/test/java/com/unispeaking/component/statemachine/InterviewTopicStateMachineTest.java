package com.unispeaking.component.statemachine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.InterviewErrorCode;
import com.unispeaking.domain.vo.scene.InterviewDifficulty;
import com.unispeaking.domain.vo.scene.InterviewTopicEvent;
import com.unispeaking.domain.vo.scene.InterviewTopicState;
import java.util.List;
import org.junit.jupiter.api.Test;

class InterviewTopicStateMachineTest {

	private final InterviewTopicStateMachine stateMachine =
			new InterviewTopicStateMachine();

	@Test
	void startInitializesStateAndCurrentReturnsIt() {
		InterviewTopicState state = stateMachine.start(
				"session-1",
				topics("自我介绍", "项目经历"),
				InterviewDifficulty.STANDARD);

		assertNull(state.currentTopic());
		assertEquals(0, state.completedTopicCount());
		assertEquals(0, state.unknownStreak());
		assertEquals(0, state.followUpCount());
		assertFalse(state.mandatoryTopicsCompleted());
		assertFalse(state.shouldEnd());
		assertEquals(state, stateMachine.current("session-1"));
	}

	@Test
	void advanceIdentifiesTopicAndTracksCompletionAndFollowUps() {
		stateMachine.start(
				"session-1",
				topics("自我介绍", "项目经历"),
				InterviewDifficulty.STANDARD);

		InterviewTopicState first = stateMachine.advance(
				"session-1",
				1,
				new InterviewTopicEvent("自我介绍", false));
		assertEquals("自我介绍", first.currentTopic());
		assertEquals(0, first.completedTopicCount());
		assertFalse(first.shouldEnd());

		InterviewTopicState second = stateMachine.advance(
				"session-1",
				2,
				new InterviewTopicEvent("自我介绍", true));
		assertEquals("自我介绍", second.currentTopic());
		assertEquals(1, second.completedTopicCount());
		assertEquals(1, second.followUpCount());
		assertFalse(second.shouldEnd());
	}

	@Test
	void threeUnknownsEndInterview() {
		stateMachine.start(
				"session-1",
				topics("自我介绍", "项目经历"),
				InterviewDifficulty.STANDARD);

		stateMachine.advance("session-1", 1, InterviewTopicEvent.unknown());
		stateMachine.advance("session-1", 2, InterviewTopicEvent.unknown());
		InterviewTopicState state = stateMachine.advance(
				"session-1",
				3,
				InterviewTopicEvent.unknown());

		assertEquals(3, state.unknownStreak());
		assertTrue(state.shouldEnd());
	}

	@Test
	void unknownStreakResetsOnRealTopic() {
		stateMachine.start(
				"session-1",
				topics("自我介绍", "项目经历"),
				InterviewDifficulty.STANDARD);

		stateMachine.advance("session-1", 1, InterviewTopicEvent.unknown());
		stateMachine.advance("session-1", 2, InterviewTopicEvent.unknown());
		InterviewTopicState state = stateMachine.advance(
				"session-1",
				3,
				new InterviewTopicEvent("自我介绍", false));

		assertEquals(0, state.unknownStreak());
		assertFalse(state.shouldEnd());
	}

	@Test
	void offListTopicIsTreatedAsUnknownWithoutGrowingTopics() {
		stateMachine.start(
				"session-1",
				topics("自我介绍"),
				InterviewDifficulty.STANDARD);

		InterviewTopicState state = stateMachine.advance(
				"session-1",
				1,
				new InterviewTopicEvent("编造的新主题", true));

		assertEquals(1, state.unknownStreak());
		assertNull(state.currentTopic());
		assertFalse(state.shouldEnd());
	}

	@Test
	void fifthTopicCompletedForcesEndEvenWhenMandatoryNotSatisfied() {
		List<String> topics = topics(
				"自我介绍", "技术栈", "职业规划", "团队协作", "薪资期望");
		stateMachine.start("session-1", topics, InterviewDifficulty.STANDARD);

		for (int turnNo = 1; turnNo <= 4; turnNo++) {
			InterviewTopicState state = stateMachine.advance(
					"session-1",
					turnNo,
					new InterviewTopicEvent(topics.get(turnNo - 1), true));
			assertFalse(state.shouldEnd());
			assertFalse(state.mandatoryTopicsCompleted());
		}
		InterviewTopicState fifth = stateMachine.advance(
				"session-1",
				5,
				new InterviewTopicEvent("薪资期望", true));

		assertTrue(fifth.shouldEnd());
		assertEquals(5, fifth.completedTopicCount());
	}

	@Test
	void fourTopicsCompletedWithMandatoryTopicsEnds() {
		List<String> topics = topics("自我介绍", "项目经历", "技术栈", "职业规划");
		stateMachine.start("session-1", topics, InterviewDifficulty.STANDARD);

		for (int turnNo = 1; turnNo <= 3; turnNo++) {
			InterviewTopicState state = stateMachine.advance(
					"session-1",
					turnNo,
					new InterviewTopicEvent(topics.get(turnNo - 1), true));
			assertFalse(state.shouldEnd());
		}
		InterviewTopicState fourth = stateMachine.advance(
				"session-1",
				4,
				new InterviewTopicEvent("职业规划", true));

		assertTrue(fourth.mandatoryTopicsCompleted());
		assertTrue(fourth.shouldEnd());
		assertEquals(4, fourth.completedTopicCount());
	}

	@Test
	void outOfOrderTurnThrowsOutOfOrder() {
		stateMachine.start(
				"session-1",
				topics("自我介绍", "项目经历"),
				InterviewDifficulty.STANDARD);
		stateMachine.advance(
				"session-1",
				1,
				new InterviewTopicEvent("自我介绍", true));

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> stateMachine.advance(
						"session-1",
						3,
						new InterviewTopicEvent("项目经历", true)));

		assertEquals(
				InterviewErrorCode.INTERVIEW_TURN_OUT_OF_ORDER,
				exception.code());
	}

	@Test
	void duplicateTurnShortCircuitsWithoutAdvancingTwice() {
		stateMachine.start(
				"session-1",
				topics("自我介绍", "项目经历"),
				InterviewDifficulty.STANDARD);
		stateMachine.advance(
				"session-1",
				1,
				new InterviewTopicEvent("自我介绍", true));

		InterviewTopicState duplicate = stateMachine.advance(
				"session-1",
				1,
				new InterviewTopicEvent("自我介绍", true));

		assertEquals(1, duplicate.completedTopicCount());
		assertEquals("自我介绍", duplicate.currentTopic());
	}

	@Test
	void difficultyCapsFollowUpCountPerTopic() {
		stateMachine.start(
				"session-1",
				topics("自我介绍"),
				InterviewDifficulty.HARD);
		for (int turnNo = 1; turnNo <= 4; turnNo++) {
			stateMachine.advance(
					"session-1",
					turnNo,
					new InterviewTopicEvent("自我介绍", false));
		}
		InterviewTopicState hardState = stateMachine.current("session-1");
		assertEquals(2, hardState.followUpCount());

		stateMachine.start(
				"session-2",
				topics("自我介绍"),
				InterviewDifficulty.EASY);
		stateMachine.advance(
				"session-2",
				1,
				new InterviewTopicEvent("自我介绍", false));
		InterviewTopicState easyState = stateMachine.advance(
				"session-2",
				2,
				new InterviewTopicEvent("自我介绍", false));
		assertEquals(1, easyState.followUpCount());
	}

	@Test
	void afterShouldEndFurtherAdvancesAreNoOp() {
		stateMachine.start(
				"session-1",
				topics("自我介绍", "项目经历"),
				InterviewDifficulty.STANDARD);
		stateMachine.advance("session-1", 1, InterviewTopicEvent.unknown());
		stateMachine.advance("session-1", 2, InterviewTopicEvent.unknown());
		stateMachine.advance("session-1", 3, InterviewTopicEvent.unknown());

		InterviewTopicState afterEnd = stateMachine.advance(
				"session-1",
				4,
				new InterviewTopicEvent("自我介绍", true));

		assertTrue(afterEnd.shouldEnd());
		assertEquals(0, afterEnd.completedTopicCount());
	}

	@Test
	void clearRemovesSessionState() {
		stateMachine.start(
				"session-1",
				topics("自我介绍", "项目经历"),
				InterviewDifficulty.STANDARD);

		stateMachine.clear("session-1");

		assertNull(stateMachine.current("session-1"));
	}

	private List<String> topics(String... values) {
		return List.of(values);
	}
}
