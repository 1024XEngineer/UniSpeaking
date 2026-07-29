package com.unispeaking.infrastructure.persistence.evaluation.mapper;

import com.unispeaking.infrastructure.persistence.evaluation.result.PracticeResultScores;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 五维整场评分在 {@code practice_results} 表中的数据库映射。
 */
public interface PracticeResultEvaluationMapper {

	@Insert("""
			INSERT INTO public.practice_results (
			    session_id,
			    accuracy_score,
			    fluency_score,
			    grammar_score,
			    vocabulary_score,
			    naturalness_score,
			    final_score
			) VALUES (
			    #{result.sessionId},
			    #{result.accuracyScore},
			    #{result.fluencyScore},
			    #{result.grammarScore},
			    #{result.vocabularyScore},
			    #{result.naturalnessScore},
			    #{result.finalScore}
			)
			ON CONFLICT (session_id) DO UPDATE SET
			    accuracy_score = EXCLUDED.accuracy_score,
			    fluency_score = EXCLUDED.fluency_score,
			    grammar_score = EXCLUDED.grammar_score,
			    vocabulary_score = EXCLUDED.vocabulary_score,
			    naturalness_score = EXCLUDED.naturalness_score,
			    final_score = EXCLUDED.final_score,
			    updated_at = CURRENT_TIMESTAMP
			""")
	int upsert(@Param("result") PracticeResultScores result);

	@Select("""
			SELECT
			    session_id AS "sessionId",
			    accuracy_score AS "accuracyScore",
			    fluency_score AS "fluencyScore",
			    grammar_score AS "grammarScore",
			    vocabulary_score AS "vocabularyScore",
			    naturalness_score AS "naturalnessScore",
			    final_score AS "finalScore"
			FROM public.practice_results
			WHERE session_id = #{sessionId}
			""")
	PracticeResultScores findBySessionId(
			@Param("sessionId") UUID sessionId);
}
