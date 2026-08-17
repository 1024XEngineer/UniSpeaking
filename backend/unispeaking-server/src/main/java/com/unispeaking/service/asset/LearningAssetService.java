package com.unispeaking.service.asset;

import com.unispeaking.domain.dto.asset.LearningAssetDetail;
import com.unispeaking.domain.dto.asset.LearningAssetSummary;
import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import java.util.List;

public interface LearningAssetService {

	/** Lists the learning assets owned by the current user. */
	List<LearningAssetSummary> listAssets();

	/** Returns the complete learning asset for a scene. */
	LearningAssetDetail getAsset(String sceneId);

	/** Removes a learning asset owned by the current user. */
	void deleteAsset(String sceneId);

	/** Returns the evaluation report for a scene session. */
	DialogueReportResult getReport(String sceneId, String sessionId);
}
