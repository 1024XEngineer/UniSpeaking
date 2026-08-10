package com.unispeaking.domain.vo.scene;

/**
 * 面试主题识别事件，由逐轮 LLM 主题识别产出。
 *
 * @param topic         识别出的主题名，或 {@code UNKNOWN}
 * @param topicCompleted 该轮作答是否完成当前主题
 */
public record InterviewTopicEvent(
		String topic,
		boolean topicCompleted) {

	/** 主题识别失败/未识别事件。 */
	public static InterviewTopicEvent unknown() {
		return new InterviewTopicEvent("UNKNOWN", false);
	}

	/** 空/空白转录事件：推进轮次但不计 UNKNOWN、不切题（no-op）。 */
	public static InterviewTopicEvent ignored() {
		return new InterviewTopicEvent("", false);
	}
}
