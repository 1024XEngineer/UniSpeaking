package com.unispeaking.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.domain.dto.scene.SceneGenerationRequest;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.dto.scene.StartSceneSessionResponse;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.vo.scene.SceneType;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ServiceContractDtoTest {

	@Test
	void sceneDtosExposeOnlyTheDefinedFields() {
		assertFields(
				SceneGenerationRequest.class,
				"userId",
				"userPreference",
				"sceneType",
				"sceneInput");
		assertFields(
				SceneGenerationResponse.class,
				"sceneId",
				"wordList",
				"phraseList",
				"sentenceList",
				"scenePrompt");
		assertFields(
				SceneFlowResponse.class,
				"sceneId",
				"stage",
				"completed");
	}

	@Test
	void sessionDtosExposeOnlyTheDefinedFields() {
		assertFields(StartSessionResponse.class, "sessionId", "startTime");
		assertFields(Message.class, "owner", "content", "audio");
	}

	@Test
	void combinedStartResponseUsesOneSessionIdAndOnePrompt() {
		String[] fields = fields(StartSceneSessionResponse.class);
		assertTrue(Arrays.asList(fields).contains("sessionId"));
		assertTrue(Arrays.asList(fields).contains("systemPrompt"));
		assertFalse(Arrays.asList(fields).contains("localSessionId"));
		assertFalse(Arrays.asList(fields).contains("flowId"));
		assertFalse(Arrays.asList(fields).contains("scenePrompt"));
	}

	@Test
	void sceneTypeIsEncodedInTheSceneIdPrefix() {
		assertSceneType("freechat_a1", SceneType.FREE_CHAT);
		assertSceneType("custom_b2", SceneType.CUSTOM_SCENE);
		assertSceneType("interview_c3", SceneType.INTERVIEW_SCENE);
		assertSceneType("ielts_d4", SceneType.IELTS_SCENE);
		assertTrue(SceneType.fromSceneId("scene_legacy").isEmpty());
	}

	private void assertSceneType(String sceneId, SceneType expected) {
		assertTrue(SceneType.fromSceneId(sceneId).isPresent());
		assertTrue(SceneType.fromSceneId(sceneId).get() == expected);
	}

	private void assertFields(Class<?> type, String... expected) {
		assertArrayEquals(expected, fields(type));
	}

	private String[] fields(Class<?> type) {
		return Arrays.stream(type.getRecordComponents())
				.map(RecordComponent::getName)
				.toArray(String[]::new);
	}
}
