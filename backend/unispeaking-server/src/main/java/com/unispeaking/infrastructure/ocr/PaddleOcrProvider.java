package com.unispeaking.infrastructure.ocr;

import com.unispeaking.common.exception.ocr.OcrErrorCode;
import com.unispeaking.common.exception.ocr.OcrException;
import com.unispeaking.domain.dto.ocr.OcrImage;
import com.unispeaking.infrastructure.config.OcrProperties;
import com.unispeaking.provider.OcrProvider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import tools.jackson.databind.ObjectMapper;

public final class PaddleOcrProvider implements OcrProvider {

	static final int MAX_IMAGE_COUNT = 5;
	static final int MAX_TOTAL_BYTES = 10 * 1024 * 1024;
	static final long MAX_PIXELS = 25_000_000L;
	static final int MAX_STDOUT_BYTES = 1024 * 1024;
	static final int MAX_STDERR_BYTES = 64 * 1024;
	static final String TEXT_DETECTION_MODEL_NAME = "PP-OCRv5_mobile_det";
	static final String TEXT_RECOGNITION_MODEL_NAME = "PP-OCRv5_mobile_rec";

	private final OcrProperties properties;
	private final ObjectMapper objectMapper;

	public PaddleOcrProvider(OcrProperties properties, ObjectMapper objectMapper) {
		this.properties = Objects.requireNonNull(properties, "OCR properties are required");
		this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper is required");
	}

	@Override
	public String recognizeText(List<OcrImage> images) {
		ensureAvailable();
		List<ValidatedImage> validatedImages = validateImages(images);
		Path tempDirectory = null;
		try {
			tempDirectory = createTempDirectory();
			List<Path> imagePaths = writeImages(tempDirectory, validatedImages);
			ProcessResult result = runOcrProcess(imagePaths);
			return parseRecognizedText(result.stdout(), validatedImages.size());
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
		if (!available()) {
			throw new OcrException(OcrErrorCode.UNAVAILABLE);
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

	private ProcessResult runOcrProcess(List<Path> imagePaths) throws IOException {
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
		command.add("--images");
		imagePaths.stream().map(Path::toString).forEach(command::add);

		ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.environment().put("PADDLE_PDX_CACHE_HOME", properties.getModelDirectory());
		Process process = processBuilder.start();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<LimitedOutput> stdout =
					executor.submit(() -> readLimited(process.getInputStream(), MAX_STDOUT_BYTES));
			Future<LimitedOutput> stderr =
					executor.submit(() -> readLimited(process.getErrorStream(), MAX_STDERR_BYTES));
			boolean finished = process.waitFor(
					timeoutMillis(properties.getTimeout()),
					TimeUnit.MILLISECONDS);
			if (!finished) {
				terminateProcess(process);
				throw new OcrException(OcrErrorCode.TIMEOUT);
			}
			LimitedOutput stdoutOutput = getOutput(stdout);
			LimitedOutput stderrOutput = getOutput(stderr);
			if (stdoutOutput.truncated()) {
				throw new OcrException(OcrErrorCode.RESPONSE_INVALID);
			}
			if (stderrOutput.truncated()) {
				throw new OcrException(OcrErrorCode.PROCESS_FAILED);
			}
			if (process.exitValue() != 0) {
				throw new OcrException(OcrErrorCode.PROCESS_FAILED);
			}
			return new ProcessResult(stdoutOutput.text());
		}
		catch (InterruptedException exception) {
			terminateProcess(process);
			Thread.currentThread().interrupt();
			throw new OcrException(OcrErrorCode.PROCESS_FAILED, exception);
		}
		finally {
			executor.shutdownNow();
		}
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

	private String parseRecognizedText(String stdout, int expectedCount) {
		RunnerResponse response;
		try {
			response = objectMapper.readValue(stdout, RunnerResponse.class);
		}
		catch (Exception exception) {
			throw new OcrException(OcrErrorCode.RESPONSE_INVALID);
		}
		if (response == null
				|| response.results() == null
				|| response.results().size() != expectedCount) {
			throw new OcrException(OcrErrorCode.RESPONSE_INVALID);
		}
		List<String> texts = new ArrayList<>(response.results().size());
		for (RunnerImageResult result : response.results()) {
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

	private record ProcessResult(String stdout) {
	}

	private record RunnerResponse(List<RunnerImageResult> results) {
	}

	private record RunnerImageResult(String text) {
	}
}
