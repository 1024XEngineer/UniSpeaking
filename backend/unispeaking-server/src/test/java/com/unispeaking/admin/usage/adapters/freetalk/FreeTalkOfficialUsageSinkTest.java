package com.unispeaking.admin.usage.adapters.freetalk;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import com.unispeaking.admin.usage.domain.ModelUsage;
import com.unispeaking.admin.usage.domain.OfficialUsageRecord;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class FreeTalkOfficialUsageSinkTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postsNormalizedOfficialUsageWithInputAndOutputDetails() throws Exception {
        var received = new AtomicReference<String>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/admin/usage/provider-records/import", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var response = "{\"imported\":1,\"duplicates\":0,\"matched\":1,\"unmatched\":0}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        var sink = new FreeTalkOfficialUsageSink(
                RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort());
        var record = new OfficialUsageRecord(
                "request-01", "sess_local_01", 1784534676105L, 50962L, "200",
                "qwen3.5-omni-flash-realtime", "ws-local", "6124876", "ws",
                new ModelUsage(1, 13289, 12938, 351, 12840, 98, 85, 266));

        var result = sink.importRecords(List.of(record));

        assertThat(result.matched()).isEqualTo(1);
        assertThat(received.get())
                .contains("\"request_id\":\"request-01\"")
                .contains("\"task_uuid\":\"sess_local_01\"")
                .contains("\"input_tokens\":12938")
                .contains("\"output_tokens\":351")
                .contains("\"text_tokens\":12840")
                .contains("\"audio_tokens\":266");
    }
}
