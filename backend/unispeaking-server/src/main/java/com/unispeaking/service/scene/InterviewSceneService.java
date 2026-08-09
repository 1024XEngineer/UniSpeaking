package com.unispeaking.service.scene;

import com.unispeaking.domain.dto.scene.InterviewDialogueSceneContext;
import com.unispeaking.domain.dto.scene.InterviewMaterialDraft;
import com.unispeaking.domain.dto.scene.InterviewMaterialPreparationInput;
import com.unispeaking.domain.dto.scene.InterviewSceneRequest;
import com.unispeaking.domain.dto.scene.InterviewSceneResult;

/**
 * 面试场景服务（独立接口，不 extends 任何已删除的 SceneService 基类）。
 * <p>本刀提供 {@link #generate} 与 {@link #prepareMaterials}；
 * {@code listOwnedScenes}/{@code advanceTopicState}/{@code deleteScene} 留待后续。
 */
public interface InterviewSceneService {

	/** 认证 + 校验材料 + LLM-2 生成 InterviewContext + 组装 Prompt + 落库，返回后续流程所需结果。 */
	InterviewSceneResult generate(InterviewSceneRequest request);

	/** 解析 JD/简历 → 脱敏一次 → LLM-1 结构化整理，返回可编辑材料草稿。 */
	InterviewMaterialDraft prepareMaterials(InterviewMaterialPreparationInput input);

	/** 会话启动用：内部完成归属校验并读取 scenePrompt/difficulty，不启动 Session。 */
	InterviewDialogueSceneContext prepareDialogue(String sceneId);
}
