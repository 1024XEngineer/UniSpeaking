# UniSpeaking 架构设计（精简版）

# 一、总体结构

系统只保留五个核心模块：

```text
SceneService
SceneFlowService
SessionService
EvaluationService
AiProvider
```

对应关系：

```text
SceneService<R, T>
├── FreeChatSceneService
├── CustomSceneService
└── IeltsSceneService

SceneFlowService<S>
├── CustomSceneFlowService
└── IeltsSceneFlowService

SessionService
└── 所有场景共用

EvaluationService<R, D>
├── CustomEvaluationService
└── IeltsEvaluationService

AiProvider
├── QwenAiProvider
├── DoubaoAiProvider
└── ...
```

场景与模块关系：

| 模块 | FreeChat | Custom | IELTS |
|---|---|---|---|
| SceneService | √ | √ | √ |
| SceneFlowService | × | √ | √ |
| SessionService | √ | √ | √ |
| EvaluationService | × | √ | √ |
| AiProvider | √ | √ | √ |

---

# 二、SceneService

SceneService 只定义“生成场景”这一公共能力。

不同场景生成内容不同，因此使用泛型返回值。

```java
public interface SceneService<R, T> {

    T generate(R request);
}
```

---

## 2.1 FreeChatSceneService

```java
public class FreeChatSceneService
        implements SceneService<
            FreeChatSceneRequest,
            FreeChatSceneResult> {

    @Override
    public FreeChatSceneResult generate(
            FreeChatSceneRequest request) {

        // 调用 AiProvider 生成自由聊天 Prompt
        // 保存场景

        return result;
    }
}
```

返回：

```java
FreeChatSceneResult {
    String sceneId;
    String dialoguePrompt;
}
```

---

## 2.2 CustomSceneService

```java
public class CustomSceneService
        implements SceneService<
            CustomSceneRequest,
            CustomSceneResult> {

    @Override
    public CustomSceneResult generate(
            CustomSceneRequest request) {

        // 调用 AiProvider
        // 生成单词、词组、句子和 Dialogue Prompt

        return result;
    }
}
```

返回：

```java
CustomSceneResult {
    String sceneId;

    List<LearningContentItem> wordList;
    List<LearningContentItem> phraseList;
    List<LearningContentItem> sentenceList;

    String dialoguePrompt;
}
```

---

## 2.3 IeltsSceneService

IELTS 支持：

```text
PRACTICE   专项训练
MOCK_EXAM  模拟考试
```

```java
public class IeltsSceneService
        implements SceneService<
            IeltsSceneRequest,
            IeltsSceneResult> {

    @Override
    public IeltsSceneResult generate(
            IeltsSceneRequest request) {

        // 专项训练：
        // 生成指定 Part

        // 模拟考试：
        // 生成 Part1、Part2、Part3

        return result;
    }
}
```

返回：

```java
IeltsSceneResult {
    String sceneId;
    IeltsMode mode;
    Integer targetPart;

    IeltsPartContent part1;
    IeltsPartContent part2;
    IeltsPartContent part3;
}
```

每个 Part：

```java
IeltsPartContent {
    Integer part;
    List<String> questions;
    List<String> recommendedExpressions;
    String dialoguePrompt;
}
```

---

# 三、SceneFlowService

SceneFlowService 只负责：

```text
开始流程
获取当前阶段
进入下一阶段
判断是否完成
```

FreeChat 没有学习流程，因此不实现 SceneFlowService。

```java
public interface SceneFlowService<S> {

    S start(String sceneId);

    S current(String sceneId);

    S next(String sceneId);

    boolean isCompleted(String sceneId);
}
```

系统不需要 flowId，统一使用 sceneId。

---

## 3.1 CustomSceneFlowService

```java
public enum CustomStage {

    WORD,
    PHRASE,
    SENTENCE,
    DIALOGUE,
    COMPLETED
}
```

```java
public class CustomSceneFlowService
        implements SceneFlowService<CustomStage> {

    @Override
    public CustomStage start(String sceneId) {
        return CustomStage.WORD;
    }

    @Override
    public CustomStage current(String sceneId) {
        return getCurrentStage(sceneId);
    }

    @Override
    public CustomStage next(String sceneId) {

        CustomStage current =
            getCurrentStage(sceneId);

        CustomStage next =
            switch (current) {
                case WORD ->
                    CustomStage.PHRASE;

                case PHRASE ->
                    CustomStage.SENTENCE;

                case SENTENCE ->
                    CustomStage.DIALOGUE;

                case DIALOGUE,
                     COMPLETED ->
                    CustomStage.COMPLETED;
            };

        updateStage(sceneId, next);

        return next;
    }

    @Override
    public boolean isCompleted(String sceneId) {
        return current(sceneId)
            == CustomStage.COMPLETED;
    }
}
```

流程：

```text
WORD
→ PHRASE
→ SENTENCE
→ DIALOGUE
→ COMPLETED
```

---

## 3.2 IeltsSceneFlowService

```java
public enum IeltsStage {

    PART1,
    PART2,
    PART3,
    COMPLETED
}
```

```java
public class IeltsSceneFlowService
        implements SceneFlowService<IeltsStage> {

    @Override
    public IeltsStage start(String sceneId) {

        IeltsScene scene =
            getScene(sceneId);

        if (scene.getMode()
                == IeltsMode.PRACTICE) {

            return convertPart(
                scene.getTargetPart()
            );
        }

        return IeltsStage.PART1;
    }

    @Override
    public IeltsStage current(String sceneId) {
        return getCurrentStage(sceneId);
    }

    @Override
    public IeltsStage next(String sceneId) {

        IeltsScene scene =
            getScene(sceneId);

        if (scene.getMode()
                == IeltsMode.PRACTICE) {

            updateStage(
                sceneId,
                IeltsStage.COMPLETED
            );

            return IeltsStage.COMPLETED;
        }

        // MOCK_EXAM：
        // PART1 → PART2 → PART3 → COMPLETED

        return updateNextStage(sceneId);
    }

    @Override
    public boolean isCompleted(String sceneId) {
        return current(sceneId)
            == IeltsStage.COMPLETED;
    }
}
```

专项：

```text
指定 Part
→ COMPLETED
```

模拟考试：

```text
PART1
→ PART2
→ PART3
→ COMPLETED
```

---

# 四、SessionService

SessionService 所有场景共用。

不按照 FreeChat / Custom / IELTS 分 Service。

```java
public class SessionService {

    StartSessionResponse startSession(
        StartSessionCommand command
    );

    void addMessage(
        String sessionId,
        Message message
    );

    void endSession(
        String sessionId
    );

    SessionDetail getSession(
        String sessionId
    );

    List<SessionDetail> getBySceneId(
        String sceneId
    );
}
```

Session 保存：

```text
sessionId
sceneId
sceneType
stage
dialogue
```

IELTS 模考：

```text
sceneId = ielts_xxx

PART1 → session_001
PART2 → session_002
PART3 → session_003
```

最终通过：

```java
getBySceneId(sceneId)
```

获取三个 Part 的 Session。

---

# 五、EvaluationService

FreeChat 不评分，因此不实现 EvaluationService。

Custom 和 IELTS 都需要：

```text
单轮评价
完整报告
评价详情
```

```java
public interface EvaluationService<R, D> {

    DialogueTurnEvaluationResult evaluateTurn(
        DialogueTurnEvaluationCommand command
    );

    R generateReport(
        String sceneId
    );

    D getEvaluation(
        String sceneId
    );
}
```

---

## 5.1 CustomEvaluationService

```java
public class CustomEvaluationService
        implements EvaluationService<
            CustomEvaluationReport,
            CustomEvaluationDetail> {

    /**
     * Custom 特有：
     * 句子跟读评分。
     */
    public SentenceEvaluationResult evaluateSentence(
            String sentenceId,
            byte[] audio) {

        return result;
    }

    @Override
    public DialogueTurnEvaluationResult
        evaluateTurn(
            DialogueTurnEvaluationCommand command) {

        return result;
    }

    @Override
    public CustomEvaluationReport
        generateReport(
            String sceneId) {

        return report;
    }

    @Override
    public CustomEvaluationDetail
        getEvaluation(
            String sceneId) {

        return detail;
    }
}
```

完整报告：

```java
CustomEvaluationReport {
    pronunciationScore;
    fluencyScore;
    grammarScore;
    vocabularyScore;
    communicationScore;
    finalScore;
    summary;
}
```

---

## 5.2 IeltsEvaluationService

```java
public class IeltsEvaluationService
        implements EvaluationService<
            IeltsEvaluationReport,
            IeltsEvaluationDetail> {

    @Override
    public DialogueTurnEvaluationResult
        evaluateTurn(
            DialogueTurnEvaluationCommand command) {

        return result;
    }

    @Override
    public IeltsEvaluationReport
        generateReport(
            String sceneId) {

        List<SessionDetail> sessions =
            sessionService.getBySceneId(
                sceneId
            );

        // 根据 IELTS Speaking
        // 四项官方标准生成最终评分

        return report;
    }

    @Override
    public IeltsEvaluationDetail
        getEvaluation(
            String sceneId) {

        return detail;
    }
}
```

IELTS 报告：

```java
IeltsEvaluationReport {
    fluencyAndCoherence;
    lexicalResource;
    grammaticalRangeAndAccuracy;
    pronunciation;
    bandScore;
    summary;
}
```

---

# 六、AiProvider

AiProvider 继续统一管理所有模型能力。

```java
public interface AiProvider {

    String exchangeRealtimeSdp(
        String offerSdp,
        String token
    );

    byte[] generateSpeechAudio(
        String text,
        String token
    );

    String executeLlmTask(
        String prompt,
        String token
    );

    String convertAudioToText(
        byte[] audio,
        String token
    );

    String evaluatePronunciation(
        String text,
        byte[] audio,
        String token
    );
}
```

具体实现：

```text
QwenAiProvider
DoubaoAiProvider
...
```

---

# 七、自定义场景伪代码

```java
【1. 生成场景】

CustomSceneService.generate(
    sceneInput = "咖啡店点单"
)

→ CustomSceneResult {
    sceneId = "custom_xxx",
    wordList,
    phraseList,
    sentenceList,
    dialoguePrompt
}


【2. 开始学习流程】

CustomSceneFlowService.start(
    sceneId
)

→ WORD


【3. 单词学习完成】

CustomSceneFlowService.next(
    sceneId
)

→ PHRASE


【4. 词组学习完成】

CustomSceneFlowService.next(
    sceneId
)

→ SENTENCE


【5. 句子学习与跟读评分】

CustomEvaluationService.evaluateSentence(
    sentenceId,
    audio
)

→ SentenceEvaluationResult


CustomSceneFlowService.next(
    sceneId
)

→ DIALOGUE


【6. 开始场景会话】

SessionService.startSession(
    StartSessionCommand {
        sceneId,
        sceneType = CUSTOM,
        stage = DIALOGUE,
        prompt = dialoguePrompt
    }
)

→ sessionId


【7. 实时对话】

SessionService.addMessage(...)


CustomEvaluationService.evaluateTurn(
    DialogueTurnEvaluationCommand {
        sessionId,
        turnNo,
        transcript,
        audio
    }
)

→ DialogueTurnEvaluationResult


【8. 结束会话】

SessionService.endSession(
    sessionId
)


【9. 生成完整评分】

CustomEvaluationService.generateReport(
    sceneId
)

→ CustomEvaluationReport


【10. 获取评价详情】

CustomEvaluationService.getEvaluation(
    sceneId
)

→ CustomEvaluationDetail


【11. 完成流程】

CustomSceneFlowService.next(
    sceneId
)

→ COMPLETED
```

---

# 八、IELTS 场景伪代码

## 8.1 IELTS 专项训练

以 Part2 为例：

```java
【1. 生成 IELTS 专项场景】

IeltsSceneService.generate(
    IeltsSceneRequest {
        mode = PRACTICE,
        targetPart = 2
    }
)

→ IeltsSceneResult {
    sceneId = "ielts_xxx",
    mode = PRACTICE,
    targetPart = 2,
    part2 = {
        questions,
        recommendedExpressions,
        dialoguePrompt
    }
}


【2. 开始流程】

IeltsSceneFlowService.start(
    sceneId
)

→ PART2


【3. 开始 Part2 Session】

SessionService.startSession(
    StartSessionCommand {
        sceneId,
        sceneType = IELTS,
        stage = PART2,
        prompt = part2.dialoguePrompt
    }
)

→ sessionId


【4. IELTS 对话】

SessionService.addMessage(...)


IeltsEvaluationService.evaluateTurn(
    DialogueTurnEvaluationCommand {
        sessionId,
        turnNo,
        transcript,
        audio
    }
)

→ DialogueTurnEvaluationResult


【5. 结束 Session】

SessionService.endSession(
    sessionId
)


【6. 完成专项流程】

IeltsSceneFlowService.next(
    sceneId
)

→ COMPLETED


【7. 生成专项评分】

IeltsEvaluationService.generateReport(
    sceneId
)

→ IeltsEvaluationReport


【8. 获取评价详情】

IeltsEvaluationService.getEvaluation(
    sceneId
)

→ IeltsEvaluationDetail
```

---

## 8.2 IELTS 模拟考试

```java
【1. 生成 IELTS 模考场景】

IeltsSceneService.generate(
    IeltsSceneRequest {
        mode = MOCK_EXAM
    }
)

→ IeltsSceneResult {
    sceneId = "ielts_mock_xxx",
    part1,
    part2,
    part3
}


【2. 开始考试】

IeltsSceneFlowService.start(
    sceneId
)

→ PART1


【3. Part1】

SessionService.startSession(
    sceneId,
    stage = PART1,
    prompt = part1.dialoguePrompt
)

→ session_part1


实时对话 + evaluateTurn()


SessionService.endSession(
    session_part1
)


IeltsSceneFlowService.next(
    sceneId
)

→ PART2


【4. Part2】

SessionService.startSession(
    sceneId,
    stage = PART2,
    prompt = part2.dialoguePrompt
)

→ session_part2


实时对话 + evaluateTurn()


SessionService.endSession(
    session_part2
)


IeltsSceneFlowService.next(
    sceneId
)

→ PART3


【5. Part3】

SessionService.startSession(
    sceneId,
    stage = PART3,
    prompt = part3.dialoguePrompt
)

→ session_part3


实时对话 + evaluateTurn()


SessionService.endSession(
    session_part3
)


IeltsSceneFlowService.next(
    sceneId
)

→ COMPLETED


【6. 聚合三个 Part】

SessionService.getBySceneId(
    sceneId
)

→ [
    PART1 Session,
    PART2 Session,
    PART3 Session
]


【7. 生成完整 IELTS 评分】

IeltsEvaluationService.generateReport(
    sceneId
)

→ IeltsEvaluationReport {
    fluencyAndCoherence,
    lexicalResource,
    grammaticalRangeAndAccuracy,
    pronunciation,
    bandScore,
    summary
}


【8. 获取完整评价详情】

IeltsEvaluationService.getEvaluation(
    sceneId
)

→ IeltsEvaluationDetail
```

---

# 九、FreeChat 调用流程

```java
FreeChatSceneService.generate(
    request
)

→ {
    sceneId,
    dialoguePrompt
}


SessionService.startSession(
    sceneId,
    stage = DIALOGUE,
    prompt = dialoguePrompt
)

→ sessionId


实时对话


SessionService.addMessage(...)


SessionService.endSession(
    sessionId
)
```

FreeChat 不存在：

```text
SceneFlowService
EvaluationService
```

---

# 十、最终设计原则

```text
SceneService
→ 生成场景内容

SceneFlowService
→ 控制存在流程的场景阶段

SessionService
→ 管理真实会话

EvaluationService
→ 管理需要评分场景的评价

AiProvider
→ 提供统一 AI 能力
```

公共接口只定义真正公共的方法。

具体场景存在特殊能力时，可以直接在具体实现类中扩展，不需要继续增加新的 Service。
