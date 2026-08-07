package com.unispeaking.service.session;

import com.unispeaking.domain.dto.session.StartFreeChatRequest;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;

/** 自由对话会话服务，继承通用会话生命周期能力。 */
public interface FreeChatSessionService extends SessionService<
		StartFreeChatRequest,
		StartSceneSessionResponse,
		String,
		Void> {

	/** 覆写通用启动方法，启动一个自由对话实时会话。 */
	@Override
	StartSceneSessionResponse startSession(StartFreeChatRequest request);

	/** 覆写通用结束方法，结束指定自由对话会话。 */
	@Override
	Void endSession(String sessionId);
}
