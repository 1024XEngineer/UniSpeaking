package com.unispeaking.component.statemachine;

import com.unispeaking.common.statemachine.ScenarioSuccessFactorParser;
import com.unispeaking.domain.dto.session.ScenarioDialogueStateResponse;
import com.unispeaking.domain.dto.session.ScenarioOutcomeState;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.po.scene.ScenarioDialogueEvent;
import com.unispeaking.domain.po.scene.ScenarioDialogueState;
import com.unispeaking.domain.vo.scene.ScenarioDialogueCompletionReason;
import com.unispeaking.domain.vo.scene.ScenarioDialogueStage;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ScenarioDialogueStateMachine {

	private static final Logger LOGGER = LoggerFactory.getLogger(
			ScenarioDialogueStateMachine.class);

	private final ScenarioSuccessFactorParser successFactorParser;
	private final ScenarioDialogueEventExtractor eventExtractor;
	private final SessionMessageRepository messageRepository;
	private final Map<String, ScenarioDialogueState> states =
			new ConcurrentHashMap<>();

	public ScenarioDialogueStateMachine(
			ScenarioSuccessFactorParser successFactorParser,
			ScenarioDialogueEventExtractor eventExtractor,
			SessionMessageRepository messageRepository) {
		this.successFactorParser = successFactorParser;
		this.eventExtractor = eventExtractor;
		this.messageRepository = messageRepository;
	}

	public ScenarioDialogueStateResponse start(
			String sessionId,
			String businessId,
			String successFactorJson,
			String fallbackGoal) {
		ScenarioDialogueState state = new ScenarioDialogueState(
				sessionId,
				businessId,
				successFactorParser.parse(
						successFactorJson,
						fallbackGoal));
		states.put(sessionId, state);
		return toResponse(state);
	}

	public ScenarioDialogueStateResponse advance(
			String sessionId,
			int turnNo,
			String transcript) {
		if (turnNo < 1 || transcript == null || transcript.isBlank()) {
			throw new BusinessException(
					"INVALID_SCENARIO_TURN",
					"场景轮次和用户转写不能为空");
		}
		ScenarioDialogueState state = requireState(sessionId);
		if (state.getProcessedTurns().contains(turnNo)
				|| isTerminal(state.getStage())) {
			return toResponse(state);
		}

		ScenarioDialogueEvent event;
		String warning = null;
		try {
			List<Message> dialogue = messageRepository.findMessages(sessionId);
			event = eventExtractor.extract(
					state,
					transcript.trim(),
					dialogue);
		}
		catch (RuntimeException exception) {
			LOGGER.warn(
					"scenario state extraction failed sessionId={} turnNo={} error={}",
					sessionId,
					turnNo,
					exception.getMessage());
			event = ScenarioDialogueEvent.none();
			warning = "本轮语义目标提取失败，已计入有效轮次";
		}
		state.apply(turnNo, event);
		if (warning != null) {
			state.recordExtractionFailure(warning);
		}
		LOGGER.info(
				"scenario state advanced sceneId={} sessionId={} turnNo={} stage={} "
						+ "effectiveTurns={}/{} satisfiedOutcomes={}/{} completionReason={}",
				state.getSceneId(),
				sessionId,
				turnNo,
				state.getStage(),
				state.getEffectiveUserTurns(),
				state.getSuccessFactor().maximumUserTurns(),
				state.getSatisfiedOutcomes().size(),
				state.getSuccessFactor().requiredOutcomes().size(),
				state.getCompletionReason());
		return toResponse(state);
	}

	public ScenarioDialogueStateResponse getState(String sessionId) {
		return toResponse(requireState(sessionId));
	}

	public Optional<ScenarioDialogueStateResponse> findState(String sessionId) {
		return Optional.ofNullable(states.get(sessionId))
				.map(this::toResponse);
	}

	public ScenarioDialogueStateResponse beginClosing(String sessionId) {
		ScenarioDialogueState state = requireState(sessionId);
		state.beginClosing();
		return toResponse(state);
	}

	public void remove(String sessionId) {
		states.remove(sessionId);
	}

	private ScenarioDialogueState requireState(String sessionId) {
		ScenarioDialogueState state = states.get(sessionId);
		if (state == null) {
			throw new BusinessException(
					"SCENARIO_STATE_NOT_FOUND",
					"场景会话状态不存在");
		}
		return state;
	}

	private ScenarioDialogueStateResponse toResponse(
			ScenarioDialogueState state) {
		Map<String, String> satisfied = state.getSatisfiedOutcomes();
		List<ScenarioOutcomeState> outcomes =
				state.getSuccessFactor().requiredOutcomes().entrySet().stream()
						.map(entry -> new ScenarioOutcomeState(
								entry.getKey(),
								entry.getValue(),
								satisfied.get(entry.getKey()),
								satisfied.containsKey(entry.getKey())))
						.toList();
		boolean completed =
				isTerminal(state.getStage());
		return new ScenarioDialogueStateResponse(
				state.getSceneId(),
				state.getSessionId(),
				state.getStage(),
				state.getEffectiveUserTurns(),
				state.getSuccessFactor().maximumUserTurns(),
				outcomes,
				completed,
				state.getCompletionReason(),
				controlInstruction(state, outcomes),
				state.getLastWarning());
	}

	private String controlInstruction(
			ScenarioDialogueState state,
			List<ScenarioOutcomeState> outcomes) {
		if (state.getStage() == ScenarioDialogueStage.COMPLETED) {
			String closing = state.getSuccessFactor().closingInstruction();
			String reason = state.getCompletionReason()
							== ScenarioDialogueCompletionReason.MAX_TURNS_REACHED
					? "The practice has reached its hard limit of "
							+ state.getSuccessFactor().maximumUserTurns()
							+ " effective learner turns."
					: "The learner has completed and confirmed the scenario goal.";
			return reason
					+ " Give one concise, natural in-role closing response now. "
					+ (closing.isBlank() ? "" : closing + " ")
					+ "Do not ask another question or start a new topic.";
		}
		if (state.getStage() == ScenarioDialogueStage.CLOSING) {
			return "";
		}
		if (state.getStage() == ScenarioDialogueStage.CONFIRMATION) {
			return "All required scenario outcomes are covered. Briefly recap them "
					+ "in role and ask one explicit final confirmation question. "
					+ "Do not introduce another topic.";
		}
		String missing = outcomes.stream()
				.filter(outcome -> !outcome.satisfied())
				.map(ScenarioOutcomeState::description)
				.reduce((left, right) -> left + "; " + right)
				.orElse("the scenario goal");
		return """
				Follow this role-play as a goal-driven conversation. Keep each response
				concise and remain in role. Guide the learner naturally toward these
				still-missing outcomes: %s. Never mention tracking, slots, or a state
				machine. Do not close before a final recap and explicit confirmation.
				The conversation has a hard limit of %d effective learner turns.
				""".formatted(
				missing,
				state.getSuccessFactor().maximumUserTurns()).trim();
	}

	private boolean isTerminal(ScenarioDialogueStage stage) {
		return stage == ScenarioDialogueStage.COMPLETED
				|| stage == ScenarioDialogueStage.CLOSING;
	}
}
