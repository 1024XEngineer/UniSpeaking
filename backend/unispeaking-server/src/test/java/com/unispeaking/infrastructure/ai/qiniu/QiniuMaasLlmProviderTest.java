package com.unispeaking.infrastructure.ai.qiniu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.infrastructure.config.QiniuMaasProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

class QiniuMaasLlmProviderTest {

	private HttpServer server;

	@AfterEach
	void stopServer() {
		if (server != null) server.stop(0);
	}

	@Test
	void sendsAnOpenAiCompatibleNonStreamingRequest() throws IOException {
		AtomicReference<String> authorization = new AtomicReference<>();
		AtomicReference<String> requestBody = new AtomicReference<>();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/chat/completions", exchange -> {
			authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			requestBody.set(new String(
					exchange.getRequestBody().readAllBytes(),
					StandardCharsets.UTF_8));
			byte[] response = """
					{"choices":[{"message":{"content":"ok"}}]}
					""".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		server.start();

		QiniuMaasProperties properties = properties(
				"secret-key",
				"deepseek/deepseek-v4-flash");
		QiniuMaasLlmClient client = new QiniuMaasLlmClient(
				HttpClient.newHttpClient(),
				new ObjectMapper(),
				properties,
				java.net.URI.create(
						"http://127.0.0.1:" + server.getAddress().getPort()
								+ "/v1/chat/completions"));
		QiniuMaasLlmProvider provider = new QiniuMaasLlmProvider(
				client,
				properties.primaryModel());

		String response = provider.executeLlmTask("Return JSON.", null);

		assertEquals("ok", response);
		assertEquals("Bearer secret-key", authorization.get());
		assertTrue(requestBody.get().contains("\"model\":\"deepseek/deepseek-v4-flash\""));
		assertTrue(requestBody.get().contains("\"content\":\"Return JSON.\""));
		assertTrue(requestBody.get().contains("\"stream\":false"));
		assertTrue(requestBody.get().contains("\"max_tokens\":4096"));
		assertFalse(requestBody.get().contains("secret-key"));
		assertFalse(requestBody.get().contains("thinking"));
	}

	@Test
	void reportsMissingCredentialsWithoutSendingARequest() {
		QiniuMaasProperties properties = properties("", "deepseek/deepseek-v4-flash");
		QiniuMaasLlmProvider provider = new QiniuMaasLlmProvider(
				new QiniuMaasLlmClient(
						HttpClient.newHttpClient(),
						new ObjectMapper(),
						properties),
				properties.primaryModel());

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider.executeLlmTask("hello", null));

		assertEquals("QINIU_MAAS_CREDENTIAL_MISSING", exception.code());
	}

	@Test
	void mapsRequestTimeoutsToADedicatedRetryableFailure() throws Exception {
		HttpClient httpClient = mock(HttpClient.class);
		doThrow(new HttpTimeoutException("timed out"))
				.when(httpClient)
				.send(
						any(HttpRequest.class),
						org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any());
		QiniuMaasProperties properties = properties(
				"secret-key",
				"qwen/qwen3.5-plus");
		QiniuMaasLlmProvider provider = new QiniuMaasLlmProvider(
				new QiniuMaasLlmClient(httpClient, new ObjectMapper(), properties),
				properties.primaryModel());

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider.executeLlmTask("hello", null));

		assertEquals("QINIU_MAAS_LLM_TIMEOUT", exception.code());
	}

	@Test
	void rejectsBlankPromptsAsNonRetryableInputFailures() {
		QiniuMaasProperties properties = properties("secret-key", "qwen/qwen3.5-plus");
		QiniuMaasLlmProvider provider = new QiniuMaasLlmProvider(
				new QiniuMaasLlmClient(
						HttpClient.newHttpClient(),
						new ObjectMapper(),
						properties),
				properties.primaryModel());

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider.executeLlmTask("  ", null));

		assertEquals("INVALID_LLM_PROMPT", exception.code());
	}

	@Test
	void mapsAuthenticationFailuresWithoutExposingTheApiKey() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/chat/completions", exchange -> {
			byte[] response = "{\"error\":{\"message\":\"invalid api key\"}}"
					.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(401, response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		server.start();
		QiniuMaasProperties properties = properties(
				"secret-key",
				"deepseek/deepseek-v4-flash");
		QiniuMaasLlmProvider provider = new QiniuMaasLlmProvider(
				new QiniuMaasLlmClient(
						HttpClient.newHttpClient(),
						new ObjectMapper(),
						properties,
						java.net.URI.create(
								"http://127.0.0.1:" + server.getAddress().getPort()
										+ "/v1/chat/completions")),
				properties.primaryModel());

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider.executeLlmTask("hello", null));

		assertEquals("QINIU_MAAS_LLM_REQUEST_FAILED", exception.code());
		assertFalse(exception.getMessage().contains("secret-key"));
	}

	@Test
	void logsTimingMetadataWithoutCredentialsOrPromptContent() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/chat/completions", exchange -> {
			byte[] response = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}"
					.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		server.start();
		QiniuMaasProperties properties = properties(
				"secret-key",
				"deepseek/deepseek-v4-flash");
		QiniuMaasLlmProvider provider = new QiniuMaasLlmProvider(
				new QiniuMaasLlmClient(
						HttpClient.newHttpClient(),
						new ObjectMapper(),
						properties,
						java.net.URI.create(
								"http://127.0.0.1:" + server.getAddress().getPort()
										+ "/v1/chat/completions")),
				properties.primaryModel());
		Logger logger = (Logger) LoggerFactory.getLogger(QiniuMaasLlmClient.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);

		try {
			provider.executeLlmTask("private prompt content", null);
		}
		finally {
			logger.detachAppender(appender);
		}

		String logs = appender.list.stream()
				.map(ILoggingEvent::getFormattedMessage)
				.collect(java.util.stream.Collectors.joining("\n"));
		assertTrue(logs.contains("request started model=deepseek/deepseek-v4-flash"));
		assertTrue(logs.contains("request completed model=deepseek/deepseek-v4-flash"));
		assertTrue(logs.contains("durationMs="));
		assertTrue(logs.contains("responseChars=2"));
		assertFalse(logs.contains("secret-key"));
		assertFalse(logs.contains("private prompt content"));
	}

	@Test
	void mapsMalformedAndEmptyResponsesToSafeProviderFailures() throws Exception {
		HttpClient httpClient = mock(HttpClient.class);
		@SuppressWarnings("unchecked")
		HttpResponse<InputStream> response = (HttpResponse<InputStream>) mock(HttpResponse.class);
		when(response.statusCode()).thenReturn(200);
		when(response.body()).thenReturn(new java.io.ByteArrayInputStream("not-json".getBytes(StandardCharsets.UTF_8)));
		when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()))
				.thenReturn(response);
		QiniuMaasProperties properties = properties("key", "qwen/qwen3.5-plus");
		QiniuMaasLlmProvider provider = new QiniuMaasLlmProvider(
				new QiniuMaasLlmClient(httpClient, new ObjectMapper(), properties), properties.primaryModel());
		BusinessException malformed = assertThrows(BusinessException.class,
				() -> provider.executeLlmTask("hello", null));
		assertEquals("QINIU_MAAS_LLM_RESPONSE_INVALID", malformed.code());

		when(response.body()).thenReturn(new java.io.ByteArrayInputStream(
				"{\"choices\":[{\"message\":{\"content\":\"\"}}]}".getBytes(StandardCharsets.UTF_8)));
		BusinessException empty = assertThrows(BusinessException.class,
				() -> provider.executeLlmTask("hello", null));
		assertEquals("QINIU_MAAS_LLM_EMPTY_RESPONSE", empty.code());
	}

	@Test
	void mapsHttpFailureAndResponseSizeLimitWithoutLeakingCredentials() throws Exception {
		HttpClient httpClient = mock(HttpClient.class);
		@SuppressWarnings("unchecked")
		HttpResponse<InputStream> response = (HttpResponse<InputStream>) mock(HttpResponse.class);
		when(response.statusCode()).thenReturn(429);
		when(response.body()).thenReturn(new java.io.ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
		when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()))
				.thenReturn(response);
		QiniuMaasProperties properties = properties("secret-key", "qwen/qwen3.5-plus");
		QiniuMaasLlmProvider provider = new QiniuMaasLlmProvider(
				new QiniuMaasLlmClient(httpClient, new ObjectMapper(), properties), properties.primaryModel());
		BusinessException rateLimited = assertThrows(BusinessException.class,
				() -> provider.executeLlmTask("hello", null));
		assertEquals("QINIU_MAAS_LLM_REQUEST_FAILED", rateLimited.code());
		assertFalse(rateLimited.getMessage().contains("secret-key"));

		QiniuMaasProperties tiny = new QiniuMaasProperties("https://api.qnaigc.com/v1", "key",
				"qwen/qwen3.5-plus", "deepseek/deepseek-v4-flash", Duration.ofSeconds(10),
				Duration.ofSeconds(30), 3, 4096);
		when(response.statusCode()).thenReturn(200);
		when(response.body()).thenReturn(new java.io.ByteArrayInputStream("1234".getBytes(StandardCharsets.UTF_8)));
		QiniuMaasLlmProvider limited = new QiniuMaasLlmProvider(
				new QiniuMaasLlmClient(httpClient, new ObjectMapper(), tiny), tiny.primaryModel());
		BusinessException oversized = assertThrows(BusinessException.class,
				() -> limited.executeLlmTask("hello", null));
		assertEquals("QINIU_MAAS_LLM_RESPONSE_TOO_LARGE", oversized.code());
	}

	@Test
	void mapsIoAndInterruptFailuresAndRestoresInterruptStatus() throws Exception {
		HttpClient httpClient = mock(HttpClient.class);
		QiniuMaasProperties properties = properties("key", "qwen/qwen3.5-plus");
		when(httpClient.send(any(HttpRequest.class),
				org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()))
				.thenThrow(new IOException("offline"));
		QiniuMaasLlmClient client = new QiniuMaasLlmClient(httpClient,
				new ObjectMapper(), properties);
		QiniuMaasLlmClient.ProviderFailure io = assertThrows(
				QiniuMaasLlmClient.ProviderFailure.class,
				() -> client.execute(properties.primaryModel(), "hello"));
		assertEquals("QINIU_MAAS_LLM_IO_ERROR", io.code());
		assertTrue(io.retryable());

		when(httpClient.send(any(HttpRequest.class),
				org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()))
				.thenThrow(new InterruptedException("cancelled"));
		Thread.interrupted();
		QiniuMaasLlmClient.ProviderFailure interrupted = assertThrows(
				QiniuMaasLlmClient.ProviderFailure.class,
				() -> client.execute(properties.primaryModel(), "hello"));
		assertEquals("QINIU_MAAS_LLM_INTERRUPTED", interrupted.code());
		assertFalse(interrupted.retryable());
		assertTrue(Thread.currentThread().isInterrupted());
		Thread.interrupted();
	}

	@Test
	void marksForbiddenHttpFailuresAsNonRetryable() throws Exception {
		HttpClient httpClient = mock(HttpClient.class);
		@SuppressWarnings("unchecked")
		HttpResponse<InputStream> response = (HttpResponse<InputStream>) mock(HttpResponse.class);
		when(response.statusCode()).thenReturn(403);
		when(response.body()).thenReturn(new java.io.ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
		when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()))
				.thenReturn(response);
		QiniuMaasProperties properties = properties("key", "qwen/qwen3.5-plus");
		QiniuMaasLlmClient.ProviderFailure failure = assertThrows(
				QiniuMaasLlmClient.ProviderFailure.class,
				() -> new QiniuMaasLlmClient(httpClient, new ObjectMapper(), properties)
						.execute(properties.primaryModel(), "hello"));
		assertEquals("QINIU_MAAS_LLM_REQUEST_FAILED", failure.code());
		assertFalse(failure.retryable());
	}

	private QiniuMaasProperties properties(String apiKey, String primaryModel) {
		return new QiniuMaasProperties(
				"https://api.qnaigc.com/v1",
				apiKey,
				primaryModel,
				primaryModel.equals("qwen/qwen3.5-plus")
						? "deepseek/deepseek-v4-flash"
						: "qwen/qwen3.5-plus",
				Duration.ofSeconds(10),
				Duration.ofSeconds(30),
				2_097_152,
				4096);
	}
}
