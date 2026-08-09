package com.unispeaking.infrastructure.persistence.repository.evaluation;

import com.unispeaking.domain.po.evaluation.InterviewReportRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** 面试报告仓储：Service/报告任务访问 interview_report 的唯一入口。 */
public interface InterviewReportRepository {

	/**
	 * INSERT 一个 PROCESSING 报告行；已存在（PK 冲突）时返回 {@code false}。
	 * 仅当返回 {@code true} 才提交报告任务（创建者门禁，防双任务）。
	 */
	boolean createIfAbsent(String sessionId, String sceneId, String userId);

	/** 按会话标识读回报告行（可能尚未创建）。 */
	Optional<InterviewReportRecord> findById(String sessionId);

	/** 仅当当前仍为 PROCESSING 时写入 COMPLETED 结果，避免 COMPLETED/FAILED 状态回归。 */
	void markCompleted(InterviewReportRecord completed);

	/** 仅当当前仍为 PROCESSING 时置 FAILED + 受控 failure_reason。 */
	void markFailed(String sessionId, String failureReason);

	/** 自动重试：FAILED ∧ retry_count == expectedRetryCount → PROCESSING 且 retry_count+1。 */
	boolean retryFromFailed(String sessionId, int expectedRetryCount);

	/** 手动重试：FAILED → PROCESSING（幂等 CAS，不增加 retry_count）。 */
	boolean casFailedToProcessing(String sessionId);

	/** 清扫用：查询 updated_at 早于 cutoff 的滞留 PROCESSING 行。 */
	List<InterviewReportRecord> findStuckProcessingBefore(OffsetDateTime cutoff);

	/**
	 * 按场景查询全部报告，{@code created_at} 倒序（最近在前，首条即最近报告）；
	 * 空 sceneId 返回空列表。
	 */
	List<InterviewReportRecord> findBySceneId(String sceneId);
}
