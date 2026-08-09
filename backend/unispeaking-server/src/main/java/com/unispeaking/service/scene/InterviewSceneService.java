package com.unispeaking.service.scene;

import com.unispeaking.domain.dto.scene.InterviewSceneRequest;
import com.unispeaking.domain.dto.scene.InterviewSceneResult;

/**
 * 面试场景服务（独立接口，不 extends 任何已删除的 SceneService 基类）。
 * <p>本刀只提供 {@link #generate}；{@code listOwnedScenes}/{@code prepareMaterials}/
 * {@code prepareDialogue}/{@code advanceTopicState}/{@code deleteScene} 留待后续。
 */
public interface InterviewSceneService {

	/** 认证 + 校验材料 + LLM-2 生成 InterviewContext + 组装 Prompt + 落库，返回后续流程所需结果。 */
	InterviewSceneResult generate(InterviewSceneRequest request);
}
