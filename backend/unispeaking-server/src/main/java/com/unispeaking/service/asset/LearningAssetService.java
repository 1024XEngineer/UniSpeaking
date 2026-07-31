package com.unispeaking.service.asset;

import com.unispeaking.domain.dto.asset.LearningAssetDetail;
import com.unispeaking.domain.dto.asset.LearningAssetSummary;
import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import java.util.List;

public interface LearningAssetService {

	List<LearningAssetSummary> listAssets();

	LearningAssetDetail getAsset(String sceneId);

	DialogueReportResult getReport(String sceneId, String sessionId);
}
