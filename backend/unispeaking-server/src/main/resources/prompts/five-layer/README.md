# UniSpeaking Five-Layer Prompt

The final system prompt is assembled in this order:

1. `L1_Base_Duty.md`: global duty and safety rules.
2. `L2_Coach_*.md`: coach persona selected by `preferred_voice`.
3. `L3_Difficulty_*.md` plus `L3_Speed_*.md`: adaptation selected by
   `cefr_level` and `preferred_ai_speech_speed`.
4. `L4_Learner_Memory.template.md`: long-term learner profile from
   `user_preference.memory_text`.
5. `L5_Open_Conversation.template.md` for free chat, or
   `L5_Current_Scene.template.md` for every other scene.

## Database mappings

| Database value | Template |
| --- | --- |
| `Katerina` | Clara |
| `Aiden` | David |
| `Raymond` | Leo |
| `Tina` | Emily |
| `Harvey` | James |
| `Dolce` | Arthur |
| `A` | Starter |
| `B` | Basic |
| `C` | Connected |
| `D` | Fluent |
| `SLOWER` | 0.5 / 70 WPM |
| `MODERATE` | 1.0 / 120 WPM |
| `NATURAL` | 1.5 / 165 WPM |
| `FASTER` | 2.0 / 210 WPM |

## Editing templates

By default, templates are loaded from this classpath directory.

To edit prompts outside the packaged application, set:

```text
PROMPT_TEMPLATE_DIR=/absolute/path/to/UniSpeaking_完整版提示词
```

The external files are read again for every new scene by
`FiveLayerPromptService`, so updating a Markdown file affects the next generated
prompt without changing Java code.

Supported template variables:

- L4: `{{memory_text}}`
- L5: `{{ai_role}}`, `{{title}}`, `{{learning_goal}}`, `{{scene_type}}`,
  `{{scene_input}}`, `{{current_preference}}`, `{{prepared_words}}`,
  `{{prepared_phrases}}`, `{{prepared_sentences}}`
