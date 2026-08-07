package com.unispeaking.service.session;

import com.unispeaking.domain.dto.session.CompleteCustomSceneDialogueResponse;
import com.unispeaking.domain.dto.session.EndCustomSessionCommand;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;
import com.unispeaking.domain.dto.session.StartCustomSessionCommand;

/** 自定义场景会话服务，继承通用会话生命周期能力。 */
public interface CustomSessionService extends SessionService<
		StartCustomSessionCommand,
		StartSceneSessionResponse,
		EndCustomSessionCommand,
		CompleteCustomSceneDialogueResponse> {

	/** 覆写通用启动方法，为当前用户拥有的自定义场景启动实时对话。 */
	@Override
	StartSceneSessionResponse startSession(StartCustomSessionCommand command);

	/** 覆写通用结束方法，结束自定义对话并返回本次评价结果。 */
	@Override
	CompleteCustomSceneDialogueResponse endSession(
			EndCustomSessionCommand command);
}
