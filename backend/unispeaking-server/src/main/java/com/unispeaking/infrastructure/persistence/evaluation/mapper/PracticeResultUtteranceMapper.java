package com.unispeaking.infrastructure.persistence.evaluation.mapper;

import com.unispeaking.infrastructure.persistence.evaluation.utterance.PracticeResultUtteranceRow;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 单轮气泡评分的 PostgreSQL 映射。
 */
public interface PracticeResultUtteranceMapper {

	/**
	 * 按会话和气泡序号覆盖最新评分；冲突更新不替换原记录 ID。
	 */
	@Insert("""
			INSERT INTO public.practice_result_utterances (
			    id,
			    session_id,
			    utterance_no,
			    transcript,
			    ai_text,
			    overall_score,
			    rhythm_score,
			    tone_score,
			    integrity_score,
			    pronunciation_score,
			    fluency_score,
			    feedback_summary,
			    suggested_expression,
			    pronunciation_details,
			    created_at,
			    updated_at
			)
			VALUES (
			    #{row.id},
			    #{row.sessionId},
			    #{row.utteranceNo},
			    #{row.transcript},
			    #{row.aiText, jdbcType=VARCHAR},
			    #{row.overallScore},
			    #{row.rhythmScore},
			    #{row.toneScore, jdbcType=NUMERIC},
			    #{row.integrityScore},
			    #{row.pronunciationScore},
			    #{row.fluencyScore},
			    #{row.feedbackSummary},
			    #{row.suggestedExpression},
			    CAST(#{row.pronunciationDetailsJson} AS jsonb),
			    CURRENT_TIMESTAMP,
			    CURRENT_TIMESTAMP
			)
			ON CONFLICT (session_id, utterance_no) DO UPDATE SET
			    transcript = EXCLUDED.transcript,
			    ai_text = EXCLUDED.ai_text,
			    overall_score = EXCLUDED.overall_score,
			    rhythm_score = EXCLUDED.rhythm_score,
			    tone_score = EXCLUDED.tone_score,
			    integrity_score = EXCLUDED.integrity_score,
			    pronunciation_score = EXCLUDED.pronunciation_score,
			    fluency_score = EXCLUDED.fluency_score,
			    feedback_summary = EXCLUDED.feedback_summary,
			    suggested_expression = EXCLUDED.suggested_expression,
			    pronunciation_details = EXCLUDED.pronunciation_details,
			    updated_at = CURRENT_TIMESTAMP
			""")
	int upsert(@Param("row") PracticeResultUtteranceRow row);

	/**
	 * 查询当前气泡之前的历史记录，供单轮 Prompt 重建上下文。
	 */
	@Select("""
			SELECT
			    id AS id,
			    session_id AS "sessionId",
			    utterance_no AS "utteranceNo",
			    transcript AS transcript,
			    ai_text AS "aiText",
			    overall_score AS "overallScore",
			    rhythm_score AS "rhythmScore",
			    tone_score AS "toneScore",
			    integrity_score AS "integrityScore",
			    pronunciation_score AS "pronunciationScore",
			    fluency_score AS "fluencyScore",
			    feedback_summary AS "feedbackSummary",
			    suggested_expression AS "suggestedExpression",
			    CAST(pronunciation_details AS text)
			        AS "pronunciationDetailsJson"
			FROM public.practice_result_utterances
			WHERE session_id = #{sessionId}
			  AND utterance_no < #{beforeUtteranceNo}
			ORDER BY utterance_no ASC
			""")
	List<PracticeResultUtteranceRow> selectBefore(
			@Param("sessionId") UUID sessionId,
			@Param("beforeUtteranceNo") int beforeUtteranceNo);

	/**
	 * 查询一场练习的全部气泡，正常和过短记录都包含在内。
	 */
	@Select("""
			SELECT
			    id AS id,
			    session_id AS "sessionId",
			    utterance_no AS "utteranceNo",
			    transcript AS transcript,
			    ai_text AS "aiText",
			    overall_score AS "overallScore",
			    rhythm_score AS "rhythmScore",
			    tone_score AS "toneScore",
			    integrity_score AS "integrityScore",
			    pronunciation_score AS "pronunciationScore",
			    fluency_score AS "fluencyScore",
			    feedback_summary AS "feedbackSummary",
			    suggested_expression AS "suggestedExpression",
			    CAST(pronunciation_details AS text)
			        AS "pronunciationDetailsJson"
			FROM public.practice_result_utterances
			WHERE session_id = #{sessionId}
			ORDER BY utterance_no ASC
			""")
	List<PracticeResultUtteranceRow> selectAll(
			@Param("sessionId") UUID sessionId);
}
