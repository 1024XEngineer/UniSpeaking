package com.unispeaking.domain.po.session;

/**
 * 用户轮次消息 + 音频对象键的 turn-aware 读模型（报告任务用）。
 * 禁止按位置 zip {@code findLearnerMessages} 与 {@code findAudioObjectKeys}（缺音频轮会错位）。
 */
public record LearnerMessageRecord(
		int messageNo,
		String content,
		String audioObjectKey) {
}
