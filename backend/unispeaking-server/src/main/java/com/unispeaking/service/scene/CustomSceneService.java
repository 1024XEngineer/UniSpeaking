package com.unispeaking.service.scene;

import com.unispeaking.domain.dto.scene.CustomDialogueSceneContext;
import com.unispeaking.domain.dto.scene.CustomSceneGenerationResponse;
import com.unispeaking.domain.dto.scene.CustomSceneRequest;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.dto.scene.TranslateTextResponse;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;

/** 自定义场景服务，继承通用场景生成能力并提供自定义场景专属操作。 */
public interface CustomSceneService
		extends SceneService<CustomSceneRequest, CustomSceneGenerationResponse> {

	/** 覆写通用场景生成方法，返回自定义场景生成结果。 */
	@Override
	CustomSceneGenerationResponse generate(CustomSceneRequest request);

	/** 将指定文本合成为语音，且只允许访问当前用户拥有的场景。 */
	byte[] synthesizeSpeech(String sceneId, String text, String model);

	/** 在当前用户拥有的自定义场景中翻译文本。 */
	TranslateTextResponse translate(String sceneId, String text);

	/** 获取当前用户拥有的自定义场景定义，无权限时抛出异常。 */
	CustomSceneDefinition getOwnedDefinition(String sceneId);

	/** 获取自定义场景已经生成并保存的学习内容。 */
	SceneGenerationResponse getGeneratedScene(String sceneId);

	/** 获取为自定义场景准备好的对话提示词。 */
	String getDialoguePrompt(String sceneId);

	/** 组装启动自定义场景对话所需的不可变上下文。 */
	CustomDialogueSceneContext prepareDialogue(String sceneId);
}
