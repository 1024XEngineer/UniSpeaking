package com.unispeaking.service.session;

import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.StartIeltsSessionResponse;
import com.unispeaking.domain.dto.session.StartIeltsSessionCommand;

/** IELTS 会话服务，提供会话生命周期操作。 */
public interface IeltsSessionService {

	/** 为当前 IELTS Part 启动实时对话会话。 */
	StartIeltsSessionResponse startSession(StartIeltsSessionCommand command);

	/** 将一条消息保存到指定 IELTS 会话中。 */
	void addMessage(String sessionId, Message message);

	/** 结束指定 IELTS 会话。 */
	Void endSession(String sessionId);
}
