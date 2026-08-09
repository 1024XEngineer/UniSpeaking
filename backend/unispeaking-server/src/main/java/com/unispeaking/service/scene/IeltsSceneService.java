package com.unispeaking.service.scene;

import com.unispeaking.domain.dto.scene.IeltsDialogueSceneContext;
import com.unispeaking.domain.dto.scene.IeltsGenerationRequest;
import com.unispeaking.domain.dto.scene.IeltsGenerationResponse;
import com.unispeaking.domain.dto.scene.IeltsSettingsResponse;
import com.unispeaking.domain.dto.scene.IeltsTopicSearchResponse;
import com.unispeaking.domain.dto.scene.IeltsTrainingResponse;
import com.unispeaking.domain.dto.scene.UpdateIeltsSettingsRequest;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.IeltsStage;

/** IELTS 场景服务，负责练习生成、话题查询、提示词和用户设置。 */
public interface IeltsSceneService {

	/** 生成并持久化一个 IELTS 练习，返回生成结果。 */
	IeltsGenerationResponse generate(IeltsGenerationRequest request);

	/** 准备当前用户拥有的 IELTS 当前 Part 以及对话所需提示词。 */
	IeltsDialogueSceneContext prepareDialogue(String ieltsId, String voiceId);

	/** 完成当前对话，并将 IELTS 流程推进到下一阶段。 */
	IeltsStage completeDialogue(String ieltsId, String userId);

	/** 按 Part、分类、关键词和分页条件搜索 IELTS 话题。 */
	IeltsTopicSearchResponse searchTopics(
			IeltsPart part,
			String category,
			String keyword,
			int page,
			int pageSize);

	/** 预览指定 Part 和话题最终选中的题目。 */
	IeltsTrainingResponse prepareTraining(IeltsPart part, String topicId);

	/** 为指定 IELTS Part 构造考官对话提示词。 */
	String buildDialoguePrompt(String ieltsId, IeltsPart part);

	/** 获取当前用户的 IELTS 设置。 */
	IeltsSettingsResponse getSettings();

	/** 更新并返回当前用户的 IELTS 设置。 */
	IeltsSettingsResponse updateSettings(UpdateIeltsSettingsRequest request);
}
