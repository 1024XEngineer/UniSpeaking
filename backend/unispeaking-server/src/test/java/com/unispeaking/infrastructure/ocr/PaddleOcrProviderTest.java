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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import java.lang.reflect.Method;
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

	@Test
	void startsAndStopsConfiguredWorkerDuringApplicationLifecycle() throws IOException {
		Path processId = tempRoot.resolve("lifecycle-process-id.txt");
		Path script = script("""
				import json, os, pathlib, sys
				pathlib.Path(r'%s').write_text(str(os.getpid()))
				print(json.dumps({'ready': True}), flush=True)
				for line in sys.stdin:
					pass
				""".formatted(processId));
		PaddleOcrProvider provider = provider(script, Duration.ofSeconds(2));

		provider.startWorkerOnApplicationStartup();
		long pid = Long.parseLong(Files.readString(processId));
		assertTrue(provider.available());

		provider.stopWorkerOnApplicationShutdown();

		assertAll(
				() -> assertFalse(provider.available()),
				() -> assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)));
	}

	@Test
	void skipsStartupWhenOcrIsNotConfiguredAndReportsStartupFailure() throws IOException {
		OcrProperties disabled = properties(Duration.ofSeconds(1));
		disabled.setEnabled(false);
		PaddleOcrProvider disabledProvider = new PaddleOcrProvider(disabled, new ObjectMapper());
		disabledProvider.startWorkerOnApplicationStartup();
		assertFalse(disabledProvider.available());

		OcrProperties broken = properties(Duration.ofSeconds(1));
		broken.setRunnerPath(tempRoot.resolve("runner-that-does-not-exist.py").toString());
		broken.setModelDirectory(createModelDirectory().toString());
		PaddleOcrProvider brokenProvider = new PaddleOcrProvider(broken, new ObjectMapper());
		brokenProvider.startWorkerOnApplicationStartup();
		assertFalse(brokenProvider.available());
	}

	@Test
	void rejectsInvalidWorkerReadyLinesAndWorkerErrorResponses() throws IOException {
		PaddleOcrProvider notReady = provider(script("print('{\\\"ready\\\": false}', flush=True)"),
				Duration.ofSeconds(2));
		OcrException startupException = assertThrows(
				OcrException.class,
				() -> notReady.recognizeText(List.of(new OcrImage(png()))));

		Path errorScript = script("""
				import json, sys
				print(json.dumps({'ready': True}), flush=True)
				request = json.loads(sys.stdin.readline())
				print(json.dumps({'id': request['id'], 'error': 'worker rejected request'}), flush=True)
				""");
		PaddleOcrProvider workerError = provider(errorScript, Duration.ofSeconds(2));
		OcrException responseException = assertThrows(
				OcrException.class,
				() -> workerError.recognizeText(List.of(new OcrImage(png()))));

		assertAll(
				() -> assertSame(OcrErrorCode.PROCESS_FAILED, startupException.errorCode()),
				() -> assertSame(OcrErrorCode.PROCESS_FAILED, responseException.errorCode()),
				() -> assertFalse(workerError.available()),
				() -> assertTempDirectoryClean());
	}

	@Test
	void rejectsMismatchedAndMalformedWorkerResults() throws IOException {
		assertWorkerResponseError("""
				print(json.dumps({'id': 'different-request-id', 'results': []}), flush=True)
				""", OcrErrorCode.RESPONSE_INVALID);
		assertWorkerResponseError("""
				print(json.dumps({'id': request['id'], 'results': []}), flush=True)
				""", OcrErrorCode.RESPONSE_INVALID);
		assertWorkerResponseError("""
				print(json.dumps({'id': request['id'], 'results': [{'text': None}]}), flush=True)
				""", OcrErrorCode.RESPONSE_INVALID);
		assertWorkerResponseError("""
				print(json.dumps({'id': request['id'], 'results': [{'text': '  ' }]}), flush=True)
				""", null);
	}

	@Test
	void coversLimitedOutputNormalTruncatedAndFutureFailurePaths() throws Exception {
		Object normal = invokeReadLimited(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)), 5);
		Object truncated = invokeReadLimited(new ByteArrayInputStream("hello!".getBytes(StandardCharsets.UTF_8)), 5);
		assertAll(
				() -> assertEquals("hello", recordValue(normal, "text")),
				() -> assertFalse((Boolean) recordValue(normal, "truncated")),
				() -> assertEquals("hello", recordValue(truncated, "text")),
				() -> assertTrue((Boolean) recordValue(truncated, "truncated")));

		OcrException interrupted = assertThrows(OcrException.class,
				() -> invokeGetOutput(new ThrowingFuture(new InterruptedException())));
		OcrException failed = assertThrows(OcrException.class,
				() -> invokeGetOutput(new ThrowingFuture(new ExecutionException("failed", null))));
		assertAll(
				() -> assertSame(OcrErrorCode.PROCESS_FAILED, interrupted.errorCode()),
				() -> assertSame(OcrErrorCode.PROCESS_FAILED, failed.errorCode()),
				() -> assertTrue(Thread.currentThread().isInterrupted()));
		Thread.interrupted();
	}

	@Test
	void rejectsEmptyAndMalformedImageContentBeforeStartingWorker() throws IOException {
		Path marker = tempRoot.resolve("worker-started.txt");
		Path script = script("""
				import pathlib
				pathlib.Path(r'%s').write_text('started')
				""".formatted(marker));
		PaddleOcrProvider provider = provider(script, Duration.ofSeconds(1));

		assertAll(
				() -> assertError(provider, List.of(new OcrImage(new byte[0])), OcrErrorCode.INPUT_REQUIRED),
				() -> assertError(provider, List.of(new OcrImage(pngHeader(0, 0))), OcrErrorCode.CONTENT_INVALID),
				() -> assertError(provider, List.of(new OcrImage(new byte[] {(byte) 0xff, (byte) 0xd8, 1})),
						OcrErrorCode.CONTENT_INVALID),
				() -> assertFalse(Files.exists(marker)));
	}

	private void assertWorkerResponseError(String responseBody, OcrErrorCode expected)
			throws IOException {
		Path script = script("""
				import json, sys
				print(json.dumps({'ready': True}), flush=True)
				request = json.loads(sys.stdin.readline())
				%s
				""".formatted(responseBody));
		PaddleOcrProvider provider = provider(script, Duration.ofSeconds(2));
		if (expected == null) {
			assertEquals("", provider.recognizeText(List.of(new OcrImage(png()))));
		}
		else {
			OcrException exception = assertThrows(OcrException.class,
					() -> provider.recognizeText(List.of(new OcrImage(png()))));
			assertSame(expected, exception.errorCode());
		}
	}

	private void assertError(PaddleOcrProvider provider, List<OcrImage> images, OcrErrorCode expected) {
		OcrException exception = assertThrows(OcrException.class, () -> provider.recognizeText(images));
		assertSame(expected, exception.errorCode());
	}

	private Path createModelDirectory() throws IOException {
		Path modelDirectory = tempRoot.resolve("models-for-test");
		Files.createDirectories(modelDirectory.resolve("official_models/PP-OCRv5_mobile_det"));
		Files.createDirectories(modelDirectory.resolve("official_models/PP-OCRv5_mobile_rec"));
		return modelDirectory;
	}

	private static Object invokeReadLimited(InputStream input, int limit) throws Exception {
		Method method = PaddleOcrProvider.class.getDeclaredMethod("readLimited", InputStream.class, int.class);
		method.setAccessible(true);
		return method.invoke(null, input, limit);
	}

	private static Object invokeGetOutput(Future<?> future) throws Exception {
		Method method = PaddleOcrProvider.class.getDeclaredMethod("getOutput", Future.class);
		method.setAccessible(true);
		try {
			return method.invoke(null, future);
		}
		catch (java.lang.reflect.InvocationTargetException exception) {
			if (exception.getCause() instanceof OcrException ocrException) {
				throw ocrException;
			}
			throw exception;
		}
	}

	private static Object recordValue(Object record, String accessor) throws Exception {
		Method method = record.getClass().getDeclaredMethod(accessor);
		method.setAccessible(true);
		return method.invoke(record);
	}

	private static final class ThrowingFuture implements Future<Object> {

		private final Exception exception;

		private ThrowingFuture(Exception exception) {
			this.exception = exception;
		}

		@Override
		public Object get() throws InterruptedException, ExecutionException {
			if (exception instanceof InterruptedException interrupted) {
				throw interrupted;
			}
			throw (ExecutionException) exception;
		}

		@Override
		public Object get(long timeout, java.util.concurrent.TimeUnit unit) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			return false;
		}

		@Override
		public boolean isCancelled() {
			return false;
		}

		@Override
		public boolean isDone() {
			return true;
		}
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
