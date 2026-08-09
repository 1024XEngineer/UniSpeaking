package com.unispeaking.service.session;

import com.unispeaking.domain.dto.session.CompleteCustomSceneDialogueResponse;
import com.unispeaking.domain.dto.session.EndCustomSessionCommand;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;
import com.unispeaking.domain.dto.session.StartCustomSessionCommand;

/** 自定义场景会话服务，提供会话生命周期操作。 */
public interface CustomSessionService {

	/** 为当前用户拥有的自定义场景启动实时对话。 */
	StartSceneSessionResponse startSession(StartCustomSessionCommand command);

	/** 将一条消息保存到指定自定义场景会话中。 */
	void addMessage(String sessionId, Message message);

	/** 结束自定义对话并返回本次评价结果。 */
	CompleteCustomSceneDialogueResponse endSession(
			EndCustomSessionCommand command);
}
