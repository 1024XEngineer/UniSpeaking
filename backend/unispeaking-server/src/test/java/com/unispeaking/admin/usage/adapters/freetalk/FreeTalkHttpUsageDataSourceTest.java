package com.unispeaking.admin.usage.adapters.freetalk;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class FreeTalkHttpUsageDataSourceTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void mapsUsersSessionsAndOfficialReconciliationFields() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/admin/usage/users", exchange -> {
            var body = """
                    {
                      "users": [{
                        "user_id": "user-01",
                        "display_name": "User 01",
                        "plan_code": "free",
                        "plan_name": "Free",
                        "quota_date": "2026-07-20",
                        "quota_seconds": 180,
                        "settled_seconds": 81.215,
                        "active_elapsed_seconds": 0,
                        "used_seconds": 81.215,
                        "remaining_seconds": 98.785,
                        "reset_at": 1784563200,
                        "active_session_id": null,
                        "session_count": 1,
                        "sessions": [{
                          "session_id": "local-session-1",
                          "user_id": "user-01",
                          "plan_code": "free",
                          "status": "ended",
                          "measured_seconds": 81.215,
                          "remaining_seconds": 98.785,
                          "temporary_key_id": "temp-key-id",
                          "temporary_key_expires_at": 1784092500,
                          "task_uuid": "sess_provider_1",
                          "provider_request_id": "request-1",
                          "model_usage": {"response_count": 1, "total_tokens": 20780, "input_tokens": 20040, "output_tokens": 740, "input_text_tokens": 19550, "input_audio_tokens": 490, "output_text_tokens": 176, "output_audio_tokens": 564},
                          "official_usage": {"response_count": 1, "total_tokens": 20786, "input_tokens": 20043, "output_tokens": 743, "input_text_tokens": 19553, "input_audio_tokens": 490, "output_text_tokens": 176, "output_audio_tokens": 567},
                          "official_duration_ms": 81215,
                          "estimated_cost_cny": "0.026500",
                          "pricing_status": "priced",
                          "reconciliation_status": "MISMATCH",
                          "reconciliation_reasons": ["client_official_tokens_differ"],
                          "end_reason": "user_end"
                        }],
                        "model_usage": {"response_count": 1, "total_tokens": 20780, "input_tokens": 20040, "output_tokens": 740, "input_text_tokens": 19550, "input_audio_tokens": 490, "output_text_tokens": 176, "output_audio_tokens": 564},
                        "official_usage": {"response_count": 1, "total_tokens": 20786, "input_tokens": 20043, "output_tokens": 743, "input_text_tokens": 19553, "input_audio_tokens": 490, "output_text_tokens": 176, "output_audio_tokens": 567},
                        "estimated_cost_cny": "0.026500",
                        "reconciliation_counts": {"PENDING": 0, "MATCHED": 0, "MISMATCH": 1}
                      }],
                      "vendor_total": null,
                      "provider": {"record_count": 1, "unmatched_records": []}
                    }
                    """;
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        var source = new FreeTalkHttpUsageDataSource(
                RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort());

        var snapshot = source.loadSnapshot();

        assertThat(snapshot.users()).hasSize(1);
        var user = snapshot.users().getFirst();
        assertThat(user.userId()).isEqualTo("user-01");
        assertThat(user.remainingSeconds()).isEqualTo(98.785);
        assertThat(user.officialUsage().totalTokens()).isEqualTo(20786);
        var session = user.sessions().getFirst();
        assertThat(session.taskUuid()).isEqualTo("sess_provider_1");
        assertThat(session.providerRequestId()).isEqualTo("request-1");
        assertThat(session.temporaryKeyId()).isEqualTo("temp-key-id");
        assertThat(session.reconciliationStatus()).isEqualTo("MISMATCH");
        assertThat(session.reconciliationReasons()).containsExactly("client_official_tokens_differ");
    }
}
