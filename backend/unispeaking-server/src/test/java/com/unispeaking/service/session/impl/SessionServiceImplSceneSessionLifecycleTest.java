package com.unispeaking.service.session.impl;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.prompt.FiveLayerPromptBuilder;
import com.unispeaking.component.session.ActiveSessionRegistry;
import com.unispeaking.component.session.SessionLifecycleManager;
import com.unispeaking.component.statemachine.ScenarioDialogueStateMachine;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.po.session.CustomSceneSession;
import com.unispeaking.domain.po.session.FreeChatSceneSession;
import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.IeltsPracticeRepository;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.infrastructure.realtime.RealtimeSdpExchange;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.service.profile.ProfileService;
import com.unispeaking.service.scene.impl.IeltsSceneFlowServiceImpl;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SessionServiceImplSceneSessionLifecycleTest {

	private static final String USER_ID = "f76889ee-7f7c-4dae-bcc2-61b85a63dcec";
	private static final String OTHER_USER_ID = "3d9e2f86-c0c7-4e6c-bf15-c246ba63db7e";
	private static final String SCENE_ID = "custom_scene_1";

	private ActiveSessionRegistry sessions;
	private PracticeSessionRepository practiceSessions;
	private SessionMessageRepository sessionMessages;
	private RealtimeSdpExchange realtimeSdpExchange;
	private AiProviderRegistry providerRegistry;
	private SessionLifecycleManager service;

	@BeforeEach
	void setUp() {
		sessions = new ActiveSessionRegistry();
		practiceSessions = mock(PracticeSessionRepository.class);
		sessionMessages = mock(SessionMessageRepository.class);
		realtimeSdpExchange = mock(RealtimeSdpExchange.class);
		providerRegistry = mock(AiProviderRegistry.class);
		service = new SessionLifecycleManager(
				sessions,
				sessionMessages,
				practiceSessions);
	}

	@Test
	void registersACompleteSceneBindingWithoutEnteringRealtime() {
		CustomSceneSession session = customSession("custom_session_1", USER_ID);
		session.markConnecting();
		ArgumentCaptor<PracticeSessionRecord> recordCaptor =
				ArgumentCaptor.forClass(PracticeSessionRecord.class);

		service.registerSceneSession(session);

		verify(practiceSessions).create(recordCaptor.capture());
		PracticeSessionRecord record = recordCaptor.getValue();
		assertAll(
				() -> assertEquals(session.getId(), record.sessionId()),
				() -> assertEquals(UUID.fromString(USER_ID), record.userId()),
				() -> assertEquals(SCENE_ID, record.sceneId()),
				() -> assertEquals(SceneType.CUSTOM_SCENE, record.sceneType()),
				() -> assertEquals(SessionStatus.CONNECTING, record.status()),
				() -> assertEquals(session.getCreatedAt(), record.startedAt()),
				() -> assertEquals(session.getEndedAt(), record.endedAt()),
				() -> assertSame(session, sessions.findById(session.getId()).orElseThrow()));
		verifyNoInteractions(realtimeSdpExchange, providerRegistry);
	}

	@Test
	void rejectsDuplicateRegistrationWithoutOverwritingTheOriginal() {
		CustomSceneSession original = customSession("custom_session_1", USER_ID);
		CustomSceneSession duplicate = customSession("custom_session_1", USER_ID);
		service.registerSceneSession(original);

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.registerSceneSession(duplicate));

		assertEquals("SESSION_ALREADY_REGISTERED", exception.code());
		assertSame(original, sessions.findById(original.getId()).orElseThrow());
		verify(practiceSessions, times(1)).create(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void rejectsMissingBindingsAndNonUuidUsersBeforeChangingState() {
		CustomSceneSession missingScene = new CustomSceneSession(
				"custom_session_1",
				USER_ID);
		missingScene.setSceneType(SceneType.CUSTOM_SCENE);
		CustomSceneSession invalidUser = customSession(
				"custom_session_2",
				"not-a-uuid");

		BusinessException missingBinding = assertThrows(
				BusinessException.class,
				() -> service.registerSceneSession(missingScene));
		BusinessException invalidUuid = assertThrows(
				BusinessException.class,
				() -> service.registerSceneSession(invalidUser));

		assertEquals("INVALID_SCENE_SESSION_BINDING", missingBinding.code());
		assertEquals("INVALID_SCENE_SESSION_BINDING", invalidUuid.code());
		assertTrue(sessions.findById(missingScene.getId()).isEmpty());
		assertTrue(sessions.findById(invalidUser.getId()).isEmpty());
		verify(practiceSessions, never()).create(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void removesOnlyItsRuntimeRegistrationWhenPersistenceFails() {
		CustomSceneSession session = customSession("custom_session_1", USER_ID);
		BusinessException failure = new BusinessException(
				"PRACTICE_SESSION_PERSISTENCE_FAILED",
				"failed");
		doThrow(failure).when(practiceSessions)
				.create(org.mockito.ArgumentMatchers.any());

		BusinessException thrown = assertThrows(
				BusinessException.class,
				() -> service.registerSceneSession(session));

		assertSame(failure, thrown);
		assertTrue(sessions.findById(session.getId()).isEmpty());
	}

	@Test
	void completesPersistedSessionBeforeChangingRuntimeState() {
		CustomSceneSession session = registeredSession("custom_session_1");
		Instant endedAt = Instant.parse("2026-08-04T04:05:06Z");

		service.terminateSceneSession(
				USER_ID,
				session.getId(),
				SessionStatus.COMPLETED,
				endedAt);

		verify(practiceSessions).complete(
				session.getId(),
				UUID.fromString(USER_ID),
				endedAt);
		assertEquals(SessionStatus.COMPLETED, session.getStatus());
		assertEquals(endedAt, session.getEndedAt());
		assertSame(session, sessions.findById(session.getId()).orElseThrow());
	}

	@Test
	void failsPersistedSessionAtTheRequestedInstantWithoutRemovingIt() {
		CustomSceneSession session = registeredSession("custom_session_1");
		Instant endedAt = Instant.parse("2026-08-04T04:05:06Z");

		service.terminateSceneSession(
				USER_ID,
				session.getId(),
				SessionStatus.FAILED,
				endedAt);

		verify(practiceSessions).fail(
				session.getId(),
				UUID.fromString(USER_ID),
				endedAt);
		assertEquals(SessionStatus.FAILED, session.getStatus());
		assertEquals(endedAt, session.getEndedAt());
		assertSame(session, sessions.findById(session.getId()).orElseThrow());
		verifyNoInteractions(realtimeSdpExchange, providerRegistry);
	}

	@Test
	void rejectsIllegalTerminalStatusAndMissingEndTime() {
		CustomSceneSession session = registeredSession("custom_session_1");
		Instant endedAt = Instant.parse("2026-08-04T04:05:06Z");

		BusinessException invalidStatus = assertThrows(
				BusinessException.class,
				() -> service.terminateSceneSession(
						USER_ID,
						session.getId(),
						SessionStatus.ACTIVE,
						endedAt));
		BusinessException missingTime = assertThrows(
				BusinessException.class,
				() -> service.terminateSceneSession(
						USER_ID,
						session.getId(),
						SessionStatus.COMPLETED,
						null));

		assertEquals("INVALID_SESSION_TERMINAL_STATUS", invalidStatus.code());
		assertEquals("SESSION_END_TIME_REQUIRED", missingTime.code());
		assertEquals(SessionStatus.CREATED, session.getStatus());
		verify(practiceSessions, never()).complete(
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any());
		verify(practiceSessions, never()).fail(
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any());
	}

	@Test
	void rejectsAValidButDifferentOwner() {
		CustomSceneSession session = registeredSession("custom_session_1");

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.terminateSceneSession(
						OTHER_USER_ID,
						session.getId(),
						SessionStatus.COMPLETED,
						Instant.parse("2026-08-04T04:05:06Z")));

		assertEquals("SESSION_ACCESS_DENIED", exception.code());
		assertEquals(SessionStatus.CREATED, session.getStatus());
		verify(practiceSessions, never()).complete(
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any());
	}

	@Test
	void rejectsMissingSessions() {
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.terminateSceneSession(
						USER_ID,
						"custom_missing",
						SessionStatus.COMPLETED,
						Instant.parse("2026-08-04T04:05:06Z")));

		assertEquals("SESSION_NOT_FOUND", exception.code());
		verify(practiceSessions, never()).complete(
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any());
	}

	@Test
	void preservesTheFirstTerminalStateAndRejectsAConflictingTerminalState() {
		CustomSceneSession session = registeredSession("custom_session_1");
		Instant firstEnd = Instant.parse("2026-08-04T04:05:06Z");
		Instant laterEnd = Instant.parse("2026-08-04T04:06:07Z");
		service.terminateSceneSession(
				USER_ID,
				session.getId(),
				SessionStatus.COMPLETED,
				firstEnd);

		service.terminateSceneSession(
				USER_ID,
				session.getId(),
				SessionStatus.COMPLETED,
				laterEnd);
		BusinessException conflict = assertThrows(
				BusinessException.class,
				() -> service.terminateSceneSession(
						USER_ID,
						session.getId(),
						SessionStatus.FAILED,
						laterEnd));

		assertEquals("SESSION_ALREADY_TERMINATED", conflict.code());
		assertEquals(SessionStatus.COMPLETED, session.getStatus());
		assertEquals(firstEnd, session.getEndedAt());
		verify(practiceSessions, times(1)).complete(
				session.getId(),
				UUID.fromString(USER_ID),
				firstEnd);
		verify(practiceSessions, never()).fail(
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any());
	}

	@Test
	void leavesRuntimeStateUnchangedWhenTerminalPersistenceFails() {
		CustomSceneSession session = registeredSession("custom_session_1");
		Instant endedAt = Instant.parse("2026-08-04T04:05:06Z");
		BusinessException failure = new BusinessException(
				"PRACTICE_SESSION_PERSISTENCE_FAILED",
				"failed");
		doThrow(failure).when(practiceSessions).complete(
				session.getId(),
				UUID.fromString(USER_ID),
				endedAt);

		BusinessException thrown = assertThrows(
				BusinessException.class,
				() -> service.terminateSceneSession(
						USER_ID,
						session.getId(),
						SessionStatus.COMPLETED,
						endedAt));

		assertSame(failure, thrown);
		assertEquals(SessionStatus.CREATED, session.getStatus());
		assertNull(session.getEndedAt());
	}

	@Test
	void existingStartSessionStillCreatesFreeChatAndCustomRuntimeTypes() {
		ArgumentCaptor<PracticeSessionRecord> records =
				ArgumentCaptor.forClass(PracticeSessionRecord.class);

		var freeChat = service.startSession(
				USER_ID,
				SceneType.FREE_CHAT,
				"freechat_scene_1",
				"free prompt");
		var custom = service.startSession(
				USER_ID,
				SceneType.CUSTOM_SCENE,
				"custom_scene_1",
				"custom prompt");

		verify(practiceSessions, times(2)).create(records.capture());
		List<PracticeSessionRecord> created = records.getAllValues();
		AbstractSceneSession freeRuntime = sessions.findById(
				freeChat.sessionId()).orElseThrow();
		AbstractSceneSession customRuntime = sessions.findById(
				custom.sessionId()).orElseThrow();
		assertAll(
				() -> assertEquals(SceneType.FREE_CHAT, created.get(0).sceneType()),
				() -> assertEquals(SceneType.CUSTOM_SCENE, created.get(1).sceneType()),
				() -> assertInstanceOf(FreeChatSceneSession.class, freeRuntime),
				() -> assertInstanceOf(CustomSceneSession.class, customRuntime),
				() -> assertEquals(freeRuntime.getCreatedAt().toString(), freeChat.startTime()),
				() -> assertEquals(customRuntime.getCreatedAt().toString(), custom.startTime()));
		verifyNoInteractions(realtimeSdpExchange, providerRegistry);
	}

	@Test
	void customMessagesPersistBeforeEnteringRuntimeHistory() {
		CustomSceneSession session = registeredSession("custom_session_1");
		Message message = new Message(1, "  I led the launch.  ", new byte[] {1});

		service.addMessage(USER_ID, session.getId(), message);

		verify(sessionMessages).append(
				SCENE_ID,
				session.getId(),
				1,
				message);
		assertEquals(1, session.getMessages().size());
		assertEquals("I led the launch.",
				session.getMessages().getFirst().text());
		assertSame(session, sessions.findById(session.getId()).orElseThrow());
	}

	@Test
	void endingCustomPersistsCompletionAndRetainsRuntimeForFinalization() {
		CustomSceneSession session = registeredSession("custom_session_1");

		service.endSession(USER_ID, session.getId(), "client-time-is-ignored");

		ArgumentCaptor<Instant> endedAt = ArgumentCaptor.forClass(Instant.class);
		verify(practiceSessions).complete(
				org.mockito.ArgumentMatchers.eq(session.getId()),
				org.mockito.ArgumentMatchers.eq(UUID.fromString(USER_ID)),
				endedAt.capture());
		assertEquals(SessionStatus.COMPLETED, session.getStatus());
		assertEquals(endedAt.getValue(), session.getEndedAt());
		assertSame(session, sessions.findById(session.getId()).orElseThrow());

		service.endSession(USER_ID, session.getId(), "retry");
		verify(practiceSessions, times(1)).complete(
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any());
	}

	@Test
	void freeChatMessagesRemainEphemeralAndEndingRemovesRuntime() {
		FreeChatSceneSession session = new FreeChatSceneSession(
				"freechat_session_1",
				USER_ID);
		session.setSceneId("freechat_scene_1");
		session.setSceneType(SceneType.FREE_CHAT);
		assertTrue(sessions.registerIfAbsent(session));

		service.addMessage(
				USER_ID,
				session.getId(),
				new Message(1, "ephemeral", null));
		service.endSession(USER_ID, session.getId(), null);

		verifyNoInteractions(sessionMessages);
		assertTrue(sessions.findById(session.getId()).isEmpty());
	}

	private CustomSceneSession customSession(String sessionId, String userId) {
		CustomSceneSession session = new CustomSceneSession(sessionId, userId);
		session.setSceneId(SCENE_ID);
		session.setSceneType(SceneType.CUSTOM_SCENE);
		return session;
	}

	private CustomSceneSession registeredSession(String sessionId) {
		CustomSceneSession session = customSession(sessionId, USER_ID);
		assertTrue(sessions.registerIfAbsent(session));
		return session;
	}
}
