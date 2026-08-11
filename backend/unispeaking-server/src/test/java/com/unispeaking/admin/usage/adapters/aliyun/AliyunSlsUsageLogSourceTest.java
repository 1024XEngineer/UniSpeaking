package com.unispeaking.admin.usage.adapters.aliyun;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AliyunSlsUsageLogSourceTest {
    @Test
    void readsInferenceLogsThroughRawShardCursorsWithoutIndexSearch() {
        var client = new AliyunSlsUsageLogSource.RawLogClient() {
            @Override
            public List<Integer> listShardIds(String project, String logstore) {
                assertThat(project).isEqualTo("test-project");
                assertThat(logstore).isEqualTo("bailian-model-inference-log");
                return List.of(0, 1);
            }

            @Override
            public String cursor(String project, String logstore, int shardId, long epochSeconds) {
                return epochSeconds + "-" + shardId;
            }

            @Override
            public AliyunSlsUsageLogSource.RawPage pull(
                    String project,
                    String logstore,
                    int shardId,
                    int count,
                    String cursor,
                    String endCursor) {
                return new AliyunSlsUsageLogSource.RawPage(
                        List.of("{\"shard\":" + shardId + "}"),
                        endCursor,
                        true);
            }
        };

        var source = new AliyunSlsUsageLogSource(
                "test-project",
                "bailian-model-inference-log",
                100,
                client);

        assertThat(source.loadLogs(Instant.ofEpochSecond(100), Instant.ofEpochSecond(200)))
                .containsExactly("{\"shard\":0}", "{\"shard\":1}");
    }
}
