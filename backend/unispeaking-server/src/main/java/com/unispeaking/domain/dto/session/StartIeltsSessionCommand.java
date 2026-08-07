package com.unispeaking.domain.dto.session;

/** 启动 IELTS 会话所需的练习标识和实时对话参数。 */
public record StartIeltsSessionCommand(
		String ieltsId,
		StartIeltsDialogueRequest request) {
}
