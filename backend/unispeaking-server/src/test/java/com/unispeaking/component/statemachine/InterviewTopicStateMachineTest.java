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
	void validatesStartTurnAndMissingStateAndDefaultsDifficulty() {
		assertThrows(BusinessException.class,
				() -> stateMachine.start(null, topics("one"), null));
		assertThrows(BusinessException.class,
				() -> stateMachine.start(" ", topics("one"), null));
		assertThrows(BusinessException.class,
				() -> stateMachine.start("session", null, null));
		assertThrows(BusinessException.class,
				() -> stateMachine.start("session", List.of(), null));
		stateMachine.start("session", topics("one"), null);
		assertThrows(BusinessException.class,
				() -> stateMachine.advance("session", 0, null));
		assertThrows(BusinessException.class,
				() -> stateMachine.advance("missing", 1, null));
		assertNull(stateMachine.current("missing"));
	}

	@Test
	void nullEventCountsUnknownAndRepeatedCompletionIsIdempotent() {
		stateMachine.start("session", topics("Tell me about yourself", "project"), null);
		assertEquals(1, stateMachine.advance("session", 1, null).unknownStreak());
		stateMachine.advance("session", 2,
				new InterviewTopicEvent("  TELL ME ABOUT YOURSELF  ", true));
		InterviewTopicState repeated = stateMachine.advance("session", 3,
				new InterviewTopicEvent("tell me about yourself", true));
		assertEquals(1, repeated.completedTopicCount());
		assertTrue(repeated.controlInstruction().contains("Move on to the NEXT topic"));
	}

	@Test
	void recognizesEveryMandatoryTopicPhrase() {
		for (String topic : List.of(
				"self-intro", "self intro", "introduce yourself", "about yourself",
				"tell me about yourself", "自我介绍", "experience", "project", "经历", "项目", "经验")) {
			stateMachine.start("session-" + topic, topics(topic), InterviewDifficulty.EASY);
			InterviewTopicState state = stateMachine.advance(
					"session-" + topic, 1, new InterviewTopicEvent(topic, true));
			assertEquals(1, state.completedTopicCount());
		}
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
	void shouldEndReturnsClosingInstruction() {
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

		assertTrue(state.shouldEnd());
		assertTrue(state.controlInstruction().contains("The interview is complete"));
		assertTrue(state.controlInstruction().contains("thank the candidate"));
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

	@Test
	void singleAnswerOnFifthTopicDoesNotEndInterview() {
		List<String> topics = topics(
				"自我介绍", "项目经历", "技术栈", "团队协作", "高并发问题解决");
		stateMachine.start("session-1", topics, InterviewDifficulty.STANDARD);

		stateMachine.advance(
				"session-1",
				1,
				new InterviewTopicEvent("自我介绍", true));
		InterviewTopicState state = stateMachine.advance(
				"session-1",
				2,
				new InterviewTopicEvent("高并发问题解决", true));

		assertFalse(state.shouldEnd());
		assertEquals(2, state.coveredTopicCount());
	}

	@Test
	void fifthTopicForcesEndOnlyAfterMostTopicsCovered() {
		List<String> topics = topics(
				"自我介绍", "项目经历", "技术栈", "团队协作", "高并发问题解决");
		stateMachine.start("session-1", topics, InterviewDifficulty.STANDARD);

		for (int turnNo = 1; turnNo <= 4; turnNo++) {
			InterviewTopicState state = stateMachine.advance(
					"session-1",
					turnNo,
					new InterviewTopicEvent(topics.get(turnNo - 1), true));
			assertFalse(state.shouldEnd());
		}
		InterviewTopicState fifth = stateMachine.advance(
				"session-1",
				5,
				new InterviewTopicEvent("高并发问题解决", true));

		assertTrue(fifth.shouldEnd());
		assertEquals(5, fifth.completedTopicCount());
	}

	@Test
	void topicSwitchDoesNotImplicitlyCompletePreviousTopic() {
		stateMachine.start(
				"session-1",
				topics("自我介绍", "项目经历"),
				InterviewDifficulty.STANDARD);

		stateMachine.advance(
				"session-1",
				1,
				new InterviewTopicEvent("自我介绍", false));
		InterviewTopicState state = stateMachine.advance(
				"session-1",
				2,
				new InterviewTopicEvent("项目经历", false));

		assertEquals(0, state.completedTopicCount());
		assertEquals(2, state.coveredTopicCount());
		assertEquals("项目经历", state.currentTopic());
		assertFalse(state.shouldEnd());
	}

	@Test
	void blankTranscriptIsNoOpNotUnknown() {
		stateMachine.start(
				"session-1",
				topics("自我介绍", "项目经历"),
				InterviewDifficulty.STANDARD);

		stateMachine.advance("session-1", 1, InterviewTopicEvent.ignored());
		stateMachine.advance("session-1", 2, InterviewTopicEvent.ignored());
		InterviewTopicState state = stateMachine.advance(
				"session-1",
				3,
				InterviewTopicEvent.ignored());

		assertEquals(0, state.unknownStreak());
		assertFalse(state.shouldEnd());
		assertNull(state.currentTopic());
	}

	@Test
	void naturalEndFiresAfterAllTopicsCoveredAndLastTopicFollowedUp() {
		List<String> topics = topics(
				"自我介绍", "项目经历", "技术栈", "团队协作", "高并发问题解决");
		stateMachine.start("session-1", topics, InterviewDifficulty.STANDARD);

		InterviewTopicState last = null;
		int turnNo = 1;
		for (String topic : topics) {
			stateMachine.advance(
					"session-1",
					turnNo++,
					new InterviewTopicEvent(topic, false));
			last = stateMachine.advance(
					"session-1",
					turnNo++,
					new InterviewTopicEvent(topic, false));
		}

		assertEquals(10, turnNo - 1);
		assertTrue(last.shouldEnd());
		assertEquals(5, last.coveredTopicCount());
		assertTrue(last.controlInstruction().contains("The interview is complete"));
		assertTrue(last.controlInstruction().contains("thank the candidate"));
	}

	@Test
	void singleAnswerPerTopicDoesNotEnd() {
		List<String> topics = topics(
				"自我介绍", "项目经历", "技术栈", "团队协作", "高并发问题解决");
		stateMachine.start("session-1", topics, InterviewDifficulty.STANDARD);

		InterviewTopicState last = null;
		for (int turnNo = 1; turnNo <= topics.size(); turnNo++) {
			last = stateMachine.advance(
					"session-1",
					turnNo,
					new InterviewTopicEvent(topics.get(turnNo - 1), false));
		}

		assertFalse(last.shouldEnd());
		assertEquals(5, last.coveredTopicCount());
		assertTrue(last.controlInstruction().contains("Current interview topic"));
		assertFalse(last.controlInstruction().contains("Move on to the NEXT topic"));
	}

	@Test
	void maxTurnBackstopForcesEnd() {
		List<String> topics = topics(
				"自我介绍", "项目经历", "技术栈", "团队协作", "高并发问题解决");
		stateMachine.start("session-1", topics, InterviewDifficulty.STANDARD);

		InterviewTopicState before = null;
		for (int turnNo = 1; turnNo <= 17; turnNo++) {
			before = stateMachine.advance(
					"session-1",
					turnNo,
					new InterviewTopicEvent("自我介绍", false));
		}
		assertFalse(before.shouldEnd());

		InterviewTopicState last = stateMachine.advance(
				"session-1",
				18,
				new InterviewTopicEvent("自我介绍", false));

		assertTrue(last.shouldEnd());
	}

	@Test
	void controlInstructionTracksSatisfiedTopic() {
		List<String> topics = topics(
				"自我介绍", "项目经历", "技术栈", "团队协作", "高并发问题解决");
		stateMachine.start("session-1", topics, InterviewDifficulty.STANDARD);

		InterviewTopicState first = stateMachine.advance(
				"session-1",
				1,
				new InterviewTopicEvent("自我介绍", false));
		assertTrue(first.controlInstruction().contains("Current interview topic"));
		assertFalse(first.controlInstruction().contains("Move on to the NEXT topic"));

		InterviewTopicState satisfied = stateMachine.advance(
				"session-1",
				2,
				new InterviewTopicEvent("自我介绍", false));
		assertTrue(satisfied.controlInstruction().contains("Move on to the NEXT topic"));
	}

	@Test
	void closingInstructionAfterAllCoveredButMandatoryNotSatisfied() {
		// 4 主题中仅"自我介绍"为必选：全部覆盖且最后主题满足后规则③不触发，进入收尾指令而非结束
		List<String> topics = topics("自我介绍", "技术栈", "职业规划", "薪资期望");
		stateMachine.start("session-1", topics, InterviewDifficulty.STANDARD);

		InterviewTopicState last = null;
		int turnNo = 1;
		for (String topic : topics) {
			stateMachine.advance(
					"session-1",
					turnNo++,
					new InterviewTopicEvent(topic, false));
			last = stateMachine.advance(
					"session-1",
					turnNo++,
					new InterviewTopicEvent(topic, false));
		}

		assertFalse(last.shouldEnd());
		assertTrue(last.controlInstruction().contains("The interview is complete"));
		assertTrue(last.controlInstruction().contains("thank the candidate"));
	}

	private List<String> topics(String... values) {
		return List.of(values);
	}
}
