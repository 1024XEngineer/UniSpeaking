package com.unispeaking.service.scene.impl;

import com.unispeaking.domain.dto.scene.SceneGenerationRequest;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.domain.vo.scene.SceneConfig;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.exception.SceneNotFoundException;
import com.unispeaking.repository.SceneRepository;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.profile.ProfileService;
import com.unispeaking.service.prompt.FiveLayerPromptService;
import com.unispeaking.service.scene.SceneService;
import com.unispeaking.service.scene.CustomSceneGenerationService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SceneServiceImpl implements SceneService {

	private static final Logger LOGGER = LoggerFactory.getLogger(SceneServiceImpl.class);

	private final AuthService authService;
	private final ProfileService profileService;
	private final SceneRepository sceneRepository;
	private final FiveLayerPromptService promptService;
	private final CustomSceneGenerationService customSceneGenerationService;

	public SceneServiceImpl(
			AuthService authService,
			ProfileService profileService,
			SceneRepository sceneRepository,
			FiveLayerPromptService promptService,
			CustomSceneGenerationService customSceneGenerationService) {
		this.authService = authService;
		this.profileService = profileService;
		this.sceneRepository = sceneRepository;
		this.promptService = promptService;
		this.customSceneGenerationService = customSceneGenerationService;
	}

	@Override
	public SceneGenerationResponse generateScene(SceneGenerationRequest request) {
		String userId = authService.requireUserId(request.userId());
		SceneType sceneType = request.sceneType() == null ? SceneType.FREE_CHAT : request.sceneType();
		SceneConfig sceneConfig = sceneRepository.findByType(sceneType)
				.orElseThrow(() -> new SceneNotFoundException(sceneType.name()));
		UserProfile profile = profileService.getProfile(userId);
		String sceneInput = request.sceneInput() == null ? "" : request.sceneInput().trim();
		String sceneId = generateSceneId(sceneType);
		if (sceneType == SceneType.CUSTOM_SCENE) {
			return generateCustomScene(
					sceneId,
					userId,
					sceneInput,
					request.userPreference(),
					profile,
					sceneConfig);
		}
		List<LearningContentItem> wordList = buildWordList(sceneType, sceneInput);
		List<LearningContentItem> phraseList = buildPhraseList(sceneType, sceneInput);
		List<LearningContentItem> sentenceList = buildSentenceList(sceneType, sceneInput);
		String scenePrompt = String.join("\n\n", promptService.compose(
				profile,
				sceneConfig,
				sceneType,
				sceneInput,
				request.userPreference(),
				wordList,
				phraseList,
				sentenceList));
		SceneGenerationResponse response = new SceneGenerationResponse(
				sceneId,
				wordList,
				phraseList,
				sentenceList,
				scenePrompt);
		return sceneRepository.saveGenerated(response);
	}

	private SceneGenerationResponse generateCustomScene(
			String sceneId,
			String userId,
			String sceneInput,
			String userPreference,
			UserProfile profile,
			SceneConfig sceneConfig) {
		long totalStartedAt = System.nanoTime();
		long generationStartedAt = System.nanoTime();
		CustomSceneDefinition definition = customSceneGenerationService.generate(
				sceneId,
				userId,
				sceneInput,
				userPreference,
				profile);
		long generationMillis = elapsedMillis(generationStartedAt);
		long promptStartedAt = System.nanoTime();
		String scenePrompt = String.join("\n\n", promptService.compose(
				profile,
				sceneConfig,
				SceneType.CUSTOM_SCENE,
				sceneInput,
				userPreference,
				definition.wordList(),
				definition.phraseList(),
				definition.sentenceList(),
				definition));
		long promptMillis = elapsedMillis(promptStartedAt);
		SceneGenerationResponse response = new SceneGenerationResponse(
				sceneId,
				definition.wordList(),
				definition.phraseList(),
				definition.sentenceList(),
				scenePrompt);
		long cacheStartedAt = System.nanoTime();
		SceneGenerationResponse saved = sceneRepository.saveCustomScene(definition, response);
		LOGGER.info(
				"custom scene ready sceneId={} generationMs={} promptMs={} cacheAndScheduleMs={} totalMs={}",
				sceneId,
				generationMillis,
				promptMillis,
				elapsedMillis(cacheStartedAt),
				elapsedMillis(totalStartedAt));
		return saved;
	}

	private long elapsedMillis(long startedAt) {
		return (System.nanoTime() - startedAt) / 1_000_000;
	}

	private String generateSceneId(SceneType sceneType) {
		String randomPart = UUID.randomUUID().toString().replace("-", "");
		return sceneType.sceneIdPrefix() + "_" + randomPart;
	}

	private List<LearningContentItem> buildWordList(SceneType sceneType, String sceneInput) {
		if (sceneType == SceneType.FREE_CHAT) {
			return List.of();
		}
		List<LearningContentItem> items = new ArrayList<>();
		items.add(item("word", 1, focusWord(sceneInput), "核心话题词"));
		items.add(item("word", 2, "schedule", "时间安排"));
		items.add(item("word", 3, "recommendation", "推荐"));
		items.add(item("word", 4, "preference", "偏好"));
		return List.copyOf(items);
	}

	private List<LearningContentItem> buildPhraseList(SceneType sceneType, String sceneInput) {
		if (sceneType == SceneType.FREE_CHAT) {
			return List.of();
		}
		String topic = englishTopic(sceneInput);
		return List.of(
				item("phrase", 1, "Could you tell me more about " + topic + "?", "你能多介绍一下这个场景吗？"),
				item("phrase", 2, "I would like to ask about the options.", "我想了解有哪些选择。"),
				item("phrase", 3, "What would you recommend for me?", "你会给我什么建议？"));
	}

	private List<LearningContentItem> buildSentenceList(SceneType sceneType, String sceneInput) {
		if (sceneType == SceneType.FREE_CHAT) {
			return List.of();
		}
		String topic = englishTopic(sceneInput);
		return List.of(
				item("sentence", 1, "Hi, I would like to practice a conversation about " + topic + ".", "你好，我想练习关于这个场景的对话。"),
				item("sentence", 2, "Could you explain the details and help me choose?", "你能解释细节并帮我选择吗？"),
				item("sentence", 3, "That sounds helpful. What should I do next?", "听起来很有帮助。下一步我该怎么做？"));
	}

	private String focusWord(String sceneInput) {
		String input = sceneInput == null ? "" : sceneInput.toLowerCase();
		if (input.contains("gym") || input.contains("健身")) {
			return "membership";
		}
		if (input.contains("coffee") || input.contains("cafe") || input.contains("咖啡")) {
			return "recommendation";
		}
		if (input.contains("airport") || input.contains("机场")) {
			return "boarding";
		}
		if (input.contains("hotel") || input.contains("酒店")) {
			return "reservation";
		}
		if (input.contains("interview") || input.contains("面试")) {
			return "experience";
		}
		if (input.contains("restaurant") || input.contains("餐厅") || input.contains("饭店")) {
			return "order";
		}
		if (input.contains("doctor") || input.contains("hospital") || input.contains("医院")) {
			return "appointment";
		}
		return "conversation";
	}

	private String englishTopic(String sceneInput) {
		String topic = normalizedTopic(sceneInput);
		if (!topic.matches(".*[A-Za-z].*") || !topic.matches("^[\\p{ASCII}]+$")) {
			return "this situation";
		}
		return topic;
	}

	private String normalizedTopic(String sceneInput) {
		if (sceneInput == null || sceneInput.isBlank()) {
			return "this situation";
		}
		String topic = sceneInput.trim()
				.replaceAll("[\\r\\n\\t]+", " ")
				.replaceAll("\\s+", " ");
		if (topic.length() > 48) {
			return topic.substring(0, 48).trim();
		}
		return topic;
	}

	private LearningContentItem item(String prefix, int index, String englishText, String chineseText) {
		return new LearningContentItem(
				prefix + "_" + index,
				englishText,
				chineseText,
				"");
	}
}
