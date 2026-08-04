package com.unispeaking.domain.po.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.domain.vo.session.SessionStatus;
import java.time.Instant;
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
}
