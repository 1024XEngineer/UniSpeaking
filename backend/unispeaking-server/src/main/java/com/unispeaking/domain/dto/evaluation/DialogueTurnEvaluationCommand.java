package com.unispeaking.domain.dto.evaluation;

import java.util.Objects;

/**
 * 发起单轮对话评分所需的命令。
 *
 * @param sessionId 练习会话标识
 * @param turnNo 用户气泡在会话中的顺序号，从 1 开始
 * @param audio 用户回答的完整 WAV 音频；过短回答允许为空
 * @param transcript 用户回答的原始转写文本
 */
public record DialogueTurnEvaluationCommand(
		String sessionId,
		Integer turnNo,
		byte[] audio,
		String transcript) {

	/**
	 * 复制可变音频，避免调用方在评分期间改变命令内容。
	 */
	public DialogueTurnEvaluationCommand {
		sessionId = Objects.requireNonNull(
				sessionId,
				"sessionId must not be null");
		turnNo = Objects.requireNonNull(turnNo, "turnNo must not be null");
		transcript = Objects.requireNonNull(
				transcript,
				"transcript must not be null");
		audio = copyAudio(audio);
	}

	/**
	 * 返回音频副本，避免调用方通过 record 访问器修改命令内部数据。
	 */
	@Override
	public byte[] audio() {
		return copyAudio(audio);
	}

	private static byte[] copyAudio(byte[] audio) {
		return audio == null ? null : audio.clone();
	}
}
