package com.unispeaking.component.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.po.session.CustomSceneSession;
import com.unispeaking.domain.po.session.FreeChatSceneSession;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ActiveSessionRegistryTest {

	@Test
	void registersAtomicallyWithoutOverwritingAnExistingSession() {
		ActiveSessionRegistry registry = new ActiveSessionRegistry();
		CustomSceneSession original = new CustomSceneSession("session_1", "user_1");
		CustomSceneSession duplicate = new CustomSceneSession("session_1", "user_2");

		assertTrue(registry.registerIfAbsent(original));
		assertFalse(registry.registerIfAbsent(duplicate));
		assertSame(original, registry.findById("session_1").orElseThrow());
	}

	@Test
	void compensationRemovesOnlyTheSameSessionInstance() {
		ActiveSessionRegistry registry = new ActiveSessionRegistry();
		CustomSceneSession registered = new CustomSceneSession("session_1", "user_1");
		CustomSceneSession other = new CustomSceneSession("session_1", "user_1");
		registry.registerIfAbsent(registered);

		assertFalse(registry.remove("session_1", other));
		assertSame(registered, registry.findById("session_1").orElseThrow());
		assertTrue(registry.remove("session_1", registered));
		assertTrue(registry.findById("session_1").isEmpty());
	}

	@Test
	void returnsTypedSessionsWithoutUnsafeCasts() {
		ActiveSessionRegistry registry = new ActiveSessionRegistry();
		CustomSceneSession session = new CustomSceneSession("session_1", "user_1");
		registry.registerIfAbsent(session);

		assertSame(session, registry.findById("session_1", CustomSceneSession.class).orElseThrow());
		assertTrue(registry.findById("session_1", FreeChatSceneSession.class).isEmpty());
	}

	@Test
	void snapshotIsStableAndUnmodifiableDuringConcurrentChanges() throws Exception {
		ActiveSessionRegistry registry = new ActiveSessionRegistry();
		CustomSceneSession original = new CustomSceneSession("session_1", "user_1");
		registry.registerIfAbsent(original);
		List<AbstractSceneSession> snapshot = registry.snapshot();
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			executor.submit(() -> {
				await(start);
				registry.remove("session_1", original);
			});
			executor.submit(() -> {
				await(start);
				registry.registerIfAbsent(new CustomSceneSession("session_2", "user_2"));
			});
			start.countDown();
			executor.shutdown();
			assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
		} finally {
			executor.shutdownNow();
		}

		assertEquals(List.of(original), snapshot);
		assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
		assertEquals(1, registry.snapshot().size());
	}

	@Test
	void takesSnapshotsWhileRegistryIsMutating() throws Exception {
		ActiveSessionRegistry registry = new ActiveSessionRegistry();
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<?> writer = executor.submit(() -> {
				await(start);
				for (int index = 0; index < 1000; index++) {
					String sessionId = "session_" + index;
					CustomSceneSession session = new CustomSceneSession(sessionId, "user_1");
					registry.registerIfAbsent(session);
					if (index % 2 == 0) {
						registry.remove(sessionId, session);
					}
				}
			});
			start.countDown();
			for (int index = 0; index < 100; index++) {
				assertTrue(registry.snapshot().stream().noneMatch(Objects::isNull));
			}
			writer.get(5, TimeUnit.SECONDS);
		} finally {
			executor.shutdownNow();
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("timed out waiting for concurrent registry test");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("registry test interrupted", exception);
		}
	}
}
