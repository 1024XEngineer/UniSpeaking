package com.unispeaking.service.scene;

import com.unispeaking.domain.dto.asset.InterviewAssetItem;
import com.unispeaking.domain.dto.scene.InterviewDialogueSceneContext;
import com.unispeaking.domain.dto.scene.InterviewMaterialDraft;
import com.unispeaking.domain.dto.scene.InterviewMaterialPreparationInput;
import com.unispeaking.domain.dto.scene.InterviewSceneRequest;
import com.unispeaking.domain.dto.scene.InterviewSceneResult;
import com.unispeaking.domain.vo.scene.InterviewTopicEvent;
import com.unispeaking.domain.vo.scene.InterviewTopicState;

/**
 * 面试场景服务（独立接口，不 extends 任何已删除的 SceneService 基类）。
 * <p>本刀提供 {@link #generate}、{@link #prepareMaterials}、{@link #advanceTopicState}、
 * {@link #listOwnedScenes}、{@link #isOcrAvailable} 与 {@link #deleteScene}。</p>
 */
public interface InterviewSceneService {

	/** 认证 + 校验材料 + LLM-2 生成 InterviewContext + 组装 Prompt + 落库，返回后续流程所需结果。 */
	InterviewSceneResult generate(InterviewSceneRequest request);

	/** 解析 JD/简历 → 脱敏一次 → LLM-1 结构化整理，返回可编辑材料草稿。 */
	InterviewMaterialDraft prepareMaterials(InterviewMaterialPreparationInput input);

	/** 会话启动用：内部完成归属校验并读取 scenePrompt/difficulty，不启动 Session。 */
	InterviewDialogueSceneContext prepareDialogue(String sceneId);

	/**
	 * 推进主题状态机（submitTurn 消费）。Impl 持有 {@code InterviewTopicStateMachine}，
	 * Session 只经本方法触碰状态机（DI 结构守卫）。
	 */
	InterviewTopicState advanceTopicState(
			String sceneId,
			String sessionId,
			int turnNo,
			InterviewTopicEvent event);

	/** 当前用户拥有的面试场景的候选主题列表（主题识别 LLM prompt 用）。 */
	java.util.List<String> interviewTopics(String sceneId);

	/** 当前用户拥有的面试场景资产摘要（场景快照 + 最近报告 + 复练次数），按更新时间倒序。 */
	java.util.List<InterviewAssetItem> listOwnedScenes();

	/** OCR 能力探测：委派当前装配的 {@code OcrProvider}。 */
	boolean isOcrAvailable();

	/**
	 * 后端删除：软删 {@code interview_scene}（deleted_at）+ 清该 scene 全部会话音频；
	 * practice_session/session_message/interview_report 保留（审计 + 学习日历）。
	 */
	void deleteScene(String sceneId);
}
