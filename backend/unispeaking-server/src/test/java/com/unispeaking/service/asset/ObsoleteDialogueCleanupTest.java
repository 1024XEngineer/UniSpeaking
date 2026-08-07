package com.unispeaking.service.asset;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.infrastructure.persistence.repository.evaluation.TurnEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.component.session.ObsoleteDialogueCleanup;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ObsoleteDialogueCleanupTest {

	@Test
	void retainsLatestSessionAndDeletesOnlyOlderDetails() {
		SessionMessageRepository messages =
				Mockito.mock(SessionMessageRepository.class);
		TurnEvaluationRepository turns =
				Mockito.mock(TurnEvaluationRepository.class);
		when(messages.deleteObsoleteForScene("custom_1", "scene_new"))
				.thenReturn(8);
		when(turns.deleteObsoleteForScene("custom_1", "scene_new"))
				.thenReturn(4);

		new ObsoleteDialogueCleanup(messages, turns)
				.retainLatestDialogue("custom_1", "scene_new");

		verify(messages).deleteObsoleteForScene(
				"custom_1",
				"scene_new");
		verify(turns).deleteObsoleteForScene(
				"custom_1",
				"scene_new");
	}
}
