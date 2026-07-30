package com.unispeaking.service.prompt;

import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.domain.vo.scene.SceneConfig;
import com.unispeaking.domain.vo.scene.SceneType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class FiveLayerPromptService {

	private static final String CLASSPATH_ROOT = "prompts/five-layer/";
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-zA-Z0-9_]+)}}");

	private static final Map<String, CoachTemplate> COACH_BY_VOICE = Map.of(
			"KATERINA", new CoachTemplate("Clara", "L2_Coach_Clara.md"),
			"AIDEN", new CoachTemplate("David", "L2_Coach_David.md"),
			"RAYMOND", new CoachTemplate("Leo", "L2_Coach_Leo.md"),
			"TINA", new CoachTemplate("Emily", "L2_Coach_Emily.md"),
			"HARVEY", new CoachTemplate("James", "L2_Coach_James.md"),
			"DOLCE", new CoachTemplate("Arthur", "L2_Coach_Arthur.md"));

	private static final Map<String, String> DIFFICULTY_BY_LEVEL = Map.ofEntries(
			Map.entry("A", "L3_Difficulty_Starter.md"),
			Map.entry("STARTER", "L3_Difficulty_Starter.md"),
			Map.entry("B", "L3_Difficulty_Basic.md"),
			Map.entry("BASIC", "L3_Difficulty_Basic.md"),
			Map.entry("C", "L3_Difficulty_Connected.md"),
			Map.entry("CONNECTED", "L3_Difficulty_Connected.md"),
			Map.entry("INTERMEDIATE", "L3_Difficulty_Connected.md"),
			Map.entry("D", "L3_Difficulty_Fluent.md"),
			Map.entry("FLUENT", "L3_Difficulty_Fluent.md"));

	private static final Map<String, String> SPEED_BY_PREFERENCE = Map.of(
			"SLOWER", "L3_Speed_0.5_70WPM.md",
			"MODERATE", "L3_Speed_1.0_120WPM.md",
			"NATURAL", "L3_Speed_1.5_165WPM.md",
			"FASTER", "L3_Speed_2.0_210WPM.md");

	private final Path externalDirectory;

	public FiveLayerPromptService(
			@Value("${prompt.templates.directory:}") String externalDirectory) {
		this.externalDirectory = externalDirectory == null || externalDirectory.isBlank()
				? null
				: Path.of(externalDirectory).toAbsolutePath().normalize();
	}

	public List<String> compose(
			UserProfile profile,
			SceneConfig sceneConfig,
			SceneType sceneType,
			String sceneInput,
			String userPreference,
			List<LearningContentItem> words,
			List<LearningContentItem> phrases,
			List<LearningContentItem> sentences) {
		return compose(
				profile,
				sceneConfig,
				sceneType,
				sceneInput,
				userPreference,
				words,
				phrases,
				sentences,
				null);
	}

	public List<String> compose(
			UserProfile profile,
			SceneConfig sceneConfig,
			SceneType sceneType,
			String sceneInput,
			String userPreference,
			List<LearningContentItem> words,
			List<LearningContentItem> phrases,
			List<LearningContentItem> sentences,
			CustomSceneDefinition customScene) {
		CoachTemplate coach = selectCoach(profile.voiceId(), sceneConfig);
		String layer3 = String.join(
				"\n\n",
				load(selectDifficulty(profile.level())),
				load(selectSpeed(profile.aiSpeechSpeed())));
		String layer4 = render(
				load("L4_Learner_Memory.template.md"),
				Map.of("memory_text", valueOrDefault(
						profile.memoryText(),
						"No long-term learner profile has been provided.")));
		String layer5 = render(
				load(sceneType == SceneType.FREE_CHAT
						? "L5_Open_Conversation.template.md"
						: "L5_Current_Scene.template.md"),
				sceneVariables(
						sceneType,
						sceneInput,
						userPreference,
						coach.name(),
						words,
						phrases,
						sentences,
						customScene));
		return List.of(
				load("L1_Base_Duty.md"),
				load(coach.fileName()),
				layer3,
				layer4,
				layer5);
	}

	private CoachTemplate selectCoach(String preferredVoice, SceneConfig sceneConfig) {
		String voice = valueOrDefault(
				preferredVoice,
				sceneConfig == null ? null : sceneConfig.defaultVoice());
		return COACH_BY_VOICE.getOrDefault(
				normalize(voice),
				COACH_BY_VOICE.get("KATERINA"));
	}

	private String selectDifficulty(String level) {
		return DIFFICULTY_BY_LEVEL.getOrDefault(
				normalize(level),
				"L3_Difficulty_Connected.md");
	}

	private String selectSpeed(String speed) {
		return SPEED_BY_PREFERENCE.getOrDefault(
				normalize(speed),
				"L3_Speed_1.5_165WPM.md");
	}

	private Map<String, String> sceneVariables(
			SceneType sceneType,
			String sceneInput,
			String userPreference,
			String coachName,
			List<LearningContentItem> words,
			List<LearningContentItem> phrases,
			List<LearningContentItem> sentences,
			CustomSceneDefinition customScene) {
		String input = valueOrDefault(
				sceneInput,
				"Start a natural learner-led English conversation.");
		String currentPreference = valueOrDefault(
				userPreference,
				"No additional preference was supplied for this call.");
		boolean freeChat = sceneType == SceneType.FREE_CHAT;
		String aiRole = freeChat
				? coachName + ", the selected English speaking coach"
				: valueOrDefault(
						customScene == null ? null : customScene.aiRole(),
						"the realistic counterpart appropriate to the learner's scenario, "
								+ "while retaining " + coachName + "'s coaching style");
		return Map.ofEntries(
				Map.entry("ai_role", aiRole),
				Map.entry(
						"title",
						freeChat
								? "open conversation"
								: valueOrDefault(
										customScene == null ? null : customScene.title(),
										input)),
				Map.entry(
						"background",
						valueOrDefault(
								customScene == null ? null : customScene.background(),
								input)),
				Map.entry(
						"user_role",
						valueOrDefault(
								customScene == null ? null : customScene.userRole(),
								"the learner")),
				Map.entry(
						"learning_goal",
						valueOrDefault(
								customScene == null ? null : customScene.learningGoal(),
								input)),
				Map.entry(
						"custom_instruction",
						valueOrDefault(
								customScene == null ? null : customScene.customInstruction(),
								"No additional scene instruction.")),
				Map.entry(
						"success_factor",
						valueOrDefault(
								customScene == null ? null : customScene.successFactorJson(),
								"{}")),
				Map.entry("scene_type", sceneType.name()),
				Map.entry("scene_input", input),
				Map.entry("current_preference", currentPreference),
				Map.entry("prepared_words", formatItems(words)),
				Map.entry("prepared_phrases", formatItems(phrases)),
				Map.entry("prepared_sentences", formatItems(sentences)));
	}

	private String load(String fileName) {
		try {
			if (externalDirectory != null) {
				Path template = externalDirectory.resolve(fileName).normalize();
				if (!template.startsWith(externalDirectory)) {
					throw new IllegalArgumentException("invalid prompt template path: " + fileName);
				}
				return Files.readString(template, StandardCharsets.UTF_8).strip();
			}
			return new ClassPathResource(CLASSPATH_ROOT + fileName)
					.getContentAsString(StandardCharsets.UTF_8)
					.strip();
		}
		catch (IOException exception) {
			throw new IllegalStateException("cannot load prompt template: " + fileName, exception);
		}
	}

	private String render(String template, Map<String, String> variables) {
		Matcher matcher = PLACEHOLDER.matcher(template);
		StringBuilder rendered = new StringBuilder();
		while (matcher.find()) {
			String key = matcher.group(1);
			if (!variables.containsKey(key)) {
				throw new IllegalArgumentException("missing prompt variable: " + key);
			}
			matcher.appendReplacement(
					rendered,
					Matcher.quoteReplacement(valueOrDefault(variables.get(key), "not provided")));
		}
		matcher.appendTail(rendered);
		return rendered.toString().strip();
	}

	private String formatItems(List<LearningContentItem> items) {
		if (items == null || items.isEmpty()) {
			return "none";
		}
		return items.stream()
				.map(item -> "- " + item.englishText() + " = "
						+ valueOrDefault(item.chineseText(), ""))
				.reduce((left, right) -> left + "\n" + right)
				.orElse("none");
	}

	private String valueOrDefault(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.strip();
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
	}

	private record CoachTemplate(String name, String fileName) {
	}
}
