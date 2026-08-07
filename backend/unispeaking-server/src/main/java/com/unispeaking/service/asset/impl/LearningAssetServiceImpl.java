package com.unispeaking.service.asset.impl;

import com.unispeaking.domain.dto.asset.LearningAssetDetail;
import com.unispeaking.domain.dto.asset.LearningAssetSummary;
import com.unispeaking.domain.dto.asset.SessionEvaluationRecord;
import com.unispeaking.domain.dto.evaluation.DialogueEvaluationResult;
import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.domain.po.scene.SceneAssetSnapshot;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SessionEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.service.asset.LearningAssetService;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.evaluation.impl.CustomEvaluationServiceImpl;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class LearningAssetServiceImpl implements LearningAssetService {

	private final AuthService authService;
	private final SceneRepository sceneRepository;
	private final SessionEvaluationRepository evaluationRepository;
	private final CustomEvaluationServiceImpl evaluationService;

	public LearningAssetServiceImpl(
			AuthService authService,
			SceneRepository sceneRepository,
			SessionEvaluationRepository evaluationRepository,
			CustomEvaluationServiceImpl evaluationService) {
		this.authService = authService;
		this.sceneRepository = sceneRepository;
		this.evaluationRepository = evaluationRepository;
		this.evaluationService = evaluationService;
	}

	@Override
	public List<LearningAssetSummary> listAssets() {
		String userId = authService.requireUserId(null);
		return sceneRepository.findAssetsByUserId(userId).stream()
				.map(this::toSummary)
				.toList();
	}

	@Override
	public LearningAssetDetail getAsset(String sceneId) {
		CustomSceneDefinition scene = requireOwnedScene(sceneId);
		List<SessionEvaluationRecord> reports =
				evaluationRepository.findBySceneId(sceneId);
		SessionEvaluationRecord latest =
				reports.isEmpty() ? null : reports.getFirst();
		DialogueEvaluationResult dialogue = latest == null
				? new DialogueEvaluationResult(List.of(), List.of())
				: evaluationService.getDialogueEvaluation(latest.sessionId());
		return new LearningAssetDetail(
				scene.sceneId(),
				scene.title(),
				scene.background(),
				scene.aiRole(),
				scene.userRole(),
				scene.learningGoal(),
				scene.wordList(),
				scene.phraseList(),
				scene.sentenceList(),
				latest == null ? null : latest.sessionId(),
				dialogue,
				latest == null ? null : latest.report(),
				reports);
	}

	@Override
	public DialogueReportResult getReport(
			String sceneId,
			String sessionId) {
		requireOwnedScene(sceneId);
		SessionEvaluationRecord record = evaluationRepository
				.findRecord(sessionId)
				.filter(result -> sceneId.equals(result.sceneId()))
				.orElseThrow(() -> new BusinessException(
						"LEARNING_ASSET_REPORT_NOT_FOUND",
						"会话评分报告不存在"));
		return record.report();
	}

	private LearningAssetSummary toSummary(SceneAssetSnapshot snapshot) {
		CustomSceneDefinition scene = snapshot.definition();
		List<SessionEvaluationRecord> reports =
				evaluationRepository.findBySceneId(scene.sceneId());
		SessionEvaluationRecord latest =
				reports.isEmpty() ? null : reports.getFirst();
		return new LearningAssetSummary(
				scene.sceneId(),
				scene.title(),
				scene.background(),
				scene.wordList().size(),
				scene.phraseList().size(),
				scene.sentenceList().size(),
				latest == null ? null : latest.sessionId(),
				latest == null ? null : latest.report().finalScore(),
				latest == null ? null : latest.createdAt(),
				reports.size(),
				snapshot.createdAt());
	}

	private CustomSceneDefinition requireOwnedScene(String sceneId) {
		String userId = authService.requireUserId(null);
		CustomSceneDefinition scene = sceneRepository
				.findCustomDefinitionById(sceneId)
				.orElseThrow(() -> new BusinessException(
						"LEARNING_ASSET_NOT_FOUND",
						"学习资产不存在"));
		if (!userId.equals(scene.userId())) {
			throw new BusinessException(
					"LEARNING_ASSET_ACCESS_DENIED",
					"当前用户无权访问该学习资产");
		}
		return scene;
	}
}
