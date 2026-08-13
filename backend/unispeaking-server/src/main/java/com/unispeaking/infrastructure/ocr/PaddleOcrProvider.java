package com.unispeaking.infrastructure.ocr;

import com.unispeaking.common.exception.ocr.OcrErrorCode;
import com.unispeaking.common.exception.ocr.OcrException;
import com.unispeaking.domain.dto.ocr.OcrImage;
import com.unispeaking.infrastructure.config.OcrProperties;
import com.unispeaking.provider.OcrProvider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

public final class PaddleOcrProvider implements OcrProvider {

	private static final Logger LOGGER = LoggerFactory.getLogger(PaddleOcrProvider.class);

	static final int MAX_IMAGE_COUNT = 5;
	static final int MAX_TOTAL_BYTES = 10 * 1024 * 1024;
	static final long MAX_PIXELS = 25_000_000L;
	static final int MAX_STDOUT_BYTES = 1024 * 1024;
	static final int MAX_STDERR_BYTES = 64 * 1024;
	static final String TEXT_DETECTION_MODEL_NAME = "PP-OCRv5_mobile_det";
	static final String TEXT_RECOGNITION_MODEL_NAME = "PP-OCRv5_mobile_rec";

	private final OcrProperties properties;
	private final ObjectMapper objectMapper;
	private final Object workerLock = new Object();
	private volatile Process workerProcess;
	private volatile BufferedWriter workerWriter;
	private volatile BufferedReader workerReader;
	private volatile ExecutorService workerStderrExecutor;
	private volatile boolean workerReady;

	public PaddleOcrProvider(OcrProperties properties, ObjectMapper objectMapper) {
		this.properties = Objects.requireNonNull(properties, "OCR properties are required");
		this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper is required");
	}

	/** 启动后端时预加载 PaddleOCR，并让 Python Worker 常驻内存。 */
	@PostConstruct
	void startWorkerOnApplicationStartup() {
		if (!baseAvailable()) {
			LOGGER.info("paddle OCR worker not started because OCR is not configured");
			return;
		}
		try {
			ensureWorkerStarted();
		}
		catch (OcrException exception) {
			// OCR availability remains observable through the existing endpoint. The
			// first OCR request will retry the worker start, so one transient startup
			// failure does not prevent the web application from booting.
			LOGGER.warn("paddle OCR worker failed to start during application startup");
		}
	}

	@PreDestroy
	void stopWorkerOnApplicationShutdown() {
		synchronized (workerLock) {
			stopWorker();
		}
	}

	@Override
	public String recognizeText(List<OcrImage> images) {
		List<ValidatedImage> validatedImages = validateImages(images);
		ensureAvailable();
		Path tempDirectory = null;
		try {
			tempDirectory = createTempDirectory();
			List<Path> imagePaths = writeImages(tempDirectory, validatedImages);
			return recognizeWithWorker(imagePaths, validatedImages.size());
		}
		catch (OcrException exception) {
			throw exception;
		}
		catch (IOException exception) {
			throw new OcrException(OcrErrorCode.PROCESS_FAILED, exception);
		}
		finally {
			deleteTempDirectory(tempDirectory);
		}
	}

	@Override
	public boolean available() {
		return baseAvailable() && workerReady && workerProcess != null
				&& workerProcess.isAlive();
	}

	private boolean baseAvailable() {
		return properties.configured()
				&& Files.isRegularFile(properties.runnerPath())
				&& modelDirectoriesAvailable(properties.modelDirectory());
	}

	private static boolean modelDirectoriesAvailable(Path modelDirectory) {
		Path officialModels = modelDirectory.resolve("official_models");
		return Files.isDirectory(officialModels.resolve(TEXT_DETECTION_MODEL_NAME))
				&& Files.isDirectory(officialModels.resolve(TEXT_RECOGNITION_MODEL_NAME));
	}

	private void ensureAvailable() {
		if (!baseAvailable()) {
			throw new OcrException(OcrErrorCode.UNAVAILABLE);
		}
		try {
			ensureWorkerStarted();
		}
		catch (OcrException exception) {
			throw exception;
		}
	}

	private static List<ValidatedImage> validateImages(List<OcrImage> images) {
		if (images == null || images.isEmpty()) {
			throw new OcrException(OcrErrorCode.INPUT_REQUIRED);
		}
		if (images.size() > MAX_IMAGE_COUNT) {
			throw new OcrException(OcrErrorCode.TOO_MANY_IMAGES);
		}
		long totalBytes = 0;
		List<ValidatedImage> validatedImages = new ArrayList<>(images.size());
		for (OcrImage image : images) {
			if (image == null) {
				throw new OcrException(OcrErrorCode.INPUT_REQUIRED);
			}
			byte[] content = image.content();
			if (content == null || content.length == 0) {
				throw new OcrException(OcrErrorCode.INPUT_REQUIRED);
			}
			totalBytes += content.length;
			if (totalBytes > MAX_TOTAL_BYTES) {
				throw new OcrException(OcrErrorCode.TOTAL_SIZE_EXCEEDED);
			}
			ImageType type = detectImageType(content);
			validatePixelLimit(content, type);
			validatedImages.add(new ValidatedImage(content, type));
		}
		return validatedImages;
	}

	private static ImageType detectImageType(byte[] content) {
		if (startsWith(content, new byte[] {
				(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'
		})) {
			return ImageType.PNG;
		}
		if (content.length >= 2
				&& content[0] == (byte) 0xff
				&& content[1] == (byte) 0xd8) {
			return ImageType.JPEG;
		}
		throw new OcrException(OcrErrorCode.FORMAT_UNSUPPORTED);
	}

	private static void validatePixelLimit(byte[] content, ImageType type) {
		try (ImageInputStream imageInput =
				ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
			if (imageInput == null) {
				throw new OcrException(OcrErrorCode.CONTENT_INVALID);
			}
			var readers = ImageIO.getImageReadersByFormatName(type.formatName());
			ImageReader reader = readers.hasNext() ? readers.next() : null;
			if (reader == null) {
				throw new OcrException(OcrErrorCode.CONTENT_INVALID);
			}
			try {
				reader.setInput(imageInput, true, true);
				long width = reader.getWidth(0);
				long height = reader.getHeight(0);
				if (width <= 0 || height <= 0) {
					throw new OcrException(OcrErrorCode.CONTENT_INVALID);
				}
				if (width * height > MAX_PIXELS) {
					throw new OcrException(OcrErrorCode.PIXEL_LIMIT_EXCEEDED);
				}
			}
			finally {
				reader.dispose();
			}
		}
		catch (OcrException exception) {
			throw exception;
		}
		catch (IOException | RuntimeException exception) {
			throw new OcrException(OcrErrorCode.CONTENT_INVALID);
		}
	}

	private static boolean startsWith(byte[] content, byte[] prefix) {
		if (content.length < prefix.length) {
			return false;
		}
		for (int index = 0; index < prefix.length; index++) {
			if (content[index] != prefix[index]) {
				return false;
			}
		}
		return true;
	}

	private Path createTempDirectory() throws IOException {
		Path parent = properties.tempDirectory();
		if (parent == null) {
			return Files.createTempDirectory("unispeaking-ocr-");
		}
		Files.createDirectories(parent);
		return Files.createTempDirectory(parent, "unispeaking-ocr-");
	}

	private static List<Path> writeImages(
			Path tempDirectory,
			List<ValidatedImage> images) throws IOException {
		List<Path> imagePaths = new ArrayList<>(images.size());
		for (int index = 0; index < images.size(); index++) {
			ValidatedImage image = images.get(index);
			Path imagePath = tempDirectory.resolve(
					"image-" + index + image.type().suffix());
			Files.write(imagePath, image.content());
			imagePaths.add(imagePath);
		}
		return imagePaths;
	}

	private String recognizeWithWorker(List<Path> imagePaths, int expectedCount) {
		synchronized (workerLock) {
			ensureWorkerStarted();
			String requestId = UUID.randomUUID().toString();
			try {
				WorkerRequest request = new WorkerRequest(requestId,
						imagePaths.stream().map(Path::toString).toList());
				workerWriter.write(objectMapper.writeValueAsString(request));
				workerWriter.newLine();
				workerWriter.flush();
				String responseLine = readWorkerLine(timeoutMillis(properties.getTimeout()));
				if (responseLine == null || responseLine.length() > MAX_STDOUT_BYTES) {
					throw new OcrException(OcrErrorCode.RESPONSE_INVALID);
				}
				WorkerResponse response = objectMapper.readValue(responseLine, WorkerResponse.class);
				if (response == null || !requestId.equals(response.id())) {
					throw new OcrException(OcrErrorCode.RESPONSE_INVALID);
				}
				if (response.error() != null && !response.error().isBlank()) {
					throw new OcrException(OcrErrorCode.PROCESS_FAILED);
				}
				return parseRecognizedText(response.results(), expectedCount);
			}
			catch (OcrException exception) {
				if (exception.errorCode() == OcrErrorCode.TIMEOUT
						|| exception.errorCode() == OcrErrorCode.PROCESS_FAILED
						|| exception.errorCode() == OcrErrorCode.RESPONSE_INVALID) {
					stopWorker();
				}
				throw exception;
			}
			catch (Exception exception) {
				stopWorker();
				throw new OcrException(OcrErrorCode.RESPONSE_INVALID);
			}
		}
	}

	private void ensureWorkerStarted() {
		synchronized (workerLock) {
			if (workerReady && workerProcess != null && workerProcess.isAlive()) {
				return;
			}
			stopWorker();
			if (!baseAvailable()) {
				throw new OcrException(OcrErrorCode.UNAVAILABLE);
			}
			try {
				startWorker();
				String readyLine = readWorkerLine(timeoutMillis(properties.getTimeout()));
				if (readyLine == null || readyLine.length() > MAX_STDOUT_BYTES) {
					throw new OcrException(OcrErrorCode.PROCESS_FAILED);
				}
				WorkerReady ready = objectMapper.readValue(readyLine, WorkerReady.class);
				if (ready == null || !ready.ready()) {
					throw new OcrException(OcrErrorCode.PROCESS_FAILED);
				}
				workerReady = true;
				LOGGER.info("paddle OCR worker started and model loaded");
			}
			catch (OcrException exception) {
				stopWorker();
				throw exception;
			}
			catch (Exception exception) {
				stopWorker();
				throw new OcrException(OcrErrorCode.PROCESS_FAILED);
			}
		}
	}

	private void startWorker() throws IOException {
		List<String> command = new ArrayList<>();
		command.add(properties.getPythonExecutable());
		command.add(properties.getRunnerPath());
		command.add("--text-detection-model-name");
		command.add(TEXT_DETECTION_MODEL_NAME);
		command.add("--text-recognition-model-name");
		command.add(TEXT_RECOGNITION_MODEL_NAME);
		command.add("--device");
		command.add("cpu");
		command.add("--disable-doc-orientation");
		command.add("--disable-doc-unwarping");
		command.add("--disable-textline-orientation");
		command.add("--worker");

		ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.environment().put("PADDLE_PDX_CACHE_HOME", properties.getModelDirectory());
		workerProcess = processBuilder.start();
		workerWriter = new BufferedWriter(new OutputStreamWriter(
				workerProcess.getOutputStream(), StandardCharsets.UTF_8));
		workerReader = new BufferedReader(new InputStreamReader(
				workerProcess.getInputStream(), StandardCharsets.UTF_8));
		workerStderrExecutor = Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "paddle-ocr-worker-stderr");
			thread.setDaemon(true);
			return thread;
		});
		workerStderrExecutor.submit(() -> drainWorkerStderr(workerProcess.getErrorStream()));
	}

	private String readWorkerLine(long timeoutMillis) throws Exception {
		ExecutorService readerExecutor = Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "paddle-ocr-worker-reader");
			thread.setDaemon(true);
			return thread;
		});
		try {
			Future<String> line = readerExecutor.submit(workerReader::readLine);
			try {
				return line.get(timeoutMillis, TimeUnit.MILLISECONDS);
			}
			catch (java.util.concurrent.TimeoutException exception) {
				line.cancel(true);
				throw new OcrException(OcrErrorCode.TIMEOUT);
			}
		}
		finally {
			readerExecutor.shutdownNow();
		}
	}

	private static void drainWorkerStderr(InputStream input) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				input, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				LOGGER.debug("paddle OCR worker: {}", line);
			}
		}
		catch (IOException exception) {
			// The worker lifecycle owns this stream; shutdown is expected to close it.
		}
	}

	private void stopWorker() {
		workerReady = false;
		if (workerWriter != null) {
			try {
				workerWriter.close();
			}
			catch (IOException ignored) {
			}
		}
		if (workerProcess != null) {
			terminateProcess(workerProcess);
		}
		if (workerStderrExecutor != null) {
			workerStderrExecutor.shutdownNow();
		}
		workerWriter = null;
		workerReader = null;
		workerProcess = null;
		workerStderrExecutor = null;
	}

	private static void terminateProcess(Process process) {
		process.destroyForcibly();
		try {
			process.waitFor(5, TimeUnit.SECONDS);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}

	private static long timeoutMillis(Duration timeout) {
		long millis = timeout.toMillis();
		return millis <= 0 ? 1 : millis;
	}

	private static LimitedOutput getOutput(Future<LimitedOutput> future) {
		try {
			return future.get();
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new OcrException(OcrErrorCode.PROCESS_FAILED, exception);
		}
		catch (ExecutionException exception) {
			throw new OcrException(OcrErrorCode.PROCESS_FAILED);
		}
	}

	private static LimitedOutput readLimited(InputStream input, int limit) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 8192));
		byte[] buffer = new byte[8192];
		int total = 0;
		boolean truncated = false;
		int read;
		while ((read = input.read(buffer)) != -1) {
			if (total + read <= limit) {
				output.write(buffer, 0, read);
			}
			else {
				int remaining = Math.max(0, limit - total);
				if (remaining > 0) {
					output.write(buffer, 0, remaining);
				}
				truncated = true;
			}
			total += read;
		}
		return new LimitedOutput(output.toString(StandardCharsets.UTF_8), truncated);
	}

	private String parseRecognizedText(
			List<RunnerImageResult> results,
			int expectedCount) {
		if (results == null || results.size() != expectedCount) {
			throw new OcrException(OcrErrorCode.RESPONSE_INVALID);
		}
		List<String> texts = new ArrayList<>(results.size());
		for (RunnerImageResult result : results) {
			if (result == null || result.text() == null) {
				throw new OcrException(OcrErrorCode.RESPONSE_INVALID);
			}
			String text = result.text().trim();
			if (!text.isEmpty()) {
				texts.add(text);
			}
		}
		return String.join("\n", texts);
	}

	private static void deleteTempDirectory(Path tempDirectory) {
		if (tempDirectory == null || !Files.exists(tempDirectory)) {
			return;
		}
		try (var paths = Files.walk(tempDirectory)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				}
				catch (IOException exception) {
					// OCR temp cleanup is best-effort and never reports file paths outward.
				}
			});
		}
		catch (IOException exception) {
			// Avoid leaking temp paths or image-derived details through OCR errors.
		}
	}

	private enum ImageType {
		PNG(".png", "png"),
		JPEG(".jpg", "jpeg");

		private final String suffix;
		private final String formatName;

		ImageType(String suffix, String formatName) {
			this.suffix = suffix;
			this.formatName = formatName;
		}

		String suffix() {
			return suffix;
		}

		String formatName() {
			return formatName;
		}
	}

	private record ValidatedImage(byte[] content, ImageType type) {
	}

	private record LimitedOutput(String text, boolean truncated) {
	}

	private record RunnerResponse(List<RunnerImageResult> results) {
	}

	private record RunnerImageResult(String text) {
	}

	private record WorkerRequest(String id, List<String> images) {
	}

	private record WorkerResponse(
			String id,
			List<RunnerImageResult> results,
			String error) {
	}

	private record WorkerReady(boolean ready) {
	}
}
