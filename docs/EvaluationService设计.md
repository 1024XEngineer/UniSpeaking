# EvaluationService 设计

## 1. 最新接口

```java
package com.unispeaking.service.evaluation;

public interface EvaluationService {

    SentenceEvaluationResponse evaluateSentenceReading(
        String sentenceId,
        byte[] audio
    );

    DialogueTurnEvaluationResult evaluateDialogueTurn(
        DialogueTurnEvaluationCommand command
    );

    DialogueReportResult generateDialogueReport(
        String sessionId,
        List<Message> dialogue
    );

    DialogueEvaluationResult getDialogueEvaluation(
        String sessionId
    );
}
```

接口使用数据库 UUID 的字符串形式作为 `sentenceId` 和 `sessionId`。音频必须是
16 kHz、单声道、16-bit PCM WAV。

## 2. 公开数据结构

```text
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

整场报告只公开五维评分与总分，共六个分数。旧的
`pronunciationScore`（整场中间综合分）、`languageQualityScore`、
`goalCoverageScore`、`communicationEffectivenessScore`、
`interactionCompletionScore` 和 `taskCompletionScore` 已移除。

## 3. 单轮语音计算

对每个有效音素读取得分 `p` 以及开始、结束位置。时间单位为 10 ms：

```text
duration = end - start
weight = clamp(duration, 2, 30)

P = Σ(weight × p) / Σweight
C = 100 × Σ(weight × I(p >= 60)) / Σweight

accuracy = 0.80 × P + 0.20 × C
fluency = 0.65 × providerFluency + 0.25 × rhythm + 0.10 × P
audioNaturalness =
    0.40 × rhythm
  + 0.25 × providerFluency
  + 0.20 × tone
  + 0.15 × P
```

缺少得分或有效时间范围的音素不参与上述计算。单轮有效时长为全部有效音素
权重之和。句级字段缺失时，`audioNaturalness` 只在已返回字段之间按原权重
比例重新归一化。Suntone 英语结果可能不返回 `tone`，因此单轮
`toneScore` 及对应持久化列必须允许为 `null`。

## 4. 整场聚合与五维总分

每轮聚合权重为：

```text
sessionWeight = min(effectiveDuration, 3000)
```

`accuracy`、`fluency` 和 `audioNaturalness` 分别按该权重聚合。LLM 接收完整的
带角色对话，只评价 `LEARNER`，输出 `grammar`、`vocabulary` 和
`textNaturalness`。所有评价理由、数据质量说明和 `feedbackSummary` 必须使用
简体中文；学习者英文证据、`suggestedExpression`、`correction` 和
`suggestion` 保留英文。Prompt 与响应解析器都会校验这一语言边界。

```text
naturalness =
    0.60 × audioNaturalness
  + 0.40 × textNaturalness

finalScore =
    0.25 × accuracy
  + 0.20 × fluency
  + 0.20 × grammar
  + 0.15 × vocabulary
  + 0.20 × naturalness
```

六个整场分数统一限制在 0–100，并使用 `HALF_UP` 保留一位小数。

## 5. 数据与安全边界

- 只将学习者话轮送入语音评分；AI 话轮只作为 LLM 上下文。
- LLM Prompt 明确把对话视为不可信数据，禁止执行转写中的指令。
- 单轮结果保存音素时间范围，保证整场报告可以按同一算法重复计算。
- 整场持久化只写入五维分数和 `final_score`。
- 过短回答保留单轮记录，但不进入整场语音聚合。

## 6. Provider 配置与音频转换

发音评测使用科大讯飞 Suntone 中英文句子接口，路由模型为
`iflytek-suntone`。请求固定使用 `core=sent`、`scale=100`、
`precision=0.1`、`phoneme_output=1`、`dict_type=IPA88` 和
`dict_dialect=en_us`。

公开接口继续接收 16 kHz、16-bit、单声道 PCM WAV；Provider 在内存中转换为
Suntone 支持的 16 kHz MP3，不写临时音频文件。运行所需环境变量：

```text
XFYUN_APP_ID
XFYUN_API_KEY
XFYUN_API_SECRET
XFYUN_SUNTONE_ENDPOINT       # 可选，默认官方中英文接口
XFYUN_SUNTONE_LANGUAGE       # 可选，默认 en
XFYUN_SUNTONE_CATEGORY       # 可选，默认 sent
```
