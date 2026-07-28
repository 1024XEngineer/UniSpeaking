package com.unispeaking.service.prompt;

import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.vo.prompt.CustomScenePromptContext;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CustomScenePromptService extends ScenePromptService<CustomScenePromptContext> {

	@Override
	protected String buildSystemPrompt(CustomScenePromptContext context) {
		return """
				You are UniSpeaking's realtime English scene coach.

				Your job is to run a practical role-play after the learner finishes the pre-dialogue learning flow.

				Scene:
				- type: %s
				- input: %s
				- provider: %s
				- voice: %s

				Learner:
				- userId: %s
				- level: %s
				- nativeLanguage: %s
				- preferredVoiceId: %s
				- userPreference: %s
				- memory: %s

				Required learning material already prepared for this scene:

				Words:
				%s

				Phrases:
				%s

				Sentences:
				%s

				Runtime behavior:
				- Start directly with the role-play context when the session enters DIALOGUE.
				- Keep replies concise, natural, and level-appropriate.
				- Use the prepared words, phrases, and sentences when they fit naturally.
				- Give gentle corrections inside the conversation without stopping the role-play.
				- Stay in character unless the learner explicitly asks for coaching help.
				""".formatted(
				context.sceneType(),
				valueOrDefault(context.sceneInput(), "daily life English practice"),
				context.sceneConfig().providerType(),
				valueOrDefault(context.sceneConfig().defaultVoice(), "Katerina"),
				context.profile().userId(),
				valueOrDefault(context.profile().level(), "not provided"),
				valueOrDefault(context.profile().nativeLanguage(), "not provided"),
				valueOrDefault(context.profile().voiceId(), "not provided"),
				valueOrDefault(context.userPreference(), "not provided"),
				valueOrDefault(context.profile().memory(), "not provided"),
				formatItems(context.wordList()),
				formatItems(context.phraseList()),
				formatItems(context.sentenceList()));
	}

	private String formatItems(List<LearningContentItem> items) {
		if (items == null || items.isEmpty()) {
			return "- none";
		}
		StringBuilder builder = new StringBuilder();
		for (LearningContentItem item : items) {
			builder.append("- ")
					.append(item.englishText())
					.append(" = ")
					.append(valueOrDefault(item.chineseText(), ""))
					.append('\n');
		}
		return builder.toString().trim();
	}

	private String valueOrDefault(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}
}
