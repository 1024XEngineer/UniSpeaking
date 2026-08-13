package com.unispeaking.infrastructure.ocr;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.common.exception.ocr.OcrErrorCode;
import com.unispeaking.common.exception.ocr.OcrException;
import com.unispeaking.domain.dto.ocr.OcrImage;
import com.unispeaking.infrastructure.config.OcrProperties;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class PaddleOcrProviderTest {

	@TempDir
	Path tempRoot;

	@Test
	void recognizesPngAndJpegInInputOrderWithOneBatchProcess() throws IOException {
		Path invocationCounter = tempRoot.resolve("invocations.txt");
		Path cacheHome = tempRoot.resolve("models");
		Path script = script("""
				import json, os, pathlib, sys
				count_file = pathlib.Path(r'%s')
				if os.environ.get('PADDLE_PDX_CACHE_HOME') != r'%s':
				    raise SystemExit(11)
				print(json.dumps({'ready': True}), flush=True)
				for line in sys.stdin:
				    request = json.loads(line)
				    current = int(count_file.read_text() if count_file.exists() else '0')
				    count_file.write_text(str(current + 1))
				    print(json.dumps({'id': request['id'], 'results': [
				        {'text': pathlib.Path(path).stem} for path in request['images']
				    ]}), flush=True)
				""".formatted(invocationCounter, cacheHome));
		PaddleOcrProvider provider = provider(script, Duration.ofSeconds(2));

		String text = provider.recognizeText(List.of(
				new OcrImage(png()),
				new OcrImage(jpeg())));

		assertAll(
				() -> assertEquals("image-0\nimage-1", text),
				() -> assertEquals("1", Files.readString(invocationCounter)),
				() -> assertTempDirectoryClean());
	}

	@Test
	void rejectsMissingAndTooManyImages() {
		assertAll(
				() -> assertError(null, OcrErrorCode.INPUT_REQUIRED),
				() -> assertError(List.of(), OcrErrorCode.INPUT_REQUIRED),
				() -> assertError(Arrays.asList(new OcrImage(png()), null),
						OcrErrorCode.INPUT_REQUIRED),
				() -> assertError(List.of(
								new OcrImage(png()), new OcrImage(png()),
								new OcrImage(png()), new OcrImage(png()),
								new OcrImage(png()), new OcrImage(png())),
						OcrErrorCode.TOO_MANY_IMAGES));
	}

	@Test
	void reportsUnavailableWhenPrefetchedModelsAreMissing() throws IOException {
		Path modelDirectory = tempRoot.resolve("missing-models");
		Files.createDirectories(modelDirectory.resolve("official_models"));
		OcrProperties properties = properties(Duration.ofSeconds(1));
		properties.setRunnerPath(script("raise SystemExit(99)").toString());
		properties.setModelDirectory(modelDirectory.toString());

		assertFalse(new PaddleOcrProvider(properties, new ObjectMapper()).available());
	}

	@Test
	void rejectsImagesAboveTotalSizeLimit() {
		byte[] largePng = Arrays.copyOf(png(), 3 * 1024 * 1024);

		assertError(
				List.of(
						new OcrImage(largePng),
						new OcrImage(largePng),
						new OcrImage(largePng),
						new OcrImage(largePng)),
				OcrErrorCode.TOTAL_SIZE_EXCEEDED);
	}

	@Test
	void rejectsUnsupportedMagicBytesBeforeProcessStart() {
		assertError(
				List.of(new OcrImage("not an image".getBytes(StandardCharsets.UTF_8))),
				OcrErrorCode.FORMAT_UNSUPPORTED);
	}

	@Test
	void rejectsImagesAbovePixelLimit() {
		assertError(
				List.of(new OcrImage(pngHeader(5001, 5000))),
				OcrErrorCode.PIXEL_LIMIT_EXCEEDED);
	}

	@Test
	void mapsTimeoutToStableErrorAndCleansTempDirectory() throws IOException {
		Path processId = tempRoot.resolve("process-id.txt");
		Path script = script("""
				import json, os, pathlib, sys, time
				pathlib.Path(r'%s').write_text(str(os.getpid()))
				print(json.dumps({'ready': True}), flush=True)
				sys.stdin.readline()
				time.sleep(10)
				""".formatted(processId));
		PaddleOcrProvider provider = provider(script, Duration.ofMillis(500));

		OcrException exception = assertThrows(
				OcrException.class,
				() -> provider.recognizeText(List.of(new OcrImage(png()))));

		assertAll(
				() -> assertSame(OcrErrorCode.TIMEOUT, exception.errorCode()),
				() -> assertFalse(ProcessHandle.of(Long.parseLong(
						Files.readString(processId))).map(ProcessHandle::isAlive).orElse(false)),
				() -> assertTempDirectoryClean());
	}

	@Test
	void mapsProcessExitToStableErrorAndCleansTempDirectory() throws IOException {
		Path script = script("""
				import sys
				print('safe diagnostic only', file=sys.stderr)
				raise SystemExit(7)
				""");
		PaddleOcrProvider provider = provider(script, Duration.ofSeconds(2));

		OcrException exception = assertThrows(
				OcrException.class,
				() -> provider.recognizeText(List.of(new OcrImage(png()))));

		assertAll(
				() -> assertSame(OcrErrorCode.PROCESS_FAILED, exception.errorCode()),
				() -> assertTempDirectoryClean());
	}

	@Test
	void mapsInvalidJsonToStableErrorWithoutRecognitionText() throws IOException {
		Path script = script("""
				import json, sys
				print(json.dumps({'ready': True}), flush=True)
				request = json.loads(sys.stdin.readline())
				print('recognized secret text that must not leak', flush=True)
				""");
		PaddleOcrProvider provider = provider(script, Duration.ofSeconds(2));

		OcrException exception = assertThrows(
				OcrException.class,
				() -> provider.recognizeText(List.of(new OcrImage(png()))));

		assertAll(
				() -> assertSame(OcrErrorCode.RESPONSE_INVALID, exception.errorCode()),
				() -> assertFalse(exception.getMessage().contains("recognized secret text")),
				() -> assertTempDirectoryClean());
	}

	@Test
	void mapsOversizedStdoutToInvalidResponse() throws IOException {
		Path script = script("""
				import json, sys
				print(json.dumps({'ready': True}), flush=True)
				request = json.loads(sys.stdin.readline())
				print('x' * %d, flush=True)
				""".formatted(PaddleOcrProvider.MAX_STDOUT_BYTES + 1));
		PaddleOcrProvider provider = provider(script, Duration.ofSeconds(2));

		OcrException exception = assertThrows(
				OcrException.class,
				() -> provider.recognizeText(List.of(new OcrImage(png()))));

		assertSame(OcrErrorCode.RESPONSE_INVALID, exception.errorCode());
	}

	private void assertError(List<OcrImage> images, OcrErrorCode expected) {
		PaddleOcrProvider provider;
		try {
			provider = provider(script("raise SystemExit(99)"), Duration.ofSeconds(1));
		}
		catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
		OcrException exception = assertThrows(
				OcrException.class,
				() -> provider.recognizeText(images));

		assertAll(
				() -> assertSame(expected, exception.errorCode()),
				() -> assertEquals(expected.code(), exception.code()),
				() -> assertEquals(expected.defaultMessage(), exception.getMessage()));
	}

	private PaddleOcrProvider provider(Path script, Duration timeout) throws IOException {
		Path modelDirectory = tempRoot.resolve("models");
		Files.createDirectories(modelDirectory.resolve("official_models"));
		Files.createDirectories(modelDirectory.resolve(
				"official_models/PP-OCRv5_mobile_det"));
		Files.createDirectories(modelDirectory.resolve(
				"official_models/PP-OCRv5_mobile_rec"));
		OcrProperties properties = properties(timeout);
		properties.setRunnerPath(script.toString());
		properties.setModelDirectory(modelDirectory.toString());
		properties.setTempDirectory(tempRoot.resolve("work").toString());
		return new PaddleOcrProvider(properties, new ObjectMapper());
	}

	private OcrProperties properties(Duration timeout) {
		OcrProperties properties = new OcrProperties();
		properties.setPythonExecutable("python3");
		properties.setTimeout(timeout);
		return properties;
	}

	private Path script(String body) throws IOException {
		Path script = Files.createTempFile(tempRoot, "ocr-runner-", ".py");
		Files.writeString(script, body, StandardCharsets.UTF_8);
		return script;
	}

	private void assertTempDirectoryClean() throws IOException {
		Path work = tempRoot.resolve("work");
		if (!Files.exists(work)) {
			return;
		}
		try (var paths = Files.list(work)) {
			assertTrue(paths.findAny().isEmpty());
		}
	}

	private static byte[] png() {
		return image("png");
	}

	private static byte[] jpeg() {
		return image("jpg");
	}

	private static byte[] image(String format) {
		BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				image.setRGB(x, y, Color.WHITE.getRGB());
			}
		}
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(image, format, output);
			return output.toByteArray();
		}
		catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static byte[] pngHeader(int width, int height) {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			output.write(new byte[] {
					(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'
			});
			writeChunk(output, "IHDR", ByteBuffer.allocate(13)
					.order(ByteOrder.BIG_ENDIAN)
					.putInt(width)
					.putInt(height)
					.put((byte) 8)
					.put((byte) 2)
					.put((byte) 0)
					.put((byte) 0)
					.put((byte) 0)
					.array());
			writeChunk(output, "IEND", new byte[0]);
			return output.toByteArray();
		}
		catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static void writeChunk(
			ByteArrayOutputStream output,
			String type,
			byte[] data) throws IOException {
		byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
		output.write(ByteBuffer.allocate(4)
				.order(ByteOrder.BIG_ENDIAN)
				.putInt(data.length)
				.array());
		output.write(typeBytes);
		output.write(data);
		CRC32 crc = new CRC32();
		crc.update(typeBytes);
		crc.update(data);
		output.write(ByteBuffer.allocate(4)
				.order(ByteOrder.BIG_ENDIAN)
				.putInt((int) crc.getValue())
				.array());
	}
}
