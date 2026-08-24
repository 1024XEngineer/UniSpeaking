package com.unispeaking.component.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.component.policy.UserEntitlementPolicy;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.SessionDetail;
import com.unispeaking.domain.dto.session.StartSessionCommand;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.po.session.CustomSceneSession;
import com.unispeaking.domain.po.session.FreeChatSceneSession;
import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SessionLifecycleManagerCoverageTest {

	private final UUID ownerId = UUID.randomUUID();
	private final ActiveSessionRegistry sessions = new ActiveSessionRegistry();
	private final SessionMessageRepository messages = mock(SessionMessageRepository.class);
	private final PracticeSessionRepository practices = mock(PracticeSessionRepository.class);
	private final UserEntitlementPolicy entitlement = mock(UserEntitlementPolicy.class);
	private final SessionLifecycleManager lifecycle = new SessionLifecycleManager(
			sessions, messages, practices, entitlement);

	@Test
	void startsIeltsPartSessionAndPersistsValidatedBinding() {
		StartSessionResponse response = lifecycle.startSession(new StartSessionCommand(
				ownerId.toString(),
				"ielts-topic-1",
				SceneType.IELTS_SCENE,
				"PART2",
				"Speak clearly"));

		assertEquals("ielts_", response.sessionId().substring(0, 6));
		assertEquals(IeltsPart.PART_2,
				sessions.findById(response.sessionId()).orElseThrow().getIeltsPart());
		ArgumentCaptor<PracticeSessionRecord> record =
				ArgumentCaptor.forClass(PracticeSessionRecord.class);
		verify(practices).create(record.capture());
		assertEquals(ownerId, record.getValue().userId());
		assertEquals("ielts-topic-1", record.getValue().sceneId());
		verify(entitlement).assertAllowed(ownerId.toString());
	}

	@Test
	void rejectsMissingCommandAndInvalidUserBeforePersistence() {
		assertEquals("SESSION_COMMAND_REQUIRED", assertThrows(
				BusinessException.class,
				() -> lifecycle.startSession((StartSessionCommand) null)).code());
		assertEquals("INVALID_USER_ID", assertThrows(
				BusinessException.class,
				() -> lifecycle.startSession(
						"not-a-uuid", SceneType.FREE_CHAT, "scene", "prompt")).code());
		verify(practices, never()).create(any());
	}

	@Test
	void compensatesActiveRegistryWhenDurableSessionCreationFails() {
		CustomSceneSession session = customSession("session-compensation");
		doThrow(new IllegalStateException("database unavailable"))
				.when(practices).create(any());

		assertThrows(IllegalStateException.class, () -> lifecycle.registerSceneSession(session));

		assertFalse(sessions.findById(session.getId()).isPresent());
	}

	@Test
	void completesAndFailsOwnedSessionsUsingTheMatchingPersistenceOperation() {
		CustomSceneSession completed = customSession("session-completed");
		sessions.save(completed);
		Instant completedAt = Instant.parse("2026-08-20T06:00:00Z");

		lifecycle.terminateSceneSession(
				ownerId.toString(), completed.getId(), SessionStatus.COMPLETED, completedAt);

		verify(practices).complete(completed.getId(), ownerId, completedAt);
		verify(entitlement).recordUsage(ownerId.toString(), completed.getCreatedAt(), completedAt);
		assertEquals(SessionStatus.COMPLETED, completed.getStatus());

		CustomSceneSession failed = customSession("session-failed");
		sessions.save(failed);
		Instant failedAt = Instant.parse("2026-08-20T06:10:00Z");
		lifecycle.terminateSceneSession(
				ownerId.toString(), failed.getId(), SessionStatus.FAILED, failedAt);

		verify(practices).fail(failed.getId(), ownerId, failedAt);
		verify(entitlement, never()).recordUsage(
				eq(ownerId.toString()), eq(failed.getCreatedAt()), eq(failedAt));
		assertEquals(SessionStatus.FAILED, failed.getStatus());
	}

	@Test
	void validatesTerminalStateAndOnlyPersistsMessagesForSceneSessions() {
		CustomSceneSession session = customSession("session-message");
		sessions.save(session);
		assertEquals("INVALID_SESSION_TERMINAL_STATUS", assertThrows(
				BusinessException.class,
				() -> lifecycle.terminateSceneSession(
						ownerId.toString(), session.getId(), SessionStatus.ACTIVE, Instant.now())).code());

		lifecycle.addMessage(ownerId.toString(), session.getId(), new Message(1, "  answer  ", null));
		verify(messages).append("scene-1", session.getId(), 1, new Message(1, "  answer  ", null));
		assertEquals(1, session.getMessages().size());

		assertEquals("INVALID_SESSION_MESSAGE", assertThrows(
				BusinessException.class,
				() -> lifecycle.addMessage(ownerId.toString(), session.getId(),
						new Message(2, "invalid", null))).code());
	}

	@Test
	void derivesIeltsStagesForPersistedSessionsThatAreNoLongerActive() {
		Instant startedAt = Instant.parse("2026-08-20T05:00:00Z");
		PracticeSessionRecord first = record("session-1", startedAt);
		PracticeSessionRecord second = record("session-2", startedAt.plusSeconds(60));
		when(practices.findBySceneId("ielts-topic-1")).thenReturn(List.of(first, second));
		when(messages.findMessages("session-1")).thenReturn(List.of(new Message(0, "question", null)));
		when(messages.findMessages("session-2")).thenReturn(List.of());

		List<SessionDetail> details = lifecycle.getBySceneId("ielts-topic-1");

		assertEquals(List.of("PART1", "PART2"), details.stream().map(SessionDetail::stage).toList());
		assertEquals(1, details.getFirst().dialogue().size());
	}

	@Test
	void coversStageNormalizationAndDefaultFreeChatSessionCreation() {
		StartSessionResponse part3 = lifecycle.startSession(new StartSessionCommand(
				ownerId.toString(), "ielts-topic-3", SceneType.IELTS_SCENE,
				"PART3", "prompt"));
		StartSessionResponse unknownPart = lifecycle.startSession(new StartSessionCommand(
				ownerId.toString(), "ielts-topic-x", SceneType.IELTS_SCENE,
				"PART4", "prompt"));
		StartSessionResponse freeChat = lifecycle.startSession(
				ownerId.toString(), null, "free-scene", " free prompt ");

		assertEquals(IeltsPart.PART_3,
				sessions.findById(part3.sessionId()).orElseThrow().getIeltsPart());
		assertEquals(null,
				sessions.findById(unknownPart.sessionId()).orElseThrow().getIeltsPart());
		assertEquals(SceneType.FREE_CHAT,
				sessions.findById(freeChat.sessionId()).orElseThrow().getSceneType());
		assertEquals(" free prompt ",
				sessions.findById(freeChat.sessionId()).orElseThrow().getPrompt().systemPrompt());
	}

	@Test
	void rejectsAuthenticationPromptAndMessageBoundaryInputs() {
		assertEquals("AUTHENTICATION_REQUIRED", assertThrows(
				BusinessException.class,
				() -> lifecycle.startSession(null, SceneType.FREE_CHAT, "scene", "prompt")).code());
		assertEquals("SESSION_PROMPT_REQUIRED", assertThrows(
				BusinessException.class,
				() -> lifecycle.startSession(ownerId.toString(), SceneType.FREE_CHAT, "scene", " ")).code());

		CustomSceneSession session = customSession("session-boundary");
		sessions.save(session);
		assertEquals("INVALID_SESSION_MESSAGE", assertThrows(
				BusinessException.class,
				() -> lifecycle.addMessage(ownerId.toString(), session.getId(), null)).code());
		assertEquals("INVALID_SESSION_MESSAGE", assertThrows(
				BusinessException.class,
				() -> lifecycle.addMessage(ownerId.toString(), session.getId(),
						new Message(2, "blank owner", null))).code());
		assertEquals("INVALID_SESSION_MESSAGE", assertThrows(
				BusinessException.class,
				() -> lifecycle.addMessage(ownerId.toString(), session.getId(),
						new Message(null, "text", null))).code());
	}

	@Test
	void keepsFreeChatMessagesEphemeralAndRejectsUnboundSceneMessages() {
		FreeChatSceneSession freeChat = new FreeChatSceneSession("free-ephemeral", ownerId.toString());
		freeChat.setSceneType(SceneType.FREE_CHAT);
		freeChat.setSceneId("free-scene");
		sessions.save(freeChat);
		lifecycle.addMessage(ownerId.toString(), freeChat.getId(), new Message(0, "hello", null));
		assertTrue(freeChat.getMessages().isEmpty());
		verifyNoInteractions(messages);

		CustomSceneSession unbound = new CustomSceneSession("scene-unbound", ownerId.toString());
		unbound.setSceneType(SceneType.CUSTOM_SCENE);
		sessions.save(unbound);
		assertEquals("SESSION_SCENE_NOT_BOUND", assertThrows(
				BusinessException.class,
				() -> lifecycle.addMessage(ownerId.toString(), unbound.getId(),
						new Message(1, "answer", null))).code());
	}

	@Test
	void resolvesSessionMetadataAndRejectsMissingOrMismatchedSessions() {
		CustomSceneSession session = customSession("session-metadata");
		sessions.save(session);

		assertEquals(SceneType.INTERVIEW_SCENE,
				lifecycle.requireSceneType(ownerId.toString(), session.getId()));
		assertEquals(ownerId.toString(), lifecycle.requireOwnerId(session.getId()));
		assertEquals("scene-1", lifecycle.requireSceneId(session.getId(), SceneType.INTERVIEW_SCENE));
		assertEquals("SESSION_SCENE_TYPE_MISMATCH", assertThrows(
				BusinessException.class,
				() -> lifecycle.requireSceneId(session.getId(), SceneType.FREE_CHAT)).code());
		assertEquals("SESSION_NOT_FOUND", assertThrows(
				BusinessException.class,
				() -> lifecycle.getSession("missing")).code());
		assertEquals("SESSION_NOT_FOUND", assertThrows(
				BusinessException.class,
				() -> lifecycle.requireOwnerId(" ")).code());
	}

	private CustomSceneSession customSession(String sessionId) {
		CustomSceneSession session = new CustomSceneSession(sessionId, ownerId.toString());
		session.setSceneId("scene-1");
		session.setSceneType(SceneType.INTERVIEW_SCENE);
		session.setPrompt(new com.unispeaking.domain.vo.session.SessionPrompt("prompt"));
		return session;
	}

	private PracticeSessionRecord record(String sessionId, Instant startedAt) {
		return new PracticeSessionRecord(
				sessionId,
				ownerId,
				"ielts-topic-1",
				SceneType.IELTS_SCENE,
				SessionStatus.COMPLETED,
				startedAt,
				startedAt.plusSeconds(30));
	}
}
