package com.unispeaking.service.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.dto.asset.SessionEvaluationRecord;
import com.unispeaking.domain.dto.evaluation.DialogueEvaluationResult;
import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.domain.po.scene.SceneAssetSnapshot;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SessionEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.service.asset.impl.LearningAssetServiceImpl;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.evaluation.EvaluationService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LearningAssetServiceImplTest {

	@Test
	void loadsSceneContentLatestDialogueAndReportHistory() {
		String userId = "f3cc4bdf-8db1-48b4-b504-d31375e1eb68";
		String sceneId = "custom_2001";
		String sessionId = "scene_5001";
		LearningContentItem word =
				new LearningContentItem("word_1", "order", "点单", "/ˈɔːrdər/");
		CustomSceneDefinition scene = new CustomSceneDefinition(
				sceneId,
				userId,
				"咖啡店点单",
				"在咖啡店完成点单",
				"咖啡店店员",
				"顾客",
				"完成一杯咖啡的点单",
				"简短自然",
				"{\"stop_when\":\"order confirmed\"}",
				List.of(word),
				List.of(),
				List.of());
		DialogueReportResult report = new DialogueReportResult(
				new BigDecimal("84"),
				new BigDecimal("82"),
				new BigDecimal("86"),
				new BigDecimal("80"),
				new BigDecimal("83"),
				new BigDecimal("83"),
				"完成了点单",
				List.of("表达清楚"),
				List.of("增加礼貌表达"));
		OffsetDateTime completedAt = OffsetDateTime.now();
		SessionEvaluationRecord reportRecord = new SessionEvaluationRecord(
				sceneId,
				sessionId,
				report,
				completedAt);
		DialogueEvaluationResult dialogue = new DialogueEvaluationResult(
				List.of(new Message(1, "A coffee, please.", null)),
				List.of());

		AuthService authService = mock(AuthService.class);
		SceneRepository sceneRepository = mock(SceneRepository.class);
		SessionEvaluationRepository reportRepository =
				mock(SessionEvaluationRepository.class);
		EvaluationService evaluationService = mock(EvaluationService.class);
		when(authService.requireUserId(null)).thenReturn(userId);
		when(sceneRepository.findCustomDefinitionById(sceneId))
				.thenReturn(Optional.of(scene));
		when(sceneRepository.findAssetsByUserId(userId)).thenReturn(List.of(
				new SceneAssetSnapshot(scene, completedAt, completedAt)));
		when(reportRepository.findBySceneId(sceneId))
				.thenReturn(List.of(reportRecord));
		when(evaluationService.getDialogueEvaluation(sessionId))
				.thenReturn(dialogue);

		LearningAssetService service = new LearningAssetServiceImpl(
				authService,
				sceneRepository,
				reportRepository,
				evaluationService);

		assertEquals(1, service.listAssets().size());
		assertEquals(
				new BigDecimal("83"),
				service.listAssets().getFirst().latestScore());
		assertEquals(
				dialogue,
				service.getAsset(sceneId).dialogueEvaluation());
		assertEquals(
				report,
				service.getAsset(sceneId).latestReport());
	}
}
