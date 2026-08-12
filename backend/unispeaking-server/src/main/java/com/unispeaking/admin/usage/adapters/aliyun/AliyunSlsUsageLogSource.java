package com.unispeaking.admin.usage.adapters.aliyun;

import com.aliyun.openservices.log.Client;
import com.aliyun.openservices.log.exception.LogException;
import com.aliyun.openservices.log.request.PullLogsRequest;
import com.unispeaking.admin.usage.application.UsageSourceUnavailableException;
import com.unispeaking.admin.usage.ports.OfficialUsageLogSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class AliyunSlsUsageLogSource implements OfficialUsageLogSource {
    private static final int PAGE_SIZE = 100;

    private final String project;
    private final String logstore;
    private final int maxRecords;
    private final RawLogClient client;
    private final boolean credentialsConfigured;

    @Autowired
    public AliyunSlsUsageLogSource(
            @Value("${unispeaking.integrations.aliyun.region:cn-beijing}") String region,
            @Value("${unispeaking.integrations.aliyun.project:}") String project,
            @Value("${unispeaking.integrations.aliyun.inference-logstore:bailian-model-inference-log}") String logstore,
            @Value("${unispeaking.integrations.aliyun.access-key-id:}") String accessKeyId,
            @Value("${unispeaking.integrations.aliyun.access-key-secret:}") String accessKeySecret,
            @Value("${unispeaking.integrations.aliyun.max-query-records:1000}") int maxRecords) {
        this(
                project,
                logstore,
                maxRecords,
                new SdkRawLogClient(new Client(region + ".log.aliyuncs.com", accessKeyId, accessKeySecret)),
                accessKeyId != null && !accessKeyId.isBlank()
                        && accessKeySecret != null && !accessKeySecret.isBlank());
    }

    AliyunSlsUsageLogSource(String project, String logstore, int maxRecords, RawLogClient client) {
        this(project, logstore, maxRecords, client, true);
    }

    private AliyunSlsUsageLogSource(
            String project,
            String logstore,
            int maxRecords,
            RawLogClient client,
            boolean credentialsConfigured) {
        this.project = project;
        this.logstore = logstore;
        this.maxRecords = Math.max(PAGE_SIZE, maxRecords);
        this.client = client;
        this.credentialsConfigured = credentialsConfigured;
    }

    @Override
    public List<String> loadLogs(Instant from, Instant to) {
        ensureConfigured();
        var result = new ArrayList<String>();
        for (int shardId : client.listShardIds(project, logstore)) {
            if (result.size() >= maxRecords) {
                break;
            }
            String cursor = client.cursor(project, logstore, shardId, from.getEpochSecond());
            String endCursor = client.cursor(project, logstore, shardId, to.getEpochSecond());
            while (result.size() < maxRecords) {
                int count = Math.min(PAGE_SIZE, maxRecords - result.size());
                RawPage page = client.pull(project, logstore, shardId, count, cursor, endCursor);
                for (String log : page.logs()) {
                    if (result.size() >= maxRecords) {
                        break;
                    }
                    result.add(log);
                }
                if (page.endOfCursor()
                        || page.nextCursor() == null
                        || page.nextCursor().isBlank()
                        || page.nextCursor().equals(cursor)) {
                    break;
                }
                cursor = page.nextCursor();
            }
        }
        return List.copyOf(result);
    }

    private void ensureConfigured() {
        if (project == null || project.isBlank() || logstore == null || logstore.isBlank()
                || !credentialsConfigured) {
            throw new UsageSourceUnavailableException("阿里云 SLS 凭据或日志库未配置", null);
        }
    }

    interface RawLogClient {
        List<Integer> listShardIds(String project, String logstore);

        String cursor(String project, String logstore, int shardId, long epochSeconds);

        RawPage pull(
                String project,
                String logstore,
                int shardId,
                int count,
                String cursor,
                String endCursor);
    }

    record RawPage(List<String> logs, String nextCursor, boolean endOfCursor) {
        RawPage {
            logs = List.copyOf(logs);
        }
    }

    private static final class SdkRawLogClient implements RawLogClient {
        private final Client client;

        private SdkRawLogClient(Client client) {
            this.client = client;
        }

        @Override
        public List<Integer> listShardIds(String project, String logstore) {
            try {
                return client.ListShard(project, logstore).GetShards().stream()
                        .map(shard -> shard.getShardId())
                        .toList();
            } catch (LogException exception) {
                throw unavailable("列举分片", exception);
            }
        }

        @Override
        public String cursor(String project, String logstore, int shardId, long epochSeconds) {
            try {
                return client.GetCursor(project, logstore, shardId, epochSeconds).GetCursor();
            } catch (LogException exception) {
                throw unavailable("获取分片游标", exception);
            }
        }

        @Override
        public RawPage pull(
                String project,
                String logstore,
                int shardId,
                int count,
                String cursor,
                String endCursor) {
            try {
                var response = client.pullLogs(
                        new PullLogsRequest(project, logstore, shardId, count, cursor, endCursor));
                var logs = new ArrayList<String>();
                for (var group : response.getLogGroups()) {
                    for (var log : group.GetAllLogs()) {
                        logs.add(log.ToJsonString());
                    }
                }
                return new RawPage(logs, response.getNextCursor(), response.isEndOfCursor());
            } catch (LogException exception) {
                throw unavailable("读取分片日志", exception);
            }
        }

        private static UsageSourceUnavailableException unavailable(String operation, LogException exception) {
            return new UsageSourceUnavailableException(
                    "阿里云 SLS " + operation + "失败：" + exception.GetErrorCode(),
                    exception);
        }
    }
}
