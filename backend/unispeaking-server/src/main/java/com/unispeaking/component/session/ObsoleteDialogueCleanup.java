package com.unispeaking.component.session;

import com.unispeaking.infrastructure.persistence.repository.evaluation.TurnEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ObsoleteDialogueCleanup {

	private static final Logger LOGGER =
			LoggerFactory.getLogger(ObsoleteDialogueCleanup.class);

	private final SessionMessageRepository messageRepository;
	private final TurnEvaluationRepository turnEvaluationRepository;

	public ObsoleteDialogueCleanup(
			SessionMessageRepository messageRepository,
			TurnEvaluationRepository turnEvaluationRepository) {
		this.messageRepository = messageRepository;
		this.turnEvaluationRepository = turnEvaluationRepository;
	}

	@Async("scenePersistenceExecutor")
	public void retainLatestDialogue(
			String sceneId,
			String latestSessionId) {
		try {
			int deletedMessages = messageRepository.deleteObsoleteForScene(
					sceneId,
					latestSessionId);
			int deletedEvaluations =
					turnEvaluationRepository.deleteObsoleteForScene(
							sceneId,
							latestSessionId);
			LOGGER.info(
					"obsolete dialogue details removed sceneId={} "
							+ "latestSessionId={} messages={} turnEvaluations={}",
					sceneId,
					latestSessionId,
					deletedMessages,
					deletedEvaluations);
		}
		catch (RuntimeException exception) {
			LOGGER.warn(
					"obsolete dialogue cleanup failed sceneId={} "
							+ "latestSessionId={}",
					sceneId,
					latestSessionId,
					exception);
		}
	}
}
