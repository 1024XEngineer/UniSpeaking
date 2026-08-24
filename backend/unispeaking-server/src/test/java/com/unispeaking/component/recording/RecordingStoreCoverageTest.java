package com.unispeaking.component.recording;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.infrastructure.config.RecordingProperties;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.service.auth.AuthService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordingStoreCoverageTest {

	@TempDir
	Path tempDirectory;

	@Test
	void coversConstructorDefaultsAndIeltsCompatibilityMethods() throws Exception {
		PracticeSessionRepository repository = mock(PracticeSessionRepository.class);
		AuthService authService = mock(AuthService.class);
		RecordingStore store = newStore(
				repository,
				authService,
				Set.of(SceneType.IELTS_SCENE),
				null,
				null,
			null,
			null);
		UUID ownerId = UUID.randomUUID();
		when(authService.requireUserId(null)).thenReturn(ownerId.toString());
		when(repository.findBySessionId("session-1")).thenReturn(Optional.of(
				new PracticeSessionRecord(
						"session-1",
						ownerId,
						null,
						SceneType.IELTS_SCENE,
						SessionStatus.COMPLETED,
						Instant.now(),
						Instant.now())));

		assertEquals(
				"session-1/turn-1.wav",
				store.store("session-1", 1, new byte[] {1, 2}));
		assertArrayEquals(
				new byte[] {1, 2},
				store.loadOwned("session-1", "turn-1.wav")
						.getInputStream()
						.readAllBytes());
		assertEquals(
				"IELTS_RECORDING_PERSISTENCE_FAILED",
				assertBusinessCode(() -> store.storeTurn("session-1", 0, new byte[] {1})));
		assertEquals(
				"IELTS_RECORDING_NOT_FOUND",
				assertBusinessCode(() -> store.loadOwned("session-1", "session.wav")));
		verify(authService).requireUserId(null);
		verify(repository).findBySessionId("session-1");

		store.delete("session-1", 1);
		assertFalse(Files.exists(tempDirectory.resolve("session-1/turn-1.wav")));
		assertDoesNotThrow(() -> store.delete("session-1", 0));
		assertDoesNotThrow(() -> store.delete("session-1", -1));
	}

	@Test
	void loadsOwnedRecordingOnlyAfterOwnerAndSceneChecks() throws Exception {
		UUID ownerId = UUID.randomUUID();
		PracticeSessionRepository repository = mock(PracticeSessionRepository.class);
		AuthService authService = mock(AuthService.class);
		when(authService.requireUserId(null)).thenReturn(ownerId.toString());
		when(repository.findBySessionId("session-1")).thenReturn(Optional.of(
				new PracticeSessionRecord(
						"session-1",
						ownerId,
						"scene-1",
						SceneType.IELTS_SCENE,
						SessionStatus.COMPLETED,
						Instant.now(),
						Instant.now())));
		RecordingStore store = newStore(
				repository,
				authService,
				Set.of(SceneType.IELTS_SCENE),
				"/api/ielts/recordings/",
				RecordingStore.TURN_FILE_NAME,
				"NOT_FOUND",
				"PERSISTENCE_FAILED");
		store.store("session-1", 2, new byte[] {3, 4});

		assertArrayEquals(
				new byte[] {3, 4},
				store.loadOwned("session-1", "turn-2.wav")
						.getInputStream()
						.readAllBytes());
		assertEquals(
				"NOT_FOUND",
				assertBusinessCode(() -> store.loadOwned("session-1", "missing.wav")));
		when(repository.findBySessionId("session-2")).thenReturn(Optional.empty());
		assertEquals(
				"NOT_FOUND",
				assertBusinessCode(() -> store.loadOwned("session-2", "turn-1.wav")));
	}

	@Test
	void rejectsNullBlankTraversalAndOverlongAudioKeysWithoutOwnershipLookup() {
		PracticeSessionRepository repository = mock(PracticeSessionRepository.class);
		AuthService authService = mock(AuthService.class);
		RecordingStore store = newStore(
				repository,
			authService,
			Set.of(SceneType.IELTS_SCENE),
			"/api/ielts/recordings/",
			RecordingStore.TURN_FILE_NAME,
			"NOT_FOUND",
			"PERSISTENCE_FAILED");

		assertEquals(
				"PERSISTENCE_FAILED",
				assertBusinessCode(() -> store.storeTurn(null, 1, new byte[] {1})));
		assertEquals(
				"PERSISTENCE_FAILED",
				assertBusinessCode(() -> store.storeTurn(" ", 1, new byte[] {1})));
		assertEquals(
				"PERSISTENCE_FAILED",
				assertBusinessCode(() -> store.storeTurn("../escape", 1, new byte[] {1})));
		assertEquals(
				"PERSISTENCE_FAILED",
				assertBusinessCode(() -> store.storeTurn("a".repeat(161), 1, new byte[] {1})));
		assertEquals(
				"PERSISTENCE_FAILED",
				assertBusinessCode(() -> store.readAudio("../escape", "turn-1.wav")));
		assertEquals(
				"NOT_FOUND",
				assertBusinessCode(() -> store.loadOwned("session-1", null)));
		assertEquals(
				"NOT_FOUND",
				assertBusinessCode(() -> store.loadOwned("session-1", "../turn-1.wav")));
		assertFalse(store.hasAudio("session-1", null));
		assertFalse(store.hasAudio("session-1", "../turn-1.wav"));
		assertFalse(store.hasAudio(null, "turn-1.wav"));
		verify(authService, never()).requireUserId(null);
		verify(repository, never()).findBySessionId("session-1");
	}

	@Test
	void validatesAudioKeysAndReadsMissingOrNonRegularFilesAsNull() throws Exception {
		RecordingStore store = newStore();
		assertNull(store.readAudio("session-1", null));
		assertNull(store.readAudio("session-1", ""));
		assertNull(store.readAudio("session-1", "session.wav"));
		assertNull(store.readAudio("session-1", "turn-0.wav"));
		assertNull(store.readAudio("session-1", "turn-1.txt"));

		Path sessionDirectory = Files.createDirectories(
				tempDirectory.resolve("session-1/turn-2.wav"));
		assertTrue(Files.isDirectory(sessionDirectory));
		assertNull(store.readAudio("session-1", "turn-2.wav"));
		assertFalse(store.hasAudio("session-1", "turn-2.wav"));
	}

	@Test
	void storesReplacesAndDeletesTurnAiAndSessionAudio() throws Exception {
		RecordingStore store = newStore();
		assertEquals("turn-4.wav", store.storeTurn("session-1", 4, new byte[] {1}));
		assertEquals("turn-4.wav", store.storeTurn("session-1", 4, new byte[] {2, 3}));
		String aiKey = store.storeAiAudio("session-1", new byte[] {4, 5});
		store.storeSessionAudio("session-1", new byte[] {6, 7});

		assertTrue(Pattern.matches("ai-[A-Za-z0-9_-]+\\.wav", aiKey));
		assertTrue(store.hasAudio("session-1", "turn-4.wav"));
		assertArrayEquals(new byte[] {2, 3}, store.readAudio("session-1", "turn-4.wav"));
		assertArrayEquals(new byte[] {4, 5}, store.readAudio("session-1", aiKey));
		assertArrayEquals(new byte[] {6, 7}, store.readAudio("session-1", "session.wav"));

		store.deleteSessionAudio("session-1");
		assertFalse(Files.exists(tempDirectory.resolve("session-1")));
	}

	@Test
	void returnsPersistenceFailureWhenRootCannotBeUsedAsDirectory() {
		Path rootFile = tempDirectory.resolve("recordings-file");
		assertDoesNotThrow(() -> Files.write(rootFile, new byte[] {9}));
		RecordingProperties properties = properties(rootFile);
		RecordingStore store = new RecordingStore(
				properties,
				mock(PracticeSessionRepository.class),
				mock(AuthService.class),
				Set.of(SceneType.INTERVIEW_SCENE),
				"/api/interview-scenes/",
				RecordingStore.INTERVIEW_FILE_NAME,
				"NOT_FOUND",
				"PERSISTENCE_FAILED");

		assertEquals(
				"PERSISTENCE_FAILED",
				assertBusinessCode(() -> store.storeTurn("session-1", 1, new byte[] {1})));
		assertNull(store.readAudio("session-1", "turn-1.wav"));
		assertFalse(store.hasAudio("session-1", "turn-1.wav"));
	}

	@Test
	void cleanupHandlesRecursiveDirectoriesExpiredFilesFreshFilesAndMissingTargets() throws Exception {
		RecordingStore store = newStore();
		store.storeTurn("session-1", 1, new byte[] {1});
		store.storeTurn("session-1", 2, new byte[] {2});
		Path stale = tempDirectory.resolve("session-1/turn-1.wav");
		Path fresh = tempDirectory.resolve("session-1/turn-2.wav");
		Files.setLastModifiedTime(
				stale,
				FileTime.from(Instant.now().minus(Duration.ofDays(3))));
		Files.setLastModifiedTime(
				fresh,
				FileTime.from(Instant.now()));
		Files.createDirectories(tempDirectory.resolve("session-1/nested"));
		Files.write(tempDirectory.resolve("session-1/nested/ignored.txt"), new byte[] {3});
		Files.write(tempDirectory.resolve("not-a-session-file"), new byte[] {4});

		store.cleanupExpired(Duration.ofDays(1));

		assertFalse(Files.exists(stale));
		assertTrue(Files.exists(fresh));
		assertTrue(Files.exists(tempDirectory.resolve("session-1/nested/ignored.txt")));
		assertDoesNotThrow(() -> store.deleteSessionAudio("missing-session"));
		assertDoesNotThrow(() -> store.deleteSessionAudio(null));
		assertDoesNotThrow(() -> store.deleteSessionAudio("../escape"));
		assertDoesNotThrow(() -> store.cleanupExpired(null));
		assertDoesNotThrow(() -> store.cleanupExpired(Duration.ZERO));
		assertDoesNotThrow(() -> store.cleanupExpired(Duration.ofSeconds(-1)));
	}

	@Test
	void acceptsBoundarySessionIdAndRejectsInvalidIdentifierCharacters() {
		RecordingStore store = newStore();
		String maximumSessionId = "a".repeat(160);
		assertEquals(
				"turn-1.wav",
				store.storeTurn(maximumSessionId, 1, new byte[] {1}));
		assertEquals(
				"PERSISTENCE_FAILED",
				assertBusinessCode(() -> store.storeTurn("session/id", 1, new byte[] {1})));
		assertEquals(
				"PERSISTENCE_FAILED",
				assertBusinessCode(() -> store.storeTurn("session.with.dot", 1, new byte[] {1})));
	}

	private String assertBusinessCode(Runnable action) {
		return assertThrows(BusinessException.class, action::run).code();
	}

	private RecordingStore newStore() {
		return newStore(
				mock(PracticeSessionRepository.class),
				mock(AuthService.class),
				Set.of(SceneType.INTERVIEW_SCENE),
				"/api/interview-scenes/",
				RecordingStore.INTERVIEW_FILE_NAME,
				"NOT_FOUND",
				"PERSISTENCE_FAILED");
	}

	private RecordingStore newStore(
			PracticeSessionRepository repository,
			AuthService authService,
			Set<SceneType> allowedSceneTypes,
			String apiPrefix,
			Pattern safeFileName,
			String notFoundCode,
			String persistenceFailedCode) {
		return new RecordingStore(
				properties(tempDirectory),
				repository,
				authService,
				allowedSceneTypes,
				apiPrefix,
				safeFileName,
				notFoundCode,
				persistenceFailedCode);
	}

	private RecordingProperties properties(Path directory) {
		RecordingProperties properties = new RecordingProperties();
		properties.setDirectory(directory.toString());
		return properties;
	}
}
