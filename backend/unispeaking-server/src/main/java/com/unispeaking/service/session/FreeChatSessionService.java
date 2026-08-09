package com.unispeaking.service.session;

import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.StartFreeChatRequest;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;

/** 自由对话会话服务，提供会话生命周期操作。 */
public interface FreeChatSessionService {

	/** 为已经准备好的自由对话场景启动一个实时会话。 */
	StartSceneSessionResponse startSession(StartFreeChatRequest request);

	/** 将一条消息保存到指定自由对话会话中。 */
	void addMessage(String sessionId, Message message);

	/** 结束指定自由对话会话。 */
	Void endSession(String sessionId);
}
