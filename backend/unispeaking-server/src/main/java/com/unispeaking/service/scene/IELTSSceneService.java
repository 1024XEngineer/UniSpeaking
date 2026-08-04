package com.unispeaking.service.scene;

import com.unispeaking.domain.dto.scene.IeltsTopicSearchResponse;
import com.unispeaking.domain.dto.scene.IeltsTrainingResponse;
import com.unispeaking.domain.vo.scene.IeltsPart;

public interface IELTSSceneService {

	IeltsTopicSearchResponse searchTopics(
			IeltsPart part,
			String category,
			String keyword,
			int page,
			int pageSize);

	IeltsTrainingResponse prepareTraining(
			IeltsPart part,
			String topicId);
}
