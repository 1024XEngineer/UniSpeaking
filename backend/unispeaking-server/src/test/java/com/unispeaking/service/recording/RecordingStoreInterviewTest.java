package com.unispeaking.service.recording;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.component.recording.RecordingStore;
import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.infrastructure.config.RecordingProperties;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.service.auth.AuthService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordingStoreInterviewTest {

	@TempDir
	Path tempDirectory;

	@Test
	void storesTurnAndAiAudioAndReadsBackWithoutAuth() {
		RecordingStore store = store();
		byte[] turnAudio = new byte[] {1, 2, 3, 4};
		byte[] aiAudio = new byte[] {5, 6, 7, 8};

		String turnKey = store.storeTurn("interview_session_1", 2, turnAudio);
		String aiKey = store.storeAiAudio("interview_session_1", aiAudio);

		assertEquals("turn-2.wav", turnKey);
		assertTrue(Pattern.matches("ai-[A-Za-z0-9_-]+\\.wav", aiKey));
		assertArrayEquals(
				turnAudio,
				store.readAudio("interview_session_1", "turn-2.wav"));
		assertArrayEquals(
				aiAudio,
				store.readAudio("interview_session_1", aiKey));
		assertNull(store.readAudio("interview_session_1", "missing.wav"));
	}

	@Test
	void loadOwnedRequiresMatchingSceneIdAndOwner() throws Exception {
		UUID ownerId = UUID.randomUUID();
		PracticeSessionRepository repository = mock(PracticeSessionRepository.class);
		AuthService authService = mock(AuthService.class);
		when(authService.requireUserId(null)).thenReturn(ownerId.toString());
		when(repository.findBySessionId("interview_session_1"))
				.thenReturn(Optional.of(new PracticeSessionRecord(
						"interview_session_1",
						ownerId,
						"interview_1",
						SceneType.INTERVIEW_SCENE,
						SessionStatus.COMPLETED,
						Instant.now(),
						Instant.now())));
		RecordingStore store = store(repository, authService);
		store.storeTurn("interview_session_1", 1, new byte[] {1, 2, 3, 4});

		assertArrayEquals(
				new byte[] {1, 2, 3, 4},
				store.loadOwned("interview_1", "interview_session_1", "turn-1.wav")
						.getInputStream()
						.readAllBytes());
		assertTrue(throwsNotFound(() -> store.loadOwned(
				"interview_other", "interview_session_1", "turn-1.wav")));
		assertTrue(throwsNotFound(() -> store.loadOwned(
				"interview_1", "interview_session_1", "../escape.wav")));
	}

	@Test
	void storesSessionAudioAndDeletesSessionDirectory() throws Exception {
		RecordingStore store = store();
		byte[] sessionWav = new byte[] {9, 8, 7, 6};

		store.storeSessionAudio("interview_session_1", sessionWav);

		assertTrue(Files.exists(
				tempDirectory.resolve("interview_session_1/session.wav")));
		store.deleteSessionAudio("interview_session_1");
		assertFalse(Files.exists(
				tempDirectory.resolve("interview_session_1")));
	}

	@Test
	void cleanupExpiredRemovesOldFilesButKeepsFreshOnes() throws Exception {
		RecordingStore store = store();
		store.storeTurn("interview_session_1", 1, new byte[] {1, 2});
		Path stale = tempDirectory.resolve("interview_session_1/turn-1.wav");
		Files.setLastModifiedTime(
				stale,
				java.nio.file.attribute.FileTime.from(
						Instant.now().minus(Duration.ofDays(30))));

		store.cleanupExpired(Duration.ofDays(7));

		assertFalse(Files.exists(stale));
	}

	@Test
	void validatesConstructorAndStorageInputLimits() {
	RecordingProperties blankDirectory = new RecordingProperties();
		blankDirectory.setDirectory(" ");
		assertThrows(IllegalArgumentException.class, () -> new RecordingStore(
				blankDirectory, mock(PracticeSessionRepository.class),
				mock(AuthService.class), Set.of(SceneType.INTERVIEW_SCENE),
				"/api/interview-scenes/", RecordingStore.INTERVIEW_FILE_NAME,
				"NOT_FOUND", "PERSISTENCE_FAILED"));

		RecordingProperties invalidSize = new RecordingProperties();
		invalidSize.setDirectory(tempDirectory.toString());
		invalidSize.setMaxBytes(0);
		assertThrows(IllegalArgumentException.class, () -> new RecordingStore(
				invalidSize, mock(PracticeSessionRepository.class),
				mock(AuthService.class), Set.of(SceneType.INTERVIEW_SCENE),
				"/api/interview-scenes/", RecordingStore.INTERVIEW_FILE_NAME,
				"NOT_FOUND", "PERSISTENCE_FAILED"));

		RecordingStore limited = storeWithMaxBytes(3);
		assertPersistenceFailure(() -> limited.storeTurn("session-1", 0, new byte[]{1}));
		assertPersistenceFailure(() -> limited.storeTurn("session-1", 1, null));
		assertPersistenceFailure(() -> limited.storeTurn("session-1", 1, new byte[]{1, 2, 3, 4}));
		assertPersistenceFailure(() -> limited.storeAiAudio("session-1", new byte[0]));
		assertPersistenceFailure(() -> limited.storeSessionAudio("session-1", new byte[]{1, 2, 3, 4}));
	}

	@Test
	void rejectsUnsafeIdentifiersAndInvalidFilesWithoutTouchingOwnership() {
		PracticeSessionRepository repository = mock(PracticeSessionRepository.class);
		AuthService authService = mock(AuthService.class);
		RecordingStore store = store(repository, authService);

		assertPersistenceFailure("INTERVIEW_RECORDING_PERSISTENCE_FAILED",
				() -> store.storeTurn("../escape", 1, new byte[]{1}));
		assertPersistenceFailure("INTERVIEW_RECORDING_PERSISTENCE_FAILED",
				() -> store.storeTurn(null, 1, new byte[]{1}));
		assertDoesNotThrow(() -> store.delete("bad/id", 1));
		assertPersistenceFailure("INTERVIEW_RECORDING_PERSISTENCE_FAILED",
				() -> store.readAudio("bad/id", "turn-1.wav"));
		assertFalse(store.hasAudio("bad/id", "turn-1.wav"));
		assertTrue(throwsNotFound(() -> store.loadOwned(
				"scene-1", "session-1", null)));
		assertTrue(throwsNotFound(() -> store.loadOwned(
				"scene-1", "session-1", "../turn-1.wav")));
		verify(authService, never()).requireUserId(null);
		verify(repository, never()).findBySessionId("session-1");
	}

	@Test
	void readsSessionRecordingAndEnforcesOwnerSceneTypeAndSceneBinding() throws Exception {
		UUID ownerId = UUID.randomUUID();
		PracticeSessionRepository repository = mock(PracticeSessionRepository.class);
		AuthService authService = mock(AuthService.class);
		when(authService.requireUserId(null)).thenReturn(ownerId.toString());
		when(repository.findBySessionId("session-1")).thenReturn(Optional.of(
				new PracticeSessionRecord(
					"session-1", ownerId, "scene-1", SceneType.INTERVIEW_SCENE,
					SessionStatus.COMPLETED, Instant.now(), Instant.now())));
		RecordingStore store = store(repository, authService);
		store.storeSessionAudio("session-1", new byte[]{9, 8, 7});

		assertArrayEquals(new byte[]{9, 8, 7}, store.loadSessionRecording(
				"scene-1", "session-1").getInputStream().readAllBytes());

		when(repository.findBySessionId("wrong-owner")).thenReturn(Optional.of(
				new PracticeSessionRecord(
					"wrong-owner", UUID.randomUUID(), "scene-1", SceneType.INTERVIEW_SCENE,
					SessionStatus.COMPLETED, Instant.now(), Instant.now())));
		assertTrue(throwsNotFound(() -> store.loadOwned(
				"scene-1", "wrong-owner", "session.wav")));

		when(repository.findBySessionId("wrong-type")).thenReturn(Optional.of(
				new PracticeSessionRecord(
					"wrong-type", ownerId, "scene-1", SceneType.IELTS_SCENE,
					SessionStatus.COMPLETED, Instant.now(), Instant.now())));
		assertTrue(throwsNotFound(() -> store.loadOwned(
				"scene-1", "wrong-type", "session.wav")));
	}

	@Test
	void reportsMissingAndInvalidInternalAudioAndHandlesCleanupNoOps() throws Exception {
		RecordingStore store = store();
		assertNull(store.readAudio("session-1", "session.wav"));
		assertNull(store.readAudio("session-1", "not-a-recording.txt"));
		assertFalse(store.hasAudio("session-1", null));
		assertFalse(store.hasAudio("session-1", "../session.wav"));

		store.delete("session-1", 0);
		store.delete(null, 1);
		store.deleteSessionAudio(null);
		store.deleteSessionAudio("../escape");
		store.cleanupExpired(null);
		store.cleanupExpired(Duration.ZERO);
		store.cleanupExpired(Duration.ofSeconds(-1));
	}

	@Test
	void cleanupExpiredKeepsFreshFilesAndIgnoresMissingRoot() throws Exception {
		RecordingStore store = store();
		store.storeTurn("session-1", 1, new byte[]{1, 2});
		Path fresh = tempDirectory.resolve("session-1/turn-1.wav");
		store.cleanupExpired(Duration.ofDays(7));
		assertTrue(Files.exists(fresh));

		RecordingProperties missingRoot = new RecordingProperties();
		missingRoot.setDirectory(tempDirectory.resolve("does-not-exist").toString());
		RecordingStore missing = new RecordingStore(
				missingRoot, mock(PracticeSessionRepository.class), mock(AuthService.class),
				Set.of(SceneType.INTERVIEW_SCENE), "/api/interview-scenes/",
				RecordingStore.INTERVIEW_FILE_NAME, "NOT_FOUND", "PERSISTENCE_FAILED");
		assertDoesNotThrow(() -> missing.cleanupExpired(Duration.ofDays(1)));
	}

	private boolean throwsNotFound(Runnable runnable) {
		try {
			runnable.run();
			return false;
		}
		catch (BusinessException exception) {
			return true;
		}
	}

	private RecordingStore store() {
		return store(mock(PracticeSessionRepository.class), mock(AuthService.class));
	}

	private RecordingStore storeWithMaxBytes(long maxBytes) {
		RecordingProperties properties = new RecordingProperties();
		properties.setDirectory(tempDirectory.toString());
		properties.setMaxBytes(maxBytes);
		return new RecordingStore(
				properties, mock(PracticeSessionRepository.class), mock(AuthService.class),
				Set.of(SceneType.INTERVIEW_SCENE), "/api/interview-scenes/",
				RecordingStore.INTERVIEW_FILE_NAME, "NOT_FOUND", "PERSISTENCE_FAILED");
	}

	private void assertPersistenceFailure(Runnable action) {
		assertPersistenceFailure("PERSISTENCE_FAILED", action);
	}

	private void assertPersistenceFailure(String code, Runnable action) {
		BusinessException exception = assertThrows(BusinessException.class, action::run);
		assertEquals(code, exception.code());
	}

	private RecordingStore store(
			PracticeSessionRepository repository,
			AuthService authService) {
		RecordingProperties properties = new RecordingProperties();
		properties.setDirectory(tempDirectory.toString());
		return new RecordingStore(
				properties,
				repository,
				authService,
				Set.of(SceneType.INTERVIEW_SCENE),
				"/api/interview-scenes/",
				RecordingStore.INTERVIEW_FILE_NAME,
				"INTERVIEW_RECORDING_NOT_FOUND",
				"INTERVIEW_RECORDING_PERSISTENCE_FAILED");
	}
}
