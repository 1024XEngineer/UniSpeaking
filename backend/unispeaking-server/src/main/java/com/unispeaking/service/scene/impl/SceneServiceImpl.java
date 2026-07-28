package com.unispeaking.service.scene.impl;

import com.unispeaking.domain.dto.scene.SceneGenerationRequest;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.vo.prompt.CustomScenePromptContext;
import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.domain.vo.prompt.FreeChatPromptContext;
import com.unispeaking.domain.vo.prompt.SessionPrompt;
import com.unispeaking.domain.vo.scene.SceneConfig;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.exception.SceneNotFoundException;
import com.unispeaking.repository.SceneRepository;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.profile.ProfileService;
import com.unispeaking.service.prompt.CustomScenePromptService;
import com.unispeaking.service.prompt.FreeChatPromptService;
import com.unispeaking.service.scene.SceneService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SceneServiceImpl implements SceneService {

	private final AuthService authService;
	private final ProfileService profileService;
	private final SceneRepository sceneRepository;
	private final FreeChatPromptService freeChatPromptService;
	private final CustomScenePromptService customScenePromptService;

	public SceneServiceImpl(
			AuthService authService,
			ProfileService profileService,
			SceneRepository sceneRepository,
			FreeChatPromptService freeChatPromptService,
			CustomScenePromptService customScenePromptService) {
		this.authService = authService;
		this.profileService = profileService;
		this.sceneRepository = sceneRepository;
		this.freeChatPromptService = freeChatPromptService;
		this.customScenePromptService = customScenePromptService;
	}

	@Override
	public SceneGenerationResponse generateScene(SceneGenerationRequest request) {
		String userId = authService.requireUserId(request.userId());
		SceneType sceneType = request.sceneType() == null ? SceneType.FREE_CHAT : request.sceneType();
		SceneConfig sceneConfig = sceneRepository.findByType(sceneType)
				.orElseThrow(() -> new SceneNotFoundException(sceneType.name()));
		UserProfile profile = profileService.getProfile(userId);
		String sceneInput = request.sceneInput() == null ? "" : request.sceneInput().trim();
		String sceneName = buildSceneName(sceneType, sceneInput);
		List<LearningContentItem> wordList = buildWordList(sceneType, sceneInput);
		List<LearningContentItem> phraseList = buildPhraseList(sceneType, sceneInput);
		List<LearningContentItem> sentenceList = buildSentenceList(sceneType, sceneInput);
		String scenePrompt = buildCompletePrompt(
				sceneType,
				profile,
				sceneConfig,
				sceneInput,
				request.userPreference(),
				wordList,
				phraseList,
				sentenceList);
		return new SceneGenerationResponse(
				"scene_" + UUID.randomUUID(),
				sceneName,
				sceneType,
				wordList,
				phraseList,
				sentenceList,
				scenePrompt);
	}

	private String buildCompletePrompt(
			SceneType sceneType,
			UserProfile profile,
			SceneConfig sceneConfig,
			String sceneInput,
			String userPreference,
			List<LearningContentItem> wordList,
			List<LearningContentItem> phraseList,
			List<LearningContentItem> sentenceList) {
		String input = sceneInput == null || sceneInput.isBlank()
				? "Start a natural English conversation with the learner."
				: sceneInput;
		if (sceneType == SceneType.FREE_CHAT) {
			SessionPrompt prompt = freeChatPromptService.build(new FreeChatPromptContext(
					profile,
					sceneConfig,
					input,
					userPreference));
			return prompt.systemPrompt();
		}
		SessionPrompt prompt = customScenePromptService.build(new CustomScenePromptContext(
				profile,
				sceneConfig,
				sceneType,
				input,
				userPreference,
				wordList,
				phraseList,
				sentenceList));
		return prompt.systemPrompt();
	}

	private String buildSceneName(SceneType sceneType, String sceneInput) {
		if (sceneType == SceneType.FREE_CHAT) {
			return "Free Chat";
		}
		String input = sceneInput == null || sceneInput.isBlank() ? "Custom Scene" : sceneInput.trim();
		if (input.length() <= 24) {
			return input;
		}
		return input.substring(0, 24) + "...";
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
