package com.unispeaking.domain.po.scene;

import com.unispeaking.domain.vo.scene.ScenarioDialogueCompletionReason;
import com.unispeaking.domain.vo.scene.ScenarioDialogueEventType;
import com.unispeaking.domain.vo.scene.ScenarioDialogueStage;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ScenarioDialogueState {

	private static final double GOAL_COMPLETION_CONFIDENCE = 0.75;

	private final String sessionId;
	private final String sceneId;
	private final ScenarioSuccessFactor successFactor;
	private final Map<String, String> satisfiedOutcomes = new LinkedHashMap<>();
	private final Set<Integer> processedTurns = new LinkedHashSet<>();
	private ScenarioDialogueStage stage = ScenarioDialogueStage.GREETING;
	private ScenarioDialogueCompletionReason completionReason;
	private int effectiveUserTurns;
	private int revision;
	private String lastWarning;

	public ScenarioDialogueState(
			String sessionId,
			String sceneId,
			ScenarioSuccessFactor successFactor) {
		this.sessionId = sessionId;
		this.sceneId = sceneId;
		this.successFactor = successFactor;
	}

	public synchronized void apply(
			int turnNo,
			ScenarioDialogueEvent event) {
		if (isTerminal()
				|| turnNo < 1
				|| !processedTurns.add(turnNo)) {
			return;
		}
		effectiveUserTurns++;
		lastWarning = null;
		ScenarioDialogueEvent current = event == null
				? ScenarioDialogueEvent.none()
				: event;
		for (Map.Entry<String, String> entry
				: current.outcomeValues().entrySet()) {
			if (successFactor.requiredOutcomes().containsKey(entry.getKey())
					&& entry.getValue() != null
					&& !entry.getValue().isBlank()) {
				String previous = satisfiedOutcomes.put(
						entry.getKey(),
						entry.getValue().trim());
				if (previous != null && !previous.equals(entry.getValue().trim())) {
					revision++;
				}
			}
		}

		boolean allOutcomesSatisfied = hasAllRequiredOutcomes();
		if (current.type() == ScenarioDialogueEventType.GOAL_COMPLETED
				&& current.confidence() >= GOAL_COMPLETION_CONFIDENCE
				&& effectiveUserTurns >= successFactor.minimumUserTurns()) {
			complete(ScenarioDialogueCompletionReason.GOAL_ACHIEVED);
			return;
		}
		if (current.type() == ScenarioDialogueEventType.USER_CONFIRMED
				&& allOutcomesSatisfied
				&& effectiveUserTurns >= successFactor.minimumUserTurns()) {
			complete(ScenarioDialogueCompletionReason.GOAL_ACHIEVED);
			return;
		}
		if (effectiveUserTurns >= successFactor.maximumUserTurns()) {
			complete(ScenarioDialogueCompletionReason.MAX_TURNS_REACHED);
			return;
		}
		stage = allOutcomesSatisfied
				? ScenarioDialogueStage.CONFIRMATION
				: ScenarioDialogueStage.COLLECTING_INFORMATION;
	}

	public synchronized void recordExtractionFailure(String message) {
		lastWarning = message == null || message.isBlank()
				? "场景状态提取失败，本轮仅计入有效轮次"
				: message.trim();
	}

	public synchronized void beginClosing() {
		if (stage == ScenarioDialogueStage.COMPLETED) {
			stage = ScenarioDialogueStage.CLOSING;
		}
	}

	private boolean isTerminal() {
		return stage == ScenarioDialogueStage.COMPLETED
				|| stage == ScenarioDialogueStage.CLOSING;
	}

	private void complete(ScenarioDialogueCompletionReason reason) {
		stage = ScenarioDialogueStage.COMPLETED;
		completionReason = reason;
	}

	private boolean hasAllRequiredOutcomes() {
		return successFactor.requiredOutcomes().keySet().stream()
				.allMatch(satisfiedOutcomes::containsKey);
	}

	public String getSessionId() {
		return sessionId;
	}

	public String getSceneId() {
		return sceneId;
	}

	public ScenarioSuccessFactor getSuccessFactor() {
		return successFactor;
	}

	public synchronized Map<String, String> getSatisfiedOutcomes() {
		return Map.copyOf(satisfiedOutcomes);
	}

	public synchronized Set<Integer> getProcessedTurns() {
		return Set.copyOf(processedTurns);
	}

	public synchronized ScenarioDialogueStage getStage() {
		return stage;
	}

	public synchronized ScenarioDialogueCompletionReason getCompletionReason() {
		return completionReason;
	}

	public synchronized int getEffectiveUserTurns() {
		return effectiveUserTurns;
	}

	public synchronized int getRevision() {
		return revision;
	}

	public synchronized String getLastWarning() {
		return lastWarning;
	}
}
