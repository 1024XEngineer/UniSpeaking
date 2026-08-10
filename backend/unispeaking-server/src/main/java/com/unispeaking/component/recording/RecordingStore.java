package com.unispeaking.component.recording;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.infrastructure.config.RecordingProperties;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.service.auth.AuthService;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/**
 * 录音存储（由 {@code IeltsRecordingStore} 泛化）：场景类型/API 前缀/文件名校验按用途参数化。
 * 按会话目录落盘 {@code {root}/{sessionId}/{fileName}}；IELTS 行为保持不变
 * （turn-N.wav、{@code /api/ielts/recordings/} 前缀、IELTS_SCENE 归属过滤、原错误码）。
 *
 * <p>Interview 用途：{@code storeTurn}/{@code storeAiAudio}/{@code storeSessionAudio} +
 * turn-aware 读取 + TTL 清扫；文件名校验按用途分离
 * {@code (turn-[1-9][0-9]*|ai-[A-Za-z0-9_-]+|session)\.wav}。</p>
 */
public class RecordingStore {

	private static final Pattern SAFE_SESSION_ID = Pattern.compile(
			"[A-Za-z0-9_-]{1,160}");

	/** IELTS 文件名校验（行为不变）：仅逐轮用户录音。 */
	public static final Pattern TURN_FILE_NAME = Pattern.compile(
			"turn-[1-9][0-9]*\\.wav");

	/** Interview 文件名校验按用途分离：逐轮用户段 / AI 段 / 总音频。 */
	public static final Pattern INTERVIEW_FILE_NAME = Pattern.compile(
			"(turn-[1-9][0-9]*|ai-[A-Za-z0-9_-]+|session)\\.wav");

	private final Path root;
	private final long maxBytes;
	private final PracticeSessionRepository practiceSessionRepository;
	private final AuthService authService;
	private final Set<SceneType> allowedSceneTypes;
	private final String apiPrefix;
	private final Pattern safeFileName;
	private final String notFoundCode;
	private final String persistenceFailedCode;

	public RecordingStore(
			RecordingProperties properties,
			PracticeSessionRepository practiceSessionRepository,
			AuthService authService,
			Set<SceneType> allowedSceneTypes,
			String apiPrefix,
			Pattern safeFileName,
			String notFoundCode,
			String persistenceFailedCode) {
		if (properties.getDirectory() == null
				|| properties.getDirectory().isBlank()) {
			throw new IllegalArgumentException(
					"recording directory must not be blank");
		}
		this.root = Path.of(properties.getDirectory())
				.toAbsolutePath()
				.normalize();
		if (properties.getMaxBytes() < 1) {
			throw new IllegalArgumentException(
					"recording maxBytes must be positive");
		}
		this.maxBytes = properties.getMaxBytes();
		this.practiceSessionRepository = practiceSessionRepository;
		this.authService = authService;
		this.allowedSceneTypes = allowedSceneTypes == null
				? Set.of()
				: Set.copyOf(allowedSceneTypes);
		this.apiPrefix = apiPrefix == null ? "" : apiPrefix;
		this.safeFileName = safeFileName == null ? TURN_FILE_NAME : safeFileName;
		this.notFoundCode = notFoundCode == null
				? "IELTS_RECORDING_NOT_FOUND"
				: notFoundCode;
		this.persistenceFailedCode = persistenceFailedCode == null
				? "IELTS_RECORDING_PERSISTENCE_FAILED"
				: persistenceFailedCode;
	}

	// ---- IELTS 兼容面（行为不变） ----

	public String store(String sessionId, int turnNo, byte[] audio) {
		String fileName = storeTurn(sessionId, turnNo, audio);
		return apiPrefix + sessionId + "/" + fileName;
	}

	public void delete(String sessionId, int turnNo) {
		if (!isSafeSessionId(sessionId) || turnNo < 1) return;
		deletePath(resolve(sessionId, "turn-" + turnNo + ".wav"));
	}

	public Resource loadOwned(String sessionId, String fileName) {
		validateSessionId(sessionId);
		if (fileName == null || !safeFileName.matcher(fileName).matches()) {
			throw recordingNotFound();
		}
		UUID userId = UUID.fromString(authService.requireUserId(null));
		requireOwnedSession(sessionId, userId, null);
		return loadFile(sessionId, fileName);
	}

	// ---- Interview 用途 ----

	public String storeTurn(String sessionId, int turnNo, byte[] audio) {
		validateSessionId(sessionId);
		if (turnNo < 1 || audio == null || audio.length == 0
				|| audio.length > maxBytes) {
			throw recordingFailure();
		}
		String fileName = "turn-" + turnNo + ".wav";
		write(sessionId, fileName, audio);
		return fileName;
	}

	public String storeAiAudio(String sessionId, byte[] audio) {
		validateSessionId(sessionId);
		if (audio == null || audio.length == 0 || audio.length > maxBytes) {
			throw recordingFailure();
		}
		String fileName = "ai-" + UUID.randomUUID() + ".wav";
		write(sessionId, fileName, audio);
		return fileName;
	}

	public void storeSessionAudio(String sessionId, byte[] sessionWav) {
		validateSessionId(sessionId);
		if (sessionWav == null || sessionWav.length == 0
				|| sessionWav.length > maxBytes) {
			throw recordingFailure();
		}
		write(sessionId, "session.wav", sessionWav);
	}

	/** Interview 回放/调试读：归属 + sceneId 绑定 + 文件名校验。 */
	public Resource loadOwned(
			String sceneId,
			String sessionId,
			String fileName) {
		validateSessionId(sessionId);
		if (fileName == null || !safeFileName.matcher(fileName).matches()) {
			throw recordingNotFound();
		}
		UUID userId = UUID.fromString(authService.requireUserId(null));
		requireOwnedSession(sessionId, userId, sceneId);
		return loadFile(sessionId, fileName);
	}

	/** 总音频回放：返回 {@code session.wav}。 */
	public Resource loadSessionRecording(String sceneId, String sessionId) {
		return loadOwned(sceneId, sessionId, "session.wav");
	}

	/** 报告任务内部读：无安全上下文，仅路径校验；文件缺失返回 {@code null}。 */
	public byte[] readAudio(String sessionId, String fileName) {
		validateSessionId(sessionId);
		if (fileName == null || !safeFileName.matcher(fileName).matches()) {
			return null;
		}
		Path path = resolve(sessionId, fileName);
		if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
			return null;
		}
		try {
			return Files.readAllBytes(path);
		}
		catch (IOException exception) {
			return null;
		}
	}

	public boolean hasAudio(String sessionId, String fileName) {
		try {
			return isSafeSessionId(sessionId)
					&& Files.isRegularFile(resolve(sessionId, fileName));
		}
		catch (RuntimeException exception) {
			return false;
		}
	}

	/** 删除某会话全部录音目录（DELETE scene 时按会话清音频）。 */
	public void deleteSessionAudio(String sessionId) {
		if (!isSafeSessionId(sessionId)) return;
		try {
			Path dir = root.resolve(sessionId).normalize();
			if (!dir.startsWith(root)) return;
			deleteDirectoryRecursively(dir);
		}
		catch (IOException exception) {
			// Best-effort scene deletion must not hide the soft-delete result.
		}
	}

	/** TTL 清扫：删除会话目录中 lastModified 早于 cutoff 的文件。 */
	public void cleanupExpired(Duration ttl) {
		if (ttl == null || ttl.isNegative() || ttl.isZero()) {
			return;
		}
		Instant cutoff = Instant.now().minus(ttl);
		try (Stream<Path> dirs = Files.list(root)) {
			dirs.filter(Files::isDirectory)
					.forEach(dir -> {
						try (Stream<Path> files = Files.list(dir)) {
							for (Path file : files.toList()) {
								deleteIfOlderThan(file, cutoff);
							}
						}
						catch (IOException ignored) {
							// Continue sweeping other session directories.
						}
					});
		}
		catch (IOException ignored) {
			// Root missing/empty is not an error for TTL cleanup.
		}
	}

	private void requireOwnedSession(
			String sessionId,
			UUID userId,
			String sceneId) {
		PracticeSessionRecord session = practiceSessionRepository
				.findBySessionId(sessionId)
				.filter(record -> record.userId().equals(userId))
				.filter(record -> allowedSceneTypes.contains(record.sceneType()))
				.filter(record -> sceneId == null || sceneId.equals(record.sceneId()))
				.orElseThrow(this::recordingNotFound);
	}

	private Resource loadFile(String sessionId, String fileName) {
		Path path = resolve(sessionId, fileName);
		if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
			throw recordingNotFound();
		}
		return new FileSystemResource(path);
	}

	private void write(String sessionId, String fileName, byte[] audio) {
		Path target = resolve(sessionId, fileName);
		Path temporary = null;
		try {
			Files.createDirectories(target.getParent());
			temporary = Files.createTempFile(
					target.getParent(),
					fileName + "-",
					".tmp");
			Files.write(temporary, audio);
			try {
				Files.move(
						temporary,
						target,
						StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			}
			catch (AtomicMoveNotSupportedException exception) {
				Files.move(
						temporary,
						target,
						StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException exception) {
			if (temporary != null) deletePath(temporary);
			throw recordingFailure();
		}
	}

	private Path resolve(String sessionId, String fileName) {
		Path path = root.resolve(sessionId).resolve(fileName).normalize();
		if (!path.startsWith(root)) throw recordingFailure();
		return path;
	}

	private void validateSessionId(String sessionId) {
		if (!isSafeSessionId(sessionId)) throw recordingFailure();
	}

	private boolean isSafeSessionId(String sessionId) {
		return sessionId != null && SAFE_SESSION_ID.matcher(sessionId).matches();
	}

	private void deleteIfOlderThan(Path file, Instant cutoff) {
		try {
			if (Files.isRegularFile(file)
					&& Files.getLastModifiedTime(file)
							.toInstant()
							.isBefore(cutoff)) {
				Files.deleteIfExists(file);
			}
		}
		catch (IOException ignored) {
			// Best-effort TTL cleanup must not propagate.
		}
	}

	private void deleteDirectoryRecursively(Path dir) throws IOException {
		if (!Files.exists(dir)) return;
		try (Stream<Path> files = Files.list(dir)) {
			for (Path file : files.toList()) {
				if (Files.isDirectory(file)) {
					deleteDirectoryRecursively(file);
				}
				else {
					Files.deleteIfExists(file);
				}
			}
		}
		Files.deleteIfExists(dir);
	}

	private void deletePath(Path path) {
		try {
			Files.deleteIfExists(path);
		}
		catch (IOException ignored) {
			// Best-effort rollback must not hide the original persistence error.
		}
	}

	private BusinessException recordingFailure() {
		return new BusinessException(
				persistenceFailedCode,
				"录音保存失败");
	}

	private BusinessException recordingNotFound() {
		return new BusinessException(
				notFoundCode,
				"录音不存在");
	}
}
