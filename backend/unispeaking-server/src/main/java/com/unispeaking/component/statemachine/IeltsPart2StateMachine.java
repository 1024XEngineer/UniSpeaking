package com.unispeaking.component.statemachine;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.dto.session.IeltsPart2StateResponse;
import com.unispeaking.domain.vo.scene.IeltsPart2Event;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Controls the application-owned preparation and long-turn phases of IELTS Part 2. */
@Component
public class IeltsPart2StateMachine {

	private final Map<String, State> states = new ConcurrentHashMap<>();

	public IeltsPart2StateResponse start(String sceneId, String sessionId) {
		State state = new State(sceneId, sessionId);
		states.put(sessionId, state);
		return state.response();
	}

	public IeltsPart2StateResponse advance(
			String sceneId,
			String sessionId,
			IeltsPart2Event event) {
		if (event == null) {
			throw new BusinessException("IELTS_PART2_EVENT_REQUIRED", "Part 2 状态事件不能为空");
		}
		return requireState(sceneId, sessionId).advance(event);
	}

	public IeltsPart2StateResponse get(String sceneId, String sessionId) {
		return requireState(sceneId, sessionId).response();
	}

	public void remove(String sessionId) {
		states.remove(sessionId);
	}

	private State requireState(String sceneId, String sessionId) {
		State state = states.get(sessionId);
		if (state == null || !state.sceneId.equals(sceneId)) {
			throw new BusinessException(
					"IELTS_PART2_STATE_NOT_FOUND",
					"IELTS Part 2 状态不存在");
		}
		return state;
	}

	private enum Phase {
		PREPARATION,
		LONG_TURN,
		FINISHED
	}

	private static final class State {

		private final String sceneId;
		private final String sessionId;
		private Phase phase = Phase.PREPARATION;

		private State(String sceneId, String sessionId) {
			this.sceneId = sceneId;
			this.sessionId = sessionId;
		}

		private synchronized IeltsPart2StateResponse advance(IeltsPart2Event event) {
			if (phase == Phase.FINISHED) return response();
			switch (event) {
				case PREPARATION_COMPLETE -> {
					if (phase != Phase.PREPARATION) return response();
					phase = Phase.LONG_TURN;
				}
				case ANSWER_COMPLETE, LONG_TURN_TIME_LIMIT -> {
					if (phase != Phase.LONG_TURN) {
						throw new BusinessException(
								"IELTS_PART2_TRANSITION_INVALID",
								"Part 2 尚未进入正式作答阶段");
					}
					phase = Phase.FINISHED;
				}
			}
			return response();
		}

		private synchronized IeltsPart2StateResponse response() {
			return new IeltsPart2StateResponse(
					sceneId,
					sessionId,
					phase.name(),
					phase == Phase.FINISHED,
					controlInstruction());
		}

		private String controlInstruction() {
			return switch (phase) {
				case PREPARATION -> exact(
						"Please think about how you would answer based on the cue card. "
								+ "You may make notes if you wish. You have one minute to prepare.");
				case LONG_TURN -> exact("Please begin speaking now.");
				case FINISHED -> exact("Thank you. That is the end of Part 2.");
			};
		}

		private String exact(String utterance) {
			return "# IELTS PART 2 — Runtime State: " + phase.name() + "\n\n"
					+ "The Part 2 phase is controlled by the application. Never repeat the cue card, "
					+ "add a follow-up, evaluate the answer, or add any transition. Your entire spoken "
					+ "response must be exactly this text, with no words before or after it:\n\n\""
					+ utterance + "\"";
		}
	}
}
