package com.unispeaking.service.scene;

import com.unispeaking.domain.dto.scene.FreeChatSceneContext;
import com.unispeaking.domain.dto.scene.FreeChatSceneRequest;
import com.unispeaking.domain.dto.scene.FreeChatSceneResult;
import com.unispeaking.domain.dto.scene.TranslateTextResponse;

/** 自由对话场景服务，提供自由对话专属操作。 */
public interface FreeChatSceneService {

	/** 生成并持久化一个自由对话场景，返回场景结果。 */
	FreeChatSceneResult generate(FreeChatSceneRequest request);

	/** 根据请求准备当前用户的自由对话场景上下文。 */
	FreeChatSceneContext prepare(FreeChatSceneRequest request);

	/** 为当前用户翻译自由对话中的文本。 */
	TranslateTextResponse translate(String text);
}
