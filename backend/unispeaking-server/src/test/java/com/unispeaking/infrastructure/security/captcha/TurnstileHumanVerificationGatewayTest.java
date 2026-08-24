package com.unispeaking.infrastructure.security.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TurnstileHumanVerificationGatewayTest {

    @Test
    void rejectsMissingSecretOrTokenWithoutMakingARequest() throws Exception {
        var emptySecret = gateway(new RecordingHttpClient(response(200, "{\"success\":true}")), " ");
        assertThat(emptySecret.verify("token")).isFalse();

        var client = new RecordingHttpClient(response(200, "{\"success\":true}"));
        var gateway = gateway(client, "secret");
        assertThat(gateway.verify(null)).isFalse();
        assertThat(gateway.verify(" ")).isFalse();
        assertThat(client.request).isNull();
    }

    @Test
    void sendsEncodedSecretAndTokenAndAcceptsSuccessfulResponse() throws Exception {
        var client = new RecordingHttpClient(response(200, "{\"success\":true}"));
        var gateway = gateway(client, "secret +/=?");

        assertThat(gateway.verify("token +/=?")).isTrue();
        assertThat(client.request.uri())
                .isEqualTo(URI.create("https://challenges.cloudflare.com/turnstile/v0/siteverify"));
        assertThat(client.request.timeout()).hasValue(Duration.ofSeconds(8));
        assertThat(client.request.headers().firstValue("Content-Type"))
                .hasValue("application/x-www-form-urlencoded");
        assertThat(client.request.bodyPublisher()).isPresent();
        assertThat(client.body).isEqualTo("secret=secret+%2B%2F%3D%3F&response=token+%2B%2F%3D%3F");
    }

    @Test
    void failsClosedForNonSuccessResponsesAndUnsuccessfulJson() throws Exception {
        var nonSuccess = gateway(new RecordingHttpClient(response(503, "{\"success\":true}")), "secret");
        assertThat(nonSuccess.verify("token")).isFalse();

        var unsuccessful = gateway(new RecordingHttpClient(response(200, "{\"success\":false}")), "secret");
        assertThat(unsuccessful.verify("token")).isFalse();

        var missingFlag = gateway(new RecordingHttpClient(response(200, "{}")), "secret");
        assertThat(missingFlag.verify("token")).isFalse();
    }

    @Test
    void failsClosedForMalformedResponsesAndTransportFailures() throws Exception {
        var malformed = gateway(new RecordingHttpClient(response(200, "not-json")), "secret");
        assertThat(malformed.verify("token")).isFalse();

        var ioFailure = gateway(new RecordingHttpClient(new java.io.IOException("network down")), "secret");
        assertThat(ioFailure.verify("token")).isFalse();

        var interrupted = gateway(new RecordingHttpClient(new InterruptedException("cancelled")), "secret");
        assertThat(interrupted.verify("token")).isFalse();
    }

    private TurnstileHumanVerificationGateway gateway(HttpClient client, String secret) throws Exception {
        var gateway = new TurnstileHumanVerificationGateway(JsonMapper.builder().build(), secret);
        var field = TurnstileHumanVerificationGateway.class.getDeclaredField("client");
        field.setAccessible(true);
        field.set(gateway, client);
        return gateway;
    }

    private static HttpResponse<String> response(int statusCode, String body) {
        return new StubResponse(statusCode, body);
    }

    private static final class RecordingHttpClient extends HttpClient {
        private final Object outcome;
        private HttpRequest request;
        private String body;

        private RecordingHttpClient(Object outcome) {
            this.outcome = outcome;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
                throws java.io.IOException, InterruptedException {
            this.request = request;
            this.body = readBody(request);
            if (outcome instanceof java.io.IOException exception) {
                throw exception;
            }
            if (outcome instanceof InterruptedException exception) {
                throw exception;
            }
            return (HttpResponse<T>) outcome;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> handler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return null; }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }

        private static String readBody(HttpRequest request) {
            var result = new AtomicReference<>(new StringBuilder());
            var completed = new java.util.concurrent.CountDownLatch(1);
            request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<java.nio.ByteBuffer>() {
                @Override public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }
                @Override public void onNext(java.nio.ByteBuffer item) {
                    var bytes = new byte[item.remaining()];
                    item.get(bytes);
                    result.get().append(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                }
                @Override public void onError(Throwable throwable) { completed.countDown(); }
                @Override public void onComplete() { completed.countDown(); }
            });
            try {
                if (!completed.await(1, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new AssertionError("request body was not published");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
            return result.get().toString();
        }
    }

    private record StubResponse(int statusCode, String body) implements HttpResponse<String> {
        @Override public HttpRequest request() { return null; }
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (name, value) -> true);
        }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return URI.create("https://challenges.cloudflare.com/turnstile/v0/siteverify"); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
