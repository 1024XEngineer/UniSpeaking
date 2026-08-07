package com.unispeaking.domain.po.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.session.SessionPrompt;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AbstractSceneSessionTest {

	@Test
	void failsAtTheSpecifiedInstant() {
		CustomSceneSession session = new CustomSceneSession("session_1", "user_1");
		Instant endedAt = Instant.parse("2026-08-04T03:04:05Z");

		session.fail(endedAt);

		assertEquals(SessionStatus.FAILED, session.getStatus());
		assertEquals(endedAt, session.getEndedAt());
	}

	@Test
	void existingFailMethodStillUsesTheCurrentTime() {
		CustomSceneSession session = new CustomSceneSession("session_1", "user_1");
		Instant before = Instant.now();

		session.fail();

		Instant after = Instant.now();
		assertEquals(SessionStatus.FAILED, session.getStatus());
		assertFalse(session.getEndedAt().isBefore(before));
		assertTrue(session.getEndedAt().compareTo(after) <= 0);
	}

	@Test
	void supportsTheCompleteRuntimeLifecycleAndDefensiveMessages() {
		CustomSceneSession session = new CustomSceneSession("session_1", "user_1");
		Instant credentialExpiry = Instant.parse("2026-08-04T10:00:00Z");
		Instant completedAt = Instant.parse("2026-08-04T09:00:00Z");
		ConversationMessage message = new ConversationMessage(
				"message_1",
				"session_1",
				com.unispeaking.domain.vo.session.SpeakerType.USER,
				"Answer",
				null,
				Instant.parse("2026-08-04T08:00:00Z"));

		session.setSceneId("custom_1");
		session.setSceneType(SceneType.CUSTOM_SCENE);
		session.setPrompt(new SessionPrompt("prompt"));
		session.setProviderType(ProviderType.QWEN);
		session.setModel("model");
		session.setVoiceId("voice");
		session.setCredentialExpiresAt(credentialExpiry);
		session.markConnecting();
		session.bindProviderSession("provider_session");
		session.waitForClient();
		session.activate();
		session.pause();
		session.resume();
		session.recordInterrupt();
		session.addMessage(null);
		session.addMessage(message);
		session.complete(completedAt);

		assertEquals("session_1", session.getId());
		assertEquals("user_1", session.getUserId());
		assertTrue(session.getCreatedAt().isBefore(Instant.now().plusSeconds(1)));
		assertEquals("custom_1", session.getSceneId());
		assertEquals(SceneType.CUSTOM_SCENE, session.getSceneType());
		assertEquals("provider_session", session.getProviderSessionId());
		assertEquals(new SessionPrompt("prompt"), session.getPrompt());
		assertEquals(ProviderType.QWEN, session.getProviderType());
		assertEquals("model", session.getModel());
		assertEquals("voice", session.getVoiceId());
		assertEquals(credentialExpiry, session.getCredentialExpiresAt());
		assertEquals(List.of(message), session.getMessages());
		assertThrows(UnsupportedOperationException.class,
				() -> session.getMessages().add(message));
		assertEquals(SessionStatus.COMPLETED, session.getStatus());
		assertEquals(completedAt, session.getEndedAt());
	}

	@Test
	void nullStopTimesFallbackToNowAndFailureDetailsAreRetained() {
		CustomSceneSession completed = new CustomSceneSession("s1", "u1");
		Instant beforeComplete = Instant.now();
		completed.complete(null);
		assertFalse(completed.getEndedAt().isBefore(beforeComplete));

		CustomSceneSession failed = new CustomSceneSession("s2", "u2");
		failed.fail("PROVIDER_FAILED", "safe message");
		assertEquals(SessionStatus.FAILED, failed.getStatus());
		assertEquals("PROVIDER_FAILED", failed.getErrorCode());
		assertEquals("safe message", failed.getErrorMessage());

		CustomSceneSession nullFailed = new CustomSceneSession("s3", "u3");
		Instant beforeFail = Instant.now();
		nullFailed.fail((Instant) null);
		assertFalse(nullFailed.getEndedAt().isBefore(beforeFail));
	}
}
