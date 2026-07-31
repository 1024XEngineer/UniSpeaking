![[企业微信截图_856d95a0-b55d-4eb1-a489-88e7f551cea4 1.png]]
# 一、SceneService
```java
package com.unispeaking.SceneService;

public interface SceneService {

    /**
     * 根据场景类型和用户输入生成场景内容。
     */
    SceneGenerationResponse generateScene(
        SceneGenerationRequest request
    );
}
enum SceneType {
    FREE_CHAT,
    CUSTOM_SCENE,
    INTERVIEW_SCENE,
    IELTS_SCENE
}
LearningContentItem {
    String contentId;
    String englishText;
    String chineseText;
    String phonetic;
}
SceneGenerationRequest {
    SceneType sceneType;
    String sceneInput;
}
SceneGenerationResponse {
    String sceneId;
    List<LearningContentItem> wordList;
    List<LearningContentItem> phraseList;
    List<LearningContentItem> sentenceList;
    String scenePrompt;
}

```

# 二、SceneFlowService
```java
package com.unispeaking.SceneFlowService;

public interface SceneFlowService {
    // 为已经生成完成的场景创建流程。
    SceneFlowResponse createFlow(
        String sceneId
    );
    // 完成当前阶段并进入下一阶段。
    SceneFlowResponse advanceStage(SceneFlowStage stage);

    void completeFlow(Boolean completed);

    //获取当前阶段的内容
    List<T> getByCurrentStage(SceneFlowStage stage);

}
enum SceneFlowStage {
    WORD_LEARNING,
    PHRASE_LEARNING,
    SENTENCE_LEARNING,
    DIALOGUE,
    COMPLETED
}

SceneFlowResponse {
    String sceneId;
    SceneFlowStage stage;
    Boolean completed;
}
```


# 三、SessionService
```java
package com.unispeaking.SessionService;

public interface SessionService {
	List<Message> dialogue;
    /**
     * 开始一次业务会话，生成 sessionId 并记录开始时间。
     */
    StartSessionResponse startSession(String prompt);

    /**
     * 向当前会话中追加一条用户或 AI 的完整消息。
     *
     * 只保存最终完整文本，不保存流式 delta。
     * 消息可以先追加到内存，再异步写入数据库。
     */
    void addMessage(Message message);

    /**
     * 结束当前业务会话，记录结束时间。
     */
    void endSession(String sessionId,String stopTime);
}
StartSessionResponse {
    String sessionId;
    String startTime;
}
Message {
    Integer owner;    // 1：用户，0：模型
    String content;   // 用户或模型的完整文本
    byte[] audio;     // 可选音频，模型消息通常为空
}
```
# 四、EvaluationService
```java
package com.unispeaking.EvaluationService;

public interface EvaluationService {

		SentenceEvaluationResponse evaluateSentenceReading(
		String sentenceId,byte[] audio
		);

	DialogueTurnEvaluationResult evaluateDialogueTurn(
		DialogueTurnEvaluationCommand command
		);

	DialogueReportResult generateDialogueReport(
		String sessionId,List<Message> dialogue
		);

	DialogueEvaluationResult getDialogueEvaluation(
		String sessionId
		);
}

SentenceEvaluationResponse(
	BigDecimal overallScore,
	Boolean passed,
	List<WordPronunciationScore> words
)

WordPronunciationScore(
	String word,
	BigDecimal wordScore,
	List<PhonemeScore> phonemes
)

PhonemeScore(
	String expectedPhoneme,
	String actualPhoneme,
	BigDecimal score
)

DialogueTurnEvaluationCommand(
	String sessionId,
	Integer turnNo,
	byte[] audio,
	String transcript
)
// 保存的单轮评分
DialogueTurnEvaluationResult(
	Integer turnNo,
	String transcript,
	BigDecimal overallScore,
	BigDecimal rhythmScore,
	BigDecimal toneScore,
	BigDecimal integrityScore,
	BigDecimal pronunciationScore,
	BigDecimal fluencyScore,
	String feedbackSummary,
	String suggestedExpression,
	List<WordPronunciationScore> words
)

DialogueReportResult(
	BigDecimal accuracyScore,
	BigDecimal fluencyScore,
	BigDecimal grammarScore,
	BigDecimal vocabularyScore,
	BigDecimal naturalnessScore,
	BigDecimal finalScore,
	String summary,
	List<String> strengths,
	List<String> improvements
)

DialogueEvaluationResult(
	List<Message> dialogue,
	List<DialogueTurnEvaluationResult> turnEvaluation
)
```
# 五、AiProvider
```java
package com.unispeaking.AiProvider;

public interface AiProvider {

    /**
     * 完成 Realtime 模型的 Offer SDP / Answer SDP 交换。
     */
    String exchangeRealtimeSdp(String offerSdp,String token);

    /**
     * 将文本转换为语音音频。
     */
    Byte[]  generateSpeechAudio(String text,String token);

    /**
     * 调用 LLM 执行场景生成、文本评分等任务。
     */
    String executeLlmTask(String prompt,String token);

    /**
     * 将音频转换为文字。
     */
    String convertAudioToText(Byte[] audio,String token);

    /**
     * 评估用户跟读音频的发音和音素表现。
     */
    StrievaluatePronunciation(
        String text,Byte[] audio,String token
    );

}

```

# 六、自定义场景伪代码
```java
【一、场景生成】

SceneService.generateScene(
    request = SceneGenerationRequest {
        userId = "user_1001",
        userPreference = "英语基础一般，喜欢慢速对话",
        sceneType = CUSTOM_SCENE,
        sceneInput = "咖啡店点单"
    }
)
    └── AiProvider.executeLlmTask(
            prompt = "根据用户偏好生成咖啡店点单场景的单词、词组、句子和对话 Prompt",
            token = "LLM调用Token"
        )
        → String sceneGenerationJson

→ SceneGenerationResponse {
    // 格式：场景类型_随机唯一字符串
    sceneId = "custom_jcoeow1232",
    wordList = [
        LearningContentItem {
            contentId = "word_001",
            englishText = "coffee",
            chineseText = "咖啡",
            phonetic = "/ˈkɒfi/"
        }
    ],
    phraseList = [
        LearningContentItem {
            contentId = "phrase_001",
            englishText = "a cup of coffee",
            chineseText = "一杯咖啡",
            phonetic = null
        }
    ],
    sentenceList = [
        LearningContentItem {
            contentId = "sentence_001",
            englishText = "Could I have a cup of coffee?",
            chineseText = "我可以要一杯咖啡吗？",
            phonetic = null
        }
    ],
    scenePrompt = "你是一名咖啡店店员……"
}


【二、创建场景流程】

SceneFlowService.createFlow(
    sceneId = "custom_jcoeow1232"
)
→ SceneFlowResponse {
    sceneId = "custom_jcoeow1232",
    stage = WORD_LEARNING,
    completed = false
}


【三、单词学习】

SceneFlowService.getByCurrentStage(
    stage = WORD_LEARNING
)
→ List<LearningContentItem> wordList

AiProvider.generateSpeechAudio(
    text = wordList[0].englishText,
    token = "TTS调用Token"
)
→ Byte[] wordAudio

SceneFlowService.advanceStage(
    stage = WORD_LEARNING
)
→ SceneFlowResponse {
    sceneId = "custom_jcoeow1232",
    stage = PHRASE_LEARNING,
    completed = false
}


【四、词组学习】

SceneFlowService.getByCurrentStage(
    stage = PHRASE_LEARNING
)
→ List<LearningContentItem> phraseList

AiProvider.generateSpeechAudio(
    text = phraseList[0].englishText,
    token = "TTS调用Token"
)
→ Byte[] phraseAudio

SceneFlowService.advanceStage(
    stage = PHRASE_LEARNING
)
→ SceneFlowResponse {
    sceneId = "custom_jcoeow1232",
    stage = SENTENCE_LEARNING,
    completed = false
}


【五、句子学习与跟读评分】

SceneFlowService.getByCurrentStage(
    stage = SENTENCE_LEARNING
)
→ List<LearningContentItem> sentenceList

AiProvider.generateSpeechAudio(
    text = sentenceList[0].englishText,
    token = "TTS调用Token"
)
→ Byte[] sentenceAudio

EvaluationService.evaluateSentenceReading(
    sentenceId = sentenceList[0].contentId,
    audio = byte[]("用户跟读音频")
)
    └── AiProvider.evaluatePronunciation(
            text = sentenceList[0].englishText,
            audio = Byte[]("用户跟读音频"),
            token = "发音评测Token"
        )
        → String pronunciationEvaluationJson

→ SentenceEvaluationResponse {
    overallScore = 86,
    passed = true,
    words = [
        WordPronunciationScore {
            word = "coffee",
            wordScore = 82,
            phonemes = [
                PhonemeScore {
                    expectedPhoneme = "ɒ",
                    actualPhoneme = "ɔː",
                    score = 76
                }
            ]
        }
    ]
}

SceneFlowService.advanceStage(
    stage = SENTENCE_LEARNING
)
→ SceneFlowResponse {
    sceneId = "custom_jcoeow1232",
    stage = DIALOGUE,
    completed = false
}


【六、开始场景会话】

SessionService.startSession(
    prompt = "你是一名咖啡店店员……"
)
→ StartSessionResponse {
    sessionId = "session_5001",
    startTime = "2026-07-24 10:30:00"
}

AiProvider.exchangeRealtimeSdp(
    offerSdp = "客户端 Offer SDP",
    token = "Realtime临时Token"
)
→ String answerSdp


【七、保存对话内容】

SessionService.addMessage(
    message = Message {
        owner = 0,
        content = "What would you like to order?",
        audio = null
    }
)
→ void

SessionService.addMessage(
    message = Message {
        owner = 1,
        content = "I would like a cup of coffee.",
        audio = byte[]("用户本轮音频")
    }
)
→ void


【八、单轮对话评分】

EvaluationService.evaluateDialogueTurn(
    command = DialogueTurnEvaluationCommand {
        sessionId = "session_5001",
        turnNo = 1,
        audio = byte[]("用户本轮音频"),
        transcript = "I would like a cup of coffee."
    }
)
    ├── AiProvider.evaluatePronunciation(
            text = "I would like a cup of coffee.",
            audio = Byte[]("用户本轮音频"),
            token = "发音评测Token"
        )
        → String pronunciationEvaluationJson

    └── AiProvider.executeLlmTask(
            prompt = "结合本轮转写文本和发音评测结果，评估节奏、语调、完整度、发音和流利度",
            token = "LLM调用Token"
        )
        → String dialogueTurnEvaluationJson

→ DialogueTurnEvaluationResult {
    turnNo = 1,
    transcript = "I would like a cup of coffee.",
    overallScore = 84,
    rhythmScore = 83,
    toneScore = 82,
    integrityScore = 88,
    pronunciationScore = 86,
    fluencyScore = 83,
    feedbackSummary = "表达完整，语速基本自然，但重音仍需改善。",
    suggestedExpression = "I'd like a cup of coffee, please.",
    words = [...]
}


【九、结束会话】

SessionService.endSession(
    sessionId = "session_5001",
    stopTime = "2026-07-24 10:42:00"
)
→ void


【十、会话结束后生成五维评分】

EvaluationService.generateDialogueReport(
    sessionId = "session_5001",
    dialogue = SessionService.dialogue
)
    └── AiProvider.executeLlmTask(
            prompt = "根据完整对话和各轮评分生成对话总报告",
            token = "LLM调用Token"
        )
        → String dialogueReportJson

→ DialogueReportResult {
    accuracyScore = 85,
    fluencyScore = 81,
    grammarScore = 86,
    vocabularyScore = 79,
    naturalnessScore = 80,
    pronunciationScore = 84,
    languageQualityScore = 83,
    goalCoverageScore = 90,
    communicationEffectivenessScore = 87,
    interactionCompletionScore = 88,
    taskCompletionScore = 90,
    finalScore = 85,
    summary = "用户能够完成咖啡店点单交流……",
    strengths = [
        "能够清楚表达点单需求",
        "主要句型使用正确"
    ],
    improvements = [
        "加强单词重音练习",
        "使用更自然、礼貌的点单表达"
    ]
}

→ 前端弹出本次会话评分 {
    finalScore = 85,
    dimensionScore = {
        pronunciation = 84,
        fluency = 81,
        grammar = 86,
        vocabulary = 79,
        communication = 87
    },
    summary = "用户能够完成咖啡店点单交流……"
}


【十一、进入学习资产页面查看对话详情】

EvaluationService.getDialogueEvaluation(
    sessionId = "session_5001"
)
→ DialogueEvaluationResult {
    // 展示本次会话的完整对话内容
    dialogue = [
        Message {
            owner = 0,
            content = "What would you like to order?",
            audio = null
        },
        Message {
            owner = 1,
            content = "I would like a cup of coffee.",
            audio = byte[]("用户本轮音频")
        }
    ],

    // 只针对用户每一轮表达展示纠错信息和推荐表达
    turnEvaluation = [
        DialogueTurnEvaluationResult {
            turnNo = 1,
            transcript = "I would like a cup of coffee.",
            feedbackSummary = "表达正确，但礼貌性和自然度可以进一步提升。",
            suggestedExpression = "I'd like a cup of coffee, please.",
            overallScore = 84,
            rhythmScore = 83,
            toneScore = 82,
            integrityScore = 88,
            pronunciationScore = 86,
            fluencyScore = 83,
            words = [...]
        }
    ]
}


【十二、完成场景流程】

SceneFlowService.advanceStage(
    stage = DIALOGUE
)
→ SceneFlowResponse {
    sceneId = "custom_jcoeow1232",
    stage = COMPLETED,
    completed = true
}

SceneFlowService.completeFlow(
    completed = true
)
→ void
```
