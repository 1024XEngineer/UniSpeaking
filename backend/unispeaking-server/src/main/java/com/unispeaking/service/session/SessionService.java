package com.unispeaking.service.session;

import com.unispeaking.domain.dto.session.Message;

/**
 * Scene-neutral session lifecycle contract.
 */
public interface SessionService<
		START_REQUEST,
		START_RESPONSE,
		END_REQUEST,
		END_RESPONSE> {

	/** 为已经准备好的场景启动一个会话。 */
	START_RESPONSE startSession(START_REQUEST request);

	/** 将一条消息保存到指定会话中。 */
	void addMessage(String sessionId, Message message);

	/** 结束指定会话并释放其生命周期资源。 */
	END_RESPONSE endSession(END_REQUEST request);
}
