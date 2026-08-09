package com.unispeaking.domain.vo.scene;

/**
 * 面试主题状态机对外状态快照。
 *
 * @param currentTopic            当前主题（尚未识别时为 {@code null}）
 * @param completedTopicCount     已完成主题数（仅 LLM 明确 {@code topicCompleted=true} 计入）
 * @param coveredTopicCount       已覆盖主题数（识别/问到的不同主题，驱动展示与终止前置）
 * @param unknownStreak           连续未识别主题次数（≥3 触发结束）
 * @param followUpCount           当前主题追问次数（受难度上限约束）
 * @param mandatoryTopicsCompleted 两个必选主题（自我介绍/经历项目）是否均已完成
 * @param shouldEnd               状态机是否判定面试结束
 */
public record InterviewTopicState(
		String currentTopic,
		int completedTopicCount,
		int coveredTopicCount,
		int unknownStreak,
		int followUpCount,
		boolean mandatoryTopicsCompleted,
		boolean shouldEnd) {
}
