package com.unispeaking.domain.vo.evaluation;

/** 面试五维评分维度。发音可懂度与流利度来自逐段音频证据；逻辑/语法/词汇来自整场文本评估。 */
public enum InterviewDimension {
	FLUENCY,
	PRONUNCIATION_INTELLIGIBILITY,
	LOGIC_COHERENCE,
	GRAMMAR_CONTROL,
	VOCABULARY_EXPRESSION
}
