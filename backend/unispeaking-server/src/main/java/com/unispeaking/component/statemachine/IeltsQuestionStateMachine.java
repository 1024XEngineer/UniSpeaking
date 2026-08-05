package com.unispeaking.component.statemachine;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.dto.session.IeltsDialogueStateResponse;
import com.unispeaking.domain.vo.scene.IeltsContentQuestion;
import com.unispeaking.domain.vo.scene.IeltsPart;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 按生成阶段已经筛选好的题目推进 IELTS 对话。
 *
 * <p>此状态机只负责确定性顺序，不让模型自行追问、选题或判断是否还有题目。</p>
 */
@Component
public class IeltsQuestionStateMachine {

	private final Map<String, State> states = new ConcurrentHashMap<>();

	public IeltsDialogueStateResponse start(
			String sceneId,
			String sessionId,
			IeltsPart part,
			List<IeltsContentQuestion> questions) {
		if (questions == null || questions.isEmpty()) {
			throw new BusinessException(
					"IELTS_QUESTIONS_EMPTY",
					"当前 IELTS Part 没有可用题目");
		}
		State state = new State(
				sceneId,
				sessionId,
				part,
				questions.stream()
						.map(IeltsContentQuestion::question)
						.toList());
		states.put(sessionId, state);
		return state.response();
	}

	public IeltsDialogueStateResponse advance(
			String sceneId,
			String sessionId,
			int turnNo) {
		if (turnNo < 1) {
			throw new BusinessException(
					"INVALID_IELTS_TURN",
					"IELTS 会话轮次必须大于 0");
		}
		State state = requireState(sceneId, sessionId);
		state.advance(turnNo);
		IeltsDialogueStateResponse response = state.response();
		if (response.completed()) {
			states.remove(sessionId, state);
		}
		return response;
	}

	public IeltsDialogueStateResponse get(
			String sceneId,
			String sessionId) {
		return requireState(sceneId, sessionId).response();
	}

	public void remove(String sessionId) {
		states.remove(sessionId);
	}

	private State requireState(String sceneId, String sessionId) {
		State state = states.get(sessionId);
		if (state == null || !state.sceneId.equals(sceneId)) {
			throw new BusinessException(
					"IELTS_DIALOGUE_STATE_NOT_FOUND",
					"IELTS 题目状态不存在");
		}
		return state;
	}

	private static final class State {

		private final String sceneId;
		private final String sessionId;
		private final IeltsPart part;
		private final List<String> questions;
		private final Set<Integer> processedTurns = new LinkedHashSet<>();
		private boolean openingCompleted;
		private int answeredQuestions;
		private boolean completed;

		private State(
				String sceneId,
				String sessionId,
				IeltsPart part,
				List<String> questions) {
			this.sceneId = sceneId;
			this.sessionId = sessionId;
			this.part = part;
			this.questions = List.copyOf(questions);
			this.openingCompleted = part != IeltsPart.PART_1;
		}

		private synchronized void advance(int turnNo) {
			if (completed || !processedTurns.add(turnNo)) return;
			if (part == IeltsPart.PART_1 && !openingCompleted) {
				openingCompleted = true;
				return;
			}
			answeredQuestions++;
			completed = answeredQuestions >= questions.size();
		}

		private synchronized IeltsDialogueStateResponse response() {
			return new IeltsDialogueStateResponse(
					sceneId,
					sessionId,
					part,
					openingCompleted,
					answeredQuestions,
					questions.size(),
					completed,
					controlInstruction());
		}

		private String controlInstruction() {
			if (completed) {
				return part == IeltsPart.PART_3
						? exact("Thank you. That is the end of the speaking test.", true)
						: exact("Thank you. That is the end of Part " + partNumber() + ".", true);
			}
			int nextQuestionIndex = Math.min(answeredQuestions, questions.size() - 1);
			return exact(questions.get(nextQuestionIndex), false);
		}

		private String exact(String utterance, boolean closing) {
			String phase = closing ? "FINISHED" : "PREPARED_QUESTIONS";
			return "# IELTS " + part.name().replace('_', ' ')
					+ " — Runtime State: " + phase + "\n\n"
					+ "The question sequence is controlled by the application. "
					+ "Never greet, evaluate, encourage, explain, add a transition, "
					+ "ask a follow-up, or repeat an earlier question. "
					+ "Keep the examiner turn as short as possible. "
					+ "Your entire spoken response must be exactly this sentence, "
					+ "with no words before or after it:\n\n\""
					+ escape(utterance) + "\"";
		}

		private int partNumber() {
			return switch (part) {
				case PART_1 -> 1;
				case PART_2 -> 2;
				case PART_3 -> 3;
			};
		}

		private String escape(String value) {
			return value.replace("\\", "\\\\").replace("\"", "\\\"");
		}
	}
}
