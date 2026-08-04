package com.unispeaking.domain.dto.evaluation;

/**
 * 发起通用语音评分所需的无会话命令。
 *
 * @param referenceText 发音评测使用的参考文本
 * @param audio 完整的 PCM WAV 音频
 */
public record SpeechEvaluationCommand(
		String referenceText,
		byte[] audio) {

	/**
	 * 复制可变音频，避免调用方在评分期间改变命令内容。
	 */
	public SpeechEvaluationCommand {
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
