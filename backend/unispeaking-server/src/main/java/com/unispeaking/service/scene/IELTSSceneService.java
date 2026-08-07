package com.unispeaking.service.scene;

import com.unispeaking.domain.dto.scene.IeltsGenerationRequest;
import com.unispeaking.domain.dto.scene.IeltsGenerationResponse;
import com.unispeaking.domain.dto.scene.IeltsSettingsResponse;
import com.unispeaking.domain.dto.scene.IeltsTopicSearchResponse;
import com.unispeaking.domain.dto.scene.IeltsTrainingResponse;
import com.unispeaking.domain.dto.scene.UpdateIeltsSettingsRequest;
import com.unispeaking.domain.dto.session.StartIeltsDialogueRequest;
import com.unispeaking.domain.dto.session.StartIeltsSessionResponse;
import com.unispeaking.domain.dto.session.IeltsDialogueStateResponse;
import com.unispeaking.domain.dto.session.IeltsPart2StateResponse;
import com.unispeaking.domain.vo.scene.IeltsPart2Event;
import com.unispeaking.domain.vo.scene.IeltsPart;

public interface IELTSSceneService extends SceneService<
		IeltsGenerationRequest,
		IeltsGenerationResponse> {

	IeltsTopicSearchResponse searchTopics(
			IeltsPart part,
			String category,
			String keyword,
			int page,
			int pageSize);

	IeltsTrainingResponse prepareTraining(
			IeltsPart part,
			String topicId);

	IeltsSettingsResponse getSettings();

	IeltsSettingsResponse updateSettings(UpdateIeltsSettingsRequest request);

	StartIeltsSessionResponse startSession(
			String ieltsId,
			StartIeltsDialogueRequest request);

	IeltsDialogueStateResponse advanceSessionState(
			String ieltsId,
			String sessionId,
			int turnNo);

	default IeltsDialogueStateResponse advanceSessionState(
			String ieltsId,
			String sessionId,
			int turnNo,
			boolean timedOut) {
		return advanceSessionState(ieltsId, sessionId, turnNo);
	}

	IeltsDialogueStateResponse getSessionState(
			String ieltsId,
			String sessionId);

	IeltsPart2StateResponse advancePart2State(
			String ieltsId,
			String sessionId,
			IeltsPart2Event event);

	IeltsPart2StateResponse getPart2State(
			String ieltsId,
			String sessionId);
}
