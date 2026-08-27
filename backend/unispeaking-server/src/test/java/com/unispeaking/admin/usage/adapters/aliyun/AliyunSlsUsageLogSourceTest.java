package com.unispeaking.admin.usage.adapters.aliyun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aliyun.openservices.log.Client;
import com.aliyun.openservices.log.common.LogGroupData;
import com.aliyun.openservices.log.common.LogItem;
import com.aliyun.openservices.log.common.Shard;
import com.aliyun.openservices.log.exception.LogException;
import com.aliyun.openservices.log.request.PullLogsRequest;
import com.aliyun.openservices.log.response.GetCursorResponse;
import com.aliyun.openservices.log.response.ListShardResponse;
import com.aliyun.openservices.log.response.PullLogsResponse;
import com.unispeaking.admin.usage.application.UsageSourceUnavailableException;
import java.time.Instant;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AliyunSlsUsageLogSourceTest {
    @Test
    void readsOfficialUsageLogsThroughRawShardCursorsWithoutIndexSearch() {
        var client = new AliyunSlsUsageLogSource.RawLogClient() {
            @Override
            public List<Integer> listShardIds(String project, String logstore) {
                assertThat(project).isEqualTo("test-project");
                assertThat(logstore).isEqualTo("bailian-model-audit-log");
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
                "bailian-model-audit-log",
                100,
                client);

        assertThat(source.loadLogs(Instant.ofEpochSecond(100), Instant.ofEpochSecond(200)))
                .containsExactly("{\"shard\":0}", "{\"shard\":1}");
    }

    @Test
    void sdkClientListsShardsAndReadsCursors() throws Exception {
        Client sdk = mock(Client.class);
        when(sdk.ListShard("project", "store"))
                .thenReturn(new ListShardResponse(Map.of(), new ArrayList<>(List.of(
                        new Shard(3, "", "", "", 0),
                        new Shard(7, "", "", "", 0)))));
        when(sdk.GetCursor("project", "store", 3, 100L))
                .thenReturn(new GetCursorResponse(Map.of(), "cursor-100"));
        var client = sdkRawLogClient(sdk);

        assertThat(client.listShardIds("project", "store")).containsExactly(3, 7);
        assertThat(client.cursor("project", "store", 3, 100L)).isEqualTo("cursor-100");
    }

    @Test
    void sdkClientPullsAllLogItemsAndCarriesPageMetadata() throws Exception {
        Client sdk = mock(Client.class);
        PullLogsResponse response = mock(PullLogsResponse.class);
        LogItem first = new LogItem();
        first.PushBack("message", "first");
        LogItem second = new LogItem();
        second.PushBack("message", "second");
        LogGroupData group = mock(LogGroupData.class);
        when(group.GetAllLogs()).thenReturn(new ArrayList<>(List.of(first, second)));
        when(response.getLogGroups()).thenReturn(List.of(group));
        when(response.getNextCursor()).thenReturn("next-cursor");
        when(response.isEndOfCursor()).thenReturn(false);
        when(sdk.pullLogs(any(PullLogsRequest.class))).thenReturn(response);
        var client = sdkRawLogClient(sdk);

        var page = client.pull("project", "store", 2, 100, "begin", "end");

        assertThat(page.logs()).containsExactly(first.ToJsonString(), second.ToJsonString());
        assertThat(page.nextCursor()).isEqualTo("next-cursor");
        assertThat(page.endOfCursor()).isFalse();
    }

    @Test
    void sdkClientTranslatesListShardFailure() throws Exception {
        Client sdk = mock(Client.class);
        when(sdk.ListShard("project", "store"))
                .thenThrow(new LogException("ListShardError", "failed", "request"));

        assertUnavailable(() -> sdkRawLogClient(sdk).listShardIds("project", "store"), "列举分片", "ListShardError");
    }

    @Test
    void sdkClientTranslatesCursorFailure() throws Exception {
        Client sdk = mock(Client.class);
        when(sdk.GetCursor("project", "store", 2, 100L))
                .thenThrow(new LogException("CursorError", "failed", "request"));

        assertUnavailable(() -> sdkRawLogClient(sdk).cursor("project", "store", 2, 100L), "获取分片游标", "CursorError");
    }

    @Test
    void sdkClientTranslatesPullFailure() throws Exception {
        Client sdk = mock(Client.class);
        when(sdk.pullLogs(any(PullLogsRequest.class)))
                .thenThrow(new LogException("PullError", "failed", "request"));

        assertUnavailable(
                () -> sdkRawLogClient(sdk).pull("project", "store", 2, 100, "begin", "end"),
                "读取分片日志", "PullError");
    }

    private static AliyunSlsUsageLogSource.RawLogClient sdkRawLogClient(Client sdk) throws Exception {
        Class<?> type = Class.forName(
                "com.unispeaking.admin.usage.adapters.aliyun.AliyunSlsUsageLogSource$SdkRawLogClient");
        Constructor<?> constructor = type.getDeclaredConstructor(Client.class);
        constructor.setAccessible(true);
        return (AliyunSlsUsageLogSource.RawLogClient) constructor.newInstance(sdk);
    }

    private static void assertUnavailable(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            String operation,
            String errorCode) {
        assertThatThrownBy(action)
                .isInstanceOf(UsageSourceUnavailableException.class)
                .hasMessageContaining("阿里云 SLS " + operation + "失败：" + errorCode);
    }
}
