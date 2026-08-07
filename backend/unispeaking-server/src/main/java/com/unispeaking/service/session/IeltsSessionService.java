package com.unispeaking.service.session;

import com.unispeaking.domain.dto.session.StartIeltsSessionResponse;
import com.unispeaking.domain.dto.session.StartIeltsSessionCommand;

/** IELTS 会话服务，继承通用会话生命周期能力。 */
public interface IeltsSessionService extends SessionService<
		StartIeltsSessionCommand,
		StartIeltsSessionResponse,
		String,
		Void> {

	/** 覆写通用启动方法，为当前 IELTS Part 启动实时对话会话。 */
	@Override
	StartIeltsSessionResponse startSession(StartIeltsSessionCommand command);

	/** 覆写通用结束方法，结束指定 IELTS 会话。 */
	@Override
	Void endSession(String sessionId);
}
